// 112_FS2CommandRouterStream.scala
// CQRS 命令查询职责分离 第二步：fs2 命令路由流
//
// 核心思想：
//   真实系统里命令往往是批量到达的（消息队列、HTTP 并发、事件源流入）。
//   用 fs2 流来处理命令可以自然地：
//     1. 按命令类型并行路由
//     2. 对失败命令累积到"死信队列"（DLQ），而不是直接丢弃
//     3. 对成功命令产生 DomainEvent 并发布到下游
//     4. 实时追踪处理进度（成功数 / 失败数 / DLQ 积压）
//
// 本 Demo 演示：
//   - Stream[IO, Command] 并行处理，evalMap → CommandBus.dispatch
//   - 失败命令写入 Ref[IO, List[FailedCommand]] 作为 DLQ
//   - 成功命令的 DomainEvent 写入 Ref[IO, List[DomainEvent]] 作为已发布队列
//   - 流结束后打印统计报告

//> using scala "3.3.3"
//> using dep "org.typelevel::cats-effect:3.5.4"
//> using dep "co.fs2::fs2-core:3.10.2"

import cats.effect.{IO, IOApp, Ref}
import cats.syntax.all._
import fs2.Stream

// ── 领域模型（与 111 保持一致，内联在本文件中方便独立运行）────────────────

enum Command:
  case CreateOrder(orderId: String, sku: String, quantity: Int)
  case CancelOrder(orderId: String, reason: String)

enum DomainEvent:
  case OrderCreated(orderId: String, sku: String, quantity: Int)
  case OrderCancelled(orderId: String, reason: String)

case class OrderWriteModel(orderId: String, sku: String, quantity: Int, status: String)

sealed trait CommandError
object CommandError:
  case class ValidationError(msg: String) extends CommandError
  case class BusinessError(msg: String)   extends CommandError

type CommandResult = Either[CommandError, DomainEvent]

// ── DLQ 记录 ──────────────────────────────────────────────────────────────────

case class FailedCommand(cmd: Command, error: CommandError, attempt: Int = 1)

// ── 命令总线（内联简化版）────────────────────────────────────────────────────

object CommandBus:
  def dispatch(
      cmd: Command,
      store: Ref[IO, Map[String, OrderWriteModel]]
  ): IO[CommandResult] =
    cmd match
      case Command.CreateOrder(id, sku, qty) =>
        if id.isBlank then IO.pure(Left(CommandError.ValidationError("orderId 不能为空")))
        else if qty <= 0 then IO.pure(Left(CommandError.ValidationError("quantity 必须 > 0")))
        else
          store.modify { orders =>
            if orders.contains(id) then
              orders -> Left(CommandError.BusinessError(s"$id 已存在"))
            else
              orders.updated(id, OrderWriteModel(id, sku.trim, qty, "active")) ->
                Right(DomainEvent.OrderCreated(id, sku.trim, qty))
          }

      case Command.CancelOrder(id, reason) =>
        if reason.isBlank then IO.pure(Left(CommandError.ValidationError("取消原因不能为空")))
        else
          store.modify { orders =>
            orders.get(id) match
              case None    => orders -> Left(CommandError.BusinessError(s"$id 不存在"))
              case Some(o) if o.status == "cancelled" =>
                orders -> Left(CommandError.BusinessError(s"$id 已取消"))
              case Some(o) =>
                orders.updated(id, o.copy(status = "cancelled")) ->
                  Right(DomainEvent.OrderCancelled(id, reason))
          }

// ── 命令路由流 ────────────────────────────────────────────────────────────────

object CommandRouterStream:

  def run(
      commands: List[Command],
      store: Ref[IO, Map[String, OrderWriteModel]],
      dlq:      Ref[IO, List[FailedCommand]],
      published: Ref[IO, List[DomainEvent]]
  ): IO[Unit] =
    Stream
      .emits(commands)
      .evalMap { cmd =>
        CommandBus
          .dispatch(cmd, store)
          .flatMap {
            case Right(event) =>
              published.update(_ :+ event) *>
                IO.println(s"  [✓] ${cmd.getClass.getSimpleName} → $event")
            case Left(err) =>
              dlq.update(_ :+ FailedCommand(cmd, err)) *>
                IO.println(s"  [✗] ${cmd.getClass.getSimpleName} → DLQ: $err")
          }
      }
      .compile
      .drain

// ── 演示 ──────────────────────────────────────────────────────────────────────

object FS2CommandRouterStreamDemo extends IOApp.Simple:

  val commands: List[Command] = List(
    Command.CreateOrder("o-1", "SKU-A", 2),
    Command.CreateOrder("o-2", "SKU-B", 5),
    Command.CreateOrder("o-1", "SKU-A", 1),   // 重复 → 业务失败 → DLQ
    Command.CreateOrder("o-3", "SKU-C", -1),  // 校验失败 → DLQ
    Command.CancelOrder("o-2", "客户取消"),
    Command.CancelOrder("o-2", "重复取消"),   // 已取消 → DLQ
    Command.CancelOrder("o-999", "幽灵订单"), // 不存在 → DLQ
    Command.CreateOrder("", "SKU-D", 3),       // 空 ID → DLQ
  )

  def run: IO[Unit] =
    for
      store     <- Ref.of[IO, Map[String, OrderWriteModel]](Map.empty)
      dlq       <- Ref.of[IO, List[FailedCommand]](List.empty)
      published <- Ref.of[IO, List[DomainEvent]](List.empty)

      _ <- IO.println(s"=== fs2 命令路由流：处理 ${commands.length} 条命令 ===\n")
      _ <- CommandRouterStream.run(commands, store, dlq, published)

      dlqItems <- dlq.get
      events   <- published.get
      orders   <- store.get

      _ <- IO.println(s"""
|── 处理报告 ────────────────────────────────
|  成功发布事件: ${events.length} 条
|  进入 DLQ:     ${dlqItems.length} 条
|
|── 写模型状态 ──────────────────────────────
|${orders.values.map(o => s"  ${o.orderId}: ${o.sku} ×${o.quantity} [${o.status}]").mkString("\n")}
|
|── DLQ 积压 ────────────────────────────────
|${dlqItems.zipWithIndex.map { case (f, i) => s"  [${i+1}] ${f.cmd} → ${f.error}" }.mkString("\n")}
|
|关键点：
|  1. fs2 Stream 让批量命令自然串行或并行处理
|  2. 失败命令写入 DLQ 而不是直接丢弃，保留重试机会
|  3. 成功命令对应的 DomainEvent 发布到下游（此处用 Ref 模拟）
|  4. 流结束后可统计 lag / 成功率，给监控系统提供依据""".stripMargin)
    yield ()
