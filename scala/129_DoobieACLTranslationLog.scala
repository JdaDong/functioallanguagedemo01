// 129_DoobieACLTranslationLog.scala
// 防腐层 第四步：Doobie ACL 翻译日志
//
// 核心思想：
//   ACL 的持久化需要三张表：
//     - acl_incoming：上游消息记录（幂等去重 + 原始内容）
//     - acl_domain_events：翻译后的本地领域事件
//     - acl_rejections：翻译失败的拒绝记录
//
//   在同一事务内完成：
//     1. 记录原始上游消息（幂等 unique 约束）
//     2. 写入翻译结果（成功→领域事件表，失败→拒绝表）
//   这样保证"记录收到"与"发布领域事件"的原子性

//> using scala "3.3.3"
//> using dep "org.typelevel::cats-effect:3.5.4"
//> using dep "org.tpolecat::doobie-core:1.0.0-RC4"
//> using dep "org.tpolecat::doobie-h2:1.0.0-RC4"

import cats.effect.{IO, IOApp}
import cats.syntax.all._
import doobie._
import doobie.implicits._
import doobie.h2.H2Transactor

// ── ACL 仓库 ──────────────────────────────────────────────────────────────────

object ACLRepository:

  val initDDL: ConnectionIO[Unit] =
    for
      _ <- sql"""
        CREATE TABLE IF NOT EXISTS acl_incoming (
          message_id  VARCHAR PRIMARY KEY,
          source      VARCHAR NOT NULL,
          raw_payload VARCHAR NOT NULL,
          received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )""".update.run.void

      _ <- sql"""
        CREATE TABLE IF NOT EXISTS acl_domain_events (
          id          BIGINT IDENTITY PRIMARY KEY,
          message_id  VARCHAR NOT NULL,
          event_type  VARCHAR NOT NULL,
          order_id    VARCHAR NOT NULL,
          payload     VARCHAR NOT NULL
        )""".update.run.void

      _ <- sql"""
        CREATE TABLE IF NOT EXISTS acl_rejections (
          id          BIGINT IDENTITY PRIMARY KEY,
          message_id  VARCHAR NOT NULL,
          source      VARCHAR NOT NULL,
          reason      VARCHAR NOT NULL
        )""".update.run.void
    yield ()

  /** 检查 messageId 是否已处理 */
  def isProcessed(messageId: String): ConnectionIO[Boolean] =
    sql"SELECT COUNT(*) FROM acl_incoming WHERE message_id = $messageId"
      .query[Int].unique.map(_ > 0)

  /** 事务性处理：记录原始消息 + 写入翻译结果
    * 返回：Right(eventType) 翻译成功，Left(reason) 翻译拒绝，None 幂等跳过
    */
  def processMessage(
      messageId:  String,
      source:     String,
      rawPayload: String,
      translated: Either[String, (String, String, String)]  // Left=reason, Right=(eventType, orderId, payload)
  ): ConnectionIO[Option[Either[String, String]]] =
    for
      already <- isProcessed(messageId)
      result  <-
        if already then
          FC.pure(None)
        else
          for
            // 1. 记录原始消息
            _ <- sql"""
                   INSERT INTO acl_incoming (message_id, source, raw_payload)
                   VALUES ($messageId, $source, $rawPayload)
                 """.update.run

            // 2. 写入翻译结果
            r <- translated match
              case Right((eventType, orderId, payload)) =>
                sql"""
                  INSERT INTO acl_domain_events (message_id, event_type, order_id, payload)
                  VALUES ($messageId, $eventType, $orderId, $payload)
                """.update.run *> FC.pure(Right(eventType))
              case Left(reason) =>
                sql"""
                  INSERT INTO acl_rejections (message_id, source, reason)
                  VALUES ($messageId, $source, $reason)
                """.update.run *> FC.pure(Left(reason))
          yield Some(r)
    yield result

  /** 查询所有领域事件 */
  val queryDomainEvents: ConnectionIO[List[(String, String, String)]] =
    sql"SELECT event_type, order_id, payload FROM acl_domain_events ORDER BY id"
      .query[(String, String, String)].to[List]

  /** 查询所有拒绝记录 */
  val queryRejections: ConnectionIO[List[(String, String, String)]] =
    sql"SELECT message_id, source, reason FROM acl_rejections ORDER BY id"
      .query[(String, String, String)].to[List]

// ── 翻译辅助函数 ──────────────────────────────────────────────────────────────

def translatePaymentRaw(
    merchantRef: String, txnId: String,
    status: String, amountCents: Long
): Either[String, (String, String, String)] =
  status match
    case "SUCCESS" if merchantRef.nonEmpty && amountCents > 0 =>
      val amount = BigDecimal(amountCents) / 100
      Right(("PaymentReceived", merchantRef, s"paymentId=$txnId amount=$amount"))
    case "FAILED" =>
      Right(("PaymentDeclined", merchantRef, s"paymentId=$txnId"))
    case "SUCCESS" if merchantRef.isEmpty =>
      Left("merchantRef 为空")
    case other =>
      Left(s"不支持的支付状态: $other")

def translateLogisticsRaw(
    shipmentNo: String, refCode: String, eventCode: String, location: String
): Either[String, (String, String, String)] =
  eventCode match
    case "DELIVERED"  =>
      Right(("ShipmentDelivered", refCode, s"trackingNo=$shipmentNo"))
    case "IN_TRANSIT" =>
      Right(("ShipmentInTransit", refCode, s"trackingNo=$shipmentNo location=$location"))
    case other =>
      Left(s"不支持的物流事件: $other")

// ── 演示 ──────────────────────────────────────────────────────────────────────

object DoobieACLTranslationLogDemo extends IOApp.Simple:

  def run: IO[Unit] =
    H2Transactor
      .newH2Transactor[IO](
        "jdbc:h2:mem:acl_log;DB_CLOSE_DELAY=-1",
        "sa",
        "",
        scala.concurrent.ExecutionContext.global
      )
      .use { xa =>
        for
          _ <- ACLRepository.initDDL.transact(xa)
          _ <- IO.println("=== Doobie ACL 翻译日志：消息记录 + 领域事件 + 拒绝记录 ===\n")

          // 1. 支付成功
          r1 <- ACLRepository.processMessage(
                  "msg-001", "PaymentGateway", """{"txnId":"txn-1","status":"SUCCESS"}""",
                  translatePaymentRaw("order-100", "txn-1", "SUCCESS", 29900L)
                ).transact(xa)
          _  <- IO.println(s"[msg-001] PaymentSuccess: $r1")

          // 2. 幂等重放
          r2 <- ACLRepository.processMessage(
                  "msg-001", "PaymentGateway", """{"txnId":"txn-1","status":"SUCCESS"}""",
                  translatePaymentRaw("order-100", "txn-1", "SUCCESS", 29900L)
                ).transact(xa)
          _  <- IO.println(s"[msg-001 重放] 幂等跳过: ${r2.isEmpty}")

          // 3. 翻译失败（写入拒绝表）
          r3 <- ACLRepository.processMessage(
                  "msg-002", "PaymentGateway", """{"status":"MYSTERY"}""",
                  translatePaymentRaw("order-101", "txn-2", "MYSTERY", 10000L)
                ).transact(xa)
          _  <- IO.println(s"[msg-002] 翻译失败: $r3")

          // 4. 物流送达
          r4 <- ACLRepository.processMessage(
                  "msg-003", "Logistics", """{"eventCode":"DELIVERED"}""",
                  translateLogisticsRaw("SF-999", "order-100", "DELIVERED", "上海")
                ).transact(xa)
          _  <- IO.println(s"[msg-003] ShipmentDelivered: $r4")

          // 5. 物流未知事件
          r5 <- ACLRepository.processMessage(
                  "msg-004", "Logistics", """{"eventCode":"UNKNOWN"}""",
                  translateLogisticsRaw("YT-001", "order-102", "UNKNOWN", "广州")
                ).transact(xa)
          _  <- IO.println(s"[msg-004] 物流翻译失败: $r5")

          // 6. 查询结果
          events     <- ACLRepository.queryDomainEvents.transact(xa)
          rejections <- ACLRepository.queryRejections.transact(xa)

          _ <- IO.println(s"\n── 本地领域事件（共 ${events.length} 条）─────────────────────")
          _ <- events.traverse_ { case (t, o, p) => IO.println(s"  $t | $o | $p") }

          _ <- IO.println(s"\n── 拒绝记录（共 ${rejections.length} 条）─────────────────────")
          _ <- rejections.traverse_ { case (m, s, r) => IO.println(s"  $m | $s | $r") }

          _ <- IO.println("""
|关键点：
|  1. PRIMARY KEY(message_id) 在数据库层保证幂等：重复消息直接跳过
|  2. 翻译结果和原始消息在同一事务内写入，保证"收到"和"翻译"的原子性
|  3. 拒绝记录保留完整信息，支持后续人工介入或规则修正后重处理
|  4. acl_domain_events 就是内部事件 Inbox，后续由进程管理器消费""".stripMargin)
        yield ()
      }
