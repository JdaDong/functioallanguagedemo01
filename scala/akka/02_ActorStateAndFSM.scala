// 02_ActorStateAndFSM.scala
// Akka Actor 状态机
//
// 核心思想：
//   Actor 的"行为切换"天然实现状态机：
//   每个 Behavior 代表一个状态，返回不同 Behavior 表示状态转移。
//
//   与 Demo 09_OrderStateMachine（用 sealed trait 建模）的对比：
//     09：纯函数状态机，状态转移用函数表达，无副作用
//     本 Demo：Actor 状态机，每个状态是一个独立 Behavior，
//              状态转移可以有副作用（发消息、写日志）
//
// 本 Demo 演示：
//   1. 用 Behavior 切换实现订单状态机
//   2. 不合法的状态转移被 Actor 忽略（不抛异常）
//   3. Behaviors.logMessages 包装，自动打印所有收到的消息

//> using scala "3.3.3"
//> using dep "com.typesafe.akka::akka-actor-typed:2.8.5"

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Await

// ── 消息协议 ──────────────────────────────────────────────────────────────────

sealed trait OrderCmd
object OrderCmd:
  case class  Place(sku: String, qty: Int, price: BigDecimal) extends OrderCmd
  case class  Pay(paymentId: String, amount: BigDecimal)      extends OrderCmd
  case object Ship                                            extends OrderCmd
  case object Deliver                                         extends OrderCmd
  case class  Cancel(reason: String)                          extends OrderCmd
  case class  GetStatus(replyTo: ActorRef[String])            extends OrderCmd

// ── Actor 状态机 ──────────────────────────────────────────────────────────────

object OrderActor:

  def apply(): Behavior[OrderCmd] = empty()

  // 状态 1：空（未下单）
  private def empty(): Behavior[OrderCmd] =
    Behaviors.receive { (ctx, msg) =>
      msg match
        case OrderCmd.Place(sku, qty, price) =>
          ctx.log.info(s"[empty → awaiting-payment] 下单: $sku ×$qty @$price")
          awaitingPayment(sku, qty, price)
        case OrderCmd.GetStatus(r) =>
          r ! "empty"; Behaviors.same
        case other =>
          ctx.log.warn(s"[empty] 非法消息: $other，忽略")
          Behaviors.same
    }

  // 状态 2：等待支付
  private def awaitingPayment(sku: String, qty: Int, price: BigDecimal): Behavior[OrderCmd] =
    Behaviors.receive { (ctx, msg) =>
      msg match
        case OrderCmd.Pay(payId, amount) =>
          ctx.log.info(s"[awaiting-payment → paid] 支付: payId=$payId amount=$amount")
          paid(sku, qty, price, payId)
        case OrderCmd.Cancel(reason) =>
          ctx.log.info(s"[awaiting-payment → cancelled] 取消: $reason")
          cancelled()
        case OrderCmd.GetStatus(r) =>
          r ! "awaiting-payment"; Behaviors.same
        case other =>
          ctx.log.warn(s"[awaiting-payment] 非法消息: $other，忽略")
          Behaviors.same
    }

  // 状态 3：已支付
  private def paid(sku: String, qty: Int, price: BigDecimal, payId: String): Behavior[OrderCmd] =
    Behaviors.receive { (ctx, msg) =>
      msg match
        case OrderCmd.Ship =>
          ctx.log.info(s"[paid → shipped] 发货")
          shipped(sku, qty, price, payId)
        case OrderCmd.Cancel(reason) =>
          ctx.log.info(s"[paid → cancelled] 取消并退款: $reason")
          cancelled()
        case OrderCmd.GetStatus(r) =>
          r ! "paid"; Behaviors.same
        case other =>
          ctx.log.warn(s"[paid] 非法消息: $other，忽略")
          Behaviors.same
    }

  // 状态 4：已发货
  private def shipped(sku: String, qty: Int, price: BigDecimal, payId: String): Behavior[OrderCmd] =
    Behaviors.receive { (ctx, msg) =>
      msg match
        case OrderCmd.Deliver =>
          ctx.log.info(s"[shipped → delivered] 送达")
          delivered()
        case OrderCmd.GetStatus(r) =>
          r ! "shipped"; Behaviors.same
        case other =>
          ctx.log.warn(s"[shipped] 非法消息: $other，忽略")
          Behaviors.same
    }

  // 状态 5：已送达（终态）
  private def delivered(): Behavior[OrderCmd] =
    Behaviors.receive { (ctx, msg) =>
      msg match
        case OrderCmd.GetStatus(r) => r ! "delivered"; Behaviors.same
        case other =>
          ctx.log.warn(s"[delivered] 终态，忽略消息: $other")
          Behaviors.same
    }

  // 状态 6：已取消（终态）
  private def cancelled(): Behavior[OrderCmd] =
    Behaviors.receive { (ctx, msg) =>
      msg match
        case OrderCmd.GetStatus(r) => r ! "cancelled"; Behaviors.same
        case other =>
          ctx.log.warn(s"[cancelled] 终态，忽略消息: $other")
          Behaviors.same
    }

// ── 演示 ──────────────────────────────────────────────────────────────────────

@main def actorFSMDemo(): Unit =
  val system = ActorSystem(OrderActor(), "order-fsm")
  given Timeout                               = 3.seconds
  given scheduler: akka.actor.typed.Scheduler = system.scheduler
  import system.executionContext

  def status() = Await.result(system.ask[String](ref => OrderCmd.GetStatus(ref)), 3.seconds)

  println("=== Akka Actor 状态机：订单履约流程 ===\n")

  println(s"初始状态: ${status()}")

  // 正常流程
  system ! OrderCmd.Place("BTC-101", 2, BigDecimal(150))
  Thread.sleep(100)
  println(s"下单后: ${status()}")

  system ! OrderCmd.Pay("pay-001", BigDecimal(300))
  Thread.sleep(100)
  println(s"支付后: ${status()}")

  // 非法消息（发货前再次下单）→ Actor 忽略
  system ! OrderCmd.Place("ETH-202", 1, BigDecimal(200))
  Thread.sleep(100)
  println(s"非法下单后: ${status()}   （状态不变）")

  system ! OrderCmd.Ship
  Thread.sleep(100)
  println(s"发货后: ${status()}")

  system ! OrderCmd.Deliver
  Thread.sleep(100)
  println(s"送达后: ${status()}")

  // 终态后取消 → 被忽略
  system ! OrderCmd.Cancel("想退货")
  Thread.sleep(100)
  println(s"终态取消尝试后: ${status()}   （终态不变）")

  println("""
|关键点：
|  1. 每个状态是独立的 Behavior 函数，状态转移 = 返回新 Behavior
|  2. 非法消息在对应状态的 Behavior 里被 log.warn + Behaviors.same 忽略，不报错
|  3. 终态（delivered/cancelled）只响应 GetStatus，其他消息全忽略
|  4. 与 Demo 09（纯函数状态机）对比：Actor 版天然支持副作用（发消息/写日志）""".stripMargin)

  system.terminate()
  Await.result(system.whenTerminated, 5.seconds)
