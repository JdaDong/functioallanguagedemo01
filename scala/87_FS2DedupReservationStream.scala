//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.13.0"

/**
 * Scala 函数式编程 Demo 87: fs2 去重的预留请求流
 *
 * 86 号 Demo 先解决了同进程并发重复提交的问题，
 * 这一版继续看另一类常见来源：消息重放、网络重试、重复投递。
 *
 * - 上游可能按 at-least-once 语义重复推送同一条命令
 * - 我们希望在流里先做一次显式去重
 * - 再把唯一命令切成批次，交给下游处理
 */
import cats.effect.{IO, IOApp}
import fs2.Stream

object FS2DedupReservationStream extends IOApp.Simple {

  final case class ReservationCommand(commandId: String, requestId: String, sku: String, units: Int)

  def deduplicateByRequestId(
      commands: Stream[IO, ReservationCommand]
  ): Stream[IO, ReservationCommand] =
    commands
      .mapAccumulate(Set.empty[String]) { case (seen, command) =>
        if (seen.contains(command.requestId)) {
          seen -> (Left(command): Either[ReservationCommand, ReservationCommand])
        } else {
          (seen + command.requestId) -> (Right(command): Either[ReservationCommand, ReservationCommand])
        }
      }
      .evalTap {
        case (_, Left(duplicate)) =>
          IO.println(s"[drop] 重复 requestId=${duplicate.requestId}, commandId=${duplicate.commandId}")

        case (_, Right(accepted)) =>
          IO.println(s"[keep] 接受 requestId=${accepted.requestId}, commandId=${accepted.commandId}")
      }
      .collect { case (_, Right(command)) => command }

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== fs2 去重的预留请求流 ===")
      commands = List(
        ReservationCommand("evt-001", "req-100", "BTC-101", 2),
        ReservationCommand("evt-002", "req-100", "BTC-101", 2),
        ReservationCommand("evt-003", "req-101", "ETH-202", 1),
        ReservationCommand("evt-004", "req-102", "SOL-303", 4),
        ReservationCommand("evt-005", "req-101", "ETH-202", 1),
        ReservationCommand("evt-006", "req-103", "BTC-101", 3)
      )

      accepted <- deduplicateByRequestId(
        Stream
          .emits(commands)
          .covary[IO]
          .evalTap(command => IO.println(s"[source] $command"))
      )
        .chunkN(2, allowFewer = true)
        .evalTap { chunk =>
          val requestIds = chunk.toList.map(_.requestId)
          IO.println(s"[batch] 准备提交批次: $requestIds")
        }
        .flatMap(chunk => Stream.chunk(chunk))
        .compile
        .toList

      _ <- IO.println(s"最终唯一请求数=${accepted.size}")
      _ <- IO.println(s"最终 requestId 顺序=${accepted.map(_.requestId)}")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- 在流里显式做去重，可以先拦住消息重放和网络重试带来的重复命令")
      _ <- IO.println("- `mapAccumulate` 让去重状态成为流处理的一部分，而不是藏在外部可变变量里")
      _ <- IO.println("- 真实系统里通常还会加时间窗口、持久化状态或数据库幂等键，后面 Demo 会继续补")
    } yield ()
}
