# Scala 好玩的 SDK 和开源项目盘点

> 本文档整理 Scala 生态中**真正"好玩"或"工业级在用"**的 25 个项目，对应本目录 135 个 demo（[`01_HigherOrderFunctions.scala`](./01_HigherOrderFunctions.scala) ~ [`135_MUnitContextMapEndToEndSuite.scala`](./135_MUnitContextMapEndToEndSuite.scala) + [`akka/`](./akka) 子目录）打通"学完之后玩什么"的衔接。

Scala 的"好玩"主要来自两条线：**大数据 / 流处理主场**（Spark / Kafka / Akka 是整个 2014-2020 大数据时代的底层）和**纯 FP 工程化**（cats-effect / ZIO 两大生态把 Haskell 的抽象落地到 JVM 工业界）。它同时具备 JVM 的产业纵深和 ML 研究者喜欢的类型系统——这是 Java / Kotlin / Clojure 都不具备的位置。

下面按 8 类组织，每个项目都标了 **🌟 趣味点** 和 **🔧 上手难度**。

---

## 📊 一、大数据 / 流处理（Scala 的历史主场）

### 1. **Apache Spark** — 分布式计算引擎
- **GitHub**: `apache/spark`（40k+ stars）
- 🌟 **趣味点**：**全世界最大的 Scala 项目**，支撑了整个大数据时代；一行 `.map/.filter/.reduceByKey` 就能跑在几千台机器上
- Databricks 整个公司的技术栈核心
- 🔧 难度：⭐ 用 PySpark：零门槛；改源码：⭐⭐⭐⭐⭐
- **应用场景**：ETL、离线计算、ML pipeline、湖仓一体
- 🔗 配合本目录 [`20_FS2Pipeline.scala`](./20_FS2Pipeline.scala)、[`82_FS2CsvImportPipeline.scala`](./82_FS2CsvImportPipeline.scala) 食用（FS2 是 Spark 的纯 FP 平替思路）

### 2. **Apache Kafka** — 分布式消息队列
- **GitHub**: `apache/kafka`（28k+ stars）
- 🌟 LinkedIn 出品，**事实上的日志/事件总线标准**；Scala 写核心 broker，客户端多语言
- **应用场景**：事件驱动架构、CDC、数据中台
- 🔗 配合本目录 [`37_FS2TopicPubSub.scala`](./37_FS2TopicPubSub.scala)、[`91_CatsEffectOutboxCoordinator.scala`](./91_CatsEffectOutboxCoordinator.scala) 食用

### 3. **Apache Flink** — 流式计算
- **GitHub**: `apache/flink`（24k+ stars）
- 🌟 真·流优先（Spark Streaming 是微批），支持 exactly-once；阿里/字节大量在用
- **应用场景**：实时风控、实时推荐、CDC 流

### 4. **Akka / Pekko** — Actor 框架
- **GitHub**: `akka/akka`、`apache/pekko`（15k+ stars）
- 🌟 **Scala 的 Actor 工业标准**；Akka 2022 改商业协议后，社区 fork 出 Apache Pekko 继承衣钵
- **应用场景**：高并发后端、游戏服务器、集群分片
- 🔗 配合本目录 [`akka/`](./akka) 子目录食用（18 个 Akka demo 涵盖 Actor/Streams/Persistence/Cluster）

### 5. **Finagle / ZIO Grpc** — RPC 框架
- **GitHub**: `twitter/finagle`（9k+ stars）
- 🌟 **Twitter 早期技术栈的核心**，`Future[T]` 就是 Finagle 推广出来的；对 Netflix 的 Hystrix、Go 的 gRPC 影响深远
- **应用场景**：微服务 RPC、客户端侧负载均衡

---

## 🧩 二、cats 生态（纯 FP 工程化的主干）

### 6. **cats** — typeclass 标准库
- **GitHub**: `typelevel/cats`（5k+ stars）
- 🌟 Functor / Monad / Monoid / Semigroup 的 Scala 标准实现；**几乎所有纯 FP Scala 库都依赖它**
- 🔗 配合本目录 [`13_SemigroupAndMonoid.scala`](./13_SemigroupAndMonoid.scala)、[`14_FunctorApplicativeMonad.scala`](./14_FunctorApplicativeMonad.scala) 食用

### 7. **cats-effect** — IO / Fiber / Resource
- **GitHub**: `typelevel/cats-effect`（2.1k+ stars）
- 🌟 Scala 版的 Haskell `IO` monad；Fiber 调度器 + STM + Supervisor，是整个 Typelevel 栈的心脏
- 🔧 难度：⭐⭐⭐ 思维模型转变不小
- 🔗 配合本目录 [`17_IOBasics.scala`](./17_IOBasics.scala)、[`26_CatsEffectIOApp.scala`](./26_CatsEffectIOApp.scala)、[`36_CatsEffectDeferredRef.scala`](./36_CatsEffectDeferredRef.scala) 食用（本仓库几十个 `CatsEffect*` 都是它）

### 8. **fs2** — 纯函数式流
- **GitHub**: `typelevel/fs2`（2.4k+ stars）
- 🌟 对标 Haskell conduit，**真·背压**；管道式组合、资源安全、中断语义完备
- **应用场景**：流式 ETL、事件处理、WebSocket 流
- 🔗 配合本目录 [`20_FS2Pipeline.scala`](./20_FS2Pipeline.scala)、[`28_FS2StreamWorkflow.scala`](./28_FS2StreamWorkflow.scala)、[`34_FS2QueueWorker.scala`](./34_FS2QueueWorker.scala) 食用

### 9. **http4s** — 纯 FP 的 HTTP 框架
- **GitHub**: `http4s/http4s`（2.5k+ stars）
- 🌟 `Request => F[Response]` 就是一个函数；中间件就是函数组合；中规中矩地优雅
- 🔗 配合本目录 [`21_Http4sMiniService.scala`](./21_Http4sMiniService.scala)、[`29_Http4sRoutes.scala`](./29_Http4sRoutes.scala)、[`32_Http4sJsonApi.scala`](./32_Http4sJsonApi.scala) 食用（本仓库几十个 `Http4s*` 都是它）

### 10. **doobie** — JDBC 的 FP 封装
- **GitHub**: `typelevel/doobie`（2.2k+ stars）
- 🌟 SQL 保留手写（不是 ORM），但事务、流式读取、类型安全绑定都走 cats-effect
- 🔗 配合本目录 [`78_DoobieTransactorResource.scala`](./78_DoobieTransactorResource.scala)、[`79_DoobieRepositoryTagless.scala`](./79_DoobieRepositoryTagless.scala)、[`94_DoobieTransactionalOutbox.scala`](./94_DoobieTransactionalOutbox.scala) 食用

### 11. **circe** — JSON 编解码
- **GitHub**: `circe/circe`（2.6k+ stars）
- 🌟 基于 cats 的 JSON 库；派生 `Encoder/Decoder` 一行搞定；对标 Haskell 的 aeson
- 🔗 配合本目录 [`31_CirceJsonCodec.scala`](./31_CirceJsonCodec.scala) 食用

### 12. **skunk** — Postgres 纯协议客户端
- **GitHub**: `typelevel/skunk`（1.2k+ stars）
- 🌟 **不走 JDBC**，直接实现 Postgres 二进制协议；cats-effect + fs2 全家桶
- **应用场景**：需要流式查询 + 真·非阻塞的 Postgres 应用

---

## ⚡ 三、ZIO 生态（cats-effect 的"现代替代"）

### 13. **ZIO** — `ZIO[R, E, A]` 三参数 effect
- **GitHub**: `zio/zio`（4.2k+ stars）
- 🌟 把 Reader + Either + IO **一次打包**；比 cats-effect 更"面向应用"、更好上手
- 对"依赖注入/环境传递"做得最好的 FP effect 系统
- **应用场景**：新项目首选（特别是团队 FP 经验不深时）

### 14. **ZIO HTTP / ZIO Streams / ZIO Schema / Caliban**
- **GitHub**: `zio/zio-http`、`ghostdogpr/caliban`（2.3k+ stars）
- 🌟 **Caliban** 是 Scala 里最好的 GraphQL 服务器；ZIO HTTP 性能直逼 Rust axum
- **应用场景**：全栈 ZIO 微服务

---

## 🔬 四、编译器 / 类型系统研究

### 15. **Dotty / Scala 3** — 新一代 Scala 编译器
- **GitHub**: `scala/scala3`（6k+ stars）
- 🌟 **TASTy** 中间表示、union type、交叉类型、`given/using` 替代 implicit；Martin Odersky 主导
- 读它你会理解"类型系统演化"这件事怎么真的发生
- 🔧 难度：⭐⭐⭐⭐⭐

### 16. **Shapeless** — 泛型编程 / HList
- **GitHub**: `milessabin/shapeless`（3.3k+ stars）
- 🌟 把 Haskell 里 `Generic` + `Data.HList` 的玩法搬到 Scala；自动派生类型类的基石
- **应用场景**：ORM / JSON / Protobuf 的自动派生

### 17. **Chisel** — 用 Scala DSL 写芯片
- **GitHub**: `chipsalliance/chisel`（3.8k+ stars）
- 🌟 **UC Berkeley 用它设计 RISC-V CPU**；把硬件电路当 Scala 表达式写
- Scala 出圈最硬核的案例——它让"硬件设计"变成了"函数式编程"
- 🔧 难度：⭐⭐⭐⭐ 需要懂数字电路

### 18. **Metals / Bloop** — LSP / 增量编译
- **GitHub**: `scalameta/metals`（2.2k+ stars）、`scalacenter/bloop`
- 🌟 VSCode/vim 里的 Scala 语言服务器；让 Scala 编辑体验和 IntelliJ 拉近
- **应用场景**：所有写 Scala 的人

---

## 🤖 五、ML / 科学计算

### 19. **Spark MLlib** — 分布式机器学习
- 🌟 Spark 自带的 ML 库；特征工程 + 常见模型 + pipeline 抽象
- **应用场景**：大规模特征工程、线性模型训练

### 20. **Breeze / Smile** — 数值计算 / 统计学习
- **GitHub**: `scalanlp/breeze`（3.5k+ stars）、`haifengl/smile`（6.1k+ stars）
- 🌟 Breeze 对标 numpy，Smile 覆盖常见 ML 算法；Java/Scala 生态里最完整的两个
- **应用场景**：非 Spark 场景下的数值计算

---

## 🌐 六、Web / 前端（日常用得着）

### 21. **Play Framework** — 类 Rails 的 Web 框架
- **GitHub**: `playframework/playframework`（12k+ stars）
- 🌟 LinkedIn 在用；Scala/Java 双语言；约定优于配置
- **应用场景**：企业级 Web 后端

### 22. **Tapir** — API 描述 → 生成一切
- **GitHub**: `softwaremill/tapir`（1.4k+ stars）
- 🌟 **一份 endpoint 定义**，可以生成：http4s / akka-http / ZIO HTTP / OpenAPI / client SDK
- **应用场景**：契约驱动开发、自动化 API 文档

### 23. **Scala.js + Laminar** — Scala 写前端
- **GitHub**: `scala-js/scala-js`（4.8k+ stars）、`raquo/Laminar`（1.2k+ stars）
- 🌟 Scala 编译到 JS；**Laminar** 是类 React 但基于 FRP 的响应式前端框架
- 前端用户量不大但忠实度极高（用过就回不去）
- **应用场景**：前后端同语言的全栈 Scala 项目

---

## 🔐 七、区块链

### 24. **Ergo** — UTXO 智能合约平台
- **GitHub**: `ergoplatform/ergo`（600+ stars）
- 🌟 **纯 Scala 实现**；UTXO 模型 + 图灵完备合约；学术味很浓
- **应用场景**：小众但做得扎实的智能合约平台
- 🔗 呼应 Haskell 的 [`../haskell/45_UTXOLedger.hs`](../haskell/45_UTXOLedger.hs)

---

## 🛠 八、构建 / 测试 / 开发工具

### 25. **sbt / mill / scala-cli + MUnit / ScalaCheck**
- **GitHub**: `sbt/sbt`（4.7k+ stars）、`com-lihaoyi/mill`、`VirtusLab/scala-cli`、`scalameta/munit`、`typelevel/scalacheck`
- 🌟 **sbt** 是老牌；**mill** 是 lihaoyi 的简化替代；**scala-cli** 是"写 Scala 像写 Python"的最新尝试
- **MUnit** + **ScalaCheck** 是测试双雄：前者极简，后者是 QuickCheck 的 Scala 版
- 🔗 配合本目录 [`40_MUnitCatsEffectSuite.scala`](./40_MUnitCatsEffectSuite.scala)、[`50_MUnitAuthMiddlewareSuite.scala`](./50_MUnitAuthMiddlewareSuite.scala) 食用（本仓库几十个 `MUnit*` 都是它）

---

## 🎯 应用场景总结（对比 Haskell/Rust/Erlang）

| 场景 | 推荐 | 为什么 |
|---|---|---|
| **大数据 / 数据湖 / 离线计算** | ✅ Scala | Spark 一家独大 |
| **流式计算 / 实时数仓** | ✅ Scala | Kafka + Flink + fs2 三件套 |
| **纯 FP 业务后端（需要 JVM 生态）** | ✅ Scala | cats-effect / ZIO 比 Haskell 更容易上生产 |
| **微服务 / 高并发 Actor 系统** | ✅ Scala | Akka/Pekko 经过 10+ 年工业锤炼 |
| **芯片 / 硬件 DSL** | ✅ Scala | Chisel 独此一家 |
| **CLI 工具 / 系统级** | ❌ → Rust | JVM 启动慢是硬伤 |
| **电信级高可用 / 千万长连接** | ❌ → Erlang/Elixir | BEAM 的 actor 调度器更适合 |
| **类型理论极客玩具** | ❌ → Haskell/Idris | Scala 是工程优先，不是纯理论 |

---

## 💡 一句话总结

> **Scala 是"让 Haskell 的抽象能在 JVM 生态里活下来"的那门语言。**

它吃下了两个没人能同时做到的 niche：**大数据基础设施的主场**（Spark/Kafka/Akka）+ **纯 FP 的工业落地**（cats-effect/ZIO）。

如果只挑 3 个最能体现 Scala 独特魅力的：

1. **Spark** — 你用过大数据的话，就已经间接用过 Scala 了
2. **cats-effect + fs2 + http4s + doobie + circe 全家桶** — 看完你会理解"为什么 Haskell 的直觉可以在生产里活下来"
3. **Chisel** — Scala 出圈最硬核的案例，用 FP 写 RISC-V CPU

---

## 🎯 推荐学习路径（对照本目录 135 个 demo）

| 已掌握 | 推荐玩什么 |
|---|---|
| 完成 01~16（FP 基础 + typeclass + Reader/State） | **cats** 官方教程；**circe** 解析 JSON |
| 完成 17~30（IO + Resource + 并发 + http4s） | **cats-effect 3** 官方文档；**http4s** 写 REST API |
| 完成 31~60（JSON/Client/Retry/Race/中间件） | **Tapir** 生成 OpenAPI；**ZIO HTTP** 对比体验 |
| 完成 61~90（SSE/WebSocket/Doobie/Idempotency） | **skunk** 替 doobie；**Caliban** 写 GraphQL |
| 完成 91~135（Outbox/Saga/CQRS/DDD） | **Akka/Pekko Persistence** 做 ES 生产级；**Spark Streaming** 写实时管道 |
| [`akka/`](./akka) 18 个 Actor demo | **Akka Cluster Sharding** 集群分片；**Pekko** 迁移 |

---

## 🔗 相关文档

- 学习路线：[`SCALA_FP_ROADMAP.md`](./SCALA_FP_ROADMAP.md)（135 demo 详解）
- 实践与资源：[`SCALA_PRACTICE_AND_RESOURCES.md`](./SCALA_PRACTICE_AND_RESOURCES.md)
- FS2 demo 总览：[`SCALA_FS2_DEMOS.md`](./SCALA_FS2_DEMOS.md)
- cats-effect demo 总览：[`SCALA_EFFECT_DEMOS.md`](./SCALA_EFFECT_DEMOS.md)
- 跨语言对照：[`../LANGUAGE_COMPARISON.md`](../LANGUAGE_COMPARISON.md)
- 商业场景画像：[`../language_comparison_2.md`](../language_comparison_2.md)
- Haskell 同款盘点：[`../haskell/HASKELL_ECOSYSTEM.md`](../haskell/HASKELL_ECOSYSTEM.md)
- Rust 同款盘点：[`../rust/RUST_ECOSYSTEM.md`](../rust/RUST_ECOSYSTEM.md)
- Erlang 同款盘点：[`../erlang/ERLANG_ECOSYSTEM.md`](../erlang/ERLANG_ECOSYSTEM.md)
- Elixir 同款盘点：[`../elixir/ELIXIR_ECOSYSTEM.md`](../elixir/ELIXIR_ECOSYSTEM.md)
