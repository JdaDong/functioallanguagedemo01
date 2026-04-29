// 131_CatsEffectContextMapAssembly.scala
// 有界上下文地图集成 第一步：cats-effect 上下文地图装配
//
// 核心思想：
//   有界上下文地图（Context Map）描述多个有界上下文之间的关系：
//     - Order Context    → 上游（U），创建订单
//     - Payment Context  → 下游（D），接收支付请求
//     - Inventory Context → 下游（D），处理库存预留
//     - Logistics Context → 下游（D），处理发货
//     - ACL              → 把外部模型翻译成本地语言
//     - Process Manager  → 跨上下文工作流协调
//
//   本 Demo 演示：
//     1. 把所有上下文组件装配成一个内存运行的系统
//     2. 模拟完整的订单履约流程（下单→支付→库存→物流→送达）
//     3. 展示各上下文之间如何通过事件总线 + ACL 解耦

//> using scala "3.3.3"
//> using dep "org.typelevel::cats-effect:3.5.4"

import cats.effect.{IO, IOApp, Ref}
import cats.syntax.all._

// ── 四个有界上下文的领域模型 ─────────────────────────────────────────────────

// Order Context
case class Order(orderId: String, sku: String, quantity: Int, price: BigDecimal, status: String)

// Payment Context
case class Payment(paymentId: String, orderId: String, amount: BigDecimal, status: String)

// Inventory Context
case class Reservation(reservationId: String, orderId: String, sku: String, quantity: Int, warehouseId: String)

// Logistics Context
case class Shipment(shipmentId: String, orderId: String, warehouseId: String, trackingNo: String, status: String)

// ── 跨上下文集成事件（通过事件总线传递）────────────────────────────────────

sealed trait IntegrationEvent:
  def orderId: String

object IntegrationEvent:
  case class OrderCreated(orderId: String, sku: String, qty: Int, price: BigDecimal) extends IntegrationEvent
  case class PaymentAuthorized(orderId: String, paymentId: String, amount: BigDecimal) extends IntegrationEvent
  case class PaymentDeclined(orderId: String, paymentId: String, reason: String) extends IntegrationEvent
  case class InventoryReserved(orderId: String, reservationId: String, warehouseId: String) extends IntegrationEvent
  case class InventoryReleased(orderId: String, reason: String) extends IntegrationEvent
  case class ShipmentCreated(orderId: String, shipmentId: String, trackingNo: String) extends IntegrationEvent
  case class ShipmentDelivered(orderId: String, shipmentId: String) extends IntegrationEvent

// ── 有界上下文服务 ────────────────────────────────────────────────────────────

/** Order Context：管理订单生命周期 */
class OrderContext(store: Ref[IO, Map[String, Order]]):
  def createOrder(orderId: String, sku: String, qty: Int, price: BigDecimal): IO[IntegrationEvent] =
    store.update(_.updated(orderId, Order(orderId, sku, qty, price, "awaiting-payment"))) *>
      IO.pure(IntegrationEvent.OrderCreated(orderId, sku, qty, price))

  def markPaid(orderId: String): IO[Unit] =
    store.update(m => m.get(orderId).fold(m)(o => m.updated(orderId, o.copy(status = "paid"))))

  def markShipped(orderId: String): IO[Unit] =
    store.update(m => m.get(orderId).fold(m)(o => m.updated(orderId, o.copy(status = "shipped"))))

  def markDelivered(orderId: String): IO[Unit] =
    store.update(m => m.get(orderId).fold(m)(o => m.updated(orderId, o.copy(status = "delivered"))))

  def markCancelled(orderId: String): IO[Unit] =
    store.update(m => m.get(orderId).fold(m)(o => m.updated(orderId, o.copy(status = "cancelled"))))

  def get(orderId: String): IO[Option[Order]] = store.get.map(_.get(orderId))

/** Payment Context：处理支付授权 */
class PaymentContext(store: Ref[IO, Map[String, Payment]], rejectOrders: Set[String]):
  def authorize(orderId: String, amount: BigDecimal): IO[IntegrationEvent] =
    val paymentId = s"pay-${orderId}"
    if rejectOrders.contains(orderId) then
      store.update(_.updated(paymentId, Payment(paymentId, orderId, amount, "declined"))) *>
        IO.pure(IntegrationEvent.PaymentDeclined(orderId, paymentId, "insufficient-funds"))
    else
      store.update(_.updated(paymentId, Payment(paymentId, orderId, amount, "authorized"))) *>
        IO.pure(IntegrationEvent.PaymentAuthorized(orderId, paymentId, amount))

/** Inventory Context：处理库存预留 */
class InventoryContext(store: Ref[IO, Map[String, Reservation]], unavailable: Set[String]):
  def reserve(orderId: String, sku: String, qty: Int): IO[IntegrationEvent] =
    val reservationId = s"res-${orderId}"
    val warehouseId   = "WH-SZ"
    if unavailable.contains(sku) then
      IO.pure(IntegrationEvent.InventoryReleased(orderId, s"sku=$sku 库存不足"))
    else
      store.update(_.updated(reservationId,
        Reservation(reservationId, orderId, sku, qty, warehouseId))) *>
        IO.pure(IntegrationEvent.InventoryReserved(orderId, reservationId, warehouseId))

/** Logistics Context：处理发货 */
class LogisticsContext(store: Ref[IO, Map[String, Shipment]]):
  def createShipment(orderId: String, warehouseId: String): IO[IntegrationEvent] =
    val shipmentId = s"ship-${orderId}"
    val trackingNo = s"SF-${orderId.hashCode.abs % 10000}"
    store.update(_.updated(shipmentId,
      Shipment(shipmentId, orderId, warehouseId, trackingNo, "created"))) *>
      IO.pure(IntegrationEvent.ShipmentCreated(orderId, shipmentId, trackingNo))

  def markDelivered(orderId: String): IO[IntegrationEvent] =
    val shipmentId = s"ship-${orderId}"
    store.update(m => m.get(shipmentId).fold(m)(s =>
      m.updated(shipmentId, s.copy(status = "delivered")))) *>
      IO.pure(IntegrationEvent.ShipmentDelivered(orderId, shipmentId))

// ── 进程管理器（跨上下文协调）────────────────────────────────────────────────

class FulfillmentProcessManager(
    orderCtx:     OrderContext,
    paymentCtx:   PaymentContext,
    inventoryCtx: InventoryContext,
    logisticsCtx: LogisticsContext
):
  /** 处理集成事件，返回下一步触发的事件 */
  def handle(event: IntegrationEvent): IO[Option[IntegrationEvent]] =
    event match
      case IntegrationEvent.OrderCreated(orderId, sku, qty, price) =>
        // 下单后自动触发支付授权
        paymentCtx.authorize(orderId, price * qty).map(Some(_))

      case IntegrationEvent.PaymentAuthorized(orderId, _, _) =>
        // 支付成功 → 更新订单状态 + 触发库存预留
        orderCtx.get(orderId).flatMap {
          case Some(o) =>
            orderCtx.markPaid(orderId) *>
              inventoryCtx.reserve(orderId, o.sku, o.quantity).map(Some(_))
          case None => IO.pure(None)
        }

      case IntegrationEvent.PaymentDeclined(orderId, _, reason) =>
        // 支付失败 → 取消订单
        orderCtx.markCancelled(orderId) *> IO.pure(None)

      case IntegrationEvent.InventoryReserved(orderId, _, warehouseId) =>
        // 库存预留成功 → 触发发货
        logisticsCtx.createShipment(orderId, warehouseId).map(Some(_))

      case IntegrationEvent.InventoryReleased(orderId, reason) =>
        // 库存不足 → 取消订单（这里简化：实际要触发退款）
        orderCtx.markCancelled(orderId) *> IO.pure(None)

      case IntegrationEvent.ShipmentCreated(orderId, _, _) =>
        // 发货创建 → 更新订单为 shipped
        orderCtx.markShipped(orderId) *> IO.pure(None)

      case IntegrationEvent.ShipmentDelivered(orderId, _) =>
        // 送达 → 更新订单为 delivered
        orderCtx.markDelivered(orderId) *> IO.pure(None)

// ── 系统装配 ──────────────────────────────────────────────────────────────────

case class FulfillmentSystem(
    orderCtx:     OrderContext,
    processManager: FulfillmentProcessManager
):
  /** 运行完整履约流程直到无更多事件 */
  def runUntilComplete(initial: IntegrationEvent, traceLog: Ref[IO, List[String]]): IO[Unit] =
    def loop(ev: IntegrationEvent): IO[Unit] =
      traceLog.update(_ :+ s"  [${ev.orderId}] ${ev.getClass.getSimpleName}") *>
        processManager.handle(ev).flatMap {
          case Some(next) => loop(next)
          case None       => IO.unit
        }
    loop(initial)

object ContextMapSystemFactory:
  def create(
      rejectPaymentFor: Set[String] = Set.empty,
      unavailableSkus:  Set[String] = Set.empty
  ): IO[FulfillmentSystem] =
    for
      orderStore     <- Ref.of[IO, Map[String, Order]](Map.empty)
      paymentStore   <- Ref.of[IO, Map[String, Payment]](Map.empty)
      inventoryStore <- Ref.of[IO, Map[String, Reservation]](Map.empty)
      logisticsStore <- Ref.of[IO, Map[String, Shipment]](Map.empty)

      orderCtx      = OrderContext(orderStore)
      paymentCtx    = PaymentContext(paymentStore, rejectPaymentFor)
      inventoryCtx  = InventoryContext(inventoryStore, unavailableSkus)
      logisticsCtx  = LogisticsContext(logisticsStore)
      pm            = FulfillmentProcessManager(orderCtx, paymentCtx, inventoryCtx, logisticsCtx)
    yield FulfillmentSystem(orderCtx, pm)

// ── 演示 ──────────────────────────────────────────────────────────────────────

object CatsEffectContextMapAssemblyDemo extends IOApp.Simple:

  def run: IO[Unit] =
    for
      _ <- IO.println("=== 有界上下文地图装配：完整履约系统 ===\n")

      // 场景 1：正常履约
      _ <- IO.println("── 场景 1：正常履约 ─────────────────────────────")
      trace1 <- Ref.of[IO, List[String]](Nil)
      sys1   <- ContextMapSystemFactory.create()
      firstEvent1 <- sys1.orderCtx.createOrder("o-1", "BTC-101", 2, BigDecimal(150))
      _           <- sys1.runUntilComplete(firstEvent1, trace1)
      log1        <- trace1.get
      _           <- log1.traverse_(IO.println)
      order1      <- sys1.orderCtx.get("o-1")
      _           <- IO.println(s"  最终状态: ${order1.map(_.status)}")

      // 场景 2：支付失败
      _ <- IO.println("\n── 场景 2：支付失败 ─────────────────────────────")
      trace2 <- Ref.of[IO, List[String]](Nil)
      sys2   <- ContextMapSystemFactory.create(rejectPaymentFor = Set("o-2"))
      firstEvent2 <- sys2.orderCtx.createOrder("o-2", "ETH-202", 1, BigDecimal(100))
      _           <- sys2.runUntilComplete(firstEvent2, trace2)
      log2        <- trace2.get
      _           <- log2.traverse_(IO.println)
      order2      <- sys2.orderCtx.get("o-2")
      _           <- IO.println(s"  最终状态: ${order2.map(_.status)}")

      // 场景 3：库存不足
      _ <- IO.println("\n── 场景 3：库存不足 ─────────────────────────────")
      trace3 <- Ref.of[IO, List[String]](Nil)
      sys3   <- ContextMapSystemFactory.create(unavailableSkus = Set("RARE-SKU"))
      firstEvent3 <- sys3.orderCtx.createOrder("o-3", "RARE-SKU", 100, BigDecimal(9999))
      _           <- sys3.runUntilComplete(firstEvent3, trace3)
      log3        <- trace3.get
      _           <- log3.traverse_(IO.println)
      order3      <- sys3.orderCtx.get("o-3")
      _           <- IO.println(s"  最终状态: ${order3.map(_.status)}")

      _ <- IO.println("""
|关键点：
|  1. 四个有界上下文通过集成事件总线解耦，互相感知不到对方的内部模型
|  2. 进程管理器作为协调者，订阅事件并触发下游命令
|  3. 整套系统可以用 cats-effect 纯内存运行，不依赖任何外部基础设施
|  4. 每个有界上下文可以独立测试，只需模拟事件总线""".stripMargin)
    yield ()
