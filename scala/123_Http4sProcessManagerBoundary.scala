// 123_Http4sProcessManagerBoundary.scala
// 进程管理器 第三步：http4s 进程管理器边界
//
// 核心思想：
//   进程管理器对外暴露三个 HTTP 接口：
//     POST /processes/{orderId}/events   → 提交领域事件（推进进程状态）
//     GET  /processes/{orderId}/state    → 查询进程当前状态和事件历史
//     GET  /processes/commands/pending   → 查看所有待执行的命令
//
//   关键约定：
//     - 事件提交是幂等的（相同 eventId 只处理一次）
//     - 进程状态不可直接修改，只能通过事件推进
//     - 命令队列是 Outbox 模式的基础

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

// ── 请求/响应 DTO ─────────────────────────────────────────────────────────────

case class SubmitEventRequest(
    eventId:   String,     // 幂等键
    eventType: String,     // OrderConfirmed / PaymentCompleted / ...
    sku:       Option[String],
    quantity:  Option[Int],
    paymentId: Option[String],
    warehouseId: Option[String],
    reason:    Option[String]
)

case class ProcessStateView(
    orderId:    String,
    status:     String,
    sku:        String,
    quantity:   Int,
    warehouseId: Option[String],
    eventCount: Int
)

case class CommandView(
    orderId:     String,
    commandType: String,
    detail:      String
)

case class EventAccepted(processId: String, eventType: String, commandsTriggered: Int)
case class ErrorResp(code: String, message: String)

// ── 进程状态（简化版）────────────────────────────────────────────────────────

case class ProcessEntry(
    orderId:     String,
    sku:         String,
    quantity:    Int,
    warehouseId: Option[String],
    status:      String,          // awaiting-payment / awaiting-inventory / ...
    eventIds:    Set[String],     // 已处理的 eventId（幂等去重）
    eventCount:  Int,
    commands:    List[CommandView]
)

object ProcessEntry:
  def initial(orderId: String): ProcessEntry =
    ProcessEntry(orderId, "", 0, None, "awaiting-payment", Set.empty, 0, Nil)

// ── 路由 ──────────────────────────────────────────────────────────────────────

def processManagerRoutes(
    registry: Ref[IO, Map[String, ProcessEntry]]
)(using EntityDecoder[IO, SubmitEventRequest]): HttpRoutes[IO] =
  HttpRoutes.of[IO] {

    // 提交事件，推进进程状态
    case req @ POST -> Root / "processes" / orderId / "events" =>
      req.as[SubmitEventRequest].flatMap { body =>
        registry.modify { m =>
          val entry = m.getOrElse(orderId, ProcessEntry.initial(orderId))
          if entry.eventIds.contains(body.eventId) then
            // 幂等：相同 eventId 忽略
            m -> Left(s"eventId=${body.eventId} 已处理")
          else
            val (newStatus, newSku, newQty, newWh, newCmds) = body.eventType match
              case "OrderConfirmed" =>
                val sku = body.sku.getOrElse("")
                val qty = body.quantity.getOrElse(0)
                ("awaiting-payment", sku, qty, entry.warehouseId, Nil)
              case "PaymentCompleted" =>
                val cmds = List(CommandView(orderId, "RequestInventoryReservation",
                                           s"sku=${entry.sku} qty=${entry.quantity}"))
                ("awaiting-inventory", entry.sku, entry.quantity, entry.warehouseId, cmds)
              case "InventoryReserved" =>
                val wh   = body.warehouseId.getOrElse("")
                val cmds = List(CommandView(orderId, "RequestShipment", s"warehouseId=$wh"))
                ("awaiting-shipment", entry.sku, entry.quantity, Some(wh), cmds)
              case "ShipmentDelivered" =>
                val cmds = List(CommandView(orderId, "NotifyCustomer", "已送达"))
                ("delivered", entry.sku, entry.quantity, entry.warehouseId, cmds)
              case "PaymentFailed" =>
                val cmds = List(CommandView(orderId, "NotifyCustomer",
                                           s"支付失败: ${body.reason.getOrElse("")}"))
                ("cancelled", entry.sku, entry.quantity, entry.warehouseId, cmds)
              case "InventoryUnavailable" =>
                val r    = body.reason.getOrElse("unknown")
                val cmds = List(
                  CommandView(orderId, "IssueRefund", r),
                  CommandView(orderId, "NotifyCustomer", s"退款: $r")
                )
                ("compensating", entry.sku, entry.quantity, entry.warehouseId, cmds)
              case other =>
                (entry.status, entry.sku, entry.quantity, entry.warehouseId, Nil)

            val updated = entry.copy(
              sku         = newSku,
              quantity    = newQty,
              warehouseId = newWh,
              status      = newStatus,
              eventIds    = entry.eventIds + body.eventId,
              eventCount  = entry.eventCount + 1,
              commands    = entry.commands ++ newCmds
            )
            m.updated(orderId, updated) -> Right(newCmds.length)
        }.flatMap {
          case Right(n) =>
            Accepted(EventAccepted(orderId, body.eventType, n).asJson)
          case Left(msg) =>
            Ok(EventAccepted(orderId, body.eventType, 0).asJson)  // 幂等返回成功
        }
      }

    // 查询进程状态
    case GET -> Root / "processes" / orderId / "state" =>
      registry.get.flatMap { m =>
        m.get(orderId) match
          case None => NotFound(ErrorResp("NOT_FOUND", s"进程 $orderId 不存在").asJson)
          case Some(entry) =>
            Ok(ProcessStateView(
              entry.orderId, entry.status, entry.sku, entry.quantity,
              entry.warehouseId, entry.eventCount
            ).asJson)
      }

    // 查询待执行命令
    case GET -> Root / "processes" / "commands" / "pending" =>
      registry.get.flatMap { m =>
        val cmds = m.values.flatMap(_.commands).toList
        Ok(cmds.asJson)
      }
  }

// ── 演示 ──────────────────────────────────────────────────────────────────────

object Http4sProcessManagerBoundaryDemo extends IOApp.Simple:

  given EntityDecoder[IO, SubmitEventRequest] = jsonOf[IO, SubmitEventRequest]

  private def j[A: io.circe.Encoder](m: Method, u: Uri, b: A): Request[IO] =
    Request[IO](method = m, uri = u).withEntity(b.asJson)

  def run: IO[Unit] =
    for
      registry <- Ref.of[IO, Map[String, ProcessEntry]](Map.empty)
      app       = processManagerRoutes(registry).orNotFound

      _ <- IO.println("=== http4s 进程管理器边界：事件提交 + 状态查询 + 命令队列 ===\n")

      // 订单确认
      r1 <- app(j(Method.POST, uri"/processes/o-1/events",
                   SubmitEventRequest("evt-001", "OrderConfirmed", Some("SKU-A"), Some(2), None, None, None)))
      b1 <- r1.as[String]
      _  <- IO.println(s"[POST OrderConfirmed] ${r1.status.code}: $b1")

      // 支付完成
      r2 <- app(j(Method.POST, uri"/processes/o-1/events",
                   SubmitEventRequest("evt-002", "PaymentCompleted", None, None, Some("pay-001"), None, None)))
      b2 <- r2.as[String]
      _  <- IO.println(s"[POST PaymentCompleted] ${r2.status.code}: $b2")

      // 重复事件（幂等）
      r3 <- app(j(Method.POST, uri"/processes/o-1/events",
                   SubmitEventRequest("evt-002", "PaymentCompleted", None, None, Some("pay-001"), None, None)))
      b3 <- r3.as[String]
      _  <- IO.println(s"[POST PaymentCompleted (重复)] ${r3.status.code}: $b3")

      // 查询进程状态
      r4 <- app(Request[IO](Method.GET, uri"/processes/o-1/state"))
      b4 <- r4.as[String]
      _  <- IO.println(s"\n[GET state] ${r4.status.code}: $b4")

      // 库存确认
      r5 <- app(j(Method.POST, uri"/processes/o-1/events",
                   SubmitEventRequest("evt-003", "InventoryReserved", None, None, None, Some("WH-SZ"), None)))
      b5 <- r5.as[String]
      _  <- IO.println(s"\n[POST InventoryReserved] ${r5.status.code}: $b5")

      // 查询待执行命令
      r6 <- app(Request[IO](Method.GET, uri"/processes/commands/pending"))
      b6 <- r6.as[String]
      _  <- IO.println(s"\n[GET pending commands] ${r6.status.code}: $b6")

      _ <- IO.println("""
|关键点：
|  1. POST /events 推进进程状态，相同 eventId 幂等忽略
|  2. GET  /state  返回进程当前状态（只读，不产生副作用）
|  3. GET  /commands/pending 返回所有待执行命令（供调度器拉取）
|  4. 进程状态只能通过事件推进，不能直接 PUT 修改""".stripMargin)
    yield ()
