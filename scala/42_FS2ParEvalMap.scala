//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.13.0"

/**
 * Scala 函数式编程 Demo 42: fs2 parEvalMap 并行流处理
 *
 * 28 号 Demo 已经展示过 fs2 的基本数据管道，
 * 这一版继续补上真实项目里很重要的一个话题：
 * 同样是“对流中的每个元素做 effect 处理”，顺序执行和并行执行会有什么区别？
 */
import cats.effect.{IO, IOApp}
import fs2.Stream

import scala.concurrent.duration._

object FS2ParEvalMap extends IOApp.Simple {

  final case class Order(id: Int, customer: String)

  val orders = List(
    Order(1, "Alice"),
    Order(2, "Bob"),
    Order(3, "Cindy"),
    Order(4, "David"),
    Order(5, "Eric")
  )

  def enrich(order: Order): IO[String] = {
    val latency = (600 - order.id * 80).millis
    IO.println(s"[enrich] start order=${order.id}, latency=${latency.toMillis}ms") *>
      IO.sleep(latency) *>
      IO.pure(s"order-${order.id}-for-${order.customer}")
  }

  def measure[A](label: String)(task: IO[A]): IO[A] =
    for {
      start <- IO.monotonic
      result <- task
      end <- IO.monotonic
      _ <- IO.println(s"$label 耗时: ${(end - start).toMillis} ms")
    } yield result

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== 顺序 evalMap ===")
      sequential <- measure("evalMap") {
        Stream.emits(orders).covary[IO].evalMap(enrich).compile.toList
      }
      _ <- IO.println(s"结果顺序: $sequential")

      _ <- IO.println("\n=== 并行 parEvalMap(3) ===")
      parallelOrdered <- measure("parEvalMap") {
        Stream.emits(orders).covary[IO].parEvalMap(3)(enrich).compile.toList
      }
      _ <- IO.println(s"结果顺序: $parallelOrdered")

      _ <- IO.println("\n=== 并行 parEvalMapUnordered(3) ===")
      parallelUnordered <- measure("parEvalMapUnordered") {
        Stream.emits(orders).covary[IO].parEvalMapUnordered(3)(enrich).compile.toList
      }
      _ <- IO.println(s"结果顺序: $parallelUnordered")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- evalMap 会严格按流顺序逐个执行 effect")
      _ <- IO.println("- parEvalMap 会并发执行，但尽量保持输出顺序")
      _ <- IO.println("- parEvalMapUnordered 更追求吞吐量，先完成的结果先出来")
    } yield ()
}
