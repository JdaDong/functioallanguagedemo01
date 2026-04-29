//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.7.0"
//> using dep "co.fs2::fs2-core:3.13.0"

/**
 * Scala 函数式编程 Demo 52: fs2 流里的错误恢复
 *
 * 前面已经写过 Queue、Topic、merge、parEvalMap，
 * 这一版继续补一个真实流处理中非常关键的话题：
 * 流里某个元素失败时，应该让整条流停止，还是把错误转成普通值继续处理？
 */
import cats.effect.{IO, IOApp}
import fs2.Stream

object FS2ErrorRecovery extends IOApp.Simple {

  sealed trait ParseResult {
    def render: String
  }
  final case class Accepted(orderId: String, amount: BigDecimal) extends ParseResult {
    override def render: String = s"accepted(orderId=$orderId, amount=$amount)"
  }
  final case class Rejected(raw: String, reason: String) extends ParseResult {
    override def render: String = s"rejected(raw=$raw, reason=$reason)"
  }

  val rawLines = List(
    "order-100,89.50",
    "bad-line",
    "order-101,120.00",
    "order-102,not-a-number"
  )

  def parseLine(raw: String): IO[Accepted] =
    IO {
      val parts = raw.split(",").map(_.trim).toList
      parts match {
        case orderId :: amount :: Nil =>
          Accepted(orderId, BigDecimal(amount))
        case _ =>
          throw new IllegalArgumentException(s"格式错误: $raw")
      }
    }

  val strictStream: Stream[IO, String] =
    Stream
      .emits(rawLines)
      .covary[IO]
      .evalTap(raw => IO.println(s"[strict] 读取: $raw"))
      .evalMap(parseLine)
      .map(_.render)
      .handleErrorWith { error =>
        Stream.emit(s"strict-stop(${error.getMessage})")
      }

  val resilientStream: Stream[IO, String] =
    Stream
      .emits(rawLines)
      .covary[IO]
      .evalTap(raw => IO.println(s"[resilient] 读取: $raw"))
      .evalMap { raw =>
        parseLine(raw).attempt.map {
          case Right(value) => value.render
          case Left(error) => Rejected(raw, error.getMessage).render
        }
      }

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== 严格模式：错误进入 error channel，整条流提前结束 ===")
      strict <- strictStream.compile.toList
      _ <- IO.println(s"strict results = $strict")

      _ <- IO.println("\n=== 韧性模式：把错误转成普通值，整条流继续推进 ===")
      resilient <- resilientStream.compile.toList
      _ <- IO.println(s"resilient results = $resilient")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- fs2 的 error channel 一旦出错，默认会终止后续流")
      _ <- IO.println("- 如果你想保留坏数据并继续处理，需要把错误转回 value channel")
      _ <- IO.println("- 真实日志流、批处理、消息消费里，经常都要先做这个选择")
    } yield ()
}
