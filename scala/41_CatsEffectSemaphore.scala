//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"

/**
 * Scala 函数式编程 Demo 41: cats-effect Semaphore 并发限流
 *
 * 前面已经看过 Fiber、Queue、Topic、timeout / cancel，
 * 这一版继续补上真实项目里很常见的一块：限制同一时刻最多有多少任务并发执行。
 *
 * 这类能力很适合：
 * - 控制下游 API 并发访问数
 * - 控制数据库连接或文件句柄压力
 * - 防止后台任务把机器瞬间打满
 */
import cats.effect.std.Semaphore
import cats.effect.{IO, IOApp}
import cats.syntax.all._

import scala.concurrent.duration._

object CatsEffectSemaphore extends IOApp.Simple {

  final case class UploadJob(id: Int, fileName: String, cost: FiniteDuration)

  val jobs = List(
    UploadJob(1, "users.csv", 350.millis),
    UploadJob(2, "orders.csv", 500.millis),
    UploadJob(3, "inventory.csv", 250.millis),
    UploadJob(4, "payments.csv", 400.millis),
    UploadJob(5, "events.csv", 300.millis)
  )

  def process(job: UploadJob, semaphore: Semaphore[IO]): IO[Unit] =
    semaphore.permit.use { _ =>
      for {
        _ <- IO.println(s"[job-${job.id}] 开始上传 ${job.fileName}")
        _ <- IO.sleep(job.cost)
        _ <- IO.println(s"[job-${job.id}] 上传完成 ${job.fileName}")
      } yield ()
    }

  val run: IO[Unit] =
    for {
      semaphore <- Semaphore[IO](2)
      _ <- IO.println("=== cats-effect Semaphore 并发限流 ===")
      _ <- IO.println("最多同时允许 2 个上传任务执行")
      started <- IO.monotonic
      _ <- jobs.parTraverse_(process(_, semaphore))
      finished <- IO.monotonic
      _ <- IO.println(s"总耗时: ${(finished - started).toMillis} ms")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- Semaphore 可以限制关键区并发度，而不是一股脑全部并发")
      _ <- IO.println("- permit.use 会在任务结束后自动归还许可")
      _ <- IO.println("- 这很适合保护数据库、磁盘、下游接口这类有限资源")
    } yield ()
}
