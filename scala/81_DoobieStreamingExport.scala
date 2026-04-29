//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.13.0"
//> using dep "org.tpolecat::doobie-core:1.0.0-RC8"
//> using dep "org.tpolecat::doobie-h2:1.0.0-RC8"

/**
 * Scala 函数式编程 Demo 81: Doobie 流式导出数据库报表
 *
 * 80 号 Demo 已经把真实数据库测试闭环补齐了，
 * 这一版继续看数据库里的另一类高频场景：导出报表。
 *
 * - 小结果集可以 `to[List]` 一次性取回
 * - 大结果集更适合 `query.stream` 按行流出
 * - 这正是 CSV 导出、批量同步、报表下载的核心模型
 */
import cats.effect.{IO, IOApp, Resource}
import cats.syntax.all._
import doobie._
import doobie.implicits._
import fs2.Stream

object DoobieStreamingExport extends IOApp.Simple {

  final case class DailySales(day: String, sku: String, units: Int, revenue: Double)

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
    sql"""
      create table if not exists daily_sales (
        day varchar not null,
        sku varchar not null,
        units int not null,
        revenue double not null
      )
    """.update.run.void

  val seed: ConnectionIO[Unit] =
    Update[(String, String, Int, Double)](
      "insert into daily_sales (day, sku, units, revenue) values (?, ?, ?, ?)"
    ).updateMany(
      List(
        ("2026-04-01", "BTC-101", 12, 1200.0),
        ("2026-04-01", "ETH-202", 8, 860.5),
        ("2026-04-02", "BTC-101", 5, 510.25),
        ("2026-04-02", "SOL-303", 15, 730.0)
      )
    ).void

  def exportLine(row: DailySales): String =
    f"${row.day},${row.sku},${row.units},${row.revenue}%.2f\n"

  def exportCsv(xa: Transactor[IO]): Stream[IO, String] =
    Stream.emit("day,sku,units,revenue\n") ++
      sql"select day, sku, units, revenue from daily_sales order by day, sku"
        .query[DailySales]
        .stream
        .transact(xa)
        .map(exportLine)

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== 用 Doobie query.stream 流式导出数据库报表 ===")
      _ <- transactorResource("demo81").use { xa =>
        for {
          _ <- (createSchema *> seed).transact(xa)
          eager <- sql"select day, sku, units, revenue from daily_sales order by day, sku"
            .query[DailySales]
            .to[List]
            .transact(xa)
          _ <- IO.println(s"一次性加载结果条数=${eager.size}")

          lines <- exportCsv(xa)
            .evalTap(line => IO.println(s"[csv] ${line.trim}"))
            .compile
            .toList

          _ <- IO.println(s"导出总行数（含表头）=${lines.size}")
        } yield ()
      }

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- `query.to[List]` 适合小结果集，但会把所有行一次性装进内存")
      _ <- IO.println("- `query.stream` 会把结果变成 `Stream`，更适合 CSV 导出、同步和下载")
      _ <- IO.println("- 这也是 Doobie 和 fs2 能自然衔接的关键：数据库结果本身就可以是流")
    } yield ()
}
