//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.http4s::http4s-core:0.23.33"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.http4s::http4s-circe:0.23.33"
//> using dep "org.http4s::http4s-server:0.23.33"
//> using dep "io.circe::circe-generic:0.14.10"

/**
 * Scala 函数式编程 Demo 48: http4s AuthMiddleware + AuthedRoutes
 *
 * 38 号 Demo 手写过 Bearer 鉴权中间件，
 * 这一版继续推进到 http4s 自带的认证抽象：
 * `AuthMiddleware` + `AuthedRoutes`。
 */
import cats.data.{Kleisli, OptionT}
import cats.effect.{IO, IOApp}
import io.circe.generic.auto._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.headers.Authorization
import org.http4s.implicits._
import org.http4s.server.AuthMiddleware

object Http4sAuthMiddleware extends IOApp.Simple {

  sealed trait AuthFailure { def message: String }
  case object MissingOrInvalidToken extends AuthFailure {
    override val message: String = "缺少或非法的 Bearer Token"
  }

  final case class User(id: Long, name: String, role: String)
  final case class ErrorResponse(error: String)
  final case class ProfileResponse(id: Long, name: String, role: String)

  implicit val errorEncoder: EntityEncoder[IO, ErrorResponse] = jsonEncoderOf[IO, ErrorResponse]
  implicit val profileEncoder: EntityEncoder[IO, ProfileResponse] = jsonEncoderOf[IO, ProfileResponse]

  val tokens: Map[String, User] = Map(
    "admin-token" -> User(1L, "Alice", "admin"),
    "member-token" -> User(2L, "Bob", "member")
  )

  val authenticate: Kleisli[IO, Request[IO], Either[AuthFailure, User]] = Kleisli { req =>
    val maybeUser = req.headers.get[Authorization].collect {
      case Authorization(Credentials.Token(AuthScheme.Bearer, token)) => tokens.get(token)
    }.flatten

    IO.pure(maybeUser.toRight(MissingOrInvalidToken: AuthFailure))
  }

  val onFailure: AuthedRoutes[AuthFailure, IO] = Kleisli { authedReq =>
    OptionT.liftF(IO.pure(Response[IO](status = Status.Unauthorized).withEntity(ErrorResponse(authedReq.context.message))))
  }

  val authedRoutes: AuthedRoutes[User, IO] = AuthedRoutes.of {
    case GET -> Root / "me" as user =>
      Ok(ProfileResponse(user.id, user.name, user.role))

    case GET -> Root / "admin" as user if user.role == "admin" =>
      Ok(ProfileResponse(user.id, user.name, s"${user.role}-dashboard"))

    case GET -> Root / "admin" as _ =>
      Forbidden(ErrorResponse("需要 admin 权限"))
  }

  val middleware = AuthMiddleware(authenticate, onFailure)
  val app: HttpApp[IO] = middleware(authedRoutes).orNotFound

  def render(label: String, response: Response[IO]): IO[Unit] =
    response.as[String].flatMap(body => IO.println(s"$label -> status=${response.status.code}, body=$body"))

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== 缺少 token ===")
      missing <- app(Request[IO](Method.GET, uri"/me"))
      _ <- render("missing", missing)

      _ <- IO.println("\n=== 普通用户访问自己的资料 ===")
      member <- app(Request[IO](Method.GET, uri"/me").putHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, "member-token"))))
      _ <- render("member", member)

      _ <- IO.println("\n=== 普通用户访问管理员区域 ===")
      memberAdmin <- app(Request[IO](Method.GET, uri"/admin").putHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, "member-token"))))
      _ <- render("member-admin", memberAdmin)

      _ <- IO.println("\n=== 管理员访问管理员区域 ===")
      admin <- app(Request[IO](Method.GET, uri"/admin").putHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, "admin-token"))))
      _ <- render("admin", admin)

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- AuthMiddleware 把‘认证用户’和‘失败处理’组织成统一入口")
      _ <- IO.println("- AuthedRoutes 能让后续路由直接拿到当前用户上下文")
      _ <- IO.println("- 这比把用户对象手动层层传递更接近真实 http4s 项目结构")
    } yield ()
}
