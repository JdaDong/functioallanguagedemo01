/**
 * Scala 函数式编程 Demo 9: 订单状态机 (Order State Machine)
 *
 * 这个例子展示如何用 ADT + 不可变数据 + Either 错误处理
 * 来表达一个简单但很常见的业务流程：订单状态流转。
 */
object OrderStateMachine extends App {

  sealed trait OrderState {
    def items: List[String]
  }

  case class Created(items: List[String]) extends OrderState
  case class Paid(items: List[String], paymentId: String) extends OrderState
  case class Shipped(items: List[String], paymentId: String, trackingNo: String) extends OrderState
  case class Completed(items: List[String], paymentId: String, trackingNo: String, receivedBy: String) extends OrderState
  case class Cancelled(items: List[String], reason: String) extends OrderState

  def pay(order: OrderState, paymentId: String): Either[String, OrderState] = order match {
    case Created(items) if paymentId.trim.nonEmpty => Right(Paid(items, paymentId.trim))
    case Created(_)                                => Left("支付失败：paymentId 不能为空")
    case _: Paid                                   => Left("支付失败：订单已经支付")
    case _: Shipped                                => Left("支付失败：订单已经发货")
    case _: Completed                              => Left("支付失败：订单已经完成")
    case _: Cancelled                              => Left("支付失败：订单已取消")
  }

  def ship(order: OrderState, trackingNo: String): Either[String, OrderState] = order match {
    case Paid(items, paymentId) if trackingNo.trim.nonEmpty => Right(Shipped(items, paymentId, trackingNo.trim))
    case Paid(_, _)                                         => Left("发货失败：trackingNo 不能为空")
    case Created(_)                                         => Left("发货失败：订单还未支付")
    case _: Shipped                                         => Left("发货失败：订单已经发货")
    case _: Completed                                       => Left("发货失败：订单已经完成")
    case _: Cancelled                                       => Left("发货失败：订单已取消")
  }

  def complete(order: OrderState, receivedBy: String): Either[String, OrderState] = order match {
    case Shipped(items, paymentId, trackingNo) if receivedBy.trim.nonEmpty =>
      Right(Completed(items, paymentId, trackingNo, receivedBy.trim))
    case Shipped(_, _, _) => Left("完成失败：签收人不能为空")
    case Created(_)       => Left("完成失败：订单还未支付")
    case _: Paid          => Left("完成失败：订单还未发货")
    case _: Completed     => Left("完成失败：订单已经完成")
    case _: Cancelled     => Left("完成失败：订单已取消")
  }

  def cancel(order: OrderState, reason: String): Either[String, OrderState] = order match {
    case Created(items) if reason.trim.nonEmpty => Right(Cancelled(items, reason.trim))
    case Paid(items, _) if reason.trim.nonEmpty => Right(Cancelled(items, reason.trim))
    case Created(_)                             => Left("取消失败：原因不能为空")
    case Paid(_, _)                             => Left("取消失败：原因不能为空")
    case _: Shipped                             => Left("取消失败：订单已经发货")
    case _: Completed                           => Left("取消失败：订单已经完成")
    case _: Cancelled                           => Left("取消失败：订单已经取消")
  }

  def describe(order: OrderState): String = order match {
    case Created(items) => s"Created(items=$items)"
    case Paid(items, paymentId) => s"Paid(items=$items, paymentId=$paymentId)"
    case Shipped(items, paymentId, trackingNo) =>
      s"Shipped(items=$items, paymentId=$paymentId, trackingNo=$trackingNo)"
    case Completed(items, paymentId, trackingNo, receivedBy) =>
      s"Completed(items=$items, paymentId=$paymentId, trackingNo=$trackingNo, receivedBy=$receivedBy)"
    case Cancelled(items, reason) => s"Cancelled(items=$items, reason=$reason)"
  }

  val created: OrderState = Created(List("Scala Book", "Mechanical Keyboard"))

  println("=== 合法状态流转 ===")
  val successFlow = for {
    paid      <- pay(created, "PAY-1001")
    shipped   <- ship(paid, "SF-888888")
    completed <- complete(shipped, "Alice")
  } yield completed

  successFlow match {
    case Right(order) => println(s"最终状态: ${describe(order)}")
    case Left(error)  => println(s"流程失败: $error")
  }

  println("\n=== 非法状态流转 ===")
  println(ship(created, "SF-000001"))
  println(complete(created, "Bob"))
  println(cancel(successFlow.getOrElse(created), "不想买了"))

  println("\n=== 重点理解 ===")
  println("- 状态是一个 ADT，不同状态拥有不同数据")
  println("- 每一步流转都返回一个新状态，旧状态不会被修改")
  println("- 非法流转会被显式阻止，并返回 Left(错误信息)")
}
