/**
 * Scala 函数式编程 Demo 20: 流处理直觉 —— 像管道一样处理数据
 *
 * 真正学习 fs2 之前，先建立两个直觉：
 * 1. 数据可以一段一段地流过管道，而不是一次性全部装进内存。
 * 2. 只有真正需要时，后续元素才会被计算出来。
 *
 * 这里先用 Iterator 和 LazyList 模拟流处理的核心感觉。
 */
object FS2Pipeline extends App {

  case class Event(service: String, level: String, message: String)

  def parse(line: String): Option[Event] =
    line.split("\\|", 3).toList match {
      case service :: level :: message :: Nil =>
        Some(Event(service, level, message))
      case _ =>
        None
    }

  val rawLogLines = List(
    "auth|INFO|user login success",
    "order|ERROR|payment timeout",
    "auth|WARN|token expires soon",
    "order|ERROR|inventory not enough",
    "mail|INFO|email sent",
    "order|ERROR|coupon invalid",
    "auth|ERROR|password retry too many times"
  )

  println("=== 像管道一样逐步处理日志流 ===")
  val summaries = rawLogLines.iterator
    .flatMap(line => parse(line).toList)
    .filter(_.level == "ERROR")
    .grouped(2)
    .zipWithIndex
    .map {
      case (chunk, index) =>
        val counts = chunk
          .groupBy(_.service)
          .map { case (service, events) => s"$service -> ${events.size}" }
          .mkString(", ")
        s"第 ${index + 1} 批错误事件: $counts"
    }
    .toList

  summaries.foreach(println)

  println("\n=== LazyList: 只在需要时才生成后续数据 ===")
  def sensorStream(start: Int): LazyList[Event] = {
    def loop(n: Int): LazyList[Event] = {
      println(s"[生成] 传感器事件 $n")
      val level = if (n % 4 == 0) "WARN" else "INFO"
      Event("sensor", level, s"reading-$n") #:: loop(n + 1)
    }
    loop(start)
  }

  val firstWarnings = sensorStream(1)
    .filter(_.level == "WARN")
    .take(3)
    .toList

  println("拿到的前 3 个 WARN 事件:")
  firstWarnings.foreach(event => println(s"- $event"))

  println("\n=== 重点理解 ===")
  println("- Iterator 适合表达‘一边读取，一边处理’的批处理管道")
  println("- LazyList 适合表达按需计算的无限流或超大数据流")
  println("- 真正的 fs2 会在这些直觉之上，再补上资源安全、并发、背压等生产能力")
}
