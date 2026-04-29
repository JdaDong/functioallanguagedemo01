//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.13.0"
//> using dep "org.http4s::http4s-core:0.23.33"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.http4s::http4s-ember-server:0.23.33"

/**
 * Scala 函数式编程 Demo 74: 用 JDK WebSocket Client 桥接真实双向消息流
 *
 * 73 号 Demo 已经把 WebSocket 路由跑起来了，
 * 这一版继续推进到更贴近工程接缝的一侧：
 *
 * - Java / JDK 风格的 WebSocket listener 还是 callback 形态
 * - 我们把它桥接回 `IO` / `Queue`，继续用函数式方式组织客户端逻辑
 */
import cats.effect.std.{Dispatcher, Queue}
import cats.effect.{Deferred, IO, IOApp, Resource}
import com.comcast.ip4s._
import fs2.Stream
import fs2.concurrent.Topic
import org.http4s.HttpRoutes
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame

import java.net.URI
import java.net.http.{HttpClient, WebSocket}
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

object JdkWebSocketBridgeClient extends IOApp.Simple {

  final case class Session(
      user: String,
      socket: WebSocket,
      inbox: Queue[IO, String],
      opened: Deferred[IO, Unit]
  ) {
    def awaitOpen: IO[Unit] = opened.get

    def sendText(value: String): IO[Unit] =
      IO.println(s"[client:$user] 发送 -> $value") *>
        IO.fromCompletableFuture(IO(socket.sendText(value, true))).void

    def nextMessage: IO[String] =
      inbox.take.flatTap(msg => IO.println(s"[client:$user] 收到 <- $msg"))
  }

  def routes(wsBuilder: WebSocketBuilder2[IO], topic: Topic[IO, String]): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "ws" / "room" / room / user =>
        val send: Stream[IO, WebSocketFrame] =
          Stream.emit(WebSocketFrame.Text(s"system:$room:connected:$user", true)) ++
            topic.subscribe(32)
              .filter(_.startsWith(s"$room|"))
              .map(_.stripPrefix(s"$room|"))
              .map(message => WebSocketFrame.Text(message, true))

        val receive: fs2.Pipe[IO, WebSocketFrame, Unit] =
          _.evalMap {
            case WebSocketFrame.Text(text, _) =>
              topic.publish1(s"$room|$user:$text").void
            case _ => IO.unit
          }

        wsBuilder.build(send, receive)
    }

  def connect(user: String, url: String): Resource[IO, Session] =
    Dispatcher.sequential[IO].flatMap { dispatcher =>
      Resource.eval {
        for {
          inbox <- Queue.unbounded[IO, String]
          opened <- Deferred[IO, Unit]
          client <- IO(HttpClient.newHttpClient())
          listener = new WebSocket.Listener {
            override def onOpen(webSocket: WebSocket): Unit = {
              webSocket.request(1)
              dispatcher.unsafeRunAndForget(opened.complete(()).void)
            }

            override def onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage[_] = {
              webSocket.request(1)
              dispatcher.unsafeRunAndForget(inbox.offer(data.toString))
              CompletableFuture.completedFuture(null)
            }
          }
          socket <- IO.fromCompletableFuture(IO(client.newWebSocketBuilder().buildAsync(URI.create(url), listener)))
        } yield Session(user, socket, inbox, opened)
      }.flatMap { session =>
        Resource.make(IO.pure(session)) { s =>
          IO.fromCompletableFuture(IO(s.socket.sendClose(WebSocket.NORMAL_CLOSURE, "done"))).void
            .handleErrorWith(_ => IO(s.socket.abort()))
        }
      }
    }

  val portValue = 58271

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== 用 JDK WebSocket listener 桥接双向消息 ===")
      topic <- Topic[IO, String]
      _ <- EmberServerBuilder
        .default[IO]
        .withHost(host"127.0.0.1")
        .withPort(Port.fromInt(portValue).get)
        .withHttpWebSocketApp(wsBuilder => routes(wsBuilder, topic).orNotFound)
        .build
        .use { _ =>
          val aliceUrl = s"ws://127.0.0.1:$portValue/ws/room/trading/alice"
          val bobUrl = s"ws://127.0.0.1:$portValue/ws/room/trading/bob"

          (for {
            alice <- connect("alice", aliceUrl)
            bob <- connect("bob", bobUrl)
          } yield (alice, bob)).use { case (alice, bob) =>
            for {
              _ <- alice.awaitOpen
              _ <- bob.awaitOpen
              _ <- alice.nextMessage
              _ <- bob.nextMessage

              _ <- alice.sendText("BTC breakout")
              aliceOwn <- alice.nextMessage
              bobSeen <- bob.nextMessage

              _ <- bob.sendText("copy that")
              bobOwn <- bob.nextMessage
              aliceSeen <- alice.nextMessage

              _ <- IO.println(s"alice 自己也会收到广播: $aliceOwn")
              _ <- IO.println(s"bob 看到了 alice 的消息: $bobSeen")
              _ <- IO.println(s"bob 自己也会收到广播: $bobOwn")
              _ <- IO.println(s"alice 看到了 bob 的回复: $aliceSeen")
            } yield ()
          }
        }

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- JDK WebSocket client 仍然是 listener/callback 风格，但可以用 Dispatcher + Queue 接回 IO 世界")
      _ <- IO.println("- 一旦接回 Queue，读取消息、等待连接、顺序断言都能继续写成普通 effect 流程")
      _ <- IO.println("- 这类桥接对于 Java SDK、浏览器桥接层、旧式网络客户端非常常见")
    } yield ()
}
