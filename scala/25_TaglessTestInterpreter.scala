/**
 * Scala 函数式编程 Demo 25: Tagless Final 的测试解释器
 *
 * Tagless Final 一个非常实际的价值是：
 * 同一套业务逻辑，不只能跑在真实解释器里，
 * 也能跑在测试解释器里。
 *
 * 这样你就不需要真的连数据库、真的打日志、真的调外部系统，
 * 也能验证业务规则是不是正确。
 */
object TaglessTestInterpreter extends App {

  trait Monad[F[_]] {
    def pure[A](value: A): F[A]
    def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]
    def map[A, B](fa: F[A])(f: A => B): F[B] = flatMap(fa)(a => pure(f(a)))
  }

  object Monad {
    def apply[F[_]](implicit instance: Monad[F]): Monad[F] = instance
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
    def register(name: String, email: String): F[Either[String, User]] =
      repo.findByEmail(email).flatMap {
        case Some(existing) =>
          logger.info(s"注册失败: ${existing.email}").map(_ => Left(s"邮箱已存在: ${existing.email}"))

        case None =>
          ids.nextId.flatMap { id =>
            val user = User(id, name, email)
            repo.save(user).flatMap { saved =>
              logger.info(s"注册成功: ${saved.email}").map(_ => Right(saved))
            }
          }
      }

    def listUsers: F[List[User]] = repo.allUsers
  }

  case class TestState(nextId: Long, storage: Map[String, User], logs: Vector[String])

  final case class TestIO[+A](run: TestState => (TestState, A)) {
    def map[B](f: A => B): TestIO[B] =
      TestIO { state =>
        val (next, value) = run(state)
        (next, f(value))
      }

    def flatMap[B](f: A => TestIO[B]): TestIO[B] =
      TestIO { state =>
        val (next, value) = run(state)
        f(value).run(next)
      }
  }

  object TestIO {
    def pure[A](value: A): TestIO[A] = TestIO(state => (state, value))
  }

  implicit val testIOMonad: Monad[TestIO] = new Monad[TestIO] {
    def pure[A](value: A): TestIO[A] = TestIO.pure(value)
    def flatMap[A, B](fa: TestIO[A])(f: A => TestIO[B]): TestIO[B] = fa.flatMap(f)
  }

  object TestRepo extends UserRepo[TestIO] {
    def findByEmail(email: String): TestIO[Option[User]] =
      TestIO(state => (state, state.storage.get(email)))

    def save(user: User): TestIO[User] = TestIO { state =>
      val next = state.copy(storage = state.storage + (user.email -> user))
      (next, user)
    }

    def allUsers: TestIO[List[User]] =
      TestIO(state => (state, state.storage.values.toList.sortBy(_.id)))
  }

  object TestIds extends IdGenerator[TestIO] {
    def nextId: TestIO[Long] = TestIO { state =>
      val id = state.nextId
      val next = state.copy(nextId = state.nextId + 1)
      (next, id)
    }
  }

  object TestLogger extends Logger[TestIO] {
    def info(message: String): TestIO[Unit] = TestIO { state =>
      val next = state.copy(logs = state.logs :+ message)
      (next, ())
    }
  }

  val service = new UserService[TestIO](TestRepo, TestIds, TestLogger)

  val program = for {
    first <- service.register("Alice", "alice@example.com")
    duplicate <- service.register("Alice-2", "alice@example.com")
    second <- service.register("Bob", "bob@example.com")
    users <- service.listUsers
  } yield (first, duplicate, second, users)

  val initial = TestState(
    nextId = 100,
    storage = Map.empty,
    logs = Vector.empty
  )

  val (finalState, result) = program.run(initial)

  println("=== Tagless Final 的测试解释器 ===")
  println(s"业务结果: $result")
  println(s"最终用户数: ${finalState.storage.size}")
  println(s"分配到的下一个 id: ${finalState.nextId}")
  println("记录到的日志:")
  finalState.logs.foreach(log => println(s"- $log"))

  println("\n=== 重点理解 ===")
  println("- 同一个 UserService 不需要改代码，就能跑在测试解释器里")
  println("- 测试解释器把‘数据库、日志、ID 分配’都变成了内存中的纯状态变化")
  println("- 这就是 Tagless Final 很适合做可测试架构的原因之一")
}
