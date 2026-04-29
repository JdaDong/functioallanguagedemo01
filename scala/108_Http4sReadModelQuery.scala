//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.http4s::http4s-core:0.23.33"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.http4s::http4s-circe:0.23.33"
//> using dep "io.circe::circe-generic:0.14.10"

/**
 * Scala 函数式编程 Demo 108: http4s 读模型查询边界
 *
 * 107 号 Demo 已经把后台 replay 流跑起来了，
 * 这一版继续把查询侧真正推进到 HTTP 边界：
 * 事件写入、投影 catch-up、状态查看，以及管理员触发重建。
 */
import cats.effect.{IO, IOApp, Ref}
import cats.syntax.all._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.typelevel.ci._

object Http4sReadModelQuery extends IOApp.Simple {

  final case class AppendEventRequest(
      eventType: String,
      orderId: String,
      sku: Option[String],
      quantity: Option[Int],
      amount: Option[BigDecimal]
  )
  final case class EventAccepted(seq: Long, totalEvents: Int)
  final case class ReplayRequest(fromOffset: Long)
  final case class ProjectionResult(appliedSeqs: List[Long], checkpoint: Long, lag: Long)
  final case class ProjectionStatus(checkpoint: Long, totalEvents: Int, lag: Long)
  final case class OrderReadModel(
      orderId: String,
      sku: String,
      quantity: Int,
      paymentStatus: String,
      totalPaid: BigDecimal
  )
  final case class ErrorResponse(error: String)

  final case class DomainEvent(
      seq: Long,
      eventType: String,
      orderId: String,
      sku: Option[String],
      quantity: Option[Int],
      amount: Option[BigDecimal]
  )

  final case class ProjectionState(
      nextSeq: Long,
      checkpoint: Long,
      events: Vector[DomainEvent],
      orders: Map[String, OrderReadModel]
  )

  implicit val appendDecoder: EntityDecoder[IO, AppendEventRequest] = jsonOf[IO, AppendEventRequest]
  implicit val eventAcceptedEncoder: EntityEncoder[IO, EventAccepted] = jsonEncoderOf[IO, EventAccepted]
  implicit val replayDecoder: EntityDecoder[IO, ReplayRequest] = jsonOf[IO, ReplayRequest]
  implicit val projectionResultEncoder: EntityEncoder[IO, ProjectionResult] = jsonEncoderOf[IO, ProjectionResult]
  implicit val projectionStatusEncoder: EntityEncoder[IO, ProjectionStatus] = jsonEncoderOf[IO, ProjectionStatus]
  implicit val orderReadModelEncoder: EntityEncoder[IO, OrderReadModel] = jsonEncoderOf[IO, OrderReadModel]
  implicit val errorEncoder: EntityEncoder[IO, ErrorResponse] = jsonEncoderOf[IO, ErrorResponse]

  final class ReadModelService private (state: Ref[IO, ProjectionState]) {

    def appendEvent(request: AppendEventRequest): IO[EventAccepted] =
      validateRequest(request) *>
        state.modify { current =>
          val event = DomainEvent(
            seq = current.nextSeq,
            eventType = request.eventType,
            orderId = request.orderId,
            sku = request.sku.map(_.trim),
            quantity = request.quantity,
            amount = request.amount
          )
          val updated = current.copy(nextSeq = current.nextSeq + 1, events = current.events :+ event)
          updated -> EventAccepted(seq = event.seq, totalEvents = updated.events.size)
        }.flatTap(result => IO.println(s"[event-log] 已写入事件: seq=${result.seq}"))

    def catchUp: IO[ProjectionResult] =
      processPending(resetBeforeRun = false)

    def replayFromZero: IO[ProjectionResult] =
      processPending(resetBeforeRun = true)

    def status: IO[ProjectionStatus] =
      state.get.map { current =>
        ProjectionStatus(
          checkpoint = current.checkpoint,
          totalEvents = current.events.size,
          lag = current.events.count(_.seq > current.checkpoint)
        )
      }

    def view(orderId: String): IO[Option[OrderReadModel]] =
      state.get.map(_.orders.get(orderId))

    private def processPending(resetBeforeRun: Boolean): IO[ProjectionResult] = {
      val prepare =
        if (resetBeforeRun) {
          state.update(current => current.copy(checkpoint = 0L, orders = Map.empty)) *>
            IO.println("[projection] 已重置读模型，开始从 offset=0 重放")
        } else {
          IO.unit
        }

      prepare *> loop(List.empty)
    }

    private def loop(applied: List[Long]): IO[ProjectionResult] =
      nextPending.flatMap {
        case None =>
          state.get.map { current =>
            ProjectionResult(applied.reverse, checkpoint = current.checkpoint, lag = current.events.count(_.seq > current.checkpoint))
          }

        case Some(event) =>
          applyEvent(event) *> loop(event.seq :: applied)
      }

    private def nextPending: IO[Option[DomainEvent]] =
      state.get.map(current => current.events.find(_.seq > current.checkpoint))

    private def applyEvent(event: DomainEvent): IO[Unit] =
      state.update { current =>
        val updatedOrders = event.eventType match {
          case "OrderCreated" =>
            current.orders.updated(
              event.orderId,
              OrderReadModel(
                orderId = event.orderId,
                sku = event.sku.getOrElse("unknown"),
                quantity = event.quantity.getOrElse(0),
                paymentStatus = "awaiting-payment",
                totalPaid = BigDecimal(0)
              )
            )

          case "PaymentCaptured" =>
            val existing = current.orders.getOrElse(
              event.orderId,
              throw new IllegalStateException(s"读模型缺少订单事件: orderId=${event.orderId}")
            )
            current.orders.updated(
              event.orderId,
              existing.copy(
                paymentStatus = "paid",
                totalPaid = existing.totalPaid + event.amount.getOrElse(BigDecimal(0))
              )
            )

          case other =>
            throw new IllegalArgumentException(s"不支持的事件类型: $other")
        }

        current.copy(checkpoint = event.seq, orders = updatedOrders)
      } *> IO.println(s"[projection] 已推进到 seq=${event.seq}, orderId=${event.orderId}")

    private def validateRequest(request: AppendEventRequest): IO[Unit] =
      request.eventType match {
        case "OrderCreated" if request.sku.isDefined && request.quantity.isDefined => IO.unit
        case "PaymentCaptured" if request.amount.isDefined                         => IO.unit
        case "OrderCreated"                                                        => IO.raiseError(new RuntimeException("OrderCreated 需要 sku 和 quantity"))
        case "PaymentCaptured"                                                     => IO.raiseError(new RuntimeException("PaymentCaptured 需要 amount"))
        case other                                                                  => IO.raiseError(new RuntimeException(s"不支持的事件类型: $other"))
      }
  }

  object ReadModelService {
    def create: IO[ReadModelService] =
      Ref.of[IO, ProjectionState](ProjectionState(1L, 0L, Vector.empty, Map.empty)).map(new ReadModelService(_))
  }

  def buildApp(service: ReadModelService): HttpApp[IO] =
    HttpRoutes.of[IO] {
      case req @ POST -> Root / "events" / "orders" =>
        req.as[AppendEventRequest].flatMap(body =>
          service.appendEvent(body).flatMap(Accepted(_)).handleErrorWith(error => BadRequest(ErrorResponse(error.getMessage)))
        )

      case POST -> Root / "admin" / "projections" / "catch-up" =>
        service.catchUp.flatMap(Ok(_)).handleErrorWith(error => BadRequest(ErrorResponse(error.getMessage)))

      case req @ POST -> Root / "admin" / "projections" / "replay" =>
        req.as[ReplayRequest].flatMap { body =>
          if (body.fromOffset == 0L) {
            service.replayFromZero.flatMap(Ok(_))
          } else {
            BadRequest(ErrorResponse("这个 Demo 只演示从 offset=0 全量重建"))
          }
        }

      case GET -> Root / "admin" / "projections" / "status" =>
        service.status.flatMap(Ok(_))

      case GET -> Root / "orders" / orderId / "read-model" =>
        service.view(orderId).flatMap {
          case Some(view) => Ok(view)
          case None       => NotFound(ErrorResponse(s"找不到读模型: $orderId"))
        }
    }.orNotFound

  def jsonRequest[A](method: Method, uri: Uri, body: A)(implicit encoder: io.circe.Encoder[A]): Request[IO] =
    Request[IO](method, uri)
      .withEntity(body.asJson.noSpaces)
      .putHeaders(Header.Raw(ci"Content-Type", "application/json"))

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== http4s 读模型查询边界 ===")
      service <- ReadModelService.create
      app = buildApp(service)

      appended1 <- app(
        jsonRequest(
          Method.POST,
          uri"/events/orders",
          AppendEventRequest("OrderCreated", "order-100", Some("BTC-101"), Some(2), None)
        )
      )
      appended1Body <- appended1.as[String]
      _ <- IO.println(s"写入订单事件: status=${appended1.status.code}, body=$appended1Body")

      appended2 <- app(
        jsonRequest(
          Method.POST,
          uri"/events/orders",
          AppendEventRequest("PaymentCaptured", "order-100", None, None, Some(BigDecimal(128.5)))
        )
      )
      appended2Body <- appended2.as[String]
      _ <- IO.println(s"写入支付事件: status=${appended2.status.code}, body=$appended2Body")

      statusBefore <- app(Request[IO](Method.GET, uri"/admin/projections/status"))
      statusBeforeBody <- statusBefore.as[String]
      _ <- IO.println(s"推进前状态: status=${statusBefore.status.code}, body=$statusBeforeBody")

      catchUpResponse <- app(Request[IO](Method.POST, uri"/admin/projections/catch-up"))
      catchUpBody <- catchUpResponse.as[String]
      _ <- IO.println(s"catch-up 结果: status=${catchUpResponse.status.code}, body=$catchUpBody")

      order100 <- app(Request[IO](Method.GET, uri"/orders/order-100/read-model"))
      order100Body <- order100.as[String]
      _ <- IO.println(s"查询 order-100: status=${order100.status.code}, body=$order100Body")

      _ <- app(
        jsonRequest(
          Method.POST,
          uri"/events/orders",
          AppendEventRequest("OrderCreated", "order-200", Some("ETH-202"), Some(1), None)
        )
      )
      statusMid <- app(Request[IO](Method.GET, uri"/admin/projections/status"))
      statusMidBody <- statusMid.as[String]
      _ <- IO.println(s"新增事件后的状态: status=${statusMid.status.code}, body=$statusMidBody")

      replayResponse <- app(jsonRequest(Method.POST, uri"/admin/projections/replay", ReplayRequest(0L)))
      replayBody <- replayResponse.as[String]
      _ <- IO.println(s"replay 结果: status=${replayResponse.status.code}, body=$replayBody")

      order200 <- app(Request[IO](Method.GET, uri"/orders/order-200/read-model"))
      order200Body <- order200.as[String]
      _ <- IO.println(s"查询 order-200: status=${order200.status.code}, body=$order200Body")

      finalStatus <- app(Request[IO](Method.GET, uri"/admin/projections/status"))
      finalStatusBody <- finalStatus.as[String]
      _ <- IO.println(s"最终状态: status=${finalStatus.status.code}, body=$finalStatusBody")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- 查询侧一旦进入 HTTP 边界，就要把事件写入、投影状态和读模型查询明确拆成不同接口")
      _ <- IO.println("- lag / checkpoint 应该可观测，否则你很难知道读模型到底追上事件日志没有")
      _ <- IO.println("- 管理员 replay 的目标是重建查询侧视图，而不是重新执行写侧业务")
    } yield ()
}
