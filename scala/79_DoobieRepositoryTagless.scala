//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.tpolecat::doobie-core:1.0.0-RC8"
//> using dep "org.tpolecat::doobie-h2:1.0.0-RC8"

/**
 * Scala 函数式编程 Demo 79: Tagless Repository + Doobie SQL 解释器
 *
 * 78 号 Demo 已经把 Transactor 和事务边界走通了，
 * 这一版继续把数据库访问整理成更接近真实项目的结构：
 *
 * - trait 先定义仓储代数
 * - service 只依赖仓储能力，不依赖具体 SQL
 * - doobie interpreter 负责把仓储动作解释成真实数据库访问
 */
import cats.Monad
import cats.effect.{IO, IOApp, Resource}
import cats.syntax.all._
import doobie._
import doobie.implicits._

object DoobieRepositoryTagless extends IOApp.Simple {

  final case class CatalogItem(sku: String, name: String, stock: Int, active: Boolean)
  final case class CreateCatalogItem(sku: String, name: String, stock: Int)

  trait CatalogRepository[F[_]] {
    def findBySku(sku: String): F[Option[CatalogItem]]
    def create(item: CatalogItem): F[CatalogItem]
    def listActive: F[List[CatalogItem]]
  }

  final class CatalogService[F[_]: Monad](repo: CatalogRepository[F]) {
    def register(request: CreateCatalogItem): F[Either[String, CatalogItem]] =
      if (request.sku.trim.isEmpty) "SKU 不能为空".asLeft[CatalogItem].pure[F]
      else if (request.name.trim.isEmpty) "名称不能为空".asLeft[CatalogItem].pure[F]
      else if (request.stock < 0) "库存不能为负数".asLeft[CatalogItem].pure[F]
      else {
        val normalized = request.copy(sku = request.sku.trim, name = request.name.trim)
        repo.findBySku(normalized.sku).flatMap {
          case Some(_) => s"SKU 已存在: ${normalized.sku}".asLeft[CatalogItem].pure[F]
          case None =>
            repo.create(
              CatalogItem(
                sku = normalized.sku,
                name = normalized.name,
                stock = normalized.stock,
                active = normalized.stock > 0
              )
            ).map(_.asRight[String])
        }
      }

    def listAvailable: F[List[CatalogItem]] = repo.listActive
  }

  final class DoobieCatalogRepository extends CatalogRepository[ConnectionIO] {
    def findBySku(sku: String): ConnectionIO[Option[CatalogItem]] =
      sql"select sku, name, stock, active from catalog_item where sku = $sku"
        .query[CatalogItem]
        .option

    def create(item: CatalogItem): ConnectionIO[CatalogItem] =
      sql"insert into catalog_item (sku, name, stock, active) values (${item.sku}, ${item.name}, ${item.stock}, ${item.active})"
        .update
        .run
        .map(_ => item)

    def listActive: ConnectionIO[List[CatalogItem]] =
      sql"select sku, name, stock, active from catalog_item where active = true order by sku"
        .query[CatalogItem]
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
      create table if not exists catalog_item (
        sku varchar primary key,
        name varchar not null,
        stock int not null,
        active boolean not null
      )
    """.update.run.void

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== 用 Doobie interpreter 落地 Tagless Repository ===")
      _ <- transactorResource("demo79").use { xa =>
        val service = new CatalogService[ConnectionIO](new DoobieCatalogRepository)

        (for {
          _ <- createSchema
          first <- service.register(CreateCatalogItem("BTC-101", "Bitcoin Handbook", 10))
          duplicate <- service.register(CreateCatalogItem("BTC-101", "Duplicate Bitcoin Handbook", 3))
          zeroStock <- service.register(CreateCatalogItem("OPS-000", "Out of Stock Guide", 0))
          available <- service.listAvailable
        } yield (first, duplicate, zeroStock, available)).transact(xa).flatMap {
          case (first, duplicate, zeroStock, available) =>
            for {
              _ <- IO.println(s"首次注册: $first")
              _ <- IO.println(s"重复 SKU: $duplicate")
              _ <- IO.println(s"零库存条目: $zeroStock")
              _ <- IO.println(s"当前可售目录: $available")
            } yield ()
        }
      }

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- Service 只依赖 `CatalogRepository[F]`，并不知道底层是不是 SQL")
      _ <- IO.println("- Doobie interpreter 把仓储动作解释成 `ConnectionIO`，最后统一在边界处 transact")
      _ <- IO.println("- 这就是 Tagless Final 在真实数据库模块里的典型落地方式")
    } yield ()
}
