//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.http4s::http4s-core:0.23.33"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.http4s::http4s-client:0.23.33"

/**
 * Scala 函数式编程 Demo 53: http4s Client 中间件与请求上下文透传
 *
 * 29 号 Demo 看过 server 侧 middleware，
 * 33 号 Demo 也看过 client 的基本调用。
 *
 * 这一版继续补一个真实项目很常见的话题：
 * client 侧也可以像 server 一样包中间件，用来做日志、traceId 注入、统一头部透传。
 */
import cats.effect.{IO, IOApp, Resource}
import org.http4s._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.typelevel.ci._

object Http4sClientMiddleware extends IOApp.Simple {

  def headerValue(headers: Headers, name: CIString): Option[String] =
    headers.headers.find(_.name == name).map(_.value)

  val downstream: HttpApp[IO] = HttpApp[IO] {
    case req @ GET -> Root / "profiles" / userId =>
      val traceId = headerValue(req.headers, ci"X-Request-Id").getOrElse("missing")
      Ok(s"downstream profile=$userId traceId=$traceId")
  }

  def withRequestId(client: Client[IO]): Client[IO] =
    Client { req =>
      val existing = headerValue(req.headers, ci"X-Request-Id")

      Resource.eval {
        existing match {
          case Some(value) => IO.pure(value)
          case None => IO.realTime.map(now => s"client-${now.toMillis}")
        }
      }.flatMap { requestId =>
        val enriched =
          if (existing.isDefined) req
          else req.putHeaders(Header.Raw(ci"X-Request-Id", requestId))

        client.run(enriched)
      }
    }

  def withLogging(client: Client[IO]): Client[IO] =
    Client { req =>
      Resource.eval(IO.println(s"[client] 发送请求: ${req.method.name} ${req.uri.renderString}"))
        .flatMap(_ => client.run(req))
        .flatMap(response =>
          Resource.eval(IO.println(s"[client] 收到响应: ${response.status.code}"))
            .map(_ => response)
        )
    }

  val client: Client[IO] = withLogging(withRequestId(Client.fromHttpApp(downstream)))

  def fetchProfile(request: Request[IO]): IO[String] =
    client.expect[String](request)

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== 未显式传 traceId，由 client middleware 自动注入 ===")
      autoTrace <- fetchProfile(Request[IO](Method.GET, uri"/profiles/42"))
      _ <- IO.println(autoTrace)

      _ <- IO.println("\n=== 已携带 traceId 时，client middleware 保留原值 ===")
      manualTrace <- fetchProfile(
        Request[IO](Method.GET, uri"/profiles/7")
          .putHeaders(Header.Raw(ci"X-Request-Id", "trace-fixed-7001"))
      )
      _ <- IO.println(manualTrace)

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- http4s client 一样可以包 middleware，用来做日志、打点、头部注入")
      _ <- IO.println("- traceId、租户信息、认证头这些上下文，常常都要在 client 侧统一透传")
      _ <- IO.println("- server 和 client 两侧的中间件，本质都还是围绕 Request / Response 的函数组合")
    } yield ()
}
