//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.tpolecat::doobie-core:1.0.0-RC8"
//> using dep "org.tpolecat::doobie-h2:1.0.0-RC8"

/**
 * Scala 函数式编程 Demo 84: Tagless 批量导入模块 + Doobie 解释器
 *
 * 82 号 Demo 已经把 CSV 解析切批做好了，
 * 这一版继续把“批量导入”整理成更像真实项目的模块结构：
 *
 * - service 只依赖仓储代数，不直接写 SQL
 * - repository interpreter 负责把导入动作落到 Doobie
 * - 导入过程会同时处理新增、更新和拒绝记录
 */
import cats.Monad
import cats.effect.{IO, IOApp, Resource}
import cats.syntax.all._
import doobie._
import doobie.implicits._

object DoobieBatchImportTagless extends IOApp.Simple {

  final case class InventoryItem(sku: String, warehouse: String, name: String, stock: Int)
  final case class ImportRow(sku: String, warehouse: String, name: String, stock: Int)
  final case class ImportSummary(inserted: Int, updated: Int, rejected: List[String])
  final case class ImportStats(inserted: Int, updated: Int, rejected: List[String])

  object ImportStats {
    val empty: ImportStats = ImportStats(inserted = 0, updated = 0, rejected = Nil)
  }

  trait InventoryRepository[F[_]] {
    def find(sku: String, warehouse: String): F[Option[InventoryItem]]
    def insert(item: InventoryItem): F[Unit]
    def update(item: InventoryItem): F[Unit]
    def listAvailable(warehouse: String): F[List[InventoryItem]]
  }

  final class InventoryImportService[F[_]: Monad](repo: InventoryRepository[F]) {
    private def validate(row: ImportRow): Either[String, InventoryItem] =
      if (row.sku.trim.isEmpty) Left("SKU 不能为空")
      else if (row.warehouse.trim.isEmpty) Left(s"仓库不能为空: ${row.sku}")
      else if (row.name.trim.isEmpty) Left(s"名称不能为空: ${row.sku}")
      else if (row.stock < 0) Left(s"库存不能为负数: ${row.sku}")
      else Right(InventoryItem(row.sku.trim, row.warehouse.trim, row.name.trim, row.stock))

    def importBatch(rows: List[ImportRow]): F[ImportSummary] =
      rows.foldLeftM(ImportStats.empty) { (stats, row) =>
        validate(row) match {
          case Left(error) => (stats.copy(rejected = error :: stats.rejected)).pure[F]
          case Right(item) =>
            repo.find(item.sku, item.warehouse).flatMap {
              case Some(_) => repo.update(item).as(stats.copy(updated = stats.updated + 1))
              case None => repo.insert(item).as(stats.copy(inserted = stats.inserted + 1))
            }
        }
      }.map(stats => ImportSummary(stats.inserted, stats.updated, stats.rejected.reverse))

    def exportAvailable(warehouse: String): F[List[InventoryItem]] =
      repo.listAvailable(warehouse)
  }

  final class DoobieInventoryRepository extends InventoryRepository[ConnectionIO] {
    def find(sku: String, warehouse: String): ConnectionIO[Option[InventoryItem]] =
      sql"select sku, warehouse, name, stock from inventory_item where sku = $sku and warehouse = $warehouse"
        .query[InventoryItem]
        .option

    def insert(item: InventoryItem): ConnectionIO[Unit] =
      sql"insert into inventory_item (sku, warehouse, name, stock) values (${item.sku}, ${item.warehouse}, ${item.name}, ${item.stock})"
        .update
        .run
        .void

    def update(item: InventoryItem): ConnectionIO[Unit] =
      sql"update inventory_item set name = ${item.name}, stock = ${item.stock} where sku = ${item.sku} and warehouse = ${item.warehouse}"
        .update
        .run
        .void

    def listAvailable(warehouse: String): ConnectionIO[List[InventoryItem]] =
      sql"select sku, warehouse, name, stock from inventory_item where warehouse = $warehouse and stock > 0 order by sku"
        .query[InventoryItem]
        .to[List]
  }

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
      create table if not exists inventory_item (
        sku varchar not null,
        warehouse varchar not null,
        name varchar not null,
        stock int not null,
        primary key (sku, warehouse)
      )
    """.update.run.void

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== 用 Tagless Repository 组织批量导入 ===")
      _ <- transactorResource("demo84").use { xa =>
        val service = new InventoryImportService[ConnectionIO](new DoobieInventoryRepository)
        val rows = List(
          ImportRow("BTC-101", "cn", "Bitcoin Handbook", 12),
          ImportRow("ETH-202", "cn", "Ethereum Guide", 5),
          ImportRow("BTC-101", "cn", "Bitcoin Handbook Second Edition", 18),
          ImportRow("BAD-000", "cn", "", 3),
          ImportRow("OPS-404", "us", "Ops Manual", -1)
        )

        (for {
          _ <- createSchema
          summary <- service.importBatch(rows)
          report <- service.exportAvailable("cn")
        } yield (summary, report)).transact(xa).flatMap {
          case (summary, report) =>
            for {
              _ <- IO.println(s"导入结果: $summary")
              _ <- IO.println(s"当前 cn 仓可售记录: $report")
            } yield ()
        }
      }

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- 批量导入首先是业务规则问题，SQL 只是最后一层解释器")
      _ <- IO.println("- Service 可以只关心新增 / 更新 / 拒绝，不直接依赖数据库实现细节")
      _ <- IO.println("- 这能让同一套导入逻辑既能跑在测试解释器里，也能落到真实 Doobie 仓储")
    } yield ()
}
