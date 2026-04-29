/**
 * Scala 函数式编程 Demo 4: 函数式错误处理 (Option / Either / Try)
 *
 * 在函数式编程中，我们尽量不用抛异常来表示“预期内的失败”。
 * 更常见的做法是把“成功 / 失败”显式放进类型里：
 * - Option[A]      : 只有“有值 / 没值”，适合简单场景
 * - Either[E, A]   : 失败时保留错误信息
 * - Try[A]         : 捕获可能抛异常的计算
 */
object ErrorHandling extends App {

  println("=== Option：只表达成功 / 失败 ===")

  def safeReciprocal(n: Int): Option[Double] =
    if (n == 0) None else Some(1.0 / n)

  List(2, 1, 0, -4).foreach { n =>
    val result = safeReciprocal(n)
      .map(x => f"$x%.2f")
      .getOrElse("无法计算")

    println(s"1 / $n = $result")
  }

  println("\n=== Either：失败时保留错误信息 ===")

  def safeParseInt(text: String): Either[String, Int] = {
    scala.util.Try(text.trim.toInt).toOption match {
      case Some(value) => Right(value)
      case None        => Left(s"'$text' 不是合法整数")
    }
  }

  def safeDivide(a: Int, b: Int): Either[String, Double] =
    if (b == 0) Left("除数不能为 0") else Right(a.toDouble / b)

  def ratioFromText(numeratorText: String, denominatorText: String): Either[String, Double] =
    for {
      numerator   <- safeParseInt(numeratorText)
      denominator <- safeParseInt(denominatorText)
      ratio       <- safeDivide(numerator, denominator)
    } yield ratio

  val cases = List(
    ("42", "6"),
    ("42", "0"),
    ("abc", "3")
  )

  cases.foreach { case (a, b) =>
    val message = ratioFromText(a, b) match {
      case Right(value) => f"成功: $a / $b = $value%.2f"
      case Left(error)  => s"失败: $error"
    }
    println(message)
  }

  println("\n=== for 推导式：像流水线一样组合计算 ===")

  def percentage(partText: String, totalText: String): Either[String, String] =
    for {
      part  <- safeParseInt(partText)
      total <- safeParseInt(totalText)
      ratio <- safeDivide(part, total)
    } yield f"${ratio * 100}%.2f%%"

  println(s"出勤率: ${percentage("18", "20")}")
  println(s"出勤率: ${percentage("18", "0")}")

  println("\n=== Try：包装可能抛异常的代码 ===")

  import scala.util.{Failure, Success, Try}

  def safeSqrt(text: String): Try[Double] = Try {
    val n = text.toDouble
    require(n >= 0, "不能对负数开平方")
    math.sqrt(n)
  }

  List("49", "2.25", "-1", "hello").foreach { input =>
    safeSqrt(input) match {
      case Success(value) => println(f"sqrt($input) = $value%.4f")
      case Failure(error) => println(s"sqrt($input) 失败: ${error.getMessage}")
    }
  }

  val recovered = safeSqrt("-9").recover {
    case _: IllegalArgumentException => 0.0
  }
  println(s"recover 后的结果: $recovered")
}
