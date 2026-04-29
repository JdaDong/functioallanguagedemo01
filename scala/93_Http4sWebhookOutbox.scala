//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.http4s::http4s-core:0.23.33"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.http4s::http4s-circe:0.23.33"
//> using dep "io.circe::circe-generic:0.14.10"

/**
 * Scala 函数式编程 Demo 93: http4s Webhook + Outbox 发布边界
 *
 * 92 号 Demo 已经把后台重试流跑起来了，
 * 这一版继续把事件真正推进到 HTTP 回调边界。
 */
import cats.effect.{IO, IOApp, Ref}
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.typelevel.ci._

object Http4sWebhookOutbox extends IOApp.Simple {

  final case class OutboxEvent(id: Long, eventType: String, orderId: String, sku: String, quantity: Int)
  final case class WebhookRequest(eventType: String, orderId: String, sku: String, quantity: Int)
  final case class WebhookResponse(accepted: Boolean, message: String)

  implicit val webhookRequestDecoder: EntityDecoder[IO, WebhookRequest] =
    jsonOf[IO, WebhookRequest]

  implicit val webhookResponseEncoder: EntityEncoder[IO, WebhookResponse] =
    jsonEncoderOf[IO, WebhookResponse]

  def partnerApp(failFirstOrder: Ref[IO, Set[String]]): HttpApp[IO] =
    HttpRoutes.of[IO] {
      case req @ POST -> Root / "partner" / "webhooks" / "orders" =>
        req.as[WebhookRequest].flatMap { body =>
          failFirstOrder.modify { current =>
            if (current.contains(body.orderId)) (current - body.orderId) -> true
            else current -> false
          }.flatMap { shouldFail =>
            if (shouldFail) {
              InternalServerError(WebhookResponse(accepted = false, s"模拟下游失败: ${body.orderId}"))
            } else {
              Ok(WebhookResponse(accepted = true, s"已接收 ${body.orderId}"))
            }
          }
        }
    }.orNotFound

  def dispatchOnce(app: HttpApp[IO], event: OutboxEvent): IO[Boolean] = {
    val request = Request[IO](Method.POST, uri"/partner/webhooks/orders")
      .withEntity(
        WebhookRequest(event.eventType, event.orderId, event.sku, event.quantity).asJson.noSpaces
      )
      .putHeaders(Header.Raw(ci"Content-Type", "application/json"))

    app(request).flatMap { response =>
      response.as[String].flatMap { body =>
        IO.println(s"[webhook] eventId=${event.id}, status=${response.status.code}, body=$body") *>
          IO.pure(response.status.isSuccess)
      }
    }
  }

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== http4s Webhook + Outbox 发布边界 ===")
      failFirstOrder <- Ref.of[IO, Set[String]](Set("order-100"))
      app = partnerApp(failFirstOrder)
      event = OutboxEvent(1L, "order-created", "order-100", "BTC-101", 2)

      first <- dispatchOnce(app, event)
      _ <- IO.println(s"第一次派发是否成功: $first")

      second <- dispatchOnce(app, event)
      _ <- IO.println(s"第二次派发是否成功: $second")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- Outbox 最终需要穿过真实协议边界，最常见的就是 webhook、消息网关或内部 HTTP API")
      _ <- IO.println("- 只要下游返回失败，就应该把当前事件保留为 pending，而不是假装已经送达")
      _ <- IO.println("- 最后一版会把订单写入和 outbox 插入放进同一个 Doobie 事务，并用测试验证")
    } yield ()
}
