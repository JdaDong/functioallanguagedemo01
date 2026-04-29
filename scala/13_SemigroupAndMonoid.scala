/**
 * Scala 函数式编程 Demo 13: Semigroup 与 Monoid
 *
 * Semigroup: 只要求“能 combine”
 * Monoid:    在 Semigroup 基础上再要求有一个 empty
 *
 * 它们是函数式编程里非常重要的组合抽象。
 */
object SemigroupAndMonoid extends App {

  trait Semigroup[A] {
    def combine(x: A, y: A): A
  }

  trait Monoid[A] extends Semigroup[A] {
    def empty: A
  }

  object Monoid {
    def apply[A](implicit instance: Monoid[A]): Monoid[A] = instance

    def combineAll[A: Monoid](values: List[A]): A =
      values.foldLeft(Monoid[A].empty)(Monoid[A].combine)
  }

  implicit val intAdditionMonoid: Monoid[Int] = new Monoid[Int] {
    def empty: Int = 0
    def combine(x: Int, y: Int): Int = x + y
  }

  implicit val stringMonoid: Monoid[String] = new Monoid[String] {
    def empty: String = ""
    def combine(x: String, y: String): String = x + y
  }

  implicit def listMonoid[A]: Monoid[List[A]] = new Monoid[List[A]] {
    def empty: List[A] = Nil
    def combine(x: List[A], y: List[A]): List[A] = x ++ y
  }

  implicit val mapIntMonoid: Monoid[Map[String, Int]] = new Monoid[Map[String, Int]] {
    def empty: Map[String, Int] = Map.empty
    def combine(x: Map[String, Int], y: Map[String, Int]): Map[String, Int] = {
      val keys = x.keySet ++ y.keySet
      keys.map { key =>
        key -> (x.getOrElse(key, 0) + y.getOrElse(key, 0))
      }.toMap
    }
  }

  import Monoid._

  println("=== 最简单的组合：数字、字符串、列表 ===")
  println(s"combineAll(List(1,2,3,4,5)) = ${combineAll(List(1, 2, 3, 4, 5))}")
  println(s"combineAll(List(\"Hello\", \" \", \"Scala\")) = ${combineAll(List("Hello", " ", "Scala"))}")
  println(s"combineAll(List(List(1,2), List(3), List(4,5))) = ${combineAll(List(List(1, 2), List(3), List(4, 5)))}")

  println("\n=== 实战：合并日志片段 ===")
  val logs = List(
    List("启动应用", "读取配置"),
    List("连接数据库"),
    List("启动 HTTP 服务")
  )
  println(combineAll(logs))

  println("\n=== 实战：合并统计结果 ===")
  val day1 = Map("click" -> 120, "signup" -> 8)
  val day2 = Map("click" -> 150, "signup" -> 5, "pay" -> 2)
  val day3 = Map("click" -> 90, "pay" -> 3)

  val total = combineAll(List(day1, day2, day3))
  println(s"合并后的统计结果: $total")

  println("\n=== empty 的意义 ===")
  println(s"空字符串的 empty = '${Monoid[String].empty}'")
  println(s"空 Int 的 empty = ${Monoid[Int].empty}")
  println(s"空 Map 的 empty = ${Monoid[Map[String, Int]].empty}")

  println("\n=== 重点理解 ===")
  println("- Monoid 让‘如何组合’变成一种可复用能力")
  println("- 一旦一个类型有了 Monoid，我们就能通用地 combineAll")
  println("- 这在统计、日志、错误收集、配置合并里很常见")
}
