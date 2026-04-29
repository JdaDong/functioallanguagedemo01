// 118_Http4sEventStoreEndpoint.scala
// 事件溯源 第三步：http4s Event Store 边界
//
// 核心思想：
//   Event Store 对外暴露三个 HTTP 接口：
//     POST /aggregates/{id}/commands  → 接受命令，追加事件（带版本乐观锁）
//     GET  /aggregates/{id}/events    → 返回完整事件序列（可加 ?from=N 增量拉取）
//     GET  /aggregates/{id}/state     → 返回当前聚合根状态（从事件 fold 出来）
//
//   这和传统 CRUD API 的区别：
//     - CRUD：GET /orders/{id} 返回当前状态；PUT /orders/{id} 直接更新状态
//     - EventStore：GET events 返回历史；POST command 追加事件；state 是派生出来的视图

//> using scala "3.3.3"
//> using dep "org.typelevel::cats-effect:3.5.4"
//> using dep "org.http4s::http4s-dsl:0.23.27"
//> using dep "org.http4s::http4s-ember-server:0.23.27"
//> using dep "org.http4s::http4s-circe:0.23.27"
//> using dep "io.circe::circe-generic:0.14.9"

import cats.effect.{IO, IOApp, Ref}
import cats.syntax.all._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.implicits._

// ── 领域模型 ──────────────────────────────────────────────────────────────────

case class PlaceOrderCmd(sku: String, quantity: Int, price: BigDecimal)
case class ReceivePaymentCmd(paymentId: String, amount: BigDecimal)
case class CancelOrderCmd(reason: String)

// 事件（可以 JSON 序列化存储）
sealed trait OrderEventDTO
object OrderEventDTO:
  case class OrderPlaced(orderId: String, sku: String, quantity: Int, price: BigDecimal)
      extends OrderEventDTO
  case class PaymentReceived(orderId: String, paymentId: String, amount: BigDecimal)
      extends OrderEventDTO
  case class OrderCancelled(orderId: String, reason: String) extends OrderEventDTO

enum OrderStatus:
  case Pending, Paid, Cancelled

case class OrderStateView(
    orderId:    String,
    sku:        String,
    quantity:   Int,
    price:      BigDecimal,
    paid:       BigDecimal,
    status:     String,
    eventCount: Int
)

case class VersionedStore(
    events:  List[OrderEventDTO],
    version: Long
)
object VersionedStore:
  val empty: VersionedStore = VersionedStore(Nil, 0L)

// ── 事件 fold → 当前状态 ─────────────────────────────────────────────────────

def foldState(events: List[OrderEventDTO]): OrderStateView =
  events.foldLeft(
    OrderStateView("", "", 0, BigDecimal(0), BigDecimal(0), "pending", 0)
  ) { (state, event) =>
    event match
      case OrderEventDTO.OrderPlaced(id, sku, qty, price) =>
        state.copy(orderId = id, sku = sku, quantity = qty, price = price,
                   status = "pending", eventCount = state.eventCount + 1)
      case OrderEventDTO.PaymentReceived(_, _, amount) =>
        state.copy(paid = state.paid + amount, status = "paid",
                   eventCount = state.eventCount + 1)
      case OrderEventDTO.OrderCancelled(_, _) =>
        state.copy(status = "cancelled", eventCount = state.eventCount + 1)
  }

// ── HTTP 路由 ─────────────────────────────────────────────────────────────────

case class CommandResponse(orderId: String, newVersion: Long, eventType: String)
case class ErrorResponse(code: String, message: String)
case class EventsResponse(orderId: String, version: Long, events: List[String])

def eventStoreRoutes(
    stores: Ref[IO, Map[String, VersionedStore]]
)(using
    EntityDecoder[IO, PlaceOrderCmd],
    EntityDecoder[IO, ReceivePaymentCmd],
    EntityDecoder[IO, CancelOrderCmd]
): HttpRoutes[IO] =
  HttpRoutes.of[IO] {

    // 追加命令 → 事件
    case req @ POST -> Root / "aggregates" / orderId / "commands" / cmdType =>
      cmdType match
        case "place-order" =>
          req.as[PlaceOrderCmd].flatMap { cmd =>
            stores.modify { m =>
              val store = m.getOrElse(orderId, VersionedStore.empty)
              if store.version != 0 then
                m -> Left(s"$orderId 已存在")
              else
                val event = OrderEventDTO.OrderPlaced(orderId, cmd.sku, cmd.quantity, cmd.price)
                m.updated(orderId, VersionedStore(store.events :+ event, store.version + 1)) ->
                  Right(CommandResponse(orderId, store.version + 1, "OrderPlaced"))
            }.flatMap {
              case Right(r) => Created(r.asJson)
              case Left(e)  => Conflict(ErrorResponse("CONFLICT", e).asJson)
            }
          }

        case "receive-payment" =>
          req.as[ReceivePaymentCmd].flatMap { cmd =>
            stores.modify { m =>
              val store = m.getOrElse(orderId, VersionedStore.empty)
              val state = foldState(store.events)
              if state.status != "pending" then
                m -> Left(s"$orderId 状态为 ${state.status}，无法支付")
              else if cmd.amount < state.price then
                m -> Left(s"支付金额不足: 需要 ${state.price}，实际 ${cmd.amount}")
              else
                val event = OrderEventDTO.PaymentReceived(orderId, cmd.paymentId, cmd.amount)
                m.updated(orderId, VersionedStore(store.events :+ event, store.version + 1)) ->
                  Right(CommandResponse(orderId, store.version + 1, "PaymentReceived"))
            }.flatMap {
              case Right(r) => Ok(r.asJson)
              case Left(e)  => BadRequest(ErrorResponse("BUSINESS_ERROR", e).asJson)
            }
          }

        case "cancel-order" =>
          req.as[CancelOrderCmd].flatMap { cmd =>
            stores.modify { m =>
              val store = m.getOrElse(orderId, VersionedStore.empty)
              val state = foldState(store.events)
              if !List("pending", "paid").contains(state.status) then
                m -> Left(s"$orderId 状态为 ${state.status}，无法取消")
              else
                val event = OrderEventDTO.OrderCancelled(orderId, cmd.reason)
                m.updated(orderId, VersionedStore(store.events :+ event, store.version + 1)) ->
                  Right(CommandResponse(orderId, store.version + 1, "OrderCancelled"))
            }.flatMap {
              case Right(r) => Ok(r.asJson)
              case Left(e)  => BadRequest(ErrorResponse("BUSINESS_ERROR", e).asJson)
            }
          }

        case other =>
          BadRequest(ErrorResponse("UNKNOWN_CMD", s"未知命令: $other").asJson)

    // 加载事件序列
    case GET -> Root / "aggregates" / orderId / "events" =>
      stores.get.flatMap { m =>
        val store = m.getOrElse(orderId, VersionedStore.empty)
        Ok(EventsResponse(
          orderId, store.version,
          store.events.map(_.getClass.getSimpleName)
        ).asJson)
      }

    // 查询当前状态（从事件 fold）
    case GET -> Root / "aggregates" / orderId / "state" =>
      stores.get.flatMap { m =>
        val store = m.getOrElse(orderId, VersionedStore.empty)
        if store.version == 0 then
          NotFound(ErrorResponse("NOT_FOUND", s"$orderId 不存在").asJson)
        else
          Ok(foldState(store.events).asJson)
      }
  }

// ── 演示 ──────────────────────────────────────────────────────────────────────

object Http4sEventStoreEndpointDemo extends IOApp.Simple:

  given EntityDecoder[IO, PlaceOrderCmd]      = jsonOf[IO, PlaceOrderCmd]
  given EntityDecoder[IO, ReceivePaymentCmd]  = jsonOf[IO, ReceivePaymentCmd]
  given EntityDecoder[IO, CancelOrderCmd]     = jsonOf[IO, CancelOrderCmd]

  private def j[A: io.circe.Encoder](method: Method, uri: Uri, body: A): Request[IO] =
    Request[IO](method = method, uri = uri).withEntity(body.asJson)

  def run: IO[Unit] =
    for
      stores <- Ref.of[IO, Map[String, VersionedStore]](Map.empty)
      app     = eventStoreRoutes(stores).orNotFound

      _ <- IO.println("=== http4s Event Store：追加命令/加载事件/查询状态 ===\n")

      // 下单
      r1 <- app(j(Method.POST, uri"/aggregates/o-1/commands/place-order",
                   PlaceOrderCmd("SKU-A", 2, BigDecimal(300))))
      b1 <- r1.as[String]
      _  <- IO.println(s"[POST place-order] ${r1.status.code}: $b1")

      // 查询事件序列（1 个事件）
      r2 <- app(Request[IO](Method.GET, uri"/aggregates/o-1/events"))
      b2 <- r2.as[String]
      _  <- IO.println(s"[GET events] ${r2.status.code}: $b2")

      // 查询当前状态（从事件 fold）
      r3 <- app(Request[IO](Method.GET, uri"/aggregates/o-1/state"))
      b3 <- r3.as[String]
      _  <- IO.println(s"[GET state] ${r3.status.code}: $b3")

      // 支付（金额不足 → 失败）
      r4 <- app(j(Method.POST, uri"/aggregates/o-1/commands/receive-payment",
                   ReceivePaymentCmd("pay-x", BigDecimal(100))))
      b4 <- r4.as[String]
      _  <- IO.println(s"[POST receive-payment 不足] ${r4.status.code}: $b4")

      // 支付（正确金额）
      r5 <- app(j(Method.POST, uri"/aggregates/o-1/commands/receive-payment",
                   ReceivePaymentCmd("pay-001", BigDecimal(300))))
      b5 <- r5.as[String]
      _  <- IO.println(s"[POST receive-payment 正确] ${r5.status.code}: $b5")

      // 查询状态（应为 paid）
      r6 <- app(Request[IO](Method.GET, uri"/aggregates/o-1/state"))
      b6 <- r6.as[String]
      _  <- IO.println(s"[GET state after payment] ${r6.status.code}: $b6")

      // 取消已付款订单
      r7 <- app(j(Method.POST, uri"/aggregates/o-1/commands/cancel-order",
                   CancelOrderCmd("客户要求退款")))
      b7 <- r7.as[String]
      _  <- IO.println(s"[POST cancel-order] ${r7.status.code}: $b7")

      // 查询最终事件序列（3 个事件）
      r8 <- app(Request[IO](Method.GET, uri"/aggregates/o-1/events"))
      b8 <- r8.as[String]
      _  <- IO.println(s"[GET events final] ${r8.status.code}: $b8")

      _ <- IO.println("""
|关键点：
|  1. POST /aggregates/{id}/commands/{type} → 追加事件，返回新版本号
|  2. GET  /aggregates/{id}/events → 返回完整事件序列，可用于读模型重建
|  3. GET  /aggregates/{id}/state  → 从事件 fold 出当前状态，是派生视图
|  4. 状态始终从事件推导，不是直接存储的字段""".stripMargin)
    yield ()
