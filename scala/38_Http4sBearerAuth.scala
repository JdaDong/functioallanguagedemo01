//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.http4s::http4s-core:0.23.33"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.http4s::http4s-circe:0.23.33"
//> using dep "io.circe::circe-generic:0.14.10"

/**
 * Scala 函数式编程 Demo 38: http4s Bearer Token 鉴权中间件
 *
 * 29 号 Demo 已经展示过最小中间件，
 * 这一版换成更接近真实服务的 Bearer Token 鉴权流程。
 */
import cats.data.Kleisli
import cats.effect.{IO, IOApp}
import io.circe.generic.auto._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.headers.Authorization
import org.http4s.implicits._

object Http4sBearerAuth extends IOApp.Simple {

  final case class User(id: Long, name: String, role: String)
  final case class ErrorResponse(error: String)
  final case class ProfileResponse(id: Long, name: String, role: String, area: String)

  implicit val errorEncoder: EntityEncoder[IO, ErrorResponse] = jsonEncoderOf
  implicit val profileEncoder: EntityEncoder[IO, ProfileResponse] = jsonEncoderOf

  val tokens: Map[String, User] = Map(
    "token-alice" -> User(1, "Alice", "admin"),
    "token-bob" -> User(2, "Bob", "member")
  )

  def withBearerAuth(app: User => HttpApp[IO]): HttpApp[IO] =
    Kleisli { req =>
      req.headers.get[Authorization] match {
        case Some(Authorization(Credentials.Token(AuthScheme.Bearer, token))) =>
          tokens.get(token) match {
            case Some(user) => app(user)(req)
            case None => Forbidden(ErrorResponse("token 无效或已过期"))
          }

        case _ =>
          IO.pure(Response[IO](status = Status.Unauthorized).withEntity(ErrorResponse("缺少 Bearer Token")))
      }
    }

  def profileApp(currentUser: User): HttpApp[IO] =
    HttpApp[IO] {
      case GET -> Root / "me" =>
        Ok(ProfileResponse(currentUser.id, currentUser.name, currentUser.role, s"private-area-of-${currentUser.name}"))

      case GET -> Root / "admin" =>
        if (currentUser.role == "admin") Ok(ProfileResponse(currentUser.id, currentUser.name, currentUser.role, "admin-dashboard"))
        else Forbidden(ErrorResponse(s"用户 ${currentUser.name} 没有管理员权限"))

      case _ =>
        NotFound(ErrorResponse("route not found"))
    }

  def render(label: String, response: Response[IO]): IO[Unit] =
    response.as[String].flatMap(body => IO.println(s"$label -> status=${response.status.code}, body=$body"))

  val run: IO[Unit] =
    for {
      app <- IO.pure(withBearerAuth(profileApp))

      _ <- IO.println("=== 缺少 token ===")
      noToken <- app(Request[IO](Method.GET, uri"/me"))
      _ <- render("no-token", noToken)

      _ <- IO.println("\n=== 非法 token ===")
      badToken <- app(Request[IO](Method.GET, uri"/me").putHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, "bad-token"))))
      _ <- render("bad-token", badToken)

      _ <- IO.println("\n=== 合法普通用户 ===")
      memberMe <- app(Request[IO](Method.GET, uri"/me").putHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, "token-bob"))))
      _ <- render("member-me", memberMe)
      memberAdmin <- app(Request[IO](Method.GET, uri"/admin").putHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, "token-bob"))))
      _ <- render("member-admin", memberAdmin)

      _ <- IO.println("\n=== 合法管理员 ===")
      adminResp <- app(Request[IO](Method.GET, uri"/admin").putHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, "token-alice"))))
      _ <- render("admin", adminResp)

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- 中间件可以先做鉴权，再把当前用户注入到后续业务处理")
      _ <- IO.println("- 未登录、token 非法、权限不足，通常对应不同的 HTTP 响应")
      _ <- IO.println("- 这类结构会频繁出现在真实 API 服务里")
    } yield ()
}
