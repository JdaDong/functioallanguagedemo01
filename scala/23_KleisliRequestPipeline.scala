/**
 * Scala 函数式编程 Demo 23: Kleisli / ReaderT 风格请求管道
 *
 * Reader 解决的是“依赖环境”的问题，
 * IO 解决的是“描述副作用”的问题。
 *
 * 那如果一个计算既依赖环境，又会产生副作用怎么办？
 * 一个很自然的形式就是：R => F[A]
 * 这正是 Kleisli（也常被叫作 ReaderT）的核心形状。
 */
object KleisliRequestPipeline extends App {

  final case class IO[+A](unsafeRun: () => A) {
    def map[B](f: A => B): IO[B] = IO(() => f(unsafeRun()))
    def flatMap[B](f: A => IO[B]): IO[B] = IO(() => f(unsafeRun()).unsafeRun())
  }

  object IO {
    def pure[A](value: A): IO[A] = IO(() => value)
    def delay[A](thunk: => A): IO[A] = IO(() => thunk)
    def printLine(message: String): IO[Unit] = delay(println(message))
  }

  final case class Kleisli[R, A](run: R => IO[A]) {
    def map[B](f: A => B): Kleisli[R, B] =
      Kleisli(r => run(r).map(f))

    def flatMap[B](f: A => Kleisli[R, B]): Kleisli[R, B] =
      Kleisli(r => run(r).flatMap(a => f(a).run(r)))
  }

  object Kleisli {
    def pure[R, A](value: A): Kleisli[R, A] = Kleisli(_ => IO.pure(value))
    def ask[R]: Kleisli[R, R] = Kleisli(r => IO.pure(r))
    def liftF[R, A](ioa: IO[A]): Kleisli[R, A] = Kleisli(_ => ioa)
  }

  case class RequestContext(token: String, path: String, traceId: String)
  case class User(id: Long, name: String, role: String)
  case class AppEnv(tokens: Map[String, Long], users: Map[Long, User], serviceName: String)

  def log(message: String): Kleisli[AppEnv, Unit] =
    for {
      env <- Kleisli.ask[AppEnv]
      _ <- Kleisli.liftF(IO.printLine(s"[${env.serviceName}] $message"))
    } yield ()

  def authenticate(ctx: RequestContext): Kleisli[AppEnv, Either[String, Long]] =
    Kleisli { env =>
      IO.delay(env.tokens.get(ctx.token).toRight("token 无效或已过期"))
    }

  def fetchUser(userId: Long): Kleisli[AppEnv, Either[String, User]] =
    Kleisli { env =>
      IO.delay(env.users.get(userId).toRight(s"用户不存在: $userId"))
    }

  def authorizeAdmin(user: User): Kleisli[AppEnv, Either[String, User]] =
    Kleisli.pure {
      if (user.role == "admin") Right(user)
      else Left(s"用户 ${user.name} 没有管理员权限")
    }

  def buildResponse(user: User, ctx: RequestContext): Kleisli[AppEnv, String] =
    Kleisli.pure(s"{" +
      s"\"traceId\":\"${ctx.traceId}\"," +
      s"\"path\":\"${ctx.path}\"," +
      s"\"user\":\"${user.name}\"," +
      s"\"role\":\"${user.role}\"" +
      s"}")

  def reject(err: String, message: String): Kleisli[AppEnv, Either[String, String]] =
    log(message).map(_ => Left(err): Either[String, String])

  def adminProfile(ctx: RequestContext): Kleisli[AppEnv, Either[String, String]] = for {
    _ <- log(s"收到请求 path=${ctx.path}, traceId=${ctx.traceId}")
    userIdResult <- authenticate(ctx)
    result <- userIdResult match {
      case Left(err) =>
        reject(err, s"鉴权失败: $err")

      case Right(userId) =>
        for {
          _ <- log(s"鉴权通过，userId=$userId")
          userResult <- fetchUser(userId)
          finalResult <- userResult match {
            case Left(err) =>
              reject(err, s"加载用户失败: $err")

            case Right(user) =>
              for {
                authResult <- authorizeAdmin(user)
                output <- authResult match {
                  case Left(err) =>
                    reject(err, s"权限检查失败: $err")

                  case Right(admin) =>
                    for {
                      _ <- log(s"准备返回管理员信息: ${admin.name}")
                      body <- buildResponse(admin, ctx)
                    } yield (Right(body): Either[String, String])
                }
              } yield output
          }
        } yield finalResult
    }
  } yield result

  val env = AppEnv(
    tokens = Map("token-admin" -> 1L, "token-user" -> 2L),
    users = Map(
      1L -> User(1, "Alice", "admin"),
      2L -> User(2, "Bob", "member")
    ),
    serviceName = "kleisli-demo"
  )

  val adminRequest = RequestContext("token-admin", "/admin/profile", "trace-1001")
  val memberRequest = RequestContext("token-user", "/admin/profile", "trace-1002")
  val invalidRequest = RequestContext("bad-token", "/admin/profile", "trace-1003")

  println("=== Kleisli / ReaderT 风格请求管道 ===")
  println(s"管理员请求结果: ${adminProfile(adminRequest).run(env).unsafeRun()}")
  println()
  println(s"普通用户请求结果: ${adminProfile(memberRequest).run(env).unsafeRun()}")
  println()
  println(s"非法 token 请求结果: ${adminProfile(invalidRequest).run(env).unsafeRun()}")

  println("\n=== 重点理解 ===")
  println("- Kleisli 的形状是 R => F[A]，它把‘依赖环境’和‘effect’合在了一起")
  println("- 这很适合表达请求上下文、配置、连接池等依赖下的业务流程")
  println("- 真正进入 http4s / cats 时，你会频繁看到这种组合思路")
}
