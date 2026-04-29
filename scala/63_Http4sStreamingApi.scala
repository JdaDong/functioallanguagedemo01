//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.13.0"
//> using dep "org.http4s::http4s-core:0.23.33"
//> using dep "org.http4s::http4s-dsl:0.23.33"

/**
 * Scala 函数式编程 Demo 63: http4s 流式响应 API
 *
 * 前面已经演示过普通 JSON API、client middleware 和下游聚合，
 * 这一版继续推进到“服务端持续产出数据”的场景：
 *
 * 路由不一定一次性返回完整响应，
 * 也可以把 `Stream[IO, Byte]` 作为响应体持续往外推送。
 */
import cats.effect.{IO, IOApp}
import fs2.Stream
import fs2.text
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.typelevel.ci._

import scala.concurrent.duration._

object Http4sStreamingApi extends IOApp.Simple {

  object LimitMatcher extends OptionalQueryParamDecoderMatcher[Int]("limit")

  def tickStream(symbol: String): Stream[IO, String] =
    Stream
      .emits(List("101.2", "101.4", "101.3", "101.6", "101.8"))
      .covary[IO]
      .metered(120.millis)
      .evalTap(price => IO.println(s"[server] 生成 tick: $symbol@$price"))
      .map(price => s"$symbol,$price\n")

  val app: HttpApp[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "ticks" / symbol :? LimitMatcher(limit) =>
      val takeCount = limit.filter(_ > 0).getOrElse(5)
      val body = tickStream(symbol).take(takeCount.toLong).through(text.utf8.encode)
      Ok(body, Header.Raw(ci"Content-Type", "text/plain; charset=utf-8"))
  }.orNotFound

  def decodeLines(response: Response[IO]): IO[List[String]] =
    response.body
      .through(text.utf8.decode)
      .through(text.lines)
      .filter(_.nonEmpty)
      .evalTap(line => IO.println(s"[client] 收到流式行: $line"))
      .compile
      .toList

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== http4s 流式响应：按行持续输出 tick ===")
      response <- app(Request[IO](Method.GET, uri"/ticks/BTC?limit=3"))
      lines <- decodeLines(response)
      _ <- IO.println(s"最终读取结果: $lines")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- http4s 路由不只会返回一次性 JSON，也可以返回持续产出的流式响应")
      _ <- IO.println("- 服务端只需不断生成字节流，客户端就可以边到达边消费")
      _ <- IO.println("- 这很适合行情流、日志流、导出流、进度流等场景")
    } yield ()
}
