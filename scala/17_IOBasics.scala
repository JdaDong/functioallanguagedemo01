/**
 * Scala 函数式编程 Demo 17: IO —— 把副作用先描述出来
 *
 * 真正进入高级阶段时，一个核心问题是：
 * 我们如何把打印、读配置、拿时间、访问网络这些副作用，
 * 从“立刻执行的动作”变成“可以组合的描述”？
 *
 * 这里先手写一个极简 IO，建立直觉。
 */
object IOBasics extends App {

  final case class IO[+A](unsafeRun: () => A) {
    def map[B](f: A => B): IO[B] =
      IO(() => f(unsafeRun()))

    def flatMap[B](f: A => IO[B]): IO[B] =
      IO(() => f(unsafeRun()).unsafeRun())

    def attempt: IO[Either[Throwable, A]] =
      IO(() =>
        try Right(unsafeRun())
        catch {
          case e: Throwable => Left(e)
        }
      )
  }

  object IO {
    def pure[A](value: A): IO[A] = IO(() => value)

    def delay[A](thunk: => A): IO[A] = IO(() => thunk)

    def printLine(message: String): IO[Unit] =
      delay(println(message))
  }

  def timed[A](label: String)(ioa: IO[A]): IO[A] = for {
    start <- IO.delay(System.currentTimeMillis())
    value <- ioa
    end <- IO.delay(System.currentTimeMillis())
    _ <- IO.printLine(s"[$label] 耗时: ${end - start} ms")
  } yield value

  def fetchUserName(userId: Int): IO[String] = IO.delay {
    Thread.sleep(150)
    s"user-$userId"
  }

  def buildWelcomeMessage(userId: Int): IO[String] = for {
    name <- fetchUserName(userId)
    now <- IO.delay(new java.util.Date().toString)
  } yield s"你好，$name！当前时间: $now"

  println("=== IO 的核心：先描述，再执行 ===")
  val helloEffect = IO.printLine("这是一段副作用，但现在还不会执行")
  println("helloEffect 已经创建完成。")
  println("如果你不调用 unsafeRun，它就只是一个描述。")

  println("\n现在开始真正执行 helloEffect:")
  helloEffect.unsafeRun()

  println("\n=== 把多个副作用组合成一个流程 ===")
  val program = timed("欢迎流程") {
    for {
      _ <- IO.printLine("开始准备欢迎信息...")
      message <- buildWelcomeMessage(42)
      _ <- IO.printLine(message)
      _ <- IO.printLine("流程结束")
    } yield ()
  }

  println("program 也只是一个值，直到 run 之前都不会发生任何副作用。")
  program.unsafeRun()

  println("\n=== 把失败也包进结果里 ===")
  val riskyProgram = IO.delay("not-a-number".toInt).attempt
  println(s"执行 riskyProgram: ${riskyProgram.unsafeRun()}")

  println("\n=== 重点理解 ===")
  println("- IO 把副作用从‘立刻发生’变成‘可组合、可传递、可延后执行的值’")
  println("- map / flatMap 让副作用流程也能像纯数据流一样被组合")
  println("- 真正的 cats-effect IO 会更完整，支持取消、并发、安全资源管理等能力")
}
