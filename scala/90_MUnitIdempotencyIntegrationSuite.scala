//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.tpolecat::doobie-core:1.0.0-RC8"
//> using dep "org.tpolecat::doobie-h2:1.0.0-RC8"
//> using dep "org.http4s::http4s-core:0.23.33"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.http4s::http4s-circe:0.23.33"
//> using dep "org.typelevel::munit-cats-effect-3:1.0.7"
//> using dep "io.circe::circe-generic:0.14.10"

/**
 * Scala 函数式编程 Demo 90: Idempotency-Key + Doobie 集成测试
 *
 * 89 号 Demo 已经把数据库幂等写入跑通了，
 * 这一版继续把 HTTP 写接口和真实数据库边界一起纳入自动化回归。
 *
 * - POST 写接口要求带 `Idempotency-Key`
 * - 同一个 key + 同一个请求体应该只落库一次
 * - 同一个 key + 不同请求体应该返回冲突错误
 */
import cats.MonadThrow
import cats.effect.{IO, Resource}
import cats.syntax.all._
import doobie._
import doobie.implicits._
import fs2.text
import io.circe.generic.auto._
import io.circe.syntax._
import munit.CatsEffectSuite
import org.http4s._
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.typelevel.ci._

import java.util.UUID

object MUnitIdempotencyIntegrationSuite {

  final case class CreateReservationRequest(sku: String, units: Int)
  final case class ReservationResponse(
      requestId: String,
      sku: String,
      units: Int,
      remainingStock: Int,
      replayed: Boolean
  )
  final case class ErrorResponse(error: String)
  final case class StoredReservation(requestId: String, sku: String, units: Int, remainingStock: Int)
  final case class Environment(
      service: ReservationService[ConnectionIO],
      repo: ReservationRepository[ConnectionIO],
      xa: Transactor[IO],
      app: HttpApp[IO]
  )

  implicit val createReservationDecoder: EntityDecoder[IO, CreateReservationRequest] =
    jsonOf[IO, CreateReservationRequest]

  implicit val reservationEncoder: EntityEncoder[IO, ReservationResponse] =
    jsonEncoderOf[IO, ReservationResponse]

  implicit val errorEncoder: EntityEncoder[IO, ErrorResponse] =
    jsonEncoderOf[IO, ErrorResponse]

  trait ReservationRepository[F[_]] {
    def findRequest(requestId: String): F[Option[StoredReservation]]
    def currentStock(sku: String): F[Option[Int]]
    def updateStock(sku: String, remaining: Int): F[Unit]
    def insertRequest(stored: StoredReservation): F[Unit]
    def countRequests: F[Int]
  }

  final class ReservationService[F[_]: MonadThrow](repo: ReservationRepository[F]) {
    def reserve(requestId: String, body: CreateReservationRequest): F[ReservationResponse] =
      if (body.units <= 0) {
        new IllegalArgumentException("预留数量必须大于 0").raiseError[F, ReservationResponse]
      } else {
        repo.findRequest(requestId).flatMap {
          case Some(stored) if stored.sku == body.sku && stored.units == body.units =>
            ReservationResponse(stored.requestId, stored.sku, stored.units, stored.remainingStock, replayed = true)
              .pure[F]

          case Some(_) =>
            new RuntimeException("相同 Idempotency-Key 不能复用到不同请求体").raiseError[F, ReservationResponse]

          case None =>
            repo.currentStock(body.sku).flatMap {
              case None =>
                new RuntimeException(s"SKU 不存在: ${body.sku}").raiseError[F, ReservationResponse]

              case Some(stock) if stock < body.units =>
                new RuntimeException(s"库存不足: ${body.sku}, 当前=$stock, 请求=${body.units}")
                  .raiseError[F, ReservationResponse]

              case Some(stock) =>
                val remaining = stock - body.units
                val stored = StoredReservation(requestId, body.sku, body.units, remaining)
                (repo.updateStock(body.sku, remaining) *> repo.insertRequest(stored)) *>
                  ReservationResponse(requestId, body.sku, body.units, remaining, replayed = false).pure[F]
            }
        }
      }

    def requestCount: F[Int] = repo.countRequests
    def stockOf(sku: String): F[Option[Int]] = repo.currentStock(sku)
  }

  final class DoobieReservationRepository extends ReservationRepository[ConnectionIO] {
    def findRequest(requestId: String): ConnectionIO[Option[StoredReservation]] =
      sql"select request_id, sku, units, remaining_stock from reservation_request where request_id = $requestId"
        .query[StoredReservation]
        .option

    def currentStock(sku: String): ConnectionIO[Option[Int]] =
      sql"select stock from inventory_item where sku = $sku".query[Int].option

    def updateStock(sku: String, remaining: Int): ConnectionIO[Unit] =
      sql"update inventory_item set stock = $remaining where sku = $sku".update.run.void

    def insertRequest(stored: StoredReservation): ConnectionIO[Unit] =
      sql"insert into reservation_request (request_id, sku, units, remaining_stock) values (${stored.requestId}, ${stored.sku}, ${stored.units}, ${stored.remainingStock})"
        .update
        .run
        .void

    def countRequests: ConnectionIO[Int] =
      sql"select count(*) from reservation_request".query[Int].unique
  }

  def transactorResource(label: String): Resource[IO, Transactor[IO]] =
    Resource.eval(
      IO(
        Transactor.fromDriverManager[IO](
          driver = "org.h2.Driver",
          url = s"jdbc:h2:mem:$label-${UUID.randomUUID().toString.replace("-", "")};DB_CLOSE_DELAY=-1",
          user = "sa",
          password = "",
          logHandler = None
        )
      )
    )

  val createSchema: ConnectionIO[Unit] =
    for {
      _ <- sql"""
        create table inventory_item (
          sku varchar primary key,
          stock int not null
        )
      """.update.run
      _ <- sql"""
        create table reservation_request (
          request_id varchar primary key,
          sku varchar not null,
          units int not null,
          remaining_stock int not null
        )
      """.update.run
      _ <- Update[(String, Int)](
        "insert into inventory_item (sku, stock) values (?, ?)"
      ).updateMany(
        List(
          ("BTC-101", 10),
          ("ETH-202", 6)
        )
      )
    } yield ()

  def headerValue(headers: Headers, name: CIString): Option[String] =
    headers.headers.find(_.name == name).map(_.value)

  def buildApp(service: ReservationService[ConnectionIO], xa: Transactor[IO]): HttpApp[IO] =
    HttpRoutes.of[IO] {
      case req @ POST -> Root / "reservations" =>
        headerValue(req.headers, ci"Idempotency-Key") match {
          case None =>
            BadRequest(ErrorResponse("缺少 Idempotency-Key 头"))

          case Some(requestId) =>
            req.as[CreateReservationRequest].flatMap { body =>
              service.reserve(requestId, body).transact(xa).attempt.flatMap {
                case Right(response) => Ok(response)
                case Left(error) if error.getMessage.contains("不同请求体") =>
                  Conflict(ErrorResponse(error.getMessage))
                case Left(error) =>
                  BadRequest(ErrorResponse(error.getMessage))
              }
            }
        }
    }.orNotFound

  def responseBody(response: Response[IO]): IO[String] =
    response.body.through(text.utf8.decode).compile.string
}

class MUnitIdempotencyIntegrationSuite extends CatsEffectSuite {
  import MUnitIdempotencyIntegrationSuite._

  def withEnvironment[A](label: String)(program: Environment => IO[A]): IO[A] =
    transactorResource(label).use { xa =>
      val repo = new DoobieReservationRepository
      val service = new ReservationService[ConnectionIO](repo)
      val app = buildApp(service, xa)
      createSchema.transact(xa) *> program(Environment(service, repo, xa, app))
    }

  def request(key: Option[String], body: CreateReservationRequest): Request[IO] = {
    val base = Request[IO](Method.POST, uri"/reservations")
      .withEntity(body.asJson.noSpaces)
      .putHeaders(Header.Raw(ci"Content-Type", "application/json"))

    key.fold(base)(value => base.putHeaders(Header.Raw(ci"Idempotency-Key", value)))
  }

  test("相同 Idempotency-Key + 相同请求体应该只落库一次，并复用首个结果") {
    withEnvironment("same-key-same-body") { env =>
      for {
        first <- env.app(request(Some("idem-100"), CreateReservationRequest("BTC-101", 2)))
        firstBody <- responseBody(first)
        second <- env.app(request(Some("idem-100"), CreateReservationRequest("BTC-101", 2)))
        secondBody <- responseBody(second)
        count <- env.service.requestCount.transact(env.xa)
        stock <- env.service.stockOf("BTC-101").transact(env.xa)
      } yield {
        assertEquals(first.status, Status.Ok)
        assertEquals(second.status, Status.Ok)
        assert(firstBody.contains("\"replayed\":false"))
        assert(secondBody.contains("\"replayed\":true"))
        assert(firstBody.contains("\"remainingStock\":8"))
        assert(secondBody.contains("\"remainingStock\":8"))
        assertEquals(count, 1)
        assertEquals(stock, Some(8))
      }
    }
  }

  test("相同 Idempotency-Key + 不同请求体应该返回冲突错误，且库存不重复扣减") {
    withEnvironment("same-key-different-body") { env =>
      for {
        _ <- env.app(request(Some("idem-200"), CreateReservationRequest("ETH-202", 1)))
        conflict <- env.app(request(Some("idem-200"), CreateReservationRequest("ETH-202", 3)))
        body <- responseBody(conflict)
        count <- env.service.requestCount.transact(env.xa)
        stock <- env.service.stockOf("ETH-202").transact(env.xa)
      } yield {
        assertEquals(conflict.status, Status.Conflict)
        assert(body.contains("相同 Idempotency-Key 不能复用到不同请求体"))
        assertEquals(count, 1)
        assertEquals(stock, Some(5))
      }
    }
  }

  test("缺少 Idempotency-Key 时应该返回 400") {
    withEnvironment("missing-header") { env =>
      for {
        response <- env.app(request(None, CreateReservationRequest("BTC-101", 2)))
        body <- responseBody(response)
      } yield {
        assertEquals(response.status, Status.BadRequest)
        assert(body.contains("缺少 Idempotency-Key 头"))
      }
    }
  }
}
