//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.http4s::http4s-core:0.23.33"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.http4s::http4s-client:0.23.33"
//> using dep "org.http4s::http4s-circe:0.23.33"
//> using dep "io.circe::circe-generic:0.14.10"

/**
 * Scala 函数式编程 Demo 54: http4s Client 聚合下游服务
 *
 * 33 号 Demo 已经演示过基础 client 调用，
 * 49 号 Demo 也已经引入了 EitherT 的错误编排。
 *
 * 这一版把两者接起来：
 * - 作为调用方去请求多个下游 HTTP 接口
 * - 用 EitherT 组织失败短路
 * - 把多个响应聚合成一份上层视图
 */
import cats.data.EitherT
import cats.effect.{IO, IOApp}
import io.circe.generic.auto._
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.implicits._

object Http4sClientAggregation extends IOApp.Simple {

  final case class Profile(id: Long, name: String, plan: String)
  final case class Quota(total: Int, used: Int)
  final case class Dashboard(userId: Long, name: String, plan: String, remaining: Int)
  final case class ErrorResponse(error: String)

  sealed trait ServiceError
  case object ProfileNotFound extends ServiceError
  case object SuspendedPlan extends ServiceError
  case object QuotaUnavailable extends ServiceError

  implicit val profileDecoder: EntityDecoder[IO, Profile] = jsonOf[IO, Profile]
  implicit val profileEncoder: EntityEncoder[IO, Profile] = jsonEncoderOf[IO, Profile]
  implicit val quotaDecoder: EntityDecoder[IO, Quota] = jsonOf[IO, Quota]
  implicit val quotaEncoder: EntityEncoder[IO, Quota] = jsonEncoderOf[IO, Quota]
  implicit val dashboardEncoder: EntityEncoder[IO, Dashboard] = jsonEncoderOf[IO, Dashboard]
  implicit val errorDecoder: EntityDecoder[IO, ErrorResponse] = jsonOf[IO, ErrorResponse]
  implicit val errorEncoder: EntityEncoder[IO, ErrorResponse] = jsonEncoderOf[IO, ErrorResponse]

  def explain(error: ServiceError): String =
    error match {
      case ProfileNotFound => "用户资料不存在"
      case SuspendedPlan => "当前套餐已停用"
      case QuotaUnavailable => "配额服务暂时不可用"
    }

  val downstream: HttpApp[IO] = HttpApp[IO] {
    case GET -> Root / "profiles" / "1" => Ok(Profile(1L, "Alice", "pro"))
    case GET -> Root / "profiles" / "2" => Ok(Profile(2L, "Bob", "free"))
    case GET -> Root / "profiles" / "3" => Ok(Profile(3L, "Carol", "suspended"))
    case GET -> Root / "profiles" / _ => NotFound(ErrorResponse("profile not found"))

    case GET -> Root / "quotas" / "1" => Ok(Quota(total = 100, used = 31))
    case GET -> Root / "quotas" / "2" => ServiceUnavailable(ErrorResponse("quota backend timeout"))
    case GET -> Root / "quotas" / "3" => Ok(Quota(total = 20, used = 4))
    case GET -> Root / "quotas" / _ => NotFound(ErrorResponse("quota not found"))
  }

  val client: Client[IO] = Client.fromHttpApp(downstream)

  def fetchProfile(userId: Long): EitherT[IO, ServiceError, Profile] =
    EitherT {
      val request = Request[IO](Method.GET, Uri.unsafeFromString(s"/profiles/$userId"))
      client.run(request).use { response =>
        response.status match {
          case Status.Ok => response.as[Profile].map(Right(_))
          case Status.NotFound => IO.pure(Left(ProfileNotFound))
          case _ => IO.pure(Left(ProfileNotFound))
        }
      }
    }

  def ensurePlanActive(profile: Profile): EitherT[IO, ServiceError, Profile] =
    EitherT.fromEither[IO](
      if (profile.plan == "suspended") Left(SuspendedPlan)
      else Right(profile)
    )

  def fetchQuota(userId: Long): EitherT[IO, ServiceError, Quota] =
    EitherT {
      val request = Request[IO](Method.GET, Uri.unsafeFromString(s"/quotas/$userId"))
      client.run(request).use { response =>
        response.status match {
          case Status.Ok => response.as[Quota].map(Right(_))
          case _ => response.as[ErrorResponse].map(_ => Left(QuotaUnavailable))
        }
      }
    }

  def loadDashboard(userId: Long): EitherT[IO, ServiceError, Dashboard] =
    for {
      profile <- fetchProfile(userId)
      activeProfile <- ensurePlanActive(profile)
      quota <- fetchQuota(userId)
    } yield Dashboard(
      userId = activeProfile.id,
      name = activeProfile.name,
      plan = activeProfile.plan,
      remaining = quota.total - quota.used
    )

  def render(label: String, result: Either[ServiceError, Dashboard]): IO[Unit] =
    result match {
      case Right(dashboard) => IO.println(s"$label -> 成功: $dashboard")
      case Left(error) => IO.println(s"$label -> 失败: ${explain(error)}")
    }

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== 可成功聚合的用户 ===")
      success <- loadDashboard(1L).value
      _ <- render("user-1", success)

      _ <- IO.println("\n=== 配额服务失败 ===")
      quotaFailed <- loadDashboard(2L).value
      _ <- render("user-2", quotaFailed)

      _ <- IO.println("\n=== 套餐已停用 ===")
      suspended <- loadDashboard(3L).value
      _ <- render("user-3", suspended)

      _ <- IO.println("\n=== 资料不存在 ===")
      missing <- loadDashboard(99L).value
      _ <- render("user-99", missing)

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- 聚合服务经常要连续调用多个下游，再把结果拼成一个上层视图")
      _ <- IO.println("- EitherT 很适合把 client 调用中的失败短路组织成清晰的数据流")
      _ <- IO.println("- 这类模式在 BFF、网关聚合、用户主页拼装等场景里非常常见")
    } yield ()
}
