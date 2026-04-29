// 122_FS2ProcessManagerEventRouter.scala
// 进程管理器 第二步：fs2 事件路由到进程管理器
//
// 核心思想：
//   真实系统里，来自多个有界上下文的领域事件会混合流入。
//   事件路由器（Event Router）需要：
//     1. 按 aggregateId 路由到对应的进程管理器实例
//     2. 如果该 orderId 还没有进程管理器实例，自动创建
//     3. 把进程管理器发出的命令收集到统一的命令队列
//     4. 统计每个进程的状态分布
//
//   用 fs2 Stream 来处理事件流：
//     - evalMap 把每个事件路由给对应进程
//     - groupBy orderId 确保同一聚合根的事件顺序处理

//> using scala "3.3.3"
//> using dep "org.typelevel::cats-effect:3.5.4"
//> using dep "co.fs2::fs2-core:3.10.2"

import cats.effect.{IO, IOApp, Ref}
import cats.syntax.all._
import fs2.Stream

// ── 领域事件（内联，与 121 保持一致）─────────────────────────────────────────

sealed trait FulfillmentEvent:
  def orderId: String

object FulfillmentEvent:
  case class OrderConfirmed(orderId: String, sku: String, quantity: Int) extends FulfillmentEvent
  case class PaymentCompleted(orderId: String, paymentId: String)        extends FulfillmentEvent
  case class InventoryReserved(orderId: String, warehouseId: String)     extends FulfillmentEvent
  case class ShipmentDelivered(orderId: String)                          extends FulfillmentEvent
  case class PaymentFailed(orderId: String, reason: String)             extends FulfillmentEvent
  case class InventoryUnavailable(orderId: String, reason: String)      extends FulfillmentEvent

sealed trait FulfillmentCommand:
  def orderId: String

object FulfillmentCommand:
  case class RequestInventoryReservation(orderId: String, sku: String, qty: Int)
      extends FulfillmentCommand
  case class RequestShipment(orderId: String, warehouseId: String) extends FulfillmentCommand
  case class IssueRefund(orderId: String, reason: String)          extends FulfillmentCommand
  case class NotifyCustomer(orderId: String, message: String)      extends FulfillmentCommand

// ── 进程状态 ──────────────────────────────────────────────────────────────────

enum ProcessStatus:
  case AwaitingPayment, AwaitingInventory, AwaitingShipment, InTransit, Delivered, Cancelled, Compensating

case class ProcessState(
    orderId:    String,
    sku:        String,
    qty:        Int,
    warehouseId: Option[String],
    status:     ProcessStatus
)

object ProcessState:
  def empty(orderId: String): ProcessState =
    ProcessState(orderId, "", 0, None, ProcessStatus.AwaitingPayment)

  def apply(state: ProcessState, event: FulfillmentEvent): ProcessState =
    event match
      case FulfillmentEvent.OrderConfirmed(id, sku, qty) =>
        state.copy(sku = sku, qty = qty, status = ProcessStatus.AwaitingPayment)
      case FulfillmentEvent.PaymentCompleted(_, _) =>
        state.copy(status = ProcessStatus.AwaitingInventory)
      case FulfillmentEvent.InventoryReserved(_, wh) =>
        state.copy(warehouseId = Some(wh), status = ProcessStatus.AwaitingShipment)
      case FulfillmentEvent.ShipmentDelivered(_) =>
        state.copy(status = ProcessStatus.Delivered)
      case FulfillmentEvent.PaymentFailed(_, _) =>
        state.copy(status = ProcessStatus.Cancelled)
      case FulfillmentEvent.InventoryUnavailable(_, _) =>
        state.copy(status = ProcessStatus.Compensating)

// ── 进程管理器注册表 ──────────────────────────────────────────────────────────

case class ProcessEntry(
    state:    ProcessState,
    events:   List[FulfillmentEvent],
    commands: List[FulfillmentCommand]
)

class ProcessManagerRegistry(
    registry: Ref[IO, Map[String, ProcessEntry]]
):

  private def nextCommands(state: ProcessState, event: FulfillmentEvent): List[FulfillmentCommand] =
    event match
      case FulfillmentEvent.PaymentCompleted(id, _) =>
        List(FulfillmentCommand.RequestInventoryReservation(id, state.sku, state.qty))
      case FulfillmentEvent.InventoryReserved(id, wh) =>
        List(FulfillmentCommand.RequestShipment(id, wh))
      case FulfillmentEvent.ShipmentDelivered(id) =>
        List(FulfillmentCommand.NotifyCustomer(id, "已送达"))
      case FulfillmentEvent.InventoryUnavailable(id, r) =>
        List(FulfillmentCommand.IssueRefund(id, r),
             FulfillmentCommand.NotifyCustomer(id, s"退款: $r"))
      case _ => Nil

  def route(event: FulfillmentEvent): IO[List[FulfillmentCommand]] =
    registry.modify { m =>
      val orderId = event.orderId
      val entry   = m.getOrElse(orderId, ProcessEntry(ProcessState.empty(orderId), Nil, Nil))
      val newState = ProcessState(entry.state, event)
      val cmds    = nextCommands(entry.state, event)
      val updated = entry.copy(
        state    = newState,
        events   = entry.events :+ event,
        commands = entry.commands ++ cmds
      )
      m.updated(orderId, updated) -> cmds
    }

  def snapshot: IO[Map[String, ProcessEntry]] = registry.get

// ── 事件路由流 ────────────────────────────────────────────────────────────────

object EventRouterStream:

  def run(
      events:   List[FulfillmentEvent],
      registry: ProcessManagerRegistry,
      cmdLog:   Ref[IO, List[FulfillmentCommand]]
  ): IO[Unit] =
    Stream
      .emits(events)
      .evalMap { event =>
        registry.route(event).flatMap { cmds =>
          if cmds.nonEmpty then
            cmdLog.update(_ ++ cmds) *>
              IO.println(s"  [${event.orderId}] ${event.getClass.getSimpleName} → 触发: ${cmds.map(_.getClass.getSimpleName).mkString(", ")}")
          else
            IO.println(s"  [${event.orderId}] ${event.getClass.getSimpleName} → 无新命令")
        }
      }
      .compile
      .drain

// ── 演示 ──────────────────────────────────────────────────────────────────────

object FS2ProcessManagerEventRouterDemo extends IOApp.Simple:

  // 模拟来自多个有界上下文、多个订单的混合事件流
  val events: List[FulfillmentEvent] = List(
    FulfillmentEvent.OrderConfirmed("o-1", "SKU-A", 2),
    FulfillmentEvent.OrderConfirmed("o-2", "SKU-B", 1),
    FulfillmentEvent.PaymentCompleted("o-1", "pay-001"),
    FulfillmentEvent.PaymentFailed("o-2", "余额不足"),
    FulfillmentEvent.InventoryReserved("o-1", "WH-SZ"),
    FulfillmentEvent.OrderConfirmed("o-3", "RARE", 50),
    FulfillmentEvent.PaymentCompleted("o-3", "pay-003"),
    FulfillmentEvent.InventoryUnavailable("o-3", "全部售罄"),
    FulfillmentEvent.ShipmentDelivered("o-1"),
  )

  def run: IO[Unit] =
    for
      regRef  <- Ref.of[IO, Map[String, ProcessEntry]](Map.empty)
      registry = ProcessManagerRegistry(regRef)
      cmdLog  <- Ref.of[IO, List[FulfillmentCommand]](List.empty)

      _ <- IO.println(s"=== fs2 事件路由：${events.length} 个混合事件 → 3 个进程管理器 ===\n")
      _ <- EventRouterStream.run(events, registry, cmdLog)

      snapshot <- registry.snapshot
      allCmds  <- cmdLog.get

      _ <- IO.println(s"\n── 进程状态分布 ──────────────────────────────────")
      _ <- snapshot.values.toList.sortBy(_.state.orderId).traverse_ { entry =>
             IO.println(s"  ${entry.state.orderId}: ${entry.state.status} (${entry.events.length} 事件, ${entry.commands.length} 命令)")
           }

      _ <- IO.println(s"\n── 全局命令队列（共 ${allCmds.length} 条）─────────────────")
      _ <- allCmds.traverse_(c => IO.println(s"  [${c.orderId}] ${c.getClass.getSimpleName}"))

      _ <- IO.println("""
|关键点：
|  1. 混合事件流按 orderId 自动路由到对应进程管理器实例
|  2. 不存在的进程 ID 自动初始化，支持动态创建
|  3. 每个进程管理器独立维护自己的事件日志和命令队列
|  4. 全局命令队列收集所有进程发出的命令，等待被分发执行""".stripMargin)
    yield ()
