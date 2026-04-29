//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"

/**
 * Scala 函数式编程 Demo 56: cats-effect uncancelable 与 poll
 *
 * 35 号 Demo 已经展示过 cancel / timeout / guaranteeCase，
 * 这一版继续推进一个更贴近真实工程的问题：
 *
 * 某些步骤可以取消，比如“等待外部确认”；
 * 某些步骤不能取消，比如“已经开始写账本和提交关键状态”。
 *
 * `IO.uncancelable` + `poll` 就是用来精细控制这条边界的。
 */
import cats.effect.kernel.Outcome
import cats.effect.{IO, IOApp}

import scala.concurrent.duration._

object CatsEffectUncancelable extends IOApp.Simple {

  def paymentFlow(name: String, approvalDelay: FiniteDuration, commitDelay: FiniteDuration): IO[String] =
    IO.uncancelable { poll =>
      for {
        _ <- IO.println(s"[$name] 开始处理支付")
        _ <- IO.println(s"[$name] 进入可取消阶段：等待风控 / 外部确认")
        _ <- poll(IO.sleep(approvalDelay) *> IO.println(s"[$name] 外部确认完成"))
          .onCancel(IO.println(s"[$name] 在等待确认时被取消，尚未进入关键区"))

        _ <- IO.println(s"[$name] 进入不可取消关键区：写账本 + 提交订单状态")
        _ <- IO.sleep(commitDelay)
        _ <- IO.println(s"[$name] 关键区执行完毕")
      } yield s"$name-committed"
    }.guaranteeCase {
      case Outcome.Succeeded(_) => IO.println(s"[$name] outcome: 成功完成")
      case Outcome.Errored(error) => IO.println(s"[$name] outcome: 异常结束 -> ${error.getMessage}")
      case Outcome.Canceled() => IO.println(s"[$name] outcome: 最终被取消")
    }

  def runScenario(
      title: String,
      program: IO[String],
      cancelAfter: FiniteDuration
  ): IO[Unit] =
    for {
      _ <- IO.println(s"=== $title ===")
      fiber <- program.start
      _ <- IO.sleep(cancelAfter)
      _ <- IO.println(s"主流程在 ${cancelAfter.toMillis}ms 后发出取消请求")
      _ <- fiber.cancel
      outcome <- fiber.join
      _ <- IO.println(s"join outcome = $outcome")
    } yield ()

  val run: IO[Unit] =
    for {
      _ <- runScenario(
        title = "在可取消等待阶段取消",
        program = paymentFlow("order-1001", approvalDelay = 500.millis, commitDelay = 250.millis),
        cancelAfter = 180.millis
      )

      _ <- IO.println("")

      _ <- runScenario(
        title = "在不可取消关键区发起取消",
        program = paymentFlow("order-1002", approvalDelay = 80.millis, commitDelay = 450.millis),
        cancelAfter = 220.millis
      )

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- uncancelable 不是让整条程序永远不可取消，而是让你精确圈出关键区")
      _ <- IO.println("- poll 可以把等待外部资源的阶段重新打开为可取消")
      _ <- IO.println("- 真实支付、转账、状态提交、offset 提交等场景，经常都要这样划边界")
    } yield ()
}
