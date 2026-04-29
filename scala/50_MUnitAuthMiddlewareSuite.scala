//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.http4s::http4s-core:0.23.33"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.http4s::http4s-circe:0.23.33"
//> using dep "org.http4s::http4s-server:0.23.33"
//> using dep "io.circe::circe-generic:0.14.10"
//> using dep "org.typelevel::munit-cats-effect-3:1.0.7"

/**
 * Scala 函数式编程 Demo 50: 测试 AuthMiddleware + AuthedRoutes
 *
 * 45 号 Demo 已经把测试推进到普通路由，
 * 这一版继续补上鉴权边界：
 * 缺 token、普通用户、管理员三种路径都进入自动化断言。
 */
import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import io.circe.generic.auto._
import munit.CatsEffectSuite
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.headers.Authorization
import org.http4s.implicits._
import org.http4s.server.AuthMiddleware

object MUnitAuthMiddlewareSuite {
  sealed trait AuthFailure { def message: String }
  case object MissingOrInvalidToken extends AuthFailure {
    override val message: String = "缺少或非法的 Bearer Token"
  }

  final case class User(id: Long, name: String, role: String)
  final case class ErrorResponse(error: String)
  final case class ProfileResponse(id: Long, name: String, role: String)
}

class MUnitAuthMiddlewareSuite extends CatsEffectSuite {
  import MUnitAuthMiddlewareSuite._

  implicit val errorDecoder: EntityDecoder[IO, ErrorResponse] = jsonOf[IO, ErrorResponse]
  implicit val errorEncoder: EntityEncoder[IO, ErrorResponse] = jsonEncoderOf[IO, ErrorResponse]
  implicit val profileDecoder: EntityDecoder[IO, ProfileResponse] = jsonOf[IO, ProfileResponse]
  implicit val profileEncoder: EntityEncoder[IO, ProfileResponse] = jsonEncoderOf[IO, ProfileResponse]

  val tokens: Map[String, User] = Map(
    "admin-token" -> User(1L, "Alice", "admin"),
    "member-token" -> User(2L, "Bob", "member")
  )

  def buildApp: HttpApp[IO] = {
    val authenticate: Kleisli[IO, Request[IO], Either[AuthFailure, User]] = Kleisli { req =>
      val maybeUser = req.headers.get[Authorization].collect {
        case Authorization(Credentials.Token(AuthScheme.Bearer, token)) => tokens.get(token)
      }.flatten

      IO.pure(maybeUser.toRight(MissingOrInvalidToken: AuthFailure))
    }

    val onFailure: AuthedRoutes[AuthFailure, IO] = Kleisli { authedReq =>
      OptionT.liftF(IO.pure(Response[IO](status = Status.Unauthorized).withEntity(ErrorResponse(authedReq.context.message))))
    }

    val routes: AuthedRoutes[User, IO] = AuthedRoutes.of {
      case GET -> Root / "me" as user =>
        Ok(ProfileResponse(user.id, user.name, user.role))

      case GET -> Root / "admin" as user if user.role == "admin" =>
        Ok(ProfileResponse(user.id, user.name, s"${user.role}-dashboard"))

      case GET -> Root / "admin" as _ =>
        Forbidden(ErrorResponse("需要 admin 权限"))
    }

    val middleware = AuthMiddleware(authenticate, onFailure)
    middleware(routes).orNotFound
  }

  test("缺少 token 时应该返回 401") {
    val app = buildApp
    for {
      response <- app(Request[IO](Method.GET, uri"/me"))
      error <- response.as[ErrorResponse]
    } yield {
      assertEquals(response.status, Status.Unauthorized)
      assertEquals(error.error, "缺少或非法的 Bearer Token")
    }
  }

  test("普通用户访问 /me 应该成功") {
    val app = buildApp
    for {
      response <- app(Request[IO](Method.GET, uri"/me").putHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, "member-token"))))
      profile <- response.as[ProfileResponse]
    } yield {
      assertEquals(response.status, Status.Ok)
      assertEquals(profile.name, "Bob")
      assertEquals(profile.role, "member")
    }
  }

  test("普通用户访问 /admin 应该返回 403") {
    val app = buildApp
    for {
      response <- app(Request[IO](Method.GET, uri"/admin").putHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, "member-token"))))
      error <- response.as[ErrorResponse]
    } yield {
      assertEquals(response.status, Status.Forbidden)
      assertEquals(error.error, "需要 admin 权限")
    }
  }

  test("管理员访问 /admin 应该成功") {
    val app = buildApp
    for {
      response <- app(Request[IO](Method.GET, uri"/admin").putHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, "admin-token"))))
      profile <- response.as[ProfileResponse]
    } yield {
      assertEquals(response.status, Status.Ok)
      assertEquals(profile.name, "Alice")
      assertEquals(profile.role, "admin-dashboard")
    }
  }
}
