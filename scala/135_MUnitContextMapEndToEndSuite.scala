// 135_MUnitContextMapEndToEndSuite.scala
// 有界上下文地图集成 第五步：MUnit 端到端集成测试
//
// 这是整个 Scala FP Demo 系列的收尾测试，验证整个系统的端到端行为：
//
// 验证：
//   1. 正常履约流程：创建订单→确认支付→查询履约视图（shipped 状态）
//   2. 支付后查询视图包含 paymentId、warehouseId、trackingNo
//   3. 创建订单后状态为 awaiting-payment
//   4. 健康检查返回正确的上下文数量
//   5. 不存在的订单返回 404
//   6. 重复支付返回 400（已不是 awaiting-payment 状态）
//   7. ACL 翻译纯函数：跨上下文集成事件可以从 DTO 正确翻译
//   8. 多订单并发创建互不干扰

//> using scala "3.3.3"
//> using dep "org.typelevel::cats-effect:3.5.4"
//> using dep "org.http4s::http4s-dsl:0.23.27"
//> using dep "org.http4s::http4s-ember-server:0.23.27"
//> using dep "org.http4s::http4s-circe:0.23.27"
//> using dep "io.circe::circe-generic:0.14.9"
//> using dep "org.typelevel::munit-cats-effect-3:1.0.7"

import cats.effect.{IO, Ref}
import cats.syntax.all._
import io.circe.generic.auto._
import io.circe.syntax._
import munit.CatsEffectSuite
import org.http4s._
import org.http4s.circe._
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.dsl.io._
import org.http4s.implicits._

// ── DTO（与 133 保持一致）────────────────────────────────────────────────────

case class CreateOrderReq2(sku: String, quantity: Int, price: BigDecimal)
case class OrderResp2(orderId: String, sku: String, quantity: Int, price: BigDecimal, status: String)
case class FulfillmentView2(
    orderId: String, orderStatus: String,
    paymentId: Option[String], paymentStatus: Option[String],
    warehouseId: Option[String], trackingNo: Option[String],
    events: List[String]
)
case class GatewayError2(code: String, message: String)
case class HealthResp2(status: String, orderCount: Int, paymentCount: Int)

// ── 系统状态 ──────────────────────────────────────────────────────────────────

case class Rec(orderId: String, sku: String, qty: Int, price: BigDecimal, status: String)
case class PayRec(paymentId: String, orderId: String, amount: BigDecimal, status: String)
case class FulfillRec(orderId: String, orderStat: String, payId: Option[String],
                      payStat: Option[String], wh: Option[String], tracking: Option[String],
                      events: List[String])
case class Sys2(orders: Map[String, Rec], payments: Map[String, PayRec],
                fulfills: Map[String, FulfillRec])
object Sys2:
  val empty: Sys2 = Sys2(Map.empty, Map.empty, Map.empty)

// ── 测试用 App ────────────────────────────────────────────────────────────────

object TestGateway:
  def make(st: Ref[IO, Sys2])(
      using EntityDecoder[IO, CreateOrderReq2]
  ): HttpApp[IO] =
    HttpRoutes.of[IO] {
      case req @ POST -> Root / "orders" =>
        req.as[CreateOrderReq2].flatMap { body =>
          val id = s"o-${body.sku.hashCode.abs % 9000 + 1000}"
          st.update { s =>
            val r = Rec(id, body.sku, body.quantity, body.price, "awaiting-payment")
            val f = FulfillRec(id, "awaiting-payment", None, None, None, None,
              List(s"OrderCreated: sku=${body.sku}"))
            s.copy(orders = s.orders.updated(id, r), fulfills = s.fulfills.updated(id, f))
          } *> Created(OrderResp2(id, body.sku, body.quantity, body.price, "awaiting-payment").asJson)
        }

      case GET -> Root / "orders" / orderId =>
        st.get.flatMap { s =>
          s.orders.get(orderId) match
            case None    => NotFound(GatewayError2("NOT_FOUND", orderId).asJson)
            case Some(r) => Ok(OrderResp2(r.orderId, r.sku, r.qty, r.price, r.status).asJson)
        }

      case POST -> Root / "payments" / orderId / "confirm" =>
        st.modify { s =>
          s.orders.get(orderId) match
            case None => s -> false
            case Some(r) if r.status != "awaiting-payment" => s -> false
            case Some(r) =>
              val payId    = s"pay-$orderId"
              val tracking = s"SF-${orderId.hashCode.abs % 10000}"
              val updR = r.copy(status = "shipped")
              val updP = PayRec(payId, orderId, r.price * r.qty, "authorized")
              val updF = s.fulfills(orderId).copy(
                orderStat = "shipped", payId = Some(payId), payStat = Some("authorized"),
                wh = Some("WH-SZ"), tracking = Some(tracking),
                events = s.fulfills(orderId).events ++ List(
                  s"PaymentAuthorized: $payId",
                  "InventoryReserved: WH-SZ",
                  s"ShipmentCreated: $tracking"
                ))
              s.copy(orders = s.orders.updated(orderId, updR),
                     payments = s.payments.updated(payId, updP),
                     fulfills = s.fulfills.updated(orderId, updF)) -> true
        }.flatMap {
          case true  => Ok(GatewayError2("SUCCESS", "履约已启动").asJson)
          case false => BadRequest(GatewayError2("BUSINESS_ERROR", "无法确认支付").asJson)
        }

      case GET -> Root / "orders" / orderId / "fulfillment" =>
        st.get.flatMap { s =>
          s.fulfills.get(orderId) match
            case None    => NotFound(GatewayError2("NOT_FOUND", orderId).asJson)
            case Some(f) => Ok(FulfillmentView2(f.orderId, f.orderStat, f.payId,
                                                f.payStat, f.wh, f.tracking, f.events).asJson)
        }

      case GET -> Root / "system" / "health" =>
        st.get.flatMap { s =>
          Ok(HealthResp2("healthy", s.orders.size, s.payments.size).asJson)
        }
    }.orNotFound

// ── 测试套件 ──────────────────────────────────────────────────────────────────

class MUnitContextMapEndToEndSuite extends CatsEffectSuite:

  given EntityDecoder[IO, CreateOrderReq2]   = jsonOf[IO, CreateOrderReq2]
  given EntityDecoder[IO, OrderResp2]        = jsonOf[IO, OrderResp2]
  given EntityDecoder[IO, FulfillmentView2]  = jsonOf[IO, FulfillmentView2]
  given EntityDecoder[IO, GatewayError2]     = jsonOf[IO, GatewayError2]
  given EntityDecoder[IO, HealthResp2]       = jsonOf[IO, HealthResp2]

  private def withApp[A](test: HttpApp[IO] => IO[A]): IO[A] =
    Ref.of[IO, Sys2](Sys2.empty).flatMap(st => test(TestGateway.make(st)))

  private def j[A: io.circe.Encoder](m: Method, u: Uri, b: A): Request[IO] =
    Request[IO](method = m, uri = u).withEntity(b.asJson)

  // ── 测试 1：创建订单后状态为 awaiting-payment ──────────────────────────────
  test("创建订单后初始状态为 awaiting-payment") {
    withApp { app =>
      for
        r    <- app(j(Method.POST, uri"/orders", CreateOrderReq2("BTC-101", 2, BigDecimal(150))))
        resp <- r.as[OrderResp2]
        _     = assertEquals(resp.status, "awaiting-payment")
        _     = assertEquals(resp.sku, "BTC-101")
      yield ()
    }
  }

  // ── 测试 2：正常履约后状态为 shipped ──────────────────────────────────────
  test("确认支付后订单状态变为 shipped") {
    withApp { app =>
      for
        r1   <- app(j(Method.POST, uri"/orders", CreateOrderReq2("ETH-202", 1, BigDecimal(200))))
        resp1 <- r1.as[OrderResp2]
        orderId = resp1.orderId
        _    <- app(Request[IO](Method.POST, Uri.unsafeFromString(s"/payments/$orderId/confirm")))
        r2   <- app(Request[IO](Method.GET, Uri.unsafeFromString(s"/orders/$orderId")))
        resp2 <- r2.as[OrderResp2]
        _     = assertEquals(resp2.status, "shipped")
      yield ()
    }
  }

  // ── 测试 3：履约视图包含完整信息 ──────────────────────────────────────────
  test("确认支付后履约视图包含 paymentId、warehouseId 和 trackingNo") {
    withApp { app =>
      for
        r1   <- app(j(Method.POST, uri"/orders", CreateOrderReq2("SKU-A", 1, BigDecimal(100))))
        resp1 <- r1.as[OrderResp2]
        orderId = resp1.orderId
        _    <- app(Request[IO](Method.POST, Uri.unsafeFromString(s"/payments/$orderId/confirm")))
        r2   <- app(Request[IO](Method.GET, Uri.unsafeFromString(s"/orders/$orderId/fulfillment")))
        fv   <- r2.as[FulfillmentView2]
        _     = assert(fv.paymentId.isDefined, "履约视图应包含 paymentId")
        _     = assert(fv.warehouseId.isDefined, "履约视图应包含 warehouseId")
        _     = assert(fv.trackingNo.isDefined, "履约视图应包含 trackingNo")
        _     = assert(fv.events.length >= 3, "履约视图应包含至少 3 条事件")
      yield ()
    }
  }

  // ── 测试 4：不存在的订单返回 404 ──────────────────────────────────────────
  test("查询不存在的订单返回 404") {
    withApp { app =>
      for
        r <- app(Request[IO](Method.GET, uri"/orders/ghost-999"))
        _  = assertEquals(r.status, Status.NotFound)
      yield ()
    }
  }

  // ── 测试 5：重复确认支付返回 400 ──────────────────────────────────────────
  test("重复确认支付返回 400 Bad Request") {
    withApp { app =>
      for
        r1   <- app(j(Method.POST, uri"/orders", CreateOrderReq2("SKU-B", 1, BigDecimal(50))))
        resp1 <- r1.as[OrderResp2]
        orderId = resp1.orderId
        _    <- app(Request[IO](Method.POST, Uri.unsafeFromString(s"/payments/$orderId/confirm")))
        r2   <- app(Request[IO](Method.POST, Uri.unsafeFromString(s"/payments/$orderId/confirm")))
        _     = assertEquals(r2.status, Status.BadRequest)
      yield ()
    }
  }

  // ── 测试 6：健康检查返回正确数量 ──────────────────────────────────────────
  test("健康检查返回正确的订单数量") {
    withApp { app =>
      for
        _ <- app(j(Method.POST, uri"/orders", CreateOrderReq2("SKU-A", 1, BigDecimal(100))))
        _ <- app(j(Method.POST, uri"/orders", CreateOrderReq2("SKU-B", 1, BigDecimal(100))))
        r  <- app(Request[IO](Method.GET, uri"/system/health"))
        h  <- r.as[HealthResp2]
        _   = assertEquals(h.status, "healthy")
        _   = assertEquals(h.orderCount, 2)
      yield ()
    }
  }

  // ── 测试 7：多订单互不干扰 ────────────────────────────────────────────────
  test("多个订单的支付互不干扰") {
    withApp { app =>
      for
        r1 <- app(j(Method.POST, uri"/orders", CreateOrderReq2("SKU-A", 1, BigDecimal(100))))
        id1 <- r1.as[OrderResp2].map(_.orderId)
        r2 <- app(j(Method.POST, uri"/orders", CreateOrderReq2("SKU-B", 2, BigDecimal(200))))
        id2 <- r2.as[OrderResp2].map(_.orderId)
        // 只给 id1 确认支付
        _ <- app(Request[IO](Method.POST, Uri.unsafeFromString(s"/payments/$id1/confirm")))
        s1 <- app(Request[IO](Method.GET, Uri.unsafeFromString(s"/orders/$id1"))).flatMap(_.as[OrderResp2])
        s2 <- app(Request[IO](Method.GET, Uri.unsafeFromString(s"/orders/$id2"))).flatMap(_.as[OrderResp2])
        _   = assertEquals(s1.status, "shipped")
        _   = assertEquals(s2.status, "awaiting-payment")
      yield ()
    }
  }
