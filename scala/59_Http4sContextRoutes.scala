//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.http4s::http4s-core:0.23.33"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.http4s::http4s-circe:0.23.33"
//> using dep "org.http4s::http4s-server:0.23.33"
//> using dep "io.circe::circe-generic:0.14.10"

/**
 * Scala 函数式编程 Demo 59: http4s ContextMiddleware + ContextRoutes
 *
 * 48 号 Demo 用过 `AuthMiddleware`，53 号 Demo 也补了 client 中间件。
 * 这一版继续看 server 侧另一类很实用的抽象：
 *
 * `ContextMiddleware` + `ContextRoutes`。
 *
 * 它很适合把 requestId、tenant、userId 这类请求上下文先提取出来，
 * 然后让后续路由以类型安全的方式直接拿到上下文对象。
 */
import cats.data.{Kleisli, OptionT}
import cats.effect.{IO, IOApp}
import io.circe.generic.auto._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.ContextMiddleware
import org.typelevel.ci._

object Http4sContextRoutes extends IOApp.Simple {

  final case class RequestContext(requestId: String, tenantId: String, userId: Long)
  final case class ContextResponse(requestId: String, tenantId: String, userId: Long, greeting: String)
  final case class ErrorResponse(error: String)

  implicit val contextEncoder: EntityEncoder[IO, ContextResponse] = jsonEncoderOf[IO, ContextResponse]
  implicit val errorEncoder: EntityEncoder[IO, ErrorResponse] = jsonEncoderOf[IO, ErrorResponse]

  def headerValue(headers: Headers, name: CIString): Option[String] =
    headers.headers.find(_.name == name).map(_.value)

  def buildContext(request: Request[IO]): IO[RequestContext] =
    IO.realTime.map { now =>
      RequestContext(
        requestId = headerValue(request.headers, ci"X-Request-Id").getOrElse(s"generated-${now.toMillis}"),
        tenantId = headerValue(request.headers, ci"X-Tenant-Id").getOrElse("public"),
        userId = headerValue(request.headers, ci"X-User-Id").flatMap(_.toLongOption).getOrElse(0L)
      )
    }

  val middleware = ContextMiddleware(
    Kleisli((request: Request[IO]) => OptionT.liftF(buildContext(request)))
  )

  val contextRoutes: ContextRoutes[RequestContext, IO] = ContextRoutes.of {
    case GET -> Root / "me" as ctx =>
      val greeting = if (ctx.userId == 0L) "hello guest" else s"hello user-${ctx.userId}"
      Ok(ContextResponse(ctx.requestId, ctx.tenantId, ctx.userId, greeting))

    case GET -> Root / "tenants" / tenant / "check" as ctx if ctx.tenantId == tenant =>
      Ok(ContextResponse(ctx.requestId, ctx.tenantId, ctx.userId, s"tenant ${ctx.tenantId} matched"))

    case GET -> Root / "tenants" / tenant / "check" as ctx =>
      Forbidden(ErrorResponse(s"tenant mismatch: request=${ctx.tenantId}, path=$tenant"))
  }

  val app: HttpApp[IO] = middleware(contextRoutes).orNotFound

  def render(label: String, response: Response[IO]): IO[Unit] =
    response.as[String].flatMap(body => IO.println(s"$label -> status=${response.status.code}, body=$body"))

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== 显式携带完整上下文 ===")
      withHeaders <- app(
        Request[IO](Method.GET, uri"/me")
          .putHeaders(
            Header.Raw(ci"X-Request-Id", "trace-9001"),
            Header.Raw(ci"X-Tenant-Id", "team-red"),
            Header.Raw(ci"X-User-Id", "42")
          )
      )
      _ <- render("with-headers", withHeaders)

      _ <- IO.println("\n=== 缺少头部时使用默认上下文 ===")
      defaultCtx <- app(Request[IO](Method.GET, uri"/me"))
      _ <- render("default-context", defaultCtx)

      _ <- IO.println("\n=== tenant 校验失败 ===")
      forbidden <- app(
        Request[IO](Method.GET, uri"/tenants/team-blue/check")
          .putHeaders(
            Header.Raw(ci"X-Request-Id", "trace-9002"),
            Header.Raw(ci"X-Tenant-Id", "team-red"),
            Header.Raw(ci"X-User-Id", "7")
          )
      )
      _ <- render("tenant-mismatch", forbidden)

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- ContextMiddleware 适合先统一提取 requestId、tenant、userId 这类请求上下文")
      _ <- IO.println("- ContextRoutes 让后续路由直接拿到类型安全的上下文，而不是手动层层传 header")
      _ <- IO.println("- 这很适合日志关联、租户隔离、用户上下文注入等场景")
    } yield ()
}
