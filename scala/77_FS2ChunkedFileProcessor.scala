//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.13.0"

/**
 * Scala 函数式编程 Demo 77: fs2 固定分块处理大文件流
 *
 * 76 号 Demo 已经把 multipart 上传解析出来了，
 * 这一版继续看上传后的下一步：
 *
 * - 大文件通常不能整块读入内存
 * - 我们更常见的做法是按固定大小切块处理
 * - 每个分块都可以独立校验、上传、重试或落盘
 */
import cats.effect.{IO, IOApp}
import fs2.{Chunk, Stream}

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object FS2ChunkedFileProcessor extends IOApp.Simple {

  final case class PartSummary(index: Long, bytes: Int, digest12: String, preview: String)

  def sha256Prefix(bytes: Array[Byte]): String = {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    digest.map("%02x".format(_)).mkString.take(12)
  }

  def preview(bytes: Array[Byte]): String =
    new String(bytes.take(24), StandardCharsets.UTF_8).replace("\n", "\\n")

  def incomingFile(payload: String): Stream[IO, Byte] = {
    val networkChunks =
      payload
        .getBytes(StandardCharsets.UTF_8)
        .grouped(11)
        .toVector
        .map(group => Chunk.array(group))

    Stream
      .emits(networkChunks)
      .covary[IO]
      .evalTap(chunk => IO.println(s"[network] 收到原始 chunk: ${chunk.size} bytes"))
      .flatMap(Stream.chunk)
  }

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== 用 fs2 把大文件流切成固定大小的处理块 ===")
      payload =
        "symbol,price,volume\nBTC,101.2,50\nETH,2050,30\nSOL,145,88\nADA,0.73,130\nDOGE,0.18,400\n"

      parts <- incomingFile(payload)
        .chunkN(24, allowFewer = true)
        .zipWithIndex
        .evalMap { case (chunk, index) =>
          val bytes = chunk.toArray
          val summary = PartSummary(
            index = index + 1,
            bytes = bytes.length,
            digest12 = sha256Prefix(bytes),
            preview = preview(bytes)
          )

          IO.println(
            s"[part-${summary.index}] size=${summary.bytes}, sha=${summary.digest12}, preview=${summary.preview}"
          ) *> IO.pure(summary)
        }
        .compile
        .toList

      totalBytes = parts.map(_.bytes).sum
      _ <- IO.println(s"总分块数=${parts.size}, 总字节数=$totalBytes")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- `chunkN` 很适合把连续字节流重组为固定大小的处理块")
      _ <- IO.println("- 每个分块都可以单独做 hash、重试、上传对象存储或写入临时文件")
      _ <- IO.println("- 这正是大文件上传、断点续传、分片导入、对象存储 SDK 的常见处理模型")
    } yield ()
}
