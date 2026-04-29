// 125_MUnitProcessManagerSuite.scala
// 进程管理器 第五步：MUnit 进程管理器集成测试
//
// 验证：
//   1. 正常履约流程：订单确认 → 支付 → 库存 → 发货 → 送达
//   2. 每步触发正确的下游命令
//   3. 幂等：相同 eventId 不重复推进状态
//   4. 补偿路径：库存不足 → IssueRefund + NotifyCustomer
//   5. 支付失败：直接取消，无库存命令
//   6. 多进程并行：不同 orderId 互不干扰
//   7. 进程状态查询（只读，不产生副作用）

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

// ── DTO（与 123 保持一致）────────────────────────────────────────────────────

case class SubmitEventReq(
    eventId:    String,
    eventType:  String,
    sku:        Option[String]      = None,
    quantity:   Option[Int]         = None,
    paymentId:  Option[String]      = None,
    warehouseId: Option[String]     = None,
    reason:     Option[String]      = None
)

case class ProcessStateView(
    orderId: String, status: String, sku: String,
    quantity: Int, warehouseId: Option[String], eventCount: Int
)
case class CommandView(orderId: String, commandType: String, detail: String)
case class EventAccepted(processId: String, eventType: String, commandsTriggered: Int)
case class ErrorResp(code: String, message: String)

// ── 进程注册表 ────────────────────────────────────────────────────────────────

case class PEntry(
    orderId: String, sku: String, quantity: Int,
    warehouseId: Option[String], status: String,
    eventIds: Set[String], eventCount: Int,
    commands: List[CommandView]
)

object PEntry:
  def init(orderId: String): PEntry =
    PEntry(orderId, "", 0, None, "awaiting-payment", Set.empty, 0, Nil)

// ── 测试用 App ────────────────────────────────────────────────────────────────

object TestPMApp:
  def make(reg: Ref[IO, Map[String, PEntry]])(
      using EntityDecoder[IO, SubmitEventReq]
  ): HttpApp[IO] =
    HttpRoutes.of[IO] {

      case req @ POST -> Root / "processes" / orderId / "events" =>
        req.as[SubmitEventReq].flatMap { body =>
          reg.modify { m =>
            val e = m.getOrElse(orderId, PEntry.init(orderId))
            if e.eventIds.contains(body.eventId) then
              m -> Left("DUPLICATE")
            else
              val (ns, nsk, nqty, nwh, ncmds) = body.eventType match
                case "OrderConfirmed" =>
                  ("awaiting-payment", body.sku.getOrElse(""), body.quantity.getOrElse(0),
                   e.warehouseId, Nil)
                case "PaymentCompleted" =>
                  ("awaiting-inventory", e.sku, e.quantity, e.warehouseId,
                   List(CommandView(orderId, "RequestInventoryReservation", s"sku=${e.sku}")))
                case "InventoryReserved" =>
                  val wh = body.warehouseId.getOrElse("")
                  ("awaiting-shipment", e.sku, e.quantity, Some(wh),
                   List(CommandView(orderId, "RequestShipment", wh)))
                case "ShipmentDelivered" =>
                  ("delivered", e.sku, e.quantity, e.warehouseId,
                   List(CommandView(orderId, "NotifyCustomer", "已送达")))
                case "PaymentFailed" =>
                  val r = body.reason.getOrElse("")
                  ("cancelled", e.sku, e.quantity, e.warehouseId,
                   List(CommandView(orderId, "NotifyCustomer", s"支付失败:$r")))
                case "InventoryUnavailable" =>
                  val r = body.reason.getOrElse("")
                  ("compensating", e.sku, e.quantity, e.warehouseId,
                   List(CommandView(orderId, "IssueRefund", r),
                        CommandView(orderId, "NotifyCustomer", s"退款:$r")))
                case _ =>
                  (e.status, e.sku, e.quantity, e.warehouseId, Nil)
              val updated = e.copy(
                sku = nsk, quantity = nqty, warehouseId = nwh, status = ns,
                eventIds = e.eventIds + body.eventId,
                eventCount = e.eventCount + 1,
                commands = e.commands ++ ncmds
              )
              m.updated(orderId, updated) -> Right(ncmds.length)
          }.flatMap {
            case Right(n)        => Accepted(EventAccepted(orderId, body.eventType, n).asJson)
            case Left("DUPLICATE") => Ok(EventAccepted(orderId, body.eventType, 0).asJson)
            case Left(e)         => BadRequest(ErrorResp("ERROR", e).asJson)
          }
        }

      case GET -> Root / "processes" / orderId / "state" =>
        reg.get.flatMap { m =>
          m.get(orderId) match
            case None    => NotFound(ErrorResp("NOT_FOUND", orderId).asJson)
            case Some(e) => Ok(ProcessStateView(
                              e.orderId, e.status, e.sku, e.quantity,
                              e.warehouseId, e.eventCount).asJson)
        }

      case GET -> Root / "processes" / "commands" / "pending" =>
        reg.get.flatMap { m =>
          Ok(m.values.flatMap(_.commands).toList.asJson)
        }

    }.orNotFound

// ── 测试套件 ──────────────────────────────────────────────────────────────────

class MUnitProcessManagerSuite extends CatsEffectSuite:

  given EntityDecoder[IO, SubmitEventReq]       = jsonOf[IO, SubmitEventReq]
  given EntityDecoder[IO, EventAccepted]        = jsonOf[IO, EventAccepted]
  given EntityDecoder[IO, ProcessStateView]     = jsonOf[IO, ProcessStateView]
  given EntityDecoder[IO, List[CommandView]]    = jsonOf[IO, List[CommandView]]
  given EntityDecoder[IO, ErrorResp]            = jsonOf[IO, ErrorResp]

  private def withApp[A](test: HttpApp[IO] => IO[A]): IO[A] =
    Ref.of[IO, Map[String, PEntry]](Map.empty).flatMap { reg =>
      test(TestPMApp.make(reg))
    }

  private def j[A: io.circe.Encoder](m: Method, u: Uri, b: A): Request[IO] =
    Request[IO](method = m, uri = u).withEntity(b.asJson)

  // ── 测试 1：正常流程：支付完成触发库存预留命令 ─────────────────────────────
  test("支付完成事件触发 RequestInventoryReservation 命令") {
    withApp { app =>
      for
        _ <- app(j(Method.POST, uri"/processes/o-1/events",
                   SubmitEventReq("e1", "OrderConfirmed", sku = Some("SKU-A"), quantity = Some(2))))
        r <- app(j(Method.POST, uri"/processes/o-1/events",
                   SubmitEventReq("e2", "PaymentCompleted")))
        resp <- r.as[EventAccepted]
        _     = assertEquals(resp.commandsTriggered, 1)
        _     = assertEquals(resp.eventType, "PaymentCompleted")
      yield ()
    }
  }

  // ── 测试 2：幂等：相同 eventId 不重复推进 ───────────────────────────────────
  test("相同 eventId 提交两次，状态只推进一次") {
    withApp { app =>
      for
        _ <- app(j(Method.POST, uri"/processes/o-1/events",
                   SubmitEventReq("e1", "OrderConfirmed", sku = Some("SKU-A"), quantity = Some(2))))
        _ <- app(j(Method.POST, uri"/processes/o-1/events",
                   SubmitEventReq("e2", "PaymentCompleted")))
        // 重复提交 e2
        r2 <- app(j(Method.POST, uri"/processes/o-1/events",
                    SubmitEventReq("e2", "PaymentCompleted")))
        dup <- r2.as[EventAccepted]
        _    = assertEquals(dup.commandsTriggered, 0)  // 幂等，不触发新命令
        // 状态 eventCount 仍为 2
        rs  <- app(Request[IO](Method.GET, uri"/processes/o-1/state"))
        sv  <- rs.as[ProcessStateView]
        _    = assertEquals(sv.eventCount, 2)
      yield ()
    }
  }

  // ── 测试 3：补偿路径：库存不足触发退款 + 通知 ─────────────────────────────
  test("库存不足触发 IssueRefund 和 NotifyCustomer 两条命令") {
    withApp { app =>
      for
        _ <- app(j(Method.POST, uri"/processes/o-1/events",
                   SubmitEventReq("e1", "OrderConfirmed", sku = Some("RARE"), quantity = Some(100))))
        _ <- app(j(Method.POST, uri"/processes/o-1/events",
                   SubmitEventReq("e2", "PaymentCompleted")))
        r <- app(j(Method.POST, uri"/processes/o-1/events",
                   SubmitEventReq("e3", "InventoryUnavailable", reason = Some("全部售罄"))))
        resp <- r.as[EventAccepted]
        _     = assertEquals(resp.commandsTriggered, 2)
        // 状态为 compensating
        rs  <- app(Request[IO](Method.GET, uri"/processes/o-1/state"))
        sv  <- rs.as[ProcessStateView]
        _    = assertEquals(sv.status, "compensating")
      yield ()
    }
  }

  // ── 测试 4：支付失败直接取消 ─────────────────────────────────────────────────
  test("支付失败后状态变为 cancelled") {
    withApp { app =>
      for
        _ <- app(j(Method.POST, uri"/processes/o-1/events",
                   SubmitEventReq("e1", "OrderConfirmed", sku = Some("SKU"), quantity = Some(1))))
        _ <- app(j(Method.POST, uri"/processes/o-1/events",
                   SubmitEventReq("e2", "PaymentFailed", reason = Some("余额不足"))))
        rs  <- app(Request[IO](Method.GET, uri"/processes/o-1/state"))
        sv  <- rs.as[ProcessStateView]
        _    = assertEquals(sv.status, "cancelled")
      yield ()
    }
  }

  // ── 测试 5：完整履约流程 → 最终状态 delivered ─────────────────────────────
  test("完整履约路径最终状态为 delivered") {
    withApp { app =>
      for
        _ <- app(j(Method.POST, uri"/processes/o-1/events",
                   SubmitEventReq("e1", "OrderConfirmed", sku = Some("SKU"), quantity = Some(1))))
        _ <- app(j(Method.POST, uri"/processes/o-1/events",
                   SubmitEventReq("e2", "PaymentCompleted")))
        _ <- app(j(Method.POST, uri"/processes/o-1/events",
                   SubmitEventReq("e3", "InventoryReserved", warehouseId = Some("WH-BJ"))))
        _ <- app(j(Method.POST, uri"/processes/o-1/events",
                   SubmitEventReq("e4", "ShipmentDelivered")))
        rs  <- app(Request[IO](Method.GET, uri"/processes/o-1/state"))
        sv  <- rs.as[ProcessStateView]
        _    = assertEquals(sv.status, "delivered")
        _    = assertEquals(sv.eventCount, 4)
      yield ()
    }
  }

  // ── 测试 6：多进程互不干扰 ──────────────────────────────────────────────────
  test("不同 orderId 的进程互不干扰") {
    withApp { app =>
      for
        _ <- app(j(Method.POST, uri"/processes/o-1/events",
                   SubmitEventReq("e1", "OrderConfirmed", sku = Some("A"), quantity = Some(1))))
        _ <- app(j(Method.POST, uri"/processes/o-2/events",
                   SubmitEventReq("e2", "OrderConfirmed", sku = Some("B"), quantity = Some(2))))
        _ <- app(j(Method.POST, uri"/processes/o-1/events",
                   SubmitEventReq("e3", "PaymentCompleted")))
        s1 <- app(Request[IO](Method.GET, uri"/processes/o-1/state")).flatMap(_.as[ProcessStateView])
        s2 <- app(Request[IO](Method.GET, uri"/processes/o-2/state")).flatMap(_.as[ProcessStateView])
        _   = assertEquals(s1.status, "awaiting-inventory")
        _   = assertEquals(s2.status, "awaiting-payment")
      yield ()
    }
  }

  // ── 测试 7：待执行命令累积正确 ──────────────────────────────────────────────
  test("两个进程的命令都进入全局命令队列") {
    withApp { app =>
      for
        _ <- app(j(Method.POST, uri"/processes/o-1/events",
                   SubmitEventReq("e1", "OrderConfirmed", sku = Some("A"), quantity = Some(1))))
        _ <- app(j(Method.POST, uri"/processes/o-1/events",
                   SubmitEventReq("e2", "PaymentCompleted")))
        _ <- app(j(Method.POST, uri"/processes/o-2/events",
                   SubmitEventReq("e3", "OrderConfirmed", sku = Some("B"), quantity = Some(2))))
        _ <- app(j(Method.POST, uri"/processes/o-2/events",
                   SubmitEventReq("e4", "PaymentCompleted")))
        r  <- app(Request[IO](Method.GET, uri"/processes/commands/pending"))
        cs <- r.as[List[CommandView]]
        _   = assertEquals(cs.length, 2)  // 两个 RequestInventoryReservation
        _   = assert(cs.forall(_.commandType == "RequestInventoryReservation"))
      yield ()
    }
  }
