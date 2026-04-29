// 113_Http4sCQRSBoundary.scala
// CQRS 命令查询职责分离 第三步：http4s 命令/查询双边界
//
// 核心思想：
//   CQRS 在 HTTP 层的体现：
//     写侧：POST /commands/orders        → 接收命令，返回 202 Accepted（不返回最新读模型）
//           DELETE /commands/orders/{id} → 取消订单命令
//     读侧：GET /queries/orders          → 读取读模型列表（不触发任何写操作）
//           GET /queries/orders/{id}     → 读取单条读模型
//
//   关键约定：
//     - 命令接口不返回最新状态，只返回"命令已接受/已拒绝"
//     - 查询接口永远不产生副作用
//     - 写侧和读侧用不同的路由前缀，职责一目了然
//
// 本 Demo 演示：
//   - /commands/* 路由：写侧，返回 202/400/409
//   - /queries/*  路由：读侧，返回 200/404
//   - 进程内演示完整的命令流转：提交命令 → 查询读模型

//> using scala "3.3.3"
//> using dep "org.typelevel::cats-effect:3.5.4"
//> using dep "org.http4s::http4s-dsl:0.23.27"
//> using dep "org.http4s::http4s-ember-server:0.23.27"
//> using dep "org.http4s::http4s-ember-client:0.23.27"
//> using dep "org.http4s::http4s-circe:0.23.27"
//> using dep "io.circe::circe-generic:0.14.9"

import cats.effect.{IO, IOApp, Ref}
import cats.syntax.all._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.client.Client

// ── 领域模型 ──────────────────────────────────────────────────────────────────

case class CreateOrderCmd(orderId: String, sku: String, quantity: Int)
case class CancelOrderCmd(reason: String)

case class OrderWriteModel(orderId: String, sku: String, quantity: Int, status: String)
case class OrderReadModel(orderId: String, sku: String, quantity: Int, status: String)

case class CommandAccepted(orderId: String, message: String)
case class CommandRejected(code: String, message: String)

// ── 状态（写模型 + 读模型，进程内同步更新模拟投影）──────────────────────────

case class AppState(
    writeModels: Map[String, OrderWriteModel],
    readModels:  Map[String, OrderReadModel]
)
object AppState:
  val empty: AppState = AppState(Map.empty, Map.empty)

// ── 写侧命令路由 /commands/* ──────────────────────────────────────────────────

def commandRoutes(state: Ref[IO, AppState])(using EntityDecoder[IO, CreateOrderCmd],
                                                  EntityDecoder[IO, CancelOrderCmd]): HttpRoutes[IO] =
  HttpRoutes.of[IO] {

    // 创建订单命令
    case req @ POST -> Root / "commands" / "orders" =>
      req.as[CreateOrderCmd].flatMap { cmd =>
        if cmd.orderId.isBlank then
          BadRequest(CommandRejected("VALIDATION_ERROR", "orderId 不能为空").asJson)
        else if cmd.quantity <= 0 then
          BadRequest(CommandRejected("VALIDATION_ERROR", "quantity 必须 > 0").asJson)
        else
          state.modify { s =>
            if s.writeModels.contains(cmd.orderId) then
              s -> Left(s"订单 ${cmd.orderId} 已存在")
            else
              val wm = OrderWriteModel(cmd.orderId, cmd.sku.trim, cmd.quantity, "active")
              val rm = OrderReadModel(cmd.orderId, cmd.sku.trim, cmd.quantity, "active")
              s.copy(
                writeModels = s.writeModels.updated(cmd.orderId, wm),
                readModels  = s.readModels.updated(cmd.orderId, rm)
              ) -> Right(cmd.orderId)
          }.flatMap {
            // 202 Accepted：命令已接受，不返回最新状态
            case Right(id) => Accepted(CommandAccepted(id, "订单创建命令已接受").asJson)
            case Left(err) => Conflict(CommandRejected("BUSINESS_ERROR", err).asJson)
          }
      }

    // 取消订单命令
    case req @ DELETE -> Root / "commands" / "orders" / orderId =>
      req.as[CancelOrderCmd].flatMap { cmd =>
        if cmd.reason.isBlank then
          BadRequest(CommandRejected("VALIDATION_ERROR", "取消原因不能为空").asJson)
        else
          state.modify { s =>
            s.writeModels.get(orderId) match
              case None =>
                s -> Left(s"订单 $orderId 不存在")
              case Some(o) if o.status == "cancelled" =>
                s -> Left(s"订单 $orderId 已经取消")
              case Some(o) =>
                val wm = o.copy(status = "cancelled")
                val rm = s.readModels(orderId).copy(status = "cancelled")
                s.copy(
                  writeModels = s.writeModels.updated(orderId, wm),
                  readModels  = s.readModels.updated(orderId, rm)
                ) -> Right(orderId)
          }.flatMap {
            case Right(id) => Accepted(CommandAccepted(id, "订单取消命令已接受").asJson)
            case Left(err) => Conflict(CommandRejected("BUSINESS_ERROR", err).asJson)
          }
      }
  }

// ── 读侧查询路由 /queries/* ───────────────────────────────────────────────────

def queryRoutes(state: Ref[IO, AppState]): HttpRoutes[IO] =
  HttpRoutes.of[IO] {

    // 查询所有订单读模型
    case GET -> Root / "queries" / "orders" =>
      state.get.flatMap { s =>
        Ok(s.readModels.values.toList.sortBy(_.orderId).asJson)
      }

    // 查询单条订单读模型
    case GET -> Root / "queries" / "orders" / orderId =>
      state.get.flatMap { s =>
        s.readModels.get(orderId) match
          case Some(rm) => Ok(rm.asJson)
          case None     => NotFound(CommandRejected("NOT_FOUND", s"订单 $orderId 不存在").asJson)
      }
  }

// ── 演示 ──────────────────────────────────────────────────────────────────────

object Http4sCQRSBoundaryDemo extends IOApp.Simple:

  given EntityDecoder[IO, CreateOrderCmd] = jsonOf[IO, CreateOrderCmd]
  given EntityDecoder[IO, CancelOrderCmd] = jsonOf[IO, CancelOrderCmd]

  private def jsonRequest[A: io.circe.Encoder](method: Method, uri: Uri, body: A): Request[IO] =
    Request[IO](method = method, uri = uri)
      .withEntity(body.asJson)

  def run: IO[Unit] =
    for
      state  <- Ref.of[IO, AppState](AppState.empty)
      app     = (commandRoutes(state) <+> queryRoutes(state)).orNotFound

      _ <- IO.println("=== http4s CQRS 双边界：命令 /commands vs 查询 /queries ===\n")

      // 写侧：创建命令
      r1 <- app(jsonRequest(Method.POST, uri"/commands/orders",
                  CreateOrderCmd("o-1", "SKU-A", 2)))
      b1 <- r1.as[String]
      _  <- IO.println(s"[写侧] POST /commands/orders → ${r1.status.code}: $b1")

      r2 <- app(jsonRequest(Method.POST, uri"/commands/orders",
                  CreateOrderCmd("o-2", "SKU-B", 5)))
      b2 <- r2.as[String]
      _  <- IO.println(s"[写侧] POST /commands/orders → ${r2.status.code}: $b2")

      // 写侧：重复创建 → 409
      r3 <- app(jsonRequest(Method.POST, uri"/commands/orders",
                  CreateOrderCmd("o-1", "SKU-A", 1)))
      b3 <- r3.as[String]
      _  <- IO.println(s"[写侧] POST /commands/orders (重复) → ${r3.status.code}: $b3")

      // 读侧：查询所有（此时 o-1, o-2 均为 active）
      r4 <- app(Request[IO](Method.GET, uri"/queries/orders"))
      b4 <- r4.as[String]
      _  <- IO.println(s"\n[读侧] GET /queries/orders → ${r4.status.code}: $b4")

      // 写侧：取消 o-1
      r5 <- app(jsonRequest(Method.DELETE, uri"/commands/orders/o-1",
                  CancelOrderCmd("客户申请退款")))
      b5 <- r5.as[String]
      _  <- IO.println(s"\n[写侧] DELETE /commands/orders/o-1 → ${r5.status.code}: $b5")

      // 读侧：查询 o-1（应为 cancelled）
      r6 <- app(Request[IO](Method.GET, uri"/queries/orders/o-1"))
      b6 <- r6.as[String]
      _  <- IO.println(s"[读侧] GET /queries/orders/o-1 → ${r6.status.code}: $b6")

      // 读侧：查询不存在 → 404
      r7 <- app(Request[IO](Method.GET, uri"/queries/orders/o-999"))
      b7 <- r7.as[String]
      _  <- IO.println(s"[读侧] GET /queries/orders/o-999 → ${r7.status.code}: $b7")

      _ <- IO.println("""
|关键点：
|  1. /commands/* 只接收写操作，202 Accepted 不返回最新读模型
|  2. /queries/*  只返回读模型，永远不产生任何副作用
|  3. 写侧返回"命令已接受"而不是"当前状态"，是 CQRS 的核心 HTTP 语义
|  4. 两条路由前缀分离让监控、限流、缓存策略可以分别优化""".stripMargin)
    yield ()
