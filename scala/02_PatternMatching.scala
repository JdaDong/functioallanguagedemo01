/**
 * Scala 函数式编程 Demo 2: 模式匹配与代数数据类型 (ADT)
 * 
 * 模式匹配是函数式编程中替代 if-else/switch 的强大工具。
 * 结合 sealed trait 和 case class，可以构建类型安全的代数数据类型。
 */
object PatternMatching extends App {

  // ========== 基础模式匹配 ==========
  def describe(x: Any): String = x match {
    case 0                => "零"
    case i: Int if i > 0  => s"正整数: $i"
    case i: Int           => s"负整数: $i"
    case s: String        => s"字符串: '$s', 长度=${s.length}"
    case (a, b)           => s"元组: ($a, $b)"
    case head :: tail     => s"列表: 头=$head, 尾=$tail"
    case _                => "未知类型"
  }

  println(describe(0))
  println(describe(42))
  println(describe(-7))
  println(describe("Scala"))
  println(describe((1, "hello")))
  println(describe(List(1, 2, 3)))

  println("\n--- 代数数据类型 (ADT) ---")

  // ========== 用 ADT 表示数学表达式 ==========
  sealed trait Expr
  case class Num(value: Double) extends Expr
  case class Add(left: Expr, right: Expr) extends Expr
  case class Mul(left: Expr, right: Expr) extends Expr
  case class Neg(expr: Expr) extends Expr

  // 递归求值
  def eval(expr: Expr): Double = expr match {
    case Num(v)      => v
    case Add(l, r)   => eval(l) + eval(r)
    case Mul(l, r)   => eval(l) * eval(r)
    case Neg(e)      => -eval(e)
  }

  // 美化打印
  def show(expr: Expr): String = expr match {
    case Num(v)      => v.toString
    case Add(l, r)   => s"(${show(l)} + ${show(r)})"
    case Mul(l, r)   => s"(${show(l)} * ${show(r)})"
    case Neg(e)      => s"(-${show(e)})"
  }

  // 表达式: (3 + 4) * (-2)
  val expr = Mul(Add(Num(3), Num(4)), Neg(Num(2)))
  println(s"表达式: ${show(expr)}")
  println(s"结果:   ${eval(expr)}")

  println("\n--- Option 模式匹配 ---")

  // ========== Option: 优雅处理空值 ==========
  def safeDivide(a: Double, b: Double): Option[Double] =
    if (b != 0) Some(a / b) else None

  def showDivision(a: Double, b: Double): String =
    safeDivide(a, b) match {
      case Some(result) => s"$a / $b = $result"
      case None         => s"$a / $b = 除零错误!"
    }

  println(showDivision(10, 3))
  println(showDivision(10, 0))

  // ========== for 推导式与 Option 链 ==========
  val result = for {
    a <- safeDivide(100, 5)   // Some(20)
    b <- safeDivide(a, 4)     // Some(5)
    c <- safeDivide(b, 2)     // Some(2.5)
  } yield c

  println(s"链式安全除法: $result")
}
