/**
 * Scala 函数式编程 Demo 3: 不可变性与柯里化
 * 
 * 不可变性 (Immutability) 是函数式编程的核心原则——数据一旦创建就不可修改。
 * 柯里化 (Currying) 是将多参数函数转化为一系列单参数函数的技术。
 */
object Immutability extends App {

  // ========== 不可变数据结构 ==========
  val originalList = List(1, 2, 3, 4, 5)
  
  // "修改"操作实际上返回新的列表，原列表不变
  val newList = 0 :: originalList          // 在头部添加
  val appendedList = originalList :+ 6     // 在尾部添加
  val withoutFirst = originalList.tail     // 去掉第一个元素

  println("--- 不可变列表 ---")
  println(s"原始列表:      $originalList")
  println(s"头部添加 0:    $newList")
  println(s"尾部添加 6:    $appendedList")
  println(s"去掉第一个:    $withoutFirst")
  println(s"原始列表不变:  $originalList")

  // ========== 不可变 Map ==========
  val scores = Map("Alice" -> 95, "Bob" -> 87, "Charlie" -> 92)
  val updatedScores = scores + ("David" -> 88)         // 添加
  val modifiedScores = scores.updated("Bob", 90)       // "修改"

  println("\n--- 不可变 Map ---")
  println(s"原始成绩: $scores")
  println(s"添加David: $updatedScores")
  println(s"修改Bob:   $modifiedScores")
  println(s"原始不变:  $scores")

  // ========== case class 的 copy 方法 ==========
  case class User(name: String, age: Int, email: String)

  val alice = User("Alice", 30, "alice@example.com")
  val olderAlice = alice.copy(age = 31)
  val renamedAlice = alice.copy(name = "Alice Zhang")

  println("\n--- case class copy ---")
  println(s"原始: $alice")
  println(s"年龄+1: $olderAlice")
  println(s"改名: $renamedAlice")

  // ========== 柯里化 (Currying) ==========
  println("\n--- 柯里化 ---")

  // 普通函数
  def add(a: Int, b: Int): Int = a + b

  // 柯里化版本
  def addCurried(a: Int)(b: Int): Int = a + b

  // 部分应用：固定第一个参数，得到新函数
  val add5 = addCurried(5) _
  val add10 = addCurried(10) _

  println(s"add5(3) = ${add5(3)}")
  println(s"add10(3) = ${add10(3)}")

  // 实用场景：日志记录器
  def logger(level: String)(message: String): Unit =
    println(s"[$level] $message")

  val info = logger("INFO") _
  val error = logger("ERROR") _

  info("应用程序启动")
  error("连接超时")

  // ========== 函数组合 (Function Composition) ==========
  println("\n--- 函数组合 ---")

  val double: Int => Int = _ * 2
  val increment: Int => Int = _ + 1
  val square: Int => Int = x => x * x

  // compose: 从右到左执行  |  andThen: 从左到右执行
  val doubleThenIncrement = double andThen increment   // x => (x*2) + 1
  val incrementThenDouble = double compose increment   // x => (x+1) * 2

  println(s"doubleThenIncrement(3) = ${doubleThenIncrement(3)}")  // 7
  println(s"incrementThenDouble(3) = ${incrementThenDouble(3)}")  // 8

  // 组合多个函数
  val pipeline = double andThen increment andThen square
  println(s"pipeline(3) = ${pipeline(3)}")  // 3 -> 6 -> 7 -> 49
}
