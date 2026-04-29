//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.http4s::http4s-core:0.23.33"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.http4s::http4s-circe:0.23.33"
//> using dep "io.circe::circe-generic:0.14.10"

/**
 * Scala 函数式编程 Demo 43: http4s 里的领域错误映射
 *
 * 前面已经写过 JSON API、Bearer 鉴权、server/client 联调，
 * 这一版继续补上真实服务里非常关键的一层：
 * 把业务错误和 HTTP 响应边界清晰分开。
 */
import cats.effect.{IO, IOApp}
import io.circe.generic.auto._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.implicits._

object Http4sErrorHandling extends IOApp.Simple {

  sealed trait DomainError
  case object ProductNotFound extends DomainError
  case object ProductDiscontinued extends DomainError
  final case class InvalidQuantity(message: String) extends DomainError

  final case class OrderAccepted(sku: String, quantity: Int, accepted: Boolean)
  final case class ErrorResponse(code: String, message: String)

  implicit val orderEncoder: EntityEncoder[IO, OrderAccepted] = jsonEncoderOf[IO, OrderAccepted]
  implicit val errorEncoder: EntityEncoder[IO, ErrorResponse] = jsonEncoderOf[IO, ErrorResponse]

  def placeOrder(sku: String, quantity: Int): IO[Either[DomainError, OrderAccepted]] =
    IO.pure {
      if (quantity <= 0) Left(InvalidQuantity("数量必须大于 0"))
      else if (quantity > 5) Left(InvalidQuantity("单次下单数量不能超过 5"))
      else if (sku == "missing-sku") Left(ProductNotFound)
      else if (sku == "legacy-sku") Left(ProductDiscontinued)
      else Right(OrderAccepted(sku, quantity, accepted = true))
    }

  def toHttpResponse(error: DomainError): IO[Response[IO]] =
    error match {
      case ProductNotFound =>
        NotFound(ErrorResponse("product_not_found", "商品不存在"))
      case ProductDiscontinued =>
        Conflict(ErrorResponse("product_discontinued", "商品已下架，不能继续下单"))
      case InvalidQuantity(message) =>
        BadRequest(ErrorResponse("invalid_quantity", message))
    }

  val app: HttpApp[IO] =
    HttpRoutes.of[IO] {
      case POST -> Root / "orders" / sku / IntVar(quantity) =>
        placeOrder(sku, quantity).flatMap {
          case Right(result) => Created(result)
          case Left(error) => toHttpResponse(error)
        }

      case POST -> Root / "orders" / _ / _ =>
        BadRequest(ErrorResponse("invalid_path", "路径里的数量必须是整数"))
    }.orNotFound

  def render(label: String, response: Response[IO]): IO[Unit] =
    response.as[String].flatMap(body => IO.println(s"$label -> status=${response.status.code}, body=$body"))

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== 正常下单 ===")
      ok <- app(Request[IO](Method.POST, uri"/orders/ipad-case/2"))
      _ <- render("ok", ok)

      _ <- IO.println("\n=== 数量非法 ===")
      badQuantity <- app(Request[IO](Method.POST, uri"/orders/ipad-case/9"))
      _ <- render("bad-quantity", badQuantity)

      _ <- IO.println("\n=== 商品不存在 ===")
      missing <- app(Request[IO](Method.POST, uri"/orders/missing-sku/1"))
      _ <- render("missing", missing)

      _ <- IO.println("\n=== 商品已下架 ===")
      discontinued <- app(Request[IO](Method.POST, uri"/orders/legacy-sku/1"))
      _ <- render("discontinued", discontinued)

      _ <- IO.println("\n=== 路径参数格式错误 ===")
      invalidPath <- app(Request[IO](Method.POST, uri"/orders/ipad-case/not-a-number"))
      _ <- render("invalid-path", invalidPath)

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- 业务逻辑先返回领域错误，而不是直接在里面拼 HTTP 响应")
      _ <- IO.println("- HTTP 层只负责把领域错误翻译成状态码和 JSON 响应")
      _ <- IO.println("- 这样业务规则和协议边界就能保持清晰分层")
    } yield ()
}
