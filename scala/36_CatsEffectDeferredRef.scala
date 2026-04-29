//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"

/**
 * Scala 函数式编程 Demo 36: cats-effect 的 Ref + Deferred 协作
 *
 * 前面已经见过 `Ref` 用来保存并发安全状态，
 * 这一版继续补上 `Deferred`：它很适合表达“一次性完成的信号”。
 *
 * 一个常见组合是：
 * - `Ref` 保存过程中的可观察状态
 * - `Deferred` 表示最终结果何时准备好
 */
import cats.effect.{IO, IOApp, Ref, Deferred}
import cats.syntax.all._

import scala.concurrent.duration._

object CatsEffectDeferredRef extends IOApp.Simple {

  final case class JobState(started: Boolean, progress: Int, finished: Boolean, result: Option[String])

  def worker(state: Ref[IO, JobState], done: Deferred[IO, String]): IO[Unit] =
    for {
      _ <- IO.println("[worker] 开始生成日报")
      _ <- state.update(_.copy(started = true))
      _ <- List(25, 60, 100).traverse_ { percent =>
        IO.sleep(120.millis) *>
          state.update(_.copy(progress = percent)) *>
          IO.println(s"[worker] 当前进度: $percent%")
      }
      result = "daily-report-ready"
      _ <- state.update(_.copy(finished = true, result = Some(result)))
      _ <- done.complete(result)
      _ <- IO.println("[worker] 已发出完成信号")
    } yield ()

  val run: IO[Unit] =
    for {
      state <- Ref.of[IO, JobState](JobState(started = false, progress = 0, finished = false, result = None))
      done <- Deferred[IO, String]
      fiber <- worker(state, done).start

      _ <- IO.println("=== Ref + Deferred 协作 ===")
      initial <- state.get
      _ <- IO.println(s"初始状态: $initial")

      _ <- IO.println("主流程继续做别的事情，然后等待结果信号")
      result <- done.get.timeoutTo(2.seconds, IO.pure("timeout-fallback"))
      snapshot <- state.get
      _ <- fiber.joinWithNever

      _ <- IO.println(s"收到结果信号: $result")
      _ <- IO.println(s"最终状态: $snapshot")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- Ref 适合保存会被多次读取和更新的共享状态")
      _ <- IO.println("- Deferred 适合表达‘结果未来会到，但只会完成一次’")
      _ <- IO.println("- 两者组合非常适合任务编排、启动握手、异步通知")
    } yield ()
}
