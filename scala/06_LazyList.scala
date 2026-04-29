/**
 * Scala 函数式编程 Demo 6: 惰性求值与 LazyList
 *
 * 惰性求值 (Lazy Evaluation) 的核心思想是：
 * “先描述计算，等真正需要结果时再执行”。
 *
 * Scala 里常见的两种惰性能力：
 * - lazy val : 第一次访问时才求值，而且只计算一次
 * - LazyList : 按需生成序列，甚至可以表示无限列表
 */
object LazyListDemo extends App {

  println("=== lazy val：第一次访问时才会计算 ===")

  lazy val expensiveValue: Int = {
    println("  正在执行昂贵计算...")
    40 + 2
  }

  println("刚定义完 expensiveValue，还没有真正计算")
  println(s"第一次读取: $expensiveValue")
  println(s"第二次读取: $expensiveValue")

  println("\n=== LazyList：按需生成无限序列 ===")

  def from(n: Int): LazyList[Int] = n #:: from(n + 1)

  val naturals = from(1)
  println(s"前 10 个自然数: ${naturals.take(10).toList}")

  val evenSquares = naturals
    .filter(_ % 2 == 0)
    .map(n => n * n)
    .take(5)
    .toList

  println(s"前 5 个偶数平方: $evenSquares")

  println("\n=== 只计算真正用到的那一部分 ===")

  val traced = naturals.map { n =>
    println(s"  正在处理 $n")
    n * 2
  }

  println(s"只取前 3 个翻倍结果: ${traced.take(3).toList}")

  println("\n=== 惰性定义斐波那契数列 ===")

  lazy val fibs: LazyList[BigInt] =
    BigInt(0) #:: BigInt(1) #:: fibs.zip(fibs.tail).map { case (a, b) => a + b }

  println(s"前 12 个 Fibonacci: ${fibs.take(12).toList}")

  println("\n=== 组合过滤与映射：像数据流一样处理 ===")

  val interestingNumbers = naturals
    .filter(n => n % 3 == 0 || n % 5 == 0)
    .map(n => s"数字=$n, 平方=${n * n}")
    .take(6)
    .toList

  interestingNumbers.foreach(println)
}
