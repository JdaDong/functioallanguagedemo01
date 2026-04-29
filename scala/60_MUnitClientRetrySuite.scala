//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.http4s::http4s-core:0.23.33"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.http4s::http4s-client:0.23.33"
//> using dep "org.http4s::http4s-circe:0.23.33"
//> using dep "io.circe::circe-generic:0.14.10"
//> using dep "org.typelevel::munit-cats-effect-3:1.0.7"

/**
 * Scala 函数式编程 Demo 60: 测试 http4s Client 重试策略
 *
 * 55 号 Demo 已经把测试推进到调用方编排，
 * 这一版继续补上更细的 client 行为验证：
 *
 * - 503 时是否真的发生重试
 * - 404 时是否立即停止
 * - 重试耗尽时是否返回明确错误
 */
import cats.effect.{IO, Ref}
import cats.syntax.all._
import io.circe.generic.auto._
import munit.CatsEffectSuite
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.dsl.io._

import scala.concurrent.duration._

object MUnitClientRetrySuite {
  final case class Quote(symbol: String, price: BigDecimal, source: String)
  final case class ErrorResponse(error: String)

  sealed trait FetchError
  case object QuoteNotFound extends FetchError
  final case class RetriesExhausted(status: Status, attempts: Int) extends FetchError

  final case class Environment(
    client: Client[IO],
    attemptsRef: Ref[IO, Map[String, Int]]
  )
}

class MUnitClientRetrySuite extends CatsEffectSuite {
  import MUnitClientRetrySuite._

  implicit val quoteDecoder: EntityDecoder[IO, Quote] = jsonOf[IO, Quote]
  implicit val quoteEncoder: EntityEncoder[IO, Quote] = jsonEncoderOf[IO, Quote]
  implicit val errorDecoder: EntityDecoder[IO, ErrorResponse] = jsonOf[IO, ErrorResponse]
  implicit val errorEncoder: EntityEncoder[IO, ErrorResponse] = jsonEncoderOf[IO, ErrorResponse]

  def buildEnvironment: IO[Environment] =
    for {
      attemptsRef <- Ref.of[IO, Map[String, Int]](Map.empty)
    } yield {
      def nextAttempt(symbol: String): IO[Int] =
        attemptsRef.modify { attempts =>
          val next = attempts.getOrElse(symbol, 0) + 1
          (attempts.updated(symbol, next), next)
        }

      val app: HttpApp[IO] = HttpApp[IO] {
        case GET -> Root / "quotes" / "AAPL" =>
          nextAttempt("AAPL").flatMap { attempt =>
            if (attempt < 3) ServiceUnavailable(ErrorResponse(s"temporary outage, attempt=$attempt"))
            else Ok(Quote("AAPL", BigDecimal("189.52"), s"market-attempt-$attempt"))
          }

        case GET -> Root / "quotes" / "TSLA" =>
          nextAttempt("TSLA") *> ServiceUnavailable(ErrorResponse("upstream still unavailable"))

        case GET -> Root / "quotes" / symbol =>
          nextAttempt(symbol) *> NotFound(ErrorResponse(s"unknown symbol: $symbol"))
      }

      Environment(Client.fromHttpApp(app), attemptsRef)
    }

  def fetchWithRetry(
      client: Client[IO],
      symbol: String,
      maxRetries: Int,
      sleepFor: Int => FiniteDuration
  ): IO[Either[FetchError, Quote]] = {
    def loop(attempt: Int): IO[Either[FetchError, Quote]] = {
      val request = Request[IO](Method.GET, Uri.unsafeFromString(s"/quotes/$symbol"))

      client.run(request).use { response =>
        response.status match {
          case Status.Ok =>
            response.as[Quote].map(Right(_))

          case Status.NotFound =>
            IO.pure(Left(QuoteNotFound))

          case Status.ServiceUnavailable if attempt <= maxRetries =>
            response.as[ErrorResponse].flatMap(_ => IO.sleep(sleepFor(attempt)) *> loop(attempt + 1))

          case status =>
            IO.pure(Left(RetriesExhausted(status, attempt)))
        }
      }
    }

    loop(1)
  }

  test("临时 503 应该经过重试后成功") {
    for {
      env <- buildEnvironment
      result <- fetchWithRetry(env.client, "AAPL", maxRetries = 2, sleepFor = _ => Duration.Zero)
      attempts <- env.attemptsRef.get
    } yield {
      assertEquals(result, Right(Quote("AAPL", BigDecimal("189.52"), "market-attempt-3")))
      assertEquals(attempts.get("AAPL"), Some(3))
    }
  }

  test("404 时不应该继续重试") {
    for {
      env <- buildEnvironment
      result <- fetchWithRetry(env.client, "MSFT", maxRetries = 2, sleepFor = _ => Duration.Zero)
      attempts <- env.attemptsRef.get
    } yield {
      assertEquals(result, Left(QuoteNotFound))
      assertEquals(attempts.get("MSFT"), Some(1))
    }
  }

  test("持续 503 时应该在耗尽重试后失败") {
    for {
      env <- buildEnvironment
      result <- fetchWithRetry(env.client, "TSLA", maxRetries = 2, sleepFor = _ => Duration.Zero)
      attempts <- env.attemptsRef.get
    } yield {
      assertEquals(result, Left(RetriesExhausted(Status.ServiceUnavailable, 3)))
      assertEquals(attempts.get("TSLA"), Some(3))
    }
  }
}
