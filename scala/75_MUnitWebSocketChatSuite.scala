//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.13.0"
//> using dep "org.http4s::http4s-core:0.23.33"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.http4s::http4s-ember-server:0.23.33"
//> using dep "org.typelevel::munit-cats-effect-3:1.0.7"

/**
 * Scala 函数式编程 Demo 75: 测试 WebSocket 聊天路由
 *
 * 70 号 Demo 已经把 SSE 推到测试闭环，
 * 这一版继续补上 WebSocket 的关键行为验证：
 *
 * - 连接建立后是否先收到 welcome 消息
 * - 发送文本后是否能收到广播结果
 * - 同一房间中的两个客户端能否互相看到对方消息
 */
import cats.effect.std.{Dispatcher, Queue}
import cats.effect.{Deferred, IO, Resource}
import com.comcast.ip4s._
import fs2.Stream
import fs2.concurrent.Topic
import munit.CatsEffectSuite
import org.http4s.HttpRoutes
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame

import java.net.URI
import java.net.http.{HttpClient, WebSocket}
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class MUnitWebSocketChatSuite extends CatsEffectSuite {

  final class Session(
      val socket: WebSocket,
      val inbox: Queue[IO, String],
      val opened: Deferred[IO, Unit]
  ) {
    def awaitOpen: IO[Unit] = opened.get
    def sendText(value: String): IO[Unit] = IO.fromCompletableFuture(IO(socket.sendText(value, true))).void
    def nextMessage: IO[String] = inbox.take
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
            case WebSocketFrame.Text(text, _) => topic.publish1(s"$room|$user:$text").void
            case _ => IO.unit
          }

        wsBuilder.build(send, receive)
    }

  def serverResource(port: Int): Resource[IO, Unit] =
    Resource.eval(Topic[IO, String]).flatMap { topic =>
      EmberServerBuilder
        .default[IO]
        .withHost(host"127.0.0.1")
        .withPort(Port.fromInt(port).get)
        .withHttpWebSocketApp(wsBuilder => routes(wsBuilder, topic).orNotFound)
        .build
        .map(_ => ())
    }

  def connect(url: String): Resource[IO, Session] =
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
        } yield new Session(socket, inbox, opened)
      }.flatMap { session =>
        Resource.make(IO.pure(session)) { s =>
          IO.fromCompletableFuture(IO(s.socket.sendClose(WebSocket.NORMAL_CLOSURE, "done"))).void
            .handleErrorWith(_ => IO(s.socket.abort()))
        }
      }
    }

  test("连接建立后应该先收到 welcome，再收到自己发出的广播") {
    val port = 58281
    serverResource(port).use { _ =>
      connect(s"ws://127.0.0.1:$port/ws/room/trading/alice").use { alice =>
        for {
          _ <- alice.awaitOpen
          welcome <- alice.nextMessage
          _ <- alice.sendText("hello")
          echoed <- alice.nextMessage
        } yield {
          assertEquals(welcome, "system:trading:connected:alice")
          assertEquals(echoed, "alice:hello")
        }
      }
    }
  }

  test("同一房间的两个客户端应该互相看到对方消息") {
    val port = 58282
    serverResource(port).use { _ =>
      val aliceUrl = s"ws://127.0.0.1:$port/ws/room/trading/alice"
      val bobUrl = s"ws://127.0.0.1:$port/ws/room/trading/bob"

      (for {
        alice <- connect(aliceUrl)
        bob <- connect(bobUrl)
      } yield (alice, bob)).use { case (alice, bob) =>
        for {
          _ <- alice.awaitOpen
          _ <- bob.awaitOpen
          _ <- alice.nextMessage
          _ <- bob.nextMessage

          _ <- alice.sendText("BTC breakout")
          aliceOwn <- alice.nextMessage
          bobSawAlice <- bob.nextMessage

          _ <- bob.sendText("roger")
          bobOwn <- bob.nextMessage
          aliceSawBob <- alice.nextMessage
        } yield {
          assertEquals(aliceOwn, "alice:BTC breakout")
          assertEquals(bobSawAlice, "alice:BTC breakout")
          assertEquals(bobOwn, "bob:roger")
          assertEquals(aliceSawBob, "bob:roger")
        }
      }
    }
  }
}
