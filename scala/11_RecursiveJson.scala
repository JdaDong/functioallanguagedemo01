/**
 * Scala 函数式编程 Demo 11: 递归 JSON 数据结构 (Recursive JSON)
 *
 * 这个例子展示如何用 ADT 表达 JSON，并对递归数据结构进行处理：
 * - pretty print
 * - 统计节点数量
 * - 按 key 查找字段
 * - 收集所有字符串值
 */
object RecursiveJson extends App {

  sealed trait Json
  case class JObj(fields: Map[String, Json]) extends Json
  case class JArr(items: List[Json]) extends Json
  case class JStr(value: String) extends Json
  case class JNum(value: Double) extends Json
  case class JBool(value: Boolean) extends Json
  case object JNull extends Json

  def pretty(json: Json, indent: Int = 0): String = {
    val spaces = "  " * indent

    json match {
      case JObj(fields) if fields.isEmpty => "{}"
      case JObj(fields) =>
        val lines = fields.toList.map { case (key, value) =>
          s"${"  " * (indent + 1)}\"$key\": ${pretty(value, indent + 1)}"
        }
        s"{\n${lines.mkString(",\n")}\n$spaces}"

      case JArr(items) if items.isEmpty => "[]"
      case JArr(items) =>
        val lines = items.map(item => s"${"  " * (indent + 1)}${pretty(item, indent + 1)}")
        s"[\n${lines.mkString(",\n")}\n$spaces]"

      case JStr(value)  => s"\"$value\""
      case JNum(value)  => if (value.isWhole) value.toInt.toString else value.toString
      case JBool(value) => value.toString
      case JNull        => "null"
    }
  }

  def countNodes(json: Json): Int = json match {
    case JObj(fields) => 1 + fields.values.map(countNodes).sum
    case JArr(items)  => 1 + items.map(countNodes).sum
    case _            => 1
  }

  def findByKey(json: Json, targetKey: String): List[Json] = json match {
    case JObj(fields) =>
      val current = fields.get(targetKey).toList
      val nested = fields.values.toList.flatMap(value => findByKey(value, targetKey))
      current ++ nested
    case JArr(items) =>
      items.flatMap(item => findByKey(item, targetKey))
    case _ => Nil
  }

  def collectStrings(json: Json): List[String] = json match {
    case JObj(fields) => fields.values.toList.flatMap(collectStrings)
    case JArr(items)  => items.flatMap(collectStrings)
    case JStr(value)  => List(value)
    case _            => Nil
  }

  val profile = JObj(
    Map(
      "name" -> JStr("Alice"),
      "age" -> JNum(30),
      "active" -> JBool(true),
      "skills" -> JArr(List(JStr("Scala"), JStr("FP"), JStr("Erlang"))),
      "address" -> JObj(
        Map(
          "city" -> JStr("Shenzhen"),
          "zip" -> JStr("518000")
        )
      ),
      "projects" -> JArr(
        List(
          JObj(Map("name" -> JStr("CodeBuddy"), "stars" -> JNum(5))),
          JObj(Map("name" -> JStr("FP Playground"), "stars" -> JNum(4)))
        )
      ),
      "nickname" -> JNull
    )
  )

  println("=== pretty print ===")
  println(pretty(profile))

  println("\n=== 节点统计 ===")
  println(s"总节点数: ${countNodes(profile)}")

  println("\n=== 查找 key = name ===")
  println(findByKey(profile, "name").map(json => pretty(json)))

  println("\n=== 收集所有字符串值 ===")
  println(collectStrings(profile))

  println("\n=== 重点理解 ===")
  println("- JSON 是典型的递归数据结构：对象和数组内部还可以继续嵌套 JSON")
  println("- 对这种结构做处理时，函数通常也要写成递归形式")
  println("- pretty、count、find、collect 只是同一个递归结构上的不同解释")
}
