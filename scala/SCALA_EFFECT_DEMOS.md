# Scala Effect 体系 Demo 分类索引

> `cats-effect` 是整个 Scala FP 技术栈的核心，
> 几乎所有 Demo 都依赖它，但以下按"**以某个 effect 概念为主角**"分类。

---

## 一、`IO` 基础（纯 IO 使用）

| Demo | 文件 | 核心内容 |
|------|------|------|
| 17 | `17_IOBasics.scala` | `IO.pure / IO.delay / *> / flatMap / handleErrorWith` |
| 18 | `18_ResourceDemo.scala` | `Resource.make`、资源自动释放、`use` |
| 19 | `19_ConcurrencyBasics.scala` | `IO` 并发组合直觉 |
| 20 | `20_StreamBasics.scala` | 流处理直觉（手写） |

---

## 二、`Fiber` / 并发 / 竞速

| Demo | 文件 | 核心内容 |
|------|------|------|
| 26 | `26_CatsEffectFiber.scala` | `Fiber`、`start / join / cancel` |
| 27 | `27_CatsEffectResource.scala` | 真实 `Resource`、资源生命周期 |
| 28 | `28_FS2StreamWorkflow.scala` | `fs2 Stream + IO` 工作流 |
| 35 | `35_CatsEffectTimeoutAndCancel.scala` | `timeout`、`cancel`、`finalizer` |
| 36 | `36_CatsEffectDeferredRef.scala` | `Ref` + `Deferred` 协作 |
| 51 | `51_CatsEffectRace.scala` | `race` 竞速与自动取消 |

---

## 三、并发原语

| Demo | 文件 | 核心内容 |
|------|------|------|
| 41 | `41_CatsEffectSemaphore.scala` | `Semaphore`（并发限流） |
| 46 | `46_CatsEffectSupervisor.scala` | `Supervisor`（后台任务托管） |
| 56 | `56_CatsEffectUncancelable.scala` | `uncancelable / poll`（取消边界） |
| 61 | `61_CatsEffectDispatcher.scala` | `Dispatcher`（回调桥接 IO ↔ 非 IO 世界） |
| 66 | `66_CatsEffectIOLocal.scala` | `IOLocal`（fiber-local 上下文隔离） |
| 71 | `71_CatsEffectMapRef.scala` | `MapRef`（按 key 分片并发状态） |

---

## 四、`Ref / Deferred / Signal`（状态管理）

| Demo | 文件 | 核心内容 |
|------|------|------|
| 36 | `36_CatsEffectDeferredRef.scala` | `Ref` 原子更新 + `Deferred` 一次性信号 |
| 57 | `57_FS2InterruptAndSignallingRef.scala` | `SignallingRef`（停流信号） |
| 71 | `71_CatsEffectMapRef.scala` | `MapRef` 分片并发状态 |

---

## 五、Tagless Final（effect 抽象化）

| Demo | 文件 | 核心内容 |
|------|------|------|
| 22 | `22_TaglessUserService.scala` | Tagless Final 代数设计（`F[_]` 抽象） |
| 25 | `25_TaglessTestInterpreter.scala` | 测试解释器（内存 effect，不依赖真实 IO） |
| 30 | `30_TaglessCatsEffect.scala` | `cats-effect` 真实解释器注入 |
| 44 | `44_TaglessHttp4sUserModule.scala` | Tagless + `http4s` 模块装配 |

---

## 六、业务场景下的 Effect 协调

> 这些 Demo 以 `cats-effect` 为底层，协调真实业务中的 effect 链路

| Demo | 文件 | 核心内容 |
|------|------|------|
| 86 | `86_CatsEffectIdempotencyGate.scala` | 并发幂等门闩（`Ref + Deferred` 防重入） |
| 91 | `91_CatsEffectOutboxCoordinator.scala` | Outbox 后台发布协调器（`Supervisor + Fiber`） |
| 96 | `96_CatsEffectInboxCoordinator.scala` | Inbox 消费端协调器 |
| 101 | `101_CatsEffectSagaCoordinator.scala` | Saga 补偿事务协调器 |
| 106 | `106_CatsEffectProjectionCoordinator.scala` | 读模型投影 checkpoint 推进 |
| 111 | `111_CatsEffectCommandBus.scala` | CQRS 命令总线（纯函数 + IO 分发） |
| 116 | `116_CatsEffectEventSourcedAggregate.scala` | 事件溯源聚合根（fold + IO） |
| 121 | `121_CatsEffectProcessManager.scala` | 进程管理器状态机 |
| 126 | `126_CatsEffectACLTranslator.scala` | 防腐层翻译器（Either + IO） |
| 131 | `131_CatsEffectContextMapAssembly.scala` | 有界上下文地图完整系统装配 |

---

## 七、Effect 测试（`munit-cats-effect`）

| Demo | 文件 | 核心内容 |
|------|------|------|
| 40 | `40_MUnitCatsEffectSuite.scala` | `munit-cats-effect` 基础：`test` 里直接写 `IO` |
| 45 | `45_MUnitHttp4sRouteSuite.scala` | 路由集成测试（`HttpApp[IO]` 内存调用） |
| 50 | `50_MUnitAuthMiddlewareSuite.scala` | 鉴权中间件测试 |
| 55 | `55_MUnitClientOrchestrationSuite.scala` | 下游编排测试 |
| 60 | `60_MUnitClientRetrySuite.scala` | client 重试策略测试 |
| 65 | `65_MUnitStreamingRouteSuite.scala` | 流式路由测试 |
| 75 | `75_MUnitWebSocketChatSuite.scala` | WebSocket 路由测试 |
| 80 | `80_MUnitRepositoryIntegrationSuite.scala` | Repository 集成测试 |

---

## 核心能力递进路径

```
IO 基础          (17–20)
    ↓
Fiber / Resource / timeout (26–28, 35–36)
    ↓
并发原语         (41, 46, 56, 61, 66, 71)
    ↓
Tagless Final 抽象 (22, 25, 30, 44)
    ↓
业务场景 Effect 协调 (86, 91, 96, 101, 106, 111, 116, 121, 126, 131)
```

## 七个最核心的 Effect Demo

| 编号 | 概念 | 为什么重要 |
|---|---|---|
| `17` | `IO` | 一切 effect 的基础 |
| `26` | `Fiber` | 并发的基本单元 |
| `36` | `Ref / Deferred` | 共享状态和一次性信号的标准做法 |
| `41` | `Semaphore` | 限制并发度的标准工具 |
| `46` | `Supervisor` | 后台任务生命周期托管 |
| `56` | `uncancelable` | 正确处理取消边界，生产代码必备 |
| `66` | `IOLocal` | fiber-local 上下文，链路追踪的底层 |
