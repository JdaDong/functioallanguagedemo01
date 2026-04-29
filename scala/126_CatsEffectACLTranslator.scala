// 126_CatsEffectACLTranslator.scala
// 防腐层（Anti-Corruption Layer）第一步：cats-effect ACL 翻译器
//
// 核心思想：
//   有界上下文之间的集成面临一个根本问题：
//   上游系统（如第三方支付、ERP、遗留系统）有自己的领域模型，
//   如果直接使用，它们的概念会污染本地领域。
//
//   防腐层（ACL）= 翻译 + 验证 + 保护
//     - 翻译：把上游的 DTO 翻译成本地领域对象
//     - 验证：拒绝无法翻译的无效数据
//     - 保护：内部领域完全感知不到上游的存在
//
//   典型场景：支付网关的回调事件翻译
//     上游：{ "txn_id": "...", "status": "SUCCESS", "amount_cents": 30000 }
//     本地：PaymentReceived(paymentId, orderId, amount: BigDecimal)
//
// 本 Demo 演示：
//   1. 上游支付网关 DTO（外部模型）
//   2. 本地支付领域对象
//   3. ACL 翻译器：外部 → 本地（带验证）
//   4. 翻译失败时的拒绝语义
//   5. 多个上游系统的翻译器组合

//> using scala "3.3.3"
//> using dep "org.typelevel::cats-effect:3.5.4"

import cats.effect.{IO, IOApp}
import cats.syntax.all._

// ── 上游模型（外部系统，不可修改）───────────────────────────────────────────

/** 支付网关回调 DTO（第三方系统的格式）*/
case class PaymentGatewayCallback(
    txnId:       String,
    merchantRef: String,   // 对应本地 orderId，但字段名不同
    status:      String,   // "SUCCESS" | "FAILED" | "PENDING" | "REFUNDED"
    amountCents: Long,     // 分为单位，本地用 BigDecimal 元
    currency:    String,
    errorCode:   Option[String]
)

/** 物流系统事件 DTO（另一个上游系统）*/
case class LogisticsEvent(
    shipmentNo:    String,
    referenceCode: String,   // 对应本地 orderId
    eventCode:     String,   // "PICKED_UP" | "IN_TRANSIT" | "DELIVERED" | "FAILED"
    location:      String,
    timestamp:     Long
)

/** 库存系统响应 DTO */
case class InventoryResponse(
    requestId:    String,
    itemCode:     String,   // 对应本地 sku，但格式不同（带前缀）
    available:    Int,
    reserved:     Int,
    warehouseCode: String
)

// ── 本地领域模型（内部，纯净）────────────────────────────────────────────────

enum PaymentStatus:
  case Received, Failed, Pending, Refunded

case class PaymentResult(
    paymentId: String,
    orderId:   String,
    amount:    BigDecimal,
    currency:  String,
    status:    PaymentStatus
)

enum ShipmentStatus:
  case PickedUp, InTransit, Delivered, DeliveryFailed

case class ShipmentUpdate(
    trackingNo: String,
    orderId:    String,
    status:     ShipmentStatus,
    location:   String
)

case class StockInfo(
    sku:        String,
    available:  Int,
    reserved:   Int,
    warehouseId: String
)

// ── 翻译错误 ──────────────────────────────────────────────────────────────────

sealed trait TranslationError
object TranslationError:
  case class UnknownStatus(raw: String, source: String)   extends TranslationError
  case class InvalidAmount(cents: Long)                   extends TranslationError
  case class MissingField(field: String)                  extends TranslationError
  case class InvalidFormat(field: String, value: String)  extends TranslationError

// ── ACL 翻译器 ────────────────────────────────────────────────────────────────

object PaymentACL:
  /** 把支付网关回调翻译成本地 PaymentResult */
  def translate(dto: PaymentGatewayCallback): Either[TranslationError, PaymentResult] =
    for
      status <- dto.status match
        case "SUCCESS"  => Right(PaymentStatus.Received)
        case "FAILED"   => Right(PaymentStatus.Failed)
        case "PENDING"  => Right(PaymentStatus.Pending)
        case "REFUNDED" => Right(PaymentStatus.Refunded)
        case unknown    => Left(TranslationError.UnknownStatus(unknown, "PaymentGateway"))

      _ <- Either.cond(
             dto.amountCents > 0,
             (),
             TranslationError.InvalidAmount(dto.amountCents)
           )

      _ <- Either.cond(
             dto.merchantRef.nonEmpty,
             (),
             TranslationError.MissingField("merchantRef")
           )

      amount = BigDecimal(dto.amountCents) / 100   // 分 → 元
    yield PaymentResult(dto.txnId, dto.merchantRef, amount, dto.currency, status)

object LogisticsACL:
  /** 把物流事件翻译成本地 ShipmentUpdate */
  def translate(dto: LogisticsEvent): Either[TranslationError, ShipmentUpdate] =
    for
      status <- dto.eventCode match
        case "PICKED_UP"  => Right(ShipmentStatus.PickedUp)
        case "IN_TRANSIT" => Right(ShipmentStatus.InTransit)
        case "DELIVERED"  => Right(ShipmentStatus.Delivered)
        case "FAILED"     => Right(ShipmentStatus.DeliveryFailed)
        case unknown      => Left(TranslationError.UnknownStatus(unknown, "Logistics"))

      _ <- Either.cond(
             dto.referenceCode.nonEmpty,
             (),
             TranslationError.MissingField("referenceCode")
           )
    yield ShipmentUpdate(dto.shipmentNo, dto.referenceCode, status, dto.location)

object InventoryACL:
  /** 把库存系统响应翻译成本地 StockInfo
    * 注意：itemCode 格式是 "SKU-{本地sku}"，需要去掉前缀
    */
  def translate(dto: InventoryResponse): Either[TranslationError, StockInfo] =
    for
      sku <- (if dto.itemCode.startsWith("SKU-") then
               Right(dto.itemCode.stripPrefix("SKU-"))
             else
               Left(TranslationError.InvalidFormat("itemCode", dto.itemCode)))

      _ <- Either.cond(
             dto.available >= 0 && dto.reserved >= 0,
             (),
             TranslationError.InvalidAmount(dto.available.toLong)
           )
    yield StockInfo(sku, dto.available, dto.reserved, dto.warehouseCode)

// ── ACL 服务（组合多个翻译器，返回统一的领域事件）──────────────────────────

sealed trait LocalDomainEvent
object LocalDomainEvent:
  case class PaymentResultReceived(result: PaymentResult)   extends LocalDomainEvent
  case class ShipmentStatusUpdated(update: ShipmentUpdate)  extends LocalDomainEvent
  case class StockInfoRefreshed(info: StockInfo)            extends LocalDomainEvent
  case class TranslationRejected(error: TranslationError, source: String) extends LocalDomainEvent

class ACLService:
  def handlePaymentCallback(dto: PaymentGatewayCallback): IO[LocalDomainEvent] =
    PaymentACL.translate(dto) match
      case Right(result) => IO.pure(LocalDomainEvent.PaymentResultReceived(result))
      case Left(err)     => IO.pure(LocalDomainEvent.TranslationRejected(err, "PaymentGateway"))

  def handleLogisticsEvent(dto: LogisticsEvent): IO[LocalDomainEvent] =
    LogisticsACL.translate(dto) match
      case Right(update) => IO.pure(LocalDomainEvent.ShipmentStatusUpdated(update))
      case Left(err)     => IO.pure(LocalDomainEvent.TranslationRejected(err, "Logistics"))

  def handleInventoryResponse(dto: InventoryResponse): IO[LocalDomainEvent] =
    InventoryACL.translate(dto) match
      case Right(info) => IO.pure(LocalDomainEvent.StockInfoRefreshed(info))
      case Left(err)   => IO.pure(LocalDomainEvent.TranslationRejected(err, "Inventory"))

// ── 演示 ──────────────────────────────────────────────────────────────────────

object CatsEffectACLTranslatorDemo extends IOApp.Simple:

  val acl = ACLService()

  def printEvent(label: String, event: LocalDomainEvent): IO[Unit] =
    event match
      case LocalDomainEvent.TranslationRejected(err, src) =>
        IO.println(s"  [✗] $label → 翻译拒绝($src): $err")
      case e =>
        IO.println(s"  [✓] $label → $e")

  def run: IO[Unit] =
    for
      _ <- IO.println("=== 防腐层 ACL：把上游模型翻译成本地领域对象 ===\n")

      // 支付网关回调翻译
      _ <- IO.println("── 支付网关回调 ─────────────────────────────────")
      e1 <- acl.handlePaymentCallback(
              PaymentGatewayCallback("txn-001", "order-100", "SUCCESS", 29900L, "CNY", None))
      _  <- printEvent("SUCCESS 支付", e1)

      e2 <- acl.handlePaymentCallback(
              PaymentGatewayCallback("txn-002", "order-101", "FAILED", 0L, "CNY", Some("INSUFFICIENT_FUNDS")))
      _  <- printEvent("FAILED 支付", e2)

      e3 <- acl.handlePaymentCallback(
              PaymentGatewayCallback("txn-003", "order-102", "MYSTERY", 10000L, "CNY", None))
      _  <- printEvent("未知状态", e3)

      e4 <- acl.handlePaymentCallback(
              PaymentGatewayCallback("txn-004", "", "SUCCESS", 5000L, "CNY", None))
      _  <- printEvent("空 merchantRef", e4)

      // 物流系统事件翻译
      _ <- IO.println("\n── 物流系统事件 ─────────────────────────────────")
      e5 <- acl.handleLogisticsEvent(
              LogisticsEvent("SF-9999", "order-100", "DELIVERED", "上海浦东", 1699999999L))
      _  <- printEvent("DELIVERED", e5)

      e6 <- acl.handleLogisticsEvent(
              LogisticsEvent("SF-8888", "order-101", "UNKNOWN_CODE", "广州", 1699999999L))
      _  <- printEvent("未知事件码", e6)

      // 库存系统响应翻译
      _ <- IO.println("\n── 库存系统响应 ─────────────────────────────────")
      e7 <- acl.handleInventoryResponse(
              InventoryResponse("req-001", "SKU-BTC-101", 50, 10, "WH-SZ"))
      _  <- printEvent("正常库存", e7)

      e8 <- acl.handleInventoryResponse(
              InventoryResponse("req-002", "INVALID-FORMAT", 50, 10, "WH-BJ"))
      _  <- printEvent("错误 itemCode 格式", e8)

      _ <- IO.println("""
|关键点：
|  1. ACL 把"上游语言"翻译成"本地语言"，内部领域感知不到外部系统存在
|  2. 翻译失败不抛异常，而是返回 TranslationRejected，让上层决定如何处理
|  3. 每个上游系统有独立的 ACL 翻译器（PaymentACL / LogisticsACL / InventoryACL）
|  4. 翻译逻辑是纯函数（Either），便于单元测试且无副作用
|  5. 字段映射（merchantRef→orderId、amountCents→BigDecimal、itemCode→sku）集中在 ACL 层""".stripMargin)
    yield ()
