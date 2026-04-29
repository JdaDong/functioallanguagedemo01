//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.13.0"
//> using dep "org.http4s::http4s-core:0.23.33"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.http4s::http4s-ember-server:0.23.33"

/**
 * Scala 函数式编程 Demo 73: http4s WebSocket 聊天路由
 *
 * 前面已经把流式响应和 SSE 都走通了，
 * 这一版继续补上更实时的一步：WebSocket 双向通信。
 *
 * - server 不只往外推数据，也能持续接收 client 发来的消息
 * - 同一条连接里，收和发可以并发进行
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

object Http4sWebSocketChat extends IOApp.Simple {

  final case class ClientSession(
      socket: WebSocket,
      inbox: Queue[IO, String],
      opened: Deferred[IO, Unit]
  ) {
    def awaitOpen: IO[Unit] = opened.get
    def sendText(value: String): IO[Unit] = IO.fromCompletableFuture(IO(socket.sendText(value, true))).void
    def nextMessage: IO[String] = inbox.take
  }

  def chatRoutes(wsBuilder: WebSocketBuilder2[IO], topic: Topic[IO, String]): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "ws" / "chat" / user =>
        val send: Stream[IO, WebSocketFrame] =
          Stream.emit(WebSocketFrame.Text(s"system:connected:$user", true)) ++
            topic.subscribe(32).map(message => WebSocketFrame.Text(message, true))

        val receive: fs2.Pipe[IO, WebSocketFrame, Unit] =
          _.evalMap {
            case WebSocketFrame.Text(text, _) =>
              IO.println(s"[server] 收到 $user 的消息: $text") *>
                topic.publish1(s"$user:$text").void
            case _ => IO.unit
          }

        wsBuilder.build(send, receive)
    }

  def connect(url: String): Resource[IO, ClientSession] =
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
        } yield ClientSession(socket, inbox, opened)
      }.flatMap { session =>
        Resource.make(IO.pure(session)) { s =>
          IO.fromCompletableFuture(IO(s.socket.sendClose(WebSocket.NORMAL_CLOSURE, "done"))).void
            .handleErrorWith(_ => IO(s.socket.abort()))
        }
      }
    }

  val portValue = 58261

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== http4s WebSocket：双向聊天路由 ===")
      topic <- Topic[IO, String]
      _ <- EmberServerBuilder
        .default[IO]
        .withHost(host"127.0.0.1")
        .withPort(Port.fromInt(portValue).get)
        .withHttpWebSocketApp(wsBuilder => chatRoutes(wsBuilder, topic).orNotFound)
        .build
        .use { _ =>
          connect(s"ws://127.0.0.1:$portValue/ws/chat/alice").use { client =>
            for {
              _ <- client.awaitOpen
              welcome <- client.nextMessage
              _ <- IO.println(s"[client] 欢迎消息: $welcome")
              _ <- client.sendText("hello-websocket")
              echoed <- client.nextMessage
              _ <- IO.println(s"[client] 聊天广播: $echoed")
            } yield ()
          }
        }

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- WebSocket 和 SSE 的关键差别在于：它是双向的，client 也能持续把消息发回 server")
      _ <- IO.println("- 在 http4s 里，发送端是 Stream，接收端是 Pipe，天然就是一对可组合的数据流")
      _ <- IO.println("- 这很适合聊天室、协同编辑、实时控制台、在线游戏状态同步")
    } yield ()
}
