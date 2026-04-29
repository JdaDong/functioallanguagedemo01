//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"

/**
 * Scala 函数式编程 Demo 46: cats-effect Supervisor 管理后台任务
 *
 * 前面已经看过 Fiber、Semaphore、Queue、Topic，
 * 这一版继续补上真实项目里经常会遇到的问题：
 * 后台任务应该由谁托管？主流程结束后它们怎么办？
 *
 * `Supervisor` 很适合表达“在某个作用域里统一托管一批后台 fiber”。
 */
import cats.effect.kernel.Outcome
import cats.effect.std.Supervisor
import cats.effect.{IO, IOApp}
import cats.syntax.all._

import scala.concurrent.duration._

object CatsEffectSupervisor extends IOApp.Simple {

  def backgroundJob(name: String, interval: FiniteDuration): IO[Unit] =
    (IO.println(s"[$name] 执行一次后台同步") *> IO.sleep(interval))
      .foreverM
      .guaranteeCase {
        case Outcome.Canceled() => IO.println(s"[$name] 收到取消信号，开始收尾")
        case Outcome.Errored(error) => IO.println(s"[$name] 异常结束: ${error.getMessage}")
        case Outcome.Succeeded(_) => IO.println(s"[$name] 正常结束")
      }

  val run: IO[Unit] =
    Supervisor[IO].use { supervisor =>
      for {
        _ <- IO.println("=== cats-effect Supervisor 托管后台任务 ===")
        _ <- supervisor.supervise(backgroundJob("inventory-sync", 180.millis))
        _ <- supervisor.supervise(backgroundJob("metrics-flush", 260.millis))
        _ <- IO.println("主流程继续做前台工作，不需要手动 join 每一个后台任务")
        _ <- IO.sleep(700.millis)
        _ <- IO.println("主流程即将结束，离开 Supervisor 作用域后会自动取消后台任务")
      } yield ()
    } *> IO.println("Supervisor 作用域已结束")
}
