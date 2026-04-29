//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"

/**
 * Scala 函数式编程 Demo 51: cats-effect race 竞速与自动取消
 *
 * 真实系统里经常会遇到这种需求：
 * - 同一份数据可以从多个来源获取
 * - 谁先返回就先用谁
 * - 输掉的一方应该自动取消，避免白白浪费资源
 *
 * `IO.race` 就是这种“谁先完成就采用谁”的经典工具。
 */
import cats.effect.kernel.Outcome
import cats.effect.{IO, IOApp}

import scala.concurrent.duration._

object CatsEffectRace extends IOApp.Simple {

  def fetchFrom(name: String, delay: FiniteDuration, value: String): IO[String] =
    (IO.println(s"[$name] 开始请求") *>
      IO.sleep(delay) *>
      IO.println(s"[$name] 成功返回: $value") *>
      IO.pure(value)).guaranteeCase {
      case Outcome.Canceled() =>
        IO.println(s"[$name] 输掉 race，被自动取消")
      case Outcome.Errored(error) =>
        IO.println(s"[$name] 异常结束: ${error.getMessage}")
      case Outcome.Succeeded(_) =>
        IO.println(s"[$name] 正常完成")
    }

  def demo(label: String, left: IO[String], right: IO[String]): IO[Unit] =
    for {
      _ <- IO.println(s"=== $label ===")
      winner <- IO.race(left, right)
      _ <- winner match {
        case Left(value) => IO.println(s"胜者来自左侧来源: $value")
        case Right(value) => IO.println(s"胜者来自右侧来源: $value")
      }
      _ <- IO.sleep(80.millis)
    } yield ()

  val run: IO[Unit] =
    for {
      _ <- demo(
        label = "缓存比远端快",
        left = fetchFrom("cache", 120.millis, "price=188.20"),
        right = fetchFrom("remote", 320.millis, "price=188.40")
      )

      _ <- IO.println("")

      _ <- demo(
        label = "主库慢于副本",
        left = fetchFrom("primary-db", 280.millis, "user=Alice(primary)"),
        right = fetchFrom("replica-db", 150.millis, "user=Alice(replica)")
      )

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- race 会在第一个成功完成的分支返回后，自动取消另一边")
      _ <- IO.println("- 这很适合 cache / remote、primary / replica、多个镜像源等场景")
      _ <- IO.println("- 关键点不是‘并发越多越好’，而是把赢家和失败方生命周期都交给 effect system")
    } yield ()
}
