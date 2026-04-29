// 03_ActorStreams.scala
// Akka Streams 基础
//
// 核心思想：
//   Akka Streams = Graph（有向无环图）描述数据流
//     Source → 数据来源（发射元素）
//     Flow   → 数据变换（map / filter / async）
//     Sink   → 数据终点（消费/收集元素）
//
//   与 fs2 Stream 的对比：
//     fs2：纯函数式，用 Monad 组合，编译期类型安全，无运行时图
//     Akka Streams：图 DSL，支持扇入扇出复杂拓扑，背压由 Reactive Streams 规范保证
//
// 本 Demo 演示：
//   1. Source / Flow / Sink 基础组合
//   2. 常用操作符：map、filter、mapAsync、groupedWithin
//   3. 背压演示：下游慢时上游自动减速
//   4. 扇出（broadcast）：一个 Source 同时流向多个 Sink

//> using scala "3.3.3"
//> using dep "com.typesafe.akka::akka-actor-typed:2.8.5"
//> using dep "com.typesafe.akka::akka-stream:2.8.5"

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.scaladsl._
import akka.stream.{ClosedShape, KillSwitches, OverflowStrategy}
import scala.concurrent.duration._
import scala.concurrent.Await

@main def actorStreamsDemo(): Unit =
  // Akka Streams 需要 ActorSystem 提供 materializer
  given system: ActorSystem[Nothing] =
    ActorSystem(Behaviors.empty, "streams-system")
  import system.executionContext

  println("=== Akka Streams 基础：Source / Flow / Sink ===\n")

  // ── 1. 基础管道：Source → Flow → Sink ──────────────────────────────────────
  println("── 1. 基础管道 ─────────────────────────────────────────────")

  val source = Source(1 to 10)
  val flow   = Flow[Int].filter(_ % 2 == 0).map(_ * 10)
  val sink   = Sink.foreach[Int](n => print(s"$n "))

  val result1 = source.via(flow).runWith(sink)
  Await.result(result1, 3.seconds)
  println("\n（偶数 ×10：20 40 60 80 100）")

  // ── 2. mapAsync：异步处理（对比 fs2 evalMap）──────────────────────────────
  println("\n── 2. mapAsync 异步处理 ──────────────────────────────────────")

  val asyncResult = Source(1 to 5)
    .mapAsync(parallelism = 3) { n =>
      // 模拟异步操作（实际可以是 DB 查询、HTTP 请求）
      scala.concurrent.Future {
        Thread.sleep(10)
        n * n
      }
    }
    .runWith(Sink.seq)

  val squares = Await.result(asyncResult, 3.seconds)
  println(s"平方：$squares")

  // ── 3. groupedWithin：时间窗口批处理（对比 fs2 groupWithin）──────────────
  println("\n── 3. groupedWithin 时间窗口批处理 ──────────────────────────")

  val batchResult = Source(1 to 20)
    .throttle(5, 100.millis)   // 每 100ms 最多 5 个（模拟背压）
    .groupedWithin(5, 200.millis)
    .map(batch => s"批次: [${batch.mkString(", ")}]")
    .runWith(Sink.foreach(println))

  Await.result(batchResult, 5.seconds)

  // ── 4. 扇出（Broadcast）：一个 Source → 多个 Sink ────────────────────────
  println("\n── 4. Broadcast 扇出 ────────────────────────────────────────")

  val graph = RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._

    val src    = b.add(Source(1 to 6))
    val bcast  = b.add(Broadcast[Int](2))
    val sink1  = b.add(Sink.foreach[Int](n => print(s"[偶数筛] $n ")))
    val sink2  = b.add(Sink.foreach[Int](n => print(s"[×100] ${n*100} ")))

    src ~> bcast.in
    bcast.out(0).filter(_ % 2 == 0) ~> sink1
    bcast.out(1)                     ~> sink2

    ClosedShape
  })

  graph.run()
  Thread.sleep(300)
  println()

  println("""
|关键点：
|  1. Source / Flow / Sink 三件套，与 fs2 的 Stream / Pipe / Sink 概念对应
|  2. mapAsync(parallelism=N) 内置并行控制，对比 fs2 parEvalMap(N)
|  3. groupedWithin 是时间窗口批处理，与 fs2 groupWithin 功能相同
|  4. Broadcast 实现扇出（一发多收），与 fs2 Topic 功能类似
|  5. Akka Streams 背压由 Reactive Streams 规范保证，下游慢时上游自动减速""".stripMargin)

  system.terminate()
  Await.result(system.whenTerminated, 5.seconds)
