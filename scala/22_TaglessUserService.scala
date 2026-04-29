/**
 * Scala 函数式编程 Demo 22: Tagless Final 风格用户服务
 *
 * Tagless Final 的核心不是“语法花哨”，而是：
 * 把业务能力抽象成代数（algebra），
 * 然后让不同的解释器决定它怎么执行。
 *
 * 这样业务逻辑就不会被某个具体实现绑死。
 */
object TaglessUserService extends App {

  final case class IO[+A](unsafeRun: () => A) {
    def map[B](f: A => B): IO[B] = IO(() => f(unsafeRun()))
    def flatMap[B](f: A => IO[B]): IO[B] = IO(() => f(unsafeRun()).unsafeRun())
  }

  object IO {
    def pure[A](value: A): IO[A] = IO(() => value)
    def delay[A](thunk: => A): IO[A] = IO(() => thunk)
  }

  trait Monad[F[_]] {
    def pure[A](value: A): F[A]
    def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]
    def map[A, B](fa: F[A])(f: A => B): F[B] = flatMap(fa)(a => pure(f(a)))
  }

  object Monad {
    def apply[F[_]](implicit instance: Monad[F]): Monad[F] = instance
  }

  implicit val ioMonad: Monad[IO] = new Monad[IO] {
    def pure[A](value: A): IO[A] = IO.pure(value)
    def flatMap[A, B](fa: IO[A])(f: A => IO[B]): IO[B] = fa.flatMap(f)
  }

  implicit final class MonadOps[F[_], A](private val fa: F[A]) extends AnyVal {
    def map[B](f: A => B)(implicit F: Monad[F]): F[B] = F.map(fa)(f)
    def flatMap[B](f: A => F[B])(implicit F: Monad[F]): F[B] = F.flatMap(fa)(f)
  }

  case class User(id: Long, name: String, email: String)

  trait UserRepo[F[_]] {
    def findByEmail(email: String): F[Option[User]]
    def save(user: User): F[User]
    def allUsers: F[List[User]]
  }

  trait IdGenerator[F[_]] {
    def nextId: F[Long]
  }

  trait Logger[F[_]] {
    def info(message: String): F[Unit]
  }

  class UserService[F[_]: Monad](repo: UserRepo[F], ids: IdGenerator[F], logger: Logger[F]) {
    private val F = Monad[F]

    def register(name: String, email: String): F[Either[String, User]] =
      repo.findByEmail(email).flatMap {
        case Some(existing) =>
          logger.info(s"注册失败，邮箱已存在: $email").map(_ => Left(s"邮箱已存在: ${existing.email}"))

        case None =>
          ids.nextId.flatMap { id =>
            val user = User(id, name, email)
            repo.save(user).flatMap { saved =>
              logger.info(s"注册成功: ${saved.email}").map(_ => Right(saved))
            }
          }
      }

    def listUsers: F[List[User]] =
      repo.allUsers
  }

  final class InMemoryUserRepo extends UserRepo[IO] {
    private var storage: Map[String, User] = Map.empty

    def findByEmail(email: String): IO[Option[User]] =
      IO.delay(storage.get(email))

    def save(user: User): IO[User] = IO.delay {
      storage = storage + (user.email -> user)
      user
    }

    def allUsers: IO[List[User]] =
      IO.delay(storage.values.toList.sortBy(_.id))
  }

  final class SequenceIdGenerator(start: Long) extends IdGenerator[IO] {
    private var current = start

    def nextId: IO[Long] = IO.delay {
      current += 1
      current
    }
  }

  object ConsoleLogger extends Logger[IO] {
    def info(message: String): IO[Unit] = IO.delay(println(s"[log] $message"))
  }

  val repo = new InMemoryUserRepo
  val ids = new SequenceIdGenerator(1000)
  val service = new UserService[IO](repo, ids, ConsoleLogger)

  val program = for {
    first <- service.register("Alice", "alice@example.com")
    _ <- IO.delay(println(s"第一次注册: $first"))
    duplicate <- service.register("Another Alice", "alice@example.com")
    _ <- IO.delay(println(s"重复注册: $duplicate"))
    third <- service.register("Bob", "bob@example.com")
    _ <- IO.delay(println(s"第三次注册: $third"))
    users <- service.listUsers
    _ <- IO.delay {
      println("当前用户列表:")
      users.foreach(user => println(s"- $user"))
    }
  } yield ()

  println("=== Tagless Final 风格用户服务 ===")
  program.unsafeRun()

  println("\n=== 重点理解 ===")
  println("- UserService 依赖的是能力接口，而不是某个具体数据库或日志库")
  println("- Repo / IdGenerator / Logger 都可以替换成不同解释器")
  println("- 真正进入 cats-effect 后，F[_] 常常会是 IO、Kleisli 或更通用的 effect")
}
