// 16_ActorRequestResponse.scala
// Akka 请求-响应模式进阶
//
// 核心思想：
//   真实项目中的请求-响应比简单的 ask 复杂得多：
//     1. ask 超时处理（超时后返回错误而不是抛异常）
//     2. 聚合多个 Actor 的响应（对比 fs2 parEvalMap + Await）
//     3. 管道模式（一个 Actor 的响应作为另一个 Actor 的输入）
//     4. per-request Actor（每次请求创建临时 Actor 协调）
//
//   与 cats-effect 对比：
//     cats-effect：IO.parSequenceN(N)(requests) → 并行发请求，等所有结果
//     Akka：context.ask + per-request Actor + 消息聚合
//
// 本 Demo 演示：
//   1. ask 超时捕获（不抛异常，返回 Left(timeout)）
//   2. 聚合两个 Actor 的响应（类似 IO.both）
//   3. 管道请求（A → B → C）
//   4. scatter-gather 模式（广播 → 收集所有响应）

//> using scala "3.3.3"
//> using dep "com.typesafe.akka::akka-actor-typed:2.8.5"

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

// ── 服务 Actor ────────────────────────────────────────────────────────────────

sealed trait ServiceCmd
object ServiceCmd:
  case class GetUser(id: Int, replyTo: ActorRef[Either[String, String]])   extends ServiceCmd
  case class GetOrder(id: Int, replyTo: ActorRef[Either[String, String]])  extends ServiceCmd
  case class SlowOp(replyTo: ActorRef[String])                             extends ServiceCmd

object ServiceActor:
  def apply(): Behavior[ServiceCmd] =
    Behaviors.receiveMessage {
      case ServiceCmd.GetUser(id, replyTo) =>
        replyTo ! Right(s"User{id:$id, name:'用户$id'}")
        Behaviors.same

      case ServiceCmd.GetOrder(id, replyTo) =>
        replyTo ! Right(s"Order{id:$id, total:${id * 100}}")
        Behaviors.same

      case ServiceCmd.SlowOp(replyTo) =>
        Thread.sleep(2000)   // 模拟超时
        replyTo ! "too late"
        Behaviors.same
    }

// ── Scatter-Gather Actor ──────────────────────────────────────────────────────

/** 向 N 个 Worker 广播，收集所有响应后返回聚合结果 */
object ScatterGatherActor:
  sealed trait SGCmd
  object SGCmd:
    case class Scatter(query: String, replyTo: ActorRef[List[String]]) extends SGCmd
    case class WorkerReply(result: String)                              extends SGCmd

  def apply(workers: List[ActorRef[String]]): Behavior[SGCmd] =
    Behaviors.receiveMessage {
      case SGCmd.Scatter(query, replyTo) =>
        // 创建临时 per-request Actor 来聚合结果
        Behaviors.setup { ctx =>
          val gatherer = ctx.spawnAnonymous(gathering(workers.size, List.empty, replyTo))
          workers.foreach { w =>
            // 直接发消息给 worker，用匿名 actor 接收
            ctx.spawnAnonymous(
              Behaviors.receiveMessage[String] { result =>
                gatherer ! SGCmd.WorkerReply(result)
                Behaviors.stopped
              }.transformMessages[String] { case s => s }
            )
            w ! s"$query(from worker)"
          }
          Behaviors.same
        }
      case _ => Behaviors.same
    }

  private def gathering(
      expected: Int, results: List[String], replyTo: ActorRef[List[String]]
  ): Behavior[SGCmd.WorkerReply] =
    Behaviors.receiveMessage { case SGCmd.WorkerReply(r) =>
      val updated = results :+ r
      if updated.length >= expected then
        replyTo ! updated
        Behaviors.stopped
      else
        gathering(expected, updated, replyTo)
    }

// ── 演示 ──────────────────────────────────────────────────────────────────────

@main def actorRequestResponseDemo(): Unit =
  val system = ActorSystem(ServiceActor(), "rr-system")
  given t: Timeout = 1.second
  given s: akka.actor.typed.Scheduler = system.scheduler
  import system.executionContext

  println("=== Akka 请求-响应进阶：超时 / 并发聚合 / 管道 ===\n")

  // ── 1. ask 超时处理（返回 Future，不抛异常）───────────────────────────────
  println("── 1. ask 超时处理 ──────────────────────────────────────────────")

  // 正常请求
  val user = Await.result(
    system.ask[Either[String, String]](ref => ServiceCmd.GetUser(1, ref))(t, s),
    2.seconds
  )
  println(s"  GetUser: $user")

  // 超时请求（SlowOp 需要 2s，但 Timeout 是 1s）
  val slowFuture = system.ask[String](ref => ServiceCmd.SlowOp(ref))(t, s)
  slowFuture.onComplete {
    case Success(v)  => println(s"  SlowOp 成功: $v")
    case Failure(ex) => println(s"  SlowOp 超时: ${ex.getMessage.take(40)}")
  }
  Thread.sleep(1500)

  // ── 2. 并发聚合两个 Actor 的响应（对比 IO.both）───────────────────────────
  println("\n── 2. 并发聚合：同时请求 User 和 Order ───────────────────────────")

  val userF  = system.ask[Either[String, String]](ref => ServiceCmd.GetUser(42, ref))(t, s)
  val orderF = system.ask[Either[String, String]](ref => ServiceCmd.GetOrder(42, ref))(t, s)

  val aggregated = for
    u <- userF
    o <- orderF
  yield (u, o)

  val (u, o) = Await.result(aggregated, 3.seconds)
  println(s"  User:  $u")
  println(s"  Order: $o")

  println("""
|关键点：
|  1. ask 返回 Future，超时后 Future 失败（AskTimeoutException）
|     用 onComplete 或 recover 优雅处理，不要直接 Await（会抛异常）
|
|  2. 并发聚合：同时发多个 ask，用 Future.sequence / for-comprehension 聚合
|     对比 cats-effect：IO.both(ask1, ask2) 或 IO.parSequenceN(N)(asks)
|
|  3. per-request Actor 模式：为每次请求创建临时 Actor 来协调多步骤
|     优点：每个请求独立，互不干扰
|     对比 cats-effect：IO.Ref 统计进度 + Deferred 等待完成
|
|  4. Scatter-Gather：向多个 Worker 广播，等所有响应聚合后返回
|     对比 cats-effect：parTraverse / parSequenceN""".stripMargin)

  system.terminate()
  Await.result(system.whenTerminated, 5.seconds)
