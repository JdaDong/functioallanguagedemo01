// 133_Http4sContextMapGateway.scala
// 有界上下文地图集成 第三步：http4s 统一网关
//
// 核心思想：
//   对外暴露一个统一网关，把来自不同上下文的 HTTP 接口聚合在一起：
//     POST /orders                    → Order Context：创建订单
//     GET  /orders/{id}               → Order Context：查询订单状态
//     POST /payments/{orderId}/confirm → Payment Context：确认支付（模拟回调）
//     GET  /orders/{id}/fulfillment   → 查询完整履约状态（跨上下文聚合视图）
//     GET  /system/health             → 系统整体健康检查
//
//   网关内部通过进程管理器协调各上下文状态推进

//> using scala "3.3.3"
//> using dep "org.typelevel::cats-effect:3.5.4"
//> using dep "org.http4s::http4s-dsl:0.23.27"
//> using dep "org.http4s::http4s-ember-server:0.23.27"
//> using dep "org.http4s::http4s-circe:0.23.27"
//> using dep "io.circe::circe-generic:0.14.9"
//> using dep "io.circe::circe-parser:0.14.9"

import cats.effect.{IO, IOApp, Ref}
import cats.syntax.all._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.implicits._

// ── 请求/响应 DTO ─────────────────────────────────────────────────────────────

case class CreateOrderReq(sku: String, quantity: Int, price: BigDecimal)
case class OrderResp(orderId: String, sku: String, quantity: Int, price: BigDecimal, status: String)
case class FulfillmentView(
    orderId:     String,
    orderStatus: String,
    paymentId:   Option[String],
    paymentStatus: Option[String],
    warehouseId: Option[String],
    trackingNo:  Option[String],
    events:      List[String]
)
case class GatewayError(code: String, message: String)
case class HealthResp(status: String, orderCount: Int, paymentCount: Int)

// ── 多上下文状态 ──────────────────────────────────────────────────────────────

case class OrderRecord(orderId: String, sku: String, qty: Int, price: BigDecimal, status: String)
case class PaymentRecord(paymentId: String, orderId: String, amount: BigDecimal, status: String)
case class FulfillState(
    orderId:    String,
    orderStat:  String,
    paymentId:  Option[String],
    paymentStat: Option[String],
    warehouseId: Option[String],
    trackingNo: Option[String],
    events:     List[String]
)

case class SystemState(
    orders:       Map[String, OrderRecord],
    payments:     Map[String, PaymentRecord],
    fulfillments: Map[String, FulfillState]
)
object SystemState:
  val empty: SystemState = SystemState(Map.empty, Map.empty, Map.empty)

// ── 统一网关路由 ──────────────────────────────────────────────────────────────

def gatewayRoutes(
    state: Ref[IO, SystemState]
)(using EntityDecoder[IO, CreateOrderReq]): HttpRoutes[IO] =
  HttpRoutes.of[IO] {

    // 创建订单
    case req @ POST -> Root / "orders" =>
      req.as[CreateOrderReq].flatMap { body =>
        val orderId = s"order-${System.nanoTime() % 100000}"
        state.update { s =>
          val order = OrderRecord(orderId, body.sku, body.quantity, body.price, "awaiting-payment")
          val fulfillment = FulfillState(orderId, "awaiting-payment", None, None, None, None,
            List(s"OrderCreated: sku=${body.sku} qty=${body.quantity}"))
          s.copy(
            orders = s.orders.updated(orderId, order),
            fulfillments = s.fulfillments.updated(orderId, fulfillment)
          )
        } *> Created(OrderResp(orderId, body.sku, body.quantity, body.price, "awaiting-payment").asJson)
      }

    // 查询订单
    case GET -> Root / "orders" / orderId =>
      state.get.flatMap { s =>
        s.orders.get(orderId) match
          case None    => NotFound(GatewayError("NOT_FOUND", s"订单 $orderId 不存在").asJson)
          case Some(o) => Ok(OrderResp(o.orderId, o.sku, o.qty, o.price, o.status).asJson)
      }

    // 模拟支付回调（Payment Context 的 ACL 入口）
    case POST -> Root / "payments" / orderId / "confirm" =>
      state.modify { s =>
        s.orders.get(orderId) match
          case None => s -> Left("订单不存在")
          case Some(o) if o.status != "awaiting-payment" =>
            s -> Left(s"订单状态为 ${o.status}，无法确认支付")
          case Some(o) =>
            val payId = s"pay-$orderId"
            val updOrder = o.copy(status = "awaiting-inventory")
            val updPay   = PaymentRecord(payId, orderId, o.price * o.qty, "authorized")
            val updFulfill = s.fulfillments(orderId).copy(
              orderStat = "awaiting-inventory",
              paymentId = Some(payId),
              paymentStat = Some("authorized"),
              warehouseId = Some("WH-SZ"),
              trackingNo  = Some(s"SF-${orderId.hashCode.abs % 10000}"),
              events = s.fulfillments(orderId).events :+
                s"PaymentAuthorized: payId=$payId" :+
                "InventoryReserved: warehouseId=WH-SZ" :+
                s"ShipmentCreated: trackingNo=SF-${orderId.hashCode.abs % 10000}"
            )
            val updOrderFinal = updOrder.copy(status = "shipped")
            val updFulfillFinal = updFulfill.copy(orderStat = "shipped")
            s.copy(
              orders = s.orders.updated(orderId, updOrderFinal),
              payments = s.payments.updated(payId, updPay),
              fulfillments = s.fulfillments.updated(orderId, updFulfillFinal)
            ) -> Right(())
      }.flatMap {
        case Right(_) => Ok(GatewayError("SUCCESS", "支付确认，履约流程已启动").asJson)
        case Left(e)  => BadRequest(GatewayError("BUSINESS_ERROR", e).asJson)
      }

    // 查询完整履约视图（跨上下文聚合）
    case GET -> Root / "orders" / orderId / "fulfillment" =>
      state.get.flatMap { s =>
        s.fulfillments.get(orderId) match
          case None => NotFound(GatewayError("NOT_FOUND", orderId).asJson)
          case Some(f) =>
            Ok(FulfillmentView(f.orderId, f.orderStat, f.paymentId,
                               f.paymentStat, f.warehouseId, f.trackingNo, f.events).asJson)
      }

    // 健康检查
    case GET -> Root / "system" / "health" =>
      state.get.flatMap { s =>
        Ok(HealthResp("healthy", s.orders.size, s.payments.size).asJson)
      }
  }

// ── 演示 ──────────────────────────────────────────────────────────────────────

object Http4sContextMapGatewayDemo extends IOApp.Simple:

  given EntityDecoder[IO, CreateOrderReq] = jsonOf[IO, CreateOrderReq]

  private def j[A: io.circe.Encoder](m: Method, u: Uri, b: A): Request[IO] =
    Request[IO](method = m, uri = u).withEntity(b.asJson)

  def run: IO[Unit] =
    for
      state <- Ref.of[IO, SystemState](SystemState.empty)
      app    = gatewayRoutes(state).orNotFound

      _ <- IO.println("=== http4s 统一网关：多上下文 HTTP 接口聚合 ===\n")

      // 创建订单
      r1  <- app(j(Method.POST, uri"/orders", CreateOrderReq("BTC-101", 2, BigDecimal(150))))
      b1  <- r1.as[String]
      _   <- IO.println(s"[POST /orders] ${r1.status.code}: $b1")

      // 解析 orderId
      orderId = io.circe.parser.parse(b1).flatMap(_.hcursor.get[String]("orderId")).getOrElse("unknown")

      // 查询订单状态
      r2 <- app(Request[IO](Method.GET, Uri.unsafeFromString(s"/orders/$orderId")))
      b2 <- r2.as[String]
      _  <- IO.println(s"[GET /orders/$orderId] ${r2.status.code}: $b2")

      // 确认支付（触发整个履约链路）
      r3 <- app(Request[IO](Method.POST, Uri.unsafeFromString(s"/payments/$orderId/confirm")))
      b3 <- r3.as[String]
      _  <- IO.println(s"[POST /payments/$orderId/confirm] ${r3.status.code}: $b3")

      // 查询完整履约视图
      r4 <- app(Request[IO](Method.GET, Uri.unsafeFromString(s"/orders/$orderId/fulfillment")))
      b4 <- r4.as[String]
      _  <- IO.println(s"[GET /orders/$orderId/fulfillment] ${r4.status.code}: $b4")

      // 健康检查
      r5 <- app(Request[IO](Method.GET, uri"/system/health"))
      b5 <- r5.as[String]
      _  <- IO.println(s"\n[GET /system/health] ${r5.status.code}: $b5")

      _ <- IO.println("""
|关键点：
|  1. 统一网关对外屏蔽了多个有界上下文的分布，客户端只感知一套 API
|  2. GET /fulfillment 是跨上下文的聚合视图，合并 Order/Payment/Inventory/Logistics 状态
|  3. POST /payments/{id}/confirm 是支付网关回调的 ACL 入口
|  4. GET /system/health 聚合所有上下文的健康指标，用于监控告警""".stripMargin)
    yield ()
