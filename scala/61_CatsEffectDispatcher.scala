//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.13.0"

/**
 * Scala 函数式编程 Demo 61: cats-effect Dispatcher 桥接旧式回调边界
 *
 * 前面已经覆盖过 client / context / retry / 流式退出，
 * 这一版继续补一个真实工程里非常常见的“边界接缝”：
 *
 * 老系统、SDK、消息总线、GUI、Java 回调接口，往往会把数据通过 `A => Unit` 推进来。
 * 我们希望把这些回调重新接回 effect / queue / stream 世界，而不是把副作用散落在回调里。
 */
import cats.effect.std.{Dispatcher, Queue}
import cats.effect.{IO, IOApp}
import fs2.Stream

import scala.concurrent.duration._

object CatsEffectDispatcher extends IOApp.Simple {

  final case class LegacyEvent(seq: Int, payload: String)

  final class LegacyEventBus {
    private var listener: LegacyEvent => Unit = (_: LegacyEvent) => ()

    def subscribe(callback: LegacyEvent => Unit): Unit =
      listener = callback

    def publish(event: LegacyEvent): Unit =
      listener(event)
  }

  def installBridge(
      bus: LegacyEventBus,
      dispatcher: Dispatcher[IO],
      queue: Queue[IO, LegacyEvent]
  ): IO[Unit] =
    IO {
      bus.subscribe { event =>
        dispatcher.unsafeRunAndForget(
          IO.println(s"[bridge] 收到旧式回调: $event") *>
            queue.offer(event)
        )
      }
    }

  def legacyProducer(bus: LegacyEventBus): IO[Unit] =
    Stream
      .emits(
        List(
          LegacyEvent(1, "user-created"),
          LegacyEvent(2, "profile-synced"),
          LegacyEvent(3, "quota-refreshed")
        )
      )
      .covary[IO]
      .metered(140.millis)
      .evalMap(event => IO.println(s"[legacy] 发布事件: $event") *> IO(bus.publish(event)))
      .compile
      .drain

  val run: IO[Unit] =
    Dispatcher.sequential[IO].use { dispatcher =>
      for {
        bus <- IO(new LegacyEventBus)
        queue <- Queue.unbounded[IO, LegacyEvent]
        _ <- IO.println("=== 用 Dispatcher 把旧式回调桥接回 effect 世界 ===")
        _ <- installBridge(bus, dispatcher, queue)
        producer <- legacyProducer(bus).start
        consumed <- Stream
          .fromQueueUnterminated(queue)
          .take(3)
          .evalTap(event => IO.println(s"[consumer] 进入 effect / stream 管道: $event"))
          .compile
          .toList
        _ <- producer.joinWithNever
        _ <- IO.println(s"最终消费到的事件: $consumed")

        _ <- IO.println("\n=== 重点理解 ===")
        _ <- IO.println("- Dispatcher 很适合把 callback 世界重新接回 IO / Queue / Stream")
        _ <- IO.println("- 真正的副作用依然被放在 effect 里，而不是直接塞进回调函数体")
        _ <- IO.println("- 这类桥接在旧 SDK、事件总线、Java 监听器、GUI 回调里很常见")
      } yield ()
    }
}
