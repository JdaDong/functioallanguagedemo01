//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.http4s::http4s-core:0.23.33"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.http4s::http4s-circe:0.23.33"
//> using dep "io.circe::circe-generic:0.14.10"

/**
 * Scala 函数式编程 Demo 98: http4s Webhook Inbox 接收边界
 *
 * 97 号 Demo 已经把后台消费流跑起来了，
 * 这一版继续把消费端幂等推进到真实 HTTP webhook 接收边界。
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

object Http4sWebhookInbox extends IOApp.Simple {

  final case class WebhookPayload(orderId: String, sku: String, quantity: Int)
  final case class WebhookAck(orderId: String, replayed: Boolean, projectionCount: Int)
  final case class ErrorResponse(error: String)
  final case class ShipmentProjection(orderId: String, sku: String, quantity: Int, status: String)
  final case class ProcessedEvent(eventId: String, fingerprint: String)
  final case class State(
      shipments: Map[String, ShipmentProjection],
      processed: Map[String, ProcessedEvent],
      failFirst: Set[String]
  )

  implicit val webhookPayloadDecoder: EntityDecoder[IO, WebhookPayload] =
    jsonOf[IO, WebhookPayload]

  implicit val webhookAckEncoder: EntityEncoder[IO, WebhookAck] =
    jsonEncoderOf[IO, WebhookAck]

  implicit val errorEncoder: EntityEncoder[IO, ErrorResponse] =
    jsonEncoderOf[IO, ErrorResponse]

  def headerValue(headers: Headers, name: CIString): Option[String] =
    headers.headers.find(_.name == name).map(_.value)

  final class InboxService private (state: Ref[IO, State]) {

    def consume(eventId: String, payload: WebhookPayload): IO[WebhookAck] =
      state.modify { current =>
        val fingerprint = s"${payload.orderId}|${payload.sku.trim}|${payload.quantity}"

        current.processed.get(eventId) match {
          case Some(saved) if saved.fingerprint == fingerprint =>
            current -> Right(WebhookAck(payload.orderId, replayed = true, current.shipments.size))

          case Some(_) =>
            current -> Left(new RuntimeException("相同 eventId 不能复用到不同 payload"))

          case None if current.failFirst.contains(eventId) =>
            current.copy(failFirst = current.failFirst - eventId) ->
              Left(new RuntimeException("warehouse projection service unavailable"))

          case None =>
            val next = current.copy(
              shipments = current.shipments.updated(
                payload.orderId,
                ShipmentProjection(payload.orderId, payload.sku, payload.quantity, status = "scheduled")
              ),
              processed = current.processed.updated(eventId, ProcessedEvent(eventId, fingerprint))
            )
            next -> Right(WebhookAck(payload.orderId, replayed = false, next.shipments.size))
        }
      }.flatMap(_.liftTo[IO])
  }

  object InboxService {
    def create: IO[InboxService] =
      Ref.of[IO, State](
        State(
          shipments = Map.empty,
          processed = Map.empty,
          failFirst = Set("evt-100")
        )
      ).map(new InboxService(_))
  }

  def buildApp(service: InboxService): HttpApp[IO] =
    HttpRoutes.of[IO] {
      case req @ POST -> Root / "warehouse" / "webhooks" / "order-created" =>
        headerValue(req.headers, ci"X-Event-Id") match {
          case None =>
            BadRequest(ErrorResponse("缺少 X-Event-Id 头"))

          case Some(eventId) =>
            req.as[WebhookPayload].flatMap { body =>
              service.consume(eventId, body).attempt.flatMap {
                case Right(result) =>
                  Ok(result).map(_.putHeaders(Header.Raw(ci"X-Webhook-Replayed", result.replayed.toString)))

                case Left(error) if error.getMessage.contains("不同 payload") =>
                  Conflict(ErrorResponse(error.getMessage))

                case Left(error) =>
                  InternalServerError(ErrorResponse(error.getMessage))
              }
            }
        }
    }.orNotFound

  def request(eventId: Option[String], body: WebhookPayload): Request[IO] = {
    val base = Request[IO](Method.POST, uri"/warehouse/webhooks/order-created")
      .withEntity(body.asJson.noSpaces)
      .putHeaders(Header.Raw(ci"Content-Type", "application/json"))

    eventId.fold(base)(value => base.putHeaders(Header.Raw(ci"X-Event-Id", value)))
  }

  def render(label: String, response: Response[IO]): IO[Unit] =
    response.as[String].flatMap { body =>
      val replayed = response.headers.headers.find(_.name == ci"X-Webhook-Replayed").map(_.value).getOrElse("-")
      IO.println(s"$label -> status=${response.status.code}, replayed=$replayed, body=$body")
    }

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== http4s Webhook Inbox 接收边界 ===")
      service <- InboxService.create
      app = buildApp(service)
      payload = WebhookPayload("order-100", "BTC-101", 2)

      first <- app(request(Some("evt-100"), payload))
      _ <- render("first-fail", first)

      second <- app(request(Some("evt-100"), payload))
      _ <- render("retry-success", second)

      replay <- app(request(Some("evt-100"), payload))
      _ <- render("replay", replay)

      conflict <- app(request(Some("evt-100"), payload.copy(quantity = 3)))
      _ <- render("conflict", conflict)

      missing <- app(request(None, payload))
      _ <- render("missing-header", missing)

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- Webhook 接收端最关键的是用事件标识做消费幂等，而不是相信上游永远只投递一次")
      _ <- IO.println("- 同一个 eventId 对应不同 payload 时应该显式冲突，避免把错误重放伪装成成功")
      _ <- IO.println("- 下一步会把 projection 写入和 processed_event 记录一起下沉到 Doobie 事务里")
    } yield ()
}
