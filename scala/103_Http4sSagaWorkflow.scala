//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.http4s::http4s-core:0.23.33"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.http4s::http4s-circe:0.23.33"
//> using dep "io.circe::circe-generic:0.14.10"

/**
 * Scala 函数式编程 Demo 103: http4s Saga 工作流边界
 *
 * 102 号 Demo 已经把超时补偿流跑起来了，
 * 这一版继续把 Saga 推进到 HTTP 边界：
 * 下单创建流程、支付回调推进状态，以及查询当前工作流状态。
 */
import cats.effect.{IO, IOApp, Ref}
import cats.syntax.all._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.typelevel.ci._

object Http4sSagaWorkflow extends IOApp.Simple {

  final case class CheckoutRequest(orderId: String, sku: String, quantity: Int, amount: BigDecimal)
  final case class CheckoutAccepted(sagaId: String, status: String)
  final case class PaymentCallbackRequest(callbackId: String, sagaId: String, approved: Boolean, reason: Option[String])
  final case class PaymentAck(sagaId: String, status: String, replayed: Boolean)
  final case class SagaView(sagaId: String, orderId: String, status: String, compensationReason: Option[String])
  final case class ErrorResponse(error: String)

  final case class SagaRecord(
      sagaId: String,
      orderId: String,
      sku: String,
      quantity: Int,
      amount: BigDecimal,
      status: String,
      compensationReason: Option[String]
  )

  final case class State(
      sagas: Map[String, SagaRecord],
      processedCallbacks: Set[String],
      nextSagaId: Long
  )

  implicit val checkoutRequestDecoder: EntityDecoder[IO, CheckoutRequest] = jsonOf[IO, CheckoutRequest]
  implicit val checkoutAcceptedEncoder: EntityEncoder[IO, CheckoutAccepted] = jsonEncoderOf[IO, CheckoutAccepted]
  implicit val callbackRequestDecoder: EntityDecoder[IO, PaymentCallbackRequest] = jsonOf[IO, PaymentCallbackRequest]
  implicit val paymentAckEncoder: EntityEncoder[IO, PaymentAck] = jsonEncoderOf[IO, PaymentAck]
  implicit val sagaViewEncoder: EntityEncoder[IO, SagaView] = jsonEncoderOf[IO, SagaView]
  implicit val errorEncoder: EntityEncoder[IO, ErrorResponse] = jsonEncoderOf[IO, ErrorResponse]

  final class SagaService private (state: Ref[IO, State]) {

    def startCheckout(body: CheckoutRequest): IO[CheckoutAccepted] =
      state.modify { current =>
        val sagaId = s"saga-${current.nextSagaId}"
        val record = SagaRecord(
          sagaId = sagaId,
          orderId = body.orderId,
          sku = body.sku.trim,
          quantity = body.quantity,
          amount = body.amount,
          status = "waiting-payment",
          compensationReason = None
        )
        current.copy(
          sagas = current.sagas.updated(sagaId, record),
          nextSagaId = current.nextSagaId + 1
        ) -> CheckoutAccepted(sagaId, "waiting-payment")
      }.flatTap(accepted => IO.println(s"[checkout] 创建 Saga: ${accepted.sagaId}, orderId=${body.orderId}"))

    def handleCallback(body: PaymentCallbackRequest): IO[PaymentAck] =
      state.modify { current =>
        if (current.processedCallbacks.contains(body.callbackId)) {
          val status = current.sagas.get(body.sagaId).map(_.status).getOrElse("unknown")
          current -> Right(PaymentAck(body.sagaId, status, replayed = true))
        } else {
          current.sagas.get(body.sagaId) match {
            case None =>
              current -> Left(new RuntimeException(s"找不到 saga: ${body.sagaId}"))

            case Some(existing) if existing.status != "waiting-payment" =>
              current.copy(processedCallbacks = current.processedCallbacks + body.callbackId) ->
                Right(PaymentAck(body.sagaId, existing.status, replayed = true))

            case Some(existing) =>
              val nextStatus = if (body.approved) "completed" else "compensated"
              val reason = if (body.approved) None else Some(body.reason.getOrElse("payment-declined"))
              val updated = existing.copy(status = nextStatus, compensationReason = reason)
              current.copy(
                sagas = current.sagas.updated(body.sagaId, updated),
                processedCallbacks = current.processedCallbacks + body.callbackId
              ) -> Right(PaymentAck(body.sagaId, nextStatus, replayed = false))
          }
        }
      }.flatMap {
        case Right(ack) =>
          IO.println(s"[callback] callbackId=${body.callbackId}, sagaId=${body.sagaId}, status=${ack.status}, replayed=${ack.replayed}") *>
            ack.pure[IO]

        case Left(error) =>
          IO.raiseError(error)
      }

    def viewSaga(sagaId: String): IO[Option[SagaView]] =
      state.get.map(
        _.sagas.get(sagaId).map(record => SagaView(record.sagaId, record.orderId, record.status, record.compensationReason))
      )
  }

  object SagaService {
    def create: IO[SagaService] =
      Ref.of[IO, State](State(Map.empty, Set.empty, nextSagaId = 1L)).map(new SagaService(_))
  }

  def buildApp(service: SagaService): HttpApp[IO] =
    HttpRoutes.of[IO] {
      case req @ POST -> Root / "checkouts" =>
        req.as[CheckoutRequest].flatMap(body => Accepted(service.startCheckout(body)))

      case req @ POST -> Root / "payments" / "callback" =>
        req.as[PaymentCallbackRequest].flatMap(body =>
          service.handleCallback(body).flatMap(Ok(_)).handleErrorWith(error => BadRequest(ErrorResponse(error.getMessage)))
        )

      case GET -> Root / "sagas" / sagaId =>
        service.viewSaga(sagaId).flatMap {
          case Some(view) => Ok(view)
          case None => NotFound(ErrorResponse(s"找不到 saga: $sagaId"))
        }
    }.orNotFound

  def jsonRequest[A](method: Method, uri: Uri, body: A)(implicit encoder: io.circe.Encoder[A]): Request[IO] =
    Request[IO](method, uri)
      .withEntity(body.asJson.noSpaces)
      .putHeaders(Header.Raw(ci"Content-Type", "application/json"))

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== http4s Saga 工作流边界 ===")
      service <- SagaService.create
      app = buildApp(service)

      checkout1 <- app(jsonRequest(Method.POST, uri"/checkouts", CheckoutRequest("order-100", "BTC-101", 2, BigDecimal(128.5))))
      body1 <- checkout1.as[String]
      _ <- IO.println(s"创建第一个 Saga: status=${checkout1.status.code}, body=$body1")

      callback1 <- app(
        jsonRequest(
          Method.POST,
          uri"/payments/callback",
          PaymentCallbackRequest("cb-100", "saga-1", approved = false, reason = Some("payment-declined"))
        )
      )
      callbackBody1 <- callback1.as[String]
      _ <- IO.println(s"失败回调结果: status=${callback1.status.code}, body=$callbackBody1")

      callbackReplay <- app(
        jsonRequest(
          Method.POST,
          uri"/payments/callback",
          PaymentCallbackRequest("cb-100", "saga-1", approved = false, reason = Some("payment-declined"))
        )
      )
      callbackReplayBody <- callbackReplay.as[String]
      _ <- IO.println(s"重复回调结果: status=${callbackReplay.status.code}, body=$callbackReplayBody")

      saga1 <- app(Request[IO](Method.GET, uri"/sagas/saga-1"))
      sagaBody1 <- saga1.as[String]
      _ <- IO.println(s"查询 saga-1: status=${saga1.status.code}, body=$sagaBody1")

      checkout2 <- app(jsonRequest(Method.POST, uri"/checkouts", CheckoutRequest("order-200", "ETH-202", 1, BigDecimal(88.0))))
      body2 <- checkout2.as[String]
      _ <- IO.println(s"创建第二个 Saga: status=${checkout2.status.code}, body=$body2")

      callback2 <- app(
        jsonRequest(
          Method.POST,
          uri"/payments/callback",
          PaymentCallbackRequest("cb-200", "saga-2", approved = true, reason = None)
        )
      )
      callbackBody2 <- callback2.as[String]
      _ <- IO.println(s"成功回调结果: status=${callback2.status.code}, body=$callbackBody2")

      saga2 <- app(Request[IO](Method.GET, uri"/sagas/saga-2"))
      sagaBody2 <- saga2.as[String]
      _ <- IO.println(s"查询 saga-2: status=${saga2.status.code}, body=$sagaBody2")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- Saga 一旦进入 HTTP 边界，就需要把创建、回调、状态查询这些接口显式组织出来")
      _ <- IO.println("- 支付回调本身也可能重复投递，所以 callbackId 仍然需要幂等保护")
      _ <- IO.println("- 下一版会把 Saga 状态推进和补偿动作一起下沉到数据库事务")
    } yield ()
}
