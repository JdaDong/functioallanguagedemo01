// 17_AkkaStreamsKillSwitch.scala
// Akka Streams KillSwitch（流控制）
//
// 核心思想：
//   KillSwitch 是 Akka Streams 用于动态控制流生命周期的工具：
//     - SharedKillSwitch：多条流共享同一个开关（关闭一个，全部关闭）
//     - UniqueKillSwitch：每条流独立的开关
//
//   与 fs2 的对比：
//     fs2：SignallingRef[IO, Boolean]（Demo 57）+ .interruptWhen(signal)
//     Akka：stream.viaMat(KillSwitches.single)(Keep.right) 得到 KillSwitch
//           ks.shutdown() 或 ks.abort(cause) 控制流
//
// 本 Demo 演示：
//   1. UniqueKillSwitch：独立控制单条流的停止
//   2. SharedKillSwitch：一个开关同时关闭多条流
//   3. abort：用异常关闭流（对比 shutdown 正常关闭）

//> using scala "3.3.3"
//> using dep "com.typesafe.akka::akka-actor-typed:2.8.5"
//> using dep "com.typesafe.akka::akka-stream:2.8.5"

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.{KillSwitches, UniqueKillSwitch}
import akka.stream.scaladsl._
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.util.{Success, Failure}

@main def akkaStreamsKillSwitchDemo(): Unit =
  given system: ActorSystem[Nothing] =
    ActorSystem(Behaviors.empty, "killswitch-system")
  import system.executionContext

  println("=== Akka Streams KillSwitch：动态停止流（对比 fs2 SignallingRef）===\n")

  // ── 1. UniqueKillSwitch：独立控制单条流 ──────────────────────────────────
  println("── 1. UniqueKillSwitch：3 秒后停止无限流 ────────────────────────")

  val counter = java.util.concurrent.atomic.AtomicInteger(0)

  val (killSwitch, done) = Source
    .tick(0.millis, 100.millis, ())          // 每 100ms 一个 tick
    .map(_ => counter.incrementAndGet())
    .viaMat(KillSwitches.single)(Keep.right) // 获取 KillSwitch
    .toMat(Sink.foreach(n => print(s"$n ")))(Keep.both)
    .run()

  Thread.sleep(500)
  killSwitch.shutdown()     // 正常停止
  Await.result(done, 2.seconds)
  println(s"\n  流已停止，共处理了 ${counter.get()} 个元素")

  // ── 2. SharedKillSwitch：一个开关同时关闭多条流 ──────────────────────────
  println("\n── 2. SharedKillSwitch：一个开关控制多条流 ──────────────────────")

  val sharedKS = KillSwitches.shared("shared-ks")

  val log1 = java.util.concurrent.atomic.AtomicInteger(0)
  val log2 = java.util.concurrent.atomic.AtomicInteger(0)

  val f1 = Source.tick(0.millis, 80.millis, "stream1")
    .via(sharedKS.flow)
    .runWith(Sink.foreach(_ => log1.incrementAndGet()))

  val f2 = Source.tick(0.millis, 120.millis, "stream2")
    .via(sharedKS.flow)
    .runWith(Sink.foreach(_ => log2.incrementAndGet()))

  Thread.sleep(400)
  sharedKS.shutdown()   // 同时关闭两条流
  Await.result(f1, 2.seconds)
  Await.result(f2, 2.seconds)
  println(s"  stream1 处理了 ${log1.get()} 次，stream2 处理了 ${log2.get()} 次")
  println(s"  两条流同时被关闭")

  // ── 3. abort：用异常关闭流 ────────────────────────────────────────────────
  println("\n── 3. abort：用异常关闭流（流失败）─────────────────────────────")

  val (ks3, f3) = Source.tick(0.millis, 100.millis, ())
    .map(_ => counter.incrementAndGet())
    .viaMat(KillSwitches.single)(Keep.right)
    .toMat(Sink.ignore)(Keep.both)
    .run()

  Thread.sleep(200)
  ks3.abort(new RuntimeException("外部触发中断"))
  f3.onComplete {
    case Success(_)  => println("  流正常结束（不应该）")
    case Failure(ex) => println(s"  流因异常结束: ${ex.getMessage}")
  }
  Thread.sleep(300)

  println("""
|关键点（与 fs2 SignallingRef 对比）：
|
|  fs2 (Demo 57):                  Akka Streams KillSwitch:
|  ─────────────────────────────   ──────────────────────────────────
|  signal <- SignallingRef(false)  val (ks, done) = source
|  fiber <- stream                   .viaMat(KillSwitches.single)(Keep.right)
|    .interruptWhen(signal)           .toMat(sink)(Keep.both).run()
|    .compile.drain.start
|  signal.set(true)  // 停止        ks.shutdown()  // 正常停止
|                                   ks.abort(ex)   // 异常停止
|
|  特点：                           特点：
|  - 用 IO 信号控制                 - 直接的对象引用（ks.shutdown()）
|  - 函数式组合                     - SharedKillSwitch 控制多条流
|  - 可中途 take(N)                 - abort 可以传入自定义异常""".stripMargin)

  system.terminate()
  Await.result(system.whenTerminated, 5.seconds)
