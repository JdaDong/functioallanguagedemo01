// 121_CatsEffectProcessManager.scala
// 进程管理器（Process Manager）第一步：cats-effect 进程管理器状态机
//
// 核心思想：
//   进程管理器（Process Manager）= 跨聚合根协调 + 持久化状态机
//
//   与 Saga 的区别：
//     Saga：一次性补偿流程，失败时执行反向动作
//     进程管理器：长生命周期状态机，消费事件 → 推进状态 → 发出命令
//                 自身也是一个事件溯源的实体（有自己的事件序列）
//
//   典型场景：电商完整履约流程
//     下单 → 支付完成 → 通知库存扣减 → 库存确认 → 通知物流 → 物流取件 → 物流送达
//     这条链路跨越 Order、Payment、Inventory、Logistics 四个有界上下文
//
// 本 Demo 演示：
//   1. FulfillmentProcess：履约流程进程管理器
//   2. 进程管理器自己的状态从它接收到的事件 fold 出来
//   3. 每次收到新事件 → 推进状态 → 发出下一个命令
//   4. 命令存入 CommandOutbox，等待被执行

//> using scala "3.3.3"
//> using dep "org.typelevel::cats-effect:3.5.4"

import cats.effect.{IO, IOApp, Ref}
import cats.syntax.all._

// ── 进来的事件（来自各有界上下文）───────────────────────────────────────────

sealed trait FulfillmentEvent
object FulfillmentEvent:
  case class OrderConfirmed(orderId: String, sku: String, quantity: Int) extends FulfillmentEvent
  case class PaymentCompleted(orderId: String, paymentId: String, amount: BigDecimal)
      extends FulfillmentEvent
  case class InventoryReserved(orderId: String, warehouseId: String)  extends FulfillmentEvent
  case class ShipmentDispatched(orderId: String, trackingNo: String)  extends FulfillmentEvent
  case class ShipmentDelivered(orderId: String)                        extends FulfillmentEvent
  case class PaymentFailed(orderId: String, reason: String)           extends FulfillmentEvent
  case class InventoryUnavailable(orderId: String, reason: String)    extends FulfillmentEvent

// ── 发出的命令（发给各有界上下文）───────────────────────────────────────────

sealed trait FulfillmentCommand
object FulfillmentCommand:
  case class RequestInventoryReservation(orderId: String, sku: String, quantity: Int)
      extends FulfillmentCommand
  case class RequestShipment(orderId: String, warehouseId: String) extends FulfillmentCommand
  case class IssueRefund(orderId: String, reason: String)          extends FulfillmentCommand
  case class NotifyCustomer(orderId: String, message: String)      extends FulfillmentCommand

// ── 进程管理器状态 ────────────────────────────────────────────────────────────

enum FulfillmentStatus:
  case AwaitingPayment        // 等待支付
  case AwaitingInventory      // 等待库存确认
  case AwaitingShipment       // 等待发货
  case InTransit              // 运输中
  case Delivered              // 已送达
  case Cancelled              // 已取消
  case Compensating           // 补偿中

case class FulfillmentState(
    processId:   String,
    orderId:     String,
    sku:         String,
    quantity:    Int,
    warehouseId: Option[String],
    trackingNo:  Option[String],
    status:      FulfillmentStatus
)

object FulfillmentState:
  val empty: FulfillmentState =
    FulfillmentState("", "", "", 0, None, None, FulfillmentStatus.AwaitingPayment)

  def applyEvent(state: FulfillmentState, event: FulfillmentEvent): FulfillmentState =
    event match
      case FulfillmentEvent.OrderConfirmed(orderId, sku, qty) =>
        state.copy(orderId = orderId, sku = sku, quantity = qty,
                   status = FulfillmentStatus.AwaitingPayment)
      case FulfillmentEvent.PaymentCompleted(_, _, _) =>
        state.copy(status = FulfillmentStatus.AwaitingInventory)
      case FulfillmentEvent.InventoryReserved(_, wh) =>
        state.copy(warehouseId = Some(wh), status = FulfillmentStatus.AwaitingShipment)
      case FulfillmentEvent.ShipmentDispatched(_, trackingNo) =>
        state.copy(trackingNo = Some(trackingNo), status = FulfillmentStatus.InTransit)
      case FulfillmentEvent.ShipmentDelivered(_) =>
        state.copy(status = FulfillmentStatus.Delivered)
      case FulfillmentEvent.PaymentFailed(_, _) =>
        state.copy(status = FulfillmentStatus.Cancelled)
      case FulfillmentEvent.InventoryUnavailable(_, _) =>
        state.copy(status = FulfillmentStatus.Compensating)

  def fromEvents(events: List[FulfillmentEvent]): FulfillmentState =
    events.foldLeft(empty)(applyEvent)

// ── 进程管理器：收到事件 → 推进状态 → 发出命令 ──────────────────────────────

object FulfillmentProcessManager:

  /** 核心逻辑：当前状态 + 新事件 → 下一步要发出的命令（可以是空）
    * 纯函数，无副作用
    */
  def nextCommands(
      state: FulfillmentState,
      event: FulfillmentEvent
  ): List[FulfillmentCommand] =
    event match
      case FulfillmentEvent.PaymentCompleted(orderId, _, _) =>
        // 支付完成 → 通知库存系统预留库存
        List(FulfillmentCommand.RequestInventoryReservation(orderId, state.sku, state.quantity))

      case FulfillmentEvent.InventoryReserved(orderId, warehouseId) =>
        // 库存预留成功 → 通知物流发货
        List(FulfillmentCommand.RequestShipment(orderId, warehouseId))

      case FulfillmentEvent.ShipmentDelivered(orderId) =>
        // 送达 → 通知客户
        List(FulfillmentCommand.NotifyCustomer(orderId, "您的订单已送达，感谢购买！"))

      case FulfillmentEvent.InventoryUnavailable(orderId, reason) =>
        // 库存不足 → 发起退款 + 通知客户
        List(
          FulfillmentCommand.IssueRefund(orderId, s"库存不足: $reason"),
          FulfillmentCommand.NotifyCustomer(orderId, s"很遗憾，库存不足已为您退款: $reason")
        )

      case FulfillmentEvent.PaymentFailed(orderId, reason) =>
        // 支付失败 → 通知客户
        List(FulfillmentCommand.NotifyCustomer(orderId, s"支付失败: $reason"))

      case _ => Nil   // 其他事件不触发新命令

// ── 进程管理器实例（进程内内存状态）────────────────────────────────────────

class FulfillmentProcess(
    processId: String,
    events:    Ref[IO, List[FulfillmentEvent]],
    outbox:    Ref[IO, List[FulfillmentCommand]]
):

  def currentState: IO[FulfillmentState] =
    events.get.map(FulfillmentState.fromEvents(_).copy(processId = processId))

  def handle(event: FulfillmentEvent): IO[List[FulfillmentCommand]] =
    for
      state    <- currentState
      commands  = FulfillmentProcessManager.nextCommands(state, event)
      _        <- events.update(_ :+ event)
      _        <- outbox.update(_ ++ commands)
    yield commands

  def pendingCommands: IO[List[FulfillmentCommand]] = outbox.get
  def eventLog:        IO[List[FulfillmentEvent]]   = events.get

// ── 演示 ──────────────────────────────────────────────────────────────────────

object CatsEffectProcessManagerDemo extends IOApp.Simple:

  def run: IO[Unit] =
    for
      events <- Ref.of[IO, List[FulfillmentEvent]](List.empty)
      outbox <- Ref.of[IO, List[FulfillmentCommand]](List.empty)
      process = FulfillmentProcess("proc-001", events, outbox)

      _ <- IO.println("=== 进程管理器：跨有界上下文履约流程协调 ===\n")

      // 步骤 1：订单确认（来自 Order 上下文）
      c1 <- process.handle(FulfillmentEvent.OrderConfirmed("o-1", "BTC-101", 2))
      s1 <- process.currentState
      _  <- IO.println(s"[OrderConfirmed] 状态: ${s1.status}, 触发命令: $c1")

      // 步骤 2：支付完成（来自 Payment 上下文）
      c2 <- process.handle(FulfillmentEvent.PaymentCompleted("o-1", "pay-001", BigDecimal(300)))
      s2 <- process.currentState
      _  <- IO.println(s"[PaymentCompleted] 状态: ${s2.status}, 触发命令: ${c2.map(_.getClass.getSimpleName)}")

      // 步骤 3：库存确认（来自 Inventory 上下文）
      c3 <- process.handle(FulfillmentEvent.InventoryReserved("o-1", "WH-SZ"))
      s3 <- process.currentState
      _  <- IO.println(s"[InventoryReserved] 状态: ${s3.status}, 触发命令: ${c3.map(_.getClass.getSimpleName)}")

      // 步骤 4：物流取件（来自 Logistics 上下文）
      c4 <- process.handle(FulfillmentEvent.ShipmentDispatched("o-1", "SF-9999"))
      s4 <- process.currentState
      _  <- IO.println(s"[ShipmentDispatched] 状态: ${s4.status}, 触发命令: $c4")

      // 步骤 5：物流送达
      c5 <- process.handle(FulfillmentEvent.ShipmentDelivered("o-1"))
      s5 <- process.currentState
      _  <- IO.println(s"[ShipmentDelivered] 状态: ${s5.status}, 触发命令: ${c5.map(_.getClass.getSimpleName)}")

      cmds <- process.pendingCommands
      log  <- process.eventLog

      _ <- IO.println(s"\n── 命令 Outbox（共 ${cmds.length} 条命令）────────────")
      _ <- cmds.traverse_(c => IO.println(s"  $c"))

      _ <- IO.println(s"\n── 事件日志（共 ${log.length} 条事件）──────────────────")
      _ <- log.zipWithIndex.traverse_ { case (e, i) =>
             IO.println(s"  [${i+1}] ${e.getClass.getSimpleName}")
           }

      _ <- IO.println(s"\n── 模拟补偿路径：库存不足 ──────────────────────────")
      events2 <- Ref.of[IO, List[FulfillmentEvent]](List.empty)
      outbox2 <- Ref.of[IO, List[FulfillmentCommand]](List.empty)
      process2 = FulfillmentProcess("proc-002", events2, outbox2)
      _ <- process2.handle(FulfillmentEvent.OrderConfirmed("o-2", "RARE-SKU", 100))
      _ <- process2.handle(FulfillmentEvent.PaymentCompleted("o-2", "pay-002", BigDecimal(9999)))
      cc <- process2.handle(FulfillmentEvent.InventoryUnavailable("o-2", "全部售罄"))
      s_  <- process2.currentState
      _  <- IO.println(s"[InventoryUnavailable] 状态: ${s_.status}, 触发命令: ${cc.map(_.getClass.getSimpleName)}")

      _ <- IO.println("""
|关键点：
|  1. 进程管理器监听多个有界上下文的事件，协调跨上下文工作流
|  2. 自身也是事件溯源：状态从收到的事件序列 fold 重建
|  3. nextCommands 是纯函数：当前状态 + 事件 → 发出的命令列表
|  4. 命令存入 Outbox，由后台发布者独立执行（与 Outbox 模式配合）
|  5. 与 Saga 的区别：进程管理器可以跨越多个有界上下文和多个长步骤""".stripMargin)
    yield ()
