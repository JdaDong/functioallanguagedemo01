//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"

/**
 * Scala 函数式编程 Demo 86: cats-effect 并发幂等门闩
 *
 * 81 到 85 号 Demo 已经把批量导入导出和真实数据库边界补齐了，
 * 这一组继续处理真实系统里的另一个高频问题：重复请求。
 *
 * 第一版先从最小问题切入：
 *
 * - 同一个进程里，如果两个 fiber 几乎同时提交同一个 requestId
 * - 我们希望只有第一个真正执行业务逻辑
 * - 后面的重复请求直接复用首个执行结果
 */
import cats.effect.{Deferred, IO, IOApp, Ref}
import cats.syntax.all._

import scala.concurrent.duration._

object CatsEffectIdempotencyGate extends IOApp.Simple {

  final case class ReservationCommand(requestId: String, sku: String, units: Int)

  final class IdempotencyGate private (
      state: Ref[IO, Map[String, Deferred[IO, Either[Throwable, String]]]]
  ) {

    def run(requestId: String)(task: IO[String]): IO[String] =
      Deferred[IO, Either[Throwable, String]].flatMap { freshGate =>
        state.modify { current =>
          current.get(requestId) match {
            case Some(existingGate) => current -> Left(existingGate)
            case None => current.updated(requestId, freshGate) -> Right(freshGate)
          }
        }.flatMap {
          case Left(existingGate) =>
            IO.println(s"[$requestId] 检测到并发重复请求，直接等待首个执行结果") *>
              existingGate.get.rethrow

          case Right(leaderGate) =>
            IO.println(s"[$requestId] 获得执行权，真正开始处理业务") *>
              task.attempt
                .flatTap(result => leaderGate.complete(result).void)
                .guarantee(state.update(_ - requestId))
                .rethrow
        }
      }
  }

  object IdempotencyGate {
    def create: IO[IdempotencyGate] =
      Ref.of[IO, Map[String, Deferred[IO, Either[Throwable, String]]]](Map.empty)
        .map(new IdempotencyGate(_))
  }

  def process(command: ReservationCommand): IO[String] =
    for {
      _ <- IO.println(s"[worker] 真正执行库存预留: ${command.requestId}, sku=${command.sku}, units=${command.units}")
      _ <- IO.sleep(300.millis)
      result = s"reserved:${command.sku}:${command.units}"
      _ <- IO.println(s"[worker] 完成 ${command.requestId} -> $result")
    } yield result

  val run: IO[Unit] =
    for {
      gate <- IdempotencyGate.create
      _ <- IO.println("=== cats-effect 并发幂等门闩 ===")

      fiber1 <- gate.run("req-100")(
        process(ReservationCommand("req-100", "BTC-101", 2))
      ).start

      fiber2 <- gate.run("req-100")(
        process(ReservationCommand("req-100", "BTC-101", 2))
      ).start

      fiber3 <- gate.run("req-200")(
        process(ReservationCommand("req-200", "ETH-202", 1))
      ).start

      result1 <- fiber1.joinWithNever
      result2 <- fiber2.joinWithNever
      result3 <- fiber3.joinWithNever

      _ <- IO.println(s"req-100 首次结果: $result1")
      _ <- IO.println(s"req-100 重复结果: $result2")
      _ <- IO.println(s"req-200 独立结果: $result3")

      _ <- IO.println("\n=== 同一个 requestId 在完成后再次进入，会重新执行业务 ===")
      rerun <- gate.run("req-100")(
        process(ReservationCommand("req-100", "BTC-101", 2))
      )
      _ <- IO.println(s"重新执行结果: $rerun")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- `Ref + Deferred` 很适合同进程内的并发去重：只有 leader 真正执行，其他 fiber 复用结果")
      _ <- IO.println("- 这一层解决的是‘正在飞行中的重复请求’，还没有跨进程、跨重启持久化")
      _ <- IO.println("- 真正长期幂等仍然要下沉到 HTTP 边界和数据库边界，这正是后面几号 Demo 要补的")
    } yield ()
}
