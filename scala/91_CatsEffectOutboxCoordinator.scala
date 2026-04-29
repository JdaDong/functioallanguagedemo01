//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"

/**
 * Scala 函数式编程 Demo 91: cats-effect Outbox 协调器
 *
 * 86 到 90 号 Demo 刚刚把幂等写入补齐，
 * 这一组继续处理真实系统里的下一步问题：
 * 业务数据已经写成功了，后续事件应该怎么可靠发布？
 *
 * 第一版先不急着上数据库，先在进程内把最小模型讲清楚：
 *
 * - 业务写入和 outbox 事件一起进入同一份状态
 * - 后台 worker 负责异步发布事件
 * - 发布失败时不要丢，而是保留在 outbox 里等待后续重试
 */
import cats.effect.{IO, IOApp, Ref}
import cats.syntax.all._

object CatsEffectOutboxCoordinator extends IOApp.Simple {

  final case class Order(id: String, sku: String, quantity: Int)
  final case class OutboxEvent(id: Long, topic: String, payload: String, attempts: Int = 0)
  final case class State(
      orders: Map[String, Order],
      outbox: Vector[OutboxEvent],
      nextEventId: Long,
      failFirstPublishFor: Set[Long]
  )

  final class OrderService private (state: Ref[IO, State]) {

    def createOrder(orderId: String, sku: String, quantity: Int): IO[Unit] =
      state.modify { current =>
        val order = Order(orderId, sku, quantity)
        val event = OutboxEvent(
          id = current.nextEventId,
          topic = "order-created",
          payload = s"$orderId|$sku|$quantity"
        )
        val next = current.copy(
          orders = current.orders.updated(orderId, order),
          outbox = current.outbox :+ event,
          nextEventId = current.nextEventId + 1
        )
        next -> event
      }.flatMap(event =>
        IO.println(s"[tx] 创建订单并写入 outbox: orderId=$orderId, eventId=${event.id}")
      )

    def publishNext: IO[Unit] =
      state.get.flatMap(_.outbox.headOption match {
        case None => IO.println("[worker] 当前没有待发布事件")
        case Some(event) =>
          publish(event).attempt.flatMap {
            case Right(_) =>
              state.update(current => current.copy(outbox = current.outbox.drop(1))) *>
                IO.println(s"[worker] 事件发布成功，已从 outbox 删除: eventId=${event.id}")

            case Left(error) =>
              state.update { current =>
                current.outbox.headOption match {
                  case Some(head) if head.id == event.id =>
                    val retried = head.copy(attempts = head.attempts + 1)
                    current.copy(outbox = retried +: current.outbox.drop(1))
                  case _ => current
                }
              } *> IO.println(s"[worker] 发布失败，事件保留等待重试: eventId=${event.id}, error=${error.getMessage}")
          }
      })

    def snapshot: IO[State] = state.get

    private def publish(event: OutboxEvent): IO[Unit] =
      state.modify { current =>
        if (current.failFirstPublishFor.contains(event.id)) {
          current.copy(failFirstPublishFor = current.failFirstPublishFor - event.id) -> true
        } else {
          current -> false
        }
      }.flatMap { shouldFail =>
        if (shouldFail) {
          IO.println(s"[broker] 模拟下游发布失败: eventId=${event.id}") *>
            IO.raiseError(new RuntimeException("broker timeout"))
        } else {
          IO.println(s"[broker] 已发布到 topic=${event.topic}, payload=${event.payload}, attempts=${event.attempts}")
        }
      }
  }

  object OrderService {
    def create: IO[OrderService] =
      Ref.of[IO, State](
        State(
          orders = Map.empty,
          outbox = Vector.empty,
          nextEventId = 1L,
          failFirstPublishFor = Set(1L)
        )
      ).map(new OrderService(_))
  }

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== cats-effect Outbox 协调器 ===")
      service <- OrderService.create
      _ <- service.createOrder("order-100", "BTC-101", 2)
      _ <- service.createOrder("order-200", "ETH-202", 1)

      before <- service.snapshot
      _ <- IO.println(s"初始 outbox 大小: ${before.outbox.size}")

      _ <- service.publishNext
      afterFirst <- service.snapshot
      _ <- IO.println(s"第一次发布后 outbox 大小: ${afterFirst.outbox.size}, 首条 attempts=${afterFirst.outbox.headOption.map(_.attempts).getOrElse(0)}")

      _ <- service.publishNext
      _ <- service.publishNext

      finalState <- service.snapshot
      _ <- IO.println(s"最终订单数: ${finalState.orders.size}")
      _ <- IO.println(s"最终 outbox 大小: ${finalState.outbox.size}")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- Outbox 模式的核心不是‘立刻发出去’，而是‘先和业务数据一起可靠落下’")
      _ <- IO.println("- 后台发布失败时事件不能丢，应该留在 outbox 里等待后续重试")
      _ <- IO.println("- 下一步会把这个思路推进到 fs2 发布流、HTTP 回调边界和 Doobie 事务 outbox")
    } yield ()
}
