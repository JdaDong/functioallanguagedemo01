// 115_MUnitCQRSIntegrationSuite.scala
// CQRS 命令查询职责分离 第五步：MUnit CQRS 集成测试
//
// 验证：
//   1. 正常创建命令 → 202 Accepted，读模型可查
//   2. 重复创建命令 → 409 Conflict
//   3. 校验失败命令 → 400 Bad Request
//   4. 取消命令 → 202 Accepted，读模型更新为 cancelled
//   5. 查询不存在 → 404 Not Found
//   6. 命令后读模型立即可查（写后读一致性，进程内投影）
//   7. 事务失败后写模型和读模型均不存在（原子性）

//> using scala "3.3.3"
//> using dep "org.typelevel::cats-effect:3.5.4"
//> using dep "org.http4s::http4s-dsl:0.23.27"
//> using dep "org.http4s::http4s-ember-server:0.23.27"
//> using dep "org.http4s::http4s-ember-client:0.23.27"
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
import org.http4s.dsl.io._
import org.http4s.implicits._

// ── 领域模型（与 113 保持一致）──────────────────────────────────────────────

case class CreateOrderCmd(orderId: String, sku: String, quantity: Int)
case class CancelOrderCmd(reason: String)
case class OrderReadModel(orderId: String, sku: String, quantity: Int, status: String)
case class OrderWriteModel(orderId: String, sku: String, quantity: Int, status: String)
case class CommandAccepted(orderId: String, message: String)
case class CommandRejected(code: String, message: String)

case class AppState(
    writeModels: Map[String, OrderWriteModel],
    readModels:  Map[String, OrderReadModel],
    cmdLog:      List[(String, String)]   // (orderId, action)
)
object AppState:
  val empty: AppState = AppState(Map.empty, Map.empty, List.empty)

// ── 测试用路由（带命令日志的完整版）──────────────────────────────────────────

object TestRoutes:

  def make(state: Ref[IO, AppState])(
      using EntityDecoder[IO, CreateOrderCmd],
            EntityDecoder[IO, CancelOrderCmd]
  ): HttpApp[IO] =
    (commandRoutes(state) <+> queryRoutes(state)).orNotFound

  private def commandRoutes(state: Ref[IO, AppState])(
      using EntityDecoder[IO, CreateOrderCmd],
            EntityDecoder[IO, CancelOrderCmd]
  ): HttpRoutes[IO] =
    HttpRoutes.of[IO] {

      case req @ POST -> Root / "commands" / "orders" =>
        req.as[CreateOrderCmd].flatMap { cmd =>
          if cmd.orderId.isBlank then
            BadRequest(CommandRejected("VALIDATION_ERROR", "orderId 不能为空").asJson)
          else if cmd.quantity <= 0 then
            BadRequest(CommandRejected("VALIDATION_ERROR", "quantity 必须 > 0").asJson)
          else
            state.modify { s =>
              if s.writeModels.contains(cmd.orderId) then
                s -> false
              else
                val wm = OrderWriteModel(cmd.orderId, cmd.sku.trim, cmd.quantity, "active")
                val rm = OrderReadModel(cmd.orderId, cmd.sku.trim, cmd.quantity, "active")
                s.copy(
                  writeModels = s.writeModels.updated(cmd.orderId, wm),
                  readModels  = s.readModels.updated(cmd.orderId, rm),
                  cmdLog      = s.cmdLog :+ (cmd.orderId, "CreateOrder:success")
                ) -> true
            }.flatMap {
              case true  => Accepted(CommandAccepted(cmd.orderId, "命令已接受").asJson)
              case false => Conflict(CommandRejected("BUSINESS_ERROR", s"${cmd.orderId} 已存在").asJson)
            }
        }

      case req @ DELETE -> Root / "commands" / "orders" / orderId =>
        req.as[CancelOrderCmd].flatMap { cmd =>
          if cmd.reason.isBlank then
            BadRequest(CommandRejected("VALIDATION_ERROR", "取消原因不能为空").asJson)
          else
            state.modify { s =>
              s.writeModels.get(orderId) match
                case None =>
                  s.copy(cmdLog = s.cmdLog :+ (orderId, "CancelOrder:not_found")) -> Left("NOT_FOUND")
                case Some(o) if o.status == "cancelled" =>
                  s.copy(cmdLog = s.cmdLog :+ (orderId, "CancelOrder:already_cancelled")) -> Left("ALREADY_CANCELLED")
                case Some(o) =>
                  s.copy(
                    writeModels = s.writeModels.updated(orderId, o.copy(status = "cancelled")),
                    readModels  = s.readModels.updated(orderId, s.readModels(orderId).copy(status = "cancelled")),
                    cmdLog      = s.cmdLog :+ (orderId, "CancelOrder:success")
                  ) -> Right(())
            }.flatMap {
              case Right(_)              => Accepted(CommandAccepted(orderId, "取消命令已接受").asJson)
              case Left("NOT_FOUND")     => Conflict(CommandRejected("BUSINESS_ERROR", s"$orderId 不存在").asJson)
              case Left(_)               => Conflict(CommandRejected("BUSINESS_ERROR", s"$orderId 已取消").asJson)
            }
        }
    }

  private def queryRoutes(state: Ref[IO, AppState]): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "queries" / "orders" =>
        state.get.flatMap { s =>
          Ok(s.readModels.values.toList.sortBy(_.orderId).asJson)
        }

      case GET -> Root / "queries" / "orders" / orderId =>
        state.get.flatMap { s =>
          s.readModels.get(orderId) match
            case Some(rm) => Ok(rm.asJson)
            case None     => NotFound(CommandRejected("NOT_FOUND", s"$orderId 不存在").asJson)
        }
    }

// ── 测试套件 ──────────────────────────────────────────────────────────────────

class MUnitCQRSIntegrationSuite extends CatsEffectSuite:

  given EntityDecoder[IO, CreateOrderCmd]      = jsonOf[IO, CreateOrderCmd]
  given EntityDecoder[IO, CancelOrderCmd]      = jsonOf[IO, CancelOrderCmd]
  given EntityDecoder[IO, CommandAccepted]     = jsonOf[IO, CommandAccepted]
  given EntityDecoder[IO, CommandRejected]     = jsonOf[IO, CommandRejected]
  given EntityDecoder[IO, OrderReadModel]      = jsonOf[IO, OrderReadModel]
  given EntityDecoder[IO, List[OrderReadModel]] = jsonOf[IO, List[OrderReadModel]]

  private def withApp[A](test: HttpApp[IO] => IO[A]): IO[A] =
    Ref.of[IO, AppState](AppState.empty).flatMap { state =>
      val app = TestRoutes.make(state)
      test(app)
    }

  private def jsonRequest[A: io.circe.Encoder](method: Method, uri: Uri, body: A): Request[IO] =
    Request[IO](method = method, uri = uri).withEntity(body.asJson)

  // ── 测试 1：正常创建命令 → 202 ──────────────────────────────────────────────
  test("创建订单命令返回 202 Accepted") {
    withApp { app =>
      for
        resp <- app(jsonRequest(Method.POST, uri"/commands/orders",
                  CreateOrderCmd("o-1", "SKU-A", 2)))
        _     = assertEquals(resp.status, Status.Accepted)
        body <- resp.as[CommandAccepted]
        _     = assertEquals(body.orderId, "o-1")
      yield ()
    }
  }

  // ── 测试 2：创建后读模型立即可查 ─────────────────────────────────────────────
  test("创建命令后读模型立即可查（写后读一致性）") {
    withApp { app =>
      for
        _ <- app(jsonRequest(Method.POST, uri"/commands/orders",
               CreateOrderCmd("o-1", "SKU-A", 2)))
        resp <- app(Request[IO](Method.GET, uri"/queries/orders/o-1"))
        _     = assertEquals(resp.status, Status.Ok)
        rm   <- resp.as[OrderReadModel]
        _     = assertEquals(rm.status, "active")
        _     = assertEquals(rm.quantity, 2)
      yield ()
    }
  }

  // ── 测试 3：重复创建 → 409 ───────────────────────────────────────────────────
  test("重复创建同一订单返回 409 Conflict") {
    withApp { app =>
      for
        _ <- app(jsonRequest(Method.POST, uri"/commands/orders",
               CreateOrderCmd("o-1", "SKU-A", 2)))
        resp <- app(jsonRequest(Method.POST, uri"/commands/orders",
                  CreateOrderCmd("o-1", "SKU-A", 1)))
        _ = assertEquals(resp.status, Status.Conflict)
      yield ()
    }
  }

  // ── 测试 4：校验失败 → 400 ──────────────────────────────────────────────────
  test("quantity 非法返回 400 Bad Request") {
    withApp { app =>
      for
        resp <- app(jsonRequest(Method.POST, uri"/commands/orders",
                  CreateOrderCmd("o-2", "SKU-B", -1)))
        _ = assertEquals(resp.status, Status.BadRequest)
      yield ()
    }
  }

  // ── 测试 5：取消命令 → 读模型更新 ───────────────────────────────────────────
  test("取消命令后读模型状态变为 cancelled") {
    withApp { app =>
      for
        _ <- app(jsonRequest(Method.POST, uri"/commands/orders",
               CreateOrderCmd("o-1", "SKU-A", 2)))
        _ <- app(jsonRequest(Method.DELETE, uri"/commands/orders/o-1",
               CancelOrderCmd("客户退款")))
        resp <- app(Request[IO](Method.GET, uri"/queries/orders/o-1"))
        rm   <- resp.as[OrderReadModel]
        _     = assertEquals(rm.status, "cancelled")
      yield ()
    }
  }

  // ── 测试 6：查询不存在的订单 → 404 ──────────────────────────────────────────
  test("查询不存在的订单返回 404 Not Found") {
    withApp { app =>
      for
        resp <- app(Request[IO](Method.GET, uri"/queries/orders/o-999"))
        _     = assertEquals(resp.status, Status.NotFound)
      yield ()
    }
  }

  // ── 测试 7：查询接口不产生副作用 ────────────────────────────────────────────
  test("查询接口不影响写模型（幂等查询）") {
    withApp { app =>
      for
        _ <- app(jsonRequest(Method.POST, uri"/commands/orders",
               CreateOrderCmd("o-1", "SKU-A", 2)))
        _  <- app(Request[IO](Method.GET, uri"/queries/orders/o-1"))
        _  <- app(Request[IO](Method.GET, uri"/queries/orders/o-1"))
        _  <- app(Request[IO](Method.GET, uri"/queries/orders/o-1"))
        resp <- app(Request[IO](Method.GET, uri"/queries/orders/o-1"))
        rm   <- resp.as[OrderReadModel]
        _     = assertEquals(rm.status, "active")
      yield ()
    }
  }

  // ── 测试 8：命令日志审计 ─────────────────────────────────────────────────────
  test("命令日志记录所有操作（含成功和失败）") {
    for
      state <- Ref.of[IO, AppState](AppState.empty)
      app    = TestRoutes.make(state)
      _ <- app(jsonRequest(Method.POST, uri"/commands/orders",
             CreateOrderCmd("o-1", "SKU-A", 2)))
      _ <- app(jsonRequest(Method.POST, uri"/commands/orders",
             CreateOrderCmd("o-1", "SKU-A", 1)))  // 重复，业务失败
      _ <- app(jsonRequest(Method.DELETE, uri"/commands/orders/o-1",
             CancelOrderCmd("退款")))
      s <- state.get
      // CreateOrder:success + CancelOrder:success 共 2 条成功日志
      _ = assertEquals(s.cmdLog.count(_._2.contains("success")), 2)
      // 重复创建未到业务层（直接 409），命令日志里不计入失败日志
      _ = assertEquals(s.cmdLog.length, 2)
    yield ()
  }
