//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.http4s::http4s-core:0.23.33"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.http4s::http4s-client:0.23.33"
//> using dep "org.http4s::http4s-circe:0.23.33"
//> using dep "io.circe::circe-generic:0.14.10"

/**
 * Scala 函数式编程 Demo 33: http4s Client 调用服务
 *
 * 真实系统里，服务不只是“接请求”，也经常要“发请求”。
 * 这一版用 `Client.fromHttpApp` 模拟一个下游服务，
 * 让你先看清 http4s client 的使用方式，而不必真的起网络端口。
 */
import cats.effect.{IO, IOApp}
import io.circe.generic.auto._
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.implicits._

object Http4sClientDemo extends IOApp.Simple {

  final case class Quote(symbol: String, price: BigDecimal, source: String)
  final case class ErrorResponse(error: String)

  implicit val quoteEncoder: EntityEncoder[IO, Quote] = jsonEncoderOf
  implicit val quoteDecoder: EntityDecoder[IO, Quote] = jsonOf
  implicit val errorEncoder: EntityEncoder[IO, ErrorResponse] = jsonEncoderOf
  implicit val errorDecoder: EntityDecoder[IO, ErrorResponse] = jsonOf

  val marketApp: HttpApp[IO] = HttpApp[IO] {
    case GET -> Root / "quotes" / "AAPL" =>
      Ok(Quote("AAPL", BigDecimal("189.52"), "mock-market"))

    case GET -> Root / "quotes" / "TSLA" =>
      Ok(Quote("TSLA", BigDecimal("171.03"), "mock-market"))

    case GET -> Root / "quotes" / symbol =>
      NotFound(ErrorResponse(s"unknown symbol: $symbol"))

    case _ =>
      IO.pure(Response[IO](status = Status.MethodNotAllowed).withEntity(ErrorResponse("method not allowed")))
  }

  val client: Client[IO] = Client.fromHttpApp(marketApp)

  def fetchQuote(symbol: String): IO[Either[String, Quote]] = {
    val request = Request[IO](Method.GET, Uri.unsafeFromString(s"/quotes/$symbol"))

    client.run(request).use { response =>
      response.status match {
        case Status.Ok => response.as[Quote].map(Right(_))
        case _ => response.as[ErrorResponse].map(err => Left(err.error))
      }
    }
  }

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== http4s Client 调用成功案例 ===")
      aapl <- fetchQuote("AAPL")
      _ <- IO.println(s"AAPL -> $aapl")

      _ <- IO.println("\n=== http4s Client 调用失败案例 ===")
      msft <- fetchQuote("MSFT")
      _ <- IO.println(s"MSFT -> $msft")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- Client.run 返回 Resource，响应体消费完后会自动释放连接")
      _ <- IO.println("- client 端也同样依赖 EntityDecoder / EntityEncoder 做协议转换")
      _ <- IO.println("- 先用 fromHttpApp 模拟下游服务，有助于理解 client 代码本身的结构")
    } yield ()
}
