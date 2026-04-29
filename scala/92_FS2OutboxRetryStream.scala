//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.12.2"

/**
 * Scala 函数式编程 Demo 92: fs2 Outbox 重试发布流
 *
 * 91 号 Demo 先把 outbox 的最小一致性模型讲清楚了，
 * 这一版继续把“后台重复扫描 + 失败重试”放到 fs2 流里。
 */
import cats.effect.{IO, IOApp, Ref}
import cats.syntax.all._
import fs2.Stream

import scala.concurrent.duration._

object FS2OutboxRetryStream extends IOApp.Simple {

  final case class OutboxRecord(id: Long, payload: String, attempts: Int)
  final case class PublishResult(id: Long, status: String)

  final class Outbox private (state: Ref[IO, Vector[OutboxRecord]]) {
    def loadPending: IO[Vector[OutboxRecord]] = state.get

    def seed(records: List[OutboxRecord]): IO[Unit] =
      state.set(records.toVector)

    def markRetried(id: Long): IO[Unit] =
      state.update(_.map(record => if (record.id == id) record.copy(attempts = record.attempts + 1) else record))

    def markPublished(id: Long): IO[Unit] =
      state.update(_.filterNot(_.id == id))
  }

  object Outbox {
    def create: IO[Outbox] =
      Ref.of[IO, Vector[OutboxRecord]](Vector.empty).map(new Outbox(_))
  }

  def publish(record: OutboxRecord, failOnce: Ref[IO, Set[Long]]): IO[PublishResult] =
    failOnce.modify { current =>
      if (current.contains(record.id)) (current - record.id) -> true
      else current -> false
    }.flatMap { shouldFail =>
      if (shouldFail) {
        IO.println(s"[publish] 首次发布失败，准备重试: eventId=${record.id}") *>
          IO.raiseError(new RuntimeException("remote webhook timeout"))
      } else {
        IO.println(s"[publish] 发布成功: eventId=${record.id}, payload=${record.payload}, attempts=${record.attempts}") *>
          PublishResult(record.id, "published").pure[IO]
      }
    }

  def retryLoop(outbox: Outbox, failOnce: Ref[IO, Set[Long]]): Stream[IO, PublishResult] =
    Stream
      .repeatEval(outbox.loadPending)
      .metered(150.millis)
      .takeThrough(_.nonEmpty)
      .flatMap(records => Stream.emits(records))
      .evalMap { record =>
        publish(record, failOnce).attempt.flatMap {
          case Right(result) =>
            outbox.markPublished(record.id).as(result)

          case Left(error) =>
            outbox.markRetried(record.id) *>
              IO.println(s"[publish] 保留事件等待下一轮: eventId=${record.id}, error=${error.getMessage}") *>
              PublishResult(record.id, "retry-scheduled").pure[IO]
        }
      }

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== fs2 Outbox 重试发布流 ===")
      outbox <- Outbox.create
      _ <- outbox.seed(
        List(
          OutboxRecord(1L, "order-100|BTC-101|2", attempts = 0),
          OutboxRecord(2L, "order-200|ETH-202|1", attempts = 0)
        )
      )
      failOnce <- Ref.of[IO, Set[Long]](Set(1L))
      results <- retryLoop(outbox, failOnce).compile.toList
      remaining <- outbox.loadPending
      _ <- IO.println(s"发布轨迹: $results")
      _ <- IO.println(s"最终待发布事件: $remaining")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- fs2 很适合表达‘定时扫描待处理记录 → 尝试发布 → 失败后等待下轮’这种后台流")
      _ <- IO.println("- 失败时只更新重试次数，不把事件从 outbox 删除，才能保证最终一致性")
      _ <- IO.println("- 下一步会把发布边界推进到 http4s webhook，并最终下沉到真实 Doobie 事务里")
    } yield ()
}
