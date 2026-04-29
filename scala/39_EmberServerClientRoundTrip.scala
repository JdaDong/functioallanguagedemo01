//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.http4s::http4s-circe:0.23.33"
//> using dep "org.http4s::http4s-ember-server:0.23.33"
//> using dep "org.http4s::http4s-ember-client:0.23.33"
//> using dep "io.circe::circe-generic:0.14.10"

/**
 * Scala 函数式编程 Demo 39: Ember Server + Ember Client 本地联调
 *
 * 33 号 Demo 里 client 是通过 `Client.fromHttpApp` 模拟出来的。
 * 这一版继续推进：真正启动一个本地 Ember 服务器，再用 Ember client 发起请求。
 */
import cats.effect.{IO, IOApp}
import com.comcast.ip4s._
import io.circe.generic.auto._
import org.http4s.{EntityDecoder, EntityEncoder, HttpRoutes, Uri}
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._

import scala.concurrent.duration._

object EmberServerClientRoundTrip extends IOApp.Simple {

  final case class Health(service: String, status: String)
  final case class HelloResponse(message: String)

  implicit val healthDecoder: EntityDecoder[IO, Health] = jsonOf[IO, Health]
  implicit val helloDecoder: EntityDecoder[IO, HelloResponse] = jsonOf[IO, HelloResponse]
  implicit val healthEncoder: EntityEncoder[IO, Health] = jsonEncoderOf[IO, Health]
  implicit val helloEncoder: EntityEncoder[IO, HelloResponse] = jsonEncoderOf[IO, HelloResponse]

  val app = HttpRoutes.of[IO] {
    case GET -> Root / "health" =>
      Ok(Health("ember-demo", "up"))

    case GET -> Root / "hello" / name =>
      Ok(HelloResponse(s"hello, $name"))
  }.orNotFound

  val portValue = 58231

  val run: IO[Unit] =
    EmberServerBuilder
      .default[IO]
      .withHost(host"127.0.0.1")
      .withPort(Port.fromInt(portValue).get)
      .withHttpApp(app)
      .build
      .use { _ =>
        EmberClientBuilder.default[IO].build.use { client =>
          for {
            _ <- IO.println("=== Ember server/client 本地联调 ===")
            _ <- IO.sleep(200.millis)
            health <- client.expect[Health](Uri.unsafeFromString(s"http://127.0.0.1:$portValue/health"))
            _ <- IO.println(s"health -> $health")
            hello <- client.expect[HelloResponse](Uri.unsafeFromString(s"http://127.0.0.1:$portValue/hello/Alice"))
            _ <- IO.println(s"hello -> $hello")

            _ <- IO.println("\n=== 重点理解 ===")
            _ <- IO.println("- 这一版不再只是把请求喂给 HttpApp，而是真的启动了本地 HTTP 服务")
            _ <- IO.println("- Ember server 和 Ember client 都是基于 Resource 生命周期管理")
            _ <- IO.println("- 服务端和客户端在真实 effect system 里可以用同样的资源模型组织")
          } yield ()
        }
      }
}
