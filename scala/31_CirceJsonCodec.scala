//> using scala "2.13.16"
//> using dep "io.circe::circe-core:0.14.10"
//> using dep "io.circe::circe-parser:0.14.10"
//> using dep "io.circe::circe-generic:0.14.10"

/**
 * Scala 函数式编程 Demo 31: 用 circe 做真实 JSON 编解码
 *
 * 在真实 http4s 服务里，JSON 很少再手写字符串拼接，
 * 更常见的方式是：
 * 1. 为领域模型定义 Encoder / Decoder
 * 2. 把 JSON 解析和校验结果显式放进 Either / IO
 */
import io.circe.generic.semiauto._
import io.circe.parser.decode
import io.circe.syntax._
import io.circe.{Decoder, Encoder}

object CirceJsonCodec extends App {

  sealed trait OrderStatus
  object OrderStatus {
    case object Created extends OrderStatus
    case object Paid extends OrderStatus
    case object Cancelled extends OrderStatus

    implicit val encoder: Encoder[OrderStatus] = Encoder.encodeString.contramap {
      case Created => "created"
      case Paid => "paid"
      case Cancelled => "cancelled"
    }

    implicit val decoder: Decoder[OrderStatus] = Decoder.decodeString.emap {
      case "created" => Right(Created)
      case "paid" => Right(Paid)
      case "cancelled" => Right(Cancelled)
      case other => Left(s"未知订单状态: $other")
    }
  }

  final case class LineItem(sku: String, quantity: Int, price: Double)
  final case class Order(id: Long, user: String, status: OrderStatus, items: List[LineItem]) {
    def total: Double = items.map(i => i.quantity * i.price).sum
  }

  implicit val lineItemEncoder: Encoder[LineItem] = deriveEncoder
  implicit val lineItemDecoder: Decoder[LineItem] = deriveDecoder
  implicit val orderEncoder: Encoder[Order] = deriveEncoder
  implicit val orderDecoder: Decoder[Order] = deriveDecoder

  val order = Order(
    id = 101,
    user = "Alice",
    status = OrderStatus.Paid,
    items = List(
      LineItem("book-1", 2, 39.9),
      LineItem("pen-2", 3, 5.5)
    )
  )

  val validJson =
    """
      {
        "id": 202,
        "user": "Bob",
        "status": "created",
        "items": [
          {"sku": "keyboard", "quantity": 1, "price": 299.0},
          {"sku": "cable", "quantity": 2, "price": 19.9}
        ]
      }
    """.stripMargin

  val invalidJson =
    """
      {
        "id": 203,
        "user": "Carol",
        "status": "shipping",
        "items": []
      }
    """.stripMargin

  println("=== circe: Scala 对象 -> JSON ===")
  println(order.asJson.spaces2)
  println(f"订单总金额: ${order.total}%.2f")

  println("\n=== circe: JSON -> Scala 对象（成功） ===")
  val decodedValid = decode[Order](validJson)
  println(decodedValid)
  decodedValid.foreach(o => println(f"解析后总金额: ${o.total}%.2f"))

  println("\n=== circe: JSON -> Scala 对象（失败） ===")
  val decodedInvalid = decode[Order](invalidJson)
  println(decodedInvalid)

  println("\n=== 重点理解 ===")
  println("- Encoder / Decoder 让 JSON 协议从字符串拼接升级为类型驱动")
  println("- 枚举、嵌套结构、列表都可以被统一编码和解码")
  println("- 解析失败会显式返回错误，而不是悄悄吞掉协议问题")
}
