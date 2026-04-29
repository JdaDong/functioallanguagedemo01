# fs2 流处理 Demo 分类索引

> fs2（Functional Streams for Scala）是基于 `cats-effect` 的纯函数式流处理库。
> 以下按能力层级分类，覆盖项目中所有以 fs2 为主角的 Demo。

---

## 一、基础流（Stream 核心 API）

| Demo | 文件 | 核心内容 |
|------|------|------|
| 20 | `20_FS2Pipeline.scala` | `Stream.emits / eval / map / filter / compile / drain` 基础管道 |
| 28 | `28_FS2StreamWorkflow.scala` | `Stream` 工作流、`evalMap`、`flatMap` 组合 |

---

## 二、并发流

| Demo | 文件 | 核心内容 |
|------|------|------|
| 34 | `34_FS2QueueWorker.scala` | `Queue[IO, A]`，多生产者单消费者工作流 |
| 37 | `37_FS2Topic.scala` | `Topic`，发布订阅（一发多收） |
| 42 | `42_FS2ParEvalMap.scala` | `parEvalMap` 并行流处理 |
| 47 | `47_FS2MergeStreams.scala` | `merge` 多路流合并 |

---

## 三、流控制（中断 / 信号 / 错误）

| Demo | 文件 | 核心内容 |
|------|------|------|
| 52 | `52_FS2ErrorRecovery.scala` | `handleErrorWith`、`attempt`、流错误恢复 |
| 57 | `57_FS2InterruptAndSignallingRef.scala` | `SignallingRef`，停流信号（`interruptWhen`） |

---

## 四、流变换（高级操作符）

| Demo | 文件 | 核心内容 |
|------|------|------|
| 62 | `62_FS2GroupWithin.scala` | `groupWithin`，时间窗口批处理 |
| 67 | `67_FS2PullLineDecoder.scala` | `Pull`，自定义流变换（低层 API） |
| 77 | `77_FS2ChunkedFileProcessor.scala` | `chunkN / chunks`，固定分块处理大文件流 |

---

## 五、流与 Topic / SSE / WebSocket（协议化事件流）

| Demo | 文件 | 核心内容 |
|------|------|------|
| 72 | `72_FS2TopicHub.scala` | `Topic` 房间广播枢纽（按 roomId 扇出） |
| 63 | `63_Http4sStreamingApi.scala` | `http4s` 流式响应（`Response` body 是 `Stream`） |
| 64 | `64_EmberStreamingClient.scala` | Ember 流式 client，消费流式 API |
| 68 | `68_Http4sServerSentEvents.scala` | SSE 推送（`ServerSentEvent` stream） |

---

## 六、流与数据库（Doobie 流）

| Demo | 文件 | 核心内容 |
|------|------|------|
| 81 | `81_DoobieStreamingExport.scala` | `query.stream`，数据库流式导出（大数据量不 OOM） |
| 82 | `82_FS2CsvImportPipeline.scala` | fs2 CSV 导入管道，解析 + 分批写库 |

---

## 七、业务场景流（以流托管长生命周期任务）

> 这些 Demo 用 fs2 Stream 作为后台任务的宿主，
> 替代手动 while-loop + sleep，实现可中断、可重试的长生命周期工作流。

| Demo | 文件 | 核心内容 |
|------|------|------|
| 87 | `87_FS2DedupReservationStream.scala` | 重复请求去重流 |
| 92 | `92_FS2OutboxRetryStream.scala` | Outbox 重试发布流（扫描 + 发布 + 推进状态） |
| 97 | `97_FS2InboxRetryConsumer.scala` | Inbox 重试消费流 |
| 102 | `102_FS2SagaTimeoutCompensationStream.scala` | Saga 超时补偿扫描流 |
| 107 | `107_FS2ProjectionReplayStream.scala` | 读模型 catch-up / replay 流 |
| 112 | `112_FS2CommandRouterStream.scala` | CQRS 命令路由流 |
| 117 | `117_FS2EventAppendStream.scala` | 事件溯源追加流（乐观锁 + Topic 扇出） |
| 122 | `122_FS2ProcessManagerEventRouter.scala` | 跨上下文事件路由流 |
| 127 | `127_FS2ACLTranslationStream.scala` | ACL 翻译流（混合上游消息分流翻译） |
| 132 | `132_FS2CrossContextEventBus.scala` | 有界上下文跨上下文事件总线 |

---

## 核心能力递进路径

```
基础流 API（20、28）
    ↓
并发流（34 Queue、37 Topic、42 parEvalMap、47 merge）
    ↓
流控制（52 错误恢复、57 停流信号）
    ↓
高级变换（62 groupWithin、67 Pull、77 chunked）
    ↓
协议化（63 streaming API、68 SSE、72 Topic Hub）
    ↓
与数据库结合（81 Doobie stream、82 CSV pipeline）
    ↓
业务场景托管（87 → 92 → 97 → 102 → 107 → 112 → 117 → 122 → 127 → 132）
```

---

## 七个最核心的 fs2 Demo

| 编号 | 概念 | 为什么重要 |
|---|---|---|
| `20` | 基础 Pipeline | `Stream` 的创建、变换和消费，入门必看 |
| `34` | `Queue` | 生产者-消费者解耦，最常用的并发模式 |
| `37` | `Topic` | 广播发布订阅，多消费者场景标准做法 |
| `42` | `parEvalMap` | 并行流处理，替代手动 `Fiber.start` 的更安全方式 |
| `57` | `SignallingRef` | 受控中断，长生命周期流的停止信号 |
| `62` | `groupWithin` | 时间窗口批处理，写入类场景必备 |
| `67` | `Pull` | 自定义流变换的底层 API，理解 fs2 内部机制 |
