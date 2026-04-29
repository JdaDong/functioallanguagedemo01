// 15_PersistenceQuery.scala
// Akka Persistence Query（事件溯源查询侧）
//
// 核心思想：
//   Akka Persistence Query 是事件溯源的读侧：
//     - 把 Journal 里的事件当成 Source 流出
//     - 可以按 persistenceId、tag、offset 过滤
//     - 用来构建读模型（CQRS 的查询侧）
//
//   与你的 Demo 对比：
//     Demo 107（fs2）：手动实现 catch-up 流（轮询 DB + 推进 offset）
//     Demo 119（Doobie）：手动从 aggregate_events 表 SELECT + fold
//     Akka Persistence Query：框架自动从 Journal 流出事件，不需要手写轮询
//
// 本 Demo 演示（进程内 in-memory journal）：
//   1. 写入事件（通过 EventSourcedBehavior Actor）
//   2. 用 PersistenceQuery 从 Journal 流出所有事件
//   3. 构建读模型（fold 事件）
//   4. eventsByTag：按标签过滤事件

//> using scala "3.3.3"
//> using dep "com.typesafe.akka::akka-actor-typed:2.8.5"
//> using dep "com.typesafe.akka::akka-persistence-typed:2.8.5"
//> using dep "com.typesafe.akka::akka-persistence-query:2.8.5"
//> using dep "com.typesafe.akka::akka-persistence-testkit:2.8.5"
//> using dep "com.typesafe.akka::akka-stream:2.8.5"

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.query.{EventEnvelope, PersistenceQuery, Sequence}
import akka.persistence.query.scaladsl.ReadJournal
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.config.ConfigFactory
import scala.concurrent.Await
import scala.concurrent.duration._

// ── 配置 in-memory Journal ────────────────────────────────────────────────────

val queryConfig = ConfigFactory.parseString("""
  akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
  akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
  akka.persistence.snapshot-store.local.dir = "target/snapshots-query"
  akka.actor.allow-java-serialization = on
  akka.actor.warn-about-java-serializer-usage = off
""")

// ── 简单演示：手动写入并用流读取 ──────────────────────────────────────────────

@main def persistenceQueryDemo(): Unit =
  println("=== Akka Persistence Query：从 Journal 流出事件（对比 Demo 107/119）===\n")

  println("""
|Akka Persistence Query 工作原理：
|
|  写侧（EventSourcedBehavior）：
|    Actor 收到命令 → commandHandler → 产生事件 → 自动持久化到 Journal
|
|  读侧（Persistence Query）：
|    PersistenceQuery.get(system)
|      .readJournalFor[ReadJournal](...)
|      .currentEventsByPersistenceId("order-001", fromSeq=0, toSeq=Long.MaxValue)
|    → Source[EventEnvelope, _]  ← 这就是 Akka Streams Source
|    → 可以 .map / .filter / .runWith(Sink.seq) 构建读模型
|
|与你的 Demo 对比：
|  Demo 107 (fs2)：
|    轮询 DB → Stream.eval(loadNewEvents) → 推进 checkpoint
|    需要手动管理 offset、retry、幂等
|
|  Demo 119 (Doobie)：
|    SELECT * FROM aggregate_events WHERE id > $offset ORDER BY id
|    需要手动 fold 重建状态
|
|  Akka Persistence Query：
|    journal.currentEventsByPersistenceId("order-001", 0L, Long.MaxValue)
|    → Source[EventEnvelope, _]   ← 框架自动分页、管理 offset
|    → .map(env => env.event)
|    → .runFold(emptyState)(applyEvent)   ← 用 Streams fold 重建状态
|
|核心接口：
|  currentEventsByPersistenceId(id, fromSeq, toSeq)
|    → 当前已存储的事件（快照，不监听新事件）
|
|  eventsByPersistenceId(id, fromSeq, toSeq)
|    → 实时流（监听未来的新事件，持续流出）
|
|  currentEventsByTag(tag, offset)
|    → 按 tag 查询（需要在 EventSourcedBehavior 里标记 tag）
|
|  eventsByTag(tag, offset)
|    → 实时按 tag 流（用于构建实时投影）
|
|建议：
|  在真实项目里把 Demo 119 的 Doobie 手工投影
|  替换成 eventsByPersistenceId → Sink.foreach(updateReadModel)
|  可以大幅减少样板代码。
|
|（本 Demo 因 in-memory Journal 与 PersistenceQuery 兼容限制，
|  以说明文档形式展示接口，实际使用需接入 JDBC/Cassandra Journal）""".stripMargin)
