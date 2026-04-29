//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.http4s::http4s-core:0.23.33"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.http4s::http4s-circe:0.23.33"
//> using dep "io.circe::circe-generic:0.14.10"
//> using dep "io.circe::circe-parser:0.14.10"

/**
 * Scala 函数式编程 Demo 32: http4s + circe 的真实 JSON API
 *
 * 29 号 Demo 已经展示了真实 `HttpRoutes` 的基本形状，
 * 这一版继续往前一步：
 * - 解析 JSON 请求体
 * - 返回 JSON 响应体
 * - 把业务校验错误映射成 HTTP 状态码
 */
import cats.effect.{IO, IOApp, Ref}
import cats.syntax.all._
import io.circe.generic.auto._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.implicits._

object Http4sJsonApi extends IOApp.Simple {

  final case class User(id: Long, name: String, email: String)
  final case class CreateUserRequest(name: String, email: String)
  final case class ErrorResponse(error: String)

  implicit val createUserDecoder: EntityDecoder[IO, CreateUserRequest] = jsonOf
  implicit val createUserEncoder: EntityEncoder[IO, CreateUserRequest] = jsonEncoderOf
  implicit val userEncoder: EntityEncoder[IO, User] = jsonEncoderOf
  implicit val usersEncoder: EntityEncoder[IO, List[User]] = jsonEncoderOf
  implicit val errorEncoder: EntityEncoder[IO, ErrorResponse] = jsonEncoderOf

  def validate(req: CreateUserRequest): Either[String, CreateUserRequest] =
    if (req.name.trim.isEmpty) Left("name 不能为空")
    else if (!req.email.contains("@")) Left("email 格式不合法")
    else Right(req.copy(name = req.name.trim, email = req.email.trim.toLowerCase))

  def routes(store: Ref[IO, Map[String, User]]): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case req @ POST -> Root / "users" =>
        for {
          payload <- req.as[CreateUserRequest]
          response <- validate(payload) match {
            case Left(err) =>
              BadRequest(ErrorResponse(err))

            case Right(valid) =>
              store.modify { users =>
                users.get(valid.email) match {
                  case Some(existing) =>
                    (users, Left(existing.email): Either[String, User])
                  case None =>
                    val nextId = users.values.map(_.id).foldLeft(0L)(math.max) + 1
                    val created = User(nextId, valid.name, valid.email)
                    (users + (created.email -> created), Right(created): Either[String, User])
                }
              }.flatMap {
                case Left(email) => Conflict(ErrorResponse(s"邮箱已存在: $email"))
                case Right(user) => Created(user)
              }
          }
        } yield response

      case GET -> Root / "users" =>
        store.get.map(_.values.toList.sortBy(_.id)).flatMap(Ok(_))
    }

  def render(label: String, response: Response[IO]): IO[Unit] =
    response.as[String].flatMap { body =>
      IO.println(s"$label -> status=${response.status.code}, body=$body")
    }

  val run: IO[Unit] =
    for {
      store <- Ref.of[IO, Map[String, User]](Map.empty)
      app = routes(store).orNotFound

      _ <- IO.println("=== 创建合法用户 ===")
      createOk <- app(
        Request[IO](Method.POST, uri"/users")
          .withEntity(CreateUserRequest(" Alice ", "Alice@Example.com"))
      )
      _ <- render("create-ok", createOk)

      _ <- IO.println("\n=== 创建重复用户 ===")
      createDup <- app(
        Request[IO](Method.POST, uri"/users")
          .withEntity(CreateUserRequest("Alice-2", "alice@example.com"))
      )
      _ <- render("create-duplicate", createDup)

      _ <- IO.println("\n=== 创建非法用户 ===")
      createInvalid <- app(
        Request[IO](Method.POST, uri"/users")
          .withEntity(CreateUserRequest("", "bad-email"))
      )
      _ <- render("create-invalid", createInvalid)

      _ <- IO.println("\n=== 查询用户列表 ===")
      listResp <- app(Request[IO](Method.GET, uri"/users"))
      _ <- render("list-users", listResp)

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- jsonOf / jsonEncoderOf 把 circe codec 接进 http4s 请求响应层")
      _ <- IO.println("- 业务校验错误、冲突错误可以自然映射到不同 HTTP 状态码")
      _ <- IO.println("- JSON API 的主体仍然是纯数据 + effect + 函数组合")
    } yield ()
}
