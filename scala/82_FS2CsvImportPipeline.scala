//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.13.0"

/**
 * Scala 函数式编程 Demo 82: fs2 CSV 导入管道
 *
 * 81 号 Demo 把数据库里的结果流式导出了，
 * 这一版反过来看“外部文件怎样被流式导入进系统”。
 *
 * - 先按字节流接收 CSV 内容
 * - 再解码成文本行并做字段校验
 * - 最后把有效记录按批次切块，准备交给数据库或下游写入
 */
import cats.effect.{IO, IOApp}
import fs2.{Chunk, Stream, text}

import scala.util.Try

object FS2CsvImportPipeline extends IOApp.Simple {

  final case class InventorySnapshot(sku: String, name: String, stock: Int, warehouse: String)

  def incomingCsvBytes(csv: String): Stream[IO, Byte] =
    Stream
      .emits(csv.getBytes("UTF-8").grouped(10).toVector.map(bytes => Chunk.array(bytes)))
      .covary[IO]
      .evalTap(chunk => IO.println(s"[network] 收到原始 chunk: ${chunk.size} bytes"))
      .flatMap(chunk => Stream.chunk(chunk))

  def parseLine(lineNo: Long, line: String): Either[String, InventorySnapshot] = {
    val columns = line.split(",", -1).map(_.trim).toList

    columns match {
      case sku :: name :: stockText :: warehouse :: Nil =>
        if (sku.isEmpty) Left(s"第 $lineNo 行缺少 SKU")
        else if (name.isEmpty) Left(s"第 $lineNo 行缺少名称")
        else if (warehouse.isEmpty) Left(s"第 $lineNo 行缺少仓库")
        else {
          Try(stockText.toInt).toOption match {
            case None => Left(s"第 $lineNo 行库存不是整数: $stockText")
            case Some(value) if value < 0 => Left(s"第 $lineNo 行库存不能为负数: $value")
            case Some(value) => Right(InventorySnapshot(sku, name, value, warehouse))
          }
        }

      case _ =>
        Left(s"第 $lineNo 行列数不正确: $line")
    }
  }

  def parsedRows(csv: String): Stream[IO, Either[String, InventorySnapshot]] =
    incomingCsvBytes(csv)
      .through(text.utf8.decode)
      .through(text.lines)
      .map(_.trim)
      .filter(_.nonEmpty)
      .zipWithIndex
      .collect { case (line, index) if index > 0 => (index + 1, line) }
      .evalTap { case (lineNo, line) => IO.println(s"[line-$lineNo] $line") }
      .map { case (lineNo, line) => parseLine(lineNo, line) }

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== 用 fs2 构建 CSV 导入解析与分批写入前管道 ===")
      csv =
        "sku,name,stock,warehouse\n" +
          "BTC-101,Bitcoin Handbook,12,cn\n" +
          "ETH-202,Ethereum Guide,5,cn\n" +
          "BAD-000,,3,cn\n" +
          "SOL-303,Solana Notes,-1,us\n" +
          "OPS-404,Ops Manual,8,us\n"

      parsed <- parsedRows(csv)
        .evalTap {
          case Right(row) => IO.println(s"[ok] $row")
          case Left(error) => IO.println(s"[reject] $error")
        }
        .compile
        .toList

      valid = parsed.collect { case Right(row) => row }
      rejected = parsed.collect { case Left(error) => error }

      _ <- Stream
        .emits(valid)
        .covary[IO]
        .chunkN(2, allowFewer = true)
        .zipWithIndex
        .evalMap { case (chunk, index) =>
          val rows = chunk.toList
          IO.println(s"[batch-${index + 1}] 即将写入 ${rows.size} 条: ${rows.map(_.sku)}")
        }
        .compile
        .drain

      _ <- IO.println(s"有效记录=${valid.size}, 拒绝记录=${rejected.size}")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- `text.utf8.decode` + `text.lines` 很适合把字节流还原成逐行文本协议")
      _ <- IO.println("- 在流里尽早做字段校验，可以把坏数据挡在数据库写入之前")
      _ <- IO.println("- `chunkN` 能把有效记录切成批次，后面就能自然对接批量写库或批量调用")
    } yield ()
}
