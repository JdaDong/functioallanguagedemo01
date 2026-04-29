//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.13.0"

/**
 * Scala 函数式编程 Demo 67: fs2 Pull 自定义按行解码
 *
 * 62 号 Demo 展示过 groupWithin，63/64 号 Demo 展示过流式响应。
 * 这一版继续补一个更底层、也更贴近真实协议处理的话题：
 *
 * 当上游按任意 chunk 边界推送数据时，
 * 我们常常需要自己把残缺片段重新拼起来，再切成完整记录。
 */
import cats.effect.{IO, IOApp}
import fs2.{Chunk, Pull, Stream}

object FS2PullLineDecoder extends IOApp.Simple {

  def decodeLines(stream: Stream[IO, String], carry: String = ""): Pull[IO, String, Unit] =
    stream.pull.uncons.flatMap {
      case Some((chunk, tail)) =>
        val combined = carry + chunk.toList.mkString
        val pieces = combined.split("\n", -1).toList
        val complete = pieces.dropRight(1).filter(_.nonEmpty)
        val rest = pieces.lastOption.getOrElse("")

        Pull.output(Chunk.seq(complete)) >>
          decodeLines(tail, rest)

      case None =>
        if (carry.nonEmpty) Pull.output(Chunk.singleton(carry))
        else Pull.done
    }

  val source: Stream[IO, String] =
    Stream
      .emits(
        List(
          s"evt-1${'\n'}e",
          s"vt-2${'\n'}part",
          "ial-3",
          s"${'\n'}evt-4${'\n'}"
        )
      )
      .covary[IO]
      .evalTap(chunk => IO.println(s"[source] 收到 chunk: '$chunk'"))

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== fs2 Pull：自己处理 chunk 边界与残留片段 ===")
      lines <- decodeLines(source).stream
        .evalTap(line => IO.println(s"[decoder] 输出完整记录: $line"))
        .compile
        .toList
      _ <- IO.println(s"最终解码结果: $lines")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- Pull 适合处理‘需要手动消费输入、保留残留状态、再决定何时输出’的场景")
      _ <- IO.println("- 真实协议里，日志流、SSE、socket 文本帧、批量导入都常常会遇到这种 chunk 边界问题")
      _ <- IO.println("- 这类能力能帮你从‘会用 Stream’进一步走向‘会自定义流变换’")
    } yield ()
}
