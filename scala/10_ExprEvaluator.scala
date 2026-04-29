/**
 * Scala 函数式编程 Demo 10: 表达式求值器 (Expression Evaluator)
 *
 * 这个例子扩展了前面的表达式树：
 * - 支持加减乘除
 * - 支持变量 Var
 * - 支持局部绑定 Let
 * - 用 Either[String, Double] 做安全求值
 */
object ExprEvaluator extends App {

  sealed trait Expr
  case class Num(value: Double) extends Expr
  case class Add(left: Expr, right: Expr) extends Expr
  case class Sub(left: Expr, right: Expr) extends Expr
  case class Mul(left: Expr, right: Expr) extends Expr
  case class Div(left: Expr, right: Expr) extends Expr
  case class Var(name: String) extends Expr
  case class Let(name: String, valueExpr: Expr, inExpr: Expr) extends Expr

  def show(expr: Expr): String = expr match {
    case Num(v)          => if (v.isWhole) v.toInt.toString else v.toString
    case Add(l, r)       => s"(${show(l)} + ${show(r)})"
    case Sub(l, r)       => s"(${show(l)} - ${show(r)})"
    case Mul(l, r)       => s"(${show(l)} * ${show(r)})"
    case Div(l, r)       => s"(${show(l)} / ${show(r)})"
    case Var(name)       => name
    case Let(n, v, body) => s"(let $n = ${show(v)} in ${show(body)})"
  }

  def eval(expr: Expr, env: Map[String, Double] = Map.empty): Either[String, Double] = expr match {
    case Num(v) => Right(v)

    case Add(l, r) =>
      for {
        lv <- eval(l, env)
        rv <- eval(r, env)
      } yield lv + rv

    case Sub(l, r) =>
      for {
        lv <- eval(l, env)
        rv <- eval(r, env)
      } yield lv - rv

    case Mul(l, r) =>
      for {
        lv <- eval(l, env)
        rv <- eval(r, env)
      } yield lv * rv

    case Div(l, r) =>
      for {
        lv <- eval(l, env)
        rv <- eval(r, env)
        result <- if (rv == 0) Left("除零错误") else Right(lv / rv)
      } yield result

    case Var(name) =>
      env.get(name).toRight(s"未定义变量: $name")

    case Let(name, valueExpr, inExpr) =>
      for {
        value <- eval(valueExpr, env)
        result <- eval(inExpr, env + (name -> value))
      } yield result
  }

  val expr1 = Add(Num(10), Mul(Num(2), Num(3)))
  val expr2 = Div(Var("x"), Num(2))
  val expr3 = Let("x", Add(Num(8), Num(4)), Mul(Var("x"), Num(3)))
  val expr4 = Let("y", Num(10), Div(Var("y"), Sub(Num(5), Num(5))))

  val examples = List(
    ("基础表达式", expr1, Map.empty[String, Double]),
    ("带环境变量", expr2, Map("x" -> 42.0)),
    ("局部绑定 Let", expr3, Map.empty[String, Double]),
    ("除零错误", expr4, Map.empty[String, Double]),
    ("未定义变量", Add(Var("z"), Num(1)), Map.empty[String, Double])
  )

  println("=== 表达式求值器 ===")
  examples.foreach { case (title, expr, env) =>
    println(s"\n$title")
    println(s"表达式: ${show(expr)}")
    println(s"结果: ${eval(expr, env)}")
  }

  println("\n=== 重点理解 ===")
  println("- 表达式本身是一个递归数据结构")
  println("- 求值函数的递归结构，通常跟数据结构保持一致")
  println("- Either 让变量未定义、除零等错误都变成显式结果")
}
