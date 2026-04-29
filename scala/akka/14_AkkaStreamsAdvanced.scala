// 14_AkkaStreamsAdvanced.scala
// Akka Streams 高级：背压 / 节流 / 合并 / 广播 / 自定义 GraphStage
//
// 核心思想：
//   Akka Streams 高级特性对应 fs2 的高级操作符：
//     throttle       ↔  fs2 Stream.awakeEvery + metered
//     balance        ↔  fs2 parEvalMap（负载均衡扇出）
//     zip / merge    ↔  fs2 zip / merge
//     GraphStage     ↔  fs2 Pull（自定义流变换）
//
// 本 Demo 演示：
//   1. throttle：限速（每秒最多 N 个元素）
//   2. conflate：背压下合并（下游慢时合并上游元素）
//   3. zip / merge / balance：流合并/分流
//   4. alsoTo：流复制（不修改主流，同时写副本）
//   5. 与 fs2 高级操作符对比

//> using scala "3.3.3"
//> using dep "com.typesafe.akka::akka-actor-typed:2.8.5"
//> using dep "com.typesafe.akka::akka-stream:2.8.5"

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.scaladsl._
import scala.concurrent.duration._
import scala.concurrent.Await

@main def akkaStreamsAdvancedDemo(): Unit =
  given system: ActorSystem[Nothing] =
    ActorSystem(Behaviors.empty, "advanced-streams")
  import system.executionContext

  println("=== Akka Streams 高级：背压/节流/合并/广播 ===\n")

  // ── 1. throttle：限速（对比 fs2 Stream.awakeEvery）──────────────────────
  println("── 1. throttle 限速（每 200ms 最多 2 个）────────────────────────")
  val throttled = Source(1 to 10)
    .throttle(elements = 2, per = 200.millis)
    .map(n => s"[$n]")
    .runWith(Sink.seq)
  val t = Await.result(throttled, 5.seconds)
  println(s"  限速结果: ${t.mkString(" ")}")

  // ── 2. conflate：背压合并（下游处理慢时合并上游元素）────────────────────
  println("\n── 2. conflate 背压合并（下游慢时合并上游批次）─────────────────")
  // conflate 把积压的元素合并成一个，避免 OOM
  val conflated = Source(1 to 20)
    .conflate(_ + _)    // 把积压的数字加总
    .throttle(1, 100.millis)
    .runWith(Sink.foreach(n => print(s"$n ")))
  Await.result(conflated, 3.seconds)
  println("\n  （积压的元素被合并求和了）")

  // ── 3. zip：合并两个 Source──────────────────────────────────────────────
  println("\n── 3. zip：两个 Source 配对合并 ──────────────────────────────────")
  val src1 = Source(List("A", "B", "C"))
  val src2 = Source(List(1, 2, 3))
  val zipped = src1.zip(src2).runWith(Sink.seq)
  val zr = Await.result(zipped, 3.seconds)
  println(s"  zip 结果: $zr")

  // ── 4. merge：合并两个 Source（保留元素，不配对）────────────────────────
  println("\n── 4. merge：两个 Source 混合合并 ───────────────────────────────")
  val fast = Source(1 to 3)
  val slow = Source(4 to 6)
  val merged = fast.merge(slow).runWith(Sink.seq)
  val mr = Await.result(merged, 3.seconds)
  println(s"  merge 结果: ${mr.sorted}")

  // ── 5. alsoTo：流复制（主流 + 副本）──────────────────────────────────────
  println("\n── 5. alsoTo：流复制（主流 + 记录副本）──────────────────────────")
  val auditLog = scala.collection.mutable.ListBuffer.empty[Int]
  val withAudit = Source(1 to 5)
    .alsoTo(Sink.foreach(n => auditLog += n))  // 副本写入审计日志
    .map(_ * 2)                                 // 主流继续处理
    .runWith(Sink.seq)
  val ar = Await.result(withAudit, 3.seconds)
  println(s"  主流处理结果 (×2): $ar")
  println(s"  审计日志 (原始值): ${auditLog.toList}")

  // ── 6. groupBy：按条件分流（类似按 key 分组）────────────────────────────
  println("\n── 6. groupBy：按奇偶分流 ─────────────────────────────────────")
  import scala.concurrent.Future
  val partResult = Source(1 to 10)
    .groupBy(2, n => n % 2)
    .map(n => if n % 2 == 0 then s"偶:$n" else s"奇:$n")
    .mergeSubstreams
    .runWith(Sink.seq)
  val pr = Await.result(partResult, 3.seconds)
  println(s"  分流结果: ${pr.sorted}")

  println("""
|关键点（与 fs2 高级操作符对比）：
|
|  操作符           Akka Streams         fs2 等价
|  ────────────     ─────────────────    ─────────────────────────
|  限速             .throttle(N, 1s)     .metered(1.second / N)
|  背压合并         .conflate(f)         .chunks + .map(_.sum)
|  两流配对         .zip(other)          .zip(other)
|  两流混合         .merge(other)        .merge(other)
|  流复制           .alsoTo(sink)        .observe(sink)
|  负载均衡扇出     Balance(N)           .parEvalMap(N)(f)""".stripMargin)

  system.terminate()
  Await.result(system.whenTerminated, 5.seconds)
