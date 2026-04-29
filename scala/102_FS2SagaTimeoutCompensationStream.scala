//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.12.2"

/**
 * Scala 函数式编程 Demo 102: fs2 Saga 超时补偿流
 *
 * 101 号 Demo 先把 Saga 补偿的最小模型讲清楚了，
 * 这一版继续把“支付超时扫描 + 自动补偿”放到 fs2 后台流里。
 */
import cats.effect.{IO, IOApp, Ref}
import cats.syntax.all._
import fs2.Stream

import scala.concurrent.duration._

object FS2SagaTimeoutCompensationStream extends IOApp.Simple {

  final case class SagaInstance(
      sagaId: String,
      orderId: String,
      sku: String,
      quantity: Int,
      paymentDeadlineTick: Long,
      compensationAttempts: Int,
      status: String
  )

  final case class TimeoutTrace(tick: Long, sagaId: String, status: String, attempts: Int)

  final class SagaStore private (state: Ref[IO, Vector[SagaInstance]]) {
    def seed(instances: List[SagaInstance]): IO[Unit] = state.set(instances.toVector)

    def expiredAt(tick: Long): IO[Vector[SagaInstance]] =
      state.get.map(_.filter(instance => instance.status == "waiting-payment" && tick >= instance.paymentDeadlineTick))

    def markPaid(sagaId: String): IO[Unit] =
      state.update(_.map { instance =>
        if (instance.sagaId == sagaId && instance.status == "waiting-payment") {
          instance.copy(status = "completed")
        } else {
          instance
        }
      })

    def bumpCompensationAttempts(sagaId: String): IO[Unit] =
      state.update(_.map { instance =>
        if (instance.sagaId == sagaId) {
          instance.copy(compensationAttempts = instance.compensationAttempts + 1)
        } else {
          instance
        }
      })

    def markCompensated(sagaId: String): IO[Unit] =
      state.update(_.map { instance =>
        if (instance.sagaId == sagaId) {
          instance.copy(status = "compensated")
        } else {
          instance
        }
      })

    def snapshot: IO[Vector[SagaInstance]] = state.get
  }

  object SagaStore {
    def create: IO[SagaStore] =
      Ref.of[IO, Vector[SagaInstance]](Vector.empty).map(new SagaStore(_))
  }

  def compensate(instance: SagaInstance, failOnce: Ref[IO, Set[String]], tick: Long): IO[TimeoutTrace] =
    failOnce.modify { current =>
      if (current.contains(instance.sagaId)) (current - instance.sagaId) -> true
      else current -> false
    }.flatMap { shouldFail =>
      if (shouldFail) {
        IO.println(s"[compensate] 首次补偿失败: tick=$tick, sagaId=${instance.sagaId}") *>
          IO.raiseError(new RuntimeException("inventory release timeout"))
      } else {
        IO.println(s"[compensate] 补偿成功: tick=$tick, sagaId=${instance.sagaId}, orderId=${instance.orderId}") *>
          TimeoutTrace(tick, instance.sagaId, "compensated", instance.compensationAttempts).pure[IO]
      }
    }

  def compensationLoop(store: SagaStore, failOnce: Ref[IO, Set[String]]): Stream[IO, TimeoutTrace] =
    Stream
      .range(1, 5)
      .covary[IO]
      .metered(150.millis)
      .evalTap { tick =>
        if (tick == 2) {
          store.markPaid("saga-200") *>
            IO.println("[payment] saga-200 在截止前收到支付回调，直接完成")
        } else {
          IO.unit
        }
      }
      .flatMap { tick =>
        Stream.eval(store.expiredAt(tick.toLong)).flatMap { instances =>
          Stream
            .emits(instances)
            .covary[IO]
            .evalMap { instance =>
              compensate(instance, failOnce, tick.toLong).attempt.flatMap {
                case Right(trace) =>
                  store.markCompensated(instance.sagaId).as(trace)

                case Left(error) =>
                  store.bumpCompensationAttempts(instance.sagaId) *>
                    IO.println(s"[compensate] 保留 Saga 等待下轮: sagaId=${instance.sagaId}, error=${error.getMessage}") *>
                    TimeoutTrace(
                      tick = tick.toLong,
                      sagaId = instance.sagaId,
                      status = "retry-scheduled",
                      attempts = instance.compensationAttempts + 1
                    ).pure[IO]
              }
            }
        }
      }

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== fs2 Saga 超时补偿流 ===")
      store <- SagaStore.create
      _ <- store.seed(
        List(
          SagaInstance("saga-100", "order-100", "BTC-101", 2, paymentDeadlineTick = 1L, compensationAttempts = 0, status = "waiting-payment"),
          SagaInstance("saga-200", "order-200", "ETH-202", 1, paymentDeadlineTick = 3L, compensationAttempts = 0, status = "waiting-payment")
        )
      )
      failOnce <- Ref.of[IO, Set[String]](Set("saga-100"))
      traces <- compensationLoop(store, failOnce).compile.toList
      finalState <- store.snapshot
      _ <- IO.println(s"补偿轨迹: $traces")
      _ <- IO.println(s"最终 Saga 状态: $finalState")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- fs2 很适合表达‘周期性扫描待处理 Saga → 命中超时 → 执行补偿’这类后台工作流")
      _ <- IO.println("- 超时补偿本身也可能失败，所以要把 attempts 和状态推进一起保留下来")
      _ <- IO.println("- 真正收到支付成功回调的 Saga 应该及时退出超时扫描，不再误触发补偿")
    } yield ()
}
