# Clojure 好玩的 SDK 和开源项目盘点

> 本文档整理 Clojure / ClojureScript 生态中**真正"好玩"或"工业级在用"**的 25 个项目，对应本目录已完成的 demo（[`01_basics_and_collections.clj`](./01_basics_and_collections.clj) ~ [`08_protocols_and_records.clj`](./08_protocols_and_records.clj)）和后续规划（见 [`ROADMAP.md`](./ROADMAP.md)）打通"学完之后玩什么"的衔接。

Clojure 是个"被低估的工业巨人"——你用过的 Metabase、Datomic、CircleCI、Nubank 银行 App、LogSeq 笔记软件背后都有它。它和 Haskell/Scala 完全不同：**Haskell/Scala 玩的是静态类型，Clojure 玩的是"REPL-driven 开发 + 不可变数据 + 真正的 Lisp 宏"**——同一个程序员，用 Clojure 通常比用 Java 少写 5-10 倍代码，而且改一行可以立刻在跑着的进程里看到效果。

下面按 6 类组织，每个项目都标了 **🌟 趣味点** 和 **🔧 上手难度**。

---

## 💾 一、数据库 / 数据建模（Clojure 的招牌区）

### 1. **Datomic** — Rich Hickey 亲手设计的"事实型"数据库
- **GitHub**: `cognitect-labs/`（Datomic Pro 闭源，Datomic Free 公开）
- 🌟 **趣味点**：把数据库当成"不可变的事实流水"，**所有历史都可查询**，可以"回到过去看当时数据库长啥样"
- 内置 Datalog 查询语言（比 SQL 更适合"图状关系"）
- 🔧 难度：⭐⭐⭐⭐ 思维模型大转变
- **应用场景**：金融审计、医疗记录、任何"必须保留历史" 的领域
- **谁在用**：Nubank（收购了 Cognitect）、Walmart、HCA Healthcare
- 🔗 ROADMAP demo 32 (`datomic_mini`) 会做缩影

### 2. **XTDB** — 双时态数据库（Datomic 思路的开源版）
- **GitHub**: `xtdb/xtdb`（2.6k+ stars）
- 🌟 同时记录"事务时间 + 业务有效时间"两个时间轴，专门为合规场景设计
- **应用场景**：金融衍生品记账、保险政策追溯、GDPR 合规

### 3. **Metabase** — 开源 BI 工具
- **GitHub**: `metabase/metabase`（38k+ stars）
- 🌟 **完全用 Clojure 写的开源 BI**，支持 30+ 数据源，自助查询、可视化、Dashboard 一站式
- 自定义查询语言 MBQL（Metabase Query Language）就是 Clojure 数据结构
- **应用场景**：内部数据分析平台、自助 BI；公司里"非工程师查数据"
- 🔗 ROADMAP demo 33-34 会做 Datalog + MBQL 思路的缩影

### 4. **Datascript** — 浏览器里的 Datomic
- **GitHub**: `tonsky/datascript`（5.4k+ stars）
- 🌟 把 Datomic 的核心 API（Datalog 查询 + 不可变事实存储）搬到内存中，可以**跑在浏览器里**
- **应用场景**：LogSeq、Roam Research 这类"个人知识库"应用的核心引擎

---

## 🌐 二、Web / 后端

### 5. **Ring** — Clojure 的 Rack/WSGI
- **GitHub**: `ring-clojure/ring`（3.7k+ stars）
- 🌟 把 HTTP 请求/响应建模成普通 map；中间件就是 `(handler -> handler)` 的函数
- 简洁到 50 行就能写完一个 Web 服务
- **应用场景**：所有 Clojure Web 服务的底层
- 🔗 ROADMAP demo 29 (`ring_handler`) 会写最小版

### 6. **Reitit** — 数据驱动的路由
- **GitHub**: `metosin/reitit`（1.3k+ stars）
- 🌟 路由表是普通数据（不是函数注册表），可以序列化、可以反射、可以运行时改
- 性能也是 Clojure 圈最快之一
- 🔗 ROADMAP demo 31 (`reitit_data_router`) 会做缩影

### 7. **Pedestal** — 异步 Web 服务
- **GitHub**: `pedestal/pedestal`（1.5k+ stars）
- 🌟 Cognitect 出品，**interceptor 模式**（比 middleware 更灵活：可以中断、可以重排）
- **应用场景**：高吞吐 API 网关

### 8. **Compojure** — 经典路由 DSL
- **GitHub**: `weavejester/compojure`（4k+ stars）
- 🌟 老牌路由 DSL，Ring 之上的轻量包装；很多老项目还在用
- 新项目建议直接上 Reitit
- 🔗 ROADMAP demo 30 (`compojure_router`)

---

## ⚡ 三、并发 / 异步

### 9. **core.async** — Clojure 版 Go Channels
- **GitHub**: `clojure/core.async`（1.6k+ stars）
- 🌟 把 Go 的 CSP 模型搬到 Clojure：`go` 块 + `chan` + `<!`/`>!`；**关键是它和 transducer 无缝集成**
- **应用场景**：复杂事件流、Reactive 后台任务、ClojureScript 里替代 Promise
- 🔗 ROADMAP demo 19-20 会展开（也是切换到 `deps.edn` 项目档的起点）

### 10. **Manifold** — 跨执行模型的统一抽象
- **GitHub**: `clj-commons/manifold`（1.4k+ stars）
- 🌟 把 `Future`/`Promise`/`Stream` 包成一致接口，可以从同步/异步/反应式之间无缝切换
- **应用场景**：金融风控管道（Aphelion 等公司）

### 11. **Reducers** — 并行 fold/map
- 🌟 Clojure 自带库，基于 Java fork-join，把"集合操作"拆到多核
- 🔗 ROADMAP demo 21 (`reducers_parallel`)

---

## 🎨 四、前端 / UI（ClojureScript）

### 12. **Reagent** — React 的 Clojure 包装
- **GitHub**: `reagent-project/reagent`（5.1k+ stars）
- 🌟 **比 React 还简洁**：组件就是 `(fn [props] [:div ...])`，state 用 atom
- **应用场景**：CircleCI 前端、SaaS dashboard
- 🔗 ROADMAP demo 35 (`reagent_mental_model`)

### 13. **re-frame** — Reagent 上的"Redux"
- **GitHub**: `day8/re-frame`（5.4k+ stars）
- 🌟 实际上 **Redux 的设计灵感来自 re-frame 和 Elm**；比 Redux 简洁、调试器更强
- **应用场景**：复杂 SPA、协作工具
- 🔗 ROADMAP demo 36 (`re_frame_event_loop`)

### 14. **shadow-cljs** — ClojureScript 现代构建
- **GitHub**: `thheller/shadow-cljs`（3.4k+ stars）
- 🌟 ClojureScript 的"webpack"，无缝集成 npm 生态
- **应用场景**：所有现代 ClojureScript 项目

### 15. **Helix** — Reagent 的"现代版"（直接用 hooks）
- **GitHub**: `lilactown/helix`（800+ stars）
- 🌟 直接对接 React hooks，不再走 Reagent 的 atom 抽象；更贴近原生 React 心智
- **应用场景**：和 React 团队混编的项目

---

## 🛠 五、工具 / 测试 / 开发体验

### 16. **Leiningen** / **Clojure CLI (deps.edn)** — 构建工具双雄
- **GitHub**: `technomancy/leiningen`（6.7k+ stars）
- 🌟 **Lein 老牌**（项目文件 `project.clj` 本身是 Clojure 数据）；**Clojure CLI** 是官方现代选择，更轻量
- 本目录从 demo 19 起会用 `deps.edn` 项目档（而非 Lein）

### 17. **Clojure Spec** + **Malli** — 数据验证 / 文档 / 测试一体化
- **GitHub**: `metosin/malli`（2k+ stars）
- 🌟 用普通数据描述 schema，自动得到：验证、文档、property-based 生成器、coercion
- **应用场景**：API contract、表单验证、property test
- 🔗 ROADMAP demo 22-24 会展开

### 18. **Calva** — VS Code 的 Clojure 插件
- **GitHub**: `BetterThanTomorrow/calva`（1k+ stars）
- 🌟 把 VS Code 变成全功能 Clojure IDE（REPL/evaluation/inline display）
- **应用场景**：所有 Clojure 开发者必装

### 19. **Cursive** — IntelliJ 上的 Clojure 插件
- 🌟 商业插件，被认为是 Clojure 最好的 IDE 体验之一
- **应用场景**：从 Java/Kotlin 转 Clojure 的团队

### 20. **clj-kondo** — 静态 Lint
- **GitHub**: `clj-kondo/clj-kondo`（2k+ stars）
- 🌟 比官方编译器更早抓出问题；Borkdude 一人神作
- **应用场景**：所有 Clojure 项目的 CI

---

## 🌟 六、明星应用 / 创意项目（你可能用过却不知道）

### 21. **LogSeq** — 知识管理 / 双链笔记
- **GitHub**: `logseq/logseq`（35k+ stars）
- 🌟 **完全用 ClojureScript 写**，对标 Roam Research / Obsidian；图谱视图、双向链接、本地优先
- **应用场景**：个人知识管理（你这种文档密集型工作流应该会喜欢）

### 22. **CircleCI** — CI/CD 平台
- 🌟 后端 + 前端早期都是 Clojure；规模化运行 Clojure 在生产的代表案例
- **应用场景**：见 GitHub 上几乎所有 OSS 项目

### 23. **Nubank** — 巴西最大数字银行
- 🌟 **全栈 Clojure**（核心账务、风控、移动 BFF）；7000 万+ 用户级别的 Clojure 部署
- 收购了 Cognitect（Clojure 母公司）+ Datomic 团队
- 🔗 ROADMAP demo 40 (`nubank_style_event_sourcing`) 会做风格缩影

### 24. **Roam Research / Athens Research** — 双链笔记
- 🌟 Roam 闭源（ClojureScript），Athens 是其开源仿制
- **应用场景**：研究人员、知识工作者

### 25. **Clojurists Together** + **Babashka**
- **GitHub**: `babashka/babashka`（6.5k+ stars）
- 🌟 **Babashka** = Clojure 的脚本运行时，启动 ms 级（绕开 JVM 慢启动）；写 Bash 替代品最佳选择
- Borkdude 出品（同 clj-kondo 作者），是这两年 Clojure 圈最有活力的项目
- **应用场景**：CI 脚本、CLI 工具、运维自动化

---

## 🎯 应用场景总结（对比 Haskell / Scala / Erlang）

| 场景 | 推荐 | 为什么 |
|---|---|---|
| **REPL-driven 快速迭代后端** | ✅ Clojure | 边运行边改的体验 JVM 上无人能敌 |
| **数据驱动的内部工具 / BI** | ✅ Clojure | Metabase 的范式，"代码即数据"天然贴合 |
| **金融 / 审计 / 合规系统** | ✅ Clojure | Datomic / XTDB 的时间旅行查询是杀手锏 |
| **复杂前端 SPA（不想被 React 锁死）** | ✅ ClojureScript | Reagent + re-frame，少写一半代码 |
| **CLI 工具 / 运维脚本** | ✅ Babashka | ms 级启动，写脚本比 Bash 强 100 倍 |
| **类型驱动的金融建模** | ❌ → Haskell | 动态类型不擅长 |
| **JVM 大数据 / Spark** | ❌ → Scala | Spark 是 Scala 主场 |
| **千万级长连接 / 电信级 SLA** | ❌ → Erlang/Elixir | BEAM 的 actor 调度更适合 |

---

## 💡 一句话总结

> **Haskell 用类型证明你不会出错，Erlang 让出错也不挂；Clojure 让你"边运行边改"，把"代码就是数据"做到极致。**

如果只挑 3 个最能体现 Clojure 独特魅力的：

1. **Metabase / LogSeq** — 你每天可能在用 Clojure 写的东西却不知道
2. **Datomic + Datalog** — "时间维度的数据库"颠覆 SQL 思维
3. **Babashka** — 30 行脚本干掉一堆 Bash + Python，启动 ms 级

---

## 🎯 推荐学习路径（对照本目录 ROADMAP）

| 已掌握 | 推荐玩什么 |
|---|---|
| 完成 01-08（基础 + multimethod + protocol） | **Babashka** 写脚本；**Calva** 配 VS Code |
| 完成 09-14（宏系统） | 读 **clj-kondo** 源码体会 Lisp 宏威力；**Hiccup** 模板 |
| 完成 15-21（并发 + STM） | **core.async** 写复杂事件流；**Manifold** 看异步抽象 |
| 完成 22-28（Spec / Malli / 数据） | **Metabase** 跑起来读 MBQL 实现 |
| 完成 29-40+（Web / Datomic / Reagent） | **LogSeq** 自部署；**Datomic Free** 玩时间旅行 |

---

## 🔗 相关文档

- 学习路线：[`ROADMAP.md`](./ROADMAP.md)（40+ demo 详解）
- 跨语言对照：[`../LANGUAGE_COMPARISON.md`](../LANGUAGE_COMPARISON.md)
- 商业场景画像：[`../language_comparison_2.md`](../language_comparison_2.md)
- 其他 FP 语言：[`../OTHER_FP_LANGUAGES.md`](../OTHER_FP_LANGUAGES.md)
- Haskell 同款盘点：[`../haskell/HASKELL_ECOSYSTEM.md`](../haskell/HASKELL_ECOSYSTEM.md)
- Scala 同款盘点：[`../scala/SCALA_ECOSYSTEM.md`](../scala/SCALA_ECOSYSTEM.md)
- Rust 同款盘点：[`../rust/RUST_ECOSYSTEM.md`](../rust/RUST_ECOSYSTEM.md)
- Erlang 同款盘点：[`../erlang/ERLANG_ECOSYSTEM.md`](../erlang/ERLANG_ECOSYSTEM.md)
- Elixir 同款盘点：[`../elixir/ELIXIR_ECOSYSTEM.md`](../elixir/ELIXIR_ECOSYSTEM.md)
