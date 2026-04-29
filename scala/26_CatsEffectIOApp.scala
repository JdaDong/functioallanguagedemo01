//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.5.7"

/**
 * Scala 函数式编程 Demo 26: 真正的 cats-effect IO / Fiber / 并发组合
 *
 * 前面的 17 号 Demo 我们手写了一个极简 IO，目的是先理解：
 * “副作用也可以先被描述成值，再统一执行”。
 *
 * 现在开始进入真实库版本：cats-effect 的 IO 不只是一个包装器，
 * 它还提供了并发、取消、错误捕获、时钟、资源等能力。
 */
import cats.effect.{IO, IOApp}
import cats.syntax.all._

import scala.concurrent.duration._

object CatsEffectIOApp extends IOApp.Simple {

  final case class Profile(id: Long, name: String)
  final case class Permission(name: String)

  def loadProfile(userId: Long): IO[Profile] =
    IO.sleep(120.millis) *>
      IO.println(s"开始加载用户资料 userId=$userId") *>
      IO.pure(Profile(userId, "Alice"))

  def loadPermissions(userId: Long): IO[List[Permission]] =
    IO.sleep(150.millis) *>
      IO.println(s"开始加载权限 userId=$userId") *>
      IO.pure(List(Permission("order.read"), Permission("order.write")))

  def buildDailyReport(name: String): IO[String] =
    IO.println(s"[$name] 开始在后台生成日报...") *>
      IO.sleep(300.millis) *>
      IO.pure(s"[$name] 日报生成完成")

  val safeDivision: IO[Either[Throwable, Int]] =
    IO.delay(10 / 0).attempt

  def showDuration(label: String, start: FiniteDuration, end: FiniteDuration): IO[Unit] =
    IO.println(s"$label 耗时: ${(end - start).toMillis} ms")

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== cats-effect IO 基础能力 ===")

      serialStart <- IO.monotonic
      profile1 <- loadProfile(1)
      permissions1 <- loadPermissions(1)
      serialEnd <- IO.monotonic
      _ <- IO.println(s"串行结果: profile=$profile1, permissions=$permissions1")
      _ <- showDuration("串行组合", serialStart, serialEnd)

      _ <- IO.println("\n=== parTupled：独立任务并行执行 ===")
      parallelStart <- IO.monotonic
      parallelResult <- (loadProfile(2), loadPermissions(2)).parTupled
      parallelEnd <- IO.monotonic
      _ <- IO.println(s"并行结果: $parallelResult")
      _ <- showDuration("并行组合", parallelStart, parallelEnd)

      _ <- IO.println("\n=== Fiber：把任务放到后台运行 ===")
      fiber <- buildDailyReport("finance-report").start
      _ <- IO.println("主流程没有被阻塞，可以继续做别的事情")
      _ <- IO.sleep(80.millis)
      _ <- IO.println("主流程此时可以继续响应别的请求")
      report <- fiber.joinWithNever
      _ <- IO.println(s"Fiber 返回结果: $report")

      _ <- IO.println("\n=== attempt：把异常变成显式结果 ===")
      division <- safeDivision
      _ <- IO.println(s"attempt 结果: $division")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- 真正的 IO 不只描述副作用，还支持并发、取消、时间和错误控制")
      _ <- IO.println("- parTupled 适合组合相互独立的 effect")
      _ <- IO.println("- Fiber 是轻量级并发单元，start 后主流程不必等待")
    } yield ()
}
