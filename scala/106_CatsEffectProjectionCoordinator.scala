//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"

/**
 * Scala 函数式编程 Demo 106: cats-effect 读模型投影协调器
 *
 * 101 到 105 号 Demo 已经把跨服务 Saga 的写侧一致性讲清楚了，
 * 这一组继续看查询侧：
 * 事件已经可靠落下之后，怎样安全推进读模型，并用 checkpoint 保证失败后可续跑？
 */
import cats.effect.{IO, IOApp, Ref}
import cats.syntax.all._

object CatsEffectProjectionCoordinator extends IOApp.Simple {

  sealed trait DomainEvent {
    def seq: Long
    def orderId: String
  }

  object DomainEvent {
    final case class OrderCreated(seq: Long, orderId: String, sku: String, quantity: Int) extends DomainEvent
    final case class PaymentCaptured(seq: Long, orderId: String, amount: BigDecimal) extends DomainEvent
  }

  final case class OrderReadModel(
      orderId: String,
      sku: String,
      quantity: Int,
      paymentStatus: String,
      totalPaid: BigDecimal
  )

  final case class ProjectionSnapshot(lastAppliedOffset: Long, orders: Map[String, OrderReadModel], remaining: Long)

  final case class ProjectionState(
      lastAppliedOffset: Long,
      orders: Map[String, OrderReadModel],
      failFirstOn: Set[Long]
  )

  final class ProjectionService private (state: Ref[IO, ProjectionState], eventLog: Vector[DomainEvent]) {

    def catchUpAll: IO[Unit] =
      nextPendingEvent.flatMap {
        case None => IO.println("[projection] 没有新的事件需要推进")
        case Some(event) =>
          applyEvent(event) *> catchUpAll
      }

    def snapshot: IO[ProjectionSnapshot] =
      state.get.map { current =>
        ProjectionSnapshot(
          lastAppliedOffset = current.lastAppliedOffset,
          orders = current.orders,
          remaining = eventLog.count(_.seq > current.lastAppliedOffset).toLong
        )
      }

    private def nextPendingEvent: IO[Option[DomainEvent]] =
      state.get.map(current => eventLog.find(_.seq > current.lastAppliedOffset))

    private def applyEvent(event: DomainEvent): IO[Unit] =
      state.modify { current =>
        if (current.failFirstOn.contains(event.seq)) {
          current.copy(failFirstOn = current.failFirstOn - event.seq) ->
            Left(new RuntimeException(s"投影存储暂时不可用: seq=${event.seq}"))
        } else {
          val updatedOrders = updateReadModel(current.orders, event)
          current.copy(lastAppliedOffset = event.seq, orders = updatedOrders) -> Right(updatedOrders(event.orderId))
        }
      }.flatMap {
        case Right(view) =>
          IO.println(s"[projection] 已应用事件 seq=${event.seq}, orderId=${event.orderId}, view=$view")

        case Left(error) =>
          IO.println(s"[projection] 应用失败，checkpoint 保持不变: seq=${event.seq}, error=${error.getMessage}") *>
            IO.raiseError(error)
      }

    private def updateReadModel(
        current: Map[String, OrderReadModel],
        event: DomainEvent
    ): Map[String, OrderReadModel] =
      event match {
        case DomainEvent.OrderCreated(_, orderId, sku, quantity) =>
          current.updated(
            orderId,
            OrderReadModel(
              orderId = orderId,
              sku = sku.trim,
              quantity = quantity,
              paymentStatus = "awaiting-payment",
              totalPaid = BigDecimal(0)
            )
          )

        case DomainEvent.PaymentCaptured(_, orderId, amount) =>
          val existing = current.getOrElse(
            orderId,
            throw new IllegalStateException(s"读模型缺少前置订单事件: orderId=$orderId")
          )

          current.updated(
            orderId,
            existing.copy(
              paymentStatus = "paid",
              totalPaid = existing.totalPaid + amount
            )
          )
      }
  }

  object ProjectionService {
    def create(eventLog: Vector[DomainEvent]): IO[ProjectionService] =
      Ref.of[IO, ProjectionState](
        ProjectionState(
          lastAppliedOffset = 0L,
          orders = Map.empty,
          failFirstOn = Set(2L)
        )
      ).map(new ProjectionService(_, eventLog))
  }

  val eventLog: Vector[DomainEvent] = Vector(
    DomainEvent.OrderCreated(1L, "order-100", "BTC-101", 2),
    DomainEvent.PaymentCaptured(2L, "order-100", BigDecimal(128.5)),
    DomainEvent.OrderCreated(3L, "order-200", "ETH-202", 1)
  )

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== cats-effect 读模型投影协调器 ===")
      service <- ProjectionService.create(eventLog)

      firstAttempt <- service.catchUpAll.attempt
      _ <- IO.println(s"第一次推进结果: ${firstAttempt.leftMap(_.getMessage)}")
      afterFailure <- service.snapshot
      _ <- IO.println(s"失败后快照: $afterFailure")

      _ <- service.catchUpAll
      finalSnapshot <- service.snapshot
      _ <- IO.println(s"重试完成后的快照: $finalSnapshot")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- 读模型投影的关键不是‘尽快写进去’，而是只有应用成功后才能推进 checkpoint")
      _ <- IO.println("- 一旦某个事件应用失败，后续事件必须停住，等待从同一个 offset 安全续跑")
      _ <- IO.println("- 这一版先讲清内存里的最小 checkpoint 语义，后面会继续推进到 fs2 回放流、HTTP 查询边界和数据库事务")
    } yield ()
}
