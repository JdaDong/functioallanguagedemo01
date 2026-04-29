//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.http4s::http4s-circe:0.23.33"
//> using dep "io.circe::circe-generic:0.14.10"

/**
 * Scala 函数式编程 Demo 44: Tagless Final + http4s 模块装配
 *
 * 30 号 Demo 已经把 Tagless Final 跑在真实 IO 上，
 * 32 号和 43 号 Demo 也分别补了 JSON API 与错误映射。
 *
 * 这一版继续往“完整模块”推进：
 * - algebra 描述能力
 * - Ref 解释器提供状态实现
 * - service 组织业务规则
 * - http4s routes 作为对外协议层
 */
import cats.Monad
import cats.effect.{IO, IOApp, Ref}
import cats.syntax.all._
import io.circe.generic.auto._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.implicits._

object TaglessHttp4sUserModule extends IOApp.Simple {

  final case class User(id: Long, name: String, email: String)
  final case class CreateUserRequest(name: String, email: String)
  final case class ErrorResponse(error: String)

  trait UserRepo[F[_]] {
    def create(name: String, email: String): F[Either[String, User]]
    def findAll: F[List[User]]
  }

  final class UserService[F[_]: Monad](repo: UserRepo[F]) {
    def register(request: CreateUserRequest): F[Either[String, User]] =
      if (request.name.trim.isEmpty) "用户名不能为空".asLeft[User].pure[F]
      else if (!request.email.contains("@")) "邮箱格式不合法".asLeft[User].pure[F]
      else repo.create(request.name.trim, request.email.trim)

    def listUsers: F[List[User]] = repo.findAll
  }

  implicit val createUserDecoder: EntityDecoder[IO, CreateUserRequest] = jsonOf[IO, CreateUserRequest]
  implicit val createUserEncoder: EntityEncoder[IO, CreateUserRequest] = jsonEncoderOf[IO, CreateUserRequest]
  implicit val userEncoder: EntityEncoder[IO, User] = jsonEncoderOf[IO, User]
  implicit val usersEncoder: EntityEncoder[IO, List[User]] = jsonEncoderOf[IO, List[User]]
  implicit val errorEncoder: EntityEncoder[IO, ErrorResponse] = jsonEncoderOf[IO, ErrorResponse]

  def buildModule: IO[HttpApp[IO]] =
    for {
      nextIdRef <- Ref.of[IO, Long](1L)
      usersRef <- Ref.of[IO, Map[Long, User]](Map.empty)
      repo = new UserRepo[IO] {
        override def create(name: String, email: String): IO[Either[String, User]] =
          for {
            existing <- usersRef.get
            result <-
              if (existing.values.exists(_.email.equalsIgnoreCase(email))) {
                IO.pure(Left("邮箱已存在"))
              } else {
                for {
                  id <- nextIdRef.getAndUpdate(_ + 1)
                  user = User(id, name, email)
                  _ <- usersRef.update(_ + (id -> user))
                } yield Right(user)
              }
          } yield result

        override def findAll: IO[List[User]] =
          usersRef.get.map(_.values.toList.sortBy(_.id))
      }
      service = new UserService[IO](repo)
    } yield {
      HttpRoutes.of[IO] {
        case req @ POST -> Root / "users" =>
          req.as[CreateUserRequest].flatMap { payload =>
            service.register(payload).flatMap {
              case Right(user) => Created(user)
              case Left(message) => Conflict(ErrorResponse(message))
            }
          }

        case GET -> Root / "users" =>
          service.listUsers.flatMap(Ok(_))
      }.orNotFound
    }

  def render(label: String, response: Response[IO]): IO[Unit] =
    response.as[String].flatMap(body => IO.println(s"$label -> status=${response.status.code}, body=$body"))

  val run: IO[Unit] =
    for {
      app <- buildModule
      _ <- IO.println("=== 创建第一个用户 ===")
      alice <- app(
        Request[IO](Method.POST, uri"/users")
          .withEntity(CreateUserRequest("Alice", "alice@example.com"))
      )
      _ <- render("alice", alice)

      _ <- IO.println("\n=== 创建重复邮箱用户 ===")
      duplicate <- app(
        Request[IO](Method.POST, uri"/users")
          .withEntity(CreateUserRequest("Alice-2", "alice@example.com"))
      )
      _ <- render("duplicate", duplicate)

      _ <- IO.println("\n=== 创建第二个用户 ===")
      bob <- app(
        Request[IO](Method.POST, uri"/users")
          .withEntity(CreateUserRequest("Bob", "bob@example.com"))
      )
      _ <- render("bob", bob)

      _ <- IO.println("\n=== 查询用户列表 ===")
      list <- app(Request[IO](Method.GET, uri"/users"))
      _ <- render("list", list)

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- algebra / service / routes 分层后，业务能力和协议层可以分别演进")
      _ <- IO.println("- Ref 解释器适合用来做内存版 demo、测试版 repo、轻量原型")
      _ <- IO.println("- 这已经很接近真实函数式服务模块的组织方式")
    } yield ()
}
