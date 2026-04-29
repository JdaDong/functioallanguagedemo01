// 111_CatsEffectCommandBus.scala
// CQRS 命令查询职责分离 第一步：cats-effect 命令总线
//
// 核心思想：
//   CQRS = Command Query Responsibility Segregation
//   写侧：Command → CommandBus → CommandHandler → Event → WriteModel
//   读侧：Query → QueryHandler → ReadModel
//
// 本 Demo 聚焦写侧最小模型：
//   1. Command 类型：CreateOrder / CancelOrder
//   2. CommandBus：接收命令，校验，分发给对应 Handler
//   3. CommandHandler：执行业务逻辑，产生 DomainEvent，写入写模型
//   4. 演示成功、校验失败和业务失败三条路径
//
// 与前面阶段的关系：
//   - Outbox (91-95)：写侧事件可靠发布
//   - Inbox  (96-100)：消费端幂等接收
//   - Saga   (101-105)：跨步骤补偿编排
//   - 投影   (106-110)：查询侧 checkpoint 追赶
//   - CQRS   (111-115)：把写侧和读侧显式分开成两条独立路径

//> using scala "3.3.3"
//> using dep "org.typelevel::cats-effect:3.5.4"

import cats.effect.{IO, IOApp, Ref}
import cats.syntax.all._

// ── 领域模型 ────────────────────────────────────────────────────────────────

enum Command:
  case CreateOrder(orderId: String, sku: String, quantity: Int)
  case CancelOrder(orderId: String, reason: String)

enum DomainEvent:
  case OrderCreated(orderId: String, sku: String, quantity: Int)
  case OrderCancelled(orderId: String, reason: String)

case class OrderWriteModel(
    orderId: String,
    sku: String,
    quantity: Int,
    status: String     // "active" | "cancelled"
)

sealed trait CommandError
object CommandError:
  case class ValidationError(msg: String) extends CommandError
  case class BusinessError(msg: String)   extends CommandError

type CommandResult = Either[CommandError, DomainEvent]

// ── 命令处理器 ────────────────────────────────────────────────────────────────

/** 命令处理器特质：每个 Handler 只处理一种命令，产生一个 DomainEvent。*/
trait CommandHandler[C <: Command]:
  def handle(cmd: C, store: Ref[IO, Map[String, OrderWriteModel]]): IO[CommandResult]

object CreateOrderHandler extends CommandHandler[Command.CreateOrder]:
  def handle(
      cmd: Command.CreateOrder,
      store: Ref[IO, Map[String, OrderWriteModel]]
  ): IO[CommandResult] =
    // 校验
    if cmd.orderId.isBlank then
      IO.pure(Left(CommandError.ValidationError("orderId 不能为空")))
    else if cmd.quantity <= 0 then
      IO.pure(Left(CommandError.ValidationError("quantity 必须大于 0")))
    else
      store.modify { orders =>
        if orders.contains(cmd.orderId) then
          orders -> Left(CommandError.BusinessError(s"订单 ${cmd.orderId} 已存在"))
        else
          val model = OrderWriteModel(cmd.orderId, cmd.sku.trim, cmd.quantity, "active")
          orders.updated(cmd.orderId, model) ->
            Right(DomainEvent.OrderCreated(cmd.orderId, cmd.sku.trim, cmd.quantity))
      }

object CancelOrderHandler extends CommandHandler[Command.CancelOrder]:
  def handle(
      cmd: Command.CancelOrder,
      store: Ref[IO, Map[String, OrderWriteModel]]
  ): IO[CommandResult] =
    if cmd.reason.isBlank then
      IO.pure(Left(CommandError.ValidationError("取消原因不能为空")))
    else
      store.modify { orders =>
        orders.get(cmd.orderId) match
          case None =>
            orders -> Left(CommandError.BusinessError(s"订单 ${cmd.orderId} 不存在"))
          case Some(o) if o.status == "cancelled" =>
            orders -> Left(CommandError.BusinessError(s"订单 ${cmd.orderId} 已经取消"))
          case Some(o) =>
            orders.updated(cmd.orderId, o.copy(status = "cancelled")) ->
              Right(DomainEvent.OrderCancelled(cmd.orderId, cmd.reason))
      }

// ── 命令总线 ──────────────────────────────────────────────────────────────────

/** CommandBus：按命令类型分发给对应 Handler。
  * 注意：CQRS 的核心约定是"命令总线只处理写操作，不返回读模型"。
  * 这里返回 DomainEvent 是为了让调用方知道"发生了什么"，而不是"当前状态是什么"。
  */
class CommandBus(store: Ref[IO, Map[String, OrderWriteModel]]):
  def dispatch(cmd: Command): IO[CommandResult] =
    cmd match
      case c: Command.CreateOrder => CreateOrderHandler.handle(c, store)
      case c: Command.CancelOrder => CancelOrderHandler.handle(c, store)

// ── 演示 ──────────────────────────────────────────────────────────────────────

object CatsEffectCommandBusDemo extends IOApp.Simple:

  private def printResult(label: String, result: CommandResult): IO[Unit] =
    result match
      case Right(event) => IO.println(s"[✓] $label → $event")
      case Left(err)    => IO.println(s"[✗] $label → $err")

  def run: IO[Unit] =
    for
      store <- Ref.of[IO, Map[String, OrderWriteModel]](Map.empty)
      bus    = CommandBus(store)

      _ <- IO.println("=== 命令总线：写侧 CQRS 最小模型 ===\n")

      // 正常创建
      r1 <- bus.dispatch(Command.CreateOrder("order-001", "SKU-A", 3))
      _  <- printResult("创建订单 order-001", r1)

      // 重复创建 → 业务错误
      r2 <- bus.dispatch(Command.CreateOrder("order-001", "SKU-A", 1))
      _  <- printResult("重复创建 order-001", r2)

      // 校验失败：数量 <= 0
      r3 <- bus.dispatch(Command.CreateOrder("order-002", "SKU-B", -1))
      _  <- printResult("数量非法 order-002", r3)

      // 正常取消
      r4 <- bus.dispatch(Command.CancelOrder("order-001", "客户申请退款"))
      _  <- printResult("取消订单 order-001", r4)

      // 重复取消 → 业务错误
      r5 <- bus.dispatch(Command.CancelOrder("order-001", "再次取消"))
      _  <- printResult("重复取消 order-001", r5)

      // 取消不存在的订单 → 业务错误
      r6 <- bus.dispatch(Command.CancelOrder("order-999", "取消幽灵订单"))
      _  <- printResult("取消不存在 order-999", r6)

      // 取消原因为空 → 校验失败
      r7 <- bus.dispatch(Command.CancelOrder("order-001", ""))
      _  <- printResult("取消原因为空 order-001", r7)

      orders <- store.get
      _      <- IO.println(s"\n[写模型当前状态] ${orders.values.toList}")

      _ <- IO.println("""
|关键点：
|  1. CommandBus 只路由命令，不包含业务逻辑
|  2. CommandHandler 只关注写侧副作用，不返回读模型
|  3. 产生 DomainEvent 是 CQRS 写侧的最小输出语义
|  4. 校验失败 vs 业务失败 明确区分，方便上层决定 HTTP 状态码
|  5. 读侧（读模型查询）完全分离，由独立 QueryHandler 负责""".stripMargin)
    yield ()
