//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.13.0"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.http4s::http4s-ember-server:0.23.33"
//> using dep "org.http4s::http4s-ember-client:0.23.33"

/**
 * Scala 函数式编程 Demo 69: Ember Client 消费真实 SSE
 *
 * 68 号 Demo 已经在内存里的 HttpApp 上看过 SSE 协议，
 * 这一版继续推进到真正的本地 server/client 联调：
 *
 * - server 持续推送订单状态事件
 * - client 通过 HTTP 连接持续接收并解析 `data:` 行
 */
import cats.effect.{IO, IOApp}
import com.comcast.ip4s._
import fs2.Stream
import fs2.text
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.typelevel.ci._

import scala.concurrent.duration._

object EmberSseClient extends IOApp.Simple {

  final case class SseEvent(eventType: String, data: String)

  def encode(event: SseEvent): String =
    s"event: ${event.eventType}\ndata: ${event.data}\n\n"

  def orderEvents(orderId: String): Stream[IO, SseEvent] =
    Stream
      .emits(List("queued", "validated", "packed", "shipped"))
      .covary[IO]
      .metered(150.millis)
      .evalTap(status => IO.println(s"[server] 推送事件: $orderId -> $status"))
      .map(status => SseEvent(eventType = "order-status", data = s"$orderId,$status"))

  val app: HttpApp[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "orders" / orderId / "events" =>
      val body = orderEvents(orderId).map(encode).through(text.utf8.encode)
      Ok(body, Header.Raw(ci"Content-Type", "text/event-stream"))
  }.orNotFound

  val portValue = 58251

  def consume(orderId: String): IO[List[String]] =
    EmberClientBuilder.default[IO].build.use { client =>
      val request = Request[IO](Method.GET, Uri.unsafeFromString(s"http://127.0.0.1:$portValue/orders/$orderId/events"))

      client.run(request).use { response =>
        response.body
          .through(text.utf8.decode)
          .through(text.lines)
          .filter(_.startsWith("data: "))
          .map(_.stripPrefix("data: "))
          .evalTap(value => IO.println(s"[client] 收到 data 事件: $value"))
          .compile
          .toList
      }
    }

  val run: IO[Unit] =
    EmberServerBuilder
      .default[IO]
      .withHost(host"127.0.0.1")
      .withPort(Port.fromInt(portValue).get)
      .withHttpApp(app)
      .build
      .use { _ =>
        for {
          _ <- IO.println("=== Ember server/client SSE 联调 ===")
          _ <- IO.sleep(200.millis)
          values <- consume("order-7001")
          _ <- IO.println(s"最终收到的 data 事件: $values")

          _ <- IO.println("\n=== 重点理解 ===")
          _ <- IO.println("- SSE 在真实网络下也是一个持续响应体，client 可以边到达边解析")
          _ <- IO.println("- 对很多单向推送场景来说，SSE 比 WebSocket 更轻量、更简单")
          _ <- IO.println("- 这很适合订单进度、监控面板、通知流和 AI 输出流式展示")
        } yield ()
      }
}
