//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.tpolecat::doobie-core:1.0.0-RC8"
//> using dep "org.tpolecat::doobie-h2:1.0.0-RC8"

/**
 * Scala 函数式编程 Demo 89: Doobie 持久化幂等写入
 *
 * 88 号 Demo 在 HTTP 边界上引入了 `Idempotency-Key`，
 * 这一版继续把真正长期可靠的幂等能力下沉到数据库边界。
 *
 * - requestId 会被持久化到数据库里
 * - 同一个 requestId 重放时返回第一次的结果
 * - 但如果同一个 requestId 搭配不同 payload，则明确报错
 */
import cats.effect.{IO, IOApp, Resource}
import cats.syntax.all._
import doobie._
import doobie.implicits._

object DoobieIdempotentReservation extends IOApp.Simple {

  final case class ReservationCommand(requestId: String, sku: String, units: Int)
  final case class ReservationReceipt(
      requestId: String,
      sku: String,
      units: Int,
      remainingStock: Int,
      replayed: Boolean
  )
  final case class StoredReservation(requestId: String, sku: String, units: Int, remainingStock: Int)
  final case class InventoryItem(sku: String, stock: Int)

  def transactorResource(dbName: String): Resource[IO, Transactor[IO]] =
    Resource.eval(
      IO(
        Transactor.fromDriverManager[IO](
          driver = "org.h2.Driver",
          url = s"jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1",
          user = "sa",
          password = "",
          logHandler = None
        )
      )
    )

  val createSchema: ConnectionIO[Unit] =
    for {
      _ <- sql"""
        create table if not exists inventory_item (
          sku varchar primary key,
          stock int not null
        )
      """.update.run
      _ <- sql"""
        create table if not exists reservation_request (
          request_id varchar primary key,
          sku varchar not null,
          units int not null,
          remaining_stock int not null
        )
      """.update.run
    } yield ()

  val seed: ConnectionIO[Unit] =
    Update[(String, Int)](
      "insert into inventory_item (sku, stock) values (?, ?)"
    ).updateMany(
      List(
        ("BTC-101", 10),
        ("ETH-202", 6)
      )
    ).void

  def loadInventory: ConnectionIO[List[InventoryItem]] =
    sql"select sku, stock from inventory_item order by sku".query[InventoryItem].to[List]

  def loadRequests: ConnectionIO[List[StoredReservation]] =
    sql"select request_id, sku, units, remaining_stock from reservation_request order by request_id"
      .query[StoredReservation]
      .to[List]

  def reserve(command: ReservationCommand): ConnectionIO[ReservationReceipt] =
    if (command.units <= 0) {
      new IllegalArgumentException("预留数量必须大于 0").raiseError[ConnectionIO, ReservationReceipt]
    } else {
      sql"select request_id, sku, units, remaining_stock from reservation_request where request_id = ${command.requestId}"
        .query[StoredReservation]
        .option
        .flatMap {
          case Some(stored) if stored.sku == command.sku && stored.units == command.units =>
            ReservationReceipt(stored.requestId, stored.sku, stored.units, stored.remainingStock, replayed = true)
              .pure[ConnectionIO]

          case Some(_) =>
            new RuntimeException("同一个 requestId 不能对应不同 payload").raiseError[ConnectionIO, ReservationReceipt]

          case None =>
            sql"select stock from inventory_item where sku = ${command.sku}".query[Int].option.flatMap {
              case None =>
                new RuntimeException(s"SKU 不存在: ${command.sku}").raiseError[ConnectionIO, ReservationReceipt]

              case Some(currentStock) if currentStock < command.units =>
                new RuntimeException(s"库存不足: ${command.sku}, 当前=$currentStock, 请求=${command.units}")
                  .raiseError[ConnectionIO, ReservationReceipt]

              case Some(currentStock) =>
                val remaining = currentStock - command.units
                for {
                  _ <- sql"update inventory_item set stock = $remaining where sku = ${command.sku}".update.run
                  _ <- sql"insert into reservation_request (request_id, sku, units, remaining_stock) values (${command.requestId}, ${command.sku}, ${command.units}, $remaining)"
                    .update
                    .run
                } yield ReservationReceipt(command.requestId, command.sku, command.units, remaining, replayed = false)
            }
        }
    }

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== Doobie 持久化幂等写入 ===")
      _ <- transactorResource("demo89").use { xa =>
        for {
          _ <- (createSchema *> seed).transact(xa)

          first <- reserve(ReservationCommand("req-100", "BTC-101", 2)).transact(xa)
          _ <- IO.println(s"first = $first")

          replay <- reserve(ReservationCommand("req-100", "BTC-101", 2)).transact(xa)
          _ <- IO.println(s"replay = $replay")

          conflict <- reserve(ReservationCommand("req-100", "BTC-101", 3)).transact(xa).attempt
          _ <- IO.println(s"conflict = ${conflict.leftMap(_.getMessage)}")

          second <- reserve(ReservationCommand("req-200", "ETH-202", 1)).transact(xa)
          _ <- IO.println(s"second = $second")

          inventory <- loadInventory.transact(xa)
          requests <- loadRequests.transact(xa)
          _ <- IO.println(s"inventory = $inventory")
          _ <- IO.println(s"requests = $requests")
        } yield ()
      }

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- 真正长期幂等必须把 requestId 和首次结果一起持久化，否则重启后就失效")
      _ <- IO.println("- 同一个 requestId 再次重放时，直接返回第一次结果，不再重复扣减库存")
      _ <- IO.println("- 如果同一个 requestId 搭配不同 payload，必须显式拒绝，避免把错误重试伪装成成功")
    } yield ()
}
