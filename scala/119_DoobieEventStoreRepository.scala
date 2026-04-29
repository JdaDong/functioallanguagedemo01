// 119_DoobieEventStoreRepository.scala
// 事件溯源 第四步：Doobie 事件存储仓库
//
// 核心思想：
//   真实 Event Store 数据库设计：
//     - events 表：(id, aggregate_id, version, event_type, payload, created_at)
//     - version 字段：用于乐观锁检测并发冲突
//     - UNIQUE(aggregate_id, version)：数据库级别防止版本重复
//
//   本 Demo 演示：
//     1. 事务性追加：在事务内检查当前版本，再插入新事件
//     2. 乐观锁：如果版本不匹配，整个事务回滚（由 UNIQUE 约束保证）
//     3. 加载事件序列并重建聚合根状态
//     4. 模拟并发冲突：两个写操作争同一版本

//> using scala "3.3.3"
//> using dep "org.typelevel::cats-effect:3.5.4"
//> using dep "org.tpolecat::doobie-core:1.0.0-RC4"
//> using dep "org.tpolecat::doobie-h2:1.0.0-RC4"
//> using dep "io.circe::circe-core:0.14.9"
//> using dep "io.circe::circe-generic:0.14.9"
//> using dep "io.circe::circe-parser:0.14.9"

import cats.effect.{IO, IOApp}
import cats.syntax.all._
import doobie._
import doobie.implicits._
import doobie.h2.H2Transactor
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser.decode

// ── 事件序列化（JSON payload）────────────────────────────────────────────────

sealed trait OrderEvent
object OrderEvent:
  case class OrderPlaced(orderId: String, sku: String, quantity: Int, price: Double)
      extends OrderEvent
  case class PaymentReceived(orderId: String, paymentId: String, amount: Double)
      extends OrderEvent
  case class OrderCancelled(orderId: String, reason: String) extends OrderEvent

def serializeEvent(event: OrderEvent): (String, String) =
  event match
    case e: OrderEvent.OrderPlaced     => ("OrderPlaced",     e.asJson.noSpaces)
    case e: OrderEvent.PaymentReceived => ("PaymentReceived", e.asJson.noSpaces)
    case e: OrderEvent.OrderCancelled  => ("OrderCancelled",  e.asJson.noSpaces)

def deserializeEvent(eventType: String, payload: String): Option[OrderEvent] =
  eventType match
    case "OrderPlaced"     => decode[OrderEvent.OrderPlaced](payload).toOption
    case "PaymentReceived" => decode[OrderEvent.PaymentReceived](payload).toOption
    case "OrderCancelled"  => decode[OrderEvent.OrderCancelled](payload).toOption
    case _                 => None

// ── 聚合根当前状态 ────────────────────────────────────────────────────────────

case class OrderState(
    orderId:  String,
    sku:      String,
    quantity: Int,
    price:    Double,
    paid:     Double,
    status:   String    // pending / paid / cancelled
)
object OrderState:
  val empty: OrderState = OrderState("", "", 0, 0.0, 0.0, "pending")

  def applyEvent(state: OrderState, event: OrderEvent): OrderState =
    event match
      case OrderEvent.OrderPlaced(id, sku, qty, price) =>
        state.copy(orderId = id, sku = sku, quantity = qty, price = price, status = "pending")
      case OrderEvent.PaymentReceived(_, _, amount) =>
        state.copy(paid = state.paid + amount, status = "paid")
      case OrderEvent.OrderCancelled(_, _) =>
        state.copy(status = "cancelled")

  def fromEvents(events: List[OrderEvent]): OrderState =
    events.foldLeft(empty)(applyEvent)

// ── Doobie Event Store 仓库 ───────────────────────────────────────────────────

object EventStoreRepo:

  val initDDL: ConnectionIO[Unit] =
    sql"""
      CREATE TABLE IF NOT EXISTS aggregate_events (
        id           BIGINT IDENTITY PRIMARY KEY,
        aggregate_id VARCHAR NOT NULL,
        version      BIGINT  NOT NULL,
        event_type   VARCHAR NOT NULL,
        payload      VARCHAR NOT NULL,
        CONSTRAINT uq_aggregate_version UNIQUE (aggregate_id, version)
      )
    """.update.run.void

  /** 获取聚合根当前版本（最大 version 号） */
  def currentVersion(aggregateId: String): ConnectionIO[Long] =
    sql"SELECT COALESCE(MAX(version), -1) FROM aggregate_events WHERE aggregate_id = $aggregateId"
      .query[Long].unique

  /** 事务性追加：检查期望版本，再插入新事件
    * 如果期望版本与当前版本不符，抛出异常让事务回滚
    */
  def appendEvent(
      aggregateId:     String,
      expectedVersion: Long,
      event:           OrderEvent
  ): ConnectionIO[Long] =
    for
      currentVer <- currentVersion(aggregateId)
      _ <- (
             if currentVer != expectedVersion then
               FC.raiseError(new RuntimeException(
                 s"版本冲突: $aggregateId 期望 v$expectedVersion 但当前 v$currentVer"
               ))
             else
               FC.unit
           )
      (eventType, payload) = serializeEvent(event)
      newVersion = expectedVersion + 1
      _ <- sql"""
             INSERT INTO aggregate_events (aggregate_id, version, event_type, payload)
             VALUES ($aggregateId, $newVersion, $eventType, $payload)
           """.update.run
    yield newVersion

  /** 加载聚合根完整事件序列 */
  def loadEvents(aggregateId: String): ConnectionIO[List[OrderEvent]] =
    sql"""
      SELECT event_type, payload FROM aggregate_events
      WHERE aggregate_id = $aggregateId
      ORDER BY version ASC
    """.query[(String, String)]
       .to[List]
       .map(_.flatMap { case (t, p) => deserializeEvent(t, p) })

  /** 直接重建当前状态 */
  def rehydrate(aggregateId: String): ConnectionIO[OrderState] =
    loadEvents(aggregateId).map(OrderState.fromEvents)

// ── 演示 ──────────────────────────────────────────────────────────────────────

object DoobieEventStoreRepositoryDemo extends IOApp.Simple:

  def run: IO[Unit] =
    H2Transactor
      .newH2Transactor[IO](
        "jdbc:h2:mem:eventsource;DB_CLOSE_DELAY=-1",
        "sa",
        "",
        scala.concurrent.ExecutionContext.global
      )
      .use { xa =>
        for
          _ <- EventStoreRepo.initDDL.transact(xa)
          _ <- IO.println("=== Doobie 事件存储仓库：乐观锁 + 事务追加 + 聚合根重建 ===\n")

          // 1. 追加 OrderPlaced（v0 → v1）
          v1 <- EventStoreRepo.appendEvent(
                  "o-1", -1L,
                  OrderEvent.OrderPlaced("o-1", "SKU-A", 2, 300.0)
                ).transact(xa)
          _  <- IO.println(s"[✓] OrderPlaced 追加成功，新版本: v$v1")

          // 2. 追加 PaymentReceived（v0 → v1）
          v2 <- EventStoreRepo.appendEvent(
                  "o-1", 0L,
                  OrderEvent.PaymentReceived("o-1", "pay-001", 300.0)
                ).transact(xa)
          _  <- IO.println(s"[✓] PaymentReceived 追加成功，新版本: v$v2")

          // 3. 模拟版本冲突（使用旧版本 v0 再次追加，而当前已是 v1）
          _ <- EventStoreRepo.appendEvent(
                 "o-1", 0L,
                 OrderEvent.OrderCancelled("o-1", "版本冲突测试")
               ).transact(xa).handleErrorWith { err =>
                 IO.println(s"[✗] 版本冲突: ${err.getMessage}")
               }

          // 4. 正确追加 OrderCancelled（v1 → v2）
          v3 <- EventStoreRepo.appendEvent(
                  "o-1", 1L,
                  OrderEvent.OrderCancelled("o-1", "客户退款")
                ).transact(xa)
          _  <- IO.println(s"[✓] OrderCancelled 追加成功，新版本: v$v3")

          // 5. 重建聚合根状态
          state  <- EventStoreRepo.rehydrate("o-1").transact(xa)
          events <- EventStoreRepo.loadEvents("o-1").transact(xa)

          _ <- IO.println(s"\n── 聚合根 o-1 最终状态 ─────────────────────")
          _ <- IO.println(s"  orderId=${state.orderId}, sku=${state.sku}, status=${state.status}, paid=${state.paid}")

          _ <- IO.println(s"\n── 完整事件日志（共 ${events.length} 条）─────────────────")
          _ <- events.zipWithIndex.traverse_ { case (e, i) =>
                 IO.println(s"  [v${i+1}] $e")
               }

          // 6. 验证确定性：两次重建得到相同结果
          state2 <- EventStoreRepo.rehydrate("o-1").transact(xa)
          _       = assert(state == state2, "重建结果必须相同")
          _      <- IO.println("\n[✓] 确定性验证通过：两次重建结果一致")

          _ <- IO.println("""
|关键点：
|  1. UNIQUE(aggregate_id, version) 由数据库保证乐观锁，防止并发写入同一版本
|  2. 追加前先查当前版本，不匹配则在事务内抛出异常，整体回滚
|  3. 事件 JSON 序列化存储，支持后续扩展字段而不破坏历史记录
|  4. rehydrate = loadEvents + foldLeft，纯粹的状态重建，无副作用""".stripMargin)
        yield ()
      }
