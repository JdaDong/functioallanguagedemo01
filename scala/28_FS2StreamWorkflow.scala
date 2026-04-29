//> using scala "2.13.16"
//> using dep "org.typelevel::cats-effect:3.5.7"
//> using dep "co.fs2::fs2-core:3.11.0"

/**
 * Scala 函数式编程 Demo 28: 真正的 fs2 Stream 数据管道
 *
 * 20 号 Demo 里我们先用 Iterator / LazyList 建立了流处理直觉，
 * 现在改成真正的 fs2 Stream，看看真实项目里“读 -> 解析 -> 过滤 -> 批处理 -> 汇总”是怎么写的。
 */
import cats.effect.{IO, IOApp}
import fs2.Stream

object FS2StreamWorkflow extends IOApp.Simple {

  final case class Order(id: Long, user: String, amount: Double)
  final case class TaggedOrder(order: Order, level: String)

  val rawLines: List[String] = List(
    "1,Alice,120.5",
    "",
    "2,Bob,1500",
    "bad-line",
    "3,Carol,88.0",
    "4,David,2300"
  )

  def parseLine(line: String): Either[String, Order] =
    line.split(",").toList match {
      case id :: user :: amount :: Nil =>
        for {
          orderId <- id.toLongOption.toRight(s"订单 id 非数字: $id")
          orderAmount <- amount.toDoubleOption.toRight(s"金额非数字: $amount")
        } yield Order(orderId, user, orderAmount)

      case _ =>
        Left(s"格式错误: $line")
    }

  def tagOrder(order: Order): IO[TaggedOrder] =
    IO.println(s"打标签: order=${order.id}") *>
      IO.pure(TaggedOrder(order, if (order.amount >= 1000) "vip" else "normal"))

  val orderStream: Stream[IO, Order] =
    Stream
      .emits(rawLines)
      .covary[IO]
      .filter(_.nonEmpty)
      .evalTap(line => IO.println(s"读取到原始行: $line"))
      .map(parseLine)
      .evalTap {
        case Left(err) => IO.println(s"丢弃坏数据: $err")
        case Right(order) => IO.println(s"解析成功: $order")
      }
      .collect { case Right(order) => order }

  val batchProgram: IO[List[TaggedOrder]] =
    orderStream
      .chunkN(2)
      .evalTap(chunk => IO.println(s"批处理 chunk: ${chunk.toList.map(_.id).mkString(", ")}"))
      .flatMap(chunk => Stream.emits(chunk.toList).covary[IO])
      .evalMap(tagOrder)
      .compile
      .toList

  val infiniteDemo: IO[List[Int]] =
    Stream
      .iterate(1)(_ + 1)
      .covary[IO]
      .map(n => n * n)
      .take(5)
      .compile
      .toList

  val run: IO[Unit] =
    for {
      _ <- IO.println("=== fs2: 有限数据流 ===")
      tagged <- batchProgram
      _ <- IO.println(s"最终有效订单: $tagged")
      total = tagged.map(_.order.amount).sum
      _ <- IO.println(f"有效订单总金额: $total%.2f")

      _ <- IO.println("\n=== fs2: 无限流按需消费 ===")
      squares <- infiniteDemo
      _ <- IO.println(s"前 5 个平方数: $squares")

      _ <- IO.println("\n=== 重点理解 ===")
      _ <- IO.println("- Stream 不是一次把所有数据装进内存，而是按需拉取和组合")
      _ <- IO.println("- evalTap / evalMap 很适合把 effect 放进流处理过程")
      _ <- IO.println("- chunkN 常见于批量写库、批量发消息、批量调用下游")
    } yield ()
}
