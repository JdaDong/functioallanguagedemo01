// 05_AkkaProjection.scala
// Akka Projection（CQRS 读模型投影）
//
// 核心思想：
//   Akka Projection = 从事件流构建读模型（Query Side）
//   与你的 Demo 106-110（cats-effect 版读模型投影）对比：
//
//   Demo 106-110（cats-effect 版）：
//     - 手动实现 checkpoint 推进（Ref + Doobie）
//     - 用 fs2 Stream 后台 catch-up
//     - 自己保证 at-least-once / exactly-once 语义
//
//   Akka Projection 版：
//     - 框架自动管理 offset（checkpoint）
//     - 内置 at-least-once / exactly-once 语义选择
//     - 支持 Kafka、JDBC、Cassandra 等多种 Source
//
// 本 Demo 演示（进程内模拟，不依赖外部 Kafka/数据库）：
//   1. 手动实现一个简化版 Projection（模拟 Akka Projection 的工作原理）
//   2. 演示 at-least-once 语义：处理失败时重试，可能重复
//   3. 演示幂等处理：用 processedIds 防止重复累计
//   4. 演示 offset checkpoint：成功处理后才推进 offset

//> using scala "3.3.3"
//> using dep "com.typesafe.akka::akka-actor-typed:2.8.5"
//> using dep "com.typesafe.akka::akka-stream:2.8.5"

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.scaladsl._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.collection.mutable

// ── 领域事件（模拟来自 EventStore 的事件序列）──────────────────────────────

case class EventEnvelope(offset: Long, eventType: String, orderId: String, payload: String)

// 模拟事件日志（Akka Persistence Journal 或 Kafka 的简化版）
val eventLog = List(
  EventEnvelope(1L, "OrderPlaced",   "o-1", "sku=BTC-101 qty=2 price=150"),
  EventEnvelope(2L, "PaymentReceived","o-1", "paymentId=pay-001 amount=300"),
  EventEnvelope(3L, "OrderPlaced",   "o-2", "sku=ETH-202 qty=1 price=200"),
  EventEnvelope(4L, "OrderShipped",  "o-1", "trackingNo=SF-9999"),
  EventEnvelope(5L, "PaymentReceived","o-2", "paymentId=pay-002 amount=200"),
  EventEnvelope(6L, "OrderShipped",  "o-2", "trackingNo=YT-8888"),
)

// ── 读模型（投影目标）──────────────────────────────────────────────────────

case class OrderReadModel(
    orderId:    String,
    status:     String,
    paymentId:  Option[String],
    trackingNo: Option[String]
)

// ── 简化版 Projection（模拟 Akka Projection 工作原理）────────────────────

class SimpleProjection:
  private val readModels   = mutable.Map.empty[String, OrderReadModel]
  private val processedIds = mutable.Set.empty[Long]   // 幂等去重
  private var currentOffset: Long = 0L                 // checkpoint

  def currentReadModels: Map[String, OrderReadModel] = readModels.toMap
  def checkpoint: Long = currentOffset

  /** 处理单个事件（at-least-once：可能被重复调用）*/
  def process(envelope: EventEnvelope, simulateFailureAt: Set[Long] = Set.empty): Either[String, Unit] =
    if simulateFailureAt.contains(envelope.offset) then
      Left(s"offset=${envelope.offset} 模拟处理失败")
    else if processedIds.contains(envelope.offset) then
      // 幂等：已处理过，跳过（这是 at-least-once 下的必要保护）
      println(s"  [幂等跳过] offset=${envelope.offset} ${envelope.eventType}")
      currentOffset = math.max(currentOffset, envelope.offset)
      Right(())
    else
      // 应用事件到读模型
      val current = readModels.getOrElse(envelope.orderId,
        OrderReadModel(envelope.orderId, "pending", None, None))
      val updated = envelope.eventType match
        case "OrderPlaced"    => current.copy(status = "awaiting-payment")
        case "PaymentReceived" =>
          val payId = envelope.payload.split(" ").find(_.startsWith("paymentId="))
                        .map(_.stripPrefix("paymentId="))
          current.copy(status = "paid", paymentId = payId)
        case "OrderShipped"   =>
          val tracking = envelope.payload.split(" ").find(_.startsWith("trackingNo="))
                           .map(_.stripPrefix("trackingNo="))
          current.copy(status = "shipped", trackingNo = tracking)
        case _ => current

      readModels.update(envelope.orderId, updated)
      processedIds.add(envelope.offset)
      currentOffset = math.max(currentOffset, envelope.offset)  // 推进 checkpoint
      println(s"  [✓] offset=${envelope.offset} ${envelope.eventType}(${envelope.orderId}) → ${updated.status}")
      Right(())

// ── 演示 ──────────────────────────────────────────────────────────────────────

@main def akkaProjectionDemo(): Unit =
  println("=== Akka Projection 原理演示：CQRS 读模型投影（对比 Demo 106-110）===\n")

  // ── 场景 1：正常投影（所有事件顺序处理）
  println("── 场景 1：正常投影 ──────────────────────────────────────────")
  val proj1 = SimpleProjection()
  eventLog.foreach { e =>
    proj1.process(e) match
      case Right(_)    => ()
      case Left(err)   => println(s"  [✗] $err")
  }
  println(s"\n  最终 checkpoint: ${proj1.checkpoint}")
  println("  读模型:")
  proj1.currentReadModels.values.toList.sortBy(_.orderId).foreach { r =>
    println(s"    ${r.orderId}: ${r.status} paymentId=${r.paymentId} trackingNo=${r.trackingNo}")
  }

  // ── 场景 2：at-least-once 重放（offset 2 和 4 被重复处理）
  println("\n── 场景 2：at-least-once 重放（幂等保护）─────────────────────")
  val proj2 = SimpleProjection()
  val replayLog = eventLog ++ List(
    eventLog(1),   // offset=2 重放
    eventLog(3),   // offset=4 重放
  )
  replayLog.foreach(e => proj2.process(e))
  println(s"\n  读模型数量: ${proj2.currentReadModels.size}（与场景1一致，幂等保护生效）")

  // ── 场景 3：处理失败后从 checkpoint 重试
  println("\n── 场景 3：处理失败 + 从 checkpoint 重试 ───────────────────────")
  val proj3 = SimpleProjection()
  val failAt = Set(3L)   // offset=3 模拟失败
  var retried = false
  eventLog.foreach { e =>
    proj3.process(e, failAt) match
      case Right(_) => ()
      case Left(err) =>
        println(s"  [✗] $err → 等待重试")
        if !retried then
          retried = true
          println(s"  [重试] 从 checkpoint=${proj3.checkpoint} 重放未处理事件")
          // 重试：从失败的 offset 重放
          eventLog.filter(_.offset > proj3.checkpoint).foreach { re =>
            proj3.process(re)
          }
  }

  println("""
|关键点（与 Demo 106-110 cats-effect 版对比）：
|  1. offset / checkpoint     对应 Demo 106 的 version / checkpoint 字段
|  2. processedIds 幂等去重   对应 Demo 109 UNIQUE(aggregate_id, version) 数据库约束
|  3. at-least-once 重试      对应 Demo 107 fs2 流失败后下轮重试（handleErrorWith）
|  4. Akka Projection 会把 offset 持久化到数据库，进程重启后从上次 checkpoint 恢复
|  5. cats-effect 版需要自己实现以上机制；Akka Projection 框架帮你实现了""".stripMargin)
