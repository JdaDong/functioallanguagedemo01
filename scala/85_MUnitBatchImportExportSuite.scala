//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.13.0"
//> using dep "org.tpolecat::doobie-core:1.0.0-RC8"
//> using dep "org.tpolecat::doobie-h2:1.0.0-RC8"
//> using dep "org.http4s::http4s-core:0.23.33"
//> using dep "org.http4s::http4s-dsl:0.23.33"
//> using dep "org.typelevel::munit-cats-effect-3:1.0.7"

/**
 * Scala 函数式编程 Demo 85: 批量导入 + CSV 导出集成测试
 *
 * 84 号 Demo 已经把批量导入模块化了，
 * 这一版继续把“写库 + 导出报表”一起纳入自动化回归。
 *
 * - 让 import service 跑在真实 H2 数据库上
 * - 再让 http4s 导出路由从真实数据库读出 CSV
 * - 验证更新、过滤、拒绝记录和报表内容是否一致
 */
import cats.Monad
import cats.effect.{IO, Resource}
import cats.syntax.all._
import doobie._
import doobie.implicits._
import fs2.{Stream, text}
import munit.CatsEffectSuite
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.typelevel.ci._

import java.util.UUID

object MUnitBatchImportExportSuite {
  final case class InventoryItem(sku: String, warehouse: String, name: String, stock: Int)
  final case class ImportRow(sku: String, warehouse: String, name: String, stock: Int)
  final case class ImportSummary(inserted: Int, updated: Int, rejected: List[String])
  final case class Environment(
      service: InventoryImportService[ConnectionIO],
      xa: Transactor[IO],
      app: HttpApp[IO]
  )
  final case class ImportStats(inserted: Int, updated: Int, rejected: List[String])

  object ImportStats {
    val empty: ImportStats = ImportStats(inserted = 0, updated = 0, rejected = Nil)
  }

  trait InventoryRepository[F[_]] {
    def find(sku: String, warehouse: String): F[Option[InventoryItem]]
    def insert(item: InventoryItem): F[Unit]
    def update(item: InventoryItem): F[Unit]
    def listAvailable(warehouse: String): F[List[InventoryItem]]
    def countAll: F[Int]
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

    def exportLines(warehouse: String): F[List[String]] =
      repo.listAvailable(warehouse).map { rows =>
        "warehouse,sku,name,stock" :: rows.map(row => s"${row.warehouse},${row.sku},${row.name},${row.stock}")
      }

    def countAll: F[Int] = repo.countAll
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

    def countAll: ConnectionIO[Int] =
      sql"select count(*) from inventory_item".query[Int].unique
  }

  def transactorResource(label: String): Resource[IO, Transactor[IO]] =
    Resource.eval(
      IO(
        Transactor.fromDriverManager[IO](
          driver = "org.h2.Driver",
          url = s"jdbc:h2:mem:$label-${UUID.randomUUID().toString.replace("-", "")};DB_CLOSE_DELAY=-1",
          user = "sa",
          password = "",
          logHandler = None
        )
      )
    )

  val createSchema: ConnectionIO[Unit] =
    sql"""
      create table inventory_item (
        sku varchar not null,
        warehouse varchar not null,
        name varchar not null,
        stock int not null,
        primary key (sku, warehouse)
      )
    """.update.run.void

  def buildApp(service: InventoryImportService[ConnectionIO], xa: Transactor[IO]): HttpApp[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "reports" / warehouse / "inventory.csv" =>
        val body = Stream
          .evalSeq(service.exportLines(warehouse).transact(xa))
          .map(_ + "\n")
          .through(text.utf8.encode)

        Ok(body).map(
          _.putHeaders(
            Header.Raw(ci"Content-Type", "text/csv; charset=utf-8"),
            Header.Raw(ci"Content-Disposition", s"attachment; filename=inventory-$warehouse.csv")
          )
        )
    }.orNotFound
}

class MUnitBatchImportExportSuite extends CatsEffectSuite {
  import MUnitBatchImportExportSuite._

  def withEnvironment[A](label: String)(program: Environment => IO[A]): IO[A] =
    transactorResource(label).use { xa =>
      val service = new InventoryImportService[ConnectionIO](new DoobieInventoryRepository)
      val app = buildApp(service, xa)
      createSchema.transact(xa) *> program(Environment(service, xa, app))
    }

  def responseBody(response: Response[IO]): IO[String] =
    response.body.through(text.utf8.decode).compile.string

  test("有效批量导入后，CSV 导出应返回更新后的真实数据库内容") {
    withEnvironment("import-export") { env =>
      val rows = List(
        ImportRow("BTC-101", "cn", "Bitcoin Handbook", 12),
        ImportRow("ETH-202", "cn", "Ethereum Guide", 5),
        ImportRow("BTC-101", "cn", "Bitcoin Handbook Second Edition", 18)
      )

      for {
        summary <- env.service.importBatch(rows).transact(env.xa)
        count <- env.service.countAll.transact(env.xa)
        response <- env.app(Request[IO](Method.GET, uri"/reports/cn/inventory.csv"))
        body <- responseBody(response)
      } yield {
        assertEquals(summary, ImportSummary(inserted = 2, updated = 1, rejected = Nil))
        assertEquals(count, 2)
        assertEquals(response.status, Status.Ok)
        assert(body.contains("warehouse,sku,name,stock"))
        assert(body.contains("cn,BTC-101,Bitcoin Handbook Second Edition,18"))
        assert(body.contains("cn,ETH-202,Ethereum Guide,5"))
      }
    }
  }

  test("无效记录应该被拒绝，零库存记录不应出现在导出报表里") {
    withEnvironment("reject-invalid") { env =>
      val rows = List(
        ImportRow("BAD-000", "cn", "", 3),
        ImportRow("OPS-404", "cn", "Ops Manual", -1),
        ImportRow("ZERO-000", "cn", "Zero Stock", 0)
      )

      for {
        summary <- env.service.importBatch(rows).transact(env.xa)
        count <- env.service.countAll.transact(env.xa)
        response <- env.app(Request[IO](Method.GET, uri"/reports/cn/inventory.csv"))
        body <- responseBody(response)
      } yield {
        assertEquals(summary.rejected, List("名称不能为空: BAD-000", "库存不能为负数: OPS-404"))
        assertEquals(summary.inserted, 1)
        assertEquals(summary.updated, 0)
        assertEquals(count, 1)
        assertEquals(body.trim, "warehouse,sku,name,stock")
      }
    }
  }

  test("未命中导出路由时应该返回 404") {
    withEnvironment("route-404") { env =>
      env.app(Request[IO](Method.GET, uri"/unknown")).map { response =>
        assertEquals(response.status, Status.NotFound)
      }
    }
  }
}
