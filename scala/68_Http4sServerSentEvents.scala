//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.13.0"
//> using dep "org.http4s::http4s-core:0.23.33"
//> using dep "org.http4s::http4s-dsl:0.23.33"

/**
 * Scala 函数式编程 Demo 68: http4s Server-Sent Events
 *
 * 63 号 Demo 已经演示过普通流式响应，
 * 这一版继续推进到更贴近前端实时订阅的协议：SSE。
 *
 * 相比只返回纯文本流，SSE 会把事件名称、事件 id、数据体组织成更明确的协议格式。
 */
import cats.effect.{IO, IOApp}
import fs2.Stream
import fs2.text
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.typelevel.ci._

import scala.concurrent.duration._

object Http4sServerSentEvents extends IOApp.Simple {

  final case class SseEvent(id: String, eventType: String, data: String)

  object LimitMatcher extends OptionalQueryParamDecoderMatcher[Int]("limit")

  def encode(event: SseEvent): String =
    s"id: ${event.id}\nevent: ${event.eventType}\ndata: ${event.data}\n\n"

  def quoteEvents(symbol: String): Stream[IO, SseEvent] =
    Stream
      .emits(List("101.2", "101.4", "101.3", "101.6", "101.8"))
      .covary[IO]
      .metered(120.millis)
      .zipWithIndex
      .evalTap { case (price, index) => IO.println(s"[server] 生成 SSE: ${index + 1} -> $symbol@$price") }
      .map { case (price, index) =>
        SseEvent(
          id = (index + 1).toString,
          eventType = "quote",
          data = s"$symbol,$price"
        )
      }

  val app: HttpApp[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "sse" / "quotes" / symbol :? LimitMatcher(limit) =>
      val body = quoteEvents(symbol)
        .take(limit.filter(_ > 0).getOrElse(5).toLong)
        .map(encode)
        .through(text.utf8.encode)

      Ok(body, Header.Raw(ci"Content-Type", "text/event-stream"))
  }.orNotFound

  def decode(response: Response[IO]): IO[List[String]] =
    response.body
      .through(text.utf8.decode)
      .through(text.lines)
      .filter(_.nonEmpty)
      .evalTap(line => IO.println(s"[client] 读到协议行: $line"))
      .compile
      .toList

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== http4s SSE：结构化事件流 ===")
      response <- app(Request[IO](Method.GET, uri"/sse/quotes/BTC?limit=2"))
      lines <- decode(response)
      _ <- IO.println(s"最终协议输出: $lines")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- SSE 会把 data、event、id 这些元信息一起组织进响应体")
      _ <- IO.println("- 它很适合单向实时推送：通知、价格流、任务进度、日志尾流")
      _ <- IO.println("- 相比普通文本流，SSE 更像一个轻量级、浏览器友好的事件协议")
    } yield ()
}
