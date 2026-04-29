//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "org.tpolecat::doobie-core:1.0.0-RC8"
//> using dep "org.tpolecat::doobie-h2:1.0.0-RC8"
//> using dep "org.typelevel::munit-cats-effect-3:1.0.7"

/**
 * Scala 函数式编程 Demo 80: Doobie Repository 集成测试
 *
 * 79 号 Demo 已经把仓储代数和 Doobie 解释器搭起来了，
 * 这一版继续补上最后一块：
 *
 * - 让 service + repository 跑在真实 H2 数据库上
 * - 验证去重、过滤、校验等行为是否成立
 * - 把“数据库这层”也纳入自动化回归
 */
import cats.Monad
import cats.effect.{IO, Resource}
import cats.syntax.all._
import doobie._
import doobie.implicits._
import munit.CatsEffectSuite

import java.util.UUID

object MUnitRepositoryIntegrationSuite {
  final case class CatalogItem(sku: String, name: String, stock: Int, active: Boolean)
  final case class CreateCatalogItem(sku: String, name: String, stock: Int)

  trait CatalogRepository[F[_]] {
    def findBySku(sku: String): F[Option[CatalogItem]]
    def create(item: CatalogItem): F[CatalogItem]
    def listActive: F[List[CatalogItem]]
    def countAll: F[Int]
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
    def countAll: F[Int] = repo.countAll
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

    def countAll: ConnectionIO[Int] =
      sql"select count(*) from catalog_item".query[Int].unique
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
      create table catalog_item (
        sku varchar primary key,
        name varchar not null,
        stock int not null,
        active boolean not null
      )
    """.update.run.void
}

class MUnitRepositoryIntegrationSuite extends CatsEffectSuite {
  import MUnitRepositoryIntegrationSuite._

  def withService[A](label: String)(program: CatalogService[ConnectionIO] => Transactor[IO] => IO[A]): IO[A] =
    transactorResource(label).use { xa =>
      val service = new CatalogService[ConnectionIO](new DoobieCatalogRepository)
      createSchema.transact(xa) *> program(service)(xa)
    }

  test("首次写入后应该能在真实数据库里查到可售目录") {
    withService("create-and-list") { service => xa =>
      for {
        result <- service.register(CreateCatalogItem("BTC-101", "Bitcoin Handbook", 10)).transact(xa)
        available <- service.listAvailable.transact(xa)
        count <- service.countAll.transact(xa)
      } yield {
        assertEquals(result, Right(CatalogItem("BTC-101", "Bitcoin Handbook", 10, active = true)))
        assertEquals(available, List(CatalogItem("BTC-101", "Bitcoin Handbook", 10, active = true)))
        assertEquals(count, 1)
      }
    }
  }

  test("重复 SKU 应该被拒绝，数据库里仍然只有一条记录") {
    withService("deduplicate") { service => xa =>
      for {
        _ <- service.register(CreateCatalogItem("BTC-101", "Bitcoin Handbook", 10)).transact(xa)
        duplicate <- service.register(CreateCatalogItem("BTC-101", "Duplicate", 3)).transact(xa)
        count <- service.countAll.transact(xa)
      } yield {
        assertEquals(duplicate, Left("SKU 已存在: BTC-101"))
        assertEquals(count, 1)
      }
    }
  }

  test("零库存条目可以写入，但不应出现在可售目录里") {
    withService("inactive-filter") { service => xa =>
      for {
        zeroStock <- service.register(CreateCatalogItem("OPS-000", "Out of Stock Guide", 0)).transact(xa)
        active <- service.listAvailable.transact(xa)
        count <- service.countAll.transact(xa)
      } yield {
        assertEquals(zeroStock, Right(CatalogItem("OPS-000", "Out of Stock Guide", 0, active = false)))
        assertEquals(active, List.empty[CatalogItem])
        assertEquals(count, 1)
      }
    }
  }

  test("无效输入应该在 service 层被拦截，不写数据库") {
    withService("validation") { service => xa =>
      for {
        invalid <- service.register(CreateCatalogItem("   ", "Bad Item", 5)).transact(xa)
        count <- service.countAll.transact(xa)
      } yield {
        assertEquals(invalid, Left("SKU 不能为空"))
        assertEquals(count, 0)
      }
    }
  }
}
