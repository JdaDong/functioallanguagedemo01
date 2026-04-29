//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.http4s::http4s-core:0.23.33"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.http4s::http4s-circe:0.23.33"
//> using dep "io.circe::circe-generic:0.14.10"

/**
 * Scala 函数式编程 Demo 88: http4s Idempotency-Key 写接口
 *
 * 86 和 87 已经把“同进程并发重复”和“流里的重复投递”讲清楚了，
 * 这一版继续把幂等拉到 HTTP 边界：客户端重试同一个 POST 时，
 * 服务端应该怎么识别并复用首个结果？
 */
import cats.effect.{IO, IOApp, Ref}
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.typelevel.ci._

object Http4sIdempotencyKey extends IOApp.Simple {

  final case class CreateReservationRequest(sku: String, units: Int)
  final case class ReservationResponse(reservationId: Long, sku: String, units: Int, replayed: Boolean)
  final case class ErrorResponse(error: String)
  final case class CachedEntry(fingerprint: String, response: ReservationResponse)

  implicit val createReservationDecoder: EntityDecoder[IO, CreateReservationRequest] =
    jsonOf[IO, CreateReservationRequest]

  implicit val reservationEncoder: EntityEncoder[IO, ReservationResponse] =
    jsonEncoderOf[IO, ReservationResponse]

  implicit val errorEncoder: EntityEncoder[IO, ErrorResponse] =
    jsonEncoderOf[IO, ErrorResponse]

  def headerValue(headers: Headers, name: CIString): Option[String] =
    headers.headers.find(_.name == name).map(_.value)

  def buildApp(
      counter: Ref[IO, Long],
      store: Ref[IO, Map[String, CachedEntry]]
  ): HttpApp[IO] =
    HttpRoutes.of[IO] {
      case req @ POST -> Root / "reservations" =>
        headerValue(req.headers, ci"Idempotency-Key") match {
          case None =>
            BadRequest(ErrorResponse("缺少 Idempotency-Key 头"))

          case Some(key) =>
            for {
              body <- req.as[CreateReservationRequest]
              response <-
                if (body.units <= 0) {
                  BadRequest(ErrorResponse("units 必须大于 0"))
                } else {
                  val fingerprint = s"${body.sku.trim}:${body.units}"

                  store.get.flatMap(_.get(key) match {
                    case Some(cached) if cached.fingerprint == fingerprint =>
                      Ok(cached.response.copy(replayed = true)).map(
                        _.putHeaders(Header.Raw(ci"X-Idempotency-Replayed", "true"))
                      )

                    case Some(_) =>
                      Conflict(ErrorResponse("相同 Idempotency-Key 不能复用到不同请求体")).map(
                        _.putHeaders(Header.Raw(ci"X-Idempotency-Replayed", "false"))
                      )

                    case None =>
                      for {
                        reservationId <- counter.updateAndGet(_ + 1)
                        created = ReservationResponse(reservationId, body.sku.trim, body.units, replayed = false)
                        _ <- store.update(_ + (key -> CachedEntry(fingerprint, created)))
                        response <- Ok(created).map(
                          _.putHeaders(Header.Raw(ci"X-Idempotency-Replayed", "false"))
                        )
                      } yield response
                  })
                }
            } yield response
        }
    }.orNotFound

  def render(label: String, response: Response[IO]): IO[Unit] =
    response.as[String].flatMap { body =>
      val replayed = response.headers.headers.find(_.name == ci"X-Idempotency-Replayed").map(_.value).getOrElse("-")
      IO.println(s"$label -> status=${response.status.code}, replayed=$replayed, body=$body")
    }

  def jsonRequest(key: Option[String], body: CreateReservationRequest): Request[IO] = {
    val request = Request[IO](Method.POST, uri"/reservations")
      .withEntity(body.asJson.noSpaces)
      .putHeaders(Header.Raw(ci"Content-Type", "application/json"))

    key.fold(request)(value => request.putHeaders(Header.Raw(ci"Idempotency-Key", value)))
  }

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== http4s Idempotency-Key 写接口 ===")
      counter <- Ref.of[IO, Long](1000L)
      store <- Ref.of[IO, Map[String, CachedEntry]](Map.empty)
      app = buildApp(counter, store)

      first <- app(jsonRequest(Some("idem-100"), CreateReservationRequest("BTC-101", 2)))
      _ <- render("first", first)

      replay <- app(jsonRequest(Some("idem-100"), CreateReservationRequest("BTC-101", 2)))
      _ <- render("replay", replay)

      conflict <- app(jsonRequest(Some("idem-100"), CreateReservationRequest("BTC-101", 3)))
      _ <- render("conflict", conflict)

      missing <- app(jsonRequest(None, CreateReservationRequest("ETH-202", 1)))
      _ <- render("missing-key", missing)

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- `Idempotency-Key` 让客户端安全重试同一个 POST，而不会重复创建业务结果")
      _ <- IO.println("- 同一个 key 如果对应不同请求体，服务端应该明确拒绝，而不是静默覆盖")
      _ <- IO.println("- 这里只是内存版示意；生产里通常还要把 key 和结果持久化到数据库")
    } yield ()
}
