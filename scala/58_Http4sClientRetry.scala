//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.http4s::http4s-core:0.23.33"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.http4s::http4s-client:0.23.33"
//> using dep "org.http4s::http4s-circe:0.23.33"
//> using dep "io.circe::circe-generic:0.14.10"

/**
 * Scala 函数式编程 Demo 58: http4s Client 重试与退避
 *
 * 24 号 Demo 用标准库演示过 Retry / Backoff 思想，
 * 这一版把它推进到真实的 http4s client 场景：
 *
 * - 503 这类暂时性失败可以重试
 * - 404 这类确定性失败通常不该重试
 * - 重试次数和退避策略应该从业务中独立出来
 */
import cats.effect.{IO, IOApp, Ref, Temporal}
import io.circe.generic.auto._
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.implicits._

import scala.concurrent.duration._

object Http4sClientRetry extends IOApp.Simple {

  final case class Quote(symbol: String, price: BigDecimal, source: String)
  final case class ErrorResponse(error: String)

  sealed trait FetchError
  case object QuoteNotFound extends FetchError
  final case class RetriesExhausted(status: Status, attempts: Int) extends FetchError

  implicit val quoteDecoder: EntityDecoder[IO, Quote] = jsonOf[IO, Quote]
  implicit val quoteEncoder: EntityEncoder[IO, Quote] = jsonEncoderOf[IO, Quote]
  implicit val errorDecoder: EntityDecoder[IO, ErrorResponse] = jsonOf[IO, ErrorResponse]
  implicit val errorEncoder: EntityEncoder[IO, ErrorResponse] = jsonEncoderOf[IO, ErrorResponse]

  def explain(error: FetchError): String =
    error match {
      case QuoteNotFound => "行情不存在，不应继续重试"
      case RetriesExhausted(status, attempts) => s"重试耗尽，最终状态=${status.code}，总尝试=$attempts"
    }

  def buildClient(attemptsRef: Ref[IO, Map[String, Int]]): Client[IO] = {
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

    Client.fromHttpApp(app)
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
            response.as[ErrorResponse].flatMap { err =>
              val waitFor = sleepFor(attempt)
              IO.println(s"[retry] $symbol attempt=$attempt failed: ${err.error}, ${waitFor.toMillis}ms 后重试") *>
                IO.sleep(waitFor) *>
                loop(attempt + 1)
            }

          case status =>
            IO.pure(Left(RetriesExhausted(status, attempt)))
        }
      }
    }

    loop(attempt = 1)
  }

  val run: IO[Unit] =
    for {
      attemptsRef <- Ref.of[IO, Map[String, Int]](Map.empty)
      client = buildClient(attemptsRef)

      _ <- IO.println("=== 暂时失败后重试成功 ===")
      aapl <- fetchWithRetry(client, "AAPL", maxRetries = 2, sleepFor = attempt => (attempt * 100).millis)
      _ <- IO.println(s"AAPL -> $aapl")

      _ <- IO.println("\n=== 确定性 404，不应重试 ===")
      msft <- fetchWithRetry(client, "MSFT", maxRetries = 2, sleepFor = attempt => (attempt * 100).millis)
      _ <- IO.println(s"MSFT -> ${msft.left.map(explain)}")

      _ <- IO.println("\n=== 持续 503，最终耗尽重试 ===")
      tsla <- fetchWithRetry(client, "TSLA", maxRetries = 2, sleepFor = attempt => (attempt * 100).millis)
      _ <- IO.println(s"TSLA -> ${tsla.left.map(explain)}")

      attempts <- attemptsRef.get
      _ <- IO.println(s"\n调用次数统计: $attempts")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- client 重试要区分可恢复失败和确定性失败")
      _ <- IO.println("- 重试次数、退避时间和失败判定应该从业务逻辑里抽离出来")
      _ <- IO.println("- 真实下游调用里，503 / timeout / reset 常常适合进入这一层策略")
    } yield ()
}
