// 132_FS2CrossContextEventBus.scala
// 有界上下文地图集成 第二步：fs2 跨上下文事件总线
//
// 核心思想：
//   在真实系统里，有界上下文之间的事件传递需要一个可靠的事件总线：
//     1. 发布者发布集成事件（不关心谁订阅）
//     2. 订阅者按事件类型独立处理（可以有多个订阅者）
//     3. 事件扇出：同一个事件可以触发多个下游上下文
//     4. 背压：下游处理慢时不会无限积压
//
//   用 fs2 Topic 实现跨上下文事件总线

//> using scala "3.3.3"
//> using dep "org.typelevel::cats-effect:3.5.4"
//> using dep "co.fs2::fs2-core:3.10.2"

import cats.effect.{IO, IOApp, Ref}
import cats.syntax.all._
import fs2.Stream
import fs2.concurrent.Topic

// ── 集成事件 ──────────────────────────────────────────────────────────────────

sealed trait IntegrationEvent:
  def orderId: String
  def source:  String

object IntegrationEvent:
  case class OrderPlaced(orderId: String, sku: String, qty: Int, price: BigDecimal)
      extends IntegrationEvent:
    val source = "OrderContext"

  case class PaymentCompleted(orderId: String, paymentId: String, amount: BigDecimal)
      extends IntegrationEvent:
    val source = "PaymentContext"

  case class PaymentFailed(orderId: String, reason: String)
      extends IntegrationEvent:
    val source = "PaymentContext"

  case class InventoryReserved(orderId: String, warehouseId: String)
      extends IntegrationEvent:
    val source = "InventoryContext"

  case class ShipmentDelivered(orderId: String, trackingNo: String)
      extends IntegrationEvent:
    val source = "LogisticsContext"

// ── 有界上下文处理器 ──────────────────────────────────────────────────────────

/** 上下文处理器：订阅感兴趣的事件，发布新事件 */
trait ContextHandler:
  def name: String
  def handles(event: IntegrationEvent): Boolean
  def process(event: IntegrationEvent): IO[Option[IntegrationEvent]]

/** Payment Context：监听 OrderPlaced → 发布 PaymentCompleted/Failed */
class PaymentHandler(rejectOrders: Set[String]) extends ContextHandler:
  val name = "PaymentContext"
  def handles(event: IntegrationEvent): Boolean = event.isInstanceOf[IntegrationEvent.OrderPlaced]
  def process(event: IntegrationEvent): IO[Option[IntegrationEvent]] =
    event match
      case e: IntegrationEvent.OrderPlaced =>
        val paymentId = s"pay-${e.orderId}"
        if rejectOrders.contains(e.orderId) then
          IO.pure(Some(IntegrationEvent.PaymentFailed(e.orderId, "insufficient-funds")))
        else
          IO.pure(Some(IntegrationEvent.PaymentCompleted(e.orderId, paymentId, e.price * e.qty)))
      case _ => IO.pure(None)

/** Inventory Context：监听 PaymentCompleted → 发布 InventoryReserved */
class InventoryHandler(unavailable: Set[String]) extends ContextHandler:
  val name = "InventoryContext"
  def handles(event: IntegrationEvent): Boolean =
    event.isInstanceOf[IntegrationEvent.PaymentCompleted]
  def process(event: IntegrationEvent): IO[Option[IntegrationEvent]] =
    event match
      case e: IntegrationEvent.PaymentCompleted =>
        IO.pure(Some(IntegrationEvent.InventoryReserved(e.orderId, "WH-SZ")))
      case _ => IO.pure(None)

/** Notification Context：监听所有终态事件，记录通知日志 */
class NotificationHandler(log: Ref[IO, List[String]]) extends ContextHandler:
  val name = "NotificationContext"
  def handles(event: IntegrationEvent): Boolean = event match
    case _: IntegrationEvent.PaymentFailed    => true
    case _: IntegrationEvent.ShipmentDelivered => true
    case _ => false
  def process(event: IntegrationEvent): IO[Option[IntegrationEvent]] =
    val msg = event match
      case e: IntegrationEvent.PaymentFailed    => s"通知: 订单 ${e.orderId} 支付失败"
      case e: IntegrationEvent.ShipmentDelivered => s"通知: 订单 ${e.orderId} 已送达 ${e.trackingNo}"
      case _ => ""
    log.update(_ :+ msg) *> IO.pure(None)

// ── 事件总线 ──────────────────────────────────────────────────────────────────

class CrossContextEventBus(
    topic:    Topic[IO, Option[IntegrationEvent]],
    handlers: List[ContextHandler],
    busLog:   Ref[IO, List[String]]
):

  /** 发布事件到总线 */
  def publish(event: IntegrationEvent): IO[Unit] =
    busLog.update(_ :+ s"  [BUS→] ${event.source}: ${event.getClass.getSimpleName}(${event.orderId})") *>
      topic.publish1(Some(event)).void

  /** 运行总线：订阅所有事件，路由给对应处理器，把新事件反发回总线 */
  def runBus: IO[Unit] =
    topic.subscribeUnbounded
      .unNoneTerminate
      .evalMap { event =>
        handlers
          .filter(_.handles(event))
          .traverse(h =>
            h.process(event).flatMap {
              case Some(newEvent) => publish(newEvent)
              case None           => IO.unit
            }
          )
          .void
      }
      .compile.drain

  def shutdown: IO[Unit] = topic.publish1(None).void

// ── 演示 ──────────────────────────────────────────────────────────────────────

object FS2CrossContextEventBusDemo extends IOApp.Simple:

  def run: IO[Unit] =
    for
      topic   <- Topic[IO, Option[IntegrationEvent]]
      busLog  <- Ref.of[IO, List[String]](Nil)
      notifLog <- Ref.of[IO, List[String]](Nil)

      handlers = List(
        PaymentHandler(rejectOrders = Set("o-2")),
        InventoryHandler(unavailable = Set.empty),
        NotificationHandler(notifLog)
      )
      bus = CrossContextEventBus(topic, handlers, busLog)

      _ <- IO.println("=== fs2 跨上下文事件总线：事件扇出与多上下文协作 ===\n")

      // 启动总线（后台）
      busFiber <- bus.runBus.start

      // 发布集成事件
      _ <- bus.publish(IntegrationEvent.OrderPlaced("o-1", "SKU-A", 2, BigDecimal(150)))
      _ <- bus.publish(IntegrationEvent.OrderPlaced("o-2", "SKU-B", 1, BigDecimal(100)))
      _ <- bus.publish(IntegrationEvent.ShipmentDelivered("o-1", "SF-9999"))

      // 给总线处理时间
      _ <- IO.sleep(scala.concurrent.duration.FiniteDuration(200, "millis"))
      _ <- bus.shutdown
      _ <- busFiber.join

      log   <- busLog.get
      notif <- notifLog.get

      _ <- IO.println("── 事件总线日志 ─────────────────────────────────────")
      _ <- log.traverse_(IO.println)
      _ <- IO.println("\n── 通知日志 ─────────────────────────────────────────")
      _ <- notif.traverse_(IO.println)

      _ <- IO.println("""
|关键点：
|  1. 事件总线用 fs2 Topic 实现广播，每个上下文独立订阅
|  2. 处理器只感知集成事件，不感知其他上下文内部
|  3. 新事件反发回总线实现链式触发（OrderPlaced→PaymentCompleted→InventoryReserved）
|  4. 通知上下文可以订阅任意终态事件，完全解耦""".stripMargin)
    yield ()
