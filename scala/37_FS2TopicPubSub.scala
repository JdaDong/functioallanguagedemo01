//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.13.0"

/**
 * Scala 函数式编程 Demo 37: fs2 Topic 发布 / 订阅模型
 *
 * Queue 更像“多个 worker 分摊同一批任务”，
 * Topic 更像“同一条消息广播给多个订阅者”。
 *
 * 这很适合日志广播、指标采样、通知分发、事件总线等场景。
 */
import cats.effect.{IO, IOApp}
import fs2.Stream
import fs2.concurrent.Topic

import scala.concurrent.duration._

object FS2TopicPubSub extends IOApp.Simple {

  def subscriber(name: String, topic: Topic[IO, String]): IO[Unit] =
    topic
      .subscribe(maxQueued = 10)
      .evalMap(message => IO.println(s"[$name] 收到消息: $message"))
      .take(5)
      .compile
      .drain

  def publisher(topic: Topic[IO, String]): IO[Unit] =
    Stream
      .emits(List("system-boot", "order-created", "payment-confirmed", "invoice-generated", "shipment-dispatched"))
      .covary[IO]
      .metered(100.millis)
      .evalMap(message => IO.println(s"[publisher] 发布消息: $message") *> topic.publish1(message))
      .compile
      .drain

  val run: IO[Unit] =
    for {
      topic <- Topic[IO, String]
      _ <- IO.println("=== fs2 Topic 发布 / 订阅 ===")
      analyticsFiber <- subscriber("analytics", topic).start
      billingFiber <- subscriber("billing", topic).start
      _ <- publisher(topic)
      _ <- analyticsFiber.joinWithNever
      _ <- billingFiber.joinWithNever

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- Topic 是广播模型：每个订阅者都能看到同一条消息")
      _ <- IO.println("- Queue 是分摊模型：同一条任务通常只会被一个消费者处理")
      _ <- IO.println("- 事件总线、通知广播、指标分发常常更适合 Topic 这类结构")
    } yield ()
}
