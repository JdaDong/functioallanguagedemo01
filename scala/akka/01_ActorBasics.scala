// 01_ActorBasics.scala
// Akka Actor 基础
//
// 核心思想：
//   Actor 模型 = 计算的基本单元，每个 Actor 是一个轻量级"进程"
//   Actor 之间只通过消息通信，不共享任何可变状态
//
//   与 cats-effect Fiber 的对比：
//     Fiber：结构化并发，父子层级由 Supervisor 管理，用 IO monad 描述副作用
//     Actor：消息驱动，每个 Actor 独立维护内部状态，通过邮箱异步接收消息
//
// 本 Demo 演示：
//   1. 定义消息（sealed trait + case class/object）
//   2. 定义 Actor 行为（Behaviors.receive）
//   3. 创建 ActorSystem，spawn 根 Actor
//   4. 发送消息（!）
//   5. 请求-响应（ask 模式）
//   6. 父 Actor 创建子 Actor

//> using scala "3.3.3"
//> using dep "com.typesafe.akka::akka-actor-typed:2.8.5"

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Await

// ── 1. 定义消息协议 ─────────────────────────────────────────────────────────

/** 计数器 Actor 的消息类型 */
sealed trait CounterCmd
object CounterCmd:
  case object Increment                           extends CounterCmd
  case object Decrement                           extends CounterCmd
  case class  Reset(to: Int)                      extends CounterCmd
  case class  GetCount(replyTo: ActorRef[Int])    extends CounterCmd

// ── 2. 定义 Actor 行为 ──────────────────────────────────────────────────────

/** 计数器 Actor
  *
  * Behaviors.receive 返回一个 Behavior，描述"收到某条消息时做什么"
  * - 返回 Behaviors.same  → 行为不变，继续处理下一条消息
  * - 返回新的 Behavior    → 切换到新行为（状态机跳转）
  * - 返回 Behaviors.stopped → 停止 Actor
  */
object CounterActor:
  def apply(initial: Int = 0): Behavior[CounterCmd] =
    counting(initial)

  private def counting(count: Int): Behavior[CounterCmd] =
    Behaviors.receive { (ctx, msg) =>
      msg match
        case CounterCmd.Increment =>
          ctx.log.info(s"Increment: $count → ${count + 1}")
          counting(count + 1)               // 返回新 Behavior，count+1

        case CounterCmd.Decrement =>
          ctx.log.info(s"Decrement: $count → ${count - 1}")
          counting(count - 1)

        case CounterCmd.Reset(to) =>
          ctx.log.info(s"Reset: $count → $to")
          counting(to)

        case CounterCmd.GetCount(replyTo) =>
          replyTo ! count                    // 回复消息给请求方
          Behaviors.same
    }

// ── 3. 父子 Actor ─────────────────────────────────────────────────────────

/** 父 Actor：创建并管理多个子计数器 */
object ManagerActor:
  sealed trait ManagerCmd
  object ManagerCmd:
    case class CreateCounter(name: String) extends ManagerCmd
    case class IncrementCounter(name: String, times: Int) extends ManagerCmd
    case class QueryCounter(name: String, replyTo: ActorRef[Option[Int]]) extends ManagerCmd

  def apply(): Behavior[ManagerCmd] =
    managing(Map.empty)

  private def managing(counters: Map[String, ActorRef[CounterCmd]]): Behavior[ManagerCmd] =
    Behaviors.receive { (ctx, msg) =>
      msg match
        case ManagerCmd.CreateCounter(name) =>
          // 用 ctx.spawn 创建子 Actor，父 Actor 自动监督子 Actor
          val child = ctx.spawn(CounterActor(0), name)
          ctx.log.info(s"[Manager] 创建子计数器: $name")
          managing(counters.updated(name, child))

        case ManagerCmd.IncrementCounter(name, times) =>
          counters.get(name).foreach { ref =>
            (1 to times).foreach(_ => ref ! CounterCmd.Increment)
          }
          Behaviors.same

        case ManagerCmd.QueryCounter(name, replyTo) =>
          counters.get(name) match
            case None      => replyTo ! None; Behaviors.same
            case Some(ref) =>
              // 无法直接 ask 一个子 actor 并把结果传给外部，这里演示转发模式
              // 实际中可用 ctx.ask 解决
              ctx.log.info(s"[Manager] 查询 $name（需通过 ask 获取结果）")
              Behaviors.same
    }

// ── 4. 演示主程序 ────────────────────────────────────────────────────────────

@main def actorBasicsDemo(): Unit =
  // 创建 ActorSystem（根 Actor）
  val system: ActorSystem[CounterCmd] =
    ActorSystem(CounterActor(0), "counter-system")

  given timeout: Timeout                      = 3.seconds
  given scheduler: akka.actor.typed.Scheduler = system.scheduler
  import system.executionContext

  println("=== Akka Actor 基础：计数器 Demo ===\n")

  // 发送消息（fire-and-forget，! 操作符）
  system ! CounterCmd.Increment
  system ! CounterCmd.Increment
  system ! CounterCmd.Increment
  system ! CounterCmd.Decrement

  // ask 模式：请求-响应，返回 Future
  val countFuture = system.ask[Int](ref => CounterCmd.GetCount(ref))
  val count = Await.result(countFuture, 3.seconds)
  println(s"当前计数: $count   （预期: 2，3次+1 - 1次-1）")

  system ! CounterCmd.Reset(100)
  val count2 = Await.result(system.ask[Int](ref => CounterCmd.GetCount(ref)), 3.seconds)
  println(s"Reset 后计数: $count2   （预期: 100）")

  println("""
|关键点：
|  1. Actor 只通过消息通信，不共享可变状态（内部 count 是不可变的，每次返回新 Behavior）
|  2. ! 是 fire-and-forget（发完不等），ask 是请求-响应返回 Future
|  3. ctx.spawn 创建子 Actor，父 Actor 自动成为其 supervisor
|  4. 消息类型用 sealed trait 封闭，编译器保证 exhaustive match
|  5. 与 cats-effect Fiber 对比：Actor 是消息驱动，Fiber 是 IO monad 驱动""".stripMargin)

  system.terminate()
  Await.result(system.whenTerminated, 5.seconds)
  println("\n[ActorSystem 已停止]")
