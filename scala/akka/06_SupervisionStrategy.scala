// 06_SupervisionStrategy.scala
// Akka 监督策略（SupervisionStrategy）
//
// 核心思想：
//   Akka 的核心设计哲学之一："让它崩溃"（Let it crash）
//
//   在 cats-effect 里，异常用 IO.handleErrorWith / EitherT 优雅处理，
//   通常不让 Fiber 崩溃。
//
//   在 Akka 里，崩溃的 Actor 由其父 Actor 监督并决定如何恢复：
//     - Restart：重启 Actor（清空状态，重新初始化）
//     - Stop：停止 Actor
//     - Resume：忽略异常，继续处理下一条消息
//     - Escalate：把异常向上抛给祖父 Actor 处理
//
//   监督策略是 Akka 容错模型的核心。
//
// 本 Demo 演示：
//   1. 子 Actor 在处理特定消息时抛出异常
//   2. 父 Actor 用 Behaviors.supervise 包裹子 Actor，指定 onFailure 策略
//   3. 对比三种策略：Restart / Stop / Resume 的不同效果
//   4. 与 cats-effect Supervisor 对比

//> using scala "3.3.3"
//> using dep "com.typesafe.akka::akka-actor-typed:2.8.5"

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorSystem, Behavior, SupervisorStrategy}
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Await

// ── 消息协议 ──────────────────────────────────────────────────────────────────

sealed trait WorkerCmd
object WorkerCmd:
  case class Process(value: Int, replyTo: akka.actor.typed.ActorRef[String]) extends WorkerCmd
  case object GetCount extends WorkerCmd

// ── 不稳定的子 Worker Actor ──────────────────────────────────────────────────

object UnstableWorker:
  def apply(): Behavior[WorkerCmd] = working(processedCount = 0)

  private def working(processedCount: Int): Behavior[WorkerCmd] =
    Behaviors.receive { (ctx, msg) =>
      msg match
        case WorkerCmd.Process(value, replyTo) =>
          if value == 0 then
            throw new ArithmeticException(s"除零错误！value=$value")
          val result = 100 / value
          ctx.log.info(s"[Worker] 处理: 100/$value = $result (已处理 ${processedCount + 1} 次)")
          replyTo ! s"结果: $result"
          working(processedCount + 1)

        case WorkerCmd.GetCount =>
          ctx.log.info(s"[Worker] 当前处理次数: $processedCount")
          Behaviors.same
    }

// ── 监督父 Actor ──────────────────────────────────────────────────────────────

sealed trait SupervisorCmd
object SupervisorCmd:
  case class Send(value: Int, replyTo: akka.actor.typed.ActorRef[String]) extends SupervisorCmd
  case object CheckWorkerCount extends SupervisorCmd

object SupervisorActor:

  def withRestart(): Behavior[SupervisorCmd] =
    superviseWith(SupervisorStrategy.restart)

  def withResume(): Behavior[SupervisorCmd] =
    superviseWith(SupervisorStrategy.resume)

  private def superviseWith(strategy: SupervisorStrategy): Behavior[SupervisorCmd] =
    Behaviors.setup { ctx =>
      val worker = ctx.spawn(
        Behaviors.supervise(UnstableWorker()).onFailure(strategy),
        "unstable-worker"
      )

      Behaviors.receiveMessage {
        case SupervisorCmd.Send(value, replyTo) =>
          worker ! WorkerCmd.Process(value, replyTo)
          Behaviors.same

        case SupervisorCmd.CheckWorkerCount =>
          worker ! WorkerCmd.GetCount
          Behaviors.same
      }
    }

// ── 演示 ──────────────────────────────────────────────────────────────────────

@main def supervisionStrategyDemo(): Unit =

  println("=== Akka 监督策略：让它崩溃（Let it crash）===\n")

  // ── 场景 1：Restart 策略（崩溃后重启，状态清零）──────────────────────────
  println("── 场景 1：Restart 策略（崩溃后重启，计数器清零）────────────────────")

  val sys1 = ActorSystem(SupervisorActor.withRestart(), "restart-system")
  given t1: Timeout = 2.seconds
  given s1: akka.actor.typed.Scheduler = sys1.scheduler
  import sys1.executionContext

  // 处理两次正常消息（计数 = 2）
  val r1 = Await.result(sys1.ask[String](ref => SupervisorCmd.Send(5, ref))(t1, s1), 2.seconds)
  println(s"  处理 100/5 = $r1")
  val r2 = Await.result(sys1.ask[String](ref => SupervisorCmd.Send(4, ref))(t1, s1), 2.seconds)
  println(s"  处理 100/4 = $r2")

  // 触发崩溃（value=0，除零异常）→ Worker 重启，计数清零
  println("  发送 value=0 触发 ArithmeticException...")
  sys1 ! SupervisorCmd.Send(0, sys1.deadLetters)
  Thread.sleep(200)

  // 重启后计数应该回到 0
  sys1 ! SupervisorCmd.CheckWorkerCount
  Thread.sleep(200)
  println("  [Restart] Worker 已重启，计数器清零（见日志 '已处理 0 次'）")

  sys1.terminate()
  Await.result(sys1.whenTerminated, 3.seconds)

  // ── 场景 2：Resume 策略（崩溃后忽略异常，计数保留）──────────────────────
  println("\n── 场景 2：Resume 策略（崩溃后继续，计数不变）──────────────────────")

  val sys2 = ActorSystem(SupervisorActor.withResume(), "resume-system")
  import akka.actor.typed.scaladsl.AskPattern.Askable
  given t2: Timeout = 2.seconds
  given s2: akka.actor.typed.Scheduler = sys2.scheduler

  val r3 = Await.result(sys2.ask[String](ref => SupervisorCmd.Send(10, ref))(t2, s2), 2.seconds)
  println(s"  处理 100/10 = $r3")
  val r4 = Await.result(sys2.ask[String](ref => SupervisorCmd.Send(2, ref))(t2, s2), 2.seconds)
  println(s"  处理 100/2 = $r4")

  println("  发送 value=0 触发 ArithmeticException...")
  sys2 ! SupervisorCmd.Send(0, sys2.deadLetters)
  Thread.sleep(200)

  // Resume 后计数仍然是 2（不是 0）
  sys2 ! SupervisorCmd.CheckWorkerCount
  Thread.sleep(200)
  println("  [Resume] Worker 继续运行，计数仍为 2（见日志 '已处理 2 次'）")

  sys2.terminate()
  Await.result(sys2.whenTerminated, 3.seconds)

  println("""
|关键点：
|  1. Restart：子 Actor 崩溃后重新创建，状态清零，适合"状态可以丢弃"的场景
|  2. Resume：忽略异常，子 Actor 继续运行，状态保留，适合"异常是正常情况"的场景
|  3. Stop：子 Actor 崩溃后永久停止，父 Actor 可以选择是否重新 spawn
|  4. 父 Actor 无需 try/catch，失败处理策略与业务逻辑完全分离
|  5. 与 cats-effect Supervisor 对比：
|     - Supervisor 托管 fiber 生命周期，异常用 IO.handleErrorWith 处理
|     - Akka 的"让它崩溃"让 Actor 保持简单，异常处理交给父 Actor""".stripMargin)
