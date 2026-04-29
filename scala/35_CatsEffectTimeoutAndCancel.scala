//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"

/**
 * Scala 函数式编程 Demo 35: cats-effect 的 timeout / cancel / finalizer
 *
 * 真实 effect system 的价值，不只是“能跑副作用”，
 * 还在于它能把超时、取消、收尾逻辑都纳入统一模型。
 */
import cats.effect.{IO, IOApp}
import cats.effect.kernel.Outcome

import scala.concurrent.duration._

object CatsEffectTimeoutAndCancel extends IOApp.Simple {

  def slowJob(name: String, sleep: FiniteDuration): IO[String] =
    (IO.println(s"[$name] 开始执行") *>
      IO.sleep(sleep) *>
      IO.println(s"[$name] 正常完成") *>
      IO.pure(s"$name-done"))
      .guaranteeCase {
        case Outcome.Succeeded(_) => IO.println(s"[$name] finalizer: 成功结束")
        case Outcome.Errored(e) => IO.println(s"[$name] finalizer: 异常结束 -> ${e.getMessage}")
        case Outcome.Canceled() => IO.println(s"[$name] finalizer: 被取消了，但仍然会做清理")
      }

  val timeoutProgram: IO[Either[Throwable, String]] =
    slowJob("timeout-job", 800.millis)
      .timeout(250.millis)
      .attempt

  val cancelProgram: IO[Unit] =
    for {
      fiber <- slowJob("cancel-job", 1.second).start
      _ <- IO.sleep(200.millis)
      _ <- IO.println("主流程决定取消 cancel-job")
      _ <- fiber.cancel
      outcome <- fiber.join
      _ <- IO.println(s"cancel-job outcome: $outcome")
    } yield ()

  val timeoutFallbackProgram: IO[String] =
    slowJob("timeout-fallback-job", 700.millis)
      .timeoutTo(200.millis, IO.pure("fallback-result"))

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== timeout：超时后失败 ===")
      timeoutResult <- timeoutProgram
      _ <- IO.println(s"timeout result: $timeoutResult")

      _ <- IO.println("\n=== cancel：取消后台任务 ===")
      _ <- cancelProgram

      _ <- IO.println("\n=== timeoutTo：超时后降级返回 ===")
      fallback <- timeoutFallbackProgram
      _ <- IO.println(s"timeoutTo result: $fallback")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- timeout / cancel 都不是额外约定，而是 effect system 的一等能力")
      _ <- IO.println("- guaranteeCase 让你能在成功、失败、取消三种结局下做不同清理")
      _ <- IO.println("- 这类能力在调用下游接口、批任务、流式消费里非常关键")
    } yield ()
}
