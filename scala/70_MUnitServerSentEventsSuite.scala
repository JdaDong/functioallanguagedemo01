//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.13.0"
//> using dep "org.http4s::http4s-core:0.23.33"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.typelevel::munit-cats-effect-3:1.0.7"

/**
 * Scala 函数式编程 Demo 70: 测试 SSE 路由
 *
 * 65 号 Demo 已经把测试推进到普通流式路由，
 * 这一版继续补上协议化事件流的测试闭环：
 *
 * - limit 是否真的截断 SSE 数量
 * - data 行是否携带正确 symbol
 * - 未命中路由时是否返回 404
 */
import cats.effect.IO
import fs2.Stream
import fs2.text
import munit.CatsEffectSuite
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.typelevel.ci._

import scala.concurrent.duration._

final case class SseEnvelope(id: String, eventType: String, data: String)

class MUnitServerSentEventsSuite extends CatsEffectSuite {

  object LimitMatcher extends OptionalQueryParamDecoderMatcher[Int]("limit")

  def encode(event: SseEnvelope): String =
    s"id: ${event.id}\nevent: ${event.eventType}\ndata: ${event.data}\n\n"

  def quoteEvents(symbol: String): Stream[IO, SseEnvelope] =
    Stream
      .emits(List("101.2", "101.4", "101.3", "101.6"))
      .covary[IO]
      .zipWithIndex
      .map { case (price, index) =>
        SseEnvelope(
          id = (index + 1).toString,
          eventType = "quote",
          data = s"$symbol,$price"
        )
      }

  val app: HttpApp[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "sse" / "quotes" / symbol :? LimitMatcher(limit) =>
      val body = quoteEvents(symbol)
        .take(limit.filter(_ > 0).getOrElse(4).toLong)
        .map(encode)
        .through(text.utf8.encode)

      Ok(body, Header.Raw(ci"Content-Type", "text/event-stream"))
  }.orNotFound

  def dataLines(response: Response[IO]): IO[List[String]] =
    response.body
      .through(text.utf8.decode)
      .through(text.lines)
      .filter(_.startsWith("data: "))
      .map(_.stripPrefix("data: "))
      .compile
      .toList

  test("limit 查询参数应该截断 SSE 事件数量") {
    for {
      response <- app(Request[IO](Method.GET, uri"/sse/quotes/BTC?limit=2"))
      values <- dataLines(response)
    } yield assertEquals(values, List("BTC,101.2", "BTC,101.4"))
  }

  test("不同 symbol 应该得到不同 data 前缀") {
    for {
      response <- app(Request[IO](Method.GET, uri"/sse/quotes/ETH?limit=3"))
      values <- dataLines(response)
    } yield assertEquals(values, List("ETH,101.2", "ETH,101.4", "ETH,101.3"))
  }

  test("未命中 SSE 路由时应该返回 404") {
    app(Request[IO](Method.GET, uri"/sse/unknown")).map { response =>
      assertEquals(response.status, Status.NotFound)
    }
  }
}
