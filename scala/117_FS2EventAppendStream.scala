// 117_FS2EventAppendStream.scala
// 事件溯源 第二步：fs2 事件追加流
//
// 核心思想：
//   事件溯源的写侧面临两个挑战：
//     1. 并发写入冲突：同一聚合根被并发命令修改时，需要乐观锁检测版本冲突
//     2. 事件扇出：每条新事件写入后，需要通知读模型投影、Outbox 等下游
//
//   用 fs2 可以自然地：
//     - 用 Stream 串行化同一聚合根的命令，避免并发冲突
//     - 用 Topic[IO, ...] 把新事件扇出给多个下游订阅者
//     - 统计追加成功率和冲突率
//
// 本 Demo 演示：
//   - CommandEnvelope（命令 + 期望版本号）流水线处理
//   - 乐观锁：期望版本 != 当前版本 → 冲突拒绝
//   - 成功追加后通过 fs2 Topic 扇出新事件
//   - 两个下游订阅者：projection_updater 和 outbox_publisher

//> using scala "3.3.3"
//> using dep "org.typelevel::cats-effect:3.5.4"
//> using dep "co.fs2::fs2-core:3.10.2"

import cats.effect.{IO, IOApp, Ref}
import cats.syntax.all._
import fs2.Stream
import fs2.concurrent.Topic

// ── 领域模型 ──────────────────────────────────────────────────────────────────

sealed trait OrderEvent
object OrderEvent:
  case class OrderPlaced(orderId: String, sku: String, quantity: Int) extends OrderEvent
  case class PaymentReceived(orderId: String, amount: BigDecimal)      extends OrderEvent
  case class OrderCancelled(orderId: String, reason: String)           extends OrderEvent

case class VersionedEvents(
    orderId: String,
    version: Long,                  // 当前版本（事件数量）
    events:  List[OrderEvent]
)

/** 命令信封：携带期望版本用于乐观锁检测 */
case class CommandEnvelope(
    orderId:         String,
    expectedVersion: Long,          // 提交命令时读到的版本号
    event:           OrderEvent     // 要追加的新事件
)

sealed trait AppendResult
object AppendResult:
  case class Success(orderId: String, newVersion: Long) extends AppendResult
  case class VersionConflict(orderId: String, expected: Long, actual: Long) extends AppendResult

// ── 进程内事件存储（带版本号）────────────────────────────────────────────────

class VersionedEventStore(store: Ref[IO, Map[String, VersionedEvents]]):

  def append(envelope: CommandEnvelope): IO[AppendResult] =
    store.modify { m =>
      val current = m.getOrElse(
        envelope.orderId,
        VersionedEvents(envelope.orderId, 0L, Nil)
      )
      if current.version != envelope.expectedVersion then
        m -> AppendResult.VersionConflict(envelope.orderId, envelope.expectedVersion, current.version)
      else
        val updated = current.copy(
          version = current.version + 1,
          events  = current.events :+ envelope.event
        )
        m.updated(envelope.orderId, updated) ->
          AppendResult.Success(envelope.orderId, updated.version)
    }

  def load(orderId: String): IO[VersionedEvents] =
    store.get.map(_.getOrElse(orderId, VersionedEvents(orderId, 0L, Nil)))

// ── 事件追加流 ────────────────────────────────────────────────────────────────

object EventAppendStream:

  def run(
      commands:  List[CommandEnvelope],
      eventStore: VersionedEventStore,
      topic:      Topic[IO, Option[OrderEvent]]   // None = 流结束信号
  ): IO[(Int, Int)] =   // (成功数, 冲突数)
    Stream
      .emits(commands)
      .evalMap { env =>
        eventStore.append(env).flatMap {
          case AppendResult.Success(id, ver) =>
            topic.publish1(Some(env.event)) *>
              IO.println(s"  [✓] 追加成功: $id v$ver → ${env.event.getClass.getSimpleName}") *>
              IO.pure((1, 0))
          case AppendResult.VersionConflict(id, exp, act) =>
            IO.println(s"  [✗] 版本冲突: $id 期望=v$exp 实际=v$act") *>
              IO.pure((0, 1))
        }
      }
      .compile
      .fold((0, 0)) { case ((s, f), (ds, df)) => (s + ds, f + df) }

// ── 演示 ──────────────────────────────────────────────────────────────────────

object FS2EventAppendStreamDemo extends IOApp.Simple:

  def run: IO[Unit] =
    for
      storeRef <- Ref.of[IO, Map[String, VersionedEvents]](Map.empty)
      store     = VersionedEventStore(storeRef)

      _ <- IO.println("=== fs2 事件追加流：版本乐观锁 + 事件扇出 ===\n")

      // 创建 Topic，用于扇出新事件给多个下游
      topic <- Topic[IO, Option[OrderEvent]]

      // 下游 1：projection_updater（模拟读模型更新）
      projectionLog <- Ref.of[IO, List[String]](List.empty)
      // 下游 2：outbox_publisher（模拟 Outbox 写入）
      outboxLog     <- Ref.of[IO, List[String]](List.empty)

      // 启动两个下游订阅者（后台 fiber）
      projFiber <- topic.subscribeUnbounded
                     .unNoneTerminate
                     .evalMap { e =>
                       projectionLog.update(_ :+ s"projection:${e.getClass.getSimpleName}")
                     }
                     .compile.drain.start

      outboxFiber <- topic.subscribeUnbounded
                       .unNoneTerminate
                       .evalMap { e =>
                         outboxLog.update(_ :+ s"outbox:${e.getClass.getSimpleName}")
                       }
                       .compile.drain.start

      // 命令序列：包含正常追加和版本冲突场景
      commands = List(
        CommandEnvelope("o-1", 0L, OrderEvent.OrderPlaced("o-1", "SKU-A", 2)),
        CommandEnvelope("o-1", 1L, OrderEvent.PaymentReceived("o-1", BigDecimal(200))),
        CommandEnvelope("o-1", 1L, OrderEvent.PaymentReceived("o-1", BigDecimal(200))), // 版本冲突
        CommandEnvelope("o-1", 2L, OrderEvent.OrderCancelled("o-1", "改主意了")),
        CommandEnvelope("o-2", 0L, OrderEvent.OrderPlaced("o-2", "SKU-B", 1)),
        CommandEnvelope("o-2", 0L, OrderEvent.OrderPlaced("o-2", "SKU-B", 1)),          // 版本冲突
      )

      result           <- EventAppendStream.run(commands, store, topic)
      successes         = result._1
      conflicts         = result._2

      // 发送结束信号，让订阅者退出
      _ <- topic.publish1(None)
      // 给订阅者一点时间处理 None 信号
      _ <- cats.effect.IO.sleep(scala.concurrent.duration.FiniteDuration(200, "millis"))
      _ <- projFiber.cancel
      _ <- outboxFiber.cancel

      projLog  <- projectionLog.get
      outbLog  <- outboxLog.get
      o1Events <- store.load("o-1")
      o2Events <- store.load("o-2")

      _ <- IO.println(s"""
|── 追加报告 ──────────────────────────────
|  成功: $successes 条，版本冲突: $conflicts 条
|
|── o-1 事件序列（v${o1Events.version}）────────────
|${o1Events.events.zipWithIndex.map { case (e, i) => s"  [${i+1}] $e" }.mkString("\n")}
|
|── o-2 事件序列（v${o2Events.version}）────────────
|${o2Events.events.zipWithIndex.map { case (e, i) => s"  [${i+1}] $e" }.mkString("\n")}
|
|── 读模型投影订阅者收到 ────────────────────
|  ${projLog.mkString(", ")}
|
|── Outbox 发布者收到 ────────────────────────
|  ${outbLog.mkString(", ")}
|
|关键点：
|  1. 版本号乐观锁：期望版本 != 当前版本时拒绝追加，防止并发丢失更新
|  2. Topic 扇出：每条新事件可以同时推送给多个下游订阅者
|  3. 订阅者（投影、Outbox）完全解耦，只消费事件，不感知写侧逻辑
|  4. 事件日志是 append-only，一旦写入永不修改""".stripMargin)
    yield ()
