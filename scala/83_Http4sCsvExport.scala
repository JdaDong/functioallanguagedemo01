//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.13.0"
//> using dep "org.http4s::http4s-core:0.23.33"
//> using dep "org.http4s::http4s-dsl:0.23.33"

/**
 * Scala 函数式编程 Demo 83: http4s CSV 下载接口
 *
 * 81 和 82 已经把数据库导出、CSV 导入的核心模型走通了，
 * 这一版继续把它们放到服务边界上：做一个真正的 CSV 下载接口。
 *
 * - 路由返回的不一定是 JSON，也可以是流式 CSV
 * - 下载接口通常会带筛选条件，例如仓库、日期、状态
 * - 客户端可以边收到边写文件，不必等待整份报表完整生成
 */
import cats.effect.{IO, IOApp}
import fs2.{Stream, text}
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.typelevel.ci._

import scala.concurrent.duration._

object Http4sCsvExport extends IOApp.Simple {

  final case class InventoryRow(warehouse: String, sku: String, name: String, stock: Int)

  object WarehouseMatcher extends OptionalQueryParamDecoderMatcher[String]("warehouse")

  val rows = List(
    InventoryRow("cn", "BTC-101", "Bitcoin Handbook", 12),
    InventoryRow("cn", "ETH-202", "Ethereum Guide", 5),
    InventoryRow("us", "SOL-303", "Solana Notes", 7),
    InventoryRow("eu", "OPS-404", "Ops Manual", 3)
  )

  def csvLines(selected: List[InventoryRow]): Stream[IO, String] =
    Stream.emit("warehouse,sku,name,stock\n") ++
      Stream
        .emits(selected)
        .covary[IO]
        .metered(40.millis)
        .map(row => s"${row.warehouse},${row.sku},${row.name},${row.stock}\n")

  val app: HttpApp[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "reports" / "inventory.csv" :? WarehouseMatcher(warehouse) =>
      val selected = rows.filter(row => warehouse.forall(_ == row.warehouse))
      val scope = warehouse.getOrElse("all")
      Ok(csvLines(selected).through(text.utf8.encode)).map(
        _.putHeaders(
          Header.Raw(ci"Content-Type", "text/csv; charset=utf-8"),
          Header.Raw(ci"Content-Disposition", s"attachment; filename=inventory-$scope.csv")
        )
      )
  }.orNotFound

  def decodeBody(response: Response[IO]): IO[String] =
    response.body.through(text.utf8.decode).compile.string

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== http4s CSV 下载接口 ===")
      response <- app(Request[IO](Method.GET, uri"/reports/inventory.csv?warehouse=cn"))
      body <- decodeBody(response)
      _ <- IO.println(s"status=${response.status.code}")
      _ <- IO.println(s"headers=${response.headers.headers.map(h => s"${h.name}: ${h.value}").mkString(" | ")}")
      _ <- IO.println("body=")
      _ <- IO.println(body)

      _ <- IO.println("=== 重点理解 ===")
      _ <- IO.println("- 下载接口可以直接返回 `Stream[IO, Byte]`，天然适合大报表")
      _ <- IO.println("- 查询参数可以决定导出范围，例如仓库、日期区间或业务状态")
      _ <- IO.println("- 这类接口常见于运营导出、对账文件、库存快照和审计报表")
    } yield ()
}
