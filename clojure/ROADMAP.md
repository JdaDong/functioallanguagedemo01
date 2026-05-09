# Clojure 40+ Demo 学习路线图

> 对齐 [`haskell/HASKELL_FP_ROADMAP.md`](../haskell/HASKELL_FP_ROADMAP.md) 的粒度。
> 状态图例：⏳ 待做 / 🚧 进行中 / ✅ 完成。当前全部 ⏳。

---

## 🧱 阶段一：Lisp 基础（demo 01-08）

| # | Demo | 卖点 | 状态 |
|---|---|---|---|
| 01 | basics_and_collections | list / vector / map / set 四件套 | ⏳ |
| 02 | immutable_data_structures | `assoc` / `conj` / `update`，展示 O(log32 N) 持久化 | ⏳ |
| 03 | higher_order_and_transducers | `map` / `filter` / `reduce` → `transduce` 零中间集合 | ⏳ |
| 04 | destructuring | `[a b & rest]` / `{:keys [name age]}` 深度解构 | ⏳ |
| 05 | recur_and_loop | `recur` 尾递归、`loop` 局部递归 | ⏳ |
| 06 | lazy_seq | `lazy-seq` / `take` / 无限序列 | ⏳ |
| 07 | multimethods | `defmulti` + `defmethod`，开放分派 | ⏳ |
| 08 | protocols_and_records | `defprotocol` / `defrecord`，对标 Haskell typeclass | ⏳ |

---

## 🧬 阶段二：宏系统（demo 09-14，Clojure 的灵魂）

| # | Demo | 卖点 | 状态 |
|---|---|---|---|
| 09 | macros_intro | `defmacro` + quote / unquote / splicing | ⏳ |
| 10 | macros_anaphoric | 反卫生宏（`->` `->>` `as->` 的实现思路） | ⏳ |
| 11 | macros_dsl | 最小 SQL DSL，展示代码即数据 | ⏳ |
| 12 | macros_state_machine | 状态机宏，对标 Elixir `05_macros_dsl_router.exs` | ⏳ |
| 13 | reader_macros | `#(...)` / `#_` / `#?` 读时宏 | ⏳ |
| 14 | macro_hygiene | gensym / `#` 后缀，避免变量捕获 | ⏳ |

---

## ⚡ 阶段三：并发模型（demo 15-21）

| # | Demo | 卖点 | 状态 |
|---|---|---|---|
| 15 | atoms_and_swap | `atom` + `swap!`，单值无锁并发 | ⏳ |
| 16 | refs_and_stm | `ref` + `dosync`，软件事务内存（对标 Haskell STM） | ⏳ |
| 17 | agents_async | `agent` + `send` / `send-off`，异步状态更新 | ⏳ |
| 18 | futures_and_delay | `future` / `delay` / `promise`，惰性与异步 | ⏳ |
| 19 | core_async_channels | `go` + `chan` + `<!` / `>!`，CSP 风格 | ⏳ |
| 20 | core_async_pipeline | 多阶段 pipeline，对标 Go scheduler | ⏳ |
| 21 | reducers_parallel | `clojure.core.reducers`，fork-join 并行 | ⏳ |

---

## 🧰 阶段四：数据与类型（demo 22-28）

| # | Demo | 卖点 | 状态 |
|---|---|---|---|
| 22 | spec_basic | `clojure.spec.alpha` 定义与验证 | ⏳ |
| 23 | spec_generators | property-based testing（对标 QuickCheck） | ⏳ |
| 24 | malli_schema | 替代方案：malli（更现代的 spec） | ⏳ |
| 25 | data_oriented_programming | 官方推荐的"数据驱动"代替 OOP | ⏳ |
| 26 | edn_format | EDN 格式（Clojure 版 JSON）读写 | ⏳ |
| 27 | transit_format | Transit（跨语言高性能格式） | ⏳ |
| 28 | schema_evolution | spec + 版本化演进策略 | ⏳ |

---

## 🏭 阶段五：实战项目（demo 29-40+）

| # | Demo | 行业映射 | 状态 |
|---|---|---|---|
| 29 | ring_handler | Ring 中间件（对标 `scala/21_Http4sMiniService.scala`） | ⏳ |
| 30 | compojure_router | 路由 DSL | ⏳ |
| 31 | reitit_data_router | 数据驱动路由（新一代） | ⏳ |
| 32 | datomic_mini | 最小版 Datomic：事实存储 + 时间旅行查询 | ⏳ |
| 33 | datalog_query | 内嵌 Datalog 查询引擎（呼应 Metabase） | ⏳ |
| 34 | metabase_style_pipeline | 自助 BI 查询构造（简化版 Metabase MBQL） | ⏳ |
| 35 | reagent_mental_model | Clojurescript + Reagent 心智模型 | ⏳ |
| 36 | re_frame_event_loop | re-frame 架构（对标 Redux） | ⏳ |
| 37 | option_pricing_dsl | 金融 DSL（对标 `haskell/42_OptionPricingDSL.hs`） | ⏳ |
| 38 | utxo_ledger | 区块链账本（对标 `haskell/45_UTXOLedger.hs`） | ⏳ |
| 39 | csv_to_json_etl | ETL（对标 `haskell/48_CsvToJsonETL.hs`） | ⏳ |
| 40 | nubank_style_event_sourcing | 事件溯源缩影（呼应 Nubank 架构） | ⏳ |

---

## 📦 产出形式约定（分档）

和仓库其他语言（Haskell/Elixir/Erlang）一致，**零依赖的 demo 用散文件**，有外部依赖的 demo 才上项目结构：

| 档位 | 适用 demo | 形态 | 运行命令 |
|---|---|---|---|
| **散文件档** | 01-18（纯语言特性，无外部依赖） | `NN_name.clj` 单文件 | `clojure -M clojure/NN_name.clj` |
| **项目档** | 19+（需要 core.async / spec / reagent / datomic / …） | 子目录 + `deps.edn` | `clojure -M:run`（各自 README 说明） |

切换点：demo 19（`core_async_channels`）时在 `clojure/` 下建根 `deps.edn`，声明共享依赖和 alias。

每个 demo 顶端必须有：
- 一段功能介绍
- `;; 运行：clojure -M ...` 注释
- 外部依赖必须显式声明（散文件档零依赖；项目档在 `deps.edn` 里）

---

## ✅ 启动条件

环境已就绪：`clojure --version` ✅ 1.12.0.1517。
下一步可直接开工阶段 2。
