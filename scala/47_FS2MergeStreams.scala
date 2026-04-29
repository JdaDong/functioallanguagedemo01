//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.13.0"

/**
 * Scala 函数式编程 Demo 47: fs2 多路流合并
 *
 * 前面已经写过 Queue、Topic、parEvalMap，
 * 这一版继续补一个很常见的场景：
 * 系统里有多路不同来源的事件流，需要合并进同一条处理管道。
 */
import cats.effect.{IO, IOApp}
import fs2.Stream

import scala.concurrent.duration._

object FS2MergeStreams extends IOApp.Simple {

  def source(name: String, values: List[String], interval: FiniteDuration): Stream[IO, String] =
    Stream
      .emits(values)
      .covary[IO]
      .metered(interval)
      .evalTap(value => IO.println(s"[$name] 发出事件: $value"))
      .map(value => s"$name -> $value")

  val metricsStream = source("metrics", List("cpu=41%", "mem=63%", "qps=185"), 120.millis)
  val orderStream = source("orders", List("order-101", "order-102", "order-103", "order-104"), 180.millis)

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== fs2 merge 合并多路流 ===")
      merged <- metricsStream.merge(orderStream).compile.toList
      _ <- IO.println(s"合并后的事件顺序: $merged")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- merge 会让多路流并发推进，谁先有元素谁先输出")
      _ <- IO.println("- 这很适合把日志、指标、订单、通知等事件源汇总进统一管道")
      _ <- IO.println("- 这类合并和 parEvalMap 关注点不同：它更像‘汇总来源’，而不是‘并行处理元素’")
    } yield ()
}
