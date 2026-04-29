//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.http4s::http4s-core:0.23.33"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.http4s::http4s-client:0.23.33"
//> using dep "org.http4s::http4s-circe:0.23.33"
//> using dep "io.circe::circe-generic:0.14.10"
//> using dep "org.typelevel::munit-cats-effect-3:1.0.7"

/**
 * Scala 函数式编程 Demo 55: 测试下游聚合编排
 *
 * 前面已经测过普通路由和鉴权边界，
 * 这一版继续补上“作为调用方”的测试视角：
 * - 多个下游 HTTP 接口的编排结果是否正确
 * - EitherT 是否真的在错误时短路
 * - 不同失败场景是否被映射成明确的领域错误
 */
import cats.data.EitherT
import cats.effect.{IO, Ref}
import io.circe.generic.auto._
import munit.CatsEffectSuite
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.dsl.io._

object MUnitClientOrchestrationSuite {
  final case class Profile(id: Long, name: String, plan: String)
  final case class Quota(total: Int, used: Int)
  final case class Dashboard(userId: Long, name: String, plan: String, remaining: Int)
  final case class ErrorResponse(error: String)
  final case class Environment(
    loadDashboard: Long => IO[Either[ServiceError, Dashboard]],
    quotaCalls: Ref[IO, Int]
  )

  sealed trait ServiceError
  case object ProfileNotFound extends ServiceError
  case object SuspendedPlan extends ServiceError
  case object QuotaUnavailable extends ServiceError
}

class MUnitClientOrchestrationSuite extends CatsEffectSuite {
  import MUnitClientOrchestrationSuite._

  implicit val profileDecoder: EntityDecoder[IO, Profile] = jsonOf[IO, Profile]
  implicit val profileEncoder: EntityEncoder[IO, Profile] = jsonEncoderOf[IO, Profile]
  implicit val quotaDecoder: EntityDecoder[IO, Quota] = jsonOf[IO, Quota]
  implicit val quotaEncoder: EntityEncoder[IO, Quota] = jsonEncoderOf[IO, Quota]
  implicit val dashboardDecoder: EntityDecoder[IO, Dashboard] = jsonOf[IO, Dashboard]
  implicit val dashboardEncoder: EntityEncoder[IO, Dashboard] = jsonEncoderOf[IO, Dashboard]
  implicit val errorDecoder: EntityDecoder[IO, ErrorResponse] = jsonOf[IO, ErrorResponse]
  implicit val errorEncoder: EntityEncoder[IO, ErrorResponse] = jsonEncoderOf[IO, ErrorResponse]

  def buildEnvironment: IO[Environment] =
    for {
      quotaCalls <- Ref.of[IO, Int](0)
    } yield {
      val downstream: HttpApp[IO] = HttpApp[IO] {
        case GET -> Root / "profiles" / "1" => Ok(Profile(1L, "Alice", "pro"))
        case GET -> Root / "profiles" / "2" => Ok(Profile(2L, "Bob", "free"))
        case GET -> Root / "profiles" / "3" => Ok(Profile(3L, "Carol", "suspended"))
        case GET -> Root / "profiles" / _ => NotFound(ErrorResponse("profile not found"))

        case GET -> Root / "quotas" / "1" =>
          quotaCalls.update(_ + 1) *> Ok(Quota(total = 100, used = 31))

        case GET -> Root / "quotas" / "2" =>
          quotaCalls.update(_ + 1) *> ServiceUnavailable(ErrorResponse("quota backend timeout"))

        case GET -> Root / "quotas" / "3" =>
          quotaCalls.update(_ + 1) *> Ok(Quota(total = 20, used = 4))

        case GET -> Root / "quotas" / _ =>
          quotaCalls.update(_ + 1) *> NotFound(ErrorResponse("quota not found"))
      }

      val client = Client.fromHttpApp(downstream)

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

      def loadDashboard(userId: Long): IO[Either[ServiceError, Dashboard]] =
        (for {
          profile <- fetchProfile(userId)
          activeProfile <- ensurePlanActive(profile)
          quota <- fetchQuota(userId)
        } yield Dashboard(
          userId = activeProfile.id,
          name = activeProfile.name,
          plan = activeProfile.plan,
          remaining = quota.total - quota.used
        )).value

      Environment(loadDashboard, quotaCalls)
    }

  test("可用用户应该能成功聚合 dashboard") {
    for {
      env <- buildEnvironment
      result <- env.loadDashboard(1L)
      calls <- env.quotaCalls.get
    } yield {
      assertEquals(result, Right(Dashboard(1L, "Alice", "pro", 69)))
      assertEquals(calls, 1)
    }
  }

  test("资料不存在时应该直接短路，不请求 quota") {
    for {
      env <- buildEnvironment
      result <- env.loadDashboard(99L)
      calls <- env.quotaCalls.get
    } yield {
      assertEquals(result, Left(ProfileNotFound))
      assertEquals(calls, 0)
    }
  }

  test("套餐停用时应该短路，不请求 quota") {
    for {
      env <- buildEnvironment
      result <- env.loadDashboard(3L)
      calls <- env.quotaCalls.get
    } yield {
      assertEquals(result, Left(SuspendedPlan))
      assertEquals(calls, 0)
    }
  }

  test("quota 服务失败时应该返回领域错误") {
    for {
      env <- buildEnvironment
      result <- env.loadDashboard(2L)
      calls <- env.quotaCalls.get
    } yield {
      assertEquals(result, Left(QuotaUnavailable))
      assertEquals(calls, 1)
    }
  }
}
