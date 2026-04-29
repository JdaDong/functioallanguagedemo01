/**
 * Scala 函数式编程 Demo 12: Validated 风格的表单校验
 *
 * 前面的 Either 校验一旦遇到第一个错误就会停止。
 * 这个 Demo 展示另一种常见思路：把所有错误都收集起来。
 */
object ValidatedRegistration extends App {

  sealed trait Validated[+E, +A] {
    def map[B](f: A => B): Validated[E, B] = this match {
      case Valid(value)     => Valid(f(value))
      case i @ Invalid(_)   => i
    }
  }
  case class Valid[A](value: A) extends Validated[Nothing, A]
  case class Invalid[E](errors: List[E]) extends Validated[E, Nothing]

  object Validated {
    def valid[A](value: A): Validated[Nothing, A] = Valid(value)
    def invalid[E](error: E): Validated[E, Nothing] = Invalid(List(error))

    def map4[E, A, B, C, D, Z](
      va: Validated[E, A],
      vb: Validated[E, B],
      vc: Validated[E, C],
      vd: Validated[E, D]
    )(f: (A, B, C, D) => Z): Validated[E, Z] = {
      val errors = List(va, vb, vc, vd).collect { case Invalid(errs) => errs }.flatten
      if (errors.nonEmpty) Invalid(errors)
      else {
        (va, vb, vc, vd) match {
          case (Valid(a), Valid(b), Valid(c), Valid(d)) => Valid(f(a, b, c, d))
          case _                                        => Invalid(errors)
        }
      }
    }
  }

  import Validated._

  case class RegistrationForm(
    username: String,
    email: String,
    password: String,
    age: Int
  )

  def validateUsername(username: String): Validated[String, String] = {
    val trimmed = username.trim
    if (trimmed.length < 3) invalid("用户名至少需要 3 个字符")
    else if (!trimmed.forall(ch => ch.isLetterOrDigit || ch == '_')) invalid("用户名只能包含字母、数字或下划线")
    else valid(trimmed)
  }

  def validateEmail(email: String): Validated[String, String] = {
    val trimmed = email.trim
    if (trimmed.isEmpty) invalid("邮箱不能为空")
    else if (!trimmed.contains("@") || !trimmed.contains(".")) invalid("邮箱格式不合法")
    else valid(trimmed.toLowerCase)
  }

  def validatePassword(password: String): Validated[String, String] = {
    val checks = List(
      Option.when(password.length < 8)("密码至少需要 8 位"),
      Option.when(!password.exists(_.isLetter))("密码必须包含字母"),
      Option.when(!password.exists(_.isDigit))("密码必须包含数字")
    ).flatten

    if (checks.isEmpty) valid(password) else Invalid(checks)
  }

  def validateAge(age: Int): Validated[String, Int] =
    if (age < 13) invalid("年龄必须大于等于 13 岁") else valid(age)

  def validateForm(username: String, email: String, password: String, age: Int): Validated[String, RegistrationForm] =
    map4(
      validateUsername(username),
      validateEmail(email),
      validatePassword(password),
      validateAge(age)
    )(RegistrationForm.apply)

  val examples = List(
    ("alice_01", "alice@example.com", "hello123", 20),
    ("a!", "wrong-mail", "short", 10),
    ("bob", "", "password", 12)
  )

  println("=== Validated: 一次收集所有错误 ===")
  examples.foreach { case (username, email, password, age) =>
    println(s"\n输入: username=$username, email=$email, age=$age")
    validateForm(username, email, password, age) match {
      case Valid(form)      => println(s"校验通过: $form")
      case Invalid(errors)  =>
        println("校验失败，发现以下问题:")
        errors.foreach(err => println(s"- $err"))
    }
  }

  println("\n=== 重点理解 ===")
  println("- Either 适合按顺序短路失败")
  println("- Validated 适合把多个独立校验的错误一次性收集起来")
  println("- 这类模式在表单、配置文件、批量导入校验里非常常见")
}
