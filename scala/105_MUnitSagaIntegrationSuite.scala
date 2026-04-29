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
 * Scala 函数式编程 Demo 105: Saga 集成测试
 *
 * 104 号 Demo 已经把事务 Saga 状态跑通了，
 * 这一版继续把“HTTP 创建 + 支付回调 + 事务补偿 + 回滚重试”放进自动化回归。
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

object MUnitSagaIntegrationSuite {

  final case class CheckoutRequest(orderId: String, sku: String, quantity: Int, amount: BigDecimal)
  final case class CheckoutAccepted(sagaId: String, status: String)
  final case class PaymentCallbackRequest(sagaId: String, approved: Boolean, reason: Option[String])
  final case class PaymentAck(sagaId: String, status: String, replayed: Boolean)
  final case class ErrorResponse(error: String)
  final case class ReservationRow(orderId: String, sku: String, quantity: Int, status: String)
  final case class SagaRow(
      sagaId: String,
      orderId: String,
      sku: String,
      quantity: Int,
      amount: BigDecimal,
      status: String,
      compensationReason: Option[String]
  )
  final case class Environment(repo: DoobieSagaRepository, service: CheckoutService, xa: Transactor[IO], app: HttpApp[IO])

  implicit val checkoutRequestDecoder: EntityDecoder[IO, CheckoutRequest] = jsonOf[IO, CheckoutRequest]
  implicit val checkoutAcceptedEncoder: EntityEncoder[IO, CheckoutAccepted] = jsonEncoderOf[IO, CheckoutAccepted]
  implicit val checkoutAcceptedDecoder: EntityDecoder[IO, CheckoutAccepted] = jsonOf[IO, CheckoutAccepted]
  implicit val callbackRequestDecoder: EntityDecoder[IO, PaymentCallbackRequest] = jsonOf[IO, PaymentCallbackRequest]
  implicit val paymentAckEncoder: EntityEncoder[IO, PaymentAck] = jsonEncoderOf[IO, PaymentAck]
  implicit val paymentAckDecoder: EntityDecoder[IO, PaymentAck] = jsonOf[IO, PaymentAck]
  implicit val errorEncoder: EntityEncoder[IO, ErrorResponse] = jsonEncoderOf[IO, ErrorResponse]
  implicit val errorDecoder: EntityDecoder[IO, ErrorResponse] = jsonOf[IO, ErrorResponse]

  trait SagaRepository[F[_]] {
    def currentStock(sku: String): F[Option[Int]]
    def updateStock(sku: String, stock: Int): F[Unit]
    def insertReservation(row: ReservationRow): F[Unit]
    def findReservation(orderId: String): F[Option[ReservationRow]]
    def updateReservationStatus(orderId: String, status: String): F[Unit]
    def insertSaga(row: SagaRow): F[Unit]
    def findSaga(sagaId: String): F[Option[SagaRow]]
    def updateSagaStatus(sagaId: String, status: String, compensationReason: Option[String]): F[Unit]
  }

  final class DoobieSagaRepository extends SagaRepository[ConnectionIO] {
    def currentStock(sku: String): ConnectionIO[Option[Int]] =
      sql"select stock from inventory_item where sku = $sku".query[Int].option

    def updateStock(sku: String, stock: Int): ConnectionIO[Unit] =
      sql"update inventory_item set stock = $stock where sku = $sku".update.run.void

    def insertReservation(row: ReservationRow): ConnectionIO[Unit] =
      sql"insert into reservation_hold (order_id, sku, quantity, status) values (${row.orderId}, ${row.sku}, ${row.quantity}, ${row.status})"
        .update
        .run
        .void

    def findReservation(orderId: String): ConnectionIO[Option[ReservationRow]] =
      sql"select order_id, sku, quantity, status from reservation_hold where order_id = $orderId"
        .query[ReservationRow]
        .option

    def updateReservationStatus(orderId: String, status: String): ConnectionIO[Unit] =
      sql"update reservation_hold set status = $status where order_id = $orderId".update.run.void

    def insertSaga(row: SagaRow): ConnectionIO[Unit] =
      sql"insert into saga_instance (saga_id, order_id, sku, quantity, amount, status, compensation_reason) values (${row.sagaId}, ${row.orderId}, ${row.sku}, ${row.quantity}, ${row.amount}, ${row.status}, ${row.compensationReason})"
        .update
        .run
        .void

    def findSaga(sagaId: String): ConnectionIO[Option[SagaRow]] =
      sql"select saga_id, order_id, sku, quantity, amount, status, compensation_reason from saga_instance where saga_id = $sagaId"
        .query[SagaRow]
        .option

    def updateSagaStatus(sagaId: String, status: String, compensationReason: Option[String]): ConnectionIO[Unit] =
      sql"update saga_instance set status = $status, compensation_reason = $compensationReason where saga_id = $sagaId"
        .update
        .run
        .void
  }

  final class CheckoutService(repo: SagaRepository[ConnectionIO]) {

    def startCheckout(body: CheckoutRequest): ConnectionIO[CheckoutAccepted] = {
      val sagaId = s"saga-${body.orderId}"

      for {
        available <- requireStock(body.sku.trim)
        _ <-
          if (available >= body.quantity) ().pure[ConnectionIO]
          else new RuntimeException(s"库存不足: sku=${body.sku}, available=$available, requested=${body.quantity}").raiseError[ConnectionIO, Unit]
        _ <- repo.updateStock(body.sku.trim, available - body.quantity)
        _ <- repo.insertReservation(ReservationRow(body.orderId, body.sku.trim, body.quantity, status = "reserved"))
        _ <- repo.insertSaga(
          SagaRow(
            sagaId = sagaId,
            orderId = body.orderId,
            sku = body.sku.trim,
            quantity = body.quantity,
            amount = body.amount,
            status = "waiting-payment",
            compensationReason = None
          )
        )
      } yield CheckoutAccepted(sagaId, status = "waiting-payment")
    }

    def handlePaymentCallback(
        sagaId: String,
        approved: Boolean,
        compensationReason: String,
        failAfterRelease: Boolean
    ): ConnectionIO[PaymentAck] =
      repo.findSaga(sagaId).flatMap {
        case None =>
          new RuntimeException(s"找不到 saga: $sagaId").raiseError[ConnectionIO, PaymentAck]

        case Some(saga) if saga.status != "waiting-payment" =>
          PaymentAck(sagaId, status = saga.status, replayed = true).pure[ConnectionIO]

        case Some(saga) if approved =>
          for {
            _ <- repo.updateReservationStatus(saga.orderId, "consumed")
            _ <- repo.updateSagaStatus(sagaId, "completed", None)
          } yield PaymentAck(sagaId, status = "completed", replayed = false)

        case Some(saga) =>
          for {
            available <- requireStock(saga.sku)
            _ <- repo.updateStock(saga.sku, available + saga.quantity)
            _ <-
              if (failAfterRelease)
                new RuntimeException("模拟在释放库存后、更新 Saga 状态前崩溃").raiseError[ConnectionIO, Unit]
              else ().pure[ConnectionIO]
            _ <- repo.updateReservationStatus(saga.orderId, "released")
            _ <- repo.updateSagaStatus(sagaId, "compensated", Some(compensationReason))
          } yield PaymentAck(sagaId, status = "compensated", replayed = false)
      }

    private def requireStock(sku: String): ConnectionIO[Int] =
      repo.currentStock(sku).flatMap {
        case Some(stock) => stock.pure[ConnectionIO]
        case None => new RuntimeException(s"库存不存在: $sku").raiseError[ConnectionIO, Int]
      }
  }

  def headerValue(headers: Headers, name: CIString): Option[String] =
    headers.headers.find(_.name == name).map(_.value)

  def buildApp(service: CheckoutService, xa: Transactor[IO]): HttpApp[IO] =
    HttpRoutes.of[IO] {
      case req @ POST -> Root / "checkouts" =>
        req.as[CheckoutRequest].flatMap { body =>
          service.startCheckout(body).transact(xa).flatMap(Accepted(_)).handleErrorWith { error =>
            BadRequest(ErrorResponse(error.getMessage))
          }
        }

      case req @ POST -> Root / "payments" / "callback" =>
        req.as[PaymentCallbackRequest].flatMap { body =>
          val failAfterRelease = headerValue(req.headers, ci"X-Fail-After-Release").contains("true")
          val reason = body.reason.getOrElse("payment-declined")

          service
            .handlePaymentCallback(body.sagaId, body.approved, reason, failAfterRelease)
            .transact(xa)
            .flatMap(Ok(_))
            .handleErrorWith(error => InternalServerError(ErrorResponse(error.getMessage)))
        }
    }.orNotFound

  def transactorResource(dbName: String): Resource[IO, Transactor[IO]] =
    Resource.eval(
      IO(
        Transactor.fromDriverManager[IO](
          driver = "org.h2.Driver",
          url = s"jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1",
          user = "sa",
          password = "",
          logHandler = None
        )
      )
    )

  val createSchema: ConnectionIO[Unit] =
    for {
      _ <- sql"""
        create table if not exists inventory_item (
          sku varchar primary key,
          stock int not null
        )
      """.update.run
      _ <- sql"""
        create table if not exists reservation_hold (
          order_id varchar primary key,
          sku varchar not null,
          quantity int not null,
          status varchar not null
        )
      """.update.run
      _ <- sql"""
        create table if not exists saga_instance (
          saga_id varchar primary key,
          order_id varchar not null unique,
          sku varchar not null,
          quantity int not null,
          amount decimal(18, 2) not null,
          status varchar not null,
          compensation_reason varchar
        )
      """.update.run
    } yield ()

  val seed: ConnectionIO[Unit] =
    Update[(String, Int)](
      "insert into inventory_item (sku, stock) values (?, ?)"
    ).updateMany(
      List(
        ("BTC-101", 10),
        ("ETH-202", 6)
      )
    ).void

  def environmentResource(label: String): Resource[IO, Environment] =
    transactorResource(s"scala-saga-suite-$label-${UUID.randomUUID().toString.take(8)}").evalTap(xa => (createSchema *> seed).transact(xa)).map { xa =>
      val repo = new DoobieSagaRepository
      val service = new CheckoutService(repo)
      val app = buildApp(service, xa)
      Environment(repo, service, xa, app)
    }

  def jsonRequest[A](method: Method, uri: Uri, body: A, failAfterRelease: Boolean = false)(implicit encoder: io.circe.Encoder[A]): Request[IO] = {
    val base = Request[IO](method, uri)
      .withEntity(body.asJson.noSpaces)
      .putHeaders(Header.Raw(ci"Content-Type", "application/json"))

    if (failAfterRelease) {
      base.putHeaders(Header.Raw(ci"X-Fail-After-Release", "true"))
    } else {
      base
    }
  }
}

class MUnitSagaIntegrationSuite extends CatsEffectSuite {
  import MUnitSagaIntegrationSuite._

  test("拒绝支付后执行补偿，重复回调只返回 replayed") {
    environmentResource("compensated").use { env =>
      for {
        checkoutResponse <- env.app(
          jsonRequest(Method.POST, uri"/checkouts", CheckoutRequest("order-100", "BTC-101", 2, BigDecimal(128.5)))
        )
        _ = assertEquals(checkoutResponse.status, Status.Accepted)
        accepted <- checkoutResponse.as[CheckoutAccepted]

        firstCallbackResponse <- env.app(
          jsonRequest(
            Method.POST,
            uri"/payments/callback",
            PaymentCallbackRequest(accepted.sagaId, approved = false, reason = Some("payment-declined"))
          )
        )
        _ = assertEquals(firstCallbackResponse.status, Status.Ok)
        firstAck <- firstCallbackResponse.as[PaymentAck]
        _ = assertEquals(firstAck.status, "compensated")
        _ = assertEquals(firstAck.replayed, false)

        secondCallbackResponse <- env.app(
          jsonRequest(
            Method.POST,
            uri"/payments/callback",
            PaymentCallbackRequest(accepted.sagaId, approved = false, reason = Some("payment-declined"))
          )
        )
        _ = assertEquals(secondCallbackResponse.status, Status.Ok)
        secondAck <- secondCallbackResponse.as[PaymentAck]
        _ = assertEquals(secondAck.status, "compensated")
        _ = assertEquals(secondAck.replayed, true)

        stock <- env.repo.currentStock("BTC-101").transact(env.xa)
        saga <- env.repo.findSaga(accepted.sagaId).transact(env.xa)
        reservation <- env.repo.findReservation("order-100").transact(env.xa)
        _ = assertEquals(stock, Some(10))
        _ = assertEquals(saga.map(_.status), Some("compensated"))
        _ = assertEquals(reservation.map(_.status), Some("released"))
      } yield ()
    }
  }

  test("补偿事务中途失败会整体回滚，后续可以安全重试") {
    environmentResource("rollback").use { env =>
      for {
        checkoutResponse <- env.app(
          jsonRequest(Method.POST, uri"/checkouts", CheckoutRequest("order-200", "ETH-202", 2, BigDecimal(88.0)))
        )
        _ = assertEquals(checkoutResponse.status, Status.Accepted)
        accepted <- checkoutResponse.as[CheckoutAccepted]

        failedCallbackResponse <- env.app(
          jsonRequest(
            Method.POST,
            uri"/payments/callback",
            PaymentCallbackRequest(accepted.sagaId, approved = false, reason = Some("payment-declined")),
            failAfterRelease = true
          )
        )
        _ = assertEquals(failedCallbackResponse.status, Status.InternalServerError)
        failedError <- failedCallbackResponse.as[ErrorResponse]
        _ = assert(clue(failedError.error).contains("模拟在释放库存后"))

        stockAfterFail <- env.repo.currentStock("ETH-202").transact(env.xa)
        sagaAfterFail <- env.repo.findSaga(accepted.sagaId).transact(env.xa)
        reservationAfterFail <- env.repo.findReservation("order-200").transact(env.xa)
        _ = assertEquals(stockAfterFail, Some(4))
        _ = assertEquals(sagaAfterFail.map(_.status), Some("waiting-payment"))
        _ = assertEquals(reservationAfterFail.map(_.status), Some("reserved"))

        retryCallbackResponse <- env.app(
          jsonRequest(
            Method.POST,
            uri"/payments/callback",
            PaymentCallbackRequest(accepted.sagaId, approved = false, reason = Some("payment-declined"))
          )
        )
        _ = assertEquals(retryCallbackResponse.status, Status.Ok)
        retryAck <- retryCallbackResponse.as[PaymentAck]
        _ = assertEquals(retryAck.status, "compensated")
        _ = assertEquals(retryAck.replayed, false)

        stockAfterRetry <- env.repo.currentStock("ETH-202").transact(env.xa)
        sagaAfterRetry <- env.repo.findSaga(accepted.sagaId).transact(env.xa)
        reservationAfterRetry <- env.repo.findReservation("order-200").transact(env.xa)
        _ = assertEquals(stockAfterRetry, Some(6))
        _ = assertEquals(sagaAfterRetry.map(_.status), Some("compensated"))
        _ = assertEquals(reservationAfterRetry.map(_.status), Some("released"))
      } yield ()
    }
  }

  test("支付成功会完成 Saga 并保留库存消耗结果") {
    environmentResource("completed").use { env =>
      for {
        checkoutResponse <- env.app(
          jsonRequest(Method.POST, uri"/checkouts", CheckoutRequest("order-300", "BTC-101", 1, BigDecimal(66.0)))
        )
        _ = assertEquals(checkoutResponse.status, Status.Accepted)
        accepted <- checkoutResponse.as[CheckoutAccepted]

        callbackResponse <- env.app(
          jsonRequest(
            Method.POST,
            uri"/payments/callback",
            PaymentCallbackRequest(accepted.sagaId, approved = true, reason = None)
          )
        )
        _ = assertEquals(callbackResponse.status, Status.Ok)
        ack <- callbackResponse.as[PaymentAck]
        _ = assertEquals(ack.status, "completed")
        _ = assertEquals(ack.replayed, false)

        stock <- env.repo.currentStock("BTC-101").transact(env.xa)
        saga <- env.repo.findSaga(accepted.sagaId).transact(env.xa)
        reservation <- env.repo.findReservation("order-300").transact(env.xa)
        _ = assertEquals(stock, Some(9))
        _ = assertEquals(saga.map(_.status), Some("completed"))
        _ = assertEquals(reservation.map(_.status), Some("consumed"))
      } yield ()
    }
  }
}
