//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"

/**
 * Scala 函数式编程 Demo 101: cats-effect Saga 协调器
 *
 * 96 到 100 号 Demo 已经把可靠投递与消费端幂等闭环补齐了，
 * 这一组继续看更上层的业务问题：
 * 当一个流程要跨多个步骤推进时，失败后怎样显式补偿？
 */
import cats.effect.{IO, IOApp, Ref}
import cats.syntax.all._

object CatsEffectSagaCoordinator extends IOApp.Simple {

  final case class SagaState(
      orderId: String,
      sku: String,
      quantity: Int,
      amount: BigDecimal,
      status: String,
      compensationReason: Option[String]
  )

  final case class Snapshot(
      availableStock: Map[String, Int],
      reservations: Map[String, Int],
      chargedOrders: Set[String],
      sagas: Map[String, SagaState]
  )

  final class CheckoutService private (
      stock: Ref[IO, Map[String, Int]],
      reservations: Ref[IO, Map[String, Int]],
      chargedOrders: Ref[IO, Set[String]],
      sagas: Ref[IO, Map[String, SagaState]],
      failFirstPaymentFor: Ref[IO, Set[String]]
  ) {

    def checkout(orderId: String, sku: String, quantity: Int, amount: BigDecimal): IO[SagaState] =
      for {
        _ <- createSaga(orderId, sku, quantity, amount)
        _ <- reserveInventory(orderId, sku, quantity)
        _ <- updateSagaStatus(orderId, "charging-payment", None)
        result <- chargePayment(orderId, amount).attempt
        saga <- result match {
          case Right(_) =>
            updateSagaStatus(orderId, "completed", None)

          case Left(error) =>
            releaseInventory(orderId, sku) *>
              updateSagaStatus(orderId, "compensated", Some(error.getMessage))
        }
      } yield saga

    def snapshot: IO[Snapshot] =
      (stock.get, reservations.get, chargedOrders.get, sagas.get).mapN(Snapshot.apply)

    private def createSaga(orderId: String, sku: String, quantity: Int, amount: BigDecimal): IO[Unit] =
      sagas.update(
        _.updated(
          orderId,
          SagaState(orderId, sku.trim, quantity, amount, status = "created", compensationReason = None)
        )
      ) *> IO.println(s"[saga] 创建工作流: orderId=$orderId, sku=$sku, quantity=$quantity, amount=$amount")

    private def updateSagaStatus(orderId: String, status: String, reason: Option[String]): IO[SagaState] =
      sagas.modify { current =>
        val updated = current(orderId).copy(status = status, compensationReason = reason)
        current.updated(orderId, updated) -> updated
      }.flatTap { saga =>
        val message = reason.fold(saga.status)(r => s"${saga.status}, reason=$r")
        IO.println(s"[saga] 状态推进: orderId=$orderId -> $message")
      }

    private def reserveInventory(orderId: String, sku: String, quantity: Int): IO[Unit] =
      stock.modify { current =>
        current.get(sku) match {
          case Some(available) if available >= quantity =>
            current.updated(sku, available - quantity) -> Right(available - quantity)
          case Some(available) =>
            current -> Left(new RuntimeException(s"库存不足: sku=$sku, available=$available, requested=$quantity"))
          case None =>
            current -> Left(new RuntimeException(s"库存不存在: sku=$sku"))
        }
      }.flatMap {
        case Right(remaining) =>
          reservations.update(_.updated(orderId, quantity)) *>
            updateSagaStatus(orderId, "inventory-reserved", None).void *>
            IO.println(s"[inventory] 预留成功: orderId=$orderId, remaining=$remaining")

        case Left(error) =>
          updateSagaStatus(orderId, "failed", Some(error.getMessage)).void *>
            IO.raiseError(error)
      }

    private def releaseInventory(orderId: String, sku: String): IO[Unit] =
      reservations.modify { current =>
        val quantity = current.getOrElse(orderId, 0)
        current - orderId -> quantity
      }.flatMap { quantity =>
        stock.update(current => current.updated(sku, current.getOrElse(sku, 0) + quantity)) *>
          IO.println(s"[inventory] 执行补偿释放: orderId=$orderId, released=$quantity")
      }

    private def chargePayment(orderId: String, amount: BigDecimal): IO[Unit] =
      failFirstPaymentFor.modify { current =>
        if (current.contains(orderId)) (current - orderId) -> true
        else current -> false
      }.flatMap { shouldFail =>
        if (shouldFail) {
          IO.println(s"[payment] 模拟首笔扣款失败: orderId=$orderId, amount=$amount") *>
            IO.raiseError(new RuntimeException("payment gateway timeout"))
        } else {
          chargedOrders.update(_ + orderId) *>
            IO.println(s"[payment] 扣款成功: orderId=$orderId, amount=$amount")
        }
      }
  }

  object CheckoutService {
    def create: IO[CheckoutService] =
      for {
        stock <- Ref.of[IO, Map[String, Int]](Map("BTC-101" -> 10, "ETH-202" -> 6))
        reservations <- Ref.of[IO, Map[String, Int]](Map.empty)
        chargedOrders <- Ref.of[IO, Set[String]](Set.empty)
        sagas <- Ref.of[IO, Map[String, SagaState]](Map.empty)
        failFirstPaymentFor <- Ref.of[IO, Set[String]](Set("order-100"))
      } yield new CheckoutService(stock, reservations, chargedOrders, sagas, failFirstPaymentFor)
  }

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== cats-effect Saga 协调器 ===")
      service <- CheckoutService.create

      compensated <- service.checkout("order-100", "BTC-101", 2, BigDecimal(128.5))
      _ <- IO.println(s"补偿后的 Saga: $compensated")
      afterCompensation <- service.snapshot
      _ <- IO.println(s"补偿后快照: $afterCompensation")

      completed <- service.checkout("order-200", "ETH-202", 1, BigDecimal(88.0))
      _ <- IO.println(s"成功完成的 Saga: $completed")
      finalSnapshot <- service.snapshot
      _ <- IO.println(s"最终快照: $finalSnapshot")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- Saga 不是把远程副作用回滚掉，而是在失败后显式执行反向动作")
      _ <- IO.println("- 先预留库存、再扣款；一旦扣款失败，就要主动释放之前已经发生的库存预留")
      _ <- IO.println("- 这一版先讲清最小补偿直觉，后面会继续推进到超时扫描、HTTP 边界和数据库事务")
    } yield ()
}
