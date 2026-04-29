// 116_CatsEffectEventSourcedAggregate.scala
// 事件溯源（Event Sourcing）第一步：cats-effect 事件溯源聚合根
//
// 核心思想：
//   传统 CRUD：当前状态直接存储，历史丢失。
//   事件溯源：只存储"发生了什么"（Event），
//              当前状态 = fold(初始状态, 事件序列)
//
//   好处：
//     - 完整历史审计日志（天然可溯源）
//     - 时间旅行调试（重放到任意时间点）
//     - 读模型可以从任意 offset 重建
//     - 与 CQRS 天然配合：写侧只追加事件，读侧按需投影
//
// 本 Demo 演示：
//   1. OrderAggregate：只保存事件序列，不保存当前状态字段
//   2. apply(events): OrderState — 从事件列表重建当前状态
//   3. handle(command): Either[Error, Event] — 命令产生事件，不直接修改状态
//   4. 验证重放同一事件序列总能得到相同状态（确定性）

//> using scala "3.3.3"
//> using dep "org.typelevel::cats-effect:3.5.4"

import cats.effect.{IO, IOApp, Ref}
import cats.syntax.all._

// ── 事件（只追加，永不修改）─────────────────────────────────────────────────

sealed trait OrderEvent
object OrderEvent:
  case class OrderPlaced(orderId: String, sku: String, quantity: Int, price: BigDecimal)
      extends OrderEvent
  case class PaymentReceived(orderId: String, amount: BigDecimal, paymentId: String)
      extends OrderEvent
  case class OrderShipped(orderId: String, trackingNo: String)   extends OrderEvent
  case class OrderCancelled(orderId: String, reason: String)     extends OrderEvent
  case class RefundIssued(orderId: String, amount: BigDecimal)   extends OrderEvent

// ── 聚合根当前状态（从事件 fold 出来，不直接存储）──────────────────────────

enum OrderStatus:
  case Pending, Paid, Shipped, Cancelled, Refunded

case class OrderState(
    orderId: String,
    sku: String,
    quantity: Int,
    price: BigDecimal,
    paid: BigDecimal,
    status: OrderStatus,
    trackingNo: Option[String]
)

object OrderState:
  val empty: OrderState =
    OrderState("", "", 0, BigDecimal(0), BigDecimal(0), OrderStatus.Pending, None)

  /** 将一个事件应用到当前状态，纯函数 */
  def applyEvent(state: OrderState, event: OrderEvent): OrderState =
    event match
      case OrderEvent.OrderPlaced(id, sku, qty, price) =>
        state.copy(orderId = id, sku = sku, quantity = qty, price = price,
                   status = OrderStatus.Pending)
      case OrderEvent.PaymentReceived(_, amount, _) =>
        state.copy(paid = state.paid + amount, status = OrderStatus.Paid)
      case OrderEvent.OrderShipped(_, trackingNo) =>
        state.copy(trackingNo = Some(trackingNo), status = OrderStatus.Shipped)
      case OrderEvent.OrderCancelled(_, _) =>
        state.copy(status = OrderStatus.Cancelled)
      case OrderEvent.RefundIssued(_, _) =>
        state.copy(status = OrderStatus.Refunded)

  /** 从事件序列重建当前状态（事件溯源核心：fold over events）*/
  def fromEvents(events: List[OrderEvent]): OrderState =
    events.foldLeft(empty)(applyEvent)

// ── 命令（产生事件，不直接修改状态）──────────────────────────────────────────

sealed trait OrderCommand
object OrderCommand:
  case class PlaceOrder(orderId: String, sku: String, quantity: Int, price: BigDecimal)
      extends OrderCommand
  case class ReceivePayment(paymentId: String, amount: BigDecimal) extends OrderCommand
  case class ShipOrder(trackingNo: String)                         extends OrderCommand
  case class CancelOrder(reason: String)                           extends OrderCommand
  case class IssueRefund(amount: BigDecimal)                       extends OrderCommand

sealed trait OrderCommandError
object OrderCommandError:
  case class InvalidTransition(from: OrderStatus, cmd: String) extends OrderCommandError
  case class InsufficientPayment(needed: BigDecimal, got: BigDecimal)
      extends OrderCommandError

// ── 聚合根命令处理（纯函数：当前状态 + 命令 → 新事件列表）──────────────────

object OrderAggregate:

  /** 处理命令，返回要追加的新事件（或错误）。
    * 关键点：不修改任何状态，只产生事件。
    */
  def handle(
      state: OrderState,
      cmd: OrderCommand
  ): Either[OrderCommandError, OrderEvent] =
    cmd match

      case OrderCommand.PlaceOrder(id, sku, qty, price) =>
        Right(OrderEvent.OrderPlaced(id, sku, qty, price))

      case OrderCommand.ReceivePayment(paymentId, amount) =>
        state.status match
          case OrderStatus.Pending =>
            if amount >= state.price then
              Right(OrderEvent.PaymentReceived(state.orderId, amount, paymentId))
            else
              Left(OrderCommandError.InsufficientPayment(state.price, amount))
          case s =>
            Left(OrderCommandError.InvalidTransition(s, "ReceivePayment"))

      case OrderCommand.ShipOrder(trackingNo) =>
        state.status match
          case OrderStatus.Paid => Right(OrderEvent.OrderShipped(state.orderId, trackingNo))
          case s                => Left(OrderCommandError.InvalidTransition(s, "ShipOrder"))

      case OrderCommand.CancelOrder(reason) =>
        state.status match
          case OrderStatus.Pending | OrderStatus.Paid =>
            Right(OrderEvent.OrderCancelled(state.orderId, reason))
          case s =>
            Left(OrderCommandError.InvalidTransition(s, "CancelOrder"))

      case OrderCommand.IssueRefund(amount) =>
        state.status match
          case OrderStatus.Cancelled =>
            Right(OrderEvent.RefundIssued(state.orderId, amount))
          case s =>
            Left(OrderCommandError.InvalidTransition(s, "IssueRefund"))

// ── 进程内事件存储（用 Ref 模拟）─────────────────────────────────────────────

class InMemoryEventStore(store: Ref[IO, Map[String, List[OrderEvent]]]):

  def append(orderId: String, event: OrderEvent): IO[Unit] =
    store.update { m =>
      m.updated(orderId, m.getOrElse(orderId, Nil) :+ event)
    }

  def load(orderId: String): IO[List[OrderEvent]] =
    store.get.map(_.getOrElse(orderId, Nil))

  /** 从事件流重建当前状态 */
  def rehydrate(orderId: String): IO[OrderState] =
    load(orderId).map(OrderState.fromEvents)

// ── 演示 ──────────────────────────────────────────────────────────────────────

object CatsEffectEventSourcedAggregateDemo extends IOApp.Simple:

  def run: IO[Unit] =
    for
      storeRef <- Ref.of[IO, Map[String, List[OrderEvent]]](Map.empty)
      store     = InMemoryEventStore(storeRef)

      _ <- IO.println("=== 事件溯源聚合根：状态从事件 fold 重建 ===\n")

      orderId = "order-001"

      // 步骤 1：下单命令 → 产生 OrderPlaced 事件
      s0    = OrderState.empty
      e1   <- IO.fromEither(
                OrderAggregate.handle(s0, OrderCommand.PlaceOrder(orderId, "BTC-101", 2, BigDecimal(200)))
                  .leftMap(e => new RuntimeException(e.toString))
              )
      _    <- store.append(orderId, e1)
      s1   <- store.rehydrate(orderId)
      _    <- IO.println(s"[✓] 下单后状态: status=${s1.status}, price=${s1.price}")

      // 步骤 2：支付命令（金额不足 → 失败）
      r2    = OrderAggregate.handle(s1, OrderCommand.ReceivePayment("pay-x", BigDecimal(100)))
      _    <- IO.println(s"[✗] 支付不足: $r2")

      // 步骤 3：支付命令（正确金额 → 成功）
      e3   <- IO.fromEither(
                OrderAggregate.handle(s1, OrderCommand.ReceivePayment("pay-001", BigDecimal(200)))
                  .leftMap(e => new RuntimeException(e.toString))
              )
      _    <- store.append(orderId, e3)
      s3   <- store.rehydrate(orderId)
      _    <- IO.println(s"[✓] 支付后状态: status=${s3.status}, paid=${s3.paid}")

      // 步骤 4：发货命令
      e4   <- IO.fromEither(
                OrderAggregate.handle(s3, OrderCommand.ShipOrder("TRACK-999"))
                  .leftMap(e => new RuntimeException(e.toString))
              )
      _    <- store.append(orderId, e4)
      s4   <- store.rehydrate(orderId)
      _    <- IO.println(s"[✓] 发货后状态: status=${s4.status}, trackingNo=${s4.trackingNo}")

      // 步骤 5：已发货状态下尝试取消 → 失败
      r5    = OrderAggregate.handle(s4, OrderCommand.CancelOrder("想反悔"))
      _    <- IO.println(s"[✗] 发货后取消: $r5")

      // 验证：重放同一事件序列得到相同状态（确定性）
      events <- store.load(orderId)
      replay  = OrderState.fromEvents(events)
      _      <- IO.println(s"\n[重放验证] 事件数=${events.length}, replay.status=${replay.status}, replay.trackingNo=${replay.trackingNo}")
      _       = assert(replay == s4, "重放结果必须与当前状态完全一致")
      _      <- IO.println("[✓] 重放验证通过：确定性满足")

      _ <- IO.println(s"\n事件日志:")
      _ <- events.zipWithIndex.traverse_ { case (e, i) =>
             IO.println(s"  [${i+1}] $e")
           }

      _ <- IO.println("""
|关键点：
|  1. 聚合根不存储当前状态，只存储事件序列
|  2. 状态 = fold(空状态, 事件列表)，纯函数，无副作用
|  3. 命令处理：当前状态 + 命令 → 新事件（或错误），不直接修改任何状态
|  4. 重放同一事件序列永远得到相同状态（确定性）
|  5. 这就是为什么读模型可以任意重建、时间旅行调试可以实现""".stripMargin)
    yield ()
