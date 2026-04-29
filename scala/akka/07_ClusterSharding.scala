// 07_ClusterSharding.scala
// Akka Cluster Sharding（集群分片）
//
// 核心思想：
//   Cluster Sharding 是 Akka 最重要的分布式能力：
//     - 把大量 Actor（如每个用户/订单对应一个 Actor）均匀分布在集群节点上
//     - 按 entityId 路由消息到正确的节点
//     - Actor 不在本节点时自动转发到正确节点
//
//   这是 Akka 相比 cats-effect 最大的差异化：
//     cats-effect 的 Ref/MapRef 只能在单进程内共享状态
//     Cluster Sharding 可以把状态分布到数千个节点
//
//   典型场景：
//     - 每个用户一个 Actor（维护用户会话/状态）
//     - 每个订单一个 Actor（维护订单生命周期）
//     - 每个房间一个 Actor（维护聊天室状态）
//
// 本 Demo 演示（单节点模拟，不需要真实集群）：
//   1. 定义 Entity（分片实体）
//   2. ClusterSharding.init 注册实体
//   3. 按 entityId 发送消息（自动路由）
//   4. 不同 entityId → 不同 Actor 实例（互相隔离）

//> using scala "3.3.3"
//> using dep "com.typesafe.akka::akka-actor-typed:2.8.5"
//> using dep "com.typesafe.akka::akka-cluster-sharding-typed:2.8.5"

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import scala.concurrent.Await

// ── 实体消息协议 ──────────────────────────────────────────────────────────────

sealed trait CartCmd
object CartCmd:
  case class AddItem(item: String, qty: Int)          extends CartCmd
  case class RemoveItem(item: String)                  extends CartCmd
  case class GetCart(replyTo: ActorRef[Map[String, Int]]) extends CartCmd
  case object Checkout                                 extends CartCmd

  // 必须实现 EntityTypeKey 要求的 EntityId 注入
  case class Init(entityId: String)                    extends CartCmd

// ── 购物车 Actor（每个用户一个实例）─────────────────────────────────────────

object ShoppingCartActor:

  // EntityTypeKey：实体类型的唯一标识，用于分片注册
  val TypeKey: EntityTypeKey[CartCmd] =
    EntityTypeKey[CartCmd]("ShoppingCart")

  def apply(userId: String): Behavior[CartCmd] =
    active(userId, Map.empty)

  private def active(userId: String, items: Map[String, Int]): Behavior[CartCmd] =
    Behaviors.receive { (ctx, msg) =>
      msg match
        case CartCmd.AddItem(item, qty) =>
          val updated = items.updated(item, items.getOrElse(item, 0) + qty)
          ctx.log.info(s"[Cart:$userId] 添加: $item ×$qty，当前: $updated")
          active(userId, updated)

        case CartCmd.RemoveItem(item) =>
          val updated = items - item
          ctx.log.info(s"[Cart:$userId] 移除: $item，当前: $updated")
          active(userId, updated)

        case CartCmd.GetCart(replyTo) =>
          replyTo ! items
          Behaviors.same

        case CartCmd.Checkout =>
          ctx.log.info(s"[Cart:$userId] 结账: $items")
          active(userId, Map.empty)   // 结账后清空购物车

        case CartCmd.Init(id) =>
          ctx.log.info(s"[Cart] 初始化实体: $id")
          Behaviors.same
    }

// 单节点集群配置（不需要真实多节点）
val clusterConfig = ConfigFactory.parseString("""
  akka.actor.provider = cluster
  akka.remote.artery.canonical.hostname = "127.0.0.1"
  akka.remote.artery.canonical.port = 2551
  akka.cluster.seed-nodes = ["akka://shopping-system@127.0.0.1:2551"]
  akka.cluster.downing-provider-class = akka.cluster.sbr.SplitBrainResolverProvider
  akka.actor.allow-java-serialization = on
  akka.actor.warn-about-java-serializer-usage = off
""")

// ── 演示 ──────────────────────────────────────────────────────────────────────

@main def clusterShardingDemo(): Unit =
  val system = ActorSystem[Nothing](
    Behaviors.empty,
    "shopping-system",
    clusterConfig
  )

  given Timeout   = 3.seconds
  given scheduler: akka.actor.typed.Scheduler = system.scheduler
  import system.executionContext

  println("=== Akka Cluster Sharding：每个用户一个 Actor 实例 ===\n")

  // 注册实体类型到 ClusterSharding
  val sharding = ClusterSharding(system)
  val shardRegion = sharding.init(
    Entity(ShoppingCartActor.TypeKey) { ctx =>
      ShoppingCartActor(ctx.entityId)   // 按 entityId 创建 Actor
    }
  )

  // 按 entityId 发送消息（自动路由到正确实例）
  // user-001 和 user-002 是两个完全独立的 Actor 实例
  shardRegion ! EntityTypeKey[CartCmd].apply("user-001") -> CartCmd.AddItem("BTC", 2)
  shardRegion ! EntityTypeKey[CartCmd].apply("user-001") -> CartCmd.AddItem("ETH", 5)
  shardRegion ! EntityTypeKey[CartCmd].apply("user-002") -> CartCmd.AddItem("BNB", 10)

  Thread.sleep(300)

  // 查询 user-001 的购物车
  val cart1 = Await.result(
    sharding.entityRefFor(ShoppingCartActor.TypeKey, "user-001")
      .ask[Map[String, Int]](ref => CartCmd.GetCart(ref)),
    3.seconds
  )
  println(s"user-001 购物车: $cart1")

  // 查询 user-002 的购物车
  val cart2 = Await.result(
    sharding.entityRefFor(ShoppingCartActor.TypeKey, "user-002")
      .ask[Map[String, Int]](ref => CartCmd.GetCart(ref)),
    3.seconds
  )
  println(s"user-002 购物车: $cart2")

  // user-001 移除一个商品
  sharding.entityRefFor(ShoppingCartActor.TypeKey, "user-001") ! CartCmd.RemoveItem("ETH")
  Thread.sleep(200)

  val cart1Updated = Await.result(
    sharding.entityRefFor(ShoppingCartActor.TypeKey, "user-001")
      .ask[Map[String, Int]](ref => CartCmd.GetCart(ref)),
    3.seconds
  )
  println(s"user-001 移除 ETH 后: $cart1Updated")

  println("""
|关键点：
|  1. EntityTypeKey 是实体类型的唯一标识，不同业务（购物车/订单/房间）用不同 TypeKey
|  2. sharding.entityRefFor(TypeKey, entityId) 获取实体引用，自动路由到正确节点
|  3. 不同 entityId 对应不同 Actor 实例，完全隔离（user-001 和 user-002 互不干扰）
|  4. 真实集群中，"路由到正确节点"由 Akka 框架自动处理，业务代码无感知
|  5. 与 cats-effect MapRef 对比：
|     - MapRef：单进程内的 key → value 并发状态
|     - Cluster Sharding：跨节点的 entityId → Actor 分布式状态
|  6. 与 Saga/ProcessManager 对比：每个进程/Saga 可以是一个独立的 Entity""".stripMargin)

  system.terminate()
  Await.result(system.whenTerminated, 5.seconds)
