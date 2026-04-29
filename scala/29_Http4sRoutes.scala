//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.5.7"
//> using dep "org.http4s::http4s-core:0.23.30"
//> using dep "org.http4s::http4s-dsl:0.23.30"

/**
 * Scala 函数式编程 Demo 29: 真正的 http4s Routes / HttpApp / Middleware
 *
 * 21 号 Demo 我们手写过一个最小 http4s 风格模型。
 * 这一版直接切到真实 http4s，但仍然不真正起服务器，
 * 而是直接把 Request 喂给 HttpApp，观察响应结果。
 */
import cats.data.Kleisli
import cats.effect.{IO, IOApp, Ref}
import cats.syntax.all._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.typelevel.ci._

object Http4sRoutes extends IOApp.Simple {

  final case class User(id: Long, name: String)

  object NameParam extends QueryParamDecoderMatcher[String]("name")

  def withLogging(app: HttpApp[IO]): HttpApp[IO] =
    Kleisli { req =>
      IO.println(s"收到请求: ${req.method.name} ${req.uri.renderString}") *>
        app(req).flatTap(resp => IO.println(s"返回状态: ${resp.status.code}"))
    }

  def withRequestId(app: HttpApp[IO]): HttpApp[IO] =
    Kleisli { req =>
      val existing = req.headers.headers.find(_.name == ci"X-Request-Id").map(_.value)

      val requestIdIO = existing match {
        case Some(value) => IO.pure(value)
        case None => IO.realTime.map(now => s"generated-${now.toMillis}")
      }

      requestIdIO.flatMap { requestId =>
        app(req).map(_.putHeaders(Header.Raw(ci"X-Request-Id", requestId)))
      }
    }

  def routes(store: Ref[IO, Map[Long, User]]): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "health" =>
        Ok("ok")

      case POST -> Root / "users" :? NameParam(name) =>
        store.modify { users =>
          val nextId = users.keys.foldLeft(0L)(math.max) + 1
          val created = User(nextId, name)
          (users + (nextId -> created), created)
        }.flatMap(user => Created(s"created user: ${user.id}:${user.name}"))

      case GET -> Root / "users" / id =>
        id.toLongOption match {
          case None => BadRequest("user id 必须是数字")
          case Some(userId) =>
            store.get.flatMap { users =>
              users.get(userId) match {
                case Some(user) => Ok(s"user: ${user.id}:${user.name}")
                case None => NotFound(s"user not found: $userId")
              }
            }
        }
    }

  def render(label: String, response: Response[IO]): IO[Unit] =
    for {
      body <- response.bodyText.compile.string
      requestId = response.headers.headers.find(_.name == ci"X-Request-Id").map(_.value).getOrElse("-")
      _ <- IO.println(s"$label -> status=${response.status.code}, requestId=$requestId, body=$body")
    } yield ()

  val run: IO[Unit] =
    for {
      store <- Ref.of[IO, Map[Long, User]](Map.empty)
      app = withRequestId(withLogging(routes(store).orNotFound))

      _ <- IO.println("=== http4s Routes 直接运行 ===")
      healthResp <- app(Request[IO](Method.GET, uri"/health").putHeaders(Header.Raw(ci"X-Request-Id", "trace-2001")))
      _ <- render("health", healthResp)

      createResp <- app(Request[IO](Method.POST, uri"/users?name=Alice"))
      _ <- render("create-user", createResp)

      getResp <- app(Request[IO](Method.GET, uri"/users/1"))
      _ <- render("get-user", getResp)

      missingResp <- app(Request[IO](Method.GET, uri"/users/42"))
      _ <- render("missing-user", missingResp)

      invalidResp <- app(Request[IO](Method.GET, uri"/users/not-a-number"))
      _ <- render("invalid-user-id", invalidResp)

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- HttpRoutes 本质上还是 Request => F[Response] 这类函数形状")
      _ <- IO.println("- orNotFound 把可选路由提升成完整 HttpApp")
      _ <- IO.println("- Middleware 只是包一层前后处理逻辑，本质仍然是函数组合")
    } yield ()
}
