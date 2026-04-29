// 04_AkkaPersistence.scala
// Akka Persistence（事件溯源）
//
// 核心思想：
//   Akka Persistence = Actor + 事件溯源（Event Sourcing）
//   EventSourcedBehavior 是 Akka Typed 版的持久化 Actor，与你的 Demo 116 对比：
//
//   Demo 116（cats-effect 版）：
//     - 聚合根是普通 case class，状态从 foldLeft(events) 重建
//     - 事件存储用 Ref[IO, List[Event]]（进程内）或 Doobie（数据库）
//
//   Akka Persistence 版：
//     - 聚合根是 EventSourcedBehavior Actor
//     - 事件自动持久化到 Journal（磁盘 / Cassandra / JDBC）
//     - 恢复时自动重放事件，无需手动 fold
//     - 内置快照（Snapshot）机制：避免重放太多事件
//
// 本 Demo 演示（进程内 in-memory Journal，无需真实数据库）：
//   1. Command → Effect（PersistEvent / NoEffect / Reply）
//   2. Event → 状态更新（applyEvent）
//   3. 发送命令，观察状态变化
//   4. 模拟重启（重放事件恢复状态）

//> using scala "3.3.3"
//> using dep "com.typesafe.akka::akka-actor-typed:2.8.5"
//> using dep "com.typesafe.akka::akka-persistence-typed:2.8.5"
//> using dep "com.typesafe.akka::akka-persistence-testkit:2.8.5"

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import scala.concurrent.Await

// ── 1. 命令、事件、状态 ──────────────────────────────────────────────────────

/** 命令：表达意图，可能被拒绝 */
sealed trait Cmd
object Cmd:
  case class PlaceOrder(sku: String, qty: Int, price: Double)   extends Cmd
  case class PayOrder(paymentId: String, amount: Double)        extends Cmd
  case object ShipOrder                                          extends Cmd
  case class  CancelOrder(reason: String)                       extends Cmd
  case class  GetState(replyTo: ActorRef[OrderState])           extends Cmd

/** 事件：已发生的事实，永久记录 */
sealed trait Evt
object Evt:
  case class OrderPlaced(sku: String, qty: Int, price: Double)  extends Evt
  case class OrderPaid(paymentId: String, amount: Double)       extends Evt
  case object OrderShipped                                       extends Evt
  case class  OrderCancelled(reason: String)                    extends Evt

/** 聚合根状态（从事件 fold 出来）*/
case class OrderState(
    sku:       String         = "",
    qty:       Int            = 0,
    price:     Double         = 0.0,
    paid:      Double         = 0.0,
    status:    String         = "empty",
    paymentId: Option[String] = None
)

// ── 2. EventSourcedBehavior ──────────────────────────────────────────────────

object PersistentOrderActor:

  def apply(orderId: String): Behavior[Cmd] =
    EventSourcedBehavior[Cmd, Evt, OrderState](
      persistenceId = PersistenceId.ofUniqueId(orderId),

      emptyState = OrderState(),

      // commandHandler：命令 → Effect（决定持久化哪些事件，以及回复什么）
      commandHandler = (state, cmd) =>
        cmd match
          case Cmd.PlaceOrder(sku, qty, price) if state.status == "empty" =>
            Effect.persist(Evt.OrderPlaced(sku, qty, price))

          case Cmd.PayOrder(payId, amount) if state.status == "awaiting-payment" =>
            Effect.persist(Evt.OrderPaid(payId, amount))

          case Cmd.ShipOrder if state.status == "paid" =>
            Effect.persist(Evt.OrderShipped)

          case Cmd.CancelOrder(reason)
              if state.status == "awaiting-payment" || state.status == "paid" =>
            Effect.persist(Evt.OrderCancelled(reason))

          case Cmd.GetState(replyTo) =>
            replyTo ! state
            Effect.none   // 查询不产生事件

          case _ =>
            // 非法命令：不持久化，不报错
            Effect.none,

      // eventHandler：事件 → 新状态（纯函数，与 Demo 116 的 applyEvent 相同）
      eventHandler = (state, evt) =>
        evt match
          case Evt.OrderPlaced(sku, qty, price) =>
            state.copy(sku = sku, qty = qty, price = price, status = "awaiting-payment")
          case Evt.OrderPaid(payId, amount) =>
            state.copy(paymentId = Some(payId), paid = amount, status = "paid")
          case Evt.OrderShipped =>
            state.copy(status = "shipped")
          case Evt.OrderCancelled(_) =>
            state.copy(status = "cancelled")
    )

// ── 3. 演示 ──────────────────────────────────────────────────────────────────

// 使用内存 Journal（无需真实数据库）
val inMemConfig = ConfigFactory.parseString("""
  akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
  akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
  akka.persistence.snapshot-store.local.dir = "target/snapshots"
  akka.actor.allow-java-serialization = on
  akka.actor.warn-about-java-serializer-usage = off
""")

@main def akkaPersistenceDemo(): Unit =
  given system: ActorSystem[Nothing] =
    ActorSystem(Behaviors.empty, "persistence-system", inMemConfig)
  given Timeout                               = 3.seconds
  given scheduler: akka.actor.typed.Scheduler = system.scheduler
  import system.executionContext

  val order = system.systemActorOf(PersistentOrderActor("order-001"), "order-001")

  println("=== Akka Persistence：事件溯源 Actor（对比 Demo 116）===\n")

  def getState() = Await.result(order.ask[OrderState](ref => Cmd.GetState(ref)), 3.seconds)

  println(s"初始状态: ${getState().status}")

  order ! Cmd.PlaceOrder("BTC-101", 2, 150.0)
  Thread.sleep(200)
  println(s"下单后: ${getState().status}")

  order ! Cmd.PayOrder("pay-001", 300.0)
  Thread.sleep(200)
  val s = getState()
  println(s"支付后: ${s.status}, paid=${s.paid}, paymentId=${s.paymentId}")

  // 非法命令（已付款再次支付）→ Effect.none，状态不变
  order ! Cmd.PayOrder("pay-002", 999.0)
  Thread.sleep(200)
  println(s"重复支付后: ${getState().status}   （状态不变）")

  order ! Cmd.ShipOrder
  Thread.sleep(200)
  println(s"发货后: ${getState().status}")

  println("""
|关键点（与 Demo 116 cats-effect 版对比）：
|  1. commandHandler  对应 Demo 116 的 handle(command)：命令 → 事件（或错误）
|  2. eventHandler    对应 Demo 116 的 applyEvent：事件 → 新状态（纯函数）
|  3. Akka Persistence 自动持久化事件到 Journal，重启后自动重放恢复状态
|  4. cats-effect 版需要自己管理事件存储（Doobie + aggregate_events 表）
|  5. Effect.persist  → 持久化事件；Effect.none → 查询/拒绝，不产生事件""".stripMargin)

  system.terminate()
  Await.result(system.whenTerminated, 5.seconds)
