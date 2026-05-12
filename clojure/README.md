# Clojure 学习目录

> **状态：主线 demo 01-50 全部完成 ✅，阶段七综合项目 demo 51 ✅**（阶段一到五覆盖工业 90% 使用面，阶段六深度补遗，阶段七综合项目）。完整学习路径见 [`ROADMAP.md`](./ROADMAP.md)；分阶段总结见下方「执行状态」表。

## 为什么 Clojure 值得单独开一档

本仓库 Scala 覆盖了 JVM 上的**静态类型 FP**，但完全没覆盖 **JVM 上的动态类型 Lisp 方言**：

- **Metabase**（开源 BI，GitHub 35k+ star）后端主力
- **Datomic**（不可变数据库，Cognitect 出品）
- **Nubank**（巴西最大数字银行，全栈 Clojure）
- **Walmart / CircleCI / Funding Circle** 核心交易系统

Clojure 的独特卖点（Scala/Haskell 都给不了）：
1. **Persistent data structures 一等公民**：`assoc` / `conj` / `update` 全是 O(log32 N) 的持久化结构
2. **宏系统（真 Lisp 宏）**：代码即数据（homoiconic），编译期任意变形
3. **STM 一等公民**：`ref` + `dosync`，比 Haskell STM 更早进入工业
4. **REPL-driven 开发**：**边运行边改的交互式体验，JVM 上无人能敌**

## 工具链要求

> ✅ 本机已安装：
> ```
> Clojure CLI 1.12.0.1517
> OpenJDK 26
> ```

本目录采用**分档形态**（与仓库其他语言风格一致）：

- **阶段一（demo 01-18，纯语言特性）**：散文件 `NN_name.clj`，`clojure -M` 直接跑，零依赖
- **阶段二起（demo 19+，需 core.async / spec / reagent 等）**：切换到 `deps.edn` 项目结构

## 目录结构

50 个 demo 实际形态（散文件 32 个 + 项目档 18 个）：

```
clojure/
├── README.md                       ← 本文件
├── ROADMAP.md                      ← 50 demo 学习路径（已全部 ✅）
├── CLOJURE_ECOSYSTEM.md            ← 生态盘点
├── STAGE_2_SUMMARY.md              ← 阶段二总结（宏）
├── STAGE_4_SUMMARY.md              ← 阶段四总结（spec/malli/EDN/Transit）
├── STAGE_5_PART1_SUMMARY.md        ← 阶段五 Web 三件套
├── STAGE_5_PART2_SUMMARY.md        ← 阶段五 数据/查询
├── STAGE_5_PART3_SUMMARY.md        ← 阶段五 前端心智 + 综合实战
├── STAGE_6_SUMMARY.md              ← 阶段六 深度补遗
├── STAGE_7_SUMMARY.md              ← 阶段七 电商分析后台综合项目
│
├── 01_basics_and_collections.clj           ─┐
├── 02_immutable_data_structures.clj         │
├── 03_higher_order_and_transducers.clj      │
├── 04_destructuring.clj                     │
├── 05_recur_and_loop.clj                    │
├── 06_lazy_seq_and_infinite.clj             │ 散文件 32 个
├── 07_multimethods.clj                      │ 零依赖或仅 std lib
├── 08_protocols_and_records.clj             │ 跑：clojure -M clojure/NN_xxx.clj
├── 09_macros_intro.clj                      │
├── 10_macros_anaphoric.clj                  │
├── 11_macros_dsl.clj                        │
├── 12_macros_state_machine.clj              │
├── 13_reader_macros.clj                     │
├── 14_macro_hygiene.clj                     │
├── 15_atoms_and_state.clj                   │
├── 16_refs_and_stm.clj                      │
├── 17_agents_async.clj                      │
├── 18_futures_and_delay.clj                 │
├── 21_reducers_parallel.clj                 │
├── 22_spec_basic.clj                        │
├── 25_data_oriented_programming.clj         │
├── 26_edn_format.clj                        │
├── 35_reagent_mental_model.clj              │
├── 36_re_frame_event_loop.clj               │
├── 37_option_pricing_dsl.clj                │
├── 38_utxo_ledger.clj                       │
├── 40_nubank_style_event_sourcing.clj       │
├── 44_transducers_advanced.clj              │
├── 47_macros_deep.clj                       │
├── 48_metadata_protocols.clj                │
├── 49_reducers_fold.clj                     │
├── 50_java_interop_advanced.clj            ─┘
│
├── 19_core_async_channels/         ─┐
├── 20_core_async_pipeline/          │
├── 23_spec_generators/              │
├── 24_malli_schema/                 │
├── 27_transit_format/               │
├── 28_schema_evolution/             │
├── 29_ring_handler/                 │  项目档 18 个
├── 30_compojure_router/             │  各自 deps.edn 拉外部依赖
├── 31_reitit_data_router/           │  跑：cd 进子目录 + clojure -M:run
├── 32_datomic_mini/                 │
├── 33_datalog_query/                │
├── 34_metabase_style_pipeline/      │
├── 39_csv_to_json_etl/              │
├── 41_core_async_pipeline_async/    │
├── 42_core_async_pubsub_mix/        │
├── 43_core_async_error_dlq/         │
├── 45_spec_advanced/                 │
├── 46_malli_advanced/                │
└── 51_ecommerce_analytics/          ─┘  阶段七：综合项目
```

> 规则：阶段一到二的纯语言特性 demo 全是散文件；从 demo 19 开始凡需要 core.async / spec.gen / malli / ring / datascript / reagent 等外部依赖的，就单独建子目录用 `deps.edn` 管。例外：35-38、40、44、47-50 即便属于较后阶段，因为零依赖（或只用标准库），仍保留散文件形态。

## 执行状态

| 阶段 | 范围 | 状态 | 产物 / 总结档 |
|---|---|---|---|
| 盘点 + roadmap | — | ✅ | `README.md`、[`ROADMAP.md`](./ROADMAP.md)、[`CLOJURE_ECOSYSTEM.md`](./CLOJURE_ECOSYSTEM.md) |
| 阶段一：Lisp 基础 | demo 01-08 | ✅ | 8 个散文件，`clojure -M` 直接跑 |
| 阶段二：宏系统 | demo 09-14 | ✅ | [`STAGE_2_SUMMARY.md`](./STAGE_2_SUMMARY.md) |
| 阶段三：并发模型 | demo 15-21 | ✅ | atom / ref+STM / agent / future / core.async / reducers |
| 阶段四：数据与类型 | demo 22-28 | ✅ | [`STAGE_4_SUMMARY.md`](./STAGE_4_SUMMARY.md) — spec / malli / EDN / Transit |
| 阶段五 Part 1：Web 三件套 | demo 29-31 | ✅ | [`STAGE_5_PART1_SUMMARY.md`](./STAGE_5_PART1_SUMMARY.md) — Ring / Compojure / Reitit |
| 阶段五 Part 2：数据/查询 | demo 32-34 | ✅ | [`STAGE_5_PART2_SUMMARY.md`](./STAGE_5_PART2_SUMMARY.md) — Datomic mini / Datalog / MBQL |
| 阶段五 Part 3：前端心智 + 综合实战 | demo 35-40 | ✅ | [`STAGE_5_PART3_SUMMARY.md`](./STAGE_5_PART3_SUMMARY.md) — Reagent / re-frame / 期权定价 / UTXO / ETL / Event Sourcing |
| 阶段六：深度补遗 | demo 41-50 | ✅ | [`STAGE_6_SUMMARY.md`](./STAGE_6_SUMMARY.md) — core.async 三件套 / transducers / spec / malli / macros / Java interop |
| 阶段七：电商分析后台综合项目 | demo 51 | ✅ | [`STAGE_7_SUMMARY.md`](./STAGE_7_SUMMARY.md) — 4 domain 聚合 / DataScript 投影 / MBQL β / Integrant 7 组件 / core.async 工作池+DLQ / 8 HTTP 端点 / 9 测试 |
| 全量回归 | demo 01-50 | ✅ 50/50 | [`STAGE_REGRESSION_REPORT.md`](./STAGE_REGRESSION_REPORT.md) — 48s 全跑通，无失败 |

## 怎么跑

```bash
# 散文件 demo（绝大多数）
clojure -M clojure/01_basics_and_collections.clj
clojure -M clojure/35_reagent_mental_model.clj

# 项目档 demo（有外部依赖的，进子目录跑）
cd clojure/19_core_async_channels && clojure -M:run
cd clojure/29_ring_handler         && clojure -M:run
cd clojure/32_datomic_mini         && clojure -M:run
cd clojure/39_csv_to_json_etl      && clojure -M:run
```

详细每个 demo 的运行命令，看其文件头注释或子目录 README/`deps.edn`。
