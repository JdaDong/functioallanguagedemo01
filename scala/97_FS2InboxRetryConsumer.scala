//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.12.2"

/**
 * Scala 函数式编程 Demo 97: fs2 Inbox 重试消费流
 *
 * 96 号 Demo 先把消费端幂等的最小模型讲清楚了，
 * 这一版继续把 at-least-once 投递下的“失败重试 + 重复接收”放到 fs2 流里。
 */
import cats.effect.{IO, IOApp, Ref}
import cats.syntax.all._
import fs2.Stream

import scala.concurrent.duration._

object FS2InboxRetryConsumer extends IOApp.Simple {

  final case class Delivery(
      deliveryId: Long,
      eventId: String,
      orderId: String,
      sku: String,
      quantity: Int,
      attempts: Int
  )
  final case class ConsumeTrace(deliveryId: Long, eventId: String, status: String, attempts: Int)

  final class PendingInbox private (state: Ref[IO, Vector[Delivery]]) {
    def loadPending: IO[Vector[Delivery]] = state.get

    def seed(deliveries: List[Delivery]): IO[Unit] =
      state.set(deliveries.toVector)

    def bumpAttempts(deliveryId: Long): IO[Unit] =
      state.update(_.map { delivery =>
        if (delivery.deliveryId == deliveryId) delivery.copy(attempts = delivery.attempts + 1)
        else delivery
      })

    def ack(deliveryId: Long): IO[Unit] =
      state.update(_.filterNot(_.deliveryId == deliveryId))
  }

  object PendingInbox {
    def create: IO[PendingInbox] =
      Ref.of[IO, Vector[Delivery]](Vector.empty).map(new PendingInbox(_))
  }

  def fingerprintOf(delivery: Delivery): String =
    s"${delivery.orderId}|${delivery.sku.trim}|${delivery.quantity}"

  def consumeDelivery(
      delivery: Delivery,
      processed: Ref[IO, Map[String, String]],
      failOnce: Ref[IO, Set[String]]
  ): IO[ConsumeTrace] =
    processed.get.flatMap { seen =>
      val fingerprint = fingerprintOf(delivery)

      seen.get(delivery.eventId) match {
        case Some(saved) if saved == fingerprint =>
          IO.println(s"[consumer] 命中重复事件，直接确认: delivery=${delivery.deliveryId}, eventId=${delivery.eventId}") *>
            ConsumeTrace(delivery.deliveryId, delivery.eventId, "replayed", delivery.attempts).pure[IO]

        case Some(_) =>
          IO.raiseError(new RuntimeException("相同 eventId 不能复用到不同 payload"))

        case None =>
          failOnce.modify { current =>
            if (current.contains(delivery.eventId)) (current - delivery.eventId) -> true
            else current -> false
          }.flatMap { shouldFail =>
            if (shouldFail) {
              IO.println(s"[consumer] 首次消费失败，等待重试: delivery=${delivery.deliveryId}, eventId=${delivery.eventId}") *>
                IO.raiseError(new RuntimeException("projection database timeout"))
            } else {
              processed.update(_ + (delivery.eventId -> fingerprint)) *>
                IO.println(s"[consumer] 已应用事件: delivery=${delivery.deliveryId}, eventId=${delivery.eventId}, orderId=${delivery.orderId}") *>
                ConsumeTrace(delivery.deliveryId, delivery.eventId, "applied", delivery.attempts).pure[IO]
            }
          }
      }
    }

  def retryLoop(
      inbox: PendingInbox,
      processed: Ref[IO, Map[String, String]],
      failOnce: Ref[IO, Set[String]]
  ): Stream[IO, ConsumeTrace] =
    Stream
      .repeatEval(inbox.loadPending)
      .metered(150.millis)
      .takeThrough(_.nonEmpty)
      .flatMap(records => Stream.emits(records).covary[IO])
      .evalMap { delivery =>
        consumeDelivery(delivery, processed, failOnce).attempt.flatMap {
          case Right(trace) =>
            inbox.ack(delivery.deliveryId).as(trace)

          case Left(error) =>
            inbox.bumpAttempts(delivery.deliveryId) *>
              IO.println(s"[consumer] 保留投递等待下一轮: delivery=${delivery.deliveryId}, error=${error.getMessage}") *>
              ConsumeTrace(delivery.deliveryId, delivery.eventId, "retry-scheduled", delivery.attempts + 1).pure[IO]
        }
      }

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== fs2 Inbox 重试消费流 ===")
      inbox <- PendingInbox.create
      _ <- inbox.seed(
        List(
          Delivery(1L, "evt-100", "order-100", "BTC-101", 2, attempts = 0),
          Delivery(2L, "evt-200", "order-200", "ETH-202", 1, attempts = 0),
          Delivery(3L, "evt-100", "order-100", "BTC-101", 2, attempts = 0)
        )
      )
      processed <- Ref.of[IO, Map[String, String]](Map.empty)
      failOnce <- Ref.of[IO, Set[String]](Set("evt-100"))
      traces <- retryLoop(inbox, processed, failOnce).compile.toList
      remaining <- inbox.loadPending
      seen <- processed.get
      _ <- IO.println(s"消费轨迹: $traces")
      _ <- IO.println(s"最终待消费投递: $remaining")
      _ <- IO.println(s"最终已处理事件: ${seen.keys.toList.sorted}")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- fs2 很适合表达‘重复拉取 pending 投递 → 尝试消费 → 失败后等待下一轮’这种后台消费流")
      _ <- IO.println("- 真正的消费端幂等看的是 eventId，而不是 deliveryId；同一事件可以被重复投递很多次")
      _ <- IO.println("- 下一步会把这个思路推进到 http4s webhook 接收边界，并最终下沉到 Doobie 事务 inbox")
    } yield ()
}
