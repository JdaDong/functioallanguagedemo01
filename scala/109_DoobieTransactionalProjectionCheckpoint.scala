//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.tpolecat::doobie-core:1.0.0-RC8"
//> using dep "org.tpolecat::doobie-h2:1.0.0-RC8"

/**
 * Scala 函数式编程 Demo 109: Doobie 事务投影 checkpoint
 *
 * 108 号 Demo 已经把读模型查询边界讲清楚了，
 * 这一版继续把真正关键的一步补齐：
 * 读模型更新和 projection checkpoint 推进必须放进同一个数据库事务。
 */
import cats.effect.{IO, IOApp, Resource}
import cats.syntax.all._
import doobie._
import doobie.implicits._

import java.util.UUID

object DoobieTransactionalProjectionCheckpoint extends IOApp.Simple {

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

  trait ProjectionRepository[F[_]] {
    def findCheckpoint(projectorName: String): F[Option[Long]]
    def upsertCheckpoint(projectorName: String, lastSeq: Long): F[Unit]
    def nextEventAfter(seq: Long): F[Option[DomainEvent]]
    def findReadModel(orderId: String): F[Option[OrderReadModel]]
    def upsertReadModel(view: OrderReadModel): F[Unit]
    def loadReadModels: F[List[OrderReadModel]]
    def totalEvents: F[Int]
  }

  final class DoobieProjectionRepository extends ProjectionRepository[ConnectionIO] {
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

    def loadReadModels: ConnectionIO[List[OrderReadModel]] =
      sql"select order_id, sku, quantity, payment_status, total_paid from order_read_model order by order_id"
        .query[OrderReadModel]
        .to[List]

    def totalEvents: ConnectionIO[Int] =
      sql"select count(*) from event_log".query[Int].unique
  }

  final class ProjectionService(repo: ProjectionRepository[ConnectionIO], projectorName: String) {

    def catchUpOnce(failAfterApplySeq: Option[Long]): ConnectionIO[Option[Long]] =
      for {
        checkpoint <- repo.findCheckpoint(projectorName).map(_.getOrElse(0L))
        nextEvent <- repo.nextEventAfter(checkpoint)
        applied <- nextEvent.traverse { event =>
          for {
            current <- repo.findReadModel(event.orderId)
            nextView <- evolve(current, event)
            _ <- repo.upsertReadModel(nextView)
            _ <-
              if (failAfterApplySeq.contains(event.seq)) {
                new RuntimeException("模拟在更新读模型后、推进 checkpoint 前崩溃").raiseError[ConnectionIO, Unit]
              } else {
                ().pure[ConnectionIO]
              }
            _ <- repo.upsertCheckpoint(projectorName, event.seq)
          } yield event.seq
        }
      } yield applied

    def catchUpAll(xa: Transactor[IO], failAfterApplySeq: Option[Long]): IO[List[Long]] = {
      def loop(acc: List[Long]): IO[List[Long]] =
        catchUpOnce(failAfterApplySeq).transact(xa).flatMap {
          case Some(seq) => loop(seq :: acc)
          case None      => acc.reverse.pure[IO]
        }

      loop(Nil)
    }

    private def evolve(current: Option[OrderReadModel], event: DomainEvent): ConnectionIO[OrderReadModel] =
      event.eventType match {
        case "OrderCreated" =>
          OrderReadModel(
            orderId = event.orderId,
            sku = event.sku.getOrElse("unknown").trim,
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

  val seed: ConnectionIO[Unit] =
    Update[(Long, String, String, Option[String], Option[Int], Option[BigDecimal])](
      "insert into event_log (seq, event_type, order_id, sku, quantity, amount) values (?, ?, ?, ?, ?, ?)"
    ).updateMany(
      List(
        (1L, "OrderCreated", "order-100", Some("BTC-101"), Some(2), None),
        (2L, "PaymentCaptured", "order-100", None, None, Some(BigDecimal(128.5))),
        (3L, "OrderCreated", "order-200", Some("ETH-202"), Some(1), None)
      )
    ).void

  val run: IO[Unit] =
    transactorResource("demo109").use { xa =>
      val repo = new DoobieProjectionRepository
      val service = new ProjectionService(repo, "orders-read-model")

      for {
        _ <- IO.println("=== Doobie 事务投影 checkpoint ===")
        _ <- (createSchema *> seed *> repo.upsertCheckpoint("orders-read-model", 0L)).transact(xa)

        first <- service.catchUpOnce(None).transact(xa)
        _ <- IO.println(s"第一次推进: $first")

        failed <- service.catchUpOnce(Some(2L)).transact(xa).attempt
        _ <- IO.println(s"第二次推进(模拟失败): ${failed.leftMap(_.getMessage)}")
        checkpointAfterFail <- repo.findCheckpoint("orders-read-model").transact(xa)
        viewsAfterFail <- repo.loadReadModels.transact(xa)
        _ <- IO.println(s"失败后 checkpoint: $checkpointAfterFail")
        _ <- IO.println(s"失败后读模型: $viewsAfterFail")

        rest <- service.catchUpAll(xa, None)
        checkpointFinal <- repo.findCheckpoint("orders-read-model").transact(xa)
        viewsFinal <- repo.loadReadModels.transact(xa)
        totalEvents <- repo.totalEvents.transact(xa)
        _ <- IO.println(s"重试后继续推进: $rest")
        _ <- IO.println(s"最终 checkpoint: $checkpointFinal / totalEvents=$totalEvents")
        _ <- IO.println(s"最终读模型: $viewsFinal")

        _ <- IO.println("\n=== 重点理解 ===")
        _ <- IO.println("- 投影写入和 checkpoint 推进必须在同一事务里，否则就会出现‘读模型改了，但 offset 没记住’的脏状态")
        _ <- IO.println("- 一旦在推进 checkpoint 前崩溃，这个事件对应的读模型更新也必须一起回滚")
        _ <- IO.println("- 下一版会把 HTTP 查询、事务 checkpoint 和 replay 重建一起纳入 MUnit 自动化回归")
      } yield ()
    }
}
