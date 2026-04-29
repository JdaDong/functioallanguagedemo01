//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"

/**
 * Scala 函数式编程 Demo 96: cats-effect Inbox 协调器
 *
 * 91 到 95 号 Demo 已经把事件可靠发出的 Outbox 链路补齐了，
 * 这一组继续看下游消费者这边：
 * 重复 webhook / 重试投递打过来时，怎样保证同一个 eventId 只真正应用一次？
 */
import cats.effect.{IO, IOApp, Ref}
import cats.syntax.all._

object CatsEffectInboxCoordinator extends IOApp.Simple {

  final case class OrderCreated(eventId: String, orderId: String, sku: String, quantity: Int)
  final case class ShipmentProjection(orderId: String, sku: String, quantity: Int, status: String)
  final case class ProcessedEvent(eventId: String, fingerprint: String)
  final case class ConsumeResult(orderId: String, replayed: Boolean, projectionCount: Int)
  final case class State(
      shipments: Map[String, ShipmentProjection],
      processed: Map[String, ProcessedEvent],
      failFirst: Set[String]
  )

  final class InboxService private (state: Ref[IO, State]) {

    def consume(event: OrderCreated): IO[ConsumeResult] =
      state.modify { current =>
        val fingerprint = fingerprintOf(event)

        current.processed.get(event.eventId) match {
          case Some(saved) if saved.fingerprint == fingerprint =>
            current -> Right(ConsumeResult(event.orderId, replayed = true, current.shipments.size))

          case Some(_) =>
            current -> Left(new RuntimeException("相同 eventId 不能复用到不同 payload"))

          case None if current.failFirst.contains(event.eventId) =>
            current.copy(failFirst = current.failFirst - event.eventId) ->
              Left(new RuntimeException("warehouse projection store timeout"))

          case None =>
            val projection = ShipmentProjection(event.orderId, event.sku, event.quantity, status = "scheduled")
            val next = current.copy(
              shipments = current.shipments.updated(event.orderId, projection),
              processed = current.processed.updated(event.eventId, ProcessedEvent(event.eventId, fingerprint))
            )
            next -> Right(ConsumeResult(event.orderId, replayed = false, next.shipments.size))
        }
      }.flatMap {
        case Right(result) =>
          IO.println(s"[inbox] 已接收 eventId=${event.eventId}, orderId=${event.orderId}, replayed=${result.replayed}") *>
            result.pure[IO]

        case Left(error) =>
          IO.println(s"[inbox] 接收失败，等待重试: eventId=${event.eventId}, error=${error.getMessage}") *>
            IO.raiseError(error)
      }

    def snapshot: IO[State] = state.get

    private def fingerprintOf(event: OrderCreated): String =
      s"${event.orderId}|${event.sku.trim}|${event.quantity}"
  }

  object InboxService {
    def create: IO[InboxService] =
      Ref.of[IO, State](
        State(
          shipments = Map.empty,
          processed = Map.empty,
          failFirst = Set("evt-100")
        )
      ).map(new InboxService(_))
  }

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== cats-effect Inbox 协调器 ===")
      service <- InboxService.create
      firstEvent = OrderCreated("evt-100", "order-100", "BTC-101", 2)

      failed <- service.consume(firstEvent).attempt
      _ <- IO.println(s"第一次接收结果: ${failed.leftMap(_.getMessage)}")
      afterFailure <- service.snapshot
      _ <- IO.println(s"失败后 shipments=${afterFailure.shipments.size}, processed=${afterFailure.processed.size}")

      success <- service.consume(firstEvent)
      _ <- IO.println(s"重试成功结果: $success")

      replay <- service.consume(firstEvent)
      _ <- IO.println(s"重复投递结果: $replay")

      _ <- service.consume(OrderCreated("evt-200", "order-200", "ETH-202", 1))
      finalState <- service.snapshot
      _ <- IO.println(s"最终 shipments=${finalState.shipments.keys.toList.sorted}")
      _ <- IO.println(s"最终 processed=${finalState.processed.keys.toList.sorted}")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- Inbox 模式解决的是消费端幂等：同一个 eventId 重放时，不要重复落业务结果")
      _ <- IO.println("- 失败时不能先记 processed 再补业务写入，必须保证两者要么都成功，要么都不生效")
      _ <- IO.println("- 下一步会把这个接收闭环推进到 fs2 消费流、http4s webhook 和 Doobie 事务 inbox")
    } yield ()
}
