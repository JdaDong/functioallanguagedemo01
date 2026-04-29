// 124_DoobieProcessManagerRepository.scala
// 进程管理器 第四步：Doobie 进程管理器仓库
//
// 核心思想：
//   进程管理器的持久化需要三张表：
//     - process_instances：进程当前状态
//     - process_events：进程收到的事件（幂等去重 + 时序记录）
//     - process_commands：进程发出的命令（Outbox 模式）
//
//   在同一事务内完成：
//     1. 检查 eventId 幂等（INSERT OR IGNORE）
//     2. 更新进程状态
//     3. 写入新命令到命令队列
//   这样保证"状态推进"与"命令发出"的原子性

//> using scala "3.3.3"
//> using dep "org.typelevel::cats-effect:3.5.4"
//> using dep "org.tpolecat::doobie-core:1.0.0-RC4"
//> using dep "org.tpolecat::doobie-h2:1.0.0-RC4"

import cats.effect.{IO, IOApp}
import cats.syntax.all._
import doobie._
import doobie.implicits._
import doobie.h2.H2Transactor

// ── 进程状态 ──────────────────────────────────────────────────────────────────

case class ProcessInstance(
    orderId:    String,
    sku:        String,
    quantity:   Int,
    warehouseId: Option[String],
    status:     String
)

case class ProcessCommand(
    id:          Long,
    orderId:     String,
    commandType: String,
    detail:      String,
    published:   Boolean
)

// ── 仓库 ──────────────────────────────────────────────────────────────────────

object ProcessManagerRepo:

  val initDDL: ConnectionIO[Unit] =
    for
      _ <- sql"""
        CREATE TABLE IF NOT EXISTS process_instances (
          order_id     VARCHAR PRIMARY KEY,
          sku          VARCHAR NOT NULL DEFAULT '',
          quantity     INT     NOT NULL DEFAULT 0,
          warehouse_id VARCHAR,
          status       VARCHAR NOT NULL DEFAULT 'awaiting-payment'
        )""".update.run.void

      _ <- sql"""
        CREATE TABLE IF NOT EXISTS process_events (
          id         BIGINT IDENTITY PRIMARY KEY,
          order_id   VARCHAR NOT NULL,
          event_id   VARCHAR NOT NULL,
          event_type VARCHAR NOT NULL,
          CONSTRAINT uq_process_event UNIQUE (order_id, event_id)
        )""".update.run.void

      _ <- sql"""
        CREATE TABLE IF NOT EXISTS process_commands (
          id           BIGINT IDENTITY PRIMARY KEY,
          order_id     VARCHAR NOT NULL,
          command_type VARCHAR NOT NULL,
          detail       VARCHAR NOT NULL DEFAULT '',
          published    BOOLEAN NOT NULL DEFAULT FALSE
        )""".update.run.void
    yield ()

  /** 检查 eventId 是否已处理（幂等） */
  def isEventProcessed(orderId: String, eventId: String): ConnectionIO[Boolean] =
    sql"SELECT COUNT(*) FROM process_events WHERE order_id = $orderId AND event_id = $eventId"
      .query[Int].unique.map(_ > 0)

  /** 取进程实例（不存在则返回初始状态） */
  def loadProcess(orderId: String): ConnectionIO[ProcessInstance] =
    sql"SELECT order_id, sku, quantity, warehouse_id, status FROM process_instances WHERE order_id = $orderId"
      .query[ProcessInstance].option
      .map(_.getOrElse(ProcessInstance(orderId, "", 0, None, "awaiting-payment")))

  /** 事务性推进：幂等检查 + 状态更新 + 命令写入 */
  def advance(
      orderId:     String,
      eventId:     String,
      eventType:   String,
      newSku:      String,
      newQty:      Int,
      newWh:       Option[String],
      newStatus:   String,
      newCommands: List[(String, String)]   // List[(commandType, detail)]
  ): ConnectionIO[Either[String, Int]] =
    for
      already <- isEventProcessed(orderId, eventId)
      result  <-
        if already then
          FC.pure(Left(s"eventId=$eventId 已处理（幂等）"))
        else
          for
            // 1. 记录事件
            _ <- sql"""
                   INSERT INTO process_events (order_id, event_id, event_type)
                   VALUES ($orderId, $eventId, $eventType)
                 """.update.run

            // 2. upsert 进程状态
            _ <- sql"""
                   MERGE INTO process_instances (order_id, sku, quantity, warehouse_id, status)
                   KEY (order_id)
                   VALUES ($orderId, $newSku, $newQty, $newWh, $newStatus)
                 """.update.run

            // 3. 写入新命令到队列
            n <- newCommands.traverse_ { case (ct, detail) =>
                   sql"""
                     INSERT INTO process_commands (order_id, command_type, detail)
                     VALUES ($orderId, $ct, $detail)
                   """.update.run
                 } *> FC.pure(newCommands.length)
          yield Right(n)
    yield result

  /** 查询进程状态 */
  def queryProcess(orderId: String): ConnectionIO[ProcessInstance] =
    loadProcess(orderId)

  /** 查询待发布命令 */
  def pendingCommands: ConnectionIO[List[ProcessCommand]] =
    sql"""
      SELECT id, order_id, command_type, detail, published
      FROM process_commands WHERE published = FALSE ORDER BY id
    """.query[ProcessCommand].to[List]

  /** 标记命令为已发布 */
  def markPublished(id: Long): ConnectionIO[Unit] =
    sql"UPDATE process_commands SET published = TRUE WHERE id = $id".update.run.void

// ── 演示 ──────────────────────────────────────────────────────────────────────

object DoobieProcessManagerRepositoryDemo extends IOApp.Simple:

  def run: IO[Unit] =
    H2Transactor
      .newH2Transactor[IO](
        "jdbc:h2:mem:procmanager;DB_CLOSE_DELAY=-1",
        "sa",
        "",
        scala.concurrent.ExecutionContext.global
      )
      .use { xa =>
        for
          _ <- ProcessManagerRepo.initDDL.transact(xa)
          _ <- IO.println("=== Doobie 进程管理器仓库：状态 + 事件日志 + 命令队列 ===\n")

          // 1. 订单确认
          r1 <- ProcessManagerRepo.advance(
                  "o-1", "evt-001", "OrderConfirmed",
                  "SKU-A", 2, None, "awaiting-payment", Nil
                ).transact(xa)
          _  <- IO.println(s"[✓] OrderConfirmed: $r1")

          // 2. 支付完成 → 发出库存预留命令
          r2 <- ProcessManagerRepo.advance(
                  "o-1", "evt-002", "PaymentCompleted",
                  "SKU-A", 2, None, "awaiting-inventory",
                  List(("RequestInventoryReservation", "sku=SKU-A qty=2"))
                ).transact(xa)
          _  <- IO.println(s"[✓] PaymentCompleted: $r2")

          // 3. 幂等重放（相同 eventId）
          r3 <- ProcessManagerRepo.advance(
                  "o-1", "evt-002", "PaymentCompleted",
                  "SKU-A", 2, None, "awaiting-inventory", Nil
                ).transact(xa)
          _  <- IO.println(s"[幂等] PaymentCompleted 重放: $r3")

          // 4. 库存确认 → 发出发货命令
          r4 <- ProcessManagerRepo.advance(
                  "o-1", "evt-003", "InventoryReserved",
                  "SKU-A", 2, Some("WH-SZ"), "awaiting-shipment",
                  List(("RequestShipment", "warehouseId=WH-SZ"))
                ).transact(xa)
          _  <- IO.println(s"[✓] InventoryReserved: $r4")

          // 5. 查询进程状态
          proc <- ProcessManagerRepo.queryProcess("o-1").transact(xa)
          _    <- IO.println(s"\n── 进程状态 ─────────────────────────────────")
          _    <- IO.println(s"  $proc")

          // 6. 查询待执行命令
          cmds <- ProcessManagerRepo.pendingCommands.transact(xa)
          _    <- IO.println(s"\n── 待执行命令（共 ${cmds.length} 条）───────────────────")
          _    <- cmds.traverse_(c => IO.println(s"  [id=${c.id}] ${c.orderId}: ${c.commandType} | ${c.detail}"))

          // 7. 模拟调度器执行并标记发布
          _ <- cmds.traverse_(c => ProcessManagerRepo.markPublished(c.id).transact(xa))
          pending <- ProcessManagerRepo.pendingCommands.transact(xa)
          _  <- IO.println(s"\n[调度器执行完毕] 剩余待执行命令: ${pending.length} 条")

          _ <- IO.println("""
|关键点：
|  1. 三步原子事务：幂等检查 + 状态更新 + 命令写入
|  2. UNIQUE(order_id, event_id) 保证相同事件只处理一次
|  3. 命令队列是 Outbox 模式：先写库再由调度器发布
|  4. markPublished 解耦命令执行与状态推进，失败时可安全重试""".stripMargin)
        yield ()
      }
