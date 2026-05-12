# 🧠 函数式编程多语言学习手册

> **用 5 种语言，178 个经典 Demo，系统学习函数式编程核心思想。**

本项目精选 **Scala、Akka、Erlang、Elixir、Haskell、Rust** 六种各具特色的编程语言，通过实战代码演示函数式编程中最重要的概念。每个 Demo 都配有详细的中文注释，适合对照学习、理解不同语言对同一思想的表达方式。其中 Erlang 与 Elixir 共享同一套 BEAM 运行时，`.beam` 文件 100% 互通，一起形成 BEAM 家族的两种不同编程风格。

目前 `Scala` 部分已经从基础语法一路补充到：**错误处理、尾递归、惰性求值、Type Class 风格、表单校验、状态机建模、表达式求值器、递归 JSON、Validated、Monoid、Functor / Applicative / Monad、Reader / State，以及 IO / Resource / 并发 / 流处理直觉，并进一步过渡到最小 HTTP 服务、Tagless Final、Kleisli 请求管道、Retry / Backoff、测试解释器，最后进入真实 `cats-effect` / `fs2` / `http4s` 的高级实战阶段，并继续覆盖 `circe` JSON 编解码、http4s JSON API、http4s client、fs2 Queue 工作流、effect 的 timeout / cancel、`Ref / Deferred` 协作、Topic 发布订阅、Bearer 鉴权、Ember 本地联调、munit-cats-effect 测试化，以及 `Semaphore` 并发限流、fs2 `parEvalMap` 并行流、http4s 领域错误映射、Tagless + http4s 模块装配、路由级集成测试，并继续扩展到 `Supervisor` 后台任务托管、多路流 `merge`、官方 `AuthMiddleware`、`EitherT` 错误编排和鉴权中间件测试，随后补齐 `race` 竞速与自动取消、fs2 流式错误恢复、http4s client 中间件、下游服务聚合与编排测试，再推进到 `uncancelable` / `poll`、停流信号、client retry、`ContextRoutes` 与治理测试，并继续延伸到 `Dispatcher` 回调桥接、fs2 `groupWithin` 批处理窗口、http4s 流式响应、Ember 流式 client、流式路由测试、`IOLocal`、fs2 `Pull`、SSE、`MapRef` 分片状态、房间广播枢纽、http4s WebSocket、JDK WebSocket 回调桥接、WebSocket 路由测试，以及 Multipart 上传、fs2 固定分块处理、Doobie `Transactor` 资源、Tagless Repository + SQL 解释器与仓储集成测试，并继续推进到 Doobie 流式导出、fs2 CSV 导入管道、http4s CSV 下载接口、Tagless 批量导入模块与导入导出集成测试，以及 cats-effect 并发幂等门闩、fs2 重复请求流去重、http4s `Idempotency-Key` 写接口、Doobie 持久化幂等写入与幂等写接口集成测试，并继续延伸到 cats-effect Outbox 协调器、fs2 Outbox 重试发布流、http4s Webhook + Outbox 发布边界、Doobie 事务 Outbox 与事务 Outbox 集成测试，以及 cats-effect Inbox 协调器、fs2 Inbox 重试消费流、http4s Webhook Inbox 接收边界、Doobie 事务 Inbox 与事务 Inbox 集成测试，并继续推进到 cats-effect Saga 协调器、fs2 Saga 超时补偿流、http4s Saga 工作流边界、Doobie 事务 Saga 状态与 Saga 集成测试，以及 cats-effect 读模型投影协调器、fs2 读模型回放流、http4s 读模型查询边界、Doobie 事务投影 checkpoint 与读模型回放集成测试**，并附带了一份独立的学习路线文档 `SCALA_FP_ROADMAP.md`。

---

## 📁 项目结构

```
functioallanguagedemo01/
├── scala/                              # JVM 上的多范式语言
│   ├── 01_HigherOrderFunctions.scala   # 高阶函数
│   ├── 02_PatternMatching.scala        # 模式匹配与代数数据类型
│   ├── 03_Immutability.scala           # 不可变性与柯里化
│   ├── 04_ErrorHandling.scala          # Option / Either / Try 错误处理
│   ├── 05_RecursionAndTailRec.scala    # 递归与尾递归
│   ├── 06_LazyList.scala               # 惰性求值与 LazyList
│   ├── 07_TypeClassStyle.scala         # Type Class 风格
│   ├── 08_FormValidation.scala         # 表单校验与 Either
│   ├── 09_OrderStateMachine.scala      # 订单状态机与 ADT
│   ├── 10_ExprEvaluator.scala          # 表达式求值器
│   ├── 11_RecursiveJson.scala          # 递归 JSON 数据结构
│   ├── 12_ValidatedRegistration.scala  # Validated 风格注册校验
│   ├── 13_SemigroupAndMonoid.scala     # 可合并抽象
│   ├── 14_FunctorApplicativeMonad.scala# Functor / Applicative / Monad
│   ├── 15_ReaderConfig.scala           # Reader 环境依赖建模
│   ├── 16_StateCalculator.scala        # State 状态推进
│   ├── 17_IOBasics.scala               # IO：先描述副作用，再执行
│   ├── 18_ResourceDemo.scala           # Resource：资源安全释放
│   ├── 19_ConcurrencyDemo.scala        # 并发组合与 Future
│   ├── 20_FS2Pipeline.scala            # 流处理直觉（Iterator / LazyList）
│   ├── 21_Http4sMiniService.scala      # 最小 http4s 风格服务
│   ├── 22_TaglessUserService.scala     # Tagless Final 风格用户服务
│   ├── 23_KleisliRequestPipeline.scala # Kleisli / ReaderT 请求管道
│   ├── 24_RetryBackoff.scala           # Retry / Backoff 重试策略
│   ├── 25_TaglessTestInterpreter.scala # Tagless Final 测试解释器
│   ├── 26_CatsEffectIOApp.scala        # 真正的 cats-effect IO / Fiber
│   ├── 27_CatsEffectResource.scala     # 真正的 cats-effect Resource
│   ├── 28_FS2StreamWorkflow.scala      # 真正的 fs2 Stream 数据管道
│   ├── 29_Http4sRoutes.scala           # 真正的 http4s Routes / Middleware
│   ├── 30_TaglessCatsEffect.scala      # 真正的 cats-effect Tagless 解释器
│   ├── 31_CirceJsonCodec.scala         # 真正的 circe JSON 编解码
│   ├── 32_Http4sJsonApi.scala          # 真正的 http4s JSON API
│   ├── 33_Http4sClientDemo.scala       # 真正的 http4s Client 调用
│   ├── 34_FS2QueueWorker.scala         # 真正的 fs2 Queue 工作流
│   ├── 35_CatsEffectTimeoutAndCancel.scala # cats-effect timeout / cancel / finalizer
│   ├── 36_CatsEffectDeferredRef.scala  # cats-effect Ref / Deferred 协作
│   ├── 37_FS2TopicPubSub.scala         # fs2 Topic 发布订阅
│   ├── 38_Http4sBearerAuth.scala       # http4s Bearer Token 鉴权
│   ├── 39_EmberServerClientRoundTrip.scala # Ember Server / Client 本地联调
│   ├── 40_MUnitCatsEffectSuite.scala   # munit-cats-effect 服务测试
│   ├── 41_CatsEffectSemaphore.scala    # cats-effect Semaphore 并发限流
│   ├── 42_FS2ParEvalMap.scala          # fs2 parEvalMap 并行流处理
│   ├── 43_Http4sErrorHandling.scala    # http4s 领域错误映射
│   ├── 44_TaglessHttp4sUserModule.scala # Tagless + http4s 模块装配
│   ├── 45_MUnitHttp4sRouteSuite.scala  # http4s 路由集成测试
│   ├── 46_CatsEffectSupervisor.scala   # cats-effect Supervisor 后台任务托管
│   ├── 47_FS2MergeStreams.scala        # fs2 多路流合并
│   ├── 48_Http4sAuthMiddleware.scala   # http4s AuthMiddleware / AuthedRoutes
│   ├── 49_EitherTUserFlow.scala        # EitherT 业务流程编排
│   ├── 50_MUnitAuthMiddlewareSuite.scala # AuthMiddleware 集成测试
│   ├── 51_CatsEffectRace.scala         # cats-effect race 竞速与自动取消
│   ├── 52_FS2ErrorRecovery.scala       # fs2 流式错误恢复
│   ├── 53_Http4sClientMiddleware.scala # http4s client 中间件与 trace 透传
│   ├── 54_Http4sClientAggregation.scala # http4s 下游服务聚合编排
│   ├── 55_MUnitClientOrchestrationSuite.scala # 下游聚合编排测试
│   ├── 56_CatsEffectUncancelable.scala # uncancelable / poll 取消边界
│   ├── 57_FS2InterruptAndSignallingRef.scala # SignallingRef 停流信号
│   ├── 58_Http4sClientRetry.scala      # http4s client 重试治理
│   ├── 59_Http4sContextRoutes.scala    # ContextMiddleware / ContextRoutes
│   ├── 60_MUnitClientRetrySuite.scala  # client 重试策略测试
│   ├── 61_CatsEffectDispatcher.scala   # Dispatcher 桥接旧式回调边界
│   ├── 62_FS2GroupWithin.scala         # fs2 groupWithin 批处理窗口
│   ├── 63_Http4sStreamingApi.scala     # http4s 流式响应 API
│   ├── 64_EmberStreamingClient.scala   # Ember client 消费流式响应
│   ├── 65_MUnitStreamingRouteSuite.scala # 流式路由测试
│   ├── 66_CatsEffectIOLocal.scala      # IOLocal fiber 本地上下文
│   ├── 67_FS2PullLineDecoder.scala     # fs2 Pull 自定义按行解码
│   ├── 68_Http4sServerSentEvents.scala # http4s Server-Sent Events
│   ├── 69_EmberSseClient.scala         # Ember client 消费真实 SSE
│   ├── 70_MUnitServerSentEventsSuite.scala # SSE 路由测试
│   ├── 71_CatsEffectMapRef.scala       # MapRef 按 key 分片状态
│   ├── 72_FS2TopicHub.scala            # Topic 房间广播枢纽
│   ├── 73_Http4sWebSocketChat.scala    # http4s WebSocket 聊天路由
│   ├── 74_JdkWebSocketBridgeClient.scala # JDK WebSocket callback 桥接
│   ├── 75_MUnitWebSocketChatSuite.scala # WebSocket 路由测试
│   ├── 76_Http4sMultipartUpload.scala  # http4s Multipart 文件上传
│   ├── 77_FS2ChunkedFileProcessor.scala # fs2 固定分块处理大文件流
│   ├── 78_DoobieTransactorResource.scala # Doobie Transactor 资源与事务回滚
│   ├── 79_DoobieRepositoryTagless.scala # Tagless Repository + Doobie 解释器
│   ├── 80_MUnitRepositoryIntegrationSuite.scala # Repository 集成测试
│   ├── 81_DoobieStreamingExport.scala  # Doobie 流式导出数据库报表
│   ├── 82_FS2CsvImportPipeline.scala   # fs2 CSV 导入解析与分批管道
│   ├── 83_Http4sCsvExport.scala        # http4s 流式 CSV 下载接口
│   ├── 84_DoobieBatchImportTagless.scala # Tagless 批量导入模块
│   ├── 85_MUnitBatchImportExportSuite.scala # 导入导出一体化集成测试
│   ├── 86_CatsEffectIdempotencyGate.scala # cats-effect 并发幂等门闩
│   ├── 87_FS2DedupReservationStream.scala # fs2 重复请求流去重
│   ├── 88_Http4sIdempotencyKey.scala   # http4s Idempotency-Key 写接口
│   ├── 89_DoobieIdempotentReservation.scala # Doobie 持久化幂等写入
│   ├── 90_MUnitIdempotencyIntegrationSuite.scala # 幂等写接口集成测试
│   ├── 91_CatsEffectOutboxCoordinator.scala # cats-effect Outbox 协调器
│   ├── 92_FS2OutboxRetryStream.scala   # fs2 Outbox 重试发布流
│   ├── 93_Http4sWebhookOutbox.scala    # http4s Webhook + Outbox 发布边界
│   ├── 94_DoobieTransactionalOutbox.scala # Doobie 事务 Outbox
│   ├── 95_MUnitTransactionalOutboxSuite.scala # 事务 Outbox 集成测试
│   ├── 96_CatsEffectInboxCoordinator.scala # cats-effect Inbox 协调器
│   ├── 97_FS2InboxRetryConsumer.scala  # fs2 Inbox 重试消费流
│   ├── 98_Http4sWebhookInbox.scala     # http4s Webhook Inbox 接收边界
│   ├── 99_DoobieTransactionalInbox.scala # Doobie 事务 Inbox
│   ├── 100_MUnitTransactionalInboxSuite.scala # 事务 Inbox 集成测试
│   ├── 101_CatsEffectSagaCoordinator.scala # cats-effect Saga 协调器
│   ├── 102_FS2SagaTimeoutCompensationStream.scala # fs2 Saga 超时补偿流
│   ├── 103_Http4sSagaWorkflow.scala    # http4s Saga 工作流边界
│   ├── 104_DoobieTransactionalSagaState.scala # Doobie 事务 Saga 状态
│   ├── 105_MUnitSagaIntegrationSuite.scala # Saga 集成测试
│   ├── 106_CatsEffectProjectionCoordinator.scala # cats-effect 读模型投影协调器
│   ├── 107_FS2ProjectionReplayStream.scala # fs2 读模型回放流
│   ├── 108_Http4sReadModelQuery.scala  # http4s 读模型查询边界
│   ├── 109_DoobieTransactionalProjectionCheckpoint.scala # Doobie 事务投影 checkpoint
│   ├── 110_MUnitProjectionReplaySuite.scala # 读模型回放集成测试
│   ├── 111_CatsEffectCommandBus.scala  # cats-effect 命令总线
│   ├── 112_FS2CommandRouterStream.scala # fs2 命令路由流
│   ├── 113_Http4sCQRSBoundary.scala    # http4s CQRS 命令/查询双边界
│   ├── 114_DoobieTransactionalCommandWrite.scala # Doobie 事务命令写入
│   ├── 115_MUnitCQRSIntegrationSuite.scala # CQRS 集成测试
│   ├── 116_CatsEffectEventSourcedAggregate.scala # cats-effect 事件溯源聚合根
│   ├── 117_FS2EventAppendStream.scala  # fs2 事件追加流
│   ├── 118_Http4sEventStoreEndpoint.scala # http4s Event Store 端点
│   ├── 119_DoobieEventStoreRepository.scala # Doobie 事件存储仓库
│   ├── 120_MUnitEventSourcingSuite.scala # 事件溯源集成测试
│   ├── 121_CatsEffectProcessManager.scala # cats-effect 进程管理器
│   ├── 122_FS2ProcessManagerEventRouter.scala # fs2 事件路由到进程管理器
│   ├── 123_Http4sProcessManagerBoundary.scala # http4s 进程管理器边界
│   ├── 124_DoobieProcessManagerRepository.scala # Doobie 进程管理器仓库
│   ├── 125_MUnitProcessManagerSuite.scala # 进程管理器集成测试
│   ├── 126_CatsEffectACLTranslator.scala  # cats-effect 防腐层翻译器
│   ├── 127_FS2ACLTranslationStream.scala  # fs2 上游事件翻译流
│   ├── 128_Http4sACLAdapterEndpoint.scala # http4s ACL 适配端点
│   ├── 129_DoobieACLTranslationLog.scala  # Doobie ACL 翻译日志
│   ├── 130_MUnitACLIntegrationSuite.scala # 防腐层集成测试
│   ├── 131_CatsEffectContextMapAssembly.scala # cats-effect 上下文地图装配
│   ├── 132_FS2CrossContextEventBus.scala # fs2 跨上下文事件总线
│   ├── 133_Http4sContextMapGateway.scala # http4s 统一网关
│   ├── 134_DoobieMultiContextTransaction.scala # Doobie 多上下文事务
│   ├── 135_MUnitContextMapEndToEndSuite.scala # 端到端集成测试
│   └── SCALA_FP_ROADMAP.md             # Scala FP 学习路线图
├── scala/akka/                               # Akka Actor 模型（与 Scala FP 对比）
│   ├── 01_ActorBasics.scala            # Actor 基础：消息、行为、ask 模式
│   ├── 02_ActorStateAndFSM.scala       # Actor 状态机（对比 Demo 09）
│   ├── 03_ActorStreams.scala           # Akka Streams（对比 fs2）
│   ├── 04_AkkaPersistence.scala       # 事件溯源（对比 Demo 116）
│   ├── 05_AkkaProjection.scala        # CQRS 读模型投影（对比 Demo 106-110）
│   ├── 06_SupervisionStrategy.scala   # 监督策略（Let it crash）
│   ├── 07_ClusterSharding.scala       # Cluster Sharding 分布式 Actor
│   ├── 08_AkkaHttp.scala             # Akka HTTP Directive DSL（对比 http4s）
│   ├── 09_ActorTestKit.scala          # BehaviorTestKit + ActorTestKit（对比 munit）
│   ├── 10_ActorTimers.scala           # 定时器（对比 fs2 Stream.awakeEvery）
│   ├── 11_ActorPubSub.scala           # EventStream 发布订阅（对比 fs2 Topic）
│   ├── 12_ActorStash.scala            # Stash 消息暂存（对比 Deferred）
│   ├── 13_ActorRouter.scala           # Router 负载均衡（对比 parEvalMap）
│   ├── 14_AkkaStreamsAdvanced.scala   # Streams 高级：throttle/conflate/zip/groupBy
│   ├── 15_PersistenceQuery.scala      # Persistence Query（事件溯源读侧，文档说明）
│   ├── 16_ActorRequestResponse.scala  # 请求-响应进阶：超时/并发聚合
│   ├── 17_AkkaStreamsKillSwitch.scala # KillSwitch 流控制（对比 SignallingRef）
│   └── 18_ActorReceptionist.scala     # Receptionist 服务发现（动态注册/订阅）
├── erlang/                             # 面向并发的函数式语言
│   ├── 01_pattern_matching.erl         # 模式匹配与递归
│   ├── 02_higher_order.erl             # 高阶函数与列表推导
│   ├── 03_actor_model.erl              # Actor 模型 (进程与消息传递)
│   ├── 04_gen_server_counter.erl       # gen_server 行为（OTP 入门）
│   ├── 05_supervisor_tree.erl          # Supervisor 树＋重启策略（Let it crash）
│   ├── 06_ets_and_state.erl            # ETS 共享状态与并发读
│   ├── 07_distributed_nodes.erl        # 分布式节点 + rpc:call
│   └── 08_property_testing_proper.erl  # 属性测试（手写迷你 PropEr）
├── haskell/                            # 纯函数式语言的典范
│   ├── 01_PureAndLazy.hs               # 纯函数与惰性求值
│   ├── 02_TypeClassAndMonad.hs         # 类型类与 Functor/Monad
│   ├── 03_CurryAndCompose.hs           # 柯里化与函数组合
│   ├── 04_IOAndSideEffects.hs          # IO Monad 与副作用建模
│   ├── 05_StateAndReader.hs            # State / Reader Monad
│   ├── 06_ConcurrencySTM.hs            # STM 软件事务内存与并发
│   ├── 07_TypesAndADT.hs               # 代数数据类型与 newtype 技巧
│   ├── 08_MonadTransformers.hs         # Monad Transformer（ExceptT/StateT/ReaderT）
│   ├── 09_LensAndOptics.hs             # Lens / Prism / Optics 基础
│   ├── 10_ParserCombinators.hs         # 解析器组合子（手写 Parser Monad）
│   ├── 11_FreeMonadsAndDSL.hs          # Free Monad 与 DSL 解释器
│   ├── 12_QuickCheck.hs                # QuickCheck 基于性质的测试
│   ├── 13_ArrowAndProfunctor.hs        # Arrow / Profunctor
│   ├── 14_FoldableTraversable.hs       # Foldable / Traversable 遍历与聚合
│   ├── 15_TypesAdvanced.hs             # GADT / Phantom Type / DataKinds / TypeFamilies
│   ├── 16_StreamingPipeline.hs         # 手写最小流式管道（对照 fs2/conduit）
│   ├── 17_LambdaCalculusAndFix.hs      # Lambda 演算 / fix / Church encoding / cata-ana-hylo
│   ├── 18_AlternativeAndMonadPlus.hs   # Alternative / MonadPlus / 回溯与非确定计算
│   ├── 19_ExceptionsAndConcurrency.hs  # 异常 / bracket / MVar / 手写最小 async
│   ├── 20_GenericsAndDerivingVia.hs    # GHC.Generics / DerivingVia / Identity-Const-Compose
│   ├── 21_EffectSystemPatterns.hs      # Effect System：MTL 风格 + 手写 Free Eff
│   ├── 22_FPBestPracticesAndStateMachineTests.hs  # 状态机属性测试 + 工程最佳实践收口
│   ├── 23_Comonad.hs                     # Comonad：NonEmpty / Zipper / Store 三大实例
│   ├── 24_RecursionSchemes.hs            # Recursion Schemes: cata / ana / hylo / para
│   ├── 25_DependentTypesAndSingletons.hs # 依值类型直觉：长度索引向量
│   ├── 26_AdvancedResearchDirections.hs  # FP 研究方向地图 + 跨语言对照（收官）
│   └── HASKELL_FP_ROADMAP.md           # Haskell FP 学习路线图
├── rust/                               # 系统级语言中的函数式特性
│   ├── 01_iterators_and_closures.rs    # 迭代器与闭包
│   ├── 02_enum_and_option.rs           # 枚举、模式匹配与 Option/Result
│   ├── 03_trait_and_generics.rs        # Trait 与泛型函数式编程
│   ├── 04_error_handling_result_chain.rs # Result + ? + 自定义错误链
│   ├── 05_ownership_and_fp.rs          # 所有权 × FP: Fn / FnMut / FnOnce
│   ├── 06_iterator_internals.rs        # 手写 Iterator + 自定义适配器
│   ├── 07_smart_pointers_fp.rs         # Rc / Arc / RefCell / Mutex 的 FP 用法
│   ├── 08_async_streams_tokio.rs       # async/await + 最小 Future 执行器 + Stream
│   ├── 09_typestate_pattern.rs         # Typestate：用类型刻协议（对应 Haskell Phantom）
│   └── 10_parser_combinators.rs        # 零依赖 parser combinators（对应 Haskell 10）
├── elixir/                             # BEAM 上的现代化语法：Erlang 的孪生兄弟
│   ├── 01_basics_pipeline.exs          # 基础语法 + 管道 |> + 模式匹配
│   ├── 02_struct_protocol_behaviour.exs# struct / protocol / behaviour 多态三件套
│   ├── 03_with_result_flow.exs         # with 语句 × Result 流程控制
│   ├── 04_macros_intro.exs             # 宏系统入门 quote/unquote/defmacro
│   ├── 05_macros_dsl_router.exs        # 宏进阶：自制路由 DSL
│   ├── 06_genserver_agent_task.exs     # GenServer / Agent / Task
│   ├── 07_supervisor_registry.exs      # Supervisor / DynamicSupervisor / Registry
│   ├── 08_task_supervisor_async_stream.exs # Task.Supervisor × async_stream
│   ├── 09_ecto_repo_changeset_multi.exs# Ecto: Repo / Schema / Changeset / Multi
│   ├── 10_phoenix_router_plug.exs      # Phoenix 风格 Plug 路由（http 4001）
│   ├── 11_liveview_mental_model.exs    # LiveView 心智模型（本地版）
│   ├── 12_flow_genstage_broadway.exs   # Flow / GenStage / Broadway 流式管道
│   ├── 13_telemetry_opentelemetry.exs  # Telemetry + OpenTelemetry
│   ├── 14_exunit_doctest_mox_streamdata.exs # ExUnit + doctest + Mox + StreamData
│   ├── 15_mix_umbrella_releases.exs    # mix / umbrella / releases 骨架
│   ├── run.sh                          # Elixir Demo 一键运行脚本
│   └── ELIXIR_FP_ROADMAP.md            # Elixir FP 学习路线图
├── LANGUAGE_COMPARISON.md              # 多语言跨语言对照表
├── LANGUAGE_USE_CASES.md               # 四种语言应用场景与 Demo 索引
└── README.md
```

---

## 🎯 核心概念一览

| 函数式概念 | Scala | Erlang | Elixir | Haskell | Rust | 简要说明 |
|:---:|:---:|:---:|:---:|:---:|:---:|:---|
| **高阶函数** | ✅ 01 | ✅ 02 | ✅ 01 | ✅ 01 | ✅ 01 | 函数作为参数/返回值，`map/filter/fold` |
| **模式匹配** | ✅ 02 / 09 | ✅ 01 | ✅ 01 / 03 | ✅ 02 | ✅ 02 | 按数据结构和值进行分支 |
| **代数数据类型 (ADT)** | ✅ 02 / 09 / 10 / 11 | — | ✅ 02 | ✅ 02 | ✅ 02 | 用类型系统表达状态与结构（Elixir 用 struct + protocol + tuple tag 组合表达）|
| **不可变性** | ✅ 03 / 09 | 🔵 天然 | 🔵 天然 | 🔵 天然 | ✅ 03 | 数据创建后不可修改 |
| **柯里化 / 部分应用** | ✅ 03 | — | ✅ 01 | ✅ 03 | ✅ 01 | 多参函数拆为单参函数链（Elixir 用 `&fun/3` 捕获 + 管道达成等价效果）|
| **函数组合** | ✅ 03 | ✅ 02 | ✅ 01 | ✅ 03 | ✅ 03 | 将小函数组合成复杂管道（Elixir 的 `\|>` 是招牌语法）|
| **错误处理 (Either / Try)** | ✅ 04 / 08 / 10 | — | ✅ 03 | ✅ 02 | ✅ 02 | 把失败显式放进类型，而不是滥用异常（Elixir 用 `{:ok, _}` / `{:error, _}` tuple）|
| **递归 / 尾递归** | ✅ 05 / 10 / 11 | ✅ 01 | ✅ 01 | ✅ 01 | — | 用递归替代循环，并递归处理树形结构（BEAM 保证尾递归零栈增长）|
| **惰性求值** | ✅ 06 / 20 | — | ✅ 08 / 12 | ✅ 01 | — | 表达式延迟到真正需要时才求值（Elixir 以 `Stream` / `Flow` 表达惰性管道）|
| **Monad (Option/Maybe)** | ✅ 02 / 04 / 14 | — | ✅ 03 | ✅ 02 | ✅ 02 | 在上下文中链式计算（Elixir 用 `with` 语句近似 do-notation）|
| **类型类 / Trait** | ✅ 07 | — | ✅ 02 | ✅ 02 | ✅ 03 | 类型多态的抽象机制（Elixir 的 Protocol 是运行时 type class）|
| **业务校验** | ✅ 08 / 12 | — | ✅ 03 / 09 | ✅ 02 | ✅ 02 | 用纯函数描述校验规则（Elixir 以 `Ecto.Changeset` 管道化）|
| **状态机建模** | ✅ 09 | ✅ 03 | ✅ 06 / 11 | ✅ 15 | — | 用 ADT / 行为表达合法与非法状态流转（Elixir 以 GenServer + pattern match 表达）|
| **递归数据结构** | ✅ 10 / 11 | ✅ 01 | ✅ 01 | ✅ 01 / 02 | ✅ 02 | 表达式树、JSON、树结构等 |
| **Semigroup / Monoid** | ✅ 13 | — | — | ✅ 02 / 14 | — | 抽象“可合并”的数据与规则 |
| **Functor / Applicative / Monad** | ✅ 14 | — | ✅ 03 | ✅ 02 / 09 | — | 在上下文里变换、组合、串联计算 |
| **Reader / State** | ✅ 15 / 16 | — | ✅ 06 | ✅ 05 | — | 用纯函数表达环境依赖与状态推进（Elixir 以 Agent / GenServer 封装状态）|
| **IO / 副作用建模** | ✅ 17 / 26 / 30 | — | ✅ 06 / 10 | ✅ 04 | — | 先描述副作用，再决定何时执行，并进一步进入真实 effect system |
| **Resource** | ✅ 18 / 27 | — | ✅ 07 | — | — | 统一管理资源申请、使用和释放（Elixir 以 Supervisor / DynamicSupervisor 托管资源）|
| **并发组合** | ✅ 19 / 26 | ✅ 03 | ✅ 06 / 08 | ✅ 06 | — | 组合独立任务，并行后再汇总结果（Elixir 用 Task / async_stream）|
| **流处理 / Stream** | ✅ 20 / 28 | — | ✅ 08 / 12 | ✅ 01 / 16 | — | 像管道一样逐步消费大数据或无限流（Elixir 的 Flow/GenStage/Broadway）|
| **HTTP 服务建模** | ✅ 21 / 29 | — | ✅ 10 / 11 | — | — | 把请求、响应、路由、中间件组织成可组合函数（Elixir 的 Plug / Phoenix / LiveView）|
| **Tagless Final / Algebra** | ✅ 22 / 25 / 30 | — | ✅ 02 | ✅ 11 | — | 抽象业务能力，再替换不同解释器（Elixir 以 behaviour + Mox 达成可替换解释器）|
| **Monad Transformer** | — | — | — | ✅ 08 | — | 堆叠多个 effect（ExceptT / StateT / ReaderT）|
| **Lens / Optics** | — | — | — | ✅ 09 / 13 | — | `view` / `set` / `over` 聚焦嵌套结构的某个字段 |
| **Parser 组合子** | — | — | — | ✅ 10 | — | 用小 parser 组合出完整解析器 |
| **性质测试 / QuickCheck** | — | — | ✅ 14 | ✅ 12 | — | 对任意输入成立的性质 + 自动 shrink（Elixir 的 StreamData）|
| **Arrow / Profunctor** | — | — | — | ✅ 13 | — | 比 Monad 更抽象的计算模型，滨变 + 协变组合 |
| **Foldable / Traversable** | — | — | — | ✅ 14 | — | 用 Monoid 统一聚合，用 `traverse` 把 effect 从结构内抽出来 |
| **类型级编程 (GADT / DataKinds / TypeFamilies)** | — | — | — | ✅ 15 | — | 把约束从运行时提升到编译时 |
| **Lambda 演算 / fix / Church encoding** | — | — | — | ✅ 17 | — | FP 的数学底座，匿名递归与折叠同构 |
| **Alternative / MonadPlus** | — | — | — | ✅ 18 | — | 失败 / 选择 / 回溯搜索的统一抽象 |
| **异常 / bracket / 轻量级并发** | — | — | — | ✅ 19 | — | `try / throwIO / bracket / MVar / forkIO`，手写 async |
| **Generics / DerivingVia** | — | — | — | ✅ 20 | — | 类型驱动的编解码与实例复用 |
| **Effect System (MTL / Free)** | — | — | ✅ 13 | ✅ 21 | — | 同一段业务，生产 / 测试 / Trace 解释器自由切换（Elixir 的 Telemetry 也是同构思路）|
| **状态机属性测试 / 工程最佳实践** | — | — | ✅ 14 | ✅ 22 | — | 随机命令序列对照纯模型，附完整工程清单（Elixir 以 ExUnit + StreamData 达成）|
| **Kleisli / ReaderT** | ✅ 23 | — | — | — | — | 把环境依赖与 effect 组合成请求管道 |
| **Retry / Backoff** | ✅ 24 | — | — | — | — | 把失败恢复策略从业务中分离出来 |
| **测试解释器** | ✅ 25 | — | — | — | — | 用纯状态替代真实外部系统，验证业务逻辑 |
| **JSON 编解码** | ✅ 31 | — | — | — | ✅ 02 | 用类型驱动协议，而不是手写字符串拼接 |
| **JSON API** | ✅ 32 | — | — | — | — | 把请求体解析、响应体编码和业务校验接进 HTTP 层 |
| **HTTP Client** | ✅ 33 / 39 / 53 / 54 | — | — | — | — | 作为调用方消费下游服务并解码响应，并进一步补齐中间件与下游聚合 |
| **Queue 工作流** | ✅ 34 | — | — | — | — | 用队列表达生产者 / 消费者和异步任务分发 |
| **取消 / 超时控制** | ✅ 35 | — | — | — | — | 把 timeout、cancel、finalizer 纳入统一 effect 模型 |
| **Ref / Deferred 协作** | ✅ 36 | — | — | — | — | 用并发安全状态和一次性信号协调异步流程 |
| **Topic 发布订阅** | ✅ 37 / 72 | — | — | — | — | 用广播模型把同一条事件分发给多个订阅者，并继续推进到房间广播枢纽 |
| **Bearer 鉴权** | ✅ 38 | — | — | — | — | 在 HTTP 中间件层完成认证和权限控制 |
| **测试框架化** | ✅ 40 / 55 | — | — | — | — | 把带 IO 的服务与调用方编排都放进真实测试框架里执行断言 |
| **并发限流** | ✅ 41 | — | — | — | — | 用 Semaphore 限制关键区和下游资源的并发占用 |
| **流式并行处理** | ✅ 42 | — | — | — | — | 在 fs2 流里对元素做有序或无序的并发 effect 处理 |
| **领域错误映射** | ✅ 43 | — | — | — | — | 先返回业务错误，再在 HTTP 边界统一翻译成响应 |
| **模块装配** | ✅ 44 | — | — | — | — | 把 algebra、service、解释器和 routes 组装成完整服务模块 |
| **路由集成测试** | ✅ 45 | — | — | — | — | 直接对 http4s 路由做请求 / 响应级断言 |
| **后台任务托管** | ✅ 46 | — | — | — | — | 用 Supervisor 在作用域内统一托管和回收后台 fiber |
| **多路流合并** | ✅ 47 | — | — | — | — | 把不同来源的事件流合并进同一条 fs2 管道 |
| **AuthMiddleware 鉴权** | ✅ 48 / 50 | — | — | — | — | 用官方认证中间件把用户上下文和失败处理接入路由 |
| **EitherT 错误编排** | ✅ 49 / 54 | — | — | — | — | 把领域错误和 effect 叠成一条可组合业务流程，并推进到下游服务聚合 |
| **竞速与自动取消** | ✅ 51 | — | — | — | — | 用 race 让多个来源并发竞争，并自动取消输掉的一方 |
| **流式错误恢复** | ✅ 52 | — | — | — | — | 在 fs2 里区分 error channel 和 value channel 的恢复策略 |
| **Client 中间件** | ✅ 53 | — | — | — | — | 在调用方统一注入 traceId、日志和上下文头部 |
| **下游服务聚合** | ✅ 54 | — | — | — | — | 组合多个下游响应，拼装成上层视图或聚合接口 |
| **调用方编排测试** | ✅ 55 | — | — | — | — | 验证下游编排、短路和领域错误映射是否符合预期 |
| **取消边界** | ✅ 56 | — | — | — | — | 用 `uncancelable` / `poll` 精细划分可等待区和不可中断关键区 |
| **停流信号** | ✅ 57 | — | — | — | — | 用 `SignallingRef` 显式传播长生命周期流的停止条件 |
| **调用方重试治理** | ✅ 58 / 60 | — | — | — | — | 把 503 重试、404 直返、退避和策略测试纳入统一治理 |
| **请求上下文注入** | ✅ 59 | — | — | — | — | 用 `ContextMiddleware` / `ContextRoutes` 类型安全地下发上下文 |
| **回调边界桥接** | ✅ 61 | — | — | — | — | 用 `Dispatcher` 把旧式回调重新接回 `IO` / `Queue` / `Stream` |
| **批处理窗口** | ✅ 62 | — | — | — | — | 用 `groupWithin` 按数量或时间窗口聚合零散事件 |
| **HTTP 流式响应** | ✅ 63 / 65 | — | — | — | — | 让路由持续产出字节流，并对流式输出做自动化验证 |
| **真实流式 Client** | ✅ 64 / 69 / 74 | — | — | — | — | 在真实网络下边接收边解码边消费响应体，并继续补齐 SSE / WebSocket 客户端消费 |
| **按 key 分片状态** | ✅ 71 | — | — | — | — | 用 `MapRef` 管理房间、租户、会话等按 key 拆分的原子状态 |
| **WebSocket 双向通信** | ✅ 73 / 74 / 75 | — | — | — | — | 让客户端和服务端在同一条连接里持续双向收发消息，并进入测试闭环 |
| **Multipart 上传** | ✅ 76 | — | — | — | — | 在同一请求里同时接收表单字段和文件流 |
| **分块文件处理** | ✅ 77 | — | — | — | — | 把连续字节流重组为固定大小的处理块，服务于重试、断点续传和分片导入 |
| **数据库资源与事务** | ✅ 78 / 80 | — | — | — | — | 用 `Resource` 与事务边界统一管理数据库连接、回滚和集成测试 |
| **Repository + SQL 解释器** | ✅ 79 / 80 | — | — | — | — | 把 Tagless Repository 落到真实 SQL 仓储，并用真实数据库回归验证 |
| **数据库流式导出** | ✅ 81 | — | — | — | — | 用 `query.stream` 把数据库结果逐行导出成报表流 |
| **CSV 导入管道** | ✅ 82 / 84 / 85 | — | — | — | — | 把字节流解析、校验、分批并落到真实仓储边界 |
| **CSV 下载接口** | ✅ 83 / 85 | — | — | — | — | 用 http4s 流式返回报表文件，并对导出内容做自动化验证 |
| **重复请求去重** | ✅ 86 / 87 | — | — | — | — | 用并发门闩和流式去重拦住同进程重复提交与消息重放 |
| **幂等写入** | ✅ 88 / 89 / 90 | — | — | — | — | 用 `Idempotency-Key` 和数据库持久化记录保证 POST 重试不重复落库 |
| **事务 Outbox / 最终一致性** | ✅ 91 / 92 / 93 / 94 / 95 | — | — | — | — | 用同事务写入、后台重试和发布状态推进把“写库后发事件”真正做可靠 |
| **事务 Inbox / 消费端幂等** | ✅ 96 / 97 / 98 / 99 / 100 | — | — | — | — | 用消费端去重、Webhook 接收保护和事务 processed_event 记录保证下游重复投递只真正处理一次 |
| **Saga 补偿 / 跨服务工作流** | ✅ 101 / 102 / 103 / 104 / 105 | — | — | — | — | 用补偿动作、超时扫描、支付回调和事务状态推进组织跨步骤业务一致性 |
| **读模型投影 / 事件回放** | ✅ 106 / 107 / 108 / 109 / 110 | — | — | — | — | 用 checkpoint、lag、查询侧重建和事务投影保证读模型可追赶、可回放、可验证 |
| **CQRS 命令查询职责分离** | ✅ 111 / 112 / 113 / 114 / 115 | — | — | — | — | 把写侧命令总线和读侧查询路由显式分开，命令 202 不返回读模型，查询不产生副作用 |
| **事件溯源** | ✅ 116 / 117 / 118 / 119 / 120 | — | — | — | — | 只存事件序列，状态从 fold 重建；乐观锁防并发冲突；时间旅行可重建历史时刻 |
| **进程管理器** | ✅ 121 / 122 / 123 / 124 / 125 | — | — | — | — | 跨有界上下文工作流协调；监听事件→推进状态→发出命令；幂等事件处理与命令 Outbox |
| **防腐层（ACL）** | ✅ 126 / 127 / 128 / 129 / 130 | — | — | — | — | 把上游模型翻译成本地领域概念；翻译失败写拒绝日志；幂等接收与内部领域保护 |
| **有界上下文地图集成** | ✅ 131 / 132 / 133 / 134 / 135 | — | — | — | — | 四个有界上下文装配成完整履约系统；统一网关聚合视图；端到端集成验证 |
| **列表推导** | — | ✅ 02 | ✅ 01 | ✅ 01 | — | 声明式的列表构建语法（Elixir 的 `for` 支持 generator/filter/into）|
| **Actor 并发模型** | — | ✅ 03 | ✅ 06 / 07 | — | — | 进程间消息传递，无共享状态（Elixir 的 GenServer / Agent / Task / Registry）|
| **Akka Actor / 状态机 / Streams / Persistence / Projection** | — (见 scala/akka/) | ✅ 03 | ✅ 06 / 07 / 12 | — | — | Akka Typed Actor、FSM、Akka Streams、EventSourcedBehavior、CQRS 投影 |
| **宏与元编程** | — | — | ✅ 04 / 05 | ✅ 20 | ✅ 20 / 21 | 把代码当数据：`quote` / `unquote` / `defmacro` / DSL 生成（Elixir 是招牌能力）|
| **GenServer / Agent / Task** | — | ✅ 04 | ✅ 06 | — | — | OTP 的行为抽象三件套：同步/异步 server、状态持有者、一次性异步任务 |
| **Supervisor 树 / Registry** | — | ✅ 05 | ✅ 07 | — | — | 用监督树保证进程崩溃自愈，用 Registry 做 key→pid 命名 |
| **Phoenix / Plug / LiveView** | — | — | ✅ 10 / 11 | — | — | Elixir Web 全家桶：管道式路由、服务器端渲染、WebSocket 心智模型 |
| **Ecto (Repo / Changeset / Multi)** | — | — | ✅ 09 | — | — | 函数式 ORM：schema + 校验管道 + 多步事务打包 |
| **GenStage / Flow / Broadway** | — | — | ✅ 12 | — | — | 带背压的生产者-消费者与真实消息中间件对接的流式管道 |
| **Telemetry / OpenTelemetry** | — | — | ✅ 13 | — | — | 运行时结构化事件 + OTel 标准 trace/metric 的原生桥接 |
| **ExUnit / doctest / Mox / StreamData** | — | — | ✅ 14 | — | — | 异步单测、文档即测试、行为 mock、属性测试一套打包 |
| **mix / umbrella / releases** | — | — | ✅ 15 | — | — | Elixir 的工程化三板斧：构建工具、多应用 monorepo、自包含发布包 |
| **Erlang ↔ Elixir 互操作** | — | ✅ 25 | ✅ 01~15 | — | — | 同一套 BEAM 运行时，`.beam` 100% 互通；Elixir 模块 `Foo.Bar` ↔ Erlang `'Elixir.Foo.Bar'` |

---

## 📖 各语言 Demo 详解

### 🔷 Scala — JVM 上的函数式编程

Scala 融合了面向对象和函数式编程，是 Java 开发者学习 FP 的最佳桥梁。当前这组 Demo 已经从集合操作一路延伸到错误处理、尾递归、惰性求值、Type Class 风格，再到 `Validated`、`Monoid`、`Functor/Applicative/Monad`、`Reader`、`State`，以及 `IO`、`Resource`、并发组合、流处理直觉、最小 HTTP 服务、Tagless Final、Kleisli、Retry / Backoff、测试解释器，并继续进入真实 `cats-effect`、`fs2`、`http4s` 的高级实战、高级收束、工程化深化、服务联通、服务治理与上下文、边界桥接与流式服务、本地上下文与协议化事件流、房间状态与双向实时通信、上传边界与数据库集成、批量导入导出与流式报表、幂等写入与重复请求治理、事务 Outbox 与最终一致性、事务 Inbox 与消费端幂等、Saga 补偿与跨服务工作流、读模型投影与事件回放、CQRS 命令查询职责分离，以及事件溯源阶段，覆盖协议层、并发治理、服务边界、模块装配、下游调用、恢复策略、流式接口、上下文隔离、双向实时连接、文件上传、分块处理、数据库事务、报表导出、重复请求治理、事务消息、消费端重放保护、跨服务补偿编排、查询侧 checkpoint 治理、命令总线与写读分离、事件聚合根重建与乐观锁、跨上下文工作流协调、上游模型翻译与本地领域保护、完整有界上下文系统装配与端到端验证和测试闭环，形成了一条比较完整的**入门 → 进阶 → 中级 → 高级前置 → 服务化桥接 → 工程化补充 → 高级实战 → 高级收束 → 工程化深化 → 服务联通与恢复 → 服务治理与上下文 → 边界桥接与流式服务 → 本地上下文与协议化事件流 → 房间状态与双向实时通信 → 上传边界与数据库集成 → 批量导入导出与流式报表 → 幂等写入与重复请求治理 → 事务 Outbox 与最终一致性 → 事务 Inbox 与消费端幂等 → Saga 补偿与跨服务工作流 → 读模型投影与事件回放 → CQRS 命令查询职责分离 → 事件溯源 → 进程管理器 → 防腐层 → 有界上下文地图集成**学习路径。

#### `01_HigherOrderFunctions.scala` — 高阶函数
- `map` / `filter` / `reduce` / `foldLeft` 经典操作
- 链式函数调用：`filter → map → reduce`
- 自定义高阶函数 `applyTwice(f, x)`
- 返回函数的函数（闭包工厂）`multiplier(factor)`

#### `02_PatternMatching.scala` — 模式匹配与 ADT
- 基础模式匹配：整数、字符串、元组、列表
- `sealed trait` + `case class` 构建表达式树 ADT
- 递归求值器 `eval()` 与美化打印 `show()`
- `Option` 类型处理空值，`for` 推导式链式安全除法

#### `03_Immutability.scala` — 不可变性与柯里化
- 不可变 `List` / `Map` / `case class` 的“修改”方式
- `copy` 方法创建修改后的副本
- 柯里化函数 `addCurried(a)(b)` 与部分应用
- `andThen` / `compose` 函数组合

#### `04_ErrorHandling.scala` — 函数式错误处理
- 对比 `Option` / `Either` / `Try` 三种常见失败建模方式
- `Either[String, A]` 保留错误信息，适合业务校验
- `for` 推导式把多步解析与除法组合成清晰的数据流
- `Try` + `recover` 展示如何包装可能抛异常的计算

#### `05_RecursionAndTailRec.scala` — 递归与尾递归
- 用递归实现 `sum` 和 `myMap`，体会“用结构描述计算”
- 展示普通递归版 `factorial`
- 通过 `@tailrec` 编写可优化的尾递归 `factorialTailRec`
- 补充 `reverse`、`gcd` 等典型尾递归例子

#### `06_LazyList.scala` — 惰性求值与无限序列
- `lazy val` 演示“第一次访问时才计算，而且只算一次”
- `LazyList` 表达无限自然数序列
- 按需计算的 `filter` / `map` / `take` 数据流
- 惰性定义 Fibonacci 数列，方便对照 Haskell 的同类写法

#### `07_TypeClassStyle.scala` — Type Class 风格
- 手写 `Show[A]` 与 `Eq[A]` 两个小型 Type Class
- 为 `Int`、`String`、`User`、`PaymentStatus` 提供独立实例
- 通过隐式类提供 `show`、`===`、`=!=` 语法增强
- 展示上下文约束 `A: Show`，理解“按能力编程”而不是“按继承编程”

#### `08_FormValidation.scala` — 表单校验
- 用 `Either[String, A]` 为用户名、邮箱、密码、年龄建模校验结果
- 通过 `for` 推导式把多步校验串成一条清晰的数据流
- 展示“预期失败应该进入类型，而不是抛异常”

#### `09_OrderStateMachine.scala` — 订单状态机
- 用 ADT 建模 `Created -> Paid -> Shipped -> Completed / Cancelled`
- 用不可变对象表达状态流转
- 非法操作返回 `Left(error)`，合法操作返回新状态

#### `10_ExprEvaluator.scala` — 表达式求值器
- 扩展表达式树，加入加减乘除、变量、局部绑定 `Let`
- 用递归 + 环境 `Map[String, Double]` 求值
- 用 `Either` 处理除零与未定义变量

#### `11_RecursiveJson.scala` — 递归 JSON 数据结构
- 手写 `Json` ADT：对象、数组、字符串、数字、布尔、空值
- 递归实现 pretty print、节点统计、字段查找、字符串收集
- 体会“同一棵递归结构可以有多种解释方式”

#### `12_ValidatedRegistration.scala` — 累积错误的注册校验
- 用 `Valid` / `Invalid` 建模可以累积多个错误的校验结果
- 对比 `Either` 的“遇错即停”和 `Validated` 的“把多个错误一次收集起来”
- 很适合理解表单校验、配置校验等场景

#### `13_SemigroupAndMonoid.scala` — 可合并抽象
- 手写 `Semigroup` 与 `Monoid`，理解“如何把值合并起来”
- 演示 `Int`、`String`、`List` 等类型的组合规则
- 体会聚合统计、日志拼接、批量汇总背后的通用模型

#### `14_FunctorApplicativeMonad.scala` — 三大上下文抽象
- 用极简 Type Class 形式实现 `Functor`、`Applicative`、`Monad`
- 通过 `Option`、`List` 观察 map / ap / flatMap 的差异
- 建立“在上下文中变换值、组合值、串联值”的系统理解

#### `15_ReaderConfig.scala` — Reader 环境依赖建模
- 把“依赖配置才能运行的逻辑”显式建模成 `Reader[R, A]`
- 通过 `map` / `flatMap` 组合多个依赖环境的步骤
- 理解 FP 风格下的依赖注入与环境传递

#### `16_StateCalculator.scala` — State 状态推进
- 用 `State[S, A]` 建模“给定旧状态，返回新状态和结果”
- 把累加、乘法、重置等操作写成纯函数组合
- 很适合解释器、解析器、状态机、游戏状态等问题

#### `17_IOBasics.scala` — IO：先描述副作用，再执行
- 手写极简 `IO[A]`，把打印、取时间等副作用包成值
- 通过 `map` / `flatMap` 组合多步副作用流程
- 用 `attempt` 把异常显式转成结果值，建立 effect 直觉

#### `18_ResourceDemo.scala` — Resource：资源安全释放
- 手写极简 `Resource[A]`，统一表达 acquire / use / release
- 演示成功和失败两种路径下都能正确释放资源
- 为后续理解 `cats-effect Resource` 打基础

#### `19_ConcurrencyDemo.scala` — 并发组合与 Future
- 对比串行启动和并行启动的耗时差异
- 用 `Future.sequence` 做批量并发处理
- 用 `recover` 处理失败恢复，理解异步计算的组合方式

#### `20_FS2Pipeline.scala` — 流处理直觉
- 用 `Iterator` 模拟“边读边处理”的批处理管道
- 用 `LazyList` 模拟按需生成的无限流
- 先建立 fs2 的核心直觉，再进入真正的流式库

#### `21_Http4sMiniService.scala` — 最小 http4s 风格服务
- 手写 `Request` / `Response` / `HttpRoute` / `HttpApp`
- 展示路由组合、404 回退、日志与请求 ID 中间件
- 先理解 http 服务组织方式，再进入真正的 `http4s`

#### `22_TaglessUserService.scala` — Tagless Final 风格用户服务
- 把 `UserRepo`、`IdGenerator`、`Logger` 抽象成代数接口
- 用最小 `IO` 解释器驱动注册和查询流程
- 理解“业务逻辑依赖能力接口，而不是依赖具体实现”

#### `23_KleisliRequestPipeline.scala` — Kleisli / ReaderT 请求管道
- 用 `R => IO[A]` 的形状串起鉴权、加载用户、权限检查与响应构造
- 理解为什么服务层的 `Reader` 很容易自然长成 `Kleisli`
- 为后续理解 `http4s` / `cats` 中的组合方式打基础

#### `24_RetryBackoff.scala` — Retry / Backoff 重试策略
- 把“失败后如何重试”抽象成独立策略，而不是散落在业务代码里
- 演示最大次数、指数退避、成功与失败两条路径
- 为 timeout、circuit breaker 等工程模式打直觉基础

#### `25_TaglessTestInterpreter.scala` — Tagless Final 测试解释器
- 让同一套业务逻辑跑在测试解释器上，而不连接真实数据库或日志系统
- 用纯状态模拟外部依赖，观察业务规则是否正确
- 进一步理解 Tagless Final 的可测试性价值

#### `26_CatsEffectIOApp.scala` — 真正的 cats-effect IO / Fiber
- 从手写微型 `IO` 过渡到真实 `cats-effect IO`
- 演示串行与并行组合、`Fiber` 后台任务和 `attempt` 错误捕获
- 观察 effect system 在真实库中的基本能力边界

#### `27_CatsEffectResource.scala` — 真正的 cats-effect Resource
- 用 `Resource.make` 真实组织 acquire / use / release
- 观察成功和失败两条路径下都能稳定释放资源
- 为数据库连接池、HTTP 客户端、文件资源等场景建立生产级直觉

#### `28_FS2StreamWorkflow.scala` — 真正的 fs2 Stream 数据管道
- 用真实 `Stream` 实现“读取 → 解析 → 过滤 → 批处理 → 汇总”
- 演示 `evalTap`、`evalMap`、`chunkN` 与按需消费
- 把 20 号 Demo 的流处理直觉推进到真实流式库

#### `29_Http4sRoutes.scala` — 真正的 http4s Routes / Middleware
- 用真实 `HttpRoutes[IO]`、`HttpApp[IO]` 和中间件组织服务
- 不启动服务器，直接把 `Request` 喂给应用观察响应结果
- 把 21 号 Demo 的最小模型迁移到真实 `http4s` 风格

#### `30_TaglessCatsEffect.scala` — 真正的 cats-effect Tagless 解释器
- 让 Tagless Final 业务逻辑跑在真实 `IO` 上
- 用 `Ref` 模拟线程安全的内存仓库与审计日志
- 把 22 号和 25 号 Demo 串起来，形成更接近工程实战的结构

#### `31_CirceJsonCodec.scala` — 真正的 circe JSON 编解码
- 为领域模型定义 `Encoder` / `Decoder`，不再手写 JSON 字符串拼接
- 演示嵌套对象、枚举状态、列表结构的编码与解码
- 观察协议解析失败如何显式返回错误

#### `32_Http4sJsonApi.scala` — 真正的 http4s JSON API
- 用 `jsonOf` / `jsonEncoderOf` 把 circe codec 接到 HTTP 请求响应层
- 演示创建用户、重复冲突、参数校验失败和列表查询
- 理解业务错误如何自然映射到 `400` / `409` 等状态码

#### `33_Http4sClientDemo.scala` — 真正的 http4s Client 调用
- 用 `Client.fromHttpApp` 模拟下游服务，先聚焦 client 端写法本身
- 演示成功响应和错误响应的 JSON 解码
- 理解 `Client.run` 为什么会返回 `Resource`

#### `34_FS2QueueWorker.scala` — 真正的 fs2 Queue 工作流
- 用队列模拟异步任务进入系统，再由多个 worker 并发消费
- 观察任务如何在 worker 之间被分摊处理
- 为消息消费、异步订单处理、后台任务执行建立直觉

#### `35_CatsEffectTimeoutAndCancel.scala` — cats-effect timeout / cancel / finalizer
- 演示 `timeout`、`timeoutTo`、`cancel`、`guaranteeCase`
- 观察任务在成功、失败、取消三种结局下的收尾行为
- 补齐真实 effect system 很关键的一块工程能力

#### `36_CatsEffectDeferredRef.scala` — cats-effect Ref / Deferred 协作
- 用 `Ref` 保存共享状态，用 `Deferred` 表达一次性完成信号
- 演示任务编排中“过程状态”和“最终结果”如何协同工作
- 为异步通知、启动握手、一次性完成事件建立直觉

#### `37_FS2TopicPubSub.scala` — fs2 Topic 发布订阅
- 用 Topic 广播同一条消息给多个订阅者
- 对照 Queue，更清楚地区分“任务分摊”和“消息广播”
- 适合理解事件总线、通知分发、日志广播等场景

#### `38_Http4sBearerAuth.scala` — http4s Bearer Token 鉴权
- 用 Bearer Token 中间件完成认证和权限控制
- 演示未登录、非法 token、权限不足等分支的响应差异
- 进一步贴近真实 API 服务的鉴权入口形态

#### `39_EmberServerClientRoundTrip.scala` — Ember Server / Client 本地联调
- 真正启动本地 Ember 服务器，再用 Ember client 发起请求
- 把 server、client、JSON 协议和 `Resource` 生命周期串起来
- 让“把请求喂给 HttpApp”进一步过渡到真实网络联调

#### `40_MUnitCatsEffectSuite.scala` — munit-cats-effect 服务测试
- 用真实测试框架执行带 `IO` 的服务逻辑断言
- 演示 effect 测试如何自然进入工程化流程
- 为后续更外层的路由测试打基础

#### `41_CatsEffectSemaphore.scala` — cats-effect Semaphore 并发限流
- 用 `Semaphore` 控制同一时刻最多允许多少任务进入关键区
- 演示并发任务如何在 effect system 中受控执行
- 适合理解下游限流、资源保护、连接压力控制等场景

#### `42_FS2ParEvalMap.scala` — fs2 parEvalMap 并行流处理
- 对比 `evalMap`、`parEvalMap`、`parEvalMapUnordered` 的行为差异
- 观察有序并行与无序并行之间的吞吐量和输出顺序取舍
- 把流处理进一步推进到真实的并行消费模型

#### `43_Http4sErrorHandling.scala` — http4s 领域错误映射
- 先返回领域错误，再在 HTTP 边界统一翻译成状态码和 JSON
- 演示参数非法、商品不存在、商品下架等错误路径
- 理解业务规则和协议边界分层的重要性

#### `44_TaglessHttp4sUserModule.scala` — Tagless + http4s 模块装配
- 把 algebra、service、`Ref` 解释器和 http4s routes 组装到同一模块
- 演示业务能力、状态实现和协议层如何自然分层
- 更接近真实函数式服务的组织方式

#### `45_MUnitHttp4sRouteSuite.scala` — http4s 路由集成测试
- 用 `munit-cats-effect` 直接测试 http4s 路由的请求 / 响应行为
- 演示创建资源、参数校验和列表查询的集成断言
- 把测试范围从服务逻辑进一步推进到 HTTP 边界

#### `46_CatsEffectSupervisor.scala` — cats-effect Supervisor 后台任务托管
- 用 `Supervisor` 在作用域里统一托管后台 fiber
- 演示主流程结束后，后台任务如何被自动取消与回收
- 适合理解同步任务、定时刷新、后台轮询等场景的生命周期管理

#### `47_FS2MergeStreams.scala` — fs2 多路流合并
- 把指标流和订单流合并进同一条处理管道
- 观察不同来源的事件如何按到达时机交错输出
- 补齐真实流处理里“汇总多个来源”的常见模式

#### `48_Http4sAuthMiddleware.scala` — http4s AuthMiddleware / AuthedRoutes
- 用 http4s 官方 `AuthMiddleware` 组织认证入口和失败处理
- 用 `AuthedRoutes` 让后续路由直接拿到当前用户上下文
- 比手写鉴权中间件更接近真实项目结构

#### `49_EitherTUserFlow.scala` — EitherT 业务流程编排
- 用 `EitherT` 把领域错误和 `IO` 叠成一条清晰的 for 推导式
- 演示校验、查重、保存等多步流程中的失败短路
- 很适合理解真实服务里的 effectful error handling

#### `50_MUnitAuthMiddlewareSuite.scala` — AuthMiddleware 集成测试
- 用 `munit-cats-effect` 直接测试认证中间件和鉴权路由
- 覆盖缺 token、普通用户、管理员三类关键分支
- 把测试闭环从普通路由推进到鉴权边界

#### `51_CatsEffectRace.scala` — cats-effect race 竞速与自动取消
- 用 `IO.race` 让多个来源并发竞争，谁先返回就采用谁
- 观察输掉的一方如何被 effect system 自动取消
- 适合理解 cache / remote、primary / replica 这类竞速读取场景

#### `52_FS2ErrorRecovery.scala` — fs2 流里的错误恢复
- 对比“错误进入 error channel 后整条流提前结束”和“把错误转成普通值继续处理”
- 演示日志流、批处理、消息流里常见的坏数据容忍策略
- 帮助建立 fs2 错误模型和恢复边界的直觉

#### `53_Http4sClientMiddleware.scala` — http4s client 中间件与 trace 透传
- 在 client 侧统一注入 `X-Request-Id`，并打印请求 / 响应日志
- 展示调用方如何像 server 一样组织 middleware
- 很适合理解跨服务 trace、租户头和统一认证头透传

#### `54_Http4sClientAggregation.scala` — http4s 下游服务聚合编排
- 组合 profile 和 quota 两个下游接口，拼出一份 dashboard 视图
- 用 `EitherT` 把下游失败、业务短路和聚合逻辑组织成清晰流程
- 对应真实项目里的 BFF、网关聚合、用户主页拼装等场景

#### `55_MUnitClientOrchestrationSuite.scala` — 下游聚合编排测试
- 用 `munit-cats-effect` 测试作为调用方时的聚合逻辑
- 验证资料缺失、套餐停用、quota 失败等场景是否正确短路
- 把自动化测试从路由与鉴权边界进一步推进到服务编排边界

#### `56_CatsEffectUncancelable.scala` — uncancelable 与 poll 的取消边界
- 用 `IO.uncancelable` 划出不可取消关键区
- 用 `poll` 保留等待外部确认阶段的可取消能力
- 适合理解支付提交、状态提交、offset 提交这类关键边界

#### `57_FS2InterruptAndSignallingRef.scala` — fs2 停流信号与优雅退出
- 用 `SignallingRef` 把停止条件显式建模出来
- 用 `interruptWhen` 驱动轮询流、心跳流、守护流优雅退出
- 帮助建立长生命周期流的退出直觉

#### `58_Http4sClientRetry.scala` — http4s client 重试治理
- 在真实 client 场景里实现 503 重试、404 直接失败、重试耗尽返回明确错误
- 把次数、退避、失败分类从业务逻辑中抽离出来
- 对应真实下游容错与调用方治理场景

#### `59_Http4sContextRoutes.scala` — ContextMiddleware / ContextRoutes
- 先统一提取 requestId、tenant、userId 等请求上下文
- 再让后续路由直接拿到类型安全的上下文对象
- 适合理解日志关联、租户隔离和用户上下文注入

#### `60_MUnitClientRetrySuite.scala` — client 重试策略测试
- 用 `munit-cats-effect` 测试 503 重试、404 不重试和重试耗尽
- 把“治理逻辑本身”纳入自动化回归
- 让调用方重试策略也具备稳定测试闭环

#### `61_CatsEffectDispatcher.scala` — Dispatcher 桥接旧式回调边界
- 把老 SDK / 事件总线里的 `A => Unit` 回调重新接回 `IO` / `Queue` / `Stream`
- 用 `Dispatcher` 保持副作用依然受 effect system 管理
- 适合理解 callback 世界与函数式运行时的接缝处理

#### `62_FS2GroupWithin.scala` — fs2 groupWithin 批处理窗口
- 按数量上限或时间窗口，把零散事件聚成批次
- 演示批量写库、批量发送、批量刷盘这类常见窗口聚合模式
- 继续补齐真实流处理里的吞吐量优化手段

#### `63_Http4sStreamingApi.scala` — http4s 流式响应 API
- 让路由持续返回 `Stream[IO, Byte]`，而不只是一口气返回完整 JSON
- 用按行输出的 tick 流模拟行情、日志或进度推送
- 帮助建立服务端持续产出数据的直觉

#### `64_EmberStreamingClient.scala` — Ember client 消费真实流式响应
- 真正启动本地 Ember server，再让 Ember client 边接收边解码边处理 body
- 观察流式接口在真实网络下的消费方式
- 对应事件时间线、导出下载、实时日志等场景

#### `65_MUnitStreamingRouteSuite.scala` — 流式路由测试
- 用 `munit-cats-effect` 测试流式响应的 limit 截断、symbol 前缀和 404 分支
- 把流式接口也纳入自动化验证
- 形成从服务端持续产出到测试闭环的完整路径

#### `66_CatsEffectIOLocal.scala` — fiber 本地上下文
- 用 `IOLocal` 保存 traceId，并演示作用域覆盖与自动恢复
- 观察父 fiber 与子 fiber 如何各自持有上下文副本
- 帮助理解“局部上下文”与“共享状态”的边界

#### `67_FS2PullLineDecoder.scala` — fs2 Pull 自定义流变换
- 用 `Pull` 手动拼接跨 chunk 的残缺文本，再切成完整记录
- 观察协议边界为什么常常不能只靠简单 `lines` 处理
- 为 SSE、socket 文本帧、批量导入等场景打基础

#### `68_Http4sServerSentEvents.scala` — SSE 协议化事件流
- 用 `http4s` 返回 `text/event-stream` 响应
- 把 `id`、`event`、`data` 组织成标准 SSE 协议
- 帮助建立浏览器友好型单向推送的直觉

#### `69_EmberSseClient.scala` — 真实 SSE client 消费
- 真正启动本地 Ember server / client，边到达边解析 `data:` 事件
- 观察实时推送在真实网络下的消费方式
- 对应通知流、订单进度、AI 输出流式展示等场景

#### `70_MUnitServerSentEventsSuite.scala` — SSE 路由测试
- 用 `munit-cats-effect` 测试 SSE 的数量截断、data 前缀和 404 分支
- 把协议化事件流纳入自动化验证
- 形成从 SSE 服务到测试闭环的完整路径

#### `71_CatsEffectMapRef.scala` — MapRef 按 key 分片状态
- 用 `MapRef` 管理房间人数这类按 key 拆分的原子状态
- 观察不同房间如何共享同一套状态容器但彼此隔离
- 很适合理解会话配额、房间在线数、租户计数等场景

#### `72_FS2TopicHub.scala` — Topic 房间广播枢纽
- 把广播从“一个订阅者”推进到“一个房间多个订阅者”
- 观察同一条消息如何被多个房间成员同时看到
- 对应聊天室、行情房间、通知中心等广播模型

#### `73_Http4sWebSocketChat.scala` — http4s WebSocket 聊天路由
- 用 `http4s` 建立双向 WebSocket 路由
- 让服务端持续接收文本帧，并把消息再广播回连接端
- 帮助理解 WebSocket 与 SSE 在交互模型上的差异

#### `74_JdkWebSocketBridgeClient.scala` — JDK WebSocket callback 桥接
- 用 JDK WebSocket listener 建立真实客户端连接
- 再用 `Dispatcher` + `Queue` 把 callback 事件接回 `IO`
- 对应 Java SDK、旧式网络客户端、桥接层的常见接缝

#### `75_MUnitWebSocketChatSuite.scala` — WebSocket 路由测试
- 用真实网络连接测试 welcome、回声广播和双客户端互通
- 把双向实时通信也纳入自动化验证
- 形成从房间广播到 WebSocket 测试闭环的完整路径

#### `76_Http4sMultipartUpload.scala` — Multipart 文件上传
- 用 `http4s` 同时解析表单字段和上传文件内容
- 观察 multipart boundary、part 名称和文件体在服务端如何被解码
- 很适合作为对象存储接入、附件上传、批量导入的入门边界 Demo

#### `77_FS2ChunkedFileProcessor.scala` — fs2 固定分块处理大文件流
- 把连续字节流重组为固定大小的处理块
- 让每个分块都能独立做 hash、重试、落盘或分片上传
- 对应大文件上传、断点续传、对象存储和分片导入场景

#### `78_DoobieTransactorResource.scala` — Doobie Transactor 资源与事务回滚
- 用 `Resource` 管理 `Transactor` 生命周期，并把 SQL 放进事务边界里执行
- 观察一次成功更新和一次主键冲突失败时的回滚效果
- 帮助建立“数据库动作”和“资源生命周期”分离的工程直觉

#### `79_DoobieRepositoryTagless.scala` — Tagless Repository + Doobie 解释器
- 先定义仓储代数，再用 Doobie 把它解释成真实 SQL
- 让 service 只依赖能力接口，不直接依赖数据库实现细节
- 帮助把 Tagless Final 从内存解释器推进到真实存储层

#### `80_MUnitRepositoryIntegrationSuite.scala` — Repository 集成测试
- 让 service + repository 跑在真实 H2 数据库上做自动化回归
- 验证去重、过滤、校验和写库行为是否符合预期
- 把数据库边界也纳入可重复执行的测试闭环

#### `81_DoobieStreamingExport.scala` — Doobie 流式导出数据库报表
- 用 `query.stream` 把数据库结果逐行转成报表输出
- 对比一次性 `to[List]` 和流式导出的边界差异
- 很适合理解 CSV 导出、同步任务、批量报表这类场景

#### `82_FS2CsvImportPipeline.scala` — fs2 CSV 导入解析与分批管道
- 从字节流开始解码、切行、校验并分批组织有效记录
- 在数据库写入之前先把坏数据拦下来
- 帮助建立“导入首先是流式解析问题”的直觉

#### `83_Http4sCsvExport.scala` — http4s 流式 CSV 下载接口
- 让路由直接返回 `Stream[IO, Byte]` 形式的 CSV 报表
- 同时带上下载头和筛选查询参数
- 对应运营导出、库存快照、对账文件等常见接口

#### `84_DoobieBatchImportTagless.scala` — Tagless 批量导入模块
- 把批量导入整理成 service + repository interpreter 的模块边界
- 同时处理新增、更新和拒绝记录
- 帮助把 CSV 导入逻辑落到真实 Doobie 仓储

#### `85_MUnitBatchImportExportSuite.scala` — 导入导出一体化集成测试
- 让批量导入 service 和 CSV 导出路由一起跑在真实 H2 上
- 验证更新、过滤、拒绝记录和导出内容是否一致
- 把“写库 + 导出报表”也纳入自动化回归闭环

#### `86_CatsEffectIdempotencyGate.scala` — cats-effect 并发幂等门闩
- 用 `Ref + Deferred` 让同一个 requestId 只由首个 fiber 真正执行
- 后续并发重复请求直接复用 leader 的结果
- 帮助理解“飞行中请求去重”和“长期持久化幂等”的边界差异

#### `87_FS2DedupReservationStream.scala` — fs2 重复请求流去重
- 对 at-least-once 投递下的重复 requestId 做显式过滤
- 去重后再按批次组织唯一命令，准备交给下游处理
- 适合理解消息重放、网络重试和流式去重这类场景

#### `88_Http4sIdempotencyKey.scala` — http4s `Idempotency-Key` 写接口
- 让 POST 写接口识别客户端重试并复用第一次结果
- 对同 key 不同 payload 的错误复用给出明确冲突响应
- 帮助把幂等能力真正落到 HTTP 服务边界

#### `89_DoobieIdempotentReservation.scala` — Doobie 持久化幂等写入
- 把 requestId 和首个结果一起落到数据库里
- 同一个 requestId 重放时直接返回首次结果，不再重复扣减库存
- 帮助建立“HTTP 幂等最终仍要下沉到数据库”的工程直觉

#### `90_MUnitIdempotencyIntegrationSuite.scala` — 幂等写接口集成测试
- 让 `Idempotency-Key` 路由和真实 H2 数据库一起进入自动化回归
- 验证同 key 同体只落库一次、同 key 异体返回冲突、缺少头部返回 400
- 把重复请求治理也纳入可重复执行的测试闭环

#### `91_CatsEffectOutboxCoordinator.scala` — cats-effect Outbox 协调器
- 让业务写入和 outbox 事件一起进入同一份状态
- 模拟后台发布失败后保留事件并等待后续重试
- 帮助建立“先可靠落下，再异步发布”的最小直觉

#### `92_FS2OutboxRetryStream.scala` — fs2 Outbox 重试发布流
- 用定时扫描流表达 pending 事件的后台发布循环
- 失败时只增加 attempts，不从 outbox 删除记录
- 适合理解最终一致性里的重试与状态推进

#### `93_Http4sWebhookOutbox.scala` — http4s Webhook + Outbox 发布边界
- 把 outbox 事件真正推进到 HTTP webhook 边界
- 下游失败时返回非 2xx，并据此决定事件继续保留 pending
- 帮助理解 outbox 为什么总要穿过真实协议边界

#### `94_DoobieTransactionalOutbox.scala` — Doobie 事务 Outbox
- 把订单写入和 outbox 事件插入放进同一个数据库事务
- 发布动作异步进行，成功后标记 published，失败则保留 pending
- 帮助建立“事务写入 + 异步投递”这条真实工程主线

#### `95_MUnitTransactionalOutboxSuite.scala` — 事务 Outbox 集成测试
- 让订单写入、pending 保留和成功发布状态一起进入自动化回归
- 验证同事务创建、失败增量 attempts、重试后标记 published
- 把最终一致性这条链路也纳入可重复执行的测试闭环

#### `96_CatsEffectInboxCoordinator.scala` — cats-effect Inbox 协调器
- 用内存状态讲清楚消费端为什么要对 `eventId` 做幂等保护
- 模拟首次处理失败时不写业务结果，也不提前记录 processed 状态
- 帮助建立“接收端只真正应用一次”的最小直觉

#### `97_FS2InboxRetryConsumer.scala` — fs2 Inbox 重试消费流
- 用定时扫描流表达 pending 投递的后台消费与重试过程
- 区分 `deliveryId` 和 `eventId`，强调重复投递不等于重复业务执行
- 适合理解 at-least-once 投递下的消费端幂等闭环

#### `98_Http4sWebhookInbox.scala` — http4s Webhook Inbox 接收边界
- 把消费端幂等真正推进到 HTTP webhook 接收边界
- 对缺少事件头、重复重放和相同 eventId 不同 payload 做显式处理
- 帮助理解下游服务为什么必须主动防御重复投递

#### `99_DoobieTransactionalInbox.scala` — Doobie 事务 Inbox
- 把 projection 写入和 `processed_event` 记录放进同一个数据库事务
- 模拟事务中途崩溃，验证重试前不会留下半条脏数据
- 帮助建立“消费端幂等最终也要下沉到数据库”的工程直觉

#### `100_MUnitTransactionalInboxSuite.scala` — 事务 Inbox 集成测试
- 让 webhook 接收、事务回滚、重复投递保护和成功重试一起进入自动化回归
- 验证相同 eventId 只落库一次、事务失败后可以安全重试、缺少头部返回 400
- 把消费端最终一致性这条链路也纳入可重复执行的测试闭环

#### `101_CatsEffectSagaCoordinator.scala` — cats-effect Saga 协调器
- 用最小内存模型讲清楚“先预留、再扣款、失败后补偿”这条跨步骤业务流
- 显式展示扣款失败后释放库存，而不是假装远程副作用自动回滚
- 帮助建立 Saga 补偿事务的第一层直觉

#### `102_FS2SagaTimeoutCompensationStream.scala` — fs2 Saga 超时补偿流
- 用定时扫描流表达支付超时、自动补偿和失败后等待下轮重试
- 演示真正收到支付成功回调的 Saga 如何退出超时补偿扫描
- 帮助理解长生命周期工作流为什么适合交给 fs2 后台流管理

#### `103_Http4sSagaWorkflow.scala` — http4s Saga 工作流边界
- 把创建 Saga、支付回调和状态查询推进到真实 HTTP 边界
- 对重复 callbackId 做幂等保护，避免相同回调重复推进状态
- 帮助理解跨服务工作流需要哪些显式接口和协议语义

#### `104_DoobieTransactionalSagaState.scala` — Doobie 事务 Saga 状态
- 把库存预留、补偿释放和 Saga 状态推进一起放进数据库事务
- 模拟“释放库存后、更新状态前崩溃”，验证整次补偿会整体回滚
- 帮助建立 Saga 状态机最终也要下沉到数据库事务边界的工程直觉

#### `105_MUnitSagaIntegrationSuite.scala` — Saga 集成测试
- 让 HTTP 创建、支付回调、补偿回滚和成功完成路径一起进入自动化回归
- 验证拒绝支付后的补偿释放、事务中途失败后的安全重试、成功支付后的完成状态
- 把跨服务工作流这条链路也纳入可重复执行的测试闭环

#### `106_CatsEffectProjectionCoordinator.scala` — cats-effect 读模型投影协调器
- 用最小内存模型讲清楚读模型推进、checkpoint 更新和失败后续跑的核心语义
- 模拟某个事件第一次应用失败，验证 checkpoint 不会错误前移
- 帮助建立查询侧投影为什么必须按 offset 顺序推进的第一层直觉

#### `107_FS2ProjectionReplayStream.scala` — fs2 读模型回放流
- 用后台流表达持续 catch-up、失败后下轮重试和管理员触发 replay
- 演示 replay 不是重放写侧副作用，而是重建查询侧视图
- 帮助理解长生命周期投影任务为什么适合交给 fs2 托管

#### `108_Http4sReadModelQuery.scala` — http4s 读模型查询边界
- 把事件写入、投影 catch-up、状态查看和 replay 管理推进到真实 HTTP 边界
- 显式暴露 lag / checkpoint，让查询侧追赶情况可以被观察
- 帮助理解读模型治理需要哪些管理接口和查询接口

#### `109_DoobieTransactionalProjectionCheckpoint.scala` — Doobie 事务投影 checkpoint
- 把读模型更新和 checkpoint 推进一起放进数据库事务
- 模拟“读模型已更新、checkpoint 尚未写入”前崩溃，验证整次推进会整体回滚
- 帮助建立查询侧投影最终也要下沉到事务边界的工程直觉

#### `110_MUnitProjectionReplaySuite.scala` — 读模型回放集成测试
- 让 HTTP 事件写入、查询侧 catch-up、checkpoint 回滚和 replay 重建一起进入自动化回归
- 验证 lag 清零、失败后安全续跑、replay 后不会重复累计金额
- 把读模型治理这条链路也纳入可重复执行的测试闭环

#### `111_CatsEffectCommandBus.scala` — cats-effect 命令总线
- 用最小内存模型讲清楚命令总线、命令分发、校验失败和业务失败三条路径的最小 CQRS 写侧模型
- 演示 CommandHandler 只返回 DomainEvent，不返回最新读模型，体现写侧职责边界
- 帮助建立"命令总线只路由，Handler 只写，读模型完全分离"的第一层直觉

#### `112_FS2CommandRouterStream.scala` — fs2 命令路由流
- 用流表达批量命令并发处理：失败命令进入 DLQ，成功命令发布 DomainEvent
- 演示 fs2 Stream 让命令处理的成功/失败/积压情况变得可统计和可监控
- 帮助理解批量命令为什么适合用流来托管，而不是逐条手动 foreach

#### `113_Http4sCQRSBoundary.scala` — http4s CQRS 命令/查询双边界
- 把 `/commands/*` 和 `/queries/*` 显式分成两条独立路由，职责一目了然
- 命令接口返回 202 Accepted，不返回最新状态；查询接口永远不产生副作用
- 帮助建立 CQRS 在 HTTP 层的正确语义：写和读不是对称的增删改查

#### `114_DoobieTransactionalCommandWrite.scala` — Doobie 事务命令写入
- 把写模型更新、读模型投影和命令日志三步放进同一个数据库事务
- 模拟读模型投影失败，验证写模型和命令日志都一起回滚，不留脏数据
- 帮助建立 CQRS 写侧最终也需要下沉到事务边界的工程直觉

#### `115_MUnitCQRSIntegrationSuite.scala` — CQRS 集成测试
- 让命令接受/拒绝、查询侧写后读、事务回滚和命令日志审计一起进入自动化回归
- 验证重复创建返回 409、校验失败返回 400、查询不产生副作用、取消后读模型更新
- 把 CQRS 这条写读分离链路也纳入可重复执行的测试闭环

#### `116_CatsEffectEventSourcedAggregate.scala` — cats-effect 事件溯源聚合根
- 用最小内存模型讲清楚"只存事件，状态从 fold 重建"的核心思想
- 演示命令处理返回事件而不是直接修改状态，以及重放同一序列得到相同状态的确定性
- 帮助建立事件溯源 vs 传统 CRUD 的第一层直觉

#### `117_FS2EventAppendStream.scala` — fs2 事件追加流
- 用 fs2 Stream 串行化处理命令：成功追加事件，版本冲突拒绝
- 演示乐观锁检测、Topic 扇出到多个下游订阅者（投影更新器 + Outbox 发布者）
- 帮助理解批量命令为什么需要流来保证顺序，以及为什么 Topic 是事件扇出的自然选择

#### `118_Http4sEventStoreEndpoint.scala` — http4s Event Store 端点
- 把追加命令、加载事件序列和查询聚合根状态推进到真实 HTTP 边界
- 演示状态是从事件 fold 出来的派生视图，而不是直接存储的字段
- 帮助建立 Event Store API 与传统 CRUD API 的语义区别

#### `119_DoobieEventStoreRepository.scala` — Doobie 事件存储仓库
- 把事件追加、版本乐观锁和聚合根重建下沉到真实数据库事务
- 用 `UNIQUE(aggregate_id, version)` 约束在数据库级别防止并发写入同一版本
- 帮助建立事件存储的表设计和乐观锁的数据库实现方式

#### `120_MUnitEventSourcingSuite.scala` — 事件溯源集成测试
- 用 8 个测试覆盖 fold 确定性、HTTP 命令追加、写后读、无效状态转换、时间旅行
- 验证相同事件序列多次重建得到相同结果（确定性）和取前 N 个事件可重建历史时刻状态
- 把事件溯源这条"只追加事件，状态从 fold 派生"的链路纳入可重复执行的测试闭环

#### `121_CatsEffectProcessManager.scala` — cats-effect 进程管理器
- 用最小内存模型演示跨有界上下文的履约工作流：订单→支付→库存→物流→送达
- `nextCommands` 是纯函数：当前状态 + 新事件 → 发出的命令列表
- 帮助建立进程管理器与 Saga 的关键区别：持久化状态机 vs 一次性补偿流程

#### `122_FS2ProcessManagerEventRouter.scala` — fs2 事件路由到进程管理器
- 用 fs2 Stream 把来自多个有界上下文的混合事件流按 orderId 路由到对应进程实例
- 不存在的进程 ID 自动初始化，支持动态创建
- 帮助理解为什么事件路由天然适合用流而不是手动 foreach

#### `123_Http4sProcessManagerBoundary.scala` — http4s 进程管理器边界
- 把事件提交（幂等）、进程状态查询和命令队列查询推进到真实 HTTP 边界
- 相同 eventId 幂等忽略，进程状态只通过事件推进，不可直接修改
- 帮助建立进程管理器的 HTTP API 语义

#### `124_DoobieProcessManagerRepository.scala` — Doobie 进程管理器仓库
- 三步原子事务：幂等检查 + 状态更新 + 命令写入，保证"推进状态"与"发出命令"的强一致
- 命令队列是 Outbox 模式的底层实现，`markPublished` 解耦执行与状态推进
- 帮助建立进程管理器持久化的正确表设计

#### `125_MUnitProcessManagerSuite.scala` — 进程管理器集成测试
- 7 个测试覆盖正常履约、幂等性、补偿路径、支付失败、完整流程、多进程隔离、命令队列
- 验证完整履约路径最终状态为 delivered，不同 orderId 互不干扰
- 把进程管理器这条跨上下文工作流协调链路纳入可重复执行的测试闭环

#### `126_CatsEffectACLTranslator.scala` — cats-effect 防腐层翻译器
- 三个上游系统（支付网关、物流、库存）各有独立的 ACL 翻译器（纯函数）
- 翻译失败返回 `TranslationRejected` 而不是抛异常，让上层决定如何处理
- 帮助建立 ACL 把"外部语言"翻译成"本地语言"的核心直觉

#### `127_FS2ACLTranslationStream.scala` — fs2 上游事件翻译流
- 混合上游消息流经过翻译管道：成功发布领域事件，失败写入 Dead Letter
- 翻译失败不阻塞成功消息，两条路径完全独立
- 帮助理解 ACL 翻译流为什么适合用 fs2 表达（批量处理 + 分流）

#### `128_Http4sACLAdapterEndpoint.scala` — http4s ACL 适配端点
- 上游回调始终返回 200，防止重试风暴；翻译结果通过 `translated` 字段告知
- 相同 messageId 幂等处理，内部路由使用本地领域概念不暴露上游格式
- 帮助建立 ACL HTTP 边界的语义：接收 ≠ 翻译成功

#### `129_DoobieACLTranslationLog.scala` — Doobie ACL 翻译日志
- 原始消息、翻译后领域事件和拒绝记录在同一事务内原子写入
- PRIMARY KEY(message_id) 数据库层幂等，重复消息直接跳过
- 帮助建立 ACL 持久化的正确表设计和翻译审计链路

#### `130_MUnitACLIntegrationSuite.scala` — 防腐层集成测试
- 7 个测试覆盖翻译正确性、200 语义、幂等性、多源事件、纯函数性质
- 验证翻译失败仍返回 200（不触发上游重试），相同 messageId 不重复翻译
- 把防腐层这条"上游隔离 + 内部保护"链路纳入可重复执行的测试闭环

#### `131_CatsEffectContextMapAssembly.scala` — cats-effect 上下文地图装配
- 把 Order、Payment、Inventory、Logistics 四个上下文装配成可运行的完整履约系统
- 模拟正常履约、支付失败、库存不足三条路径，验证系统整体行为
- 帮助建立"多个有界上下文可以用 cats-effect 纯内存运行"的整体直觉

#### `132_FS2CrossContextEventBus.scala` — fs2 跨上下文事件总线
- 用 fs2 Topic 实现广播：PaymentHandler、InventoryHandler、NotificationHandler 各自独立订阅
- 单个集成事件可以链式触发多个上下文（OrderPlaced→PaymentCompleted→InventoryReserved）
- 帮助理解为什么事件总线天然适合跨上下文解耦

#### `133_Http4sContextMapGateway.scala` — http4s 统一网关
- 把多个有界上下文的 HTTP 接口聚合成一套 API（创建订单/确认支付/履约视图/健康检查）
- `GET /orders/{id}/fulfillment` 是跨上下文的聚合视图，一次请求返回完整履约状态
- 帮助理解统一网关如何对外屏蔽内部上下文分布

#### `134_DoobieMultiContextTransaction.scala` — Doobie 多上下文事务
- 用不同表模拟多个上下文，每次状态更新同时写入集成事件（Outbox 模式）
- `LEFT JOIN` 跨上下文拼接聚合视图，适合同数据库报表场景
- 帮助建立"写库 + 发事件"原子性的数据库层实现

#### `135_MUnitContextMapEndToEndSuite.scala` — 端到端集成测试
- 7 个端到端测试：正常履约/履约视图完整性/404/重复支付/健康检查/多订单隔离
- 这是整个 Scala FP Demo 系列（135 个 Demo）的最终收口测试
- 验证整个有界上下文系统端到端行为符合预期

#### `SCALA_FP_ROADMAP.md` — Scala FP 学习路线图
- 梳理你当前所处位置：入门、进阶、中级、高级前置、服务化桥接、工程化补充、高级实战、高级收束、工程化深化、服务联通与恢复、服务治理与上下文、边界桥接与流式服务、本地上下文与协议化事件流、房间状态与双向实时通信、上传边界与数据库集成、批量导入导出与流式报表、幂等写入与重复请求治理、事务 Outbox 与最终一致性、事务 Inbox 与消费端幂等、Saga 补偿与跨服务工作流、读模型投影与事件回放、CQRS 命令查询职责分离、事件溯源、进程管理器、防腐层、有界上下文地图集成
- 给出每个阶段的关键词、目标、推荐 Demo 与建议技术栈
- 适合作为后续继续扩展本仓库的路线参考

---

### 🔴 Akka — Scala 生态的 Actor 并发框架

Akka 是 Scala/JVM 上最成熟的 Actor 框架，与 `cats-effect` 体系是两条并行的 Scala 并发路线。
这组 Demo 重点对比 Akka Typed 和 cats-effect 在**相同问题**上的不同解法。

#### `01_ActorBasics.scala` — Actor 基础
- 消息协议（`sealed trait` + `case class`）、`Behaviors.receive`、`ask` 模式
- 父 Actor 用 `ctx.spawn` 创建子 Actor，自动监督子 Actor 生命周期
- 对比 cats-effect：Actor 是消息驱动，Fiber 是 IO monad 驱动

#### `02_ActorStateAndFSM.scala` — Actor 状态机
- 每个 Behavior 代表一个状态，返回新 Behavior 实现状态转移
- 非法消息被对应状态的 Behavior 忽略，不抛异常
- 对比 Demo `09_OrderStateMachine`：纯函数 vs Actor 状态机

#### `03_ActorStreams.scala` — Akka Streams
- `Source / Flow / Sink` 三件套，背压由 Reactive Streams 规范保证
- `mapAsync(N)` 并行处理（对比 fs2 `parEvalMap`）
- `groupedWithin` 时间窗口批处理（对比 fs2 `groupWithin`）
- `Broadcast` 扇出（对比 fs2 `Topic`）

#### `04_AkkaPersistence.scala` — Akka Persistence（事件溯源）
- `EventSourcedBehavior`：`commandHandler`（命令→事件）+ `eventHandler`（事件→状态）
- 事件自动持久化到 Journal，重启后自动重放恢复状态
- 对比 Demo `116_CatsEffectEventSourcedAggregate`：框架自动 vs 手动管理

#### `05_AkkaProjection.scala` — Akka Projection（CQRS 读模型投影）
- 模拟 Akka Projection 的 offset checkpoint 推进、at-least-once 语义
- 幂等处理：相同 offset 只处理一次
- 对比 Demo `106-110`：框架管理 checkpoint vs 手动实现

#### `06_SupervisionStrategy.scala` — 监督策略（Let it crash）
- Restart vs Resume 两种策略：子 Actor 崩溃后的不同恢复行为
- Restart 重启后状态清零，Resume 忽略异常状态保留
- 对比 cats-effect `Supervisor`：Akka 用"让它崩溃"哲学，副作用处理与业务逻辑分离

#### `07_ClusterSharding.scala` — Cluster Sharding（分布式 Actor）
- 按 `entityId` 路由消息到正确的 Actor 实例（每个用户/订单一个 Actor）
- `ClusterSharding.init` 注册实体，`entityRefFor` 获取引用
- 对比 `MapRef`：MapRef 是单进程内的 key→value，Cluster Sharding 是跨节点的分布式状态

#### `08_AkkaHttp.scala` — Akka HTTP（Directive DSL）
- `path / get / post / entity / complete` 四大 Directive 组合路由
- spray-json 序列化（Akka HTTP 默认 JSON 库，对比 http4s 的 circe）
- 对比 http4s：DSL 嵌套风格 vs 纯函数式 `Request => IO[Response]` 风格
- **运行方式**：`scala-cli run scala/akka/08_AkkaHttp.scala`（启动 HTTP 服务器，Ctrl+C 停止）

#### `09_ActorTestKit.scala` — Actor 测试（BehaviorTestKit + ActorTestKit）
- `BehaviorTestKit`：同步测试，无 ActorSystem，用 `TestInbox` 收消息
- `ActorTestKit`：异步测试，真实 ActorSystem + `TestProbe`，`expectMessage` 带超时
- 对比 `munit-cats-effect`：Actor 测试用 probe 接收断言 vs IO 直接断言结果

#### `10_ActorTimers.scala` — 定时器（Timers）
- `startSingleTimer`：延迟一次消息（倒计时链式触发）
- `startTimerWithFixedDelay`：固定延迟周期（心跳）
- `timers.cancel(key)`：取消定时器，支持动态调整间隔
- 对比 `fs2 Stream.awakeEvery`：Actor 定时消息 vs 流式周期 tick

#### `11_ActorPubSub.scala` — EventStream 发布订阅
- `system.eventStream.subscribe/publish`：全局事件总线，订阅父类可收到所有子类消息
- 三种订阅者：全量订阅 / 按子类型过滤 / DeadLetter 监听
- 对比 `fs2 Topic`：背压控制 vs 无背压 Actor 邮箱

#### `12_ActorStash.scala` — Stash 消息暂存
- `Behaviors.withStash(capacity)` 暂存"现在还不能处理"的消息
- `buffer.stash(msg)` 暂存，`buffer.unstashAll(nextBehavior)` 就绪后重放
- 对比 `cats-effect Deferred`：Deferred 等一个值，Stash 等状态就绪后重放多条消息

#### `13_ActorRouter.scala` — Router 负载均衡
- 手动实现 RoundRobin Router：轮询分发给 N 个 Worker
- BroadcastPool：每个 Worker 都处理同一条消息
- 对比 `fs2 parEvalMap(N)`：Actor 消息分发 vs 流式并行处理

#### `14_AkkaStreamsAdvanced.scala` — Akka Streams 高级操作符
- `throttle`（限速）、`conflate`（背压合并）、`zip`（配对）、`merge`（混合）
- `alsoTo`（流复制）、`groupBy`（按条件分流）
- 与 fs2 操作符一一对比

#### `15_PersistenceQuery.scala` — Persistence Query（文档说明）
- 说明 Akka Persistence Query 的核心接口：`eventsByPersistenceId` / `eventsByTag`
- 对比 Demo `107/119`：框架自动管理 offset vs 手动轮询数据库
- 说明如何把手工投影替换成 Akka Projection 框架

#### `16_ActorRequestResponse.scala` — 请求-响应进阶
- ask 超时捕获（返回 `AskTimeoutException` 而不是阻塞）
- 并发聚合两个 Actor 的响应（对比 `IO.both`）
- 对比 `cats-effect parTraverse / IO.parSequenceN`

#### `17_AkkaStreamsKillSwitch.scala` — KillSwitch 流控制
- `UniqueKillSwitch`：独立控制单条流停止（shutdown / abort）
- `SharedKillSwitch`：一个开关同时关闭多条流
- 对比 `fs2 SignallingRef`（Demo 57）：信号 IO vs 对象引用

#### `18_ActorReceptionist.scala` — Receptionist 服务发现
- `ServiceKey[T]` 定义服务类型，`Receptionist.Register` 注册，Actor 停止自动注销
- `Receptionist.Subscribe` 订阅变更，`Receptionist.Find` 一次性查询
- 对比 `cats-effect Ref[IO, Map[...]]` 手动维护服务注册表

```bash
# 运行 Akka Demo（全部）
scala-cli run scala/akka/01_ActorBasics.scala
scala-cli run scala/akka/02_ActorStateAndFSM.scala
scala-cli run scala/akka/03_ActorStreams.scala
scala-cli run scala/akka/04_AkkaPersistence.scala
scala-cli run scala/akka/05_AkkaProjection.scala
scala-cli run scala/akka/06_SupervisionStrategy.scala
scala-cli run scala/akka/08_AkkaHttp.scala       # 启动 HTTP 服务器（阻塞，Ctrl+C 停止）
scala-cli run scala/akka/09_ActorTestKit.scala
scala-cli run scala/akka/10_ActorTimers.scala
scala-cli run scala/akka/11_ActorPubSub.scala
scala-cli run scala/akka/12_ActorStash.scala
scala-cli run scala/akka/13_ActorRouter.scala
scala-cli run scala/akka/14_AkkaStreamsAdvanced.scala
scala-cli run scala/akka/15_PersistenceQuery.scala
scala-cli run scala/akka/16_ActorRequestResponse.scala
scala-cli run scala/akka/17_AkkaStreamsKillSwitch.scala
scala-cli run scala/akka/18_ActorReceptionist.scala
# 集群相关（需要集群环境）
scala-cli run scala/akka/07_ClusterSharding.scala
```

---

### 🟢 Erlang — 并发世界的函数式先驱

Erlang 为电信系统而生，其轻量级进程和消息传递是 Actor 模型的经典实现。当前这组 Demo 已经从入门语法走到工程化 OTP 并延伸到运维与生态：**模式匹配 / 高阶 → 原生 Actor → OTP gen_server / gen_statem → Supervisor & Let-it-crash → ETS / DETS / disk_log / Mnesia 数据层 → 分布式节点 → 属性测试 / Common Test 集成测试 → 二进制协议解析 → 热升级 → gen_tcp / TLS 服务器 → 邮箱语义 → link/monitor/trap_exit → OTP logger → application & release 打包 → BEAM 运行时自省 → NIF vs Port → benchmark & profile → rebar3 工程骨架 → Elixir ↔ Erlang 互操作 → gen_event 事件总线 → erlang:trace / dbg 在线追踪**，一条路径把 BEAM 的八大招牌能力（轻量进程、容错、分布式、二进制处理、热升级、运维可观测、工程化发布、在线诊断）全部覆盖。

#### `01_pattern_matching.erl` — 模式匹配与递归
- 变量绑定即模式匹配、列表解构 `[H | T]`
- 阶乘 `factorial/1`、斐波那契 `fibonacci/1`
- 尾递归优化的列表求和 `list_sum/1`
- **快速排序** —— Erlang 最经典的三行代码实现
- 守卫表达式 (Guards) 实现温度分级

#### `02_higher_order.erl` — 高阶函数与列表推导
- `lists:map` / `lists:filter` / `lists:foldl`
- **列表推导**：平方、笛卡尔积、勾股数
- 自定义 `my_map/2` / `my_filter/2` / `my_foldl/3` 递归实现
- 函数组合 `compose(F, G)`

#### `03_actor_model.erl` — Actor 模型
- **计数器 Actor**：通过递归保持状态（无可变变量）
- **计算器 Actor**：进程间请求-响应模式
- **Ping-Pong**：两个进程互相收发消息
- 展示 `spawn` / `!`(发送) / `receive` 三大原语

#### `04_gen_server_counter.erl` — OTP gen_server 入门
- 用 `-behaviour(gen_server)` 把 Actor 套路标准化
- 五个回调：`init/1` / `handle_call/3` / `handle_cast/2` / `handle_info/2` / `terminate/2`
- 同步 `call` vs 异步 `cast` 的语义差别
- 对标：Scala cats-effect `Ref` / Haskell STM `MVar` / Rust `Arc<Mutex>`

#### `05_supervisor_tree.erl` — Supervisor 与 Let-it-crash
- 业务 worker 不写 try/catch, 出错就 `exit`
- Supervisor 用声明式 child spec 按策略自动重启
- 三种重启策略：`one_for_one` / `one_for_all` / `rest_for_one`
- 展示：进程主动崩溃、`badarith` 除零崩溃都会被自动恢复

#### `06_ets_and_state.erl` — ETS：纯 FP 在 BEAM 上的边界
- Erlang 进程里是纯不可变 —— 多进程共享状态得靠 ETS (Erlang Term Storage)
- 插入 / 查找 / 匹配 / `fold` 操作
- 从"每次新建一个不可变 map"到"表内原子更新"的权衡
- 对标：Scala `Ref` / Haskell `IORef` / Rust `DashMap`

#### `07_distributed_nodes.erl` — 分布式节点：位置透明的 spawn
- `spawn/2,4` 可以在远程节点上起进程
- `rpc:call/4` / `rpc:multicall/4` 跨节点同步调用
- 全局进程注册 `global:register_name/2` + 消息传递
- 和单机 Actor 代码几乎一样 —— BEAM 原生分布式的魅力

#### `08_property_testing_proper.erl` — 属性测试（自制迷你 PropEr）
- 生成器 + 性质 + 自动 shrinking 的最小实现
- 演示 4 条性质：`reverse.reverse == id`、排序保序、排序保长、故意 buggy 的交换律
- shrinking 把反例自动缩小到最小
- 生产版把 `check/3` 换成 `proper:quickcheck/1` 即可；对标 QuickCheck / ScalaCheck

#### `09_gen_statem_order_fsm.erl` — gen_statem 状态机的正确姿势
- 订单 FSM：`created → paid → shipped → delivered`，或任意一步 `cancelled`
- `state_functions` 回调模式：每个状态一个函数，天然防止"非法状态下的非法动作"
- 内置 `state_timeout`：2 秒不付款自动取消
- 对标：Scala 09 号 `OrderStateMachine` / Akka FSM / Rust typestate

#### `10_binary_pattern_matching.erl` — 二进制模式匹配（BEAM 杀手特性）
- `<<...>>` 语法：把协议字段结构直接写在模式里
- **TCP header 20 字节一行解析**：端口 / seq / ack / flags / window 全拆干净
- `utf8` 类型：变长 UTF-8 码点直接模式匹配
- TLV (type-length-value) 流解析 + bitmap 取位
- 对标：Rust nom / Haskell binary / Scala scodec

#### `11_mnesia_transactional_store.erl` — Mnesia 分布式事务数据库
- BEAM 自带的 ACID 数据库，单节点 `ram_copies` 到多节点复制一键切换
- `mnesia:transaction/1` + `mnesia:abort/1`：账户转账余额不够自动回滚
- 二级索引 (`{index, [owner]}`) + **QLC** 查询列表推导
- 对标：Scala 99 号 Doobie 事务 Inbox / Haskell STM / Rust sqlx tx

#### `12_hot_code_upgrade.erl` — 热代码升级（BEAM 独有能力）
- 同一个 Pid 全程不重启，状态保留，业务逻辑三次换版本（v1→v2→v3）
- 关键机制：`?MODULE:loop/1` **完全限定调用**，让 BEAM 下轮循环重新 dispatch
- 真实 OTP：`appup` + `relup` + `release_handler`，机制本质相同
- 这是 JVM / CLR / Go 都做不到的 in-place 升级

#### `13_gen_tcp_echo_server.erl` — gen_tcp Echo 服务器
- BEAM 并发招牌：**一个 acceptor + 每连接一个 handler 进程**
- `gen_tcp:listen / accept`，`controlling_process/2` 把 socket 所有权交给子进程
- 两个客户端并发发数据，互不影响，交织回显
- 这就是 cowboy / ranch 的内核；对标：Rust `tokio::spawn + TcpListener`

#### `14_selective_receive_mailbox.erl` — 选择性 receive 与邮箱语义
- 邮箱是 FIFO，但 `receive` 按模式扫描，能跳过不匹配的消息
- 不匹配消息留在邮箱 —— 邮箱积压是 Erlang 生产故障常见源头
- `after 0` 抽干邮箱；`after N` 做超时；`after infinity` 永远阻塞
- `make_ref()` 做请求-响应关联，避免串线（新手高频 bug）

#### `15_link_monitor_trap_exit.erl` — link / monitor / trap_exit 三兄弟
- `link`：双向、默认致命，被连者挂我也挂
- `monitor`：单向、不致命，死亡变成 `{'DOWN', Ref, ...}` 消息
- `trap_exit`：把 `link` 的致命死亡变成普通 `{'EXIT', Pid, Reason}` 消息
- supervisor 的底层就是 `link + trap_exit`；应用层监听"别人死了"一般用 monitor

#### `16_logger_and_formatter.erl` — OTP logger：生产日志框架
- 8 级分级 + 结构化日志（`logger:info(#{event => ..., uid => ...})`）
- `logger_formatter` 模板定制：`[LEVEL TIME PID] MSG`
- 运行时 `set_primary_config(level, debug)` 调全局级别
- `set_module_level/2` 按模块精细调级 —— 线上调试不停服
- 对标：Scala log4cats / Rust tracing / Haskell katip

#### `17_common_test_ct.erl` — Common Test 集成测试骨架
- OTP 官方集成测试框架（`rebar3 ct`），和 Demo 08 的 PropEr 形成互补
- 标准回调：`all/0` / `groups/0` / `init_per_suite` / `init_per_testcase` / ...
- groups 支持 `sequence` / `parallel`，天然利用 BEAM 并发
- setup / teardown 钩子保证用例间相互隔离
- 和单元测试 + 属性测试一起构成"三层测试金字塔"

#### `18_application_and_release.erl` — application & release 打包
- OTP 三件套最后一块：**application behaviour**
- `*.app.src` 元数据 + `application:start/2` + 顶层 supervisor + worker
- `application:get_env/3` 读 sys.config 中的运行时配置
- `rebar3 release` 打成包含 ERTS 的可发布 tar
- release 是生产发布的标准形态，自带热升级能力

#### `19_recon_observer_introspect.erl` — BEAM 运行时自省
- 线上排障核心：`erlang:process_info/2` / `system_info` / `ets:info`
- 按 `message_queue_len` / `reductions` / `memory` 做 top-N（recon 同款思路）
- 邮箱堆积 => 典型反压失败或慢消费者信号
- reductions 是 BEAM 自己的"虚拟 CPU 时间片"，排 CPU 热点用它
- 真实生产等价 `recon:proc_count/2`、`recon:info/1`

#### `20_nif_and_port.erl` — NIF vs Port
- BEAM 调外部世界的两条路：**NIF**（同进程极快，崩溃砸 VM） vs **Port**（独立进程安全）
- `crypto:hash/2`、`term_to_binary/1` 就是现成 NIF，感受原生速度
- `open_port({spawn, "..."})` 拉真实 OS 进程，通过 `{Port, {data, _}}` 消息交互
- NIF 注意 dirty scheduler / `enif_schedule_nif`，否则阻塞调度器
- 决策树：性能 + 稳定 C 库走 NIF；不稳定/第三方走 Port

#### `21_dets_and_disc_log.erl` — DETS 与 disk_log
- **DETS**：磁盘版 ETS，单机 KV 持久化，接口和 ETS 几乎一致
- **disk_log**：OTP 内建的 append-only 日志，支持 wrap（环形 segment）
- 关闭+重开验证持久化；`chunk/2` 流式读取
- 轻量持久化选型：ETS（内存）→ DETS（单机磁盘）→ Mnesia（事务+集群）→ 外部 DB

#### `22_ssl_and_tls.erl` — ssl 模块（TLS）
- API 和 `gen_tcp` 对称：`ssl:listen` / `transport_accept` / `handshake` / `send` / `recv`
- 自签证书演示握手全流程，`connection_information/2` 看选到的 cipher 和协议
- 生产配置：必须 `verify_peer` + `cacertfile`，别用 `verify_none`
- 可以把 Demo 13 的明文 echo 平滑换成 TLS echo

#### `23_bench_and_profile.erl` — benchmark & profile
- `timer:tc/1,3` —— 最简单的耗时测量
- `erlang:statistics(reductions|garbage_collection|run_queue)` —— 运行时统计
- **eprof**：函数级 wall-clock 时间占比（轻量）
- **fprof**：带 call graph 和 own/accumulated time（重量）
- OTP 27+ 的 `tprof` 统一 profile 入口

#### `24_rebar3_project_skeleton.erl` — rebar3 工程骨架速查
- 标准目录树：`src/` / `config/` / `include/` / `priv/` / `test/` / `_build/`
- `rebar.config` 示例：deps / relx / profiles / dialyzer
- `src/myapp.app.src` / `config/sys.config` / `config/vm.args` 三份核心配置
- `rebar3 compile` / `shell` / `ct` / `eunit` / `proper` / `dialyzer` / `release` / `relup`
- 对标：Rust cargo / Scala sbt / Haskell cabal

#### `25_elixir_vs_erlang.erl` — Elixir ↔ Erlang 互操作
- BEAM 家族（Erlang / Elixir / Gleam / LFE）编成同一种 `.beam`，100% 互通
- 同一个计数器 `gen_server` 的 Erlang 和 Elixir 并排对照
- 调用约定：Elixir 模块 `Foo.Bar` 在 Erlang 眼里是原子 `'Elixir.Foo.Bar'`
- Elixir 调 Erlang：`:lists.sum([1,2,3])`、`:crypto.hash(:sha256, data)`
- GenServer / Supervisor / Registry 都是 Elixir 对 OTP 的语法糖

#### `26_gen_event_pubsub.erl` — gen_event 事件总线
- OTP 四大 behaviour 中的最后一块：一对多事件分发（pub/sub 雏形）
- `add_handler` / `delete_handler` / `notify` / `call` 核心 API
- 每个 handler 有独立状态，manager 负责逐个派发
- 早期 OTP logger 就是基于 gen_event 做的多 backend
- 高吞吐 + 背压场景改用 GenStage / Broadway / Phoenix.PubSub

#### `27_erl_trace_and_dbg.erl` — erlang:trace 与 dbg 在线追踪
- BEAM 独家生产力：**不改代码 / 不重启**给任意进程/函数打 tracer
- `erlang:trace/3` 原始 API：追消息收发（`send` / `receive` 事件）
- `dbg:tpl(Mod, Fun, Arity, MS)` + tracer fun：追函数调用，含返回值
- **match spec** 按入参条件过滤：`[{'>', '$1', 5}]` 只在 X > 5 时触发
- 生产上配合 `recon_trace:calls/2` 使用，自带速率限制，用完 `dbg:stop_clear()`

---

### 🟣 Haskell — 纯函数式语言的殿堂

Haskell 是学术界和工业界公认的纯函数式语言标杆，所有函数都是纯函数，默认惰性求值。当前这组 Demo 已经覆盖了一条完整的知识圈：**纯函数与惰性求值 → 类型类与 Monad → 柯里化与组合 → IO 与副作用建模 → Reader / State → STM 并发 → ADT 与 newtype → Monad Transformer → Lens / Optics → 手写 Parser 组合子 → Free Monad DSL → QuickCheck 性质测试 → Arrow / Profunctor → Foldable / Traversable → GADT / Phantom / DataKinds / TypeFamilies → 手写最小流式管道 → Lambda 演算 / fix / Church encoding → Alternative / MonadPlus → 异常 & bracket & 手写 async → Generics / DerivingVia → Effect System（MTL + Free Eff）→ 状态机属性测试 & 工程最佳实践**，并附带 `HASKELL_FP_ROADMAP.md` 学习路线图。整条路径从**数学底座 → 入门 → Monad 抽象 → 副作用 & 并发 → 类型系统 → 抽象终点 → 工程化实战 → 最佳实践收口**一气呵成。

#### `01_PureAndLazy.hs` — 纯函数与惰性求值
- 纯函数示例：温度转换、BMI 计算器
- **无限列表**：自然数 `[0..]`、偶数 `[0, 2..]`
- 斐波那契数列的惰性定义 `fibs = 0 : 1 : zipWith (+) fibs (tail fibs)`
- **埃拉托斯特尼素数筛** —— 无限素数列表
- 自定义 `myMap` / `myFilter` / `myFoldr`

#### `02_TypeClassAndMonad.hs` — 类型类与 Monad
- 自定义类型类 `Describable`（为 Shape / Int / Bool 实现）
- **Maybe Monad**：`safeDivide` / `safeSqrt` / `safeLog` 链式安全计算
- `do` 表示法：Monad 的优雅语法糖
- **Either Monad**：带错误信息的验证
- 自定义 `Tree` 类型实现 `Functor`（`fmap` 操作树）

#### `03_CurryAndCompose.hs` — 柯里化与函数组合
- Haskell 天然柯里化：`add 5` 即部分应用
- 运算符切片 (Section)：`(> 0)` / `(* 2)` / `(/ 2)`
- **函数组合 (.)**：`titleCase = unwords . map capitalize . words`
- 字母频率统计 —— 纯函数组合管道
- **Point-free style**：无参风格编程
- Caesar 密码的函数式实现

#### `04_IOAndSideEffects.hs` — IO Monad 与副作用建模
- 用 `IO` 把副作用显式变成一个"将来要执行的动作值"
- 通过 `do` 语法、`>>=`、`>>` 组合多个 IO 动作
- 对比纯函数和 `IO Int`：`IO` 不是"带副作用的值"，而是"描述副作用的程序"
- 用 `IORef` 建模局部可变状态，体会与纯 State 的差异
- `readFile` / `writeFile` 等常见 IO 动作的组合方式

#### `05_StateAndReader.hs` — State / Reader Monad
- `Reader r a`：把"依赖环境"的计算抽象出来，替代到处传参
- `State s a`：给定旧状态，返回新状态和结果
- 用 `do` 记法写出可读性很高的状态推进 / 环境依赖管道
- 为什么 `State` 看似有"可变"，却依然是纯函数式
- 对照 Demo `15_ReaderConfig` / `16_StateCalculator`（Scala），体会同一套抽象在不同语言的写法

#### `06_ConcurrencySTM.hs` — STM 软件事务内存
- `TVar` 表示可事务读写的共享变量，`STM` 表示事务块
- `atomically` 把 STM 事务原子提交到真实世界
- `retry` / `orElse`：天然的"条件等待"与"事务选择"
- 演示银行账户转账、生产者-消费者、读写模型等经典并发例子
- 对比基于锁 / Actor 的并发模型，体会 STM 的组合性优势

#### `07_TypesAndADT.hs` — 代数数据类型与 newtype
- `data` / `newtype` / `type` 的差异与使用场景
- 求和类型（`|`）与积类型（record 语法）
- 递归 ADT：表达式树、JSON、自定义 List
- 用 `newtype` 区分同结构不同语义的类型（比如 Meters vs Feet）
- `deriving (Show, Eq, Ord, Functor, ...)`：自动派生的能力边界

#### `08_MonadTransformers.hs` — Monad Transformer 叠加多种效果
- 单个 Monad 不够用时，用 `ExceptT` / `StateT` / `ReaderT` 堆叠
- 用 `lift` 把"下层"的动作抬到外层
- 实战：同时需要"可能失败 + 可变状态 + 只读环境"的业务流程
- 为什么真实工程常常收敛到 `ReaderT Env IO` 这种标准 stack
- 对比 Demo `49_EitherTUserFlow`（Scala），体会同一思路的两种写法

#### `09_LensAndOptics.hs` — Lens / Prism / Optics 基础
- Lens 的核心形状：`view` / `set` / `over`，聚焦嵌套结构的一个字段
- 手写最小 Lens，理解 "first-class getter + setter" 的直觉
- Prism 用于"可能存在"的部分（sum type 的一个分支）
- 函数式更新深层嵌套字段，不再手工解构重建
- 连接 Profunctor 与 van Laarhoven 表示的直觉

#### `10_ParserCombinators.hs` — 手写解析器组合子
- Parser 本质就是一个 `String -> Maybe (a, String)`
- 基础原语：`satisfy` / `char` / `anyChar` / `eof`
- 组合子：`<|>`、`many`、`some`、`sepBy`、`between`
- 用组合子搭出一个完整的 JSON 解析器（递归下降）
- 理解 `parsec` / `megaparsec` / `attoparsec` 的核心抽象

#### `11_FreeMonadsAndDSL.hs` — Free Monad 与 DSL 解释器
- 用 `Free f a` 把"命令序列"变成纯数据结构
- 业务逻辑只依赖 DSL 指令，解释器决定"最终语义"
- 同一段程序可以被"真实 IO 解释器"和"测试解释器"分别解释
- 对比 Demo `22_TaglessUserService` / `25_TaglessTestInterpreter`（Scala）
- 理解 Free Monad 与 Tagless Final 的取舍

#### `12_QuickCheck.hs` — 基于性质的随机测试
- 不再写"输入 X 预期输出 Y"，而是写"对任意输入都成立的性质"
- `reverse . reverse == id`、`sort` 是幂等且保持元素集合
- `Arbitrary` 类型类：如何为自定义数据生成随机样本
- shrink：当失败样本复杂时，自动简化成最小反例
- 为什么 QuickCheck 能在几十行测试里覆盖成千上万种输入组合

#### `13_ArrowAndProfunctor.hs` — Arrow 与 Profunctor
- Arrow 是比 Monad 更抽象的计算模型，可表示静态数据流
- 组合子：`(>>>)`、`(***)`、`(&&&)`、`(|||)`
- 用 Arrow 搭建数据验证管道与信号处理流水线
- Profunctor：`dimap` 同时处理输入端 `lmap` 和输出端 `rmap`
- 理解现代 Lens 为什么可以写成 `p a b -> p s t`

#### `14_FoldableTraversable.hs` — Foldable / Traversable
- `Foldable`：任何结构都能"用 Monoid 压成一个值"
- 用 `foldMap` 一次遍历同时算 sum、count、avg、all、any
- `Traversable`：保持结构不变的前提下，把 effect 从内部抽到外部
- 经典例子：`[Maybe a] -> Maybe [a]`、`traverse safeDiv`
- 自定义 `Tree` 的 `Foldable` / `Traversable` 实例，并用"手写 State"给每个节点编号
- 小结：`mapM` / `forM` / `sequence` 本质上都是 `traverse`

#### `15_TypesAdvanced.hs` — GADT / Phantom / DataKinds / TypeFamilies
- GADT：给每个构造器"单独指定"返回类型，eval 不再需要 Either
- Phantom Type：用类型参数做标签，区分"未校验" vs "已校验"的 Email
- DataKinds + GADT：类型级订单状态机——非法转移直接编译期拒绝
- TypeFamily：在类型层面写函数，让返回类型随输入类型变化
- Type-Level Nat + `Vec n a`：长度带到类型里，`vhead VNil` 编译期报错
- 核心哲学：能在类型层表达的约束，就别放到运行时

#### `16_StreamingPipeline.hs` — 手写最小流式管道（对照 fs2/conduit）
- 从零定义 `Stream m a`：`Done | Yield a next`
- 基本组合子：`smap` / `sfilter` / `stake` / `chunkN` / `evalTap`
- 消费方式：`drain` / `toList` / `foldS`（对应 fs2 的 compile.drain / toList / fold）
- 实战管道：清洗 + 转换 + 批处理 + 汇总
- 背压直觉：下游不拉，上游就不会真正执行 effect
- 对比原生列表惰性流：纯场景直接用 `[]`，带 IO 再上 `Stream m a`

#### `17_LambdaCalculusAndFix.hs` — Lambda 演算 / fix / Church encoding
- **Church Encoding**：只用函数编码 `true` / `false` / 自然数 / pair
- **fix** 与 **Y 组合子**：从本质上理解“匿名递归”，看透 `let rec` 是怎么实现的
- **foldr 是 List 的 eliminator**：map / filter / foldl 都能用 foldr 派生出来
- **catamorphism / anamorphism / hylomorphism**："消费与生产" 的对偶与融合
- 把 `point-free` 风格还原成 eta-reduction 的糖

#### `18_AlternativeAndMonadPlus.hs` — 失败 / 选择 / 非确定计算
- `Alternative (<|>, empty)` 是“失败与选择”的统一抽象
- `Maybe` 上的 `<|>`：多个字典查找第一个命中
- `List` 作为非确定计算：毕达哥拉斯三元组、N 皇后
- 手写最小 Parser：`<|>` / `some` / `many` 全部来自 Alternative
- `guard` 本质 = 滤波器，写出贴近数学语言的素数筛

#### `19_ExceptionsAndConcurrency.hs` — 异常 / 资源 / 并发
- **异常分两类**：业务失败用 `Either`；外部意外用 `Exception`
- **bracket = Haskell 版 Resource**：acquire / use / release 三段式，永远安全
- **MVar 本质 = 容量 1 的盒子**：做互斥锁 / 一次性信号 / 通道都行
- **手写最小 async**：`forkIO` + `MVar` 在 20 行内实现并发汇合
- **finally / onException**：精细控制清理时机

#### `20_GenericsAndDerivingVia.hs` — 类型驱动的工程技巧
- **4 种 deriving 策略**：stock / newtype / anyclass / via
- **GHC.Generics**：把类型结构本身变成可编程对象，一次写完对所有派生类型通用
- **DerivingVia**：代码复用核武器，同一 newtype 选择不同语义（Sum vs Max）
- **Identity / Const / Compose** 三剑客：Lens / foldMap / generic JSON 的幕后英雄
- 工程套路：`newtype AppM = ... deriving newtype (Monad, MonadReader, MonadIO)`

#### `21_EffectSystemPatterns.hs` — Effect System 的两种主流写法
- **MTL 风格**：用 `class MonadLogger m` / `class MonadUserStore m` 抽象能力
- 同一段业务在 `ProdM`（真实 IO）和 `TestM`（纯State）上自由切换
- **Free Eff 风格**：先把程序构造成数据，再由解释器决定语义
- 一段 `Program` 配三种解释器：IO / Pure / Trace
- 对标：Tagless Final ≈ MTL；Algebra + Interpreter ≈ Free

#### `22_FPBestPracticesAndStateMachineTests.hs` — 工程收口
- **状态机属性测试**：驱动“纯模型 vs 真实 SUT”随机命令序列对照，模拟 hedgehog / qsm
- 支持一键注入 bug，现场看到测试自动发现反例的效果
- **工程最佳实践清单**：项目结构 / 语言扩展 / 运行时架构 / 性能并发 / 测试金字塔 / 反模式
- **系列总收口**：整张 01–22 的 Haskell FP 全图一页读完

#### `HASKELL_FP_ROADMAP.md` — Haskell FP 学习路线图
- 汇总 Haskell 侧从入门到进阶的推荐顺序
- 指出每个阶段想要拿下的"核心直觉"
- 作为 Demo 之间的"地图"，避免只见树木不见森林

---

### 🟠 Rust — 系统编程中的函数式优雅

Rust 将函数式特性融入系统级语言，迭代器是零成本抽象的典范。当前这组 Demo 已经从入门语法走到工程化生态：**迭代器 / 闭包 / 枚举 / Trait → Result & 所有权 × FP → 迭代器内幕 & 智能指针 → async/Stream & typestate → 零依赖 Parser Combinator → Trait Object vs impl Trait → 生命周期进阶 → Send/Sync × 内部可变 → Channel & 数据并行 → 手写 serde → thiserror & anyhow → tracing 结构化日志 → axum 风格路由 → tokio 同步原语 → macro_rules! → proc macro 心智模型 → 手写 proptest → unsafe & FFI → cargo workspace → 手写 Pin/Future/Executor**，一条路径把 Rust 的七大招牌能力（零成本抽象、所有权、生命周期、类型系统、async 模型、工程化生态、逃生舱 unsafe/FFI）全部覆盖。

#### `01_iterators_and_closures.rs` — 迭代器与闭包
- `map` / `filter` / `fold` / `sum` 迭代器链
- `zip` / `enumerate` / `any` / `all` / `find` / `flat_map`
- 闭包捕获环境变量
- 返回闭包的高阶函数 `make_adder(n)`
- 实战：文本处理管道（单词统计、Title Case）

#### `02_enum_and_option.rs` — 枚举、模式匹配与 Option/Result
- `enum Expr` 构建表达式树 ADT
- `Option<T>` 的 `map` / `and_then` / `unwrap_or`（Monad 风格）
- `Result<T, E>` 链式错误处理
- `if let` / `while let` 简洁模式匹配
- 坐标点解构匹配（象限判断）

#### `03_trait_and_generics.rs` — Trait 与泛型
- `trait Shape` 多态抽象（Circle / Rectangle / Triangle）
- 泛型工具函数 `my_map` / `my_filter` / `my_fold`
- 函数组合 `compose` 与管道 `pipe`
- 所有权系统保证不可变性
- 实战：学生成绩函数式管道（荣誉榜筛选）

#### `04_error_handling_result_chain.rs` — Result 链式错误处理
- 自定义错误 enum + `impl Display/Error`，形成精确 sum type
- `?` 操作符与 `From<E>`：跨错误类型零样板转换
- `map` / `and_then` / `or_else` 等组合子串出纯函数式管道
- 失败路径的多层聚合：解析 → 校验 → 业务规则

#### `05_ownership_and_fp.rs` — 所有权 × FP（Fn / FnMut / FnOnce）
- 三种闭包 trait 的本质：借用 / 可变借用 / 移动语义
- 闭包何时实现 `Copy`，何时只能 `Move`
- 高阶函数接收闭包的正确签名：`impl Fn`/`&dyn Fn`/`Box<dyn FnOnce>` 的选型
- 所有权如何帮你避免"闭包逃逸"造成的悬空引用

#### `06_iterator_internals.rs` — Iterator 内幕：惰性 & 零成本
- 手写 `struct Counter: Iterator` 理解 `next()` 的迭代器契约
- 手写 `map` / `filter` / `take` 适配器：都是结构体 + 状态机
- `collect` 的真相：`FromIterator` trait 的分派机制
- 为什么迭代器链等价于一个手写循环（零成本抽象）

#### `07_smart_pointers_fp.rs` — 智能指针：FP 里的"可变性开关"
- `Box<T>`：把数据放堆上、递归 ADT 的标配
- `Rc<T>` / `Arc<T>`：共享所有权（单/多线程）
- `RefCell<T>` / `Mutex<T>`：内部可变性（单/多线程）
- 组合拳 `Rc<RefCell<T>>` 与 `Arc<Mutex<T>>`：在函数式骨架里开小口子做可变

#### `08_async_streams_tokio.rs` — async/await 与 Stream
- `async fn` 返回 `impl Future`，编译期状态机
- `tokio::spawn` 启动并发任务，`join!` 并发组合
- `futures::Stream`：异步迭代器，`map` / `filter` / `buffer_unordered`
- 取消、超时、反压的基本模式
- 对标：Scala fs2、Haskell Streamly、Erlang gen_event

#### `09_typestate_pattern.rs` — Typestate：用类型刻协议到编译期
- 状态就是类型：`File<Closed>` / `File<Open>`
- 非法状态转换直接编译失败（例：关闭状态的文件不能 read）
- 和 Scala 的 phantom type、Haskell 的 DataKinds 一脉相承
- 实战：HTTP Request Builder / 状态机的编译期证明

#### `10_parser_combinators.rs` — 零依赖 Parser Combinators
- `type Parser<'a, T> = Box<dyn Fn(&'a str) -> Option<(T, &'a str)>>`
- 原语：`satisfy` / `char` / `digit`
- 组合子：`alt` / `many` / `many1` / `sep_by` / `between`
- 实战：JSON 子集解析器
- 对标：Haskell `megaparsec` / Rust 生态的 `nom`

#### `11_trait_object_vs_impl.rs` — 静态分发 vs 动态分发
- `impl Trait`：编译期单态化，零成本抽象
- `Box<dyn Trait>`：运行时 vtable，支持异构集合
- object safety 规则：带泛型/返回 Self 的方法不能 dyn
- 决策树：何时用 `impl`，何时不得不用 `dyn`

#### `12_lifetimes_advanced.rs` — 生命周期进阶
- 多生命周期标注 `<'a, 'b>` 与省略规则
- `'static` 的真相：不是"豁免检查"，而是"活到程序结束"
- `Box::leak` —— 在初始化阶段换 `'static` 的合法技巧
- HRTB：`for<'a> Fn(&'a str) -> String` 处理任意生命周期闭包

#### `13_send_sync_interior_mut.rs` — Send/Sync & 内部可变性
- `Send` / `Sync` auto trait 的含义与推导规则
- `Arc<Mutex<T>>` / `Arc<RwLock<T>>` / `Arc<Atomic*>` 三选一 benchmark
- 读多写少场景下 RwLock 的正确姿势
- 反例：`Rc<T>` 不是 Send，编译期被拦下
- 编译期断言 `fn assert_send_sync<T: Send + Sync>()`

#### `14_channel_and_parallel.rs` — Channel & 数据并行
- `std::sync::mpsc` 多生产者单消费者管道
- 多阶段 pipeline：stage1 → stage2 → stage3
- 手写 work-stealing 线程池模拟 rayon 核心思路
- `std::thread::scope`（Rust 1.63+）安全借用栈上数据
- 串行 vs 并行 benchmark（1M 元素求和）

#### `15_serde_handwritten.rs` — 手写 serde
- 核心 trait：`Serialize` / `Deserialize`（最小版）
- 给基础类型 / Option / Vec 提供实现
- 给业务结构体手动写"derive 展开"的代码
- 体会真实 serde derive 做的事：对每个字段递归调用 ser/de
- 对标：Haskell aeson / Scala circe

#### `16_thiserror_anyhow.rs` — thiserror & anyhow 风格
- 库作者：手写等价 thiserror 的 enum + `impl Display/Error/From`
- 应用作者：手写等价 anyhow 的 `Box<dyn Error> + context chain`
- `.context("...")` 扩展 trait 让任意 Result 都能带上下文
- 用 `source()` 追溯 root cause，打印完整错误链
- 何时选谁的经验法则

#### `17_tracing_structured_log.rs` — tracing 风格结构化日志
- span 是嵌套栈：子 span 自动继承父 span 上下文
- field 是结构化键值对，不靠字符串拼接
- 手写 `span!` / `info!` / `warn_!` / `error!` 宏
- subscriber 抽象：同一段业务代码可切换打印/JSON/OTel 后端
- 对标：Scala log4cats、Haskell katip、Erlang OTP logger

#### `18_axum_like_router.rs` — axum 风格路由器 & 中间件
- Handler 抽象：`Arc<dyn Fn(Request) -> Response>`
- 中间件就是 `Handler -> Handler` 的 endomorphism
- logger / auth / CORS 三个典型中间件，层叠顺序
- 路由表作为不可变数据结构，纯函数注册
- 对标真实 axum：`Router::new().route(...).layer(...)`

#### `19_tokio_sync_primitives.rs` — 同步原语 & 事件模型
- 用 Mutex + Condvar 手写 tokio 核心原语的同步等价物：
  - `oneshot`：一次性请求-应答
  - `broadcast`：多消费者 fanout（独立游标）
  - `Notify`：无载荷信号
  - `select` 风格：谁先到用谁
- 一次看懂 tokio::sync 全家桶的心智模型

#### `20_declarative_macros.rs` — macro_rules! 声明宏
- 可变参数：最小 `my_vec!`、`map!` 字面量
- 递归展开：`min!(3,1,4,1,5)`
- 代码生成：一次 `impl_greet_for!` 批量生成多个结构体 + impl
- DSL：`json!({...})` 实现 JSON 字面量
- `stringify!` / `file!` / `line!` 内建工具宏

#### `21_proc_macro_model.rs` — proc macro 心智模型
- 用 `macro_rules!` 模拟 `#[derive(Getters)]` / `#[derive(Builder)]`
- 运行时 `DynReflect` trait 展示 derive 真正做的事：扫字段 → 生成行为
- 通用 `to_insert_sql::<T: DynReflect>` 演示零样板代码的能力
- 文档注释附真实 proc macro crate 骨架（`proc-macro = true` + syn + quote）

#### `22_proptest_property.rs` — 手写迷你 proptest
- `Arbitrary` trait：随机生成 + shrink 候选集
- `forall(name, seed, n, prop)` runner：失败自动进入 shrink
- 三条经典性质：`reverse ∘ reverse = id` / 排序单调 / 排序保持 multiset
- 故意写错的 `buggy_sum` 被 shrink 压成最小反例
- 对标：Haskell QuickCheck、Scala ScalaCheck、Erlang PropEr

#### `23_unsafe_and_ffi.rs` — unsafe / 裸指针 / FFI 基础
- `Box<T> ↔ *mut T` 互转 + `Box::from_raw` 避免泄漏
- 自定义 `unsafe fn sum_inplace(...)` 带 Safety 契约
- `extern "C"` 调用 libc 的 `strlen` / `abs`
- `CString` / `CStr` 在 Rust ↔ C 字符串边界做安全转换
- 给 FFI 套 safe wrapper 的标准姿势

#### `24_cargo_workspace.rs` — cargo workspace 工程骨架
- 标准目录：`crates/` / `.cargo/` / `rust-toolchain.toml` / `xtask`
- 根 `Cargo.toml` 中的 `workspace.dependencies` 统一版本
- profile 调优：`lto = "fat"` / `codegen-units = 1` / `panic = "abort"`
- `cargo fmt` / `clippy` / `test` / `bench` / `audit` / `deny` 标配命令
- 对标：sbt 多模块 / rebar3 umbrella / cabal project

#### `25_pin_future_executor.rs` — 手写 Future / Waker / Executor
- `Ready<T>` / `TimerFuture`：两个基础叶子 Future
- 用 `Condvar` 实现 `Wake` + `block_on` 最小 executor
- 手写 `join2` 组合子：并发 poll 两个 future
- 用 `async fn` 写业务，跑在自己实现的 executor 上（seq 80ms vs par 44ms）
- 把 Pin / poll / waker / Ready/Pending 彻底串起来，理解 async/await 的底层真相

---

### 🟡 Elixir — BEAM 上的现代化语法糖

Elixir 和 Erlang 共享同一套 BEAM 运行时，`.beam` 文件 100% 互通，但带来了更现代的语法：管道 `|>`、强大的宏系统、`with` 表达式、Protocol / Behaviour 多态三件套，以及 Phoenix / Ecto / LiveView 这套成熟的 Web 生态。当前这组 Demo 从零依赖语法一路覆盖到真实 hex 生态：**基础语法 & 管道 → Protocol / Behaviour 多态 → `with` × Result 流程 → 宏入门 → 宏 DSL 路由 → GenServer/Agent/Task → Supervisor/Registry → Task.Supervisor async_stream → Ecto Repo/Changeset/Multi → Phoenix Plug 路由 → LiveView 心智模型 → Flow/GenStage/Broadway → Telemetry + OpenTelemetry → ExUnit + doctest + Mox + StreamData → mix/umbrella/releases 工程骨架**，一条路径把 Elixir 的核心心智模型和 BEAM 生态的工程化能力串起来。其中 01~08 是零依赖的 `.exs` 脚本，09~15 通过 `Mix.install` 嵌入式拉 hex 包，在单个脚本里完成从框架心智到测试与发布的全链路演示。

#### `01_basics_pipeline.exs` — 基础语法 + 管道 + 模式匹配
- 模块 `defmodule` / 函数 `def` / 私有函数 `defp`
- 管道操作符 `|>`：把嵌套调用拍扁成从左到右的数据流
- 模式匹配与绑定：`{:ok, value}` / `[head | tail]` / 解构赋值
- 守卫 `when` 子句：在匹配层做类型与范围过滤
- 对标：Erlang 01 `01_pattern_matching` / Haskell `where` + 管道 `&`

#### `02_struct_protocol_behaviour.exs` — struct / protocol / behaviour 多态三件套
- `defstruct`：带默认值的记录 + 编译期字段检查
- `defprotocol` / `defimpl`：类型类风格的开放多态（对标 Scala type class）
- `@behaviour` + `@callback`：接口契约 + `@impl true` 静态检查
- 三者协作：同一个业务在结构、能力和契约三层分别扩展
- 对标：Scala case class + type class + trait / Haskell Record + class

#### `03_with_result_flow.exs` — `with` 语句 × Result 流程控制
- `{:ok, value}` / `{:error, reason}` 作为事实上的 Result
- `with` 表达式：把多个 `case` 链条拍扁成顺序模式匹配
- 失败短路：任一步骤不匹配立即跳到 `else` 分支
- 业务校验流水线：解析 → 校验 → 持久化的三段式
- 对标：Haskell `do` / Scala `for` / Rust `?` 操作符

#### `04_macros_intro.exs` — 宏系统入门 `quote` / `unquote` / `defmacro`
- AST 即数据：所有 Elixir 代码都能被 `quote` 成三元组
- `unquote` 把变量注入到模板里，`unquote_splicing` 处理列表
- 卫生宏（hygienic macro）：自动避免变量捕获
- `Macro.expand/2` 在 REPL 里观察展开结果
- 对标：Scheme `syntax-rules` / Scala 3 `inline + quotes` / Rust `macro_rules!`

#### `05_macros_dsl_router.exs` — 宏进阶：自制路由 DSL
- 用宏搭建一个迷你 HTTP 路由 DSL：`get/post/put/delete "path", do: ... end`
- 编译期把声明累加进模块属性 `@routes`
- 一次性在 `__before_compile__` 里生成真正的 `match/2` 函数
- 理解 Phoenix Router / Ecto schema DSL 背后的生成机制
- 对标：Scala 3 inline macros / Rust proc macro 派生

#### `06_genserver_agent_task.exs` — GenServer / Agent / Task 三件套
- `GenServer`：OTP 的同步/异步 server 行为（`handle_call` / `handle_cast`）
- `Agent`：轻量状态持有，天然适合计数器、缓存、配置
- `Task` / `Task.async` / `Task.await`：一次性异步任务
- 状态 = fold 消息流，进程 = 最小隔离单元
- 对标：Erlang 04 `04_gen_server_counter` / Akka Typed Actor / Scala cats-effect `Ref`

#### `07_supervisor_registry.exs` — Supervisor / DynamicSupervisor / Registry
- 静态 `Supervisor`：固定子进程树 + `:one_for_one` / `:rest_for_one` 策略
- `DynamicSupervisor`：按需动态拉起子进程（房间、会话、工作者）
- `Registry`：进程名字服务（`{:via, Registry, {Reg, key}}`）
- 按 key 查找进程 + 崩溃重启 + 自动清理的组合拳
- 对标：Erlang 05 `05_supervisor_tree` / Akka Cluster Sharding

#### `08_task_supervisor_async_stream.exs` — Task.Supervisor × async_stream
- `Task.Supervisor` 统一托管异步任务生命周期
- `async_stream/3`：有监督的并发迭代器（可限流、可超时、可 `ordered`）
- 链式 `Stream.map` + `async_stream` 组合流式并行处理
- 失败任务的隔离：单个任务 crash 不会打垮整条管道
- 对标：Scala fs2 `parEvalMap` / Rust `buffer_unordered`

#### `09_ecto_repo_changeset_multi.exs` — Ecto: Repo / Schema / Changeset / Multi
- `Ecto.Schema` 声明表结构与字段类型
- `Ecto.Changeset`：cast / validate / constraint 的函数式校验管道
- `Ecto.Repo`：`insert/update/delete/all` 的最小持久化 API
- `Ecto.Multi`：把多条操作打包成单事务，失败整体回滚
- 对标：Scala Doobie + Quill / Rust sqlx / Haskell persistent

#### `10_phoenix_router_plug.exs` — Phoenix 风格 Plug 路由
- Plug 是 Elixir Web 的核心抽象：`fn conn -> conn end` 管道函数
- 在 4001 端口拉一个 Cowboy server，自测 `GET /hello` / `POST /echo`
- `Plug.Router` DSL：`get` / `post` + `plug :match` / `plug :dispatch`
- 中间件 = 普通函数：路由、日志、CORS、auth 全都是 Plug 链条上的节点
- 对标：Scala http4s Routes / Rust axum Router / Haskell WAI middleware

#### `11_liveview_mental_model.exs` — LiveView 心智模型（本地版）
- 不依赖真实浏览器，用纯 Elixir 模拟 LiveView 的 server-side render 循环
- `mount/3`：首次渲染拿初始 assigns；`handle_event/3`：事件推进状态
- diff 计算：服务端只发 changed assigns，而不是整段 HTML
- 状态保存在 LiveView 进程里，每个连接一个 GenServer
- 对标：React + Redux 的心智，但状态在服务端

#### `12_flow_genstage_broadway.exs` — Flow / GenStage / Broadway 流式管道
- `GenStage`：带背压（back-pressure）的生产者-消费者协议
- `Flow`：基于 GenStage 的并行数据流，支持 `partition` / `window` / `reduce`
- `Broadway`：面向真实消息中间件（Kafka / RabbitMQ / SQS）的生产级管道
- 背压是一等公民：下游消费慢，上游自动放缓
- 对标：Scala fs2 + cats-effect / Akka Streams / Haskell streamly

#### `13_telemetry_opentelemetry.exs` — Telemetry + OpenTelemetry
- `:telemetry.execute/3`：发出结构化事件（event name + measurements + metadata）
- `:telemetry.attach/4`：在运行时挂载 handler，完全解耦监控与业务
- OpenTelemetry 桥接：把 Elixir 事件翻译成标准 span / metric
- 全生态共用同一套 hook：Phoenix / Ecto / Broadway 默认都发 telemetry
- 对标：Scala log4cats + otel4s / Rust tracing + opentelemetry

#### `14_exunit_doctest_mox_streamdata.exs` — ExUnit + doctest + Mox + StreamData
- `ExUnit`：async 默认开启的单元测试框架
- `doctest`：把 `@doc` 里的交互式示例直接升级为可执行测试
- `Mox`：基于 behaviour 的静态 mock，强制 `@impl` 契约一致
- `StreamData`：基于性质的测试（property-based），自动 shrink 反例
- 对标：Scala munit + ScalaCheck / Haskell HSpec + QuickCheck

#### `15_mix_umbrella_releases.exs` — mix / umbrella / releases 工程骨架
- `mix new` / `mix.exs`：单项目的依赖、编译、任务定义
- umbrella：`apps/*` 多子应用共享依赖版本的 monorepo 结构
- `mix release`：编译出自包含、带 ERTS 的生产发布包
- 热升级 / config provider / runtime.exs 的最小心智
- 对标：Rust cargo workspace / Scala sbt 多模块 / Erlang rebar3 umbrella

---

## 🚀 运行方式

### Scala
```bash
# 标准库 / 无外部依赖的 Demo 可直接用 scala 运行
scala scala/01_HigherOrderFunctions.scala
scala scala/08_FormValidation.scala
scala scala/12_ValidatedRegistration.scala
scala scala/13_SemigroupAndMonoid.scala
scala scala/14_FunctorApplicativeMonad.scala
scala scala/15_ReaderConfig.scala
scala scala/16_StateCalculator.scala
scala scala/17_IOBasics.scala
scala scala/18_ResourceDemo.scala
scala scala/19_ConcurrencyDemo.scala
scala scala/20_FS2Pipeline.scala
scala scala/21_Http4sMiniService.scala
scala scala/22_TaglessUserService.scala
scala scala/23_KleisliRequestPipeline.scala
scala scala/24_RetryBackoff.scala
scala scala/25_TaglessTestInterpreter.scala
```

```bash
# 真实库版本的高级实战 Demo 需要 scala-cli（会自动拉取依赖）
scala-cli run scala/26_CatsEffectIOApp.scala
scala-cli run scala/27_CatsEffectResource.scala
scala-cli run scala/28_FS2StreamWorkflow.scala
scala-cli run scala/29_Http4sRoutes.scala
scala-cli run scala/30_TaglessCatsEffect.scala
scala-cli run scala/31_CirceJsonCodec.scala
scala-cli run scala/32_Http4sJsonApi.scala
scala-cli run scala/33_Http4sClientDemo.scala
scala-cli run scala/34_FS2QueueWorker.scala
scala-cli run scala/35_CatsEffectTimeoutAndCancel.scala
scala-cli run scala/36_CatsEffectDeferredRef.scala
scala-cli run scala/37_FS2TopicPubSub.scala
scala-cli run scala/38_Http4sBearerAuth.scala
scala-cli run scala/39_EmberServerClientRoundTrip.scala
scala-cli run scala/41_CatsEffectSemaphore.scala
scala-cli run scala/42_FS2ParEvalMap.scala
scala-cli run scala/43_Http4sErrorHandling.scala
scala-cli run scala/44_TaglessHttp4sUserModule.scala
scala-cli run scala/46_CatsEffectSupervisor.scala
scala-cli run scala/47_FS2MergeStreams.scala
scala-cli run scala/48_Http4sAuthMiddleware.scala
scala-cli run scala/49_EitherTUserFlow.scala
scala-cli run scala/51_CatsEffectRace.scala
scala-cli run scala/52_FS2ErrorRecovery.scala
scala-cli run scala/53_Http4sClientMiddleware.scala
scala-cli run scala/54_Http4sClientAggregation.scala
scala-cli run scala/56_CatsEffectUncancelable.scala
scala-cli run scala/57_FS2InterruptAndSignallingRef.scala
scala-cli run scala/58_Http4sClientRetry.scala
scala-cli run scala/59_Http4sContextRoutes.scala
scala-cli run scala/61_CatsEffectDispatcher.scala
scala-cli run scala/62_FS2GroupWithin.scala
scala-cli run scala/63_Http4sStreamingApi.scala
scala-cli run scala/64_EmberStreamingClient.scala
scala-cli run scala/66_CatsEffectIOLocal.scala
scala-cli run scala/67_FS2PullLineDecoder.scala
scala-cli run scala/68_Http4sServerSentEvents.scala
scala-cli run scala/69_EmberSseClient.scala
scala-cli run scala/71_CatsEffectMapRef.scala
scala-cli run scala/72_FS2TopicHub.scala
scala-cli run scala/73_Http4sWebSocketChat.scala
scala-cli run scala/74_JdkWebSocketBridgeClient.scala
scala-cli run scala/76_Http4sMultipartUpload.scala
scala-cli run scala/77_FS2ChunkedFileProcessor.scala
scala-cli run scala/78_DoobieTransactorResource.scala
scala-cli run scala/79_DoobieRepositoryTagless.scala
scala-cli run scala/81_DoobieStreamingExport.scala
scala-cli run scala/82_FS2CsvImportPipeline.scala
scala-cli run scala/83_Http4sCsvExport.scala
scala-cli run scala/84_DoobieBatchImportTagless.scala
scala-cli run scala/86_CatsEffectIdempotencyGate.scala
scala-cli run scala/87_FS2DedupReservationStream.scala
scala-cli run scala/88_Http4sIdempotencyKey.scala
scala-cli run scala/89_DoobieIdempotentReservation.scala
scala-cli run scala/91_CatsEffectOutboxCoordinator.scala
scala-cli run scala/92_FS2OutboxRetryStream.scala
scala-cli run scala/93_Http4sWebhookOutbox.scala
scala-cli run scala/94_DoobieTransactionalOutbox.scala
scala-cli run scala/96_CatsEffectInboxCoordinator.scala
scala-cli run scala/97_FS2InboxRetryConsumer.scala
scala-cli run scala/98_Http4sWebhookInbox.scala
scala-cli run scala/99_DoobieTransactionalInbox.scala
scala-cli run scala/101_CatsEffectSagaCoordinator.scala
scala-cli run scala/102_FS2SagaTimeoutCompensationStream.scala
scala-cli run scala/103_Http4sSagaWorkflow.scala
scala-cli run scala/104_DoobieTransactionalSagaState.scala
scala-cli run scala/106_CatsEffectProjectionCoordinator.scala
scala-cli run scala/107_FS2ProjectionReplayStream.scala
scala-cli run scala/108_Http4sReadModelQuery.scala
scala-cli run scala/109_DoobieTransactionalProjectionCheckpoint.scala
scala-cli run scala/111_CatsEffectCommandBus.scala
scala-cli run scala/112_FS2CommandRouterStream.scala
scala-cli run scala/113_Http4sCQRSBoundary.scala
scala-cli run scala/114_DoobieTransactionalCommandWrite.scala
scala-cli run scala/116_CatsEffectEventSourcedAggregate.scala
scala-cli run scala/117_FS2EventAppendStream.scala
scala-cli run scala/118_Http4sEventStoreEndpoint.scala
scala-cli run scala/119_DoobieEventStoreRepository.scala
scala-cli run scala/121_CatsEffectProcessManager.scala
scala-cli run scala/122_FS2ProcessManagerEventRouter.scala
scala-cli run scala/123_Http4sProcessManagerBoundary.scala
scala-cli run scala/124_DoobieProcessManagerRepository.scala
scala-cli run scala/126_CatsEffectACLTranslator.scala
scala-cli run scala/127_FS2ACLTranslationStream.scala
scala-cli run scala/128_Http4sACLAdapterEndpoint.scala
scala-cli run scala/129_DoobieACLTranslationLog.scala
scala-cli run scala/131_CatsEffectContextMapAssembly.scala
scala-cli run scala/132_FS2CrossContextEventBus.scala
scala-cli run scala/133_Http4sContextMapGateway.scala
scala-cli run scala/134_DoobieMultiContextTransaction.scala
```

```bash
# 测试类 Demo 使用 scala-cli test
scala-cli test scala/40_MUnitCatsEffectSuite.scala
scala-cli test scala/45_MUnitHttp4sRouteSuite.scala
scala-cli test scala/50_MUnitAuthMiddlewareSuite.scala
scala-cli test scala/55_MUnitClientOrchestrationSuite.scala
scala-cli test scala/60_MUnitClientRetrySuite.scala
scala-cli test scala/65_MUnitStreamingRouteSuite.scala
scala-cli test scala/70_MUnitServerSentEventsSuite.scala
scala-cli test scala/75_MUnitWebSocketChatSuite.scala
scala-cli test scala/80_MUnitRepositoryIntegrationSuite.scala
scala-cli test scala/85_MUnitBatchImportExportSuite.scala
scala-cli test scala/90_MUnitIdempotencyIntegrationSuite.scala
scala-cli test scala/95_MUnitTransactionalOutboxSuite.scala
scala-cli test scala/100_MUnitTransactionalInboxSuite.scala
scala-cli test scala/105_MUnitSagaIntegrationSuite.scala
scala-cli test scala/110_MUnitProjectionReplaySuite.scala
scala-cli test scala/115_MUnitCQRSIntegrationSuite.scala
scala-cli test scala/120_MUnitEventSourcingSuite.scala
scala-cli test scala/125_MUnitProcessManagerSuite.scala
scala-cli test scala/130_MUnitACLIntegrationSuite.scala
scala-cli test scala/135_MUnitContextMapEndToEndSuite.scala
```

### Erlang
```bash
# 需要安装 Erlang（建议 OTP 26+；OTP 28 起文件名与模块名须一致，本仓库 module 名已带数字前缀，无需 workaround）
cd erlang

# 推荐：用脚本一键编译并运行（参数为 demo 编号）
./run.sh 01     # 跑 01_pattern_matching
./run.sh 04     # 跑 04_gen_server_counter
# 不带参数则跑全部
./run.sh

# 也可以手动编译运行单个 demo（module 名 = 文件名去掉 .erl，含数字前缀，需用单引号括起来）
erlc 01_pattern_matching.erl
erl -noshell -s '01_pattern_matching' main -s init stop
```

### Haskell
```bash
# 需要安装 GHC
cd haskell
runhaskell 01_PureAndLazy.hs
runhaskell 02_TypeClassAndMonad.hs
runhaskell 03_CurryAndCompose.hs
runhaskell 04_IOAndSideEffects.hs
runhaskell 05_StateAndReader.hs
runhaskell 06_ConcurrencySTM.hs
runhaskell 07_TypesAndADT.hs
runhaskell 08_MonadTransformers.hs
runhaskell 09_LensAndOptics.hs
runhaskell 10_ParserCombinators.hs
runhaskell 11_FreeMonadsAndDSL.hs
runhaskell 12_QuickCheck.hs      # 需要 QuickCheck：cabal install --lib QuickCheck
runhaskell 13_ArrowAndProfunctor.hs
runhaskell 14_FoldableTraversable.hs
runhaskell 15_TypesAdvanced.hs
runhaskell 16_StreamingPipeline.hs
runhaskell 17_LambdaCalculusAndFix.hs
runhaskell 18_AlternativeAndMonadPlus.hs
runhaskell 19_ExceptionsAndConcurrency.hs
runhaskell 20_GenericsAndDerivingVia.hs
runhaskell 21_EffectSystemPatterns.hs
runhaskell 22_FPBestPracticesAndStateMachineTests.hs
```

### Rust
```bash
# 需要安装 Rust
cd rust
rustc 01_iterators_and_closures.rs -o demo && ./demo
```

### Elixir
```bash
# 需要安装 Elixir（建议 1.15+ / OTP 26+，macOS: brew install elixir）
cd elixir

# 一键运行脚本（封装了分组 / 单个 / 全量三种模式）
./run.sh           # 只跑零依赖组 01~08（不联网、秒级完成）
./run.sh 3         # 只跑编号 03（03_with_result_flow.exs）
./run.sh deps      # 只跑依赖组 09~15（首次会 Mix.install 拉 hex 包）
./run.sh all       # 跑全部 15 个 Demo（含依赖组）

# 也可以直接用 elixir 命令跑单个脚本
elixir 01_basics_pipeline.exs
elixir 02_struct_protocol_behaviour.exs
elixir 03_with_result_flow.exs
elixir 04_macros_intro.exs
elixir 05_macros_dsl_router.exs
elixir 06_genserver_agent_task.exs
elixir 07_supervisor_registry.exs
elixir 08_task_supervisor_async_stream.exs

# 09~15 使用 Mix.install 嵌入式拉依赖，首次 1~3 分钟，之后有缓存
elixir 09_ecto_repo_changeset_multi.exs
elixir 10_phoenix_router_plug.exs        # 会在 4001 端口短暂启一个 HTTP 服务并自测
elixir 11_liveview_mental_model.exs
elixir 12_flow_genstage_broadway.exs
elixir 13_telemetry_opentelemetry.exs
elixir 14_exunit_doctest_mox_streamdata.exs
elixir 15_mix_umbrella_releases.exs
```

---

## 🧭 推荐学习路径

```
1️⃣ 入门: 高阶函数 + 模式匹配 + 不可变性
   Scala 01 → Scala 02 → Scala 03
   建立函数式编程的基本直觉

2️⃣ 进阶基础: 错误处理 + 递归 + 惰性求值 + Type Class
   Scala 04 → Scala 05 → Scala 06 → Scala 07
   理解失败建模、递归结构、按需计算与按能力编程

3️⃣ 进阶建模: 表单校验 + 状态机 + 表达式树 + JSON
   Scala 08 → Scala 09 → Scala 10 → Scala 11
   把 FP 风格真正用于业务建模和递归数据结构处理

4️⃣ 中级抽象: Validated + Monoid + Functor / Applicative / Monad + Reader / State
   Scala 12 → Scala 13 → Scala 14 → Scala 15 → Scala 16
   开始系统理解常见 FP 抽象，以及它们为什么会反复出现

5️⃣ 高级前置: IO + Resource + 并发组合 + 流处理直觉
   Scala 17 → Scala 18 → Scala 19 → Scala 20
   先用标准库和手写微型抽象建立直觉，再为真实库做准备

6️⃣ 服务化桥接: 最小 HTTP 服务 + Tagless Final
   Scala 21 → Scala 22
   先理解服务如何组织、代数如何设计，再进入真实框架

7️⃣ 工程化补充: Kleisli + Retry / Backoff + 测试解释器
   Scala 23 → Scala 24 → Scala 25
   把 ReaderT 风格、重试策略、可测试解释器这些真实工程里很常见的模式串起来

8️⃣ 高级实战上半场: cats-effect + fs2 + http4s + 真实 Tagless 解释器
   Scala 26 → Scala 27 → Scala 28 → Scala 29 → Scala 30
   先把 effect、资源、流、服务与解释器这些核心骨架搭起来

9️⃣ 高级实战中段: circe JSON + http4s client + Queue + timeout/cancel
   Scala 31 → Scala 32 → Scala 33 → Scala 34 → Scala 35
   继续补齐协议层、服务调用方、异步任务流和并发控制这些真实工程细节

🔟 高级实战深化: Ref/Deferred + Topic + 鉴权 + Ember + 测试化
   Scala 36 → Scala 37 → Scala 38 → Scala 39 → Scala 40
   把并发协作、广播事件流、认证授权、真实联调和测试框架都纳入工程实践

1️⃣1️⃣ 高级实战收束: Semaphore + parEvalMap + 错误映射 + 模块装配 + 路由测试
   Scala 41 → Scala 42 → Scala 43 → Scala 44 → Scala 45
   继续补齐并发治理、HTTP 边界分层、服务模块组织和路由级测试闭环

1️⃣2️⃣ 工程化深化: Supervisor + merge + AuthMiddleware + EitherT + 鉴权测试
   Scala 46 → Scala 47 → Scala 48 → Scala 49 → Scala 50
   继续推进后台任务生命周期、事件流汇总、官方鉴权抽象、错误编排和鉴权边界测试

1️⃣3️⃣ 服务联通与恢复: race + 流恢复 + client middleware + 聚合编排 + 编排测试
   Scala 51 → Scala 52 → Scala 53 → Scala 54 → Scala 55
   继续补齐竞速读取、fs2 错误恢复、调用方上下文透传、下游聚合与编排测试闭环

1️⃣4️⃣ 服务治理与上下文: uncancelable + 停流信号 + client retry + ContextRoutes + 重试测试
   Scala 56 → Scala 57 → Scala 58 → Scala 59 → Scala 60
   继续补齐取消边界、长生命周期流优雅退出、下游调用治理、请求上下文注入和调用方重试测试闭环

1️⃣5️⃣ 边界桥接与流式服务: Dispatcher + groupWithin + 流式响应 + 流式 client + 流式路由测试
   Scala 61 → Scala 62 → Scala 63 → Scala 64 → Scala 65
   继续补齐旧式回调接缝、窗口聚合、持续响应，以及流式消费 / 测试闭环

1️⃣6️⃣ 本地上下文与协议化事件流: IOLocal + Pull + SSE + SSE client + SSE 测试
   Scala 66 → Scala 67 → Scala 68 → Scala 69 → Scala 70
   继续补齐 fiber-local 上下文、自定义流变换、协议化事件推送和 SSE 测试闭环

1️⃣7️⃣ 房间状态与双向实时通信: MapRef + Topic hub + WebSocket + JDK client + WebSocket 测试
   Scala 71 → Scala 72 → Scala 73 → Scala 74 → Scala 75
   继续补齐按 key 分片状态、房间广播、双向实时连接和 WebSocket 自动化测试

1️⃣8️⃣ 上传边界与数据库集成: Multipart + chunked processing + Doobie + Repository + 集成测试
   Scala 76 → Scala 77 → Scala 78 → Scala 79 → Scala 80
   继续补齐文件上传、分块处理、事务边界、真实 SQL 仓储与数据库回归测试

1️⃣9️⃣ 批量导入导出与流式报表: query.stream + CSV pipeline + CSV download + batch import + integration suite
   Scala 81 → Scala 82 → Scala 83 → Scala 84 → Scala 85
   继续补齐数据库流式导出、CSV 导入校验分批、下载接口、批量写库与导入导出闭环测试

2️⃣0️⃣ 幂等写入与重复请求治理: idempotency gate + dedup stream + Idempotency-Key + persistent idempotency + integration suite
   Scala 86 → Scala 87 → Scala 88 → Scala 89 → Scala 90
   继续补齐并发重复提交去重、消息重放过滤、HTTP 幂等键、数据库持久化防重与重复请求测试闭环

2️⃣1️⃣ 事务 Outbox 与最终一致性: outbox coordinator + retry stream + webhook dispatch + transactional outbox + integration suite
   Scala 91 → Scala 92 → Scala 93 → Scala 94 → Scala 95
   继续补齐写库后发事件、后台重试投递、HTTP 回调边界、同事务 outbox 写入与最终一致性测试闭环

2️⃣2️⃣ 事务 Inbox 与消费端幂等: inbox coordinator + retry consumer + webhook inbox + transactional inbox + integration suite
   Scala 96 → Scala 97 → Scala 98 → Scala 99 → Scala 100
   继续补齐重复投递防护、消费端幂等、Webhook 接收保护、同事务 processed_event 记录与消费端重试测试闭环

2️⃣3️⃣ Saga 补偿与跨服务工作流: saga coordinator + timeout compensation stream + workflow boundary + transactional saga state + integration suite
   Scala 101 → Scala 102 → Scala 103 → Scala 104 → Scala 105
   继续补齐跨步骤状态推进、支付超时补偿、工作流回调边界、事务 Saga 状态与跨服务工作流测试闭环

2️⃣4️⃣ 读模型投影与事件回放: projection coordinator + replay stream + read-model query + transactional checkpoint + replay suite
   Scala 106 → Scala 107 → Scala 108 → Scala 109 → Scala 110
   继续补齐查询侧 checkpoint 推进、后台回放流、读模型查询边界、事务投影一致性与 replay 测试闭环

2️⃣5️⃣ CQRS 命令查询职责分离: command bus + command router stream + CQRS boundary + transactional command write + integration suite
   Scala 111 → Scala 112 → Scala 113 → Scala 114 → Scala 115
   继续补齐命令总线、批量命令路由流、HTTP 写读双边界、事务命令写入与 CQRS 测试闭环

2️⃣6️⃣ 事件溯源: event-sourced aggregate + event append stream + event store endpoint + event store repository + event sourcing suite
   Scala 116 → Scala 117 → Scala 118 → Scala 119 → Scala 120
   继续补齐聚合根重建、乐观锁追加、Event Store HTTP 边界、数据库事件存储与事件溯源测试闭环

2️⃣7️⃣ 进程管理器: process manager + event router stream + process manager boundary + process manager repository + integration suite
   Scala 121 → Scala 122 → Scala 123 → Scala 124 → Scala 125
   继续补齐跨上下文事件路由、进程状态机、命令 Outbox、幂等推进与进程管理器测试闭环

2️⃣8️⃣ 防腐层（ACL）: ACL translator + translation stream + ACL adapter endpoint + translation log + integration suite
   Scala 126 → Scala 127 → Scala 128 → Scala 129 → Scala 130
   继续补齐上游模型翻译、拒绝日志、幂等接收、翻译持久化与防腐层测试闭环

2️⃣9️⃣ 有界上下文地图集成: context map assembly + cross-context event bus + context map gateway + multi-context transaction + end-to-end suite
   Scala 131 → Scala 132 → Scala 133 → Scala 134 → Scala 135
   把所有有界上下文装配成完整系统，验证跨上下文工作流的端到端行为

3️⃣0️⃣ 横向对比: 同一个思想在不同语言中的实现
   Erlang 01/02/03 → Haskell 01/02/03 → Rust 01/02/03
   理解不同语言对函数式编程的取舍与强调点

3️⃣1️⃣ BEAM 现代语法糖: 走一圈 Elixir 的招牌能力（建议顺序 03 → 06 → 07）
   Elixir 03 (with × Result)
     → 看 `|>` 管道 + `with` 表达式如何把 Scala `for` / Haskell `do` 的 Monad 味道
       用最轻的语法拍扁，业务流程写起来像纯数据管道
   Elixir 06 (GenServer / Agent / Task)
     → 看 OTP 行为三件套如何把“状态 = fold 消息流”“进程 = 最小隔离单元”
       落成可直接投产的 API（对标 Erlang 04 `04_gen_server_counter` / Akka Typed Actor）
   Elixir 07 (Supervisor / DynamicSupervisor / Registry)
     → 看监督树 + 动态拉子进程 + key→pid 注册如何组合成
       “崩溃即重启、按需开房间、天然可命名”的并发骨架（对标 Erlang 05 `05_supervisor_tree`）
   这三步合起来差不多就覆盖了 Elixir 最独特的心智模型：
     管道 / with 写业务 × OTP 行为抽象 × 监督树可靠性

🔚 后续扩展: 查看 `scala/SCALA_FP_ROADMAP.md` 与 `elixir/ELIXIR_FP_ROADMAP.md`
   继续从真实库入门走向更完整的函数式服务、测试与架构拆分
```

---

## 📚 延伸阅读

- [Learn You a Haskell for Great Good!](http://learnyouahaskell.com/) — Haskell 入门经典
- [Learn You Some Erlang for Great Good!](https://learnyousomeerlang.com/) — Erlang 入门经典
- [Functional Programming in Scala](https://www.manning.com/books/functional-programming-in-scala) — Scala FP 红宝书
- [Rust By Example](https://doc.rust-lang.org/rust-by-example/) — Rust 官方示例教程

---

## 📝 License

本项目仅用于学习目的，欢迎自由使用和分享。
# functioallanguagedemo01
