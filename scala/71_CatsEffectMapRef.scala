//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"

/**
 * Scala 函数式编程 Demo 71: cats-effect MapRef 管理按 key 分片的状态
 *
 * 66 号 Demo 已经看过 fiber-local 上下文，
 * 这一版继续补一个在实时系统里很常见的状态模型：
 *
 * - 不同房间、会话、租户、symbol 都有各自的计数或状态
 * - 我们希望按 key 原子更新，而不是手写一大坨共享 Map 锁
 */
import cats.effect.std.MapRef
import cats.effect.{IO, IOApp}

object CatsEffectMapRef extends IOApp.Simple {

  def join(presence: MapRef[IO, String, Option[Int]], room: String): IO[Int] =
    presence(room).modify { current =>
      val next = current.getOrElse(0) + 1
      (Some(next), next)
    }

  def leave(presence: MapRef[IO, String, Option[Int]], room: String): IO[Int] =
    presence(room).modify { current =>
      val next = math.max(0, current.getOrElse(0) - 1)
      (Some(next), next)
    }

  def current(presence: MapRef[IO, String, Option[Int]], room: String): IO[Int] =
    presence(room).get.map(_.getOrElse(0))

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== 用 MapRef 管理按房间分片的在线人数 ===")
      presence <- MapRef.ofShardedImmutableMap[IO, String, Int](shardCount = 8)

      btc1 <- join(presence, "room-btc")
      _ <- IO.println(s"[join] room-btc -> $btc1")
      btc2 <- join(presence, "room-btc")
      _ <- IO.println(s"[join] room-btc -> $btc2")
      eth1 <- join(presence, "room-eth")
      _ <- IO.println(s"[join] room-eth -> $eth1")
      btc3 <- leave(presence, "room-btc")
      _ <- IO.println(s"[leave] room-btc -> $btc3")

      btc <- current(presence, "room-btc")
      eth <- current(presence, "room-eth")
      sol <- current(presence, "room-sol")

      _ <- IO.println(s"最终房间人数: room-btc=$btc, room-eth=$eth, room-sol=$sol")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- MapRef 很适合表达“按 key 切开的原子状态”，比如房间人数、会话配额、租户计数")
      _ <- IO.println("- 你只更新某个 key 对应的 Ref，不必手动管理整张共享 Map 的读改写")
      _ <- IO.println("- 这在聊天室、订阅中心、实时会话管理里特别常见")
    } yield ()
}
