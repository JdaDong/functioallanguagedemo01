// 114_DoobieTransactionalCommandWrite.scala
// CQRS 命令查询职责分离 第四步：Doobie 事务命令写入
//
// 核心思想：
//   在真实系统里，CQRS 的写侧需要：
//     1. 写入写模型（command_write_model）
//     2. 写入读模型投影（command_read_model）
//     3. 记录命令日志（command_log）
//   这三步必须在同一个数据库事务里完成。
//   任何一步失败，整次命令执行全部回滚。
//
// 本 Demo 演示：
//   - 用 H2 内存数据库模拟上述三张表
//   - 正常创建命令：三步全部成功
//   - 模拟读模型投影失败：验证写模型和命令日志都一起回滚
//   - 查询写模型和读模型，验证数据一致性
//
// 关键约定：
//   - 写模型：只追加，不更新（Append-only）
//   - 读模型：可更新，跟随命令推进
//   - 命令日志：审计记录，记录每条命令的执行结果

//> using scala "3.3.3"
//> using dep "org.typelevel::cats-effect:3.5.4"
//> using dep "org.tpolecat::doobie-core:1.0.0-RC4"
//> using dep "org.tpolecat::doobie-h2:1.0.0-RC4"

import cats.effect.{IO, IOApp}
import cats.syntax.all._
import doobie._
import doobie.implicits._
import doobie.h2.H2Transactor

object DoobieTransactionalCommandWriteDemo extends IOApp.Simple:

  // ── 数据库初始化 ────────────────────────────────────────────────────────────

  val initDDL: ConnectionIO[Unit] =
    for
      _ <- sql"""
        CREATE TABLE IF NOT EXISTS order_write_model (
          order_id VARCHAR PRIMARY KEY,
          sku      VARCHAR NOT NULL,
          quantity INT     NOT NULL,
          status   VARCHAR NOT NULL DEFAULT 'active'
        )""".update.run.void

      _ <- sql"""
        CREATE TABLE IF NOT EXISTS order_read_model (
          order_id VARCHAR PRIMARY KEY,
          sku      VARCHAR NOT NULL,
          quantity INT     NOT NULL,
          status   VARCHAR NOT NULL DEFAULT 'active'
        )""".update.run.void

      _ <- sql"""
        CREATE TABLE IF NOT EXISTS command_log (
          id         BIGINT IDENTITY PRIMARY KEY,
          command    VARCHAR NOT NULL,
          order_id   VARCHAR NOT NULL,
          status     VARCHAR NOT NULL,   -- 'success' | 'failed'
          message    VARCHAR
        )""".update.run.void
    yield ()

  // ── 事务命令写入 ────────────────────────────────────────────────────────────

  /** 正常创建：写模型 + 读模型 + 命令日志 在同一事务里 */
  def createOrder(
      orderId: String,
      sku: String,
      quantity: Int,
      simulateProjectionFailure: Boolean = false
  ): ConnectionIO[Unit] =
    for
      // 1. 写入写模型
      _ <- sql"""
        INSERT INTO order_write_model (order_id, sku, quantity)
        VALUES ($orderId, $sku, $quantity)
      """.update.run.void

      // 2. 写入读模型投影（可以在这里模拟失败）
      _ <- (
             if simulateProjectionFailure then
               // NULL 会触发 H2 NOT NULL 约束失败
               sql"INSERT INTO order_read_model (order_id, sku, quantity) VALUES ($orderId, NULL, $quantity)".update.run.void
             else
               sql"""
                 INSERT INTO order_read_model (order_id, sku, quantity)
                 VALUES ($orderId, $sku, $quantity)
               """.update.run.void
           )

      // 3. 记录命令日志
      _ <- sql"""
        INSERT INTO command_log (command, order_id, status, message)
        VALUES ('CreateOrder', $orderId, 'success', ${"qty=" + quantity})
      """.update.run.void
    yield ()

  /** 取消订单：更新写模型状态 + 更新读模型 + 记录命令日志 在同一事务里 */
  def cancelOrder(orderId: String, reason: String): ConnectionIO[Either[String, Unit]] =
    for
      existing <- sql"SELECT status FROM order_write_model WHERE order_id = $orderId"
                    .query[String].option
      result <- existing match
        case None =>
          sql"INSERT INTO command_log (command, order_id, status, message) VALUES ('CancelOrder', $orderId, 'failed', 'not found')".update.run *>
            FC.pure(Left(s"订单 $orderId 不存在"))
        case Some("cancelled") =>
          sql"INSERT INTO command_log (command, order_id, status, message) VALUES ('CancelOrder', $orderId, 'failed', 'already cancelled')".update.run *>
            FC.pure(Left(s"订单 $orderId 已经取消"))
        case _ =>
          for
            _ <- sql"UPDATE order_write_model SET status = 'cancelled' WHERE order_id = $orderId".update.run
            _ <- sql"UPDATE order_read_model  SET status = 'cancelled' WHERE order_id = $orderId".update.run
            _ <- sql"INSERT INTO command_log (command, order_id, status, message) VALUES ('CancelOrder', $orderId, 'success', $reason)".update.run
          yield Right(())
    yield result

  // ── 读模型查询 ──────────────────────────────────────────────────────────────

  case class OrderRM(orderId: String, sku: String, quantity: Int, status: String)

  val queryReadModels: ConnectionIO[List[OrderRM]] =
    sql"SELECT order_id, sku, quantity, status FROM order_read_model ORDER BY order_id"
      .query[OrderRM].to[List]

  val queryCommandLog: ConnectionIO[List[String]] =
    sql"SELECT command || ' | ' || order_id || ' | ' || status || ' | ' || COALESCE(message,'') FROM command_log ORDER BY id"
      .query[String].to[List]

  // ── 演示 ────────────────────────────────────────────────────────────────────

  def run: IO[Unit] =
    H2Transactor
      .newH2Transactor[IO](
        "jdbc:h2:mem:cqrs_write;DB_CLOSE_DELAY=-1",
        "sa",
        "",
        scala.concurrent.ExecutionContext.global
      )
      .use { xa =>

        for
          _ <- initDDL.transact(xa)
          _ <- IO.println("=== Doobie 事务命令写入：写模型 + 读模型 + 命令日志 ===\n")

          // 1. 正常创建两条订单
          _ <- createOrder("o-1", "SKU-A", 2).transact(xa)
          _ <- IO.println("[✓] 创建 o-1 成功（写模型 + 读模型 + 命令日志 同事务）")

          _ <- createOrder("o-2", "SKU-B", 5).transact(xa)
          _ <- IO.println("[✓] 创建 o-2 成功")

          // 2. 模拟读模型投影失败：整个事务应该回滚
          _ <- createOrder("o-3", "SKU-C", 3, simulateProjectionFailure = true)
                  .transact(xa)
                  .handleErrorWith { err =>
                    IO.println(s"[✗] 创建 o-3 失败（模拟投影失败）: ${err.getMessage.take(60)}")
                  }

          // 3. 取消 o-1
          r1 <- cancelOrder("o-1", "客户退款").transact(xa)
          _  <- IO.println(s"[${if r1.isRight then "✓" else "✗"}] 取消 o-1: $r1")

          // 4. 重复取消 → 命令日志记录失败，不报异常
          r2 <- cancelOrder("o-1", "再次取消").transact(xa)
          _  <- IO.println(s"[${if r2.isRight then "✓" else "✗"}] 重复取消 o-1: $r2")

          // 5. 读取读模型和命令日志
          rms  <- queryReadModels.transact(xa)
          logs <- queryCommandLog.transact(xa)

          _ <- IO.println(s"\n── 读模型（order_read_model）─────────────────")
          _ <- rms.traverse_(r => IO.println(s"  ${r.orderId}: ${r.sku} ×${r.quantity} [${r.status}]"))

          _ <- IO.println(s"\n── 命令日志（command_log）────────────────────")
          _ <- logs.traverse_(l => IO.println(s"  $l"))

          _ <- IO.println("""
|关键点：
|  1. 写模型 + 读模型 + 命令日志 必须在同一事务里，三者强一致
|  2. 投影失败会让写模型一起回滚，不会出现"写模型有但读模型没有"的脏状态
|  3. 命令日志是审计链路，即使业务失败也要记录（在失败时也在同一事务里写入）
|  4. 写模型和读模型职责分离：写模型是权威数据，读模型是查询优化视图""".stripMargin)
        yield ()
      }
