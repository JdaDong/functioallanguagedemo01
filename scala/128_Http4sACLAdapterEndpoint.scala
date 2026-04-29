// 128_Http4sACLAdapterEndpoint.scala
// 防腐层 第三步：http4s ACL 适配边界
//
// 核心思想：
//   上游系统通过 HTTP 回调推送事件时，ACL 适配端点需要：
//     1. 接收上游 DTO（外部格式）
//     2. 立即返回 200（让上游知道已收到）
//     3. 在内部翻译成本地领域事件
//     4. 如果翻译失败，记录拒绝日志但仍然返回 200（防止上游重试风暴）
//     5. 对重复回调幂等处理（相同 messageId 只翻译一次）
//
//   接口设计：
//     POST /acl/payment-gateway/callbacks  → 支付网关回调（外部格式）
//     POST /acl/logistics/events           → 物流事件（外部格式）
//     GET  /acl/events/translated          → 查看翻译后的本地领域事件
//     GET  /acl/events/rejected            → 查看拒绝日志

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

// ── 上游回调 DTO ──────────────────────────────────────────────────────────────

case class PaymentCallbackDTO(
    messageId:   String,
    txnId:       String,
    merchantRef: String,
    status:      String,
    amountCents: Long,
    currency:    String
)

case class LogisticsEventDTO(
    messageId:  String,
    shipmentNo: String,
    refCode:    String,
    eventCode:  String,
    location:   String
)

// ── 本地领域事件（翻译后）────────────────────────────────────────────────────

sealed trait DomainEvent
object DomainEvent:
  case class PaymentReceived(orderId: String, paymentId: String, amount: BigDecimal) extends DomainEvent
  case class PaymentDeclined(orderId: String, paymentId: String)                     extends DomainEvent
  case class ShipmentDelivered(orderId: String, trackingNo: String)                  extends DomainEvent
  case class ShipmentInTransit(orderId: String, trackingNo: String, location: String) extends DomainEvent

case class RejectionRecord(messageId: String, source: String, reason: String)

// ── 状态 ──────────────────────────────────────────────────────────────────────

case class ACLState(
    processedIds: Set[String],
    events:       List[DomainEvent],
    rejections:   List[RejectionRecord]
)
object ACLState:
  val empty: ACLState = ACLState(Set.empty, Nil, Nil)

// ── 翻译逻辑（纯函数）────────────────────────────────────────────────────────

def translatePayment(dto: PaymentCallbackDTO): Either[String, DomainEvent] =
  dto.status match
    case "SUCCESS" if dto.merchantRef.nonEmpty && dto.amountCents > 0 =>
      Right(DomainEvent.PaymentReceived(
        dto.merchantRef, dto.txnId, BigDecimal(dto.amountCents) / 100))
    case "FAILED" =>
      Right(DomainEvent.PaymentDeclined(dto.merchantRef, dto.txnId))
    case "SUCCESS" if dto.merchantRef.isEmpty =>
      Left("merchantRef 为空")
    case other =>
      Left(s"不支持的状态: $other")

def translateLogistics(dto: LogisticsEventDTO): Either[String, DomainEvent] =
  dto.eventCode match
    case "DELIVERED"  => Right(DomainEvent.ShipmentDelivered(dto.refCode, dto.shipmentNo))
    case "IN_TRANSIT" => Right(DomainEvent.ShipmentInTransit(dto.refCode, dto.shipmentNo, dto.location))
    case other        => Left(s"不支持的事件码: $other")

// ── HTTP 路由 ─────────────────────────────────────────────────────────────────

case class ACKResponse(received: Boolean, translated: Boolean, messageId: String)
case class EventView(eventType: String, detail: String)
case class RejectionView(messageId: String, source: String, reason: String)

def aclRoutes(
    state: Ref[IO, ACLState]
)(using
    EntityDecoder[IO, PaymentCallbackDTO],
    EntityDecoder[IO, LogisticsEventDTO]
): HttpRoutes[IO] =
  HttpRoutes.of[IO] {

    // 支付网关回调入口
    case req @ POST -> Root / "acl" / "payment-gateway" / "callbacks" =>
      req.as[PaymentCallbackDTO].flatMap { dto =>
        state.modify { s =>
          if s.processedIds.contains(dto.messageId) then
            // 幂等：已处理，直接返回成功
            s -> ACKResponse(received = true, translated = true, dto.messageId)
          else
            translatePayment(dto) match
              case Right(event) =>
                s.copy(
                  processedIds = s.processedIds + dto.messageId,
                  events = s.events :+ event
                ) -> ACKResponse(received = true, translated = true, dto.messageId)
              case Left(reason) =>
                s.copy(
                  processedIds = s.processedIds + dto.messageId,
                  rejections = s.rejections :+ RejectionRecord(dto.messageId, "PaymentGateway", reason)
                ) -> ACKResponse(received = true, translated = false, dto.messageId)
        }.flatMap(ack => Ok(ack.asJson))
      }

    // 物流事件入口
    case req @ POST -> Root / "acl" / "logistics" / "events" =>
      req.as[LogisticsEventDTO].flatMap { dto =>
        state.modify { s =>
          if s.processedIds.contains(dto.messageId) then
            s -> ACKResponse(received = true, translated = true, dto.messageId)
          else
            translateLogistics(dto) match
              case Right(event) =>
                s.copy(
                  processedIds = s.processedIds + dto.messageId,
                  events = s.events :+ event
                ) -> ACKResponse(received = true, translated = true, dto.messageId)
              case Left(reason) =>
                s.copy(
                  processedIds = s.processedIds + dto.messageId,
                  rejections = s.rejections :+ RejectionRecord(dto.messageId, "Logistics", reason)
                ) -> ACKResponse(received = true, translated = false, dto.messageId)
        }.flatMap(ack => Ok(ack.asJson))
      }

    // 查询翻译后的本地领域事件
    case GET -> Root / "acl" / "events" / "translated" =>
      state.get.flatMap { s =>
        val views = s.events.map(e => EventView(e.getClass.getSimpleName, e.toString))
        Ok(views.asJson)
      }

    // 查询拒绝日志
    case GET -> Root / "acl" / "events" / "rejected" =>
      state.get.flatMap { s =>
        val views = s.rejections.map(r => RejectionView(r.messageId, r.source, r.reason))
        Ok(views.asJson)
      }
  }

// ── 演示 ──────────────────────────────────────────────────────────────────────

object Http4sACLAdapterEndpointDemo extends IOApp.Simple:

  given EntityDecoder[IO, PaymentCallbackDTO]  = jsonOf[IO, PaymentCallbackDTO]
  given EntityDecoder[IO, LogisticsEventDTO]   = jsonOf[IO, LogisticsEventDTO]

  private def j[A: io.circe.Encoder](m: Method, u: Uri, b: A): Request[IO] =
    Request[IO](method = m, uri = u).withEntity(b.asJson)

  def run: IO[Unit] =
    for
      state <- Ref.of[IO, ACLState](ACLState.empty)
      app    = aclRoutes(state).orNotFound

      _ <- IO.println("=== http4s ACL 适配端点：接收上游回调，翻译成本地领域事件 ===\n")

      // 支付成功回调
      r1 <- app(j(Method.POST, uri"/acl/payment-gateway/callbacks",
                   PaymentCallbackDTO("msg-001", "txn-1", "order-100", "SUCCESS", 29900L, "CNY")))
      b1 <- r1.as[String]
      _  <- IO.println(s"[支付成功] ${r1.status.code}: $b1")

      // 重复回调（幂等）
      r2 <- app(j(Method.POST, uri"/acl/payment-gateway/callbacks",
                   PaymentCallbackDTO("msg-001", "txn-1", "order-100", "SUCCESS", 29900L, "CNY")))
      b2 <- r2.as[String]
      _  <- IO.println(s"[重复回调] ${r2.status.code}: $b2")

      // 翻译失败（未知状态），仍返回 200
      r3 <- app(j(Method.POST, uri"/acl/payment-gateway/callbacks",
                   PaymentCallbackDTO("msg-002", "txn-2", "order-101", "MYSTERY", 10000L, "CNY")))
      b3 <- r3.as[String]
      _  <- IO.println(s"[翻译失败] ${r3.status.code}: $b3")

      // 物流送达事件
      r4 <- app(j(Method.POST, uri"/acl/logistics/events",
                   LogisticsEventDTO("msg-003", "SF-999", "order-100", "DELIVERED", "上海浦东")))
      b4 <- r4.as[String]
      _  <- IO.println(s"[物流送达] ${r4.status.code}: $b4")

      // 查询翻译后的本地事件
      r5 <- app(Request[IO](Method.GET, uri"/acl/events/translated"))
      b5 <- r5.as[String]
      _  <- IO.println(s"\n[GET translated] ${r5.status.code}: $b5")

      // 查询拒绝日志
      r6 <- app(Request[IO](Method.GET, uri"/acl/events/rejected"))
      b6 <- r6.as[String]
      _  <- IO.println(s"[GET rejected] ${r6.status.code}: $b6")

      _ <- IO.println("""
|关键点：
|  1. 上游回调始终返回 200，防止上游重试风暴；翻译结果通过 translated 字段告知
|  2. 相同 messageId 幂等处理，防止重复翻译
|  3. 翻译失败写入拒绝日志，不阻塞正常消息处理
|  4. 内部路由完全使用本地领域概念，不暴露上游模型""".stripMargin)
    yield ()
