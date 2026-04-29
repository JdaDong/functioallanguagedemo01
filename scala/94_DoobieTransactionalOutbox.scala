//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.tpolecat::doobie-core:1.0.0-RC8"
//> using dep "org.tpolecat::doobie-h2:1.0.0-RC8"

/**
 * Scala 函数式编程 Demo 94: Doobie 事务 Outbox
 *
 * 93 号 Demo 已经把 webhook 发布边界讲清楚了，
 * 这一版继续把真正关键的一步补齐：
 * 订单写入和 outbox 事件插入必须放进同一个数据库事务。
 */
import cats.effect.{IO, IOApp, Resource}
import cats.syntax.all._
import doobie._
import doobie.implicits._

import java.util.UUID

object DoobieTransactionalOutbox extends IOApp.Simple {

  final case class OrderRow(orderId: String, sku: String, quantity: Int)
  final case class OutboxRow(id: Long, aggregateId: String, topic: String, payload: String, status: String, attempts: Int)

  trait OrderRepository[F[_]] {
    def insertOrder(order: OrderRow): F[Unit]
    def nextOutboxId: F[Long]
    def insertOutbox(event: OutboxRow): F[Unit]
    def loadPending: F[List[OutboxRow]]
    def markPublished(id: Long): F[Unit]
    def bumpAttempts(id: Long): F[Unit]
    def orderCount: F[Int]
  }

  final class DoobieOrderRepository extends OrderRepository[ConnectionIO] {
    def insertOrder(order: OrderRow): ConnectionIO[Unit] =
      sql"insert into orders (order_id, sku, quantity) values (${order.orderId}, ${order.sku}, ${order.quantity})"
        .update
        .run
        .void

    def nextOutboxId: ConnectionIO[Long] =
      sql"select coalesce(max(id), 0) + 1 from outbox_event".query[Long].unique

    def insertOutbox(event: OutboxRow): ConnectionIO[Unit] =
      sql"insert into outbox_event (id, aggregate_id, topic, payload, status, attempts) values (${event.id}, ${event.aggregateId}, ${event.topic}, ${event.payload}, ${event.status}, ${event.attempts})"
        .update
        .run
        .void

    def loadPending: ConnectionIO[List[OutboxRow]] =
      sql"select id, aggregate_id, topic, payload, status, attempts from outbox_event where status = 'pending' order by id"
        .query[OutboxRow]
        .to[List]

    def markPublished(id: Long): ConnectionIO[Unit] =
      sql"update outbox_event set status = 'published' where id = $id".update.run.void

    def bumpAttempts(id: Long): ConnectionIO[Unit] =
      sql"update outbox_event set attempts = attempts + 1 where id = $id".update.run.void

    def orderCount: ConnectionIO[Int] =
      sql"select count(*) from orders".query[Int].unique
  }

  final class OrderService(repo: OrderRepository[ConnectionIO]) {
    def createOrder(orderId: String, sku: String, quantity: Int): ConnectionIO[Unit] =
      for {
        outboxId <- repo.nextOutboxId
        order = OrderRow(orderId, sku, quantity)
        event = OutboxRow(outboxId, orderId, "order-created", s"$orderId|$sku|$quantity", "pending", attempts = 0)
        _ <- repo.insertOrder(order)
        _ <- repo.insertOutbox(event)
      } yield ()

    def publishPending(xa: Transactor[IO], failFirst: cats.effect.Ref[IO, Set[Long]]): IO[Unit] =
      repo.loadPending.transact(xa).flatMap { pending =>
        pending.traverse_ { event =>
          failFirst.modify { current =>
            if (current.contains(event.id)) (current - event.id) -> true
            else current -> false
          }.flatMap { shouldFail =>
            if (shouldFail) {
              IO.println(s"[publisher] 模拟失败，保留 pending: eventId=${event.id}") *>
                repo.bumpAttempts(event.id).transact(xa)
            } else {
              IO.println(s"[publisher] 发布成功: eventId=${event.id}, payload=${event.payload}") *>
                repo.markPublished(event.id).transact(xa)
            }
          }
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
      _ <- sql"create table orders (order_id varchar primary key, sku varchar not null, quantity int not null)".update.run
      _ <- sql"create table outbox_event (id bigint primary key, aggregate_id varchar not null, topic varchar not null, payload varchar not null, status varchar not null, attempts int not null)".update.run
    } yield ()

  val run: IO[Unit] =
    transactorResource("demo94").use { xa =>
      val repo = new DoobieOrderRepository
      val service = new OrderService(repo)

      for {
        _ <- IO.println("=== Doobie 事务 Outbox ===")
        _ <- createSchema.transact(xa)
        _ <- service.createOrder("order-100", "BTC-101", 2).transact(xa)
        _ <- service.createOrder("order-200", "ETH-202", 1).transact(xa)
        count <- repo.orderCount.transact(xa)
        pendingBefore <- repo.loadPending.transact(xa)
        _ <- IO.println(s"订单数: $count, 初始 pending outbox: $pendingBefore")

        failFirst <- cats.effect.Ref.of[IO, Set[Long]](Set(1L))
        _ <- service.publishPending(xa, failFirst)
        afterFirst <- repo.loadPending.transact(xa)
        _ <- IO.println(s"第一次发布后 pending outbox: $afterFirst")

        _ <- service.publishPending(xa, failFirst)
        finalPending <- repo.loadPending.transact(xa)
        _ <- IO.println(s"第二次发布后 pending outbox: $finalPending")

        _ <- IO.println("\n=== 重点理解 ===")
        _ <- IO.println("- 真正关键的是‘订单写入 + outbox 插入’同事务成功或失败，而不是分两步碰碰运气")
        _ <- IO.println("- 发布动作可以慢一点、晚一点，但 outbox 记录必须先可靠落下")
        _ <- IO.println("- 下一版会用 MUnit 把这个事务 outbox 闭环做成集成测试")
      } yield ()
    }
}
