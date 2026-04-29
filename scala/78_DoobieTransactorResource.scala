//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.tpolecat::doobie-core:1.0.0-RC8"
//> using dep "org.tpolecat::doobie-h2:1.0.0-RC8"

/**
 * Scala 函数式编程 Demo 78: 用 Resource 管理 Doobie Transactor
 *
 * 前面已经把服务边界和流式边界都补得很完整了，
 * 这一版继续正式进入数据库资源管理：
 *
 * - `ConnectionIO` 负责描述“在连接里要做什么”
 * - `Transactor` 负责描述“怎么拿连接并跑事务”
 * - `Resource` 则负责把数据库生命周期放到明确边界里
 */
import cats.effect.{IO, IOApp, Resource}
import cats.syntax.all._
import doobie._
import doobie.implicits._

object DoobieTransactorResource extends IOApp.Simple {

  final case class InventoryItem(sku: String, name: String, stock: Int)
  final case class ReservationLog(requestId: String, sku: String, units: Int)

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
          name varchar not null,
          stock int not null
        )
      """.update.run
      _ <- sql"""
        create table if not exists reservation_log (
          request_id varchar primary key,
          sku varchar not null,
          units int not null
        )
      """.update.run
    } yield ()

  val seed: ConnectionIO[Unit] =
    Update[(String, String, Int)](
      "insert into inventory_item (sku, name, stock) values (?, ?, ?)"
    ).updateMany(
      List(
        ("btc-book", "Bitcoin Handbook", 10),
        ("eth-book", "Ethereum Handbook", 6)
      )
    ).void

  def reserve(requestId: String, sku: String, units: Int): ConnectionIO[Int] =
    for {
      current <- sql"select stock from inventory_item where sku = $sku".query[Int].unique
      next = current - units
      _ <- sql"update inventory_item set stock = $next where sku = $sku".update.run
      _ <- sql"insert into reservation_log (request_id, sku, units) values ($requestId, $sku, $units)".update.run
    } yield next

  val loadInventory: ConnectionIO[List[InventoryItem]] =
    sql"select sku, name, stock from inventory_item order by sku".query[InventoryItem].to[List]

  val loadLogs: ConnectionIO[List[ReservationLog]] =
    sql"select request_id, sku, units from reservation_log order by request_id".query[ReservationLog].to[List]

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== 用 Resource 管理 Transactor，并验证事务回滚 ===")
      _ <- transactorResource("demo78").use { xa =>
        for {
          _ <- (createSchema *> seed).transact(xa)
          before <- loadInventory.transact(xa)
          _ <- IO.println(s"初始库存: $before")

          remaining <- reserve("req-100", "btc-book", 2).transact(xa)
          _ <- IO.println(s"第一次预留后剩余库存: $remaining")

          failed <- reserve("req-100", "btc-book", 3).transact(xa).attempt
          _ <- failed match {
            case Left(error) => IO.println(s"重复 requestId 导致事务失败: ${error.getMessage}")
            case Right(value) => IO.println(s"意外成功: $value")
          }

          afterRollback <- loadInventory.transact(xa)
          logs <- loadLogs.transact(xa)
          _ <- IO.println(s"回滚后的库存: $afterRollback")
          _ <- IO.println(s"成功写入的日志: $logs")
        } yield ()
      }

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- `ConnectionIO` 只描述数据库动作本身，不直接决定连接生命周期")
      _ <- IO.println("- `transact` 才会真正进入数据库，并把一串动作包进事务边界")
      _ <- IO.println("- 当中途 SQL 失败时，前面已执行的更新会一起回滚，这正是事务的核心价值")
    } yield ()
}
