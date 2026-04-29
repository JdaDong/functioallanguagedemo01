//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"

/**
 * Scala 函数式编程 Demo 49: EitherT 组织带错误的 effect 流程
 *
 * 真实服务里经常会遇到这种场景：
 * - 流程里每一步都可能失败
 * - 每一步又都带着 effect（查状态、写日志、保存数据）
 *
 * 如果直接用 `IO[Either[E, A]]`，嵌套会很快变深；
 * `EitherT` 就是把这两层上下文叠起来，方便组合。
 */
import cats.data.EitherT
import cats.effect.{IO, IOApp, Ref}
import cats.syntax.all._

object EitherTUserFlow extends IOApp.Simple {

  sealed trait RegistrationError
  case object InvalidName extends RegistrationError
  case object InvalidEmail extends RegistrationError
  case object EmailAlreadyExists extends RegistrationError

  final case class User(id: Long, name: String, email: String)

  def explain(error: RegistrationError): String =
    error match {
      case InvalidName => "用户名不能为空"
      case InvalidEmail => "邮箱格式不合法"
      case EmailAlreadyExists => "邮箱已存在"
    }

  def validateName(name: String): EitherT[IO, RegistrationError, String] =
    EitherT.fromEither[IO](Either.cond(name.trim.nonEmpty, name.trim, InvalidName))

  def validateEmail(email: String): EitherT[IO, RegistrationError, String] =
    EitherT.fromEither[IO](Either.cond(email.contains("@"), email.trim.toLowerCase, InvalidEmail))

  def ensureUnique(email: String, usersRef: Ref[IO, Map[Long, User]]): EitherT[IO, RegistrationError, Unit] =
    EitherT {
      usersRef.get.map { users =>
        if (!users.values.exists(_.email == email)) Right(())
        else Left(EmailAlreadyExists: RegistrationError)
      }
    }

  def saveUser(name: String, email: String, nextIdRef: Ref[IO, Long], usersRef: Ref[IO, Map[Long, User]]): EitherT[IO, RegistrationError, User] =
    EitherT.liftF {
      for {
        id <- nextIdRef.getAndUpdate(_ + 1)
        user = User(id, name, email)
        _ <- usersRef.update(_ + (id -> user))
      } yield user
    }

  def register(name: String, email: String, nextIdRef: Ref[IO, Long], usersRef: Ref[IO, Map[Long, User]]): EitherT[IO, RegistrationError, User] =
    for {
      validName <- validateName(name)
      validEmail <- validateEmail(email)
      _ <- ensureUnique(validEmail, usersRef)
      user <- saveUser(validName, validEmail, nextIdRef, usersRef)
    } yield user

  def render(label: String, result: Either[RegistrationError, User]): IO[Unit] =
    result match {
      case Right(user) => IO.println(s"$label -> 注册成功: $user")
      case Left(error) => IO.println(s"$label -> 注册失败: ${explain(error)}")
    }

  val run: IO[Unit] =
    for {
      nextIdRef <- Ref.of[IO, Long](1L)
      usersRef <- Ref.of[IO, Map[Long, User]](Map.empty)

      _ <- IO.println("=== 第一次注册 ===")
      first <- register("Alice", "alice@example.com", nextIdRef, usersRef).value
      _ <- render("first", first)

      _ <- IO.println("\n=== 重复邮箱注册 ===")
      duplicate <- register("Alice-2", "alice@example.com", nextIdRef, usersRef).value
      _ <- render("duplicate", duplicate)

      _ <- IO.println("\n=== 非法邮箱注册 ===")
      invalidEmail <- register("Bob", "not-an-email", nextIdRef, usersRef).value
      _ <- render("invalid-email", invalidEmail)

      _ <- IO.println("\n=== 最终用户状态 ===")
      users <- usersRef.get
      _ <- IO.println(users.values.toList.sortBy(_.id).mkString("users=", ", ", ""))

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- EitherT 能把 ‘IO + 领域错误’ 两层上下文组合成一条 for 推导式")
      _ <- IO.println("- 这样业务流程可以更像写纯逻辑，而不是不停拆 IO[Either[...]]")
      _ <- IO.println("- 在服务编排、校验链、仓储调用组合里都非常常见")
    } yield ()
}
