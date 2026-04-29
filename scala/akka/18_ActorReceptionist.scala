// 18_ActorReceptionist.scala
// Akka Typed Receptionist（服务发现）
//
// 核心思想：
//   Receptionist 是 Akka Typed 的内置服务注册/发现机制：
//     - Actor 把自己注册到 Receptionist（用 ServiceKey）
//     - 其他 Actor 向 Receptionist 订阅/查询，获取提供该服务的 Actor 列表
//     - 支持动态更新：Actor 停止时自动从 Receptionist 移除
//
//   与 cats-effect 的对比：
//     cats-effect：没有内置服务发现，通常用 Ref[IO, Map[ServiceKey, ActorRef]] 手动维护
//     Akka Receptionist：框架级服务发现，支持 Cluster 级别（跨节点发现）
//
//   典型场景：
//     - 动态服务注册（Worker 启动后注册自己，挂掉后自动注销）
//     - 负载发现（查询所有提供某服务的 Actor，再做负载均衡）
//     - 发布者找订阅者（事件发布者不知道有哪些订阅者，由 Receptionist 管理）
//
// 本 Demo 演示：
//   1. ServiceKey 定义服务接口
//   2. 注册服务（Receptionist.Register）
//   3. 订阅服务变更（Receptionist.Subscribe）
//   4. 查询服务（Receptionist.Find）

//> using scala "3.3.3"
//> using dep "com.typesafe.akka::akka-actor-typed:2.8.5"

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Await

// ── 服务定义 ──────────────────────────────────────────────────────────────────

/** 计算服务接口 */
sealed trait CalcCmd
object CalcCmd:
  case class Add(a: Int, b: Int, replyTo: ActorRef[Int]) extends CalcCmd

object CalcService:
  // ServiceKey：服务的唯一类型标识
  val Key: ServiceKey[CalcCmd] = ServiceKey[CalcCmd]("calc-service")

  def apply(name: String): Behavior[CalcCmd] =
    Behaviors.setup { ctx =>
      // 注册自己到 Receptionist
      ctx.system.receptionist ! Receptionist.Register(Key, ctx.self)
      ctx.log.info(s"[$name] 已注册到 Receptionist")

      Behaviors.receiveMessage {
        case CalcCmd.Add(a, b, replyTo) =>
          ctx.log.info(s"[$name] 计算 $a + $b")
          replyTo ! (a + b)
          Behaviors.same
      }
    }

// ── 客户端 Actor（监听服务变更）──────────────────────────────────────────────

sealed trait ClientCmd
object ClientCmd:
  // 包裹 Receptionist 的 Listing 消息
  case class ServicesUpdated(listing: Receptionist.Listing) extends ClientCmd
  case class CalcResult(value: Int)                          extends ClientCmd

object ClientActor:
  def apply(): Behavior[ClientCmd] =
    Behaviors.setup { ctx =>
      // 订阅 CalcService.Key 的变更（有新服务注册/注销时收到通知）
      val listingAdapter = ctx.messageAdapter[Receptionist.Listing](ClientCmd.ServicesUpdated.apply)
      ctx.system.receptionist ! Receptionist.Subscribe(CalcService.Key, listingAdapter)
      ctx.log.info("[Client] 订阅 CalcService 变更")

      watching(Set.empty)
    }

  private def watching(services: Set[ActorRef[CalcCmd]]): Behavior[ClientCmd] =
    Behaviors.receive { (ctx, msg) =>
      msg match
        case ClientCmd.ServicesUpdated(listing) =>
          val instances = listing.serviceInstances(CalcService.Key)
          ctx.log.info(s"[Client] 服务变更！当前 CalcService 实例数: ${instances.size}")
          watching(instances)

        case ClientCmd.CalcResult(v) =>
          ctx.log.info(s"[Client] 收到计算结果: $v")
          Behaviors.same
    }

// ── 演示 ──────────────────────────────────────────────────────────────────────

@main def actorReceptionistDemo(): Unit =
  val system = ActorSystem(Behaviors.empty[Nothing], "receptionist-system")
  given Timeout = 3.seconds
  given scheduler: akka.actor.typed.Scheduler = system.scheduler
  import system.executionContext

  println("=== Akka Receptionist：服务发现（动态注册/订阅）===\n")

  // 启动客户端（订阅服务变更）
  val client = system.systemActorOf(ClientActor(), "client")

  Thread.sleep(200)

  // 动态注册两个 CalcService 实例
  println("── 注册两个 CalcService 实例 ────────────────────────────────────")
  val svc1 = system.systemActorOf(CalcService("calc-1"), "calc-1")
  Thread.sleep(200)
  val svc2 = system.systemActorOf(CalcService("calc-2"), "calc-2")
  Thread.sleep(200)

  // 查询所有 CalcService 实例
  println("\n── 查询并使用服务 ───────────────────────────────────────────────")
  val listing = Await.result(
    system.receptionist.ask[Receptionist.Listing](ref =>
      Receptionist.Find(CalcService.Key, ref)),
    3.seconds
  )
  val instances = listing.serviceInstances(CalcService.Key)
  println(s"  发现 ${instances.size} 个 CalcService 实例")

  // 向第一个实例发送请求
  instances.headOption.foreach { svc =>
    val result = Await.result(
      svc.ask[Int](ref => CalcCmd.Add(10, 32, ref)),
      3.seconds
    )
    println(s"  10 + 32 = $result")
  }

  println("""
|关键点：
|  1. ServiceKey[T] 是服务的类型安全标识，不同服务用不同 Key
|  2. Receptionist.Register：Actor 注册自己；Actor 停止时自动注销
|  3. Receptionist.Subscribe：订阅服务变更，有 Actor 注册/注销时收到通知
|  4. Receptionist.Find：一次性查询，获取当前所有实例列表
|
|与 cats-effect 对比：
|  cats-effect 没有内置服务发现，通常用：
|    Ref[IO, Map[String, ActorRef]] 手动维护服务注册表
|    或借助外部系统（Consul / etcd / ZooKeeper）
|
|  Akka Receptionist 的优势：
|    - 框架级实现，Actor 停止时自动注销（无需手动清理）
|    - Cluster 模式下自动跨节点服务发现
|    - 类型安全：ServiceKey[T] 保证消息类型正确""".stripMargin)

  system.terminate()
  Await.result(system.whenTerminated, 5.seconds)
