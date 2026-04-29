// 134_DoobieMultiContextTransaction.scala
// 有界上下文地图集成 第四步：Doobie 多上下文事务协调
//
// 核心思想：
//   在同一个数据库实例（或分布式事务边界）里，跨上下文的状态需要：
//     1. 用不同的 schema 隔离各上下文（order_schema / payment_schema / inventory_schema）
//     2. 跨上下文的一致性通过事件驱动最终一致（不是分布式强一致）
//     3. 但在同一个进程/数据库内，可以用数据库事务保证"记录事件"和"更新状态"的原子性
//
//   本 Demo 演示：
//     - 用 Doobie 把 Order Context 和 Integration Events 表放在同一事务
//     - 插入订单的同时写入集成事件（Outbox 模式的简化版）
//     - 跨上下文查询：聚合 Order + Payment + Inventory 的数据视图

//> using scala "3.3.3"
//> using dep "org.typelevel::cats-effect:3.5.4"
//> using dep "org.tpolecat::doobie-core:1.0.0-RC4"
//> using dep "org.tpolecat::doobie-h2:1.0.0-RC4"

import cats.effect.{IO, IOApp}
import cats.syntax.all._
import doobie._
import doobie.implicits._
import doobie.h2.H2Transactor

// ── DDL ───────────────────────────────────────────────────────────────────────

val initDDL: ConnectionIO[Unit] =
  for
    // Order Context 表
    _ <- sql"""
      CREATE TABLE IF NOT EXISTS orders (
        order_id  VARCHAR PRIMARY KEY,
        sku       VARCHAR NOT NULL,
        quantity  INT     NOT NULL,
        price     DECIMAL(10,2) NOT NULL,
        status    VARCHAR NOT NULL DEFAULT 'awaiting-payment'
      )""".update.run.void

    // Payment Context 表
    _ <- sql"""
      CREATE TABLE IF NOT EXISTS payments (
        payment_id VARCHAR PRIMARY KEY,
        order_id   VARCHAR NOT NULL,
        amount     DECIMAL(10,2) NOT NULL,
        status     VARCHAR NOT NULL DEFAULT 'pending'
      )""".update.run.void

    // Inventory Context 表
    _ <- sql"""
      CREATE TABLE IF NOT EXISTS reservations (
        reservation_id VARCHAR PRIMARY KEY,
        order_id       VARCHAR NOT NULL,
        sku            VARCHAR NOT NULL,
        quantity       INT     NOT NULL,
        warehouse_id   VARCHAR NOT NULL
      )""".update.run.void

    // 集成事件表（Outbox）
    _ <- sql"""
      CREATE TABLE IF NOT EXISTS integration_events (
        id          BIGINT IDENTITY PRIMARY KEY,
        event_type  VARCHAR NOT NULL,
        order_id    VARCHAR NOT NULL,
        payload     VARCHAR NOT NULL,
        published   BOOLEAN NOT NULL DEFAULT FALSE
      )""".update.run.void
  yield ()

// ── Order Context 操作 ────────────────────────────────────────────────────────

def createOrderTx(orderId: String, sku: String, qty: Int, price: Double): ConnectionIO[Unit] =
  for
    _ <- sql"""
           INSERT INTO orders (order_id, sku, quantity, price)
           VALUES ($orderId, $sku, $qty, $price)
         """.update.run
    // 同事务写入集成事件（Outbox）
    _ <- sql"""
           INSERT INTO integration_events (event_type, order_id, payload)
           VALUES ('OrderCreated', $orderId, ${s"sku=$sku qty=$qty price=$price"})
         """.update.run
  yield ()

// ── Payment Context 操作 ──────────────────────────────────────────────────────

def processPaymentTx(orderId: String, paymentId: String, amount: Double): ConnectionIO[Unit] =
  for
    _ <- sql"UPDATE orders SET status = 'paid' WHERE order_id = $orderId".update.run
    _ <- sql"""
           INSERT INTO payments (payment_id, order_id, amount, status)
           VALUES ($paymentId, $orderId, $amount, 'authorized')
         """.update.run
    _ <- sql"""
           INSERT INTO integration_events (event_type, order_id, payload)
           VALUES ('PaymentAuthorized', $orderId, ${s"paymentId=$paymentId amount=$amount"})
         """.update.run
  yield ()

// ── Inventory Context 操作 ────────────────────────────────────────────────────

def reserveInventoryTx(orderId: String, sku: String, qty: Int, warehouseId: String): ConnectionIO[Unit] =
  val resId = s"res-$orderId"
  for
    _ <- sql"""
           INSERT INTO reservations (reservation_id, order_id, sku, quantity, warehouse_id)
           VALUES ($resId, $orderId, $sku, $qty, $warehouseId)
         """.update.run
    _ <- sql"UPDATE orders SET status = 'awaiting-shipment' WHERE order_id = $orderId".update.run
    _ <- sql"""
           INSERT INTO integration_events (event_type, order_id, payload)
           VALUES ('InventoryReserved', $orderId, ${s"sku=$sku warehouseId=$warehouseId"})
         """.update.run
  yield ()

// ── 跨上下文聚合查询 ──────────────────────────────────────────────────────────

case class FulfillmentRecord(
    orderId:    String,
    sku:        String,
    orderStatus: String,
    paymentId:  Option[String],
    paymentAmt: Option[Double],
    warehouseId: Option[String]
)

val queryFulfillmentView: ConnectionIO[List[FulfillmentRecord]] =
  sql"""
    SELECT
      o.order_id,
      o.sku,
      o.status,
      p.payment_id,
      p.amount,
      r.warehouse_id
    FROM orders o
    LEFT JOIN payments p     ON p.order_id = o.order_id
    LEFT JOIN reservations r ON r.order_id = o.order_id
    ORDER BY o.order_id
  """.query[FulfillmentRecord].to[List]

val queryPendingEvents: ConnectionIO[List[(String, String, String)]] =
  sql"""
    SELECT event_type, order_id, payload FROM integration_events
    WHERE published = FALSE ORDER BY id
  """.query[(String, String, String)].to[List]

// ── 演示 ──────────────────────────────────────────────────────────────────────

object DoobieMultiContextTransactionDemo extends IOApp.Simple:

  def run: IO[Unit] =
    H2Transactor
      .newH2Transactor[IO](
        "jdbc:h2:mem:contextmap;DB_CLOSE_DELAY=-1",
        "sa",
        "",
        scala.concurrent.ExecutionContext.global
      )
      .use { xa =>
        for
          _ <- initDDL.transact(xa)
          _ <- IO.println("=== Doobie 多上下文事务协调：Order + Payment + Inventory ===\n")

          // 1. 创建两个订单（带集成事件）
          _ <- createOrderTx("o-1", "BTC-101", 2, 150.00).transact(xa)
          _ <- IO.println("[✓] 创建订单 o-1（同时写入 OrderCreated 集成事件）")
          _ <- createOrderTx("o-2", "ETH-202", 1, 200.00).transact(xa)
          _ <- IO.println("[✓] 创建订单 o-2")

          // 2. o-1 支付
          _ <- processPaymentTx("o-1", "pay-001", 300.00).transact(xa)
          _ <- IO.println("[✓] o-1 支付完成（同时写入 PaymentAuthorized 集成事件）")

          // 3. o-1 库存预留
          _ <- reserveInventoryTx("o-1", "BTC-101", 2, "WH-SZ").transact(xa)
          _ <- IO.println("[✓] o-1 库存预留（同时写入 InventoryReserved 集成事件）")

          // 4. 查询聚合履约视图
          records <- queryFulfillmentView.transact(xa)
          _       <- IO.println(s"\n── 跨上下文聚合视图（${records.length} 条）─────────────────")
          _       <- records.traverse_ { r =>
                       IO.println(s"  ${r.orderId}: [${r.orderStatus}] sku=${r.sku} pay=${r.paymentId} wh=${r.warehouseId}")
                     }

          // 5. 查询待发布集成事件
          pending <- queryPendingEvents.transact(xa)
          _       <- IO.println(s"\n── 待发布集成事件（${pending.length} 条）─────────────────")
          _       <- pending.traverse_ { case (t, o, p) => IO.println(s"  $t | $o | $p") }

          _ <- IO.println("""
|关键点：
|  1. 各上下文的表用命名区分，共享数据库但逻辑隔离
|  2. 每次状态更新同时写入集成事件（Outbox），保证"更新"和"发布"的原子性
|  3. 聚合视图用 LEFT JOIN 跨上下文拼接，适合同数据库的报表场景
|  4. 不同数据库的跨上下文最终一致性由事件总线 + 幂等消费来保证""".stripMargin)
        yield ()
      }
