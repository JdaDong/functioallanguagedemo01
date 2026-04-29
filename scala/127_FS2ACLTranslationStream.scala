// 127_FS2ACLTranslationStream.scala
// 防腐层 第二步：fs2 上游事件翻译流
//
// 核心思想：
//   上游系统（支付网关、物流、库存）会持续产生事件流。
//   ACL 翻译流需要：
//     1. 按事件来源分流（支付 / 物流 / 库存）
//     2. 翻译成本地领域事件
//     3. 翻译失败的事件写入拒绝日志（Dead Letter）
//     4. 翻译成功的事件发布给本地领域处理器
//
//   用 fs2 Stream 自然表达这条翻译管道

//> using scala "3.3.3"
//> using dep "org.typelevel::cats-effect:3.5.4"
//> using dep "co.fs2::fs2-core:3.10.2"

import cats.effect.{IO, IOApp, Ref}
import cats.syntax.all._
import fs2.Stream

// ── 上游消息信封（来自消息队列）─────────────────────────────────────────────

sealed trait UpstreamMessage:
  def messageId: String
  def source:    String

object UpstreamMessage:
  case class PaymentMsg(messageId: String,
      txnId: String, merchantRef: String, status: String,
      amountCents: Long, currency: String) extends UpstreamMessage:
    val source = "PaymentGateway"

  case class LogisticsMsg(messageId: String,
      shipmentNo: String, refCode: String, eventCode: String,
      location: String) extends UpstreamMessage:
    val source = "Logistics"

  case class InventoryMsg(messageId: String,
      requestId: String, itemCode: String, available: Int,
      reserved: Int, warehouseCode: String) extends UpstreamMessage:
    val source = "Inventory"

// ── 本地领域事件 ──────────────────────────────────────────────────────────────

sealed trait DomainEvent:
  def orderId: String

object DomainEvent:
  case class PaymentReceived(orderId: String, paymentId: String, amount: BigDecimal) extends DomainEvent
  case class PaymentFailed(orderId: String, paymentId: String, reason: String)       extends DomainEvent
  case class ShipmentDelivered(orderId: String, trackingNo: String)                  extends DomainEvent
  case class ShipmentInTransit(orderId: String, trackingNo: String, loc: String)     extends DomainEvent
  case class StockUpdated(sku: String, available: Int, warehouseId: String) extends DomainEvent:
    val orderId = ""   // 库存事件不关联 orderId

case class RejectedMessage(messageId: String, source: String, reason: String)

// ── ACL 翻译函数（纯函数）────────────────────────────────────────────────────

def translateMessage(msg: UpstreamMessage): Either[String, DomainEvent] =
  msg match
    case m: UpstreamMessage.PaymentMsg =>
      m.status match
        case "SUCCESS" if m.amountCents > 0 && m.merchantRef.nonEmpty =>
          Right(DomainEvent.PaymentReceived(
            m.merchantRef, m.txnId, BigDecimal(m.amountCents) / 100))
        case "FAILED" =>
          Right(DomainEvent.PaymentFailed(m.merchantRef, m.txnId, "payment-declined"))
        case "SUCCESS" if m.merchantRef.isEmpty =>
          Left("missing merchantRef")
        case other =>
          Left(s"unknown payment status: $other")

    case m: UpstreamMessage.LogisticsMsg =>
      m.eventCode match
        case "DELIVERED"  => Right(DomainEvent.ShipmentDelivered(m.refCode, m.shipmentNo))
        case "IN_TRANSIT" => Right(DomainEvent.ShipmentInTransit(m.refCode, m.shipmentNo, m.location))
        case other        => Left(s"unknown logistics event: $other")

    case m: UpstreamMessage.InventoryMsg =>
      if m.itemCode.startsWith("SKU-") then
        Right(DomainEvent.StockUpdated(
          m.itemCode.stripPrefix("SKU-"), m.available, m.warehouseCode))
      else
        Left(s"invalid itemCode format: ${m.itemCode}")

// ── ACL 翻译流 ────────────────────────────────────────────────────────────────

object ACLTranslationStream:

  def run(
      messages: List[UpstreamMessage],
      accepted: Ref[IO, List[DomainEvent]],
      rejected: Ref[IO, List[RejectedMessage]]
  ): IO[(Int, Int)] =
    Stream
      .emits(messages)
      .evalMap { msg =>
        translateMessage(msg) match
          case Right(event) =>
            accepted.update(_ :+ event) *>
              IO.println(s"  [✓] ${msg.source}/${msg.messageId} → ${event.getClass.getSimpleName}") *>
              IO.pure((1, 0))
          case Left(reason) =>
            rejected.update(_ :+ RejectedMessage(msg.messageId, msg.source, reason)) *>
              IO.println(s"  [✗] ${msg.source}/${msg.messageId} → 拒绝: $reason") *>
              IO.pure((0, 1))
      }
      .compile
      .fold((0, 0)) { case ((a, r), (da, dr)) => (a + da, r + dr) }

// ── 演示 ──────────────────────────────────────────────────────────────────────

object FS2ACLTranslationStreamDemo extends IOApp.Simple:

  val messages: List[UpstreamMessage] = List(
    UpstreamMessage.PaymentMsg("msg-001", "txn-1", "order-100", "SUCCESS", 29900L, "CNY"),
    UpstreamMessage.LogisticsMsg("msg-002", "SF-001", "order-100", "IN_TRANSIT", "深圳"),
    UpstreamMessage.InventoryMsg("msg-003", "req-1", "SKU-BTC-101", 50, 10, "WH-SZ"),
    UpstreamMessage.PaymentMsg("msg-004", "txn-2", "order-101", "FAILED", 0L, "CNY"),
    UpstreamMessage.LogisticsMsg("msg-005", "SF-001", "order-100", "DELIVERED", "上海"),
    UpstreamMessage.PaymentMsg("msg-006", "txn-3", "", "SUCCESS", 10000L, "CNY"),    // 缺 merchantRef
    UpstreamMessage.LogisticsMsg("msg-007", "YT-002", "order-102", "UNKNOWN", "北京"), // 未知事件
    UpstreamMessage.InventoryMsg("msg-008", "req-2", "INVALID-BTC-101", 20, 5, "WH-BJ"), // 格式错误
  )

  def run: IO[Unit] =
    for
      accepted <- Ref.of[IO, List[DomainEvent]](List.empty)
      rejected <- Ref.of[IO, List[RejectedMessage]](List.empty)

      _ <- IO.println(s"=== fs2 ACL 翻译流：${messages.length} 条上游消息 ===\n")
      result <- ACLTranslationStream.run(messages, accepted, rejected)
      (ok, rej) = result

      domainEvents <- accepted.get
      deadLetters  <- rejected.get

      _ <- IO.println(s"""
|── 翻译报告 ─────────────────────────────────
|  接受: $ok 条  拒绝: $rej 条
|
|── 本地领域事件（共 ${domainEvents.length} 条）────────────────────
|${domainEvents.zipWithIndex.map { case (e, i) => s"  [${i+1}] $e" }.mkString("\n")}
|
|── 拒绝日志（共 ${deadLetters.length} 条）─────────────────────────
|${deadLetters.map(r => s"  [${r.source}/${r.messageId}] ${r.reason}").mkString("\n")}
|
|关键点：
|  1. ACL 翻译流把上游消息流转成本地领域事件流，内部完全解耦
|  2. 翻译失败写入拒绝日志，不阻塞成功消息的处理
|  3. 同一条流可以处理来自多个上游系统的混合消息（按类型匹配）
|  4. 每条翻译规则是纯函数，可以独立单元测试""".stripMargin)
    yield ()
