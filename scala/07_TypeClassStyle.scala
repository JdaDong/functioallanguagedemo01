/**
 * Scala 函数式编程 Demo 7: Type Class 风格
 *
 * Type Class 是函数式编程里非常重要的一种抽象方式：
 * “某个类型拥有什么能力”，不一定要靠继承来表达，
 * 也可以通过“为类型提供一份独立的能力实现”来表达。
 *
 * 这样做的好处：
 * 1. 不需要修改原始类型定义
 * 2. 一个类型可以按需拥有多种能力
 * 3. 通用函数只依赖“能力”，不依赖具体类型
 */
object TypeClassStyle extends App {

  println("=== 定义 Type Class：Show[A] ===")

  trait Show[A] {
    def show(value: A): String
  }

  object Show {
    def apply[A](implicit instance: Show[A]): Show[A] = instance

    implicit class ShowOps[A](private val value: A) extends AnyVal {
      def show(implicit instance: Show[A]): String = instance.show(value)
    }
  }

  import Show._

  println("\n=== 为不同类型提供 Show 实例 ===")

  case class User(name: String, age: Int)

  sealed trait PaymentStatus
  case object Pending extends PaymentStatus
  case object Paid extends PaymentStatus
  case object Failed extends PaymentStatus

  implicit val intShow: Show[Int] =
    (value: Int) => s"整数($value)"

  implicit val stringShow: Show[String] =
    (value: String) => s"字符串('$value')"

  implicit val userShow: Show[User] =
    (user: User) => s"User(name=${user.name}, age=${user.age})"

  implicit val paymentStatusShow: Show[PaymentStatus] = {
    case Pending => "待支付"
    case Paid    => "已支付"
    case Failed  => "支付失败"
  }

  implicit def optionShow[A](implicit showA: Show[A]): Show[Option[A]] = {
    case Some(value) => s"Some(${showA.show(value)})"
    case None        => "None"
  }

  println(123.show)
  println("Scala".show)
  println(User("Alice", 30).show)
  println((Paid: PaymentStatus).show)
  println(Option(User("Bob", 25)).show)
  println((None: Option[Int]).show)

  println("\n=== 通用函数只依赖能力，而不是具体类型 ===")

  def printReport[A: Show](values: List[A]): Unit = {
    values.zipWithIndex.foreach { case (value, index) =>
      println(s"${index + 1}. ${value.show}")
    }
  }

  printReport(List(User("Alice", 30), User("Bob", 25)))
  printReport(List[PaymentStatus](Pending, Paid, Failed))

  println("\n=== 再定义一个 Type Class：Eq[A] ===")

  trait Eq[A] {
    def eqv(left: A, right: A): Boolean
  }

  object Eq {
    def apply[A](implicit instance: Eq[A]): Eq[A] = instance

    implicit class EqOps[A](private val left: A) extends AnyVal {
      def ===(right: A)(implicit instance: Eq[A]): Boolean = instance.eqv(left, right)
      def =!=(right: A)(implicit instance: Eq[A]): Boolean = !instance.eqv(left, right)
    }
  }

  import Eq._

  implicit val intEq: Eq[Int] =
    (left: Int, right: Int) => left == right

  implicit val userEq: Eq[User] =
    (left: User, right: User) => left.name == right.name && left.age == right.age

  println(s"1 === 1 -> ${1 === 1}")
  println(s"1 =!= 2 -> ${1 =!= 2}")
  println(s"User(Alice,30) === User(Alice,30) -> ${User("Alice", 30) === User("Alice", 30)}")
  println(s"User(Alice,30) === User(Bob,25) -> ${User("Alice", 30) === User("Bob", 25)}")

  println("\n=== 重点理解 ===")
  println("- case class / Int / String 本身没有继承 Show 或 Eq")
  println("- 但我们依然可以在外部为它们补充能力")
  println("- 这就是 Type Class 风格的核心：按能力编程，而不是按继承层次编程")
}
