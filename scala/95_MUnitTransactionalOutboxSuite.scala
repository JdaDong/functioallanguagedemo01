//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.tpolecat::doobie-core:1.0.0-RC8"
//> using dep "org.tpolecat::doobie-h2:1.0.0-RC8"
//> using dep "org.typelevel::munit-cats-effect-3:1.0.7"

/**
 * Scala 函数式编程 Demo 95: 事务 Outbox 集成测试
 *
 * 94 号 Demo 已经把事务 outbox 跑通了，
 * 这一版继续把“同事务写入 + 失败保留 pending + 成功后标记 published”放进自动化回归。
 */
import cats.effect.{IO, Ref, Resource}
import cats.syntax.all._
import doobie._
import doobie.implicits._
import munit.CatsEffectSuite

import java.util.UUID

object MUnitTransactionalOutboxSuite {

  final case class OrderRow(orderId: String, sku: String, quantity: Int)
  final case class OutboxRow(id: Long, aggregateId: String, topic: String, payload: String, status: String, attempts: Int)

  trait OrderRepository[F[_]] {
    def insertOrder(order: OrderRow): F[Unit]
    def nextOutboxId: F[Long]
    def insertOutbox(event: OutboxRow): F[Unit]
    def loadPending: F[List[OutboxRow]]
    def findOutbox(id: Long): F[Option[OutboxRow]]
    def markPublished(id: Long): F[Unit]
    def bumpAttempts(id: Long): F[Unit]
    def orderCount: F[Int]
  }

  final class DoobieOrderRepository extends OrderRepository[ConnectionIO] {
    def insertOrder(order: OrderRow): ConnectionIO[Unit] =
      sql"insert into orders (order_id, sku, quantity) values (${order.orderId}, ${order.sku}, ${order.quantity})".update.run.void

    def nextOutboxId: ConnectionIO[Long] =
      sql"select coalesce(max(id), 0) + 1 from outbox_event".query[Long].unique

    def insertOutbox(event: OutboxRow): ConnectionIO[Unit] =
      sql"insert into outbox_event (id, aggregate_id, topic, payload, status, attempts) values (${event.id}, ${event.aggregateId}, ${event.topic}, ${event.payload}, ${event.status}, ${event.attempts})".update.run.void

    def loadPending: ConnectionIO[List[OutboxRow]] =
      sql"select id, aggregate_id, topic, payload, status, attempts from outbox_event where status = 'pending' order by id".query[OutboxRow].to[List]

    def findOutbox(id: Long): ConnectionIO[Option[OutboxRow]] =
      sql"select id, aggregate_id, topic, payload, status, attempts from outbox_event where id = $id".query[OutboxRow].option

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
        _ <- repo.insertOrder(OrderRow(orderId, sku, quantity))
        _ <- repo.insertOutbox(OutboxRow(outboxId, orderId, "order-created", s"$orderId|$sku|$quantity", "pending", 0))
      } yield ()

    def publishPending(xa: Transactor[IO], failFirst: Ref[IO, Set[Long]]): IO[Unit] =
      repo.loadPending.transact(xa).flatMap { pending =>
        pending.foldLeft(IO.unit) { (acc, event) =>
          acc *> failFirst.modify { current =>
            if (current.contains(event.id)) (current - event.id) -> true
            else current -> false
          }.flatMap { shouldFail =>
            if (shouldFail) repo.bumpAttempts(event.id).transact(xa)
            else repo.markPublished(event.id).transact(xa)
          }
        }
      }
  }

  final case class Environment(repo: DoobieOrderRepository, service: OrderService, xa: Transactor[IO])

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

  def withEnvironment[A](label: String)(program: Environment => IO[A]): IO[A] =
    transactorResource(label).use { xa =>
      val repo = new DoobieOrderRepository
      val service = new OrderService(repo)
      createSchema.transact(xa) *> program(Environment(repo, service, xa))
    }
}

class MUnitTransactionalOutboxSuite extends CatsEffectSuite {
  import MUnitTransactionalOutboxSuite._

  test("创建订单时应该同时写入订单和 pending outbox 事件") {
    withEnvironment("create-order") { env =>
      for {
        _ <- env.service.createOrder("order-100", "BTC-101", 2).transact(env.xa)
        orderCount <- env.repo.orderCount.transact(env.xa)
        pending <- env.repo.loadPending.transact(env.xa)
      } yield {
        assertEquals(orderCount, 1)
        assertEquals(pending.map(_.aggregateId), List("order-100"))
        assertEquals(pending.map(_.status), List("pending"))
      }
    }
  }

  test("发布失败时应该保留 pending 并增加 attempts") {
    withEnvironment("publish-failure") { env =>
      for {
        _ <- env.service.createOrder("order-200", "ETH-202", 1).transact(env.xa)
        failFirst <- Ref.of[IO, Set[Long]](Set(1L))
        _ <- env.service.publishPending(env.xa, failFirst)
        pending <- env.repo.loadPending.transact(env.xa)
      } yield {
        assertEquals(pending.map(_.id), List(1L))
        assertEquals(pending.map(_.attempts), List(1))
      }
    }
  }

  test("重试成功后应该把 outbox 标记为 published") {
    withEnvironment("publish-success") { env =>
      for {
        _ <- env.service.createOrder("order-300", "SOL-303", 4).transact(env.xa)
        failFirst <- Ref.of[IO, Set[Long]](Set(1L))
        _ <- env.service.publishPending(env.xa, failFirst)
        _ <- env.service.publishPending(env.xa, failFirst)
        event <- env.repo.findOutbox(1L).transact(env.xa)
        pending <- env.repo.loadPending.transact(env.xa)
      } yield {
        assertEquals(event.map(_.status), Some("published"))
        assertEquals(event.map(_.attempts), Some(1))
        assertEquals(pending, Nil)
      }
    }
  }
}
