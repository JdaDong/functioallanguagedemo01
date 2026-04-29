//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.13.0"

/**
 * Scala 函数式编程 Demo 57: fs2 interruptWhen + SignallingRef
 *
 * 52 号 Demo 讨论了流里的错误恢复，
 * 这一版继续补一个长生命周期流经常会遇到的话题：
 *
 * 流什么时候优雅退出？
 * 如何把“停止信号”做成显式、可组合的值？
 */
import cats.effect.{IO, IOApp}
import fs2.Stream
import fs2.concurrent.SignallingRef

import scala.concurrent.duration._

object FS2InterruptAndSignallingRef extends IOApp.Simple {

  def worker(stopSignal: SignallingRef[IO, Boolean]): Stream[IO, Unit] =
    Stream
      .awakeEvery[IO](120.millis)
      .scan(0)((count, _) => count + 1)
      .evalMap(count => IO.println(s"[worker] tick=$count"))
      .interruptWhen(stopSignal)

  def watcher(stopSignal: SignallingRef[IO, Boolean]): Stream[IO, Unit] =
    stopSignal.discrete.evalMap(value => IO.println(s"[signal] stop=$value"))

  def controller(stopSignal: SignallingRef[IO, Boolean]): Stream[IO, Unit] =
    Stream.eval(
      IO.sleep(650.millis) *>
        IO.println("[controller] 发出停止信号") *>
        stopSignal.set(true)
    )

  val run: IO[Unit] =
    for {
      stopSignal <- SignallingRef[IO, Boolean](false)
      _ <- IO.println("=== 使用显式停止信号优雅关闭流 ===")
      _ <- worker(stopSignal)
        .concurrently(controller(stopSignal))
        .concurrently(watcher(stopSignal))
        .compile
        .drain
      finalState <- stopSignal.get
      _ <- IO.println(s"final stop state = $finalState")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- SignallingRef 让‘是否停止’变成显式共享状态，而不是隐藏在回调里")
      _ <- IO.println("- interruptWhen 可以让长生命周期流在收到停止信号后优雅退出")
      _ <- IO.println("- 这很适合轮询任务、心跳流、订阅流和后台守护流程")
    } yield ()
}
