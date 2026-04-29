// 12_ActorStash.scala
// Akka Stash（消息暂存）
//
// 核心思想：
//   Stash 解决一个典型问题：
//     Actor 在某个状态下收到了"现在还不能处理"的消息，
//     不想丢弃，也不想重试，而是暂存起来，等状态就绪后再统一处理。
//
//   典型场景：
//     - 数据库连接 Actor：初始化中收到查询请求，先暂存，连接就绪后再处理
//     - 认证 Actor：token 刷新中收到请求，先暂存，token 就绪后统一重放
//     - 分布式锁 Actor：等待锁释放时暂存命令
//
//   Stash vs Deferred (cats-effect) 对比：
//     Deferred：一次性信号，等一个值就绪
//     Stash：暂存多条消息，状态就绪后一次性重放（unstashAll）
//
// 本 Demo 演示：
//   1. 数据库连接 Actor：初始化中暂存请求
//   2. unstashAll：状态就绪后重放所有暂存消息
//   3. 暂存容量限制

//> using scala "3.3.3"
//> using dep "com.typesafe.akka::akka-actor-typed:2.8.5"

import akka.actor.typed.scaladsl.{Behaviors, StashBuffer}
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Await

// ── 数据库 Actor（需要初始化）────────────────────────────────────────────────

sealed trait DbCmd
object DbCmd:
  case object Connect                                     extends DbCmd
  case class  Query(sql: String, replyTo: ActorRef[String]) extends DbCmd
  case class  Insert(data: String)                        extends DbCmd
  case object Disconnect                                  extends DbCmd

object DatabaseActor:

  def apply(): Behavior[DbCmd] =
    // capacity = 100：最多暂存 100 条消息
    Behaviors.withStash(capacity = 100) { buffer =>
      initializing(buffer)
    }

  /** 初始化中：暂存所有业务消息，等待连接就绪 */
  private def initializing(buffer: StashBuffer[DbCmd]): Behavior[DbCmd] =
    Behaviors.receive { (ctx, msg) =>
      msg match
        case DbCmd.Connect =>
          ctx.log.info("[DB] 开始建立数据库连接...")
          // 模拟异步连接（延迟后自发一条消息）
          ctx.scheduleOnce(300.millis, ctx.self, DbCmd.Connect)
          // 假设第一次 Connect 是"初始化触发"，之后进入 ready
          ctx.log.info("[DB] 连接建立完成，重放暂存的消息...")
          buffer.unstashAll(ready())   // 切换到 ready 状态，并重放所有暂存消息

        case other =>
          // 其他消息暂存
          ctx.log.info(s"[DB] 初始化中，暂存消息: $other")
          buffer.stash(other)
          Behaviors.same
    }

  /** 就绪状态：正常处理消息 */
  private def ready(): Behavior[DbCmd] =
    Behaviors.receive { (ctx, msg) =>
      msg match
        case DbCmd.Query(sql, replyTo) =>
          ctx.log.info(s"[DB] 执行查询: $sql")
          replyTo ! s"查询结果: [$sql] → {id:1, name:'张三'}"
          Behaviors.same

        case DbCmd.Insert(data) =>
          ctx.log.info(s"[DB] 插入数据: $data")
          Behaviors.same

        case DbCmd.Connect =>
          ctx.log.warn("[DB] 已连接，忽略 Connect 消息")
          Behaviors.same

        case DbCmd.Disconnect =>
          ctx.log.info("[DB] 断开连接")
          Behaviors.stopped
      }

// ── 演示 ──────────────────────────────────────────────────────────────────────

@main def actorStashDemo(): Unit =
  val system = ActorSystem(DatabaseActor(), "stash-system")
  given Timeout = 3.seconds
  given scheduler: akka.actor.typed.Scheduler = system.scheduler
  import system.executionContext

  println("=== Akka Stash：消息暂存（对比 cats-effect Deferred）===\n")

  // 先发送业务请求（此时 Actor 还在初始化中）
  println("── 在初始化完成前发送多条请求 ─────────────────────────────────")
  system ! DbCmd.Insert("user{id:1, name:'李四'}")
  system ! DbCmd.Insert("user{id:2, name:'王五'}")

  val queryFuture = system.ask[String](ref => DbCmd.Query("SELECT * FROM users", ref))

  // 发送 Connect 触发初始化完成
  println("── 发送 Connect 触发初始化完成，触发 unstashAll ───────────────")
  system ! DbCmd.Connect

  // 等待查询结果（此时 Actor 已 unstashAll，Query 消息被处理）
  val result = Await.result(queryFuture, 5.seconds)
  println(s"\n查询结果: $result")

  println("""
|说明（查看上方日志顺序）：
|  1. Insert × 2 先到达，Actor 在初始化中 → 暂存
|  2. Query 到达，Actor 在初始化中 → 暂存
|  3. Connect 到达 → 触发 unstashAll，切换到 ready 状态
|  4. 暂存的 Insert × 2 + Query 按顺序被重放处理
|
|关键点：
|  1. Stash 不丢消息，等状态就绪后 unstashAll 按顺序重放
|  2. capacity 限制暂存容量，超出时抛 StashOverflowException
|  3. 用途：初始化等待、分布式锁等待、资源加载等待
|
|与 cats-effect 对比：
|  Deferred[IO, Connection]：一次性信号，等一个值 → IO.pure(conn)
|  Stash：等状态就绪后，重放多条消息（更适合 Actor 消息驱动模型）""".stripMargin)

  system.terminate()
  Await.result(system.whenTerminated, 5.seconds)
