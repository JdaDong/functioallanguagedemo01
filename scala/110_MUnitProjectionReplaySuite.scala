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
 * Scala 函数式编程 Demo 110: 读模型回放集成测试
 *
 * 109 号 Demo 已经把事务 checkpoint 跑通了，
 * 这一版继续把“HTTP 事件写入 + 查询侧 catch-up + checkpoint 回滚 + replay 重建”放进自动化回归。
 */
import cats.effect.{IO, Resource}
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

object MUnitProjectionReplaySuite {

  final case class AppendEventRequest(
      eventType: String,
      orderId: String,
      sku: Option[String],
      quantity: Option[Int],
      amount: Option[BigDecimal]
  )
  final case class EventAccepted(seq: Long, totalEvents: Int)
  final case class ReplayRequest(fromOffset: Long)
  final case class ProjectionResult(appliedSeqs: List[Long], checkpoint: Long, lag: Long)
  final case class ProjectionStatus(checkpoint: Long, totalEvents: Int, lag: Long)
  final case class ErrorResponse(error: String)
  final case class DomainEvent(
      seq: Long,
      eventType: String,
      orderId: String,
      sku: Option[String],
      quantity: Option[Int],
      amount: Option[BigDecimal]
  )
  final case class OrderReadModel(
      orderId: String,
      sku: String,
      quantity: Int,
      paymentStatus: String,
      totalPaid: BigDecimal
  )
  final case class Environment(repo: DoobieProjectionRepository, service: ProjectionService, xa: Transactor[IO], app: HttpApp[IO])

  implicit val appendDecoder: EntityDecoder[IO, AppendEventRequest] = jsonOf[IO, AppendEventRequest]
  implicit val eventAcceptedEncoder: EntityEncoder[IO, EventAccepted] = jsonEncoderOf[IO, EventAccepted]
  implicit val eventAcceptedDecoder: EntityDecoder[IO, EventAccepted] = jsonOf[IO, EventAccepted]
  implicit val replayDecoder: EntityDecoder[IO, ReplayRequest] = jsonOf[IO, ReplayRequest]
  implicit val projectionResultEncoder: EntityEncoder[IO, ProjectionResult] = jsonEncoderOf[IO, ProjectionResult]
  implicit val projectionResultDecoder: EntityDecoder[IO, ProjectionResult] = jsonOf[IO, ProjectionResult]
  implicit val projectionStatusEncoder: EntityEncoder[IO, ProjectionStatus] = jsonEncoderOf[IO, ProjectionStatus]
  implicit val projectionStatusDecoder: EntityDecoder[IO, ProjectionStatus] = jsonOf[IO, ProjectionStatus]
  implicit val orderReadModelEncoder: EntityEncoder[IO, OrderReadModel] = jsonEncoderOf[IO, OrderReadModel]
  implicit val orderReadModelDecoder: EntityDecoder[IO, OrderReadModel] = jsonOf[IO, OrderReadModel]
  implicit val errorEncoder: EntityEncoder[IO, ErrorResponse] = jsonEncoderOf[IO, ErrorResponse]
  implicit val errorDecoder: EntityDecoder[IO, ErrorResponse] = jsonOf[IO, ErrorResponse]

  trait ProjectionRepository[F[_]] {
    def nextSeq: F[Long]
    def appendEvent(event: DomainEvent): F[Unit]
    def totalEvents: F[Int]
    def findCheckpoint(projectorName: String): F[Option[Long]]
    def upsertCheckpoint(projectorName: String, lastSeq: Long): F[Unit]
    def nextEventAfter(seq: Long): F[Option[DomainEvent]]
    def findReadModel(orderId: String): F[Option[OrderReadModel]]
    def upsertReadModel(view: OrderReadModel): F[Unit]
    def clearReadModels: F[Unit]
  }

  final class DoobieProjectionRepository extends ProjectionRepository[ConnectionIO] {
    def nextSeq: ConnectionIO[Long] =
      sql"select coalesce(max(seq), 0) + 1 from event_log".query[Long].unique

    def appendEvent(event: DomainEvent): ConnectionIO[Unit] =
      sql"insert into event_log (seq, event_type, order_id, sku, quantity, amount) values (${event.seq}, ${event.eventType}, ${event.orderId}, ${event.sku}, ${event.quantity}, ${event.amount})"
        .update
        .run
        .void

    def totalEvents: ConnectionIO[Int] =
      sql"select count(*) from event_log".query[Int].unique

    def findCheckpoint(projectorName: String): ConnectionIO[Option[Long]] =
      sql"select last_seq from projection_checkpoint where projector_name = $projectorName".query[Long].option

    def upsertCheckpoint(projectorName: String, lastSeq: Long): ConnectionIO[Unit] =
      sql"merge into projection_checkpoint (projector_name, last_seq) key(projector_name) values ($projectorName, $lastSeq)"
        .update
        .run
        .void

    def nextEventAfter(seq: Long): ConnectionIO[Option[DomainEvent]] =
      sql"select seq, event_type, order_id, sku, quantity, amount from event_log where seq > $seq order by seq asc limit 1"
        .query[DomainEvent]
        .option

    def findReadModel(orderId: String): ConnectionIO[Option[OrderReadModel]] =
      sql"select order_id, sku, quantity, payment_status, total_paid from order_read_model where order_id = $orderId"
        .query[OrderReadModel]
        .option

    def upsertReadModel(view: OrderReadModel): ConnectionIO[Unit] =
      sql"merge into order_read_model (order_id, sku, quantity, payment_status, total_paid) key(order_id) values (${view.orderId}, ${view.sku}, ${view.quantity}, ${view.paymentStatus}, ${view.totalPaid})"
        .update
        .run
        .void

    def clearReadModels: ConnectionIO[Unit] =
      sql"delete from order_read_model".update.run.void
  }

  final class ProjectionService(repo: ProjectionRepository[ConnectionIO], projectorName: String) {

    def appendEvent(body: AppendEventRequest): ConnectionIO[EventAccepted] =
      validate(body) *>
        (for {
          seq <- repo.nextSeq
          event = DomainEvent(seq, body.eventType, body.orderId, body.sku.map(_.trim), body.quantity, body.amount)
          _ <- repo.appendEvent(event)
          total <- repo.totalEvents
        } yield EventAccepted(seq, total))

    def catchUpOnce(failAfterSeq: Option[Long]): ConnectionIO[Option[Long]] =
      for {
        checkpoint <- repo.findCheckpoint(projectorName).map(_.getOrElse(0L))
        nextEvent <- repo.nextEventAfter(checkpoint)
        applied <- nextEvent.traverse { event =>
          for {
            current <- repo.findReadModel(event.orderId)
            nextView <- evolve(current, event)
            _ <- repo.upsertReadModel(nextView)
            _ <-
              if (failAfterSeq.contains(event.seq)) {
                new RuntimeException("模拟在更新读模型后、推进 checkpoint 前崩溃").raiseError[ConnectionIO, Unit]
              } else {
                ().pure[ConnectionIO]
              }
            _ <- repo.upsertCheckpoint(projectorName, event.seq)
          } yield event.seq
        }
      } yield applied

    def catchUpAllIO(xa: Transactor[IO], failAfterSeq: Option[Long]): IO[List[Long]] = {
      def loop(acc: List[Long]): IO[List[Long]] =
        catchUpOnce(failAfterSeq).transact(xa).flatMap {
          case Some(seq) => loop(seq :: acc)
          case None      => acc.reverse.pure[IO]
        }

      loop(Nil)
    }

    def replayFromZeroIO(xa: Transactor[IO]): IO[List[Long]] =
      (repo.clearReadModels *> repo.upsertCheckpoint(projectorName, 0L)).transact(xa) *> catchUpAllIO(xa, None)

    def status(xa: Transactor[IO]): IO[ProjectionStatus] =
      (repo.findCheckpoint(projectorName), repo.totalEvents).mapN { (checkpoint, total) =>
        val saved = checkpoint.getOrElse(0L)
        ProjectionStatus(saved, total, lag = total - saved.toInt)
      }.transact(xa)

    private def validate(body: AppendEventRequest): ConnectionIO[Unit] =
      body.eventType match {
        case "OrderCreated" if body.sku.isDefined && body.quantity.isDefined => ().pure[ConnectionIO]
        case "PaymentCaptured" if body.amount.isDefined                       => ().pure[ConnectionIO]
        case "OrderCreated"                                                  => new RuntimeException("OrderCreated 需要 sku 和 quantity").raiseError[ConnectionIO, Unit]
        case "PaymentCaptured"                                               => new RuntimeException("PaymentCaptured 需要 amount").raiseError[ConnectionIO, Unit]
        case other                                                            => new RuntimeException(s"不支持的事件类型: $other").raiseError[ConnectionIO, Unit]
      }

    private def evolve(current: Option[OrderReadModel], event: DomainEvent): ConnectionIO[OrderReadModel] =
      event.eventType match {
        case "OrderCreated" =>
          OrderReadModel(
            orderId = event.orderId,
            sku = event.sku.getOrElse("unknown"),
            quantity = event.quantity.getOrElse(0),
            paymentStatus = "awaiting-payment",
            totalPaid = BigDecimal(0)
          ).pure[ConnectionIO]

        case "PaymentCaptured" =>
          current match {
            case Some(existing) =>
              existing.copy(
                paymentStatus = "paid",
                totalPaid = existing.totalPaid + event.amount.getOrElse(BigDecimal(0))
              ).pure[ConnectionIO]
            case None =>
              new RuntimeException(s"读模型缺少前置订单事件: orderId=${event.orderId}").raiseError[ConnectionIO, OrderReadModel]
          }

        case other =>
          new RuntimeException(s"不支持的事件类型: $other").raiseError[ConnectionIO, OrderReadModel]
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
      _ <- sql"""
        create table event_log (
          seq bigint primary key,
          event_type varchar not null,
          order_id varchar not null,
          sku varchar,
          quantity int,
          amount decimal(18, 2)
        )
      """.update.run
      _ <- sql"""
        create table order_read_model (
          order_id varchar primary key,
          sku varchar not null,
          quantity int not null,
          payment_status varchar not null,
          total_paid decimal(18, 2) not null
        )
      """.update.run
      _ <- sql"""
        create table projection_checkpoint (
          projector_name varchar primary key,
          last_seq bigint not null
        )
      """.update.run
    } yield ()

  def buildApp(service: ProjectionService, repo: DoobieProjectionRepository, xa: Transactor[IO]): HttpApp[IO] =
    HttpRoutes.of[IO] {
      case req @ POST -> Root / "events" / "orders" =>
        req.as[AppendEventRequest].flatMap { body =>
          service.appendEvent(body).transact(xa).flatMap(Accepted(_)).handleErrorWith(error => BadRequest(ErrorResponse(error.getMessage)))
        }

      case req @ POST -> Root / "admin" / "projections" / "catch-up" =>
        val failAfterSeq = req.headers.get(ci"X-Fail-After-Seq").flatMap(_.head.value.toLongOption)
        service.catchUpAllIO(xa, failAfterSeq).flatMap { applied =>
          service.status(xa).flatMap(status => Ok(ProjectionResult(applied, status.checkpoint, status.lag)))
        }.handleErrorWith(error => InternalServerError(ErrorResponse(error.getMessage)))

      case req @ POST -> Root / "admin" / "projections" / "replay" =>
        req.as[ReplayRequest].flatMap { body =>
          if (body.fromOffset == 0L) {
            service.replayFromZeroIO(xa).flatMap { applied =>
              service.status(xa).flatMap(status => Ok(ProjectionResult(applied, status.checkpoint, status.lag)))
            }
          } else {
            BadRequest(ErrorResponse("这个 Demo 只演示从 offset=0 全量 replay"))
          }
        }

      case GET -> Root / "admin" / "projections" / "status" =>
        service.status(xa).flatMap(Ok(_))

      case GET -> Root / "orders" / orderId / "read-model" =>
        repo.findReadModel(orderId).transact(xa).flatMap {
          case Some(view) => Ok(view)
          case None       => NotFound(ErrorResponse(s"找不到读模型: $orderId"))
        }
    }.orNotFound

  def environmentResource(label: String): Resource[IO, Environment] =
    transactorResource(label).evalTap(xa => (createSchema *> new DoobieProjectionRepository().upsertCheckpoint("orders-read-model", 0L)).transact(xa)).map { xa =>
      val repo = new DoobieProjectionRepository
      val service = new ProjectionService(repo, "orders-read-model")
      val app = buildApp(service, repo, xa)
      Environment(repo, service, xa, app)
    }

  def jsonRequest[A](method: Method, uri: Uri, body: A, failAfterSeq: Option[Long] = None)(implicit encoder: io.circe.Encoder[A]): Request[IO] = {
    val base = Request[IO](method, uri)
      .withEntity(body.asJson.noSpaces)
      .putHeaders(Header.Raw(ci"Content-Type", "application/json"))

    failAfterSeq match {
      case Some(seq) => base.putHeaders(Header.Raw(ci"X-Fail-After-Seq", seq.toString))
      case None      => base
    }
  }
}

class MUnitProjectionReplaySuite extends CatsEffectSuite {
  import MUnitProjectionReplaySuite._

  test("catch-up 后可以查询读模型，并且 lag 清零") {
    environmentResource("catchup").use { env =>
      for {
        _ <- env.app(jsonRequest(Method.POST, uri"/events/orders", AppendEventRequest("OrderCreated", "order-100", Some("BTC-101"), Some(2), None)))
        _ <- env.app(jsonRequest(Method.POST, uri"/events/orders", AppendEventRequest("PaymentCaptured", "order-100", None, None, Some(BigDecimal(128.5)))))

        statusBeforeResponse <- env.app(Request[IO](Method.GET, uri"/admin/projections/status"))
        statusBefore <- statusBeforeResponse.as[ProjectionStatus]
        _ = assertEquals(statusBefore.checkpoint, 0L)
        _ = assertEquals(statusBefore.lag, 2L)

        catchUpResponse <- env.app(Request[IO](Method.POST, uri"/admin/projections/catch-up"))
        _ = assertEquals(catchUpResponse.status, Status.Ok)
        catchUp <- catchUpResponse.as[ProjectionResult]
        _ = assertEquals(catchUp.appliedSeqs, List(1L, 2L))
        _ = assertEquals(catchUp.checkpoint, 2L)
        _ = assertEquals(catchUp.lag, 0L)

        orderResponse <- env.app(Request[IO](Method.GET, uri"/orders/order-100/read-model"))
        _ = assertEquals(orderResponse.status, Status.Ok)
        order <- orderResponse.as[OrderReadModel]
        _ = assertEquals(order.paymentStatus, "paid")
        _ = assertEquals(order.totalPaid, BigDecimal(128.5))
      } yield ()
    }
  }

  test("当前事件在推进 checkpoint 前失败会整体回滚，后续可以安全续跑") {
    environmentResource("rollback").use { env =>
      for {
        _ <- env.app(jsonRequest(Method.POST, uri"/events/orders", AppendEventRequest("OrderCreated", "order-200", Some("ETH-202"), Some(1), None)))
        _ <- env.app(jsonRequest(Method.POST, uri"/events/orders", AppendEventRequest("PaymentCaptured", "order-200", None, None, Some(BigDecimal(88.0)))))

        failedResponse <- env.app(jsonRequest(Method.POST, uri"/admin/projections/catch-up", ReplayRequest(0L), failAfterSeq = Some(2L)))
        _ = assertEquals(failedResponse.status, Status.InternalServerError)
        failedError <- failedResponse.as[ErrorResponse]
        _ = assert(clue(failedError.error).contains("推进 checkpoint 前崩溃"))

        orderAfterFail <- env.repo.findReadModel("order-200").transact(env.xa)
        statusAfterFail <- env.service.status(env.xa)
        _ = assertEquals(orderAfterFail.map(_.paymentStatus), Some("awaiting-payment"))
        _ = assertEquals(orderAfterFail.map(_.totalPaid), Some(BigDecimal(0)))
        _ = assertEquals(statusAfterFail.checkpoint, 1L)
        _ = assertEquals(statusAfterFail.lag, 1L)

        retryResponse <- env.app(Request[IO](Method.POST, uri"/admin/projections/catch-up"))
        _ = assertEquals(retryResponse.status, Status.Ok)
        retry <- retryResponse.as[ProjectionResult]
        _ = assertEquals(retry.appliedSeqs, List(2L))

        orderAfterRetry <- env.repo.findReadModel("order-200").transact(env.xa)
        statusAfterRetry <- env.service.status(env.xa)
        _ = assertEquals(orderAfterRetry.map(_.paymentStatus), Some("paid"))
        _ = assertEquals(orderAfterRetry.map(_.totalPaid), Some(BigDecimal(88.0)))
        _ = assertEquals(statusAfterRetry.checkpoint, 2L)
        _ = assertEquals(statusAfterRetry.lag, 0L)
      } yield ()
    }
  }

  test("replay from 0 会重建读模型，而且不会重复累计金额") {
    environmentResource("replay").use { env =>
      for {
        _ <- env.app(jsonRequest(Method.POST, uri"/events/orders", AppendEventRequest("OrderCreated", "order-300", Some("BTC-101"), Some(1), None)))
        _ <- env.app(jsonRequest(Method.POST, uri"/events/orders", AppendEventRequest("PaymentCaptured", "order-300", None, None, Some(BigDecimal(66.0)))))
        _ <- env.app(Request[IO](Method.POST, uri"/admin/projections/catch-up"))

        replayResponse <- env.app(jsonRequest(Method.POST, uri"/admin/projections/replay", ReplayRequest(0L)))
        _ = assertEquals(replayResponse.status, Status.Ok)
        replay <- replayResponse.as[ProjectionResult]
        _ = assertEquals(replay.appliedSeqs, List(1L, 2L))
        _ = assertEquals(replay.checkpoint, 2L)
        _ = assertEquals(replay.lag, 0L)

        orderResponse <- env.app(Request[IO](Method.GET, uri"/orders/order-300/read-model"))
        _ = assertEquals(orderResponse.status, Status.Ok)
        order <- orderResponse.as[OrderReadModel]
        _ = assertEquals(order.paymentStatus, "paid")
        _ = assertEquals(order.totalPaid, BigDecimal(66.0))
      } yield ()
    }
  }
}
