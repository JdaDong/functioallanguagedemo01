//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.13.0"

/**
 * Scala 函数式编程 Demo 62: fs2 groupWithin 批处理窗口
 *
 * 47 号 Demo 展示过 merge，57 号 Demo 展示过 interruptWhen。
 * 这一版继续补一个流处理里很实用的模式：
 *
 * 把零散到来的元素，按“数量上限”或“时间窗口”聚成批次。
 * 这非常适合日志批量落库、指标批量上报、消息聚合提交等场景。
 */
import cats.effect.{IO, IOApp}
import fs2.Stream

import scala.concurrent.duration._

object FS2GroupWithin extends IOApp.Simple {

  final case class Input(delay: FiniteDuration, value: String)

  val inputs = List(
    Input(Duration.Zero, "evt-1"),
    Input(40.millis, "evt-2"),
    Input(40.millis, "evt-3"),
    Input(260.millis, "evt-4"),
    Input(260.millis, "evt-5"),
    Input(40.millis, "evt-6")
  )

  def source: Stream[IO, String] =
    Stream
      .emits(inputs)
      .covary[IO]
      .evalMap { input =>
        IO.sleep(input.delay) *>
          IO.println(s"[source] 收到元素: ${input.value}") *>
          IO.pure(input.value)
      }

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== groupWithin：按数量或时间窗口聚合 ===")
      batches <- source
        .groupWithin(3, 200.millis)
        .map(_.toList)
        .evalTap(batch => IO.println(s"[batch] 输出批次: $batch"))
        .compile
        .toList
      _ <- IO.println(s"最终批次结果: $batches")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- groupWithin 会在‘达到指定数量’或‘等待超过指定时间’时触发批量输出")
      _ <- IO.println("- 它适合把零散事件整理成更高吞吐、更低开销的批处理提交")
      _ <- IO.println("- 真实场景里常见于批量写库、批量发送、批量刷盘和窗口聚合")
    } yield ()
}
