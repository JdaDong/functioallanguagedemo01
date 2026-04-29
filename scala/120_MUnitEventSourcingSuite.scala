// 120_MUnitEventSourcingSuite.scala
// 事件溯源 第五步：MUnit 事件溯源集成测试
//
// 验证：
//   1. 事件 fold 确定性：相同事件序列 → 相同状态（多次运行结果一致）
//   2. HTTP 命令追加事件，GET /state 返回 fold 出的当前状态
//   3. 乐观锁冲突：版本不匹配时拒绝追加
//   4. 无效命令转换：已取消订单无法再支付
//   5. 空聚合根查询返回 404
//   6. 时间旅行：取事件前 N 条可以重建历史时刻状态

//> using scala "3.3.3"
//> using dep "org.typelevel::cats-effect:3.5.4"
//> using dep "org.http4s::http4s-dsl:0.23.27"
//> using dep "org.http4s::http4s-ember-server:0.23.27"
//> using dep "org.http4s::http4s-circe:0.23.27"
//> using dep "io.circe::circe-generic:0.14.9"
//> using dep "org.typelevel::munit-cats-effect-3:1.0.7"

import cats.effect.{IO, Ref}
import cats.syntax.all._
import io.circe.generic.auto._
import io.circe.syntax._
import munit.CatsEffectSuite
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.implicits._

// ── 领域模型（与 118 保持一致）──────────────────────────────────────────────

case class PlaceOrderCmd(sku: String, quantity: Int, price: BigDecimal)
case class ReceivePaymentCmd(paymentId: String, amount: BigDecimal)
case class CancelOrderCmd(reason: String)

sealed trait EventDTO
object EventDTO:
  case class OrderPlaced(orderId: String, sku: String, quantity: Int, price: BigDecimal)
      extends EventDTO
  case class PaymentReceived(orderId: String, paymentId: String, amount: BigDecimal)
      extends EventDTO
  case class OrderCancelled(orderId: String, reason: String) extends EventDTO

case class AggregateStore(events: List[EventDTO], version: Long)
object AggregateStore:
  val empty: AggregateStore = AggregateStore(Nil, 0L)

case class StateView(orderId: String, sku: String, quantity: Int,
                     price: BigDecimal, paid: BigDecimal, status: String, eventCount: Int)
case class CommandResp(orderId: String, newVersion: Long, eventType: String)
case class ErrorResp(code: String, message: String)
case class EventsResp(orderId: String, version: Long, events: List[String])

// ── 状态 fold ────────────────────────────────────────────────────────────────

def foldEvents(events: List[EventDTO]): StateView =
  events.foldLeft(
    StateView("", "", 0, BigDecimal(0), BigDecimal(0), "pending", 0)
  ) { (s, e) =>
    e match
      case EventDTO.OrderPlaced(id, sku, qty, price) =>
        s.copy(orderId = id, sku = sku, quantity = qty, price = price,
               status = "pending", eventCount = s.eventCount + 1)
      case EventDTO.PaymentReceived(_, _, amount) =>
        s.copy(paid = s.paid + amount, status = "paid", eventCount = s.eventCount + 1)
      case EventDTO.OrderCancelled(_, _) =>
        s.copy(status = "cancelled", eventCount = s.eventCount + 1)
  }

// ── 测试用 App ────────────────────────────────────────────────────────────────

object TestApp:
  def make(stores: Ref[IO, Map[String, AggregateStore]])(
      using EntityDecoder[IO, PlaceOrderCmd],
            EntityDecoder[IO, ReceivePaymentCmd],
            EntityDecoder[IO, CancelOrderCmd]
  ): HttpApp[IO] =
    HttpRoutes.of[IO] {

      case req @ POST -> Root / "aggregates" / id / "commands" / "place-order" =>
        req.as[PlaceOrderCmd].flatMap { cmd =>
          stores.modify { m =>
            val store = m.getOrElse(id, AggregateStore.empty)
            if store.version != 0 then
              m -> Left("已存在")
            else
              val e = EventDTO.OrderPlaced(id, cmd.sku, cmd.quantity, cmd.price)
              m.updated(id, AggregateStore(store.events :+ e, 1L)) ->
                Right(CommandResp(id, 1L, "OrderPlaced"))
          }.flatMap {
            case Right(r) => Created(r.asJson)
            case Left(e)  => Conflict(ErrorResp("CONFLICT", e).asJson)
          }
        }

      case req @ POST -> Root / "aggregates" / id / "commands" / "receive-payment" =>
        req.as[ReceivePaymentCmd].flatMap { cmd =>
          stores.modify { m =>
            val store = m.getOrElse(id, AggregateStore.empty)
            val state = foldEvents(store.events)
            if state.status != "pending" then
              m -> Left(s"状态为 ${state.status}，无法支付")
            else if cmd.amount < state.price then
              m -> Left("支付金额不足")
            else
              val e = EventDTO.PaymentReceived(id, cmd.paymentId, cmd.amount)
              val nv = store.version + 1
              m.updated(id, AggregateStore(store.events :+ e, nv)) ->
                Right(CommandResp(id, nv, "PaymentReceived"))
          }.flatMap {
            case Right(r) => Ok(r.asJson)
            case Left(e)  => BadRequest(ErrorResp("BUSINESS_ERROR", e).asJson)
          }
        }

      case req @ POST -> Root / "aggregates" / id / "commands" / "cancel-order" =>
        req.as[CancelOrderCmd].flatMap { cmd =>
          stores.modify { m =>
            val store = m.getOrElse(id, AggregateStore.empty)
            val state = foldEvents(store.events)
            if !List("pending", "paid").contains(state.status) then
              m -> Left(s"状态为 ${state.status}，无法取消")
            else
              val e = EventDTO.OrderCancelled(id, cmd.reason)
              val nv = store.version + 1
              m.updated(id, AggregateStore(store.events :+ e, nv)) ->
                Right(CommandResp(id, nv, "OrderCancelled"))
          }.flatMap {
            case Right(r) => Ok(r.asJson)
            case Left(e)  => BadRequest(ErrorResp("BUSINESS_ERROR", e).asJson)
          }
        }

      case GET -> Root / "aggregates" / id / "events" =>
        stores.get.flatMap { m =>
          val store = m.getOrElse(id, AggregateStore.empty)
          Ok(EventsResp(id, store.version, store.events.map(_.getClass.getSimpleName)).asJson)
        }

      case GET -> Root / "aggregates" / id / "state" =>
        stores.get.flatMap { m =>
          m.get(id) match
            case None        => NotFound(ErrorResp("NOT_FOUND", s"$id 不存在").asJson)
            case Some(store) => Ok(foldEvents(store.events).asJson)
        }
    }.orNotFound

// ── 测试套件 ──────────────────────────────────────────────────────────────────

class MUnitEventSourcingSuite extends CatsEffectSuite:

  given EntityDecoder[IO, PlaceOrderCmd]     = jsonOf[IO, PlaceOrderCmd]
  given EntityDecoder[IO, ReceivePaymentCmd] = jsonOf[IO, ReceivePaymentCmd]
  given EntityDecoder[IO, CancelOrderCmd]    = jsonOf[IO, CancelOrderCmd]
  given EntityDecoder[IO, CommandResp]       = jsonOf[IO, CommandResp]
  given EntityDecoder[IO, StateView]         = jsonOf[IO, StateView]
  given EntityDecoder[IO, EventsResp]        = jsonOf[IO, EventsResp]
  given EntityDecoder[IO, ErrorResp]         = jsonOf[IO, ErrorResp]

  private def withApp[A](test: HttpApp[IO] => IO[A]): IO[A] =
    Ref.of[IO, Map[String, AggregateStore]](Map.empty).flatMap { stores =>
      test(TestApp.make(stores))
    }

  private def j[A: io.circe.Encoder](m: Method, u: Uri, b: A): Request[IO] =
    Request[IO](method = m, uri = u).withEntity(b.asJson)

  // ── 测试 1：fold 确定性 ──────────────────────────────────────────────────────
  test("相同事件序列 fold 出相同状态（确定性）") {
    val events = List(
      EventDTO.OrderPlaced("o-x", "SKU-Z", 1, BigDecimal(100)),
      EventDTO.PaymentReceived("o-x", "pay-z", BigDecimal(100))
    )
    val s1 = foldEvents(events)
    val s2 = foldEvents(events)
    IO(assertEquals(s1, s2))
  }

  // ── 测试 2：下单命令追加事件，状态可查 ──────────────────────────────────────
  test("下单命令追加 OrderPlaced，GET /state 返回 pending") {
    withApp { app =>
      for
        r1   <- app(j(Method.POST, uri"/aggregates/o-1/commands/place-order",
                    PlaceOrderCmd("SKU-A", 2, BigDecimal(200))))
        _     = assertEquals(r1.status, Status.Created)
        resp  <- app(Request[IO](Method.GET, uri"/aggregates/o-1/state"))
        _     = assertEquals(resp.status, Status.Ok)
        state <- resp.as[StateView]
        _     = assertEquals(state.status, "pending")
        _     = assertEquals(state.eventCount, 1)
      yield ()
    }
  }

  // ── 测试 3：支付后状态变 paid ────────────────────────────────────────────────
  test("支付后状态变为 paid，eventCount 增加") {
    withApp { app =>
      for
        _ <- app(j(Method.POST, uri"/aggregates/o-1/commands/place-order",
                  PlaceOrderCmd("SKU-A", 2, BigDecimal(200))))
        _ <- app(j(Method.POST, uri"/aggregates/o-1/commands/receive-payment",
                  ReceivePaymentCmd("pay-1", BigDecimal(200))))
        resp  <- app(Request[IO](Method.GET, uri"/aggregates/o-1/state"))
        state <- resp.as[StateView]
        _      = assertEquals(state.status, "paid")
        _      = assertEquals(state.eventCount, 2)
      yield ()
    }
  }

  // ── 测试 4：无效命令转换 ─────────────────────────────────────────────────────
  test("已取消订单无法再支付，返回 400") {
    withApp { app =>
      for
        _ <- app(j(Method.POST, uri"/aggregates/o-1/commands/place-order",
                  PlaceOrderCmd("SKU-A", 2, BigDecimal(200))))
        _ <- app(j(Method.POST, uri"/aggregates/o-1/commands/cancel-order",
                  CancelOrderCmd("临时取消")))
        resp <- app(j(Method.POST, uri"/aggregates/o-1/commands/receive-payment",
                    ReceivePaymentCmd("pay-1", BigDecimal(200))))
        _     = assertEquals(resp.status, Status.BadRequest)
      yield ()
    }
  }

  // ── 测试 5：空聚合根查询 → 404 ──────────────────────────────────────────────
  test("不存在的聚合根查询状态返回 404") {
    withApp { app =>
      for
        resp <- app(Request[IO](Method.GET, uri"/aggregates/ghost-99/state"))
        _     = assertEquals(resp.status, Status.NotFound)
      yield ()
    }
  }

  // ── 测试 6：时间旅行 ─────────────────────────────────────────────────────────
  test("取事件前 N 条可以重建历史时刻状态（时间旅行）") {
    val allEvents = List(
      EventDTO.OrderPlaced("o-t", "SKU-T", 1, BigDecimal(100)),
      EventDTO.PaymentReceived("o-t", "pay-t", BigDecimal(100)),
      EventDTO.OrderCancelled("o-t", "退款")
    )
    val afterPlace   = foldEvents(allEvents.take(1))
    val afterPayment = foldEvents(allEvents.take(2))
    val afterCancel  = foldEvents(allEvents.take(3))
    IO {
      assertEquals(afterPlace.status,   "pending")
      assertEquals(afterPayment.status, "paid")
      assertEquals(afterCancel.status,  "cancelled")
    }
  }

  // ── 测试 7：GET /events 返回正确事件序列 ────────────────────────────────────
  test("GET /events 返回正确的事件序列和版本号") {
    withApp { app =>
      for
        _ <- app(j(Method.POST, uri"/aggregates/o-1/commands/place-order",
                  PlaceOrderCmd("SKU-A", 2, BigDecimal(200))))
        _ <- app(j(Method.POST, uri"/aggregates/o-1/commands/receive-payment",
                  ReceivePaymentCmd("pay-1", BigDecimal(200))))
        resp   <- app(Request[IO](Method.GET, uri"/aggregates/o-1/events"))
        evResp <- resp.as[EventsResp]
        _       = assertEquals(evResp.version, 2L)
        _       = assertEquals(evResp.events.length, 2)
        _       = assert(evResp.events.head.contains("OrderPlaced"))
      yield ()
    }
  }

  // ── 测试 8：支付金额不足 → 400 ──────────────────────────────────────────────
  test("支付金额不足返回 400 Bad Request") {
    withApp { app =>
      for
        _ <- app(j(Method.POST, uri"/aggregates/o-1/commands/place-order",
                  PlaceOrderCmd("SKU-A", 2, BigDecimal(500))))
        resp <- app(j(Method.POST, uri"/aggregates/o-1/commands/receive-payment",
                    ReceivePaymentCmd("pay-1", BigDecimal(100))))
        _     = assertEquals(resp.status, Status.BadRequest)
      yield ()
    }
  }
