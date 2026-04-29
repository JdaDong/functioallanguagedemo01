//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.13.0"

/**
 * Scala 函数式编程 Demo 72: fs2 Topic 广播枢纽
 *
 * 37 号 Demo 已经单独演示过 Topic 发布订阅，
 * 这一版继续把它推进到更贴近实时系统的“房间广播”模型：
 *
 * - 同一房间里的多个订阅者都能看到同一条消息
 * - 不同房间可以共享同一个广播总线，再在消费端过滤
 */
import cats.effect.{Deferred, IO, IOApp}
import fs2.Stream
import fs2.concurrent.Topic

import scala.concurrent.duration._

object FS2TopicHub extends IOApp.Simple {

  final case class RoomMessage(room: String, from: String, text: String)

  def publish(topic: Topic[IO, RoomMessage], message: RoomMessage): IO[Unit] =
    IO.println(s"[publish] $message") *> topic.publish1(message).void

  def roomFeed(
      topic: Topic[IO, RoomMessage],
      room: String,
      listener: String,
      ready: Deferred[IO, Unit]
  ): Stream[IO, String] =
    Stream.exec(ready.complete(()).void) ++
      topic.subscribe(16)
        .filter(_.room == room)
        .map(msg => s"[$listener] ${msg.room}:${msg.from} -> ${msg.text}")
        .evalTap(line => IO.println(s"[subscriber] $line"))

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== 用 Topic 构建房间广播枢纽 ===")
      topic <- Topic[IO, RoomMessage]
      aliceReady <- Deferred[IO, Unit]
      bobReady <- Deferred[IO, Unit]
      carolReady <- Deferred[IO, Unit]

      aliceFiber <- roomFeed(topic, "room-btc", "alice", aliceReady).take(2).compile.toList.start
      bobFiber <- roomFeed(topic, "room-btc", "bob", bobReady).take(2).compile.toList.start
      carolFiber <- roomFeed(topic, "room-eth", "carol", carolReady).take(1).compile.toList.start

      _ <- aliceReady.get *> bobReady.get *> carolReady.get
      _ <- IO.sleep(120.millis)
      _ <- publish(topic, RoomMessage("room-btc", "trader-1", "buy wall at 101.2"))
      _ <- IO.sleep(80.millis)
      _ <- publish(topic, RoomMessage("room-eth", "trader-2", "breakout at 2050"))
      _ <- IO.sleep(80.millis)
      _ <- publish(topic, RoomMessage("room-btc", "trader-3", "take profit at 101.8"))

      alice <- aliceFiber.joinWithNever
      bob <- bobFiber.joinWithNever
      carol <- carolFiber.joinWithNever

      _ <- IO.println(s"alice 收到: $alice")
      _ <- IO.println(s"bob 收到: $bob")
      _ <- IO.println(s"carol 收到: $carol")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- Topic 很适合表达“同一条消息要同时发给多个订阅者”的广播模型")
      _ <- IO.println("- 先共享一个总线，再在订阅端按 room / tenant / symbol 过滤，是很常见的组织方式")
      _ <- IO.println("- 这正是聊天室、行情房间、通知中心、协同编辑事件分发的基础直觉")
    } yield ()
}
