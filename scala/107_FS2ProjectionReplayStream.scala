//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.12.2"

/**
 * Scala 函数式编程 Demo 107: fs2 读模型回放流
 *
 * 106 号 Demo 已经把投影 checkpoint 的最小语义讲清楚了，
 * 这一版继续把“后台 catch-up + 管理员触发 replay”放进 fs2 长生命周期流里。
 */
import cats.effect.{IO, IOApp, Ref}
import cats.syntax.all._
import fs2.Stream

import scala.concurrent.duration._

object FS2ProjectionReplayStream extends IOApp.Simple {

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

  final case class ProjectionState(
      checkpoint: Long,
      orders: Map[String, OrderReadModel],
      replayFrom: Option[Long],
      failFirstOn: Set[Long]
  )

  final case class Trace(tick: Long, action: String, checkpoint: Long, lag: Long)

  final class ProjectionRuntime private (
      state: Ref[IO, ProjectionState],
      log: Ref[IO, Vector[DomainEvent]]
  ) {

    def append(event: DomainEvent): IO[Unit] =
      log.update(_ :+ event) *> IO.println(s"[log] 追加事件: $event")

    def requestReplay(fromOffset: Long): IO[Unit] =
      state.update(_.copy(replayFrom = Some(fromOffset))) *>
        IO.println(s"[admin] 请求从 offset=$fromOffset 重新回放读模型")

    def snapshot: IO[(ProjectionState, Vector[DomainEvent])] = (state.get, log.get).tupled

    def processTick(tick: Long): IO[List[Trace]] =
      for {
        _ <- maybeResetForReplay(tick)
        traces <- loopPending(tick, Nil)
      } yield traces.reverse

    private def loopPending(tick: Long, acc: List[Trace]): IO[List[Trace]] =
      nextPending.flatMap {
        case None => acc.pure[IO]
        case Some(event) =>
          applyEvent(tick, event).attempt.flatMap {
            case Right(trace) => loopPending(tick, trace :: acc)
            case Left(_)      => acc.pure[IO]
          }
      }

    private def maybeResetForReplay(tick: Long): IO[Unit] =
      state.get.flatMap {
        case ProjectionState(_, _, Some(fromOffset), _) =>
          state.update(_.copy(checkpoint = fromOffset, orders = Map.empty, replayFrom = None)) *>
            IO.println(s"[replay] tick=$tick 已重置读模型，准备从 offset=$fromOffset 重新构建")
        case _ => IO.unit
      }

    private def nextPending: IO[Option[DomainEvent]] =
      (state.get, log.get).mapN { (current, events) =>
        events.sortBy(_.seq).find(_.seq > current.checkpoint)
      }

    private def applyEvent(tick: Long, event: DomainEvent): IO[Trace] =
      log.get.flatMap { events =>
        state.modify { current =>
          if (current.failFirstOn.contains(event.seq)) {
            val lag = events.count(_.seq > current.checkpoint).toLong
            current.copy(failFirstOn = current.failFirstOn - event.seq) ->
              Left(Trace(tick, s"seq=${event.seq} 应用失败，等待下轮重试", current.checkpoint, lag))
          } else {
            val updatedOrders = updateReadModel(current.orders, event)
            val checkpoint = event.seq
            val lag = events.count(_.seq > checkpoint).toLong
            current.copy(checkpoint = checkpoint, orders = updatedOrders) ->
              Right(Trace(tick, s"seq=${event.seq} 应用成功", checkpoint, lag))
          }
        }
      }.flatMap {
        case Right(trace) =>
          IO.println(s"[projection] tick=$tick ${trace.action}, checkpoint=${trace.checkpoint}, lag=${trace.lag}") *>
            trace.pure[IO]

        case Left(trace) =>
          IO.println(s"[projection] tick=$tick ${trace.action}, checkpoint=${trace.checkpoint}, lag=${trace.lag}") *>
            IO.raiseError(new RuntimeException(trace.action))
      }

    private def updateReadModel(
        current: Map[String, OrderReadModel],
        event: DomainEvent
    ): Map[String, OrderReadModel] =
      event match {
        case DomainEvent.OrderCreated(_, orderId, sku, quantity) =>
          current.updated(orderId, OrderReadModel(orderId, sku.trim, quantity, "awaiting-payment", BigDecimal(0)))

        case DomainEvent.PaymentCaptured(_, orderId, amount) =>
          val existing = current.getOrElse(
            orderId,
            throw new IllegalStateException(s"读模型缺少订单事件: orderId=$orderId")
          )
          current.updated(orderId, existing.copy(paymentStatus = "paid", totalPaid = existing.totalPaid + amount))
      }
  }

  object ProjectionRuntime {
    def create(initialLog: Vector[DomainEvent]): IO[ProjectionRuntime] =
      for {
        state <- Ref.of[IO, ProjectionState](ProjectionState(0L, Map.empty, None, failFirstOn = Set(2L)))
        log <- Ref.of[IO, Vector[DomainEvent]](initialLog)
      } yield new ProjectionRuntime(state, log)
  }

  val initialLog: Vector[DomainEvent] = Vector(
    DomainEvent.OrderCreated(1L, "order-100", "BTC-101", 2),
    DomainEvent.PaymentCaptured(2L, "order-100", BigDecimal(128.5))
  )

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== fs2 读模型回放流 ===")
      runtime <- ProjectionRuntime.create(initialLog)
      traces <- Stream
        .range(1, 5)
        .covary[IO]
        .metered(150.millis)
        .evalTap { tick =>
          if (tick == 2) {
            runtime.append(DomainEvent.OrderCreated(3L, "order-200", "ETH-202", 1))
          } else if (tick == 3) {
            runtime.requestReplay(0L)
          } else {
            IO.unit
          }
        }
        .evalMap(tick => runtime.processTick(tick.toLong))
        .flatMap(Stream.emits)
        .compile
        .toList
      snapshot <- runtime.snapshot
      _ <- IO.println(s"回放轨迹: $traces")
      _ <- IO.println(s"最终状态: ${snapshot._1}")
      _ <- IO.println(s"最终事件日志: ${snapshot._2}")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- fs2 很适合托管‘持续 catch-up + 管理员重建 replay’这类后台投影任务")
      _ <- IO.println("- replay 不是把事件重新发一遍，而是重置读模型后按事件日志重新构建查询侧状态")
      _ <- IO.println("- 只要 checkpoint 没推进，失败事件就会在后续 tick 从同一个位置继续尝试")
    } yield ()
}
