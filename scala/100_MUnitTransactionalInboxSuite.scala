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
 * Scala 函数式编程 Demo 100: 事务 Inbox 集成测试
 *
 * 99 号 Demo 已经把事务 inbox 跑通了，
 * 这一版继续把“Webhook 接收 + 事务写入 + 重放保护 + 回滚重试”放进自动化回归。
 */
import cats.effect.{IO, Ref, Resource}
import cats.syntax.all._
import doobie._
import doobie.implicits._
import io.circe.generic.auto._
import io.circe.syntax._
import munit.CatsEffectSuite
import org.http4s._
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.typelevel.ci._

import java.util.UUID

object MUnitTransactionalInboxSuite {

  final case class WebhookPayload(orderId: String, sku: String, quantity: Int)
  final case class WebhookAck(orderId: String, replayed: Boolean)
  final case class ErrorResponse(error: String)
  final case class OrderCreated(eventId: String, orderId: String, sku: String, quantity: Int)
  final case class ShipmentProjection(orderId: String, sku: String, quantity: Int, status: String)
  final case class ProcessedEvent(eventId: String, fingerprint: String, orderId: String)
  final case class Environment(repo: DoobieInboxRepository, service: ShipmentService, xa: Transactor[IO], app: HttpApp[IO])

  implicit val webhookPayloadDecoder: EntityDecoder[IO, WebhookPayload] =
    jsonOf[IO, WebhookPayload]

  implicit val webhookAckEncoder: EntityEncoder[IO, WebhookAck] =
    jsonEncoderOf[IO, WebhookAck]

  implicit val errorEncoder: EntityEncoder[IO, ErrorResponse] =
    jsonEncoderOf[IO, ErrorResponse]

  trait InboxRepository[F[_]] {
    def findProcessed(eventId: String): F[Option[ProcessedEvent]]
    def insertProcessed(event: ProcessedEvent): F[Unit]
    def insertShipment(projection: ShipmentProjection): F[Unit]
    def shipmentCount: F[Int]
    def processedCount: F[Int]
  }

  final class DoobieInboxRepository extends InboxRepository[ConnectionIO] {
    def findProcessed(eventId: String): ConnectionIO[Option[ProcessedEvent]] =
      sql"select event_id, fingerprint, order_id from processed_event where event_id = $eventId"
        .query[ProcessedEvent]
        .option

    def insertProcessed(event: ProcessedEvent): ConnectionIO[Unit] =
      sql"insert into processed_event (event_id, fingerprint, order_id) values (${event.eventId}, ${event.fingerprint}, ${event.orderId})"
        .update
        .run
        .void

    def insertShipment(projection: ShipmentProjection): ConnectionIO[Unit] =
      sql"insert into shipment_projection (order_id, sku, quantity, status) values (${projection.orderId}, ${projection.sku}, ${projection.quantity}, ${projection.status})"
        .update
        .run
        .void

    def shipmentCount: ConnectionIO[Int] =
      sql"select count(*) from shipment_projection".query[Int].unique

    def processedCount: ConnectionIO[Int] =
      sql"select count(*) from processed_event".query[Int].unique
  }

  final class ShipmentService(repo: InboxRepository[ConnectionIO]) {
    def consume(event: OrderCreated, failAfterProjection: Boolean): ConnectionIO[WebhookAck] = {
      val fingerprint = s"${event.orderId}|${event.sku.trim}|${event.quantity}"

      repo.findProcessed(event.eventId).flatMap {
        case Some(saved) if saved.fingerprint == fingerprint =>
          WebhookAck(event.orderId, replayed = true).pure[ConnectionIO]

        case Some(_) =>
          new RuntimeException("相同 eventId 不能复用到不同 payload").raiseError[ConnectionIO, WebhookAck]

        case None =>
          for {
            _ <- repo.insertShipment(ShipmentProjection(event.orderId, event.sku, event.quantity, status = "scheduled"))
            _ <-
              if (failAfterProjection)
                new RuntimeException("模拟在写入 projection 后崩溃").raiseError[ConnectionIO, Unit]
              else ().pure[ConnectionIO]
            _ <- repo.insertProcessed(ProcessedEvent(event.eventId, fingerprint, event.orderId))
          } yield WebhookAck(event.orderId, replayed = false)
      }
    }
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
      _ <- sql"create table shipment_projection (order_id varchar primary key, sku varchar not null, quantity int not null, status varchar not null)".update.run
      _ <- sql"create table processed_event (event_id varchar primary key, fingerprint varchar not null, order_id varchar not null)".update.run
    } yield ()

  def headerValue(headers: Headers, name: CIString): Option[String] =
    headers.headers.find(_.name == name).map(_.value)

  def buildApp(
      service: ShipmentService,
      xa: Transactor[IO],
      failAfterProjection: Ref[IO, Set[String]]
  ): HttpApp[IO] =
    HttpRoutes.of[IO] {
      case req @ POST -> Root / "warehouse" / "webhooks" / "order-created" =>
        headerValue(req.headers, ci"X-Event-Id") match {
          case None =>
            BadRequest(ErrorResponse("缺少 X-Event-Id 头"))

          case Some(eventId) =>
            req.as[WebhookPayload].flatMap { body =>
              failAfterProjection.modify { current =>
                if (current.contains(eventId)) (current - eventId) -> true
                else current -> false
              }.flatMap { shouldFail =>
                service.consume(OrderCreated(eventId, body.orderId, body.sku, body.quantity), shouldFail).transact(xa).attempt.flatMap {
                  case Right(response) => Ok(response)
                  case Left(error) if error.getMessage.contains("不同 payload") =>
                    Conflict(ErrorResponse(error.getMessage))
                  case Left(error) =>
                    InternalServerError(ErrorResponse(error.getMessage))
                }
              }
            }
        }
    }.orNotFound

  def withEnvironment[A](label: String, failFirstEvents: Set[String] = Set.empty)(program: Environment => IO[A]): IO[A] =
    transactorResource(label).use { xa =>
      val repo = new DoobieInboxRepository
      val service = new ShipmentService(repo)
      for {
        failRef <- Ref.of[IO, Set[String]](failFirstEvents)
        _ <- createSchema.transact(xa)
        app = buildApp(service, xa, failRef)
        result <- program(Environment(repo, service, xa, app))
      } yield result
    }
}

class MUnitTransactionalInboxSuite extends CatsEffectSuite {
  import MUnitTransactionalInboxSuite._

  def request(eventId: Option[String], body: WebhookPayload): Request[IO] = {
    val base = Request[IO](Method.POST, uri"/warehouse/webhooks/order-created")
      .withEntity(body.asJson.noSpaces)
      .putHeaders(Header.Raw(ci"Content-Type", "application/json"))

    eventId.fold(base)(value => base.putHeaders(Header.Raw(ci"X-Event-Id", value)))
  }

  def responseBody(response: Response[IO]): IO[String] =
    response.as[String]

  test("相同 eventId + 相同 payload 应该只写入一次 projection，并返回 replayed") {
    withEnvironment("same-event-same-payload") { env =>
      val payload = WebhookPayload("order-100", "BTC-101", 2)
      for {
        first <- env.app(request(Some("evt-100"), payload))
        firstBody <- responseBody(first)
        second <- env.app(request(Some("evt-100"), payload))
        secondBody <- responseBody(second)
        shipmentCount <- env.repo.shipmentCount.transact(env.xa)
        processedCount <- env.repo.processedCount.transact(env.xa)
      } yield {
        assertEquals(first.status, Status.Ok)
        assertEquals(second.status, Status.Ok)
        assert(firstBody.contains("\"replayed\":false"))
        assert(secondBody.contains("\"replayed\":true"))
        assertEquals(shipmentCount, 1)
        assertEquals(processedCount, 1)
      }
    }
  }

  test("第一次在事务中失败回滚后，重试应该成功且不会留下半条数据") {
    withEnvironment("rollback-then-retry", failFirstEvents = Set("evt-200")) { env =>
      val payload = WebhookPayload("order-200", "ETH-202", 1)
      for {
        first <- env.app(request(Some("evt-200"), payload))
        firstBody <- responseBody(first)
        shipmentCountAfterFail <- env.repo.shipmentCount.transact(env.xa)
        processedCountAfterFail <- env.repo.processedCount.transact(env.xa)
        second <- env.app(request(Some("evt-200"), payload))
        secondBody <- responseBody(second)
        shipmentCountAfterRetry <- env.repo.shipmentCount.transact(env.xa)
        processedCountAfterRetry <- env.repo.processedCount.transact(env.xa)
      } yield {
        assertEquals(first.status, Status.InternalServerError)
        assert(firstBody.contains("模拟在写入 projection 后崩溃"))
        assertEquals(shipmentCountAfterFail, 0)
        assertEquals(processedCountAfterFail, 0)

        assertEquals(second.status, Status.Ok)
        assert(secondBody.contains("\"replayed\":false"))
        assertEquals(shipmentCountAfterRetry, 1)
        assertEquals(processedCountAfterRetry, 1)
      }
    }
  }

  test("相同 eventId + 不同 payload 应该返回冲突错误") {
    withEnvironment("same-event-different-payload") { env =>
      for {
        _ <- env.app(request(Some("evt-300"), WebhookPayload("order-300", "SOL-303", 4)))
        conflict <- env.app(request(Some("evt-300"), WebhookPayload("order-300", "SOL-303", 5)))
        body <- responseBody(conflict)
        shipmentCount <- env.repo.shipmentCount.transact(env.xa)
        processedCount <- env.repo.processedCount.transact(env.xa)
      } yield {
        assertEquals(conflict.status, Status.Conflict)
        assert(body.contains("相同 eventId 不能复用到不同 payload"))
        assertEquals(shipmentCount, 1)
        assertEquals(processedCount, 1)
      }
    }
  }

  test("缺少 X-Event-Id 时应该返回 400") {
    withEnvironment("missing-header") { env =>
      for {
        response <- env.app(request(None, WebhookPayload("order-400", "BTC-101", 1)))
        body <- responseBody(response)
      } yield {
        assertEquals(response.status, Status.BadRequest)
        assert(body.contains("缺少 X-Event-Id 头"))
      }
    }
  }
}
