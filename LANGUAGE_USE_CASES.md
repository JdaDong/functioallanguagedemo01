# 四种语言的应用场景对比

> 本文档分两部分：
> - **Part 1 · 行业应用场景**：Haskell / Scala / Erlang / Elixir 各自在业界被实际采用的典型场景。
> - **Part 2 · 本仓库 demo × 场景映射**：把本仓库 `haskell/` `scala/` `erlang/` `elixir/` 目录下现有的 demo，对应到 Part 1 讲的场景，方便按兴趣切入阅读。
>
> 语法/FP 概念层面的对比请看 [LANGUAGE_COMPARISON.md](./LANGUAGE_COMPARISON.md)，二者正交。

---

# Part 1 · 行业应用场景

基于本项目涉及的四门函数式/偏函数式语言（**Haskell / Scala / Erlang / Elixir**），按"**最被业界实际采用的场景**"来说：

---

## 🟣 Haskell — "正确性至上"的领域

**定位**：纯函数式 + 强静态类型 + 惰性求值。编译通过 ≈ 很多 bug 已被类型系统堵死。

| 典型场景 | 代表案例 |
|---|---|
| **金融 / 量化** | Standard Chartered、Barclays、Jane Street（虽主用 OCaml）、IOHK |
| **区块链 / 形式化** | Cardano（IOHK 用 Haskell 写节点）、Plutus 智能合约 |
| **编译器 / DSL / 语言工具** | GHC 自身、Pandoc（文档转换）、Elm 编译器早期、shellcheck |
| **高可靠后端 / 数据管道** | Mercury（银行 API）、Hasura（GraphQL 引擎） |
| **研究与规范** | 论文算法原型、类型系统研究 |

**不适合**：需要快速堆业务、团队招人困难、依赖成熟 SDK 生态（如手游、移动端）的场景。

---

## 🔴 Scala — "JVM 上的多范式 + 大数据"

**定位**：跑在 JVM 上，能调所有 Java 库；既能写纯 FP（Cats/ZIO），也能写 OO；类型系统强但比 Haskell 务实。

| 典型场景 | 代表案例 |
|---|---|
| **大数据 / 流处理**（最大本命） | **Apache Spark**、**Kafka**（核心用 Scala/Java 混合）、Flink 部分、Akka Streams |
| **高并发后端 API** | Twitter（早期迁 Scala）、LinkedIn、Netflix 部分服务、唯品会/知乎部分后端 |
| **金融交易系统** | Morgan Stanley、高盛（Scala + Akka） |
| **机器学习 / 数据工程平台** | Databricks（Spark 母公司）、Coursera、Foursquare |
| **需要 JVM 生态但想写 FP 的团队** | Cats Effect / ZIO 技术栈的 SaaS 后端 |

**不适合**：冷启动敏感的 Serverless、对二进制体积有要求的 CLI。

---

## 🟠 Erlang — "永不宕机的电信级系统"

**定位**：爱立信 1986 年为电信交换机而生。**Actor 模型 + 抢占式调度 + 热更新 + 进程隔离**，强在**高并发 + 容错 + 长时间运行**。

| 典型场景 | 代表案例 |
|---|---|
| **即时通讯 / 推送**（最经典） | **WhatsApp**（2 台服务器扛 200 万连接）、Discord 早期语音、微信红包部分 |
| **电信 / 交换设备** | 爱立信 AXD301、思科部分路由器 |
| **消息中间件** | **RabbitMQ**（核心用 Erlang 写） |
| **游戏服务器 / 长连接网关** | 英雄联盟聊天系统（Riot）、某些 MMO 后端 |
| **支付 / 高可用交易** | 部分银行核心、博彩系统 |

**不适合**：CPU 密集型数值计算（浮点慢）、纯算法研究、前端。

---

## 🟢 Elixir — "Erlang VM + 现代语法 + Web 生产力"

**定位**：Ruby 风格语法 + 跑在 BEAM VM 上，**继承 Erlang 所有并发/容错优势**，但开发体验现代化（Mix 工具链、Phoenix 框架、文档、宏系统）。

| 典型场景 | 代表案例 |
|---|---|
| **高并发 Web / 实时应用**（本命） | **Discord**（单节点百万连接）、Pinterest 通知、Bleacher Report |
| **LiveView 实时 UI**（取代部分前端 JS） | Phoenix LiveView 全栈应用、内部运营平台 |
| **物联网 / 嵌入式**（Nerves） | 工业设备、农业传感器网关 |
| **金融科技 / SaaS 后端** | PepsiCo、Brex、Divvy、Change.org |
| **机器学习新势力**（Nx / Axon 生态） | 替代部分 Python 推理服务（José Valim 近年主推） |

**不适合**：CPU 密集数值运算（和 Erlang 同样的短板）、需要强静态类型保证的大型代码库（但 2024+ 的 set-theoretic types 正在补这块）。

---

## 📊 一张表速查

| 维度 | Haskell | Scala | Erlang | Elixir |
|---|---|---|---|---|
| **生态平台** | 独立 / GHC | JVM | BEAM VM | BEAM VM |
| **一句话定位** | 纯 FP + 强类型 | JVM 上的 FP/OO 混合 | 电信级容错并发 | 现代化的 Erlang |
| **最强场景** | 正确性敏感（金融/编译器/区块链） | 大数据 / 高并发后端 | 电信 / IM / 消息队列 | 实时 Web / LiveView / IoT |
| **并发模型** | STM / async / 纯函数 | Actor(Akka) / Future / ZIO Fiber | **Actor + 监督树**（原生） | **Actor + 监督树**（原生） |
| **类型系统** | ⭐⭐⭐⭐⭐（最强） | ⭐⭐⭐⭐ | ⭐（动态） | ⭐⭐（动态，正在加 set-theoretic） |
| **招人难度** | 很难 | 中等 | 难 | 中等（近年回暖） |
| **杀手级项目** | GHC / Cardano / Pandoc | Spark / Kafka / Akka | WhatsApp / RabbitMQ | Discord / Phoenix |

---

## 🎯 选型口诀

- **想要编译期消灭 bug、写编译器/金融/区块链** → **Haskell**
- **已有 JVM 技术栈、要搞大数据 or 高并发后端** → **Scala**
- **要扛百万长连接、7×24 不宕机、电信级 SLA** → **Erlang**
- **要 Erlang 的能力但不想写 Erlang 语法、做实时 Web/IoT** → **Elixir**

---

# Part 2 · 本仓库 demo × 场景映射

> 说明：下表按 **Part 1 给出的场景** 反向索引本仓库的 demo。
> - Haskell / Erlang / Elixir demo 数量适中，按编号逐个或成组列出；
> - Scala demo 数量很多（135+ 编号文件 + `scala/akka/` 子目录），采用**主题分组**映射，更长的目录请直接看 [scala/SCALA_FP_ROADMAP.md](./scala/SCALA_FP_ROADMAP.md)、[scala/SCALA_FP_COVERAGE_OVERVIEW.md](./scala/SCALA_FP_COVERAGE_OVERVIEW.md)、[scala/SCALA_EFFECT_DEMOS.md](./scala/SCALA_EFFECT_DEMOS.md)、[scala/SCALA_FS2_DEMOS.md](./scala/SCALA_FS2_DEMOS.md)。
> - 很多 demo 是在**训练 FP 概念**（Functor / Monad / 类型类），并不直接对应商业场景；这类归入"**FP 概念训练**"一栏，不硬拗成行业场景。

---

## 🟣 Haskell（[haskell/](./haskell/)，共 26 个 demo + 1 个 roadmap）

| 场景（呼应 Part 1） | 对应 demo |
|---|---|
| **正确性 / 强类型（金融、编译器、区块链都吃这口）** | `07_TypesAndADT.hs`、`15_TypesAdvanced.hs`、`20_GenericsAndDerivingVia.hs`、`25_DependentTypesAndSingletons.hs` |
| **编译器 / DSL / 语言工具** | `10_ParserCombinators.hs`、`11_FreeMonadsAndDSL.hs`、`17_LambdaCalculusAndFix.hs`、`24_RecursionSchemes.hs` |
| **高可靠后端 / 数据管道** | `16_StreamingPipeline.hs`、`19_ExceptionsAndConcurrency.hs`、`21_EffectSystemPatterns.hs` |
| **并发 / STM（库内并发，区别于 BEAM 的 Actor）** | `06_ConcurrencySTM.hs`、`19_ExceptionsAndConcurrency.hs` |
| **测试 / 正确性保障（QuickCheck、状态机测试 —— 金融/协议场景常用）** | `12_QuickCheck.hs`、`22_FPBestPracticesAndStateMachineTests.hs` |
| **FP 概念训练（不直接对应行业场景）** | `01_PureAndLazy.hs`、`02_TypeClassAndMonad.hs`、`03_CurryAndCompose.hs`、`04_IOAndSideEffects.hs`、`05_StateAndReader.hs`、`08_MonadTransformers.hs`、`09_LensAndOptics.hs`、`13_ArrowAndProfunctor.hs`、`14_FoldableTraversable.hs`、`18_AlternativeAndMonadPlus.hs`、`23_Comonad.hs`、`26_AdvancedResearchDirections.hs` |

> 注：`haskell/13_ArrowAndProfnotor.hs` 与 `haskell/13_ArrowAndProfunctor.hs` 看起来像是重复/拼写差异，**本次不动它**，仅提及（守则 3）。

---

## 🔴 Scala（[scala/](./scala/)，135 个编号 demo + `scala/akka/` 子目录）

按主题聚类，每一组给一个**编号区间 + 代表文件**示例；详尽清单见 roadmap/overview 四份 md。

| 场景（呼应 Part 1） | 对应 demo 组 |
|---|---|
| **大数据 / 流处理（Scala 最大本命）** | FS2 流：`20_FS2Pipeline.scala`、`28_FS2StreamWorkflow.scala`、`34_FS2QueueWorker.scala`、`37_FS2TopicPubSub.scala`、`42_FS2ParEvalMap.scala`、`47_FS2MergeStreams.scala`、`62_FS2GroupWithin.scala`、`67_FS2PullLineDecoder.scala`、`72_FS2TopicHub.scala`、`77_FS2ChunkedFileProcessor.scala`、`82_FS2CsvImportPipeline.scala` |
| **高并发后端 API（Cats Effect + Http4s 栈）** | Http4s：`21_Http4sMiniService.scala`、`29_Http4sRoutes.scala`、`32_Http4sJsonApi.scala`、`38_Http4sBearerAuth.scala`、`43_Http4sErrorHandling.scala`、`48_Http4sAuthMiddleware.scala`、`58_Http4sClientRetry.scala`、`63_Http4sStreamingApi.scala`、`68_Http4sServerSentEvents.scala`、`73_Http4sWebSocketChat.scala`、`88_Http4sIdempotencyKey.scala` |
| **金融 / 事务 / 可靠消息（Outbox / Inbox / Idempotency）** | `86_~90_` 幂等；`91_~95_` 事务性 Outbox；`96_~100_` 事务性 Inbox；`114_` / `119_` / `124_` 事务仓储 |
| **Saga / CQRS / Event Sourcing（企业后端架构）** | Saga：`101_~105_`；投影/CQRS：`106_~115_`；Event Sourcing：`116_~120_`；Process Manager：`121_~125_`；ACL / Context Map：`126_~135_` |
| **数据库 / 数据工程** | Doobie：`78_~85_`、`94_`、`99_`、`104_`、`109_`、`114_`、`119_`、`124_`、`129_`、`134_` |
| **高并发 Actor / 集群（Akka 系，对标 Erlang/Elixir 场景）** | `scala/akka/01_ActorBasics.scala` ~ `scala/akka/18_ActorReceptionist.scala`（Actor、FSM、Persistence、Projection、Cluster Sharding、Akka HTTP 等） |
| **测试（MUnit / Cats Effect Suite —— 交付可靠性）** | `40_MUnitCatsEffectSuite.scala`、`45_MUnitHttp4sRouteSuite.scala`、`50_~`、`55_`、`60_`、`65_`、`70_`、`75_`、`80_`、`85_`、`90_`、`95_`、`100_`、`105_`、`110_`、`115_`、`120_`、`125_`、`130_`、`135_` |
| **FP 概念训练（不直接对应行业场景）** | `01_~19_`（高阶/模式匹配/不可变/递归/Lazy/类型类/Validated/ADT/Semigroup/Functor-App-Monad/Reader/State/IO/Resource/Concurrency 基础） |

---

## 🟠 Erlang（[erlang/](./erlang/)，27 个 demo + 1 个 `run.sh`）

| 场景（呼应 Part 1） | 对应 demo |
|---|---|
| **IM / 长连接 / 高并发（WhatsApp 式）** | `03_actor_model.erl`、`13_gen_tcp_echo_server.erl`、`14_selective_receive_mailbox.erl`、`22_ssl_and_tls.erl` |
| **电信级容错（OTP 监督树）** | `04_gen_server_counter.erl`、`05_supervisor_tree.erl`、`09_gen_statem_order_fsm.erl`、`15_link_monitor_trap_exit.erl`、`18_application_and_release.erl` |
| **消息中间件 / 发布订阅（RabbitMQ 式）** | `26_gen_event_pubsub.erl` |
| **分布式 / 跨节点（集群能力）** | `07_distributed_nodes.erl` |
| **持久化 / 内存表（电信计费、会话状态）** | `06_ets_and_state.erl`、`11_mnesia_transactional_store.erl`、`21_dets_and_disc_log.erl` |
| **热更新（7×24 不停机升级）** | `12_hot_code_upgrade.erl` |
| **运维 / 可观测（生产排障）** | `16_logger_and_formatter.erl`、`19_recon_observer_introspect.erl`、`23_bench_and_profile.erl`、`27_erl_trace_and_dbg.erl` |
| **与外部系统集成（C/Rust NIF、Port）** | `20_nif_and_port.erl` |
| **测试 / 质量** | `08_property_testing_proper.erl`、`17_common_test_ct.erl` |
| **工程化 / 对比参考** | `24_rebar3_project_skeleton.erl`、`25_elixir_vs_erlang.erl` |
| **FP 概念训练** | `01_pattern_matching.erl`、`02_higher_order.erl`、`10_binary_pattern_matching.erl` |

---

## 🟢 Elixir（[elixir/](./elixir/)，15 个 demo + `mix.exs` + `run.sh` + roadmap）

| 场景（呼应 Part 1） | 对应 demo |
|---|---|
| **高并发 Web（Phoenix / Plug，Discord 式）** | `10_phoenix_router_plug.exs` |
| **LiveView 实时 UI（取代部分前端 JS）** | `11_liveview_mental_model.exs` |
| **BEAM 并发 / 监督（继承 Erlang 的容错）** | `06_genserver_agent_task.exs`、`07_supervisor_registry.exs`、`08_task_supervisor_async_stream.exs` |
| **数据流 / 背压（Broadway / Flow / GenStage —— IoT、实时管道）** | `12_flow_genstage_broadway.exs` |
| **金融科技 / SaaS 后端（Ecto 事务 + Multi）** | `09_ecto_repo_changeset_multi.exs` |
| **可观测（Telemetry / OpenTelemetry —— 生产必备）** | `13_telemetry_opentelemetry.exs` |
| **元编程 / DSL（宏系统是 Elixir 一大卖点）** | `04_macros_intro.exs`、`05_macros_dsl_router.exs` |
| **测试 / 质量（ExUnit / Doctest / Mox / StreamData）** | `14_exunit_doctest_mox_streamdata.exs` |
| **工程化（Mix / Umbrella / Releases）** | `15_mix_umbrella_releases.exs`、`mix.exs`、`run.sh` |
| **FP 概念训练** | `01_basics_pipeline.exs`、`02_struct_protocol_behaviour.exs`、`03_with_result_flow.exs` |

---

## 怎么按场景挑 demo 读

- 想看 **"FP 能怎么救金融/编译器/区块链"** → 走 Haskell `07/10/11/12/15/22/25`。
- 想看 **"怎么用 FP 写一个真实的生产后端"** → 走 Scala 的 Http4s + FS2 + Doobie + Saga/CQRS 那几组（`21/28/78/101/116`）。
- 想看 **"怎么扛住百万长连接 + 永不宕机"** → 走 Erlang `03/04/05/13/15` + Elixir `06/07/08`。
- 想看 **"怎么写实时 Web"** → 走 Elixir `10/11/12`。
