/**
 * Scala 函数式编程 Demo 8: 表单校验 (Form Validation)
 *
 * 这个例子展示如何把“可能失败的业务校验”写成纯函数，
 * 并用 Either[String, A] 显式表达成功或失败。
 */
object FormValidation extends App {

  case class RegistrationForm(
    username: String,
    email: String,
    password: String,
    age: Int
  )

  def validateUsername(username: String): Either[String, String] = {
    val trimmed = username.trim
    if (trimmed.length < 3) Left("用户名至少需要 3 个字符")
    else if (!trimmed.forall(ch => ch.isLetterOrDigit || ch == '_')) Left("用户名只能包含字母、数字或下划线")
    else Right(trimmed)
  }

  def validateEmail(email: String): Either[String, String] = {
    val trimmed = email.trim
    if (trimmed.isEmpty) Left("邮箱不能为空")
    else if (!trimmed.contains("@") || !trimmed.contains(".")) Left("邮箱格式不合法")
    else Right(trimmed.toLowerCase)
  }

  def validatePassword(password: String): Either[String, String] = {
    val hasLetter = password.exists(_.isLetter)
    val hasDigit = password.exists(_.isDigit)

    if (password.length < 8) Left("密码至少需要 8 位")
    else if (!hasLetter || !hasDigit) Left("密码必须同时包含字母和数字")
    else Right(password)
  }

  def validateAge(age: Int): Either[String, Int] =
    if (age < 13) Left("年龄必须大于等于 13 岁") else Right(age)

  def validateForm(
    username: String,
    email: String,
    password: String,
    age: Int
  ): Either[String, RegistrationForm] =
    for {
      validUsername <- validateUsername(username)
      validEmail    <- validateEmail(email)
      validPassword <- validatePassword(password)
      validAge      <- validateAge(age)
    } yield RegistrationForm(validUsername, validEmail, validPassword, validAge)

  val cases = List(
    ("alice_01", "Alice@example.com", "hello123", 20),
    ("ab", "alice@example.com", "hello123", 20),
    ("bob", "invalid-mail", "hello123", 20),
    ("charlie", "charlie@example.com", "short", 20),
    ("david", "david@example.com", "password123", 10)
  )

  println("=== 表单校验 ===")
  cases.foreach { case (username, email, password, age) =>
    println(s"\n输入: username=$username, email=$email, age=$age")
    validateForm(username, email, password, age) match {
      case Right(form) => println(s"校验通过: $form")
      case Left(error) => println(s"校验失败: $error")
    }
  }

  println("\n=== 重点理解 ===")
  println("- 每个校验器都是纯函数：输入相同，输出一定相同")
  println("- 失败不会抛异常，而是返回 Left(错误信息)")
  println("- for 推导式让多步校验看起来像一条清晰的数据流")
}
