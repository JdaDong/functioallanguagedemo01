//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.12.2"

/**
 * Scala 函数式编程 Demo 34: fs2 Queue 驱动的生产者 / 消费者工作流
 *
 * 流处理不只适合“读文件”，也很适合“异步任务队列”。
 * 这一版用 Queue 模拟订单事件进入队列，再由多个 worker 并发消费。
 */
import cats.effect.std.Queue
import cats.effect.{IO, IOApp}
import fs2.Stream

object FS2QueueWorker extends IOApp.Simple {

  sealed trait Job
  object Job {
    final case class Process(orderId: Long, amount: Double) extends Job
    case object Stop extends Job
  }

  def producer(queue: Queue[IO, Job], workerCount: Int): IO[Unit] = {
    val jobs = List(
      Job.Process(1001, 88.5),
      Job.Process(1002, 300.0),
      Job.Process(1003, 1500.0),
      Job.Process(1004, 45.2)
    )

    Stream
      .emits(jobs)
      .covary[IO]
      .evalMap { job =>
        IO.println(s"生产任务: $job") *> queue.offer(job)
      }
      .compile
      .drain *> Stream
      .emits(List.fill(workerCount)(Job.Stop))
      .covary[IO]
      .evalMap(queue.offer)
      .compile
      .drain
  }

  def worker(name: String, queue: Queue[IO, Job]): Stream[IO, Unit] =
    Stream
      .fromQueueUnterminated(queue)
      .evalMap {
        case Job.Process(orderId, amount) =>
          val level = if (amount >= 1000) "VIP" else "NORMAL"
          IO.println(s"[$name] 处理订单 $orderId, amount=$amount, level=$level")

        case Job.Stop =>
          IO.println(s"[$name] 收到停止信号") *> IO.raiseError(new RuntimeException(s"$name-stop"))
      }
      .handleErrorWith {
        case e if e.getMessage.endsWith("-stop") => Stream.empty
        case e => Stream.eval(IO.println(s"[$name] 处理失败: ${e.getMessage}"))
      }

  val run: IO[Unit] =
    for {
      queue <- Queue.unbounded[IO, Job]
      _ <- IO.println("=== fs2 Queue 工作流 ===")
      producerFiber <- producer(queue, workerCount = 2).start
      worker1Fiber <- worker("worker-1", queue).compile.drain.start
      worker2Fiber <- worker("worker-2", queue).compile.drain.start
      _ <- producerFiber.joinWithNever
      _ <- worker1Fiber.joinWithNever
      _ <- worker2Fiber.joinWithNever

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- Queue 适合建模异步生产者 / 消费者协作")
      _ <- IO.println("- 多个 worker 共享同一队列时，任务会被分摊消费")
      _ <- IO.println("- 这类结构很常见于订单处理、消息消费、异步任务执行")
    } yield ()
}
