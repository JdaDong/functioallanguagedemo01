//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.tpolecat::doobie-core:1.0.0-RC8"
//> using dep "org.tpolecat::doobie-h2:1.0.0-RC8"

/**
 * Scala 函数式编程 Demo 99: Doobie 事务 Inbox
 *
 * 98 号 Demo 已经把 webhook 接收边界讲清楚了，
 * 这一版继续把真正关键的一步补齐：
 * projection 写入和 processed_event 记录必须放进同一个数据库事务。
 */
import cats.effect.{IO, IOApp, Resource}
import cats.syntax.all._
import doobie._
import doobie.implicits._

import java.util.UUID

object DoobieTransactionalInbox extends IOApp.Simple {

  final case class OrderCreated(eventId: String, orderId: String, sku: String, quantity: Int)
  final case class ShipmentProjection(orderId: String, sku: String, quantity: Int, status: String)
  final case class ProcessedEvent(eventId: String, fingerprint: String, orderId: String)
  final case class ConsumeResult(orderId: String, replayed: Boolean)

  trait InboxRepository[F[_]] {
    def findProcessed(eventId: String): F[Option[ProcessedEvent]]
    def insertProcessed(event: ProcessedEvent): F[Unit]
    def insertShipment(projection: ShipmentProjection): F[Unit]
    def shipmentCount: F[Int]
    def processedCount: F[Int]
    def loadShipments: F[List[ShipmentProjection]]
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

    def loadShipments: ConnectionIO[List[ShipmentProjection]] =
      sql"select order_id, sku, quantity, status from shipment_projection order by order_id"
        .query[ShipmentProjection]
        .to[List]
  }

  final class ShipmentService(repo: InboxRepository[ConnectionIO]) {

    def consume(event: OrderCreated, failAfterProjection: Boolean): ConnectionIO[ConsumeResult] = {
      val fingerprint = s"${event.orderId}|${event.sku.trim}|${event.quantity}"

      repo.findProcessed(event.eventId).flatMap {
        case Some(saved) if saved.fingerprint == fingerprint =>
          ConsumeResult(event.orderId, replayed = true).pure[ConnectionIO]

        case Some(_) =>
          new RuntimeException("相同 eventId 不能复用到不同 payload").raiseError[ConnectionIO, ConsumeResult]

        case None =>
          for {
            _ <- repo.insertShipment(ShipmentProjection(event.orderId, event.sku, event.quantity, status = "scheduled"))
            _ <-
              if (failAfterProjection)
                new RuntimeException("模拟在写入 projection 后崩溃").raiseError[ConnectionIO, Unit]
              else ().pure[ConnectionIO]
            _ <- repo.insertProcessed(ProcessedEvent(event.eventId, fingerprint, event.orderId))
          } yield ConsumeResult(event.orderId, replayed = false)
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

  val run: IO[Unit] =
    transactorResource("demo99").use { xa =>
      val repo = new DoobieInboxRepository
      val service = new ShipmentService(repo)
      val event = OrderCreated("evt-100", "order-100", "BTC-101", 2)

      for {
        _ <- IO.println("=== Doobie 事务 Inbox ===")
        _ <- createSchema.transact(xa)

        failed <- service.consume(event, failAfterProjection = true).transact(xa).attempt
        _ <- IO.println(s"第一次接收结果: ${failed.leftMap(_.getMessage)}")
        shipmentCountAfterFail <- repo.shipmentCount.transact(xa)
        processedCountAfterFail <- repo.processedCount.transact(xa)
        _ <- IO.println(s"失败回滚后 shipment_count=$shipmentCountAfterFail, processed_count=$processedCountAfterFail")

        success <- service.consume(event, failAfterProjection = false).transact(xa)
        _ <- IO.println(s"重试成功结果: $success")

        replay <- service.consume(event, failAfterProjection = false).transact(xa)
        _ <- IO.println(s"重复投递结果: $replay")

        conflict <- service.consume(event.copy(quantity = 3), failAfterProjection = false).transact(xa).attempt
        _ <- IO.println(s"冲突结果: ${conflict.leftMap(_.getMessage)}")

        shipments <- repo.loadShipments.transact(xa)
        shipmentCount <- repo.shipmentCount.transact(xa)
        processedCount <- repo.processedCount.transact(xa)
        _ <- IO.println(s"当前 shipment_projection=$shipments")
        _ <- IO.println(s"最终 shipment_count=$shipmentCount, processed_count=$processedCount")

        _ <- IO.println("\n=== 重点理解 ===")
        _ <- IO.println("- 事务 Inbox 的核心是 projection 写入和 processed_event 记录必须一起成功或一起回滚")
        _ <- IO.println("- 这样即使消费者在半路崩溃，重试时也不会留下‘业务写了一半，但没记 processed’的脏状态")
        _ <- IO.println("- 下一版会把 webhook 接收端和真实 H2 数据库一起放进 MUnit 自动化回归")
      } yield ()
    }
}
