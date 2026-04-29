//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.tpolecat::doobie-core:1.0.0-RC8"
//> using dep "org.tpolecat::doobie-h2:1.0.0-RC8"

/**
 * Scala 函数式编程 Demo 104: Doobie 事务 Saga 状态
 *
 * 103 号 Demo 已经把 Saga 的 HTTP 边界讲清楚了，
 * 这一版继续把真正关键的一步补齐：
 * Saga 状态推进、库存预留和补偿释放必须落到同一个数据库事务边界。
 */
import cats.effect.{IO, IOApp, Resource}
import cats.syntax.all._
import doobie._
import doobie.implicits._

import java.util.UUID

object DoobieTransactionalSagaState extends IOApp.Simple {

  final case class InventoryItem(sku: String, stock: Int)
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
  final case class SagaDecision(sagaId: String, status: String, replayed: Boolean)

  trait SagaRepository[F[_]] {
    def currentStock(sku: String): F[Option[Int]]
    def updateStock(sku: String, stock: Int): F[Unit]
    def insertReservation(row: ReservationRow): F[Unit]
    def findReservation(orderId: String): F[Option[ReservationRow]]
    def updateReservationStatus(orderId: String, status: String): F[Unit]
    def insertSaga(row: SagaRow): F[Unit]
    def findSaga(sagaId: String): F[Option[SagaRow]]
    def updateSagaStatus(sagaId: String, status: String, compensationReason: Option[String]): F[Unit]
    def loadInventory: F[List[InventoryItem]]
    def loadReservations: F[List[ReservationRow]]
    def loadSagas: F[List[SagaRow]]
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

    def loadInventory: ConnectionIO[List[InventoryItem]] =
      sql"select sku, stock from inventory_item order by sku".query[InventoryItem].to[List]

    def loadReservations: ConnectionIO[List[ReservationRow]] =
      sql"select order_id, sku, quantity, status from reservation_hold order by order_id".query[ReservationRow].to[List]

    def loadSagas: ConnectionIO[List[SagaRow]] =
      sql"select saga_id, order_id, sku, quantity, amount, status, compensation_reason from saga_instance order by saga_id"
        .query[SagaRow]
        .to[List]
  }

  final class CheckoutService(repo: SagaRepository[ConnectionIO]) {

    def startCheckout(
        sagaId: String,
        orderId: String,
        sku: String,
        quantity: Int,
        amount: BigDecimal
    ): ConnectionIO[SagaDecision] =
      for {
        available <- requireStock(sku)
        _ <-
          if (available >= quantity) ().pure[ConnectionIO]
          else new RuntimeException(s"库存不足: sku=$sku, available=$available, requested=$quantity").raiseError[ConnectionIO, Unit]
        _ <- repo.updateStock(sku, available - quantity)
        _ <- repo.insertReservation(ReservationRow(orderId, sku, quantity, status = "reserved"))
        _ <- repo.insertSaga(SagaRow(sagaId, orderId, sku, quantity, amount, status = "waiting-payment", compensationReason = None))
      } yield SagaDecision(sagaId, status = "waiting-payment", replayed = false)

    def handlePaymentCallback(
        sagaId: String,
        approved: Boolean,
        compensationReason: String,
        failAfterRelease: Boolean
    ): ConnectionIO[SagaDecision] =
      repo.findSaga(sagaId).flatMap {
        case None =>
          new RuntimeException(s"找不到 saga: $sagaId").raiseError[ConnectionIO, SagaDecision]

        case Some(saga) if saga.status != "waiting-payment" =>
          SagaDecision(sagaId, status = saga.status, replayed = true).pure[ConnectionIO]

        case Some(saga) if approved =>
          for {
            _ <- repo.updateReservationStatus(saga.orderId, "consumed")
            _ <- repo.updateSagaStatus(sagaId, "completed", None)
          } yield SagaDecision(sagaId, status = "completed", replayed = false)

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
          } yield SagaDecision(sagaId, status = "compensated", replayed = false)
      }

    private def requireStock(sku: String): ConnectionIO[Int] =
      repo.currentStock(sku).flatMap {
        case Some(stock) => stock.pure[ConnectionIO]
        case None => new RuntimeException(s"库存不存在: $sku").raiseError[ConnectionIO, Int]
      }
  }

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

  val run: IO[Unit] =
    transactorResource(s"scala-saga-${UUID.randomUUID().toString.take(8)}").use { xa =>
      val repo = new DoobieSagaRepository
      val service = new CheckoutService(repo)

      for {
        _ <- IO.println("=== Doobie 事务 Saga 状态 ===")
        _ <- (createSchema *> seed).transact(xa)

        created1 <- service.startCheckout("saga-100", "order-100", "BTC-101", 2, BigDecimal(128.5)).transact(xa)
        _ <- IO.println(s"创建第一个 Saga: $created1")

        failedCompensation <-
          service
            .handlePaymentCallback("saga-100", approved = false, compensationReason = "payment-declined", failAfterRelease = true)
            .transact(xa)
            .attempt
        _ <- IO.println(s"第一次补偿结果: ${failedCompensation.leftMap(_.getMessage)}")
        inventoryAfterFail <- repo.loadInventory.transact(xa)
        reservationsAfterFail <- repo.loadReservations.transact(xa)
        sagasAfterFail <- repo.loadSagas.transact(xa)
        _ <- IO.println(s"失败后库存: $inventoryAfterFail")
        _ <- IO.println(s"失败后预留: $reservationsAfterFail")
        _ <- IO.println(s"失败后 Saga: $sagasAfterFail")

        compensated <-
          service
            .handlePaymentCallback("saga-100", approved = false, compensationReason = "payment-declined", failAfterRelease = false)
            .transact(xa)
        _ <- IO.println(s"补偿成功结果: $compensated")

        created2 <- service.startCheckout("saga-200", "order-200", "ETH-202", 1, BigDecimal(88.0)).transact(xa)
        _ <- IO.println(s"创建第二个 Saga: $created2")
        completed <-
          service
            .handlePaymentCallback("saga-200", approved = true, compensationReason = "approved", failAfterRelease = false)
            .transact(xa)
        _ <- IO.println(s"成功完成结果: $completed")

        finalInventory <- repo.loadInventory.transact(xa)
        finalReservations <- repo.loadReservations.transact(xa)
        finalSagas <- repo.loadSagas.transact(xa)
        _ <- IO.println(s"最终库存: $finalInventory")
        _ <- IO.println(s"最终预留: $finalReservations")
        _ <- IO.println(s"最终 Saga: $finalSagas")

        _ <- IO.println("\n=== 重点理解 ===")
        _ <- IO.println("- Saga 状态推进和补偿动作必须跟数据库事务边界一起设计，否则很容易留下半条脏数据")
        _ <- IO.println("- 即使在释放库存之后立刻崩溃，只要事务还没提交，这次补偿就应该整体回滚")
        _ <- IO.println("- 下一版会把 HTTP 工作流和事务 Saga 状态一起纳入自动化回归")
      } yield ()
    }
}
