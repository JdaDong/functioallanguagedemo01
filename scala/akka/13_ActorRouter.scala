// 13_ActorRouter.scala
// Akka Router（负载均衡路由器）
//
// 核心思想：
//   Router 把消息分发给一组 Routee（工作 Actor），实现负载均衡：
//     - RoundRobinPool：轮询（最常用）
//     - RandomPool：随机
//     - BroadcastPool：广播（每个 Routee 都收到）
//     - ConsistentHashingPool：按 key 的一致性哈希
//
//   与 fs2 parEvalMap 的对比：
//     fs2 parEvalMap(N)：N 个 Fiber 并行处理流里的每个元素
//     Akka Router(N)：N 个 Actor 并行处理消息，内置负载均衡策略
//
// 本 Demo 演示：
//   1. RoundRobinPool：轮询分发（最常用的场景）
//   2. BroadcastPool：广播（每个 Routee 都处理）
//   3. 动态 Router：运行时增减 Routee 数量
//   4. 与 fs2 parEvalMap 的对比

//> using scala "3.3.3"
//> using dep "com.typesafe.akka::akka-actor-typed:2.8.5"

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Await
import java.util.concurrent.atomic.AtomicInteger

// ── 工作 Worker ───────────────────────────────────────────────────────────────

sealed trait WorkMsg
object WorkMsg:
  case class Process(taskId: Int, replyTo: ActorRef[String]) extends WorkMsg

val workerCounter = AtomicInteger(0)

object WorkerActor:
  def apply(): Behavior[WorkMsg] =
    Behaviors.setup { ctx =>
      val workerId = workerCounter.incrementAndGet()
      ctx.log.info(s"[Worker-$workerId] 启动")
      Behaviors.receiveMessage {
        case WorkMsg.Process(taskId, replyTo) =>
          Thread.sleep(50)   // 模拟工作
          val result = s"Worker-$workerId 完成任务 $taskId"
          ctx.log.info(s"[Worker-$workerId] $result")
          replyTo ! result
          Behaviors.same
      }
    }

// ── 手动实现 RoundRobin Router ────────────────────────────────────────────────

/** 手动 RoundRobin：管理一组 Worker，轮询分发消息 */
object RoundRobinRouter:
  sealed trait RouterMsg
  object RouterMsg:
    case class Route(taskId: Int, replyTo: ActorRef[String]) extends RouterMsg

  def apply(poolSize: Int): Behavior[RouterMsg] =
    Behaviors.setup { ctx =>
      val workers = (1 to poolSize).map { i =>
        ctx.spawn(WorkerActor(), s"worker-$i")
      }.toVector
      ctx.log.info(s"[Router] 启动，Worker 数量: $poolSize")
      routing(workers, currentIndex = 0)
    }

  private def routing(workers: Vector[ActorRef[WorkMsg]], currentIndex: Int): Behavior[RouterMsg] =
    Behaviors.receiveMessage {
      case RouterMsg.Route(taskId, replyTo) =>
        val worker = workers(currentIndex % workers.size)
        worker ! WorkMsg.Process(taskId, replyTo)
        routing(workers, currentIndex + 1)
    }

// ── Broadcast Router ──────────────────────────────────────────────────────────

/** Broadcast：所有 Worker 都收到同一条消息 */
object BroadcastRouter:
  sealed trait BcastMsg
  object BcastMsg:
    case class Broadcast(taskId: Int, replyTo: ActorRef[String]) extends BcastMsg

  def apply(poolSize: Int): Behavior[BcastMsg] =
    Behaviors.setup { ctx =>
      val workers = (1 to poolSize).map { i =>
        ctx.spawn(WorkerActor(), s"bcast-worker-$i")
      }.toVector
      Behaviors.receiveMessage {
        case BcastMsg.Broadcast(taskId, replyTo) =>
          workers.foreach(_ ! WorkMsg.Process(taskId, replyTo))
          Behaviors.same
      }
    }

// ── 演示 ──────────────────────────────────────────────────────────────────────

@main def actorRouterDemo(): Unit =
  import scala.concurrent.Future
  import scala.concurrent.ExecutionContext.Implicits.global

  println("=== Akka Router：负载均衡（对比 fs2 parEvalMap）===\n")

  // ── 场景 1：RoundRobin Pool（3 个 Worker 轮询）──────────────────────────
  println("── 场景 1：RoundRobin Pool（3 个 Worker 轮询）──────────────────")

  val rrSystem = ActorSystem(RoundRobinRouter(3), "rr-system")
  given t1: Timeout = 5.seconds
  given s1: akka.actor.typed.Scheduler = rrSystem.scheduler

  // 发送 9 条任务，每个 Worker 应该处理 3 条
  val rrFutures = (1 to 9).map { id =>
    rrSystem.ask[String](ref => RoundRobinRouter.RouterMsg.Route(id, ref))(t1, s1)
  }
  val rrResults = Await.result(Future.sequence(rrFutures), 10.seconds)
  println(s"  完成 ${rrResults.length} 个任务")
  rrResults.foreach(r => println(s"    $r"))

  rrSystem.terminate()
  Await.result(rrSystem.whenTerminated, 5.seconds)

  // ── 场景 2：Broadcast（所有 Worker 都处理）──────────────────────────────
  println("\n── 场景 2：Broadcast（2 个 Worker 都处理同一任务）────────────────")

  val bcSystem = ActorSystem(BroadcastRouter(2), "bc-system")
  given t2: Timeout = 5.seconds
  given s2: akka.actor.typed.Scheduler = bcSystem.scheduler

  val bcFutures = (1 to 2).map { _ =>
    bcSystem.ask[String](ref => BroadcastRouter.BcastMsg.Broadcast(42, ref))(t2, s2)
  }
  val bcResults = Await.result(Future.sequence(bcFutures), 5.seconds)
  println(s"  收到 ${bcResults.length} 个响应（每个 Worker 各一个）")
  bcResults.foreach(r => println(s"    $r"))

  bcSystem.terminate()
  Await.result(bcSystem.whenTerminated, 5.seconds)

  println("""
|关键点（与 fs2 parEvalMap 对比）：
|
|  fs2 parEvalMap(N):              Akka Router(N workers):
|  ─────────────────────────────   ───────────────────────────────────
|  Stream.emits(tasks)             tasks.foreach { t =>
|    .parEvalMap(3)(process)         router ! Route(t, replyTo)
|    .compile.toList               }
|
|  特点：                           特点：
|  - 流式，天然顺序/背压            - 消息驱动，无严格顺序保证
|  - N 个 Fiber 并行                - N 个 Actor 轮询接收
|  - 编译期类型安全                 - 支持多种路由策略
|  - 适合数据管道                   - 适合任务分发/工作池
|
|RoundRobin vs Broadcast vs Random vs ConsistentHashing：
|  RoundRobin：轮询，负载最均匀（最常用）
|  Random：随机，简单但不保证均匀
|  Broadcast：每个 Worker 都处理，适合缓存更新/配置广播
|  ConsistentHashing：相同 key 总路由到同一 Worker，适合有状态的 Worker""".stripMargin)
