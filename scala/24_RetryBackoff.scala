/**
 * Scala 函数式编程 Demo 24: Retry / Backoff —— 把重试策略变成可组合逻辑
 *
 * 网络调用、远程依赖、消息投递经常会失败，
 * 但“失败后怎么重试”不应该散落在业务代码里。
 *
 * 这一类逻辑通常值得被抽象成：
 * - 重试几次
 * - 每次等待多久
 * - 失败时记录什么日志
 */
object RetryBackoff extends App {

  final case class IO[+A](unsafeRun: () => A) {
    def map[B](f: A => B): IO[B] = IO(() => f(unsafeRun()))
    def flatMap[B](f: A => IO[B]): IO[B] = IO(() => f(unsafeRun()).unsafeRun())
    def attempt: IO[Either[Throwable, A]] = IO(() =>
      try Right(unsafeRun())
      catch {
        case e: Throwable => Left(e)
      }
    )
  }

  object IO {
    def pure[A](value: A): IO[A] = IO(() => value)
    def delay[A](thunk: => A): IO[A] = IO(() => thunk)
    def printLine(message: String): IO[Unit] = delay(println(message))
    def sleep(millis: Long): IO[Unit] = delay(Thread.sleep(millis))
  }

  case class RetryPolicy(maxRetries: Int, baseDelayMillis: Long) {
    def nextDelay(attempt: Int): Long = baseDelayMillis * math.pow(2, attempt.toDouble).toLong
  }

  def retry[A](label: String, policy: RetryPolicy)(task: IO[A]): IO[Either[Throwable, A]] = {
    def loop(attempt: Int): IO[Either[Throwable, A]] =
      task.attempt.flatMap {
        case ok @ Right(value) =>
          IO.printLine(s"[$label] 第 ${attempt + 1} 次尝试成功: $value").map(_ => ok)

        case err @ Left(e) if attempt >= policy.maxRetries =>
          IO.printLine(s"[$label] 第 ${attempt + 1} 次尝试失败，已达到最大重试次数: ${e.getMessage}")
            .map(_ => err)

        case Left(e) =>
          val delay = policy.nextDelay(attempt)
          for {
            _ <- IO.printLine(s"[$label] 第 ${attempt + 1} 次尝试失败: ${e.getMessage}，$delay ms 后重试")
            _ <- IO.sleep(delay)
            result <- loop(attempt + 1)
          } yield result
      }

    loop(0)
  }

  final class FlakyService(successAt: Int) {
    private var counter = 0

    def fetchConfig(): IO[String] = IO.delay {
      counter += 1
      if (counter >= successAt) s"config-loaded-at-$counter"
      else throw new RuntimeException(s"临时错误，当前第 $counter 次")
    }
  }

  val policy = RetryPolicy(maxRetries = 4, baseDelayMillis = 80)

  println("=== 一个最终会成功的远程调用 ===")
  val flakySuccess = new FlakyService(successAt = 3)
  val successResult = retry("load-config", policy)(flakySuccess.fetchConfig()).unsafeRun()
  println(s"最终结果: $successResult")

  println("\n=== 一个最终会失败的远程调用 ===")
  val flakyFailure = new FlakyService(successAt = 10)
  val failureResult = retry("load-report", policy)(flakyFailure.fetchConfig()).unsafeRun()
  println(s"最终结果: $failureResult")

  println("\n=== 重点理解 ===")
  println("- 重试策略本身也可以是普通数据，比如最大次数、退避时间")
  println("- 业务逻辑只描述‘我要做什么’，重试逻辑描述‘失败后怎么办’")
  println("- 真正用 cats-effect 时，这类逻辑通常会和 timeout、circuit breaker 等一起出现")
}
