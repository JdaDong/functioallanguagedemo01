/**
 * Scala 函数式编程 Demo 1: 高阶函数 (Higher-Order Functions)
 * 
 * 高阶函数是函数式编程的基石——函数可以作为参数传递，也可以作为返回值。
 * Scala 中的 map、filter、reduce 是最经典的高阶函数。
 */
object HigherOrderFunctions extends App {

  val numbers = List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

  // ========== map: 对每个元素应用转换函数 ==========
  val doubled = numbers.map(_ * 2)
  println(s"原始列表: $numbers")
  println(s"每个元素翻倍: $doubled")

  val words = List("hello", "functional", "world")
  val uppered = words.map(_.toUpperCase)
  println(s"转大写: $uppered")

  // ========== filter: 保留满足条件的元素 ==========
  val evens = numbers.filter(_ % 2 == 0)
  val odds = numbers.filterNot(_ % 2 == 0)
  println(s"偶数: $evens")
  println(s"奇数: $odds")

  // ========== reduce / fold: 将列表归约为单个值 ==========
  val sum = numbers.reduce(_ + _)
  val product = numbers.reduce(_ * _)
  println(s"求和: $sum")
  println(s"求积: $product")

  // foldLeft 可以指定初始值，比 reduce 更安全
  val sumWithInit = numbers.foldLeft(0)(_ + _)
  println(s"foldLeft 求和: $sumWithInit")

  // ========== 函数组合: 链式调用 ==========
  // 找出所有偶数，翻倍后求和
  val result = numbers
    .filter(_ % 2 == 0)
    .map(_ * 2)
    .reduce(_ + _)
  println(s"偶数翻倍后求和: $result")

  // ========== 自定义高阶函数 ==========
  def applyTwice(f: Int => Int, x: Int): Int = f(f(x))

  println(s"对 3 应用两次 (+10): ${applyTwice(_ + 10, 3)}")   // 3 -> 13 -> 23
  println(s"对 2 应用两次 (*3):  ${applyTwice(_ * 3, 2)}")    // 2 -> 6 -> 18

  // ========== 返回函数的函数 ==========
  def multiplier(factor: Int): Int => Int = (x: Int) => x * factor

  val triple = multiplier(3)
  val quadruple = multiplier(4)
  println(s"triple(5) = ${triple(5)}")
  println(s"quadruple(5) = ${quadruple(5)}")
}
