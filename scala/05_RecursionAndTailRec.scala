/**
 * Scala 函数式编程 Demo 5: 递归与尾递归
 *
 * 在函数式编程里，我们倾向于用“递归”描述重复计算，
 * 而不是依赖可变变量和传统循环。
 *
 * 尾递归 (Tail Recursion) 是一种特殊形式的递归：
 * 如果递归调用是函数中的最后一步，编译器就能把它优化成循环，
 * 既保留函数式写法，又避免栈溢出。
 */
object RecursionAndTailRec extends App {
  import scala.annotation.tailrec

  println("=== 递归处理列表 ===")

  def sum(list: List[Int]): Int = list match {
    case Nil          => 0
    case head :: tail => head + sum(tail)
  }

  def myMap[A, B](list: List[A])(f: A => B): List[B] = list match {
    case Nil          => Nil
    case head :: tail => f(head) :: myMap(tail)(f)
  }

  val numbers = List(1, 2, 3, 4, 5)
  println(s"原始列表: $numbers")
  println(s"递归求和: ${sum(numbers)}")
  println(s"递归 map 平方: ${myMap(numbers)(x => x * x)}")

  println("\n=== 普通递归：数学定义很自然 ===")

  def factorial(n: Int): BigInt =
    if (n <= 1) 1 else n * factorial(n - 1)

  println(s"factorial(5) = ${factorial(5)}")
  println(s"factorial(10) = ${factorial(10)}")

  println("\n=== 尾递归：更适合大规模输入 ===")

  @tailrec
  def factorialTailRec(n: Int, acc: BigInt = 1): BigInt =
    if (n <= 1) acc else factorialTailRec(n - 1, acc * n)

  @tailrec
  def reverse[A](list: List[A], acc: List[A] = Nil): List[A] = list match {
    case Nil          => acc
    case head :: tail => reverse(tail, head :: acc)
  }

  @tailrec
  def gcd(a: Int, b: Int): Int =
    if (b == 0) a.abs else gcd(b, a % b)

  println(s"尾递归 factorial(20) = ${factorialTailRec(20)}")
  println(s"50! 的位数 = ${factorialTailRec(50).toString.length}")
  println(s"reverse(List(a, b, c, d)) = ${reverse(List("a", "b", "c", "d"))}")
  println(s"gcd(48, 18) = ${gcd(48, 18)}")

  println("\n=== @tailrec 的意义 ===")
  println("如果一个函数看起来像尾递归，但实际上无法优化，编译器会直接报错。")
  println("这能帮助我们写出既优雅又高效的递归代码。")
}
