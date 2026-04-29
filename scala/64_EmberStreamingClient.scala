//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.13.0"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.http4s::http4s-ember-server:0.23.33"
//> using dep "org.http4s::http4s-ember-client:0.23.33"

/**
 * Scala 函数式编程 Demo 64: Ember Client 消费真实流式响应
 *
 * 63 号 Demo 已经在内存里的 HttpApp 上看过流式响应，
 * 这一版继续推进到真正的本地 server/client 联调：
 *
 * - Ember server 持续输出订单事件
 * - Ember client 边收到边解码边处理
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

object EmberStreamingClient extends IOApp.Simple {

  def orderEventStream(orderId: String): Stream[IO, String] =
    Stream
      .emits(List("queued", "validated", "packed", "shipped"))
      .covary[IO]
      .metered(150.millis)
      .evalTap(status => IO.println(s"[server] 生成事件: $orderId -> $status"))
      .map(status => s"$orderId,$status\n")

  val app: HttpApp[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "orders" / orderId / "events" =>
      Ok(
        orderEventStream(orderId).through(text.utf8.encode),
        Header.Raw(ci"Content-Type", "text/plain; charset=utf-8")
      )
  }.orNotFound

  val portValue = 58241

  def consume(orderId: String): IO[List[String]] =
    EmberClientBuilder.default[IO].build.use { client =>
      val request = Request[IO](Method.GET, Uri.unsafeFromString(s"http://127.0.0.1:$portValue/orders/$orderId/events"))

      client.run(request).use { response =>
        response.body
          .through(text.utf8.decode)
          .through(text.lines)
          .filter(_.nonEmpty)
          .evalTap(line => IO.println(s"[client] 实时收到: $line"))
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
          _ <- IO.println("=== Ember server/client 流式联调 ===")
          _ <- IO.sleep(200.millis)
          lines <- consume("order-9001")
          _ <- IO.println(s"最终消费到的事件序列: $lines")

          _ <- IO.println("\n=== 重点理解 ===")
          _ <- IO.println("- 真实网络下，client 一样可以边收到 body 边解码边处理")
          _ <- IO.println("- 流式接口不必等完整响应结束后再一次性消费")
          _ <- IO.println("- 这很适合事件时间线、导出下载、实时日志和状态推进通知")
        } yield ()
      }
}
