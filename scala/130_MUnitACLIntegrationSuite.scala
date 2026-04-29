// 130_MUnitACLIntegrationSuite.scala
// 防腐层 第五步：MUnit ACL 集成测试
//
// 验证：
//   1. 支付成功回调正确翻译为 PaymentReceived 本地事件
//   2. 翻译失败（未知状态）仍返回 200，写入拒绝日志
//   3. 相同 messageId 幂等处理，状态不变
//   4. 物流送达回调翻译为 ShipmentDelivered
//   5. 拒绝日志可查询
//   6. 翻译后事件数量正确（不含幂等重放的重复）
//   7. ACL 翻译是纯函数：相同输入总得到相同输出

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
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.dsl.io._
import org.http4s.implicits._

// ── DTO（与 128 保持一致）────────────────────────────────────────────────────

case class PaymentDTO(messageId: String, txnId: String, merchantRef: String,
                      status: String, amountCents: Long, currency: String)
case class LogisticsDTO(messageId: String, shipmentNo: String, refCode: String,
                        eventCode: String, location: String)

// ── 本地事件与状态 ────────────────────────────────────────────────────────────

sealed trait DomainEvt
object DomainEvt:
  case class PaymentReceived(orderId: String, paymentId: String, amount: BigDecimal) extends DomainEvt
  case class PaymentDeclined(orderId: String, paymentId: String)                     extends DomainEvt
  case class ShipmentDelivered(orderId: String, trackingNo: String)                  extends DomainEvt
  case class ShipmentInTransit(orderId: String, trackingNo: String, loc: String)     extends DomainEvt

case class Rejection(messageId: String, source: String, reason: String)

case class ACLSt(
    processed: Set[String],
    events:    List[DomainEvt],
    rejects:   List[Rejection]
)
object ACLSt:
  val empty: ACLSt = ACLSt(Set.empty, Nil, Nil)

// ── 翻译函数 ──────────────────────────────────────────────────────────────────

def xlatePayment(dto: PaymentDTO): Either[String, DomainEvt] =
  dto.status match
    case "SUCCESS" if dto.merchantRef.nonEmpty && dto.amountCents > 0 =>
      Right(DomainEvt.PaymentReceived(dto.merchantRef, dto.txnId, BigDecimal(dto.amountCents) / 100))
    case "FAILED" =>
      Right(DomainEvt.PaymentDeclined(dto.merchantRef, dto.txnId))
    case "SUCCESS" if dto.merchantRef.isEmpty => Left("merchantRef 为空")
    case other => Left(s"不支持的状态: $other")

def xlateLogistics(dto: LogisticsDTO): Either[String, DomainEvt] =
  dto.eventCode match
    case "DELIVERED"  => Right(DomainEvt.ShipmentDelivered(dto.refCode, dto.shipmentNo))
    case "IN_TRANSIT" => Right(DomainEvt.ShipmentInTransit(dto.refCode, dto.shipmentNo, dto.location))
    case other        => Left(s"不支持的事件码: $other")

// ── DTO 响应 ──────────────────────────────────────────────────────────────────

case class ACKResp(received: Boolean, translated: Boolean, messageId: String)
case class EvtView(eventType: String, orderId: String)
case class RejView(messageId: String, source: String, reason: String)

// ── 测试用 App ────────────────────────────────────────────────────────────────

object TestACLApp:
  def make(st: Ref[IO, ACLSt])(
      using EntityDecoder[IO, PaymentDTO],
            EntityDecoder[IO, LogisticsDTO]
  ): HttpApp[IO] =
    HttpRoutes.of[IO] {

      case req @ POST -> Root / "acl" / "payment" =>
        req.as[PaymentDTO].flatMap { dto =>
          st.modify { s =>
            if s.processed.contains(dto.messageId) then
              s -> ACKResp(true, true, dto.messageId)
            else xlatePayment(dto) match
              case Right(e) =>
                s.copy(processed = s.processed + dto.messageId, events = s.events :+ e) ->
                  ACKResp(true, true, dto.messageId)
              case Left(r) =>
                s.copy(
                  processed = s.processed + dto.messageId,
                  rejects   = s.rejects :+ Rejection(dto.messageId, "Payment", r)
                ) -> ACKResp(true, false, dto.messageId)
          }.flatMap(ack => Ok(ack.asJson))
        }

      case req @ POST -> Root / "acl" / "logistics" =>
        req.as[LogisticsDTO].flatMap { dto =>
          st.modify { s =>
            if s.processed.contains(dto.messageId) then
              s -> ACKResp(true, true, dto.messageId)
            else xlateLogistics(dto) match
              case Right(e) =>
                s.copy(processed = s.processed + dto.messageId, events = s.events :+ e) ->
                  ACKResp(true, true, dto.messageId)
              case Left(r) =>
                s.copy(
                  processed = s.processed + dto.messageId,
                  rejects   = s.rejects :+ Rejection(dto.messageId, "Logistics", r)
                ) -> ACKResp(true, false, dto.messageId)
          }.flatMap(ack => Ok(ack.asJson))
        }

      case GET -> Root / "acl" / "events" =>
        st.get.flatMap { s =>
          Ok(s.events.map(e => EvtView(e.getClass.getSimpleName, e match
            case DomainEvt.PaymentReceived(o, _, _) => o
            case DomainEvt.PaymentDeclined(o, _)    => o
            case DomainEvt.ShipmentDelivered(o, _)  => o
            case DomainEvt.ShipmentInTransit(o,_,_) => o
          )).asJson)
        }

      case GET -> Root / "acl" / "rejections" =>
        st.get.flatMap { s =>
          Ok(s.rejects.map(r => RejView(r.messageId, r.source, r.reason)).asJson)
        }
    }.orNotFound

// ── 测试套件 ──────────────────────────────────────────────────────────────────

class MUnitACLIntegrationSuite extends CatsEffectSuite:

  given EntityDecoder[IO, PaymentDTO]      = jsonOf[IO, PaymentDTO]
  given EntityDecoder[IO, LogisticsDTO]    = jsonOf[IO, LogisticsDTO]
  given EntityDecoder[IO, ACKResp]         = jsonOf[IO, ACKResp]

  private def withApp[A](test: HttpApp[IO] => IO[A]): IO[A] =
    Ref.of[IO, ACLSt](ACLSt.empty).flatMap(st => test(TestACLApp.make(st)))

  private def j[A: io.circe.Encoder](m: Method, u: Uri, b: A): Request[IO] =
    Request[IO](method = m, uri = u).withEntity(b.asJson)

  // ── 测试 1：支付成功翻译 ────────────────────────────────────────────────────
  test("支付成功回调翻译为 PaymentReceived，返回 translated=true") {
    withApp { app =>
      for
        r    <- app(j(Method.POST, uri"/acl/payment",
                   PaymentDTO("m1", "txn-1", "o-1", "SUCCESS", 29900L, "CNY")))
        ack  <- r.as[ACKResp]
        _     = assertEquals(ack.translated, true)
        evts <- app(Request[IO](Method.GET, uri"/acl/events")).flatMap(_.as[List[EvtView]])
        _     = assertEquals(evts.length, 1)
        _     = assertEquals(evts.head.eventType, "PaymentReceived")
      yield ()
    }
  }

  // ── 测试 2：翻译失败仍返回 200，写入拒绝日志 ─────────────────────────────
  test("翻译失败仍返回 200 OK，translated=false，写入拒绝日志") {
    withApp { app =>
      for
        r   <- app(j(Method.POST, uri"/acl/payment",
                  PaymentDTO("m1", "txn-1", "o-1", "MYSTERY", 10000L, "CNY")))
        _    = assertEquals(r.status, Status.Ok)
        ack <- r.as[ACKResp]
        _    = assertEquals(ack.translated, false)
        rejs <- app(Request[IO](Method.GET, uri"/acl/rejections")).flatMap(_.as[List[RejView]])
        _    = assertEquals(rejs.length, 1)
        _    = assert(rejs.head.reason.contains("MYSTERY"))
      yield ()
    }
  }

  // ── 测试 3：幂等：相同 messageId 不重复翻译 ────────────────────────────────
  test("相同 messageId 幂等处理，事件不重复") {
    withApp { app =>
      for
        _ <- app(j(Method.POST, uri"/acl/payment",
                  PaymentDTO("m1", "txn-1", "o-1", "SUCCESS", 10000L, "CNY")))
        _ <- app(j(Method.POST, uri"/acl/payment",
                  PaymentDTO("m1", "txn-1", "o-1", "SUCCESS", 10000L, "CNY")))  // 重复
        evts <- app(Request[IO](Method.GET, uri"/acl/events")).flatMap(_.as[List[EvtView]])
        _     = assertEquals(evts.length, 1)   // 只有 1 条，不是 2
      yield ()
    }
  }

  // ── 测试 4：物流送达翻译 ────────────────────────────────────────────────────
  test("物流送达回调翻译为 ShipmentDelivered") {
    withApp { app =>
      for
        _ <- app(j(Method.POST, uri"/acl/logistics",
                  LogisticsDTO("m1", "SF-001", "o-1", "DELIVERED", "上海")))
        evts <- app(Request[IO](Method.GET, uri"/acl/events")).flatMap(_.as[List[EvtView]])
        _     = assertEquals(evts.length, 1)
        _     = assertEquals(evts.head.eventType, "ShipmentDelivered")
        _     = assertEquals(evts.head.orderId, "o-1")
      yield ()
    }
  }

  // ── 测试 5：ACL 翻译纯函数：相同输入相同输出 ────────────────────────────────
  test("ACL 翻译是纯函数：相同输入总得到相同结果") {
    val dto = PaymentDTO("m1", "txn-1", "o-1", "SUCCESS", 29900L, "CNY")
    val r1  = xlatePayment(dto)
    val r2  = xlatePayment(dto)
    IO(assertEquals(r1, r2))
  }

  // ── 测试 6：多个上游源事件互不干扰 ────────────────────────────────────────
  test("支付和物流来源的事件都进入同一领域事件列表") {
    withApp { app =>
      for
        _ <- app(j(Method.POST, uri"/acl/payment",
                  PaymentDTO("m1", "txn-1", "o-1", "SUCCESS", 10000L, "CNY")))
        _ <- app(j(Method.POST, uri"/acl/logistics",
                  LogisticsDTO("m2", "SF-001", "o-1", "IN_TRANSIT", "深圳")))
        evts <- app(Request[IO](Method.GET, uri"/acl/events")).flatMap(_.as[List[EvtView]])
        _     = assertEquals(evts.length, 2)
        _     = assert(evts.map(_.eventType).contains("PaymentReceived"))
        _     = assert(evts.map(_.eventType).contains("ShipmentInTransit"))
      yield ()
    }
  }

  // ── 测试 7：支付失败翻译为 PaymentDeclined ──────────────────────────────────
  test("支付失败回调翻译为 PaymentDeclined") {
    withApp { app =>
      for
        _ <- app(j(Method.POST, uri"/acl/payment",
                  PaymentDTO("m1", "txn-1", "o-1", "FAILED", 0L, "CNY")))
        evts <- app(Request[IO](Method.GET, uri"/acl/events")).flatMap(_.as[List[EvtView]])
        _     = assertEquals(evts.head.eventType, "PaymentDeclined")
      yield ()
    }
  }
