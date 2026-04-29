//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.5.7"

/**
 * Scala 函数式编程 Demo 30: 真正的 cats-effect + Tagless Final 解释器
 *
 * 22 号 Demo 和 25 号 Demo 分别展示了 Tagless Final 的业务组织方式、
 * 以及测试解释器的价值。
 *
 * 这一版把业务真正跑到 cats-effect IO 上，
 * 并用 Ref 模拟一个“线程安全的内存仓库”。
 */
import cats.effect.kernel.Sync
import cats.effect.{IO, IOApp, Ref}
import cats.syntax.all._

object TaglessCatsEffect extends IOApp.Simple {

  final case class User(id: Long, name: String, email: String, createdAtMillis: Long)
  final case class AppState(nextId: Long, users: Map[String, User], audits: Vector[String])

  trait UserRepo[F[_]] {
    def findByEmail(email: String): F[Option[User]]
    def save(user: User): F[Unit]
    def all: F[List[User]]
  }

  trait IdGenerator[F[_]] {
    def nextId: F[Long]
  }

  trait Audit[F[_]] {
    def record(message: String): F[Unit]
  }

  final class UserService[F[_]](repo: UserRepo[F], ids: IdGenerator[F], audit: Audit[F])(implicit F: Sync[F]) {
    def register(name: String, email: String): F[Either[String, User]] =
      repo.findByEmail(email).flatMap {
        case Some(existing) =>
          audit.record(s"注册失败: ${existing.email}") *>
            F.pure(Left(s"邮箱已存在: ${existing.email}"))

        case None =>
          for {
            id <- ids.nextId
            now <- F.realTime.map(_.toMillis)
            user = User(id, name, email, now)
            _ <- repo.save(user)
            _ <- audit.record(s"注册成功: ${user.email}")
          } yield Right(user)
      }

    def allUsers: F[List[User]] = repo.all
  }

  def liveRepo(state: Ref[IO, AppState]): UserRepo[IO] = new UserRepo[IO] {
    def findByEmail(email: String): IO[Option[User]] =
      state.get.map(_.users.get(email))

    def save(user: User): IO[Unit] =
      state.update(s => s.copy(users = s.users + (user.email -> user)))

    def all: IO[List[User]] =
      state.get.map(_.users.values.toList.sortBy(_.id))
  }

  def liveIds(state: Ref[IO, AppState]): IdGenerator[IO] = new IdGenerator[IO] {
    def nextId: IO[Long] =
      state.modify { s =>
        val id = s.nextId
        (s.copy(nextId = s.nextId + 1), id)
      }
  }

  def liveAudit(state: Ref[IO, AppState]): Audit[IO] = new Audit[IO] {
    def record(message: String): IO[Unit] =
      state.update(s => s.copy(audits = s.audits :+ message)) *>
        IO.println(s"[audit] $message")
  }

  val run: IO[Unit] =
    for {
      state <- Ref.of[IO, AppState](AppState(nextId = 1000, users = Map.empty, audits = Vector.empty))
      service = new UserService[IO](liveRepo(state), liveIds(state), liveAudit(state))

      _ <- IO.println("=== Tagless Final 跑在真实 IO 上 ===")
      first <- service.register("Alice", "alice@example.com")
      _ <- IO.println(s"第一次注册: $first")
      duplicate <- service.register("Alice-2", "alice@example.com")
      _ <- IO.println(s"重复注册: $duplicate")
      second <- service.register("Bob", "bob@example.com")
      _ <- IO.println(s"第二个用户注册: $second")

      users <- service.allUsers
      snapshot <- state.get

      _ <- IO.println(s"\n当前用户列表: $users")
      _ <- IO.println(s"审计日志条数: ${snapshot.audits.size}")
      _ <- IO.println(s"下一个可分配 id: ${snapshot.nextId}")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- 业务逻辑依旧只依赖代数接口，没有直接绑死具体实现")
      _ <- IO.println("- 解释器这次换成了真实 IO + Ref，更接近实际工程写法")
      _ <- IO.println("- 同一套服务还可以继续替换成数据库解释器、HTTP 客户端解释器、测试解释器")
    } yield ()
}
