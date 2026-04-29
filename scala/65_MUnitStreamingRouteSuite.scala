//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.13.0"
//> using dep "org.http4s::http4s-core:0.23.33"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.typelevel::munit-cats-effect-3:1.0.7"

/**
 * Scala 函数式编程 Demo 65: 测试 http4s 流式路由
 *
 * 60 号 Demo 已经把测试推进到 client retry，
 * 这一版继续补“流式响应”本身的自动化验证：
 *
 * - limit 查询参数是否真的截断输出
 * - 不同 symbol 是否能得到不同的数据前缀
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

class MUnitStreamingRouteSuite extends CatsEffectSuite {

  object LimitMatcher extends OptionalQueryParamDecoderMatcher[Int]("limit")

  def tickStream(symbol: String): Stream[IO, String] =
    Stream
      .emits(List("101.2", "101.4", "101.3", "101.6"))
      .covary[IO]
      .metered(5.millis)
      .map(price => s"$symbol,$price\n")

  val app: HttpApp[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "ticks" / symbol :? LimitMatcher(limit) =>
      val takeCount = limit.filter(_ > 0).getOrElse(4)
      Ok(
        tickStream(symbol).take(takeCount.toLong).through(text.utf8.encode),
        Header.Raw(ci"Content-Type", "text/plain; charset=utf-8")
      )
  }.orNotFound

  def decodeLines(response: Response[IO]): IO[List[String]] =
    response.body
      .through(text.utf8.decode)
      .through(text.lines)
      .filter(_.nonEmpty)
      .compile
      .toList

  test("limit 查询参数应该截断流输出") {
    for {
      response <- app(Request[IO](Method.GET, uri"/ticks/BTC?limit=2"))
      lines <- decodeLines(response)
    } yield assertEquals(lines, List("BTC,101.2", "BTC,101.4"))
  }

  test("不同 symbol 应该得到不同前缀的数据") {
    for {
      response <- app(Request[IO](Method.GET, uri"/ticks/ETH?limit=3"))
      lines <- decodeLines(response)
    } yield assertEquals(lines, List("ETH,101.2", "ETH,101.4", "ETH,101.3"))
  }

  test("未命中路由时应该返回 404") {
    app(Request[IO](Method.GET, uri"/unknown")).map { response =>
      assertEquals(response.status, Status.NotFound)
    }
  }
}
