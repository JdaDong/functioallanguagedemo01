// 10_ActorTimers.scala
// Akka 定时器（Timers）
//
// 核心思想：
//   Actor 内部可以用 Timers 安排延迟消息或周期性消息：
//     - timers.startSingleTimer(key, msg, delay) → 延迟一次
//     - timers.startTimerWithFixedDelay(key, msg, delay) → 固定延迟周期
//     - timers.startTimerAtFixedRate(key, msg, interval) → 固定频率周期
//     - timers.cancel(key) → 取消定时器
//
//   与 fs2 Stream 的对比：
//     fs2：Stream.awakeEvery(1.second).evalMap(...)
//          优点：可以 pipe、transform，背压自然，可中断
//
//     Akka Timers：Actor 内部收到定时消息，处理在 Actor 线程内
//                  优点：与 Actor 状态天然集成，无需额外协调
//
// 本 Demo 演示：
//   1. 延迟单次消息（startSingleTimer）
//   2. 周期性消息（startTimerWithFixedDelay）
//   3. 取消定时器
//   4. 心跳 Actor（典型的 Timers 应用场景）

//> using scala "3.3.3"
//> using dep "com.typesafe.akka::akka-actor-typed:2.8.5"

import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.actor.typed.{ActorSystem, Behavior}
import scala.concurrent.duration._
import scala.concurrent.Await

// ── 心跳 Actor ────────────────────────────────────────────────────────────────

sealed trait HeartbeatCmd
object HeartbeatCmd:
  case object Tick                    extends HeartbeatCmd   // 定时器发送
  case object Start                   extends HeartbeatCmd
  case object Stop                    extends HeartbeatCmd
  case class  SetInterval(d: FiniteDuration) extends HeartbeatCmd

object HeartbeatActor:
  private case object HeartbeatKey    // 定时器 key（用于取消）

  def apply(): Behavior[HeartbeatCmd] =
    Behaviors.withTimers { timers =>
      idle(timers, beatCount = 0)
    }

  private def idle(timers: TimerScheduler[HeartbeatCmd], beatCount: Int): Behavior[HeartbeatCmd] =
    Behaviors.receive { (ctx, msg) =>
      msg match
        case HeartbeatCmd.Start =>
          ctx.log.info("[Heartbeat] 启动，每 300ms 发送一次心跳")
          timers.startTimerWithFixedDelay(HeartbeatKey, HeartbeatCmd.Tick, 300.millis)
          beating(timers, beatCount)

        case other =>
          ctx.log.warn(s"[Heartbeat] 空闲状态，忽略: $other")
          Behaviors.same
    }

  private def beating(timers: TimerScheduler[HeartbeatCmd], beatCount: Int): Behavior[HeartbeatCmd] =
    Behaviors.receive { (ctx, msg) =>
      msg match
        case HeartbeatCmd.Tick =>
          ctx.log.info(s"[Heartbeat] 💓 第 ${beatCount + 1} 次心跳")
          beating(timers, beatCount + 1)

        case HeartbeatCmd.Stop =>
          timers.cancel(HeartbeatKey)   // 取消定时器
          ctx.log.info(s"[Heartbeat] 停止，共心跳 $beatCount 次")
          idle(timers, beatCount)

        case HeartbeatCmd.SetInterval(d) =>
          // 重新设置间隔（先取消旧的，再启动新的）
          timers.startTimerWithFixedDelay(HeartbeatKey, HeartbeatCmd.Tick, d)
          ctx.log.info(s"[Heartbeat] 间隔调整为 $d")
          Behaviors.same

        case other =>
          ctx.log.warn(s"[Heartbeat] 运行中，忽略: $other")
          Behaviors.same
    }

// ── 倒计时 Actor（startSingleTimer 演示）──────────────────────────────────────

sealed trait CountdownCmd
object CountdownCmd:
  case class Start(from: Int)         extends CountdownCmd
  case object Tick                    extends CountdownCmd

object CountdownActor:
  private case object TickKey

  def apply(): Behavior[CountdownCmd] =
    Behaviors.withTimers { timers =>
      Behaviors.receive { (ctx, msg) =>
        msg match
          case CountdownCmd.Start(n) =>
            ctx.log.info(s"[Countdown] 开始倒计时: $n")
            timers.startSingleTimer(TickKey, CountdownCmd.Tick, 200.millis)
            counting(timers, n)
          case _ => Behaviors.same
      }
    }

  private def counting(timers: TimerScheduler[CountdownCmd], n: Int): Behavior[CountdownCmd] =
    Behaviors.receive { (ctx, msg) =>
      msg match
        case CountdownCmd.Tick =>
          if n > 0 then
            ctx.log.info(s"[Countdown] $n ...")
            timers.startSingleTimer(TickKey, CountdownCmd.Tick, 200.millis)
            counting(timers, n - 1)
          else
            ctx.log.info("[Countdown] 🎉 完成！")
            Behaviors.same
        case _ => Behaviors.same
    }

// ── 演示 ──────────────────────────────────────────────────────────────────────

@main def actorTimersDemo(): Unit =
  val system = ActorSystem(Behaviors.empty[Nothing], "timer-system")
  import system.executionContext

  println("=== Akka Timers：定时消息（对比 fs2 Stream.awakeEvery）===\n")

  // 演示 1：倒计时（startSingleTimer）
  println("── 1. 倒计时（startSingleTimer 链式触发）────────────────────────")
  val countdown = system.systemActorOf(CountdownActor(), "countdown")
  countdown ! CountdownCmd.Start(5)
  Thread.sleep(1500)   // 等待倒计时完成（5×200ms + 余量）

  // 演示 2：心跳（startTimerWithFixedDelay）
  println("\n── 2. 心跳（startTimerWithFixedDelay）─────────────────────────")
  val heartbeat = system.systemActorOf(HeartbeatActor(), "heartbeat")
  heartbeat ! HeartbeatCmd.Start
  Thread.sleep(1000)   // 让心跳跑约 3 次
  heartbeat ! HeartbeatCmd.Stop
  Thread.sleep(200)

  // 演示 3：调整心跳间隔
  println("\n── 3. 动态调整心跳间隔 ──────────────────────────────────────────")
  val hb2 = system.systemActorOf(HeartbeatActor(), "heartbeat2")
  hb2 ! HeartbeatCmd.Start
  Thread.sleep(500)
  hb2 ! HeartbeatCmd.SetInterval(100.millis)   // 加速到 100ms
  Thread.sleep(500)
  hb2 ! HeartbeatCmd.Stop
  Thread.sleep(200)

  println("""
|关键点（与 fs2 Stream.awakeEvery 对比）：
|
|  fs2 周期性流:                   Akka Timers:
|  ────────────────────────────    ──────────────────────────────────
|  Stream                          Behaviors.withTimers { timers =>
|    .awakeEvery(1.second)           timers.startTimerWithFixedDelay(
|    .evalMap(_ => doWork)              key, Tick, 1.second)
|    .interruptWhen(stop)           }
|    .compile.drain                 // 收到 Stop 消息时 timers.cancel(key)
|
|  特点：                           特点：
|  - 流式，可 pipe/transform         - 定时消息进入 Actor 邮箱
|  - interruptWhen 信号中断          - timers.cancel(key) 取消
|  - 背压自然                        - 与 Actor 状态天然集成
|  - 适合数据管道场景                - 适合"心跳/超时/重试"场景""".stripMargin)

  system.terminate()
  Await.result(system.whenTerminated, 5.seconds)
