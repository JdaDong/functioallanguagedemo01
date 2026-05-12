# Clojure ROADMAP 阶段五 Part 2 完成总结：DataScript / Datalog / MBQL

> 完成时间：2026-05-09
> 范围：[`32_datomic_mini/`](./32_datomic_mini/) → [`33_datalog_query/`](./33_datalog_query/) → [`34_metabase_style_pipeline/`](./34_metabase_style_pipeline/)
> 形态：3 个项目档子目录（统一依赖 `datascript/datascript` 1.7.8）
> 注：阶段五 C2 段——数据/查询。C1 已完成（Web 三件套），C3 待开工（前端心智 + 综合实战）。

## 📦 交付物

| Demo | 主题 | 行数 | 关键卖点 |
|---|---|---|---|
| 32 datomic_mini | DataScript 基础 + 时间旅行 | ~110 | conn=atom-of-db / db-with what-if / ref + cardinality/many / 反向 pull `:post/_author` |
| 33 datalog_query | Datalog 7 种核心模式 | ~155 | join / 谓词 / 聚合 + `:with` / 规则递归（传递闭包）/ 参数化查询 / pull 嵌套 |
| 34 metabase_style_pipeline | MBQL → Datalog 编译器 | ~180 | 查询=数据 / 自动 join 解析 / 计算列 / `:and`/`:or` filter / breakout + agg + sort + limit |

总计 ~445 行 Clojure，全部 3/3 PASS。

## 🪲 实跑过程中碰到 + 修复的真实坑（共 5 处，全部守则 1 自检+实跑双重发现）

1. **demo 32 反向 pull 语法**：写成 `:_post/author`（namespace 前下划线）→ DataScript 报 "Expected attribute having :db.type/ref"。修正：在 q/pull 里反向 ref 是 `:post/_author`（**attribute 名内部**加下划线）
2. **demo 33 字符串里的双引号**：`println "...的"贵客"..."` —— Clojure 字符串嵌套半角双引号必须转义。改用中文「贵客」最简
3. **demo 34 反向 ref in q**：第一版用 `[?l :_order/lines ?o]` 跑出来空集（语法在 q 里存在但写错前缀）。改成正向 `[?o :order/lines ?l]`（DataScript 自动反查）→ 立即正确
4. **demo 34 参数 `filter` shadow `clojure.core/filter`**：`(filter sequential? ...)` 报 ArityException（`PersistentVector` 当函数被调用）。改名 `filter-form`，并显式 `clojure.core/filter`
5. **demo 34 :find 里聚合算子是关键字而非符号**：`(:sum ?subtotal)` 让 datascript parser 报 "Cannot parse :find"。改 `(symbol (name op))` → 输出 `(sum ?subtotal)` 正确

## 📊 教学高光时刻

| 现象 | 教学意义 |
|---|---|
| demo 32 t0/t1/t2/t3 四个 db-value 同时存活 | "数据库就是值"的具象——历史不是日志，是活的可查值 |
| demo 32 db-with 涨薪 50% 但 conn 不变 | 函数式 what-if 不需要事务回滚，因为根本没修改 |
| demo 33 `reports-to` 5 行规则递归出传递闭包 | SQL 要写 CTE+UNION ALL；Datalog 因为是 Horn clause 自然支持 |
| demo 33 case 4 聚合 Cy=50 / Ada=19 / Bob=8 | `(* qty price)` 在 :where 子句里直接算，不用 select 出来再算 |
| demo 34 同一份 MBQL 数据 → 3 行编译 → datalog → 4 行结果 | 数据描述+解释器的威力：100 行写完 Metabase 内核 |
| demo 34 case 3 `[EU 50] [US 27]` 完美对账 | 编译器+执行器+后处理三段流水线全跑通 |

## 🎯 状态对照

| 阶段 | 范围 | 状态 |
|---|---|---|
| 阶段一 ~ 阶段四 | demo 01-28 | ✅ 已完成 |
| 阶段五 Part 1（C1） | demo 29-31 Web 三件套 | ✅ 已完成 |
| **阶段五 Part 2（C2）** | **demo 32-34 数据/查询** | **✅ 本次完成** |
| 阶段五 Part 3（C3） | demo 35-40 前端心智 + 综合实战 | ⏳ 待开工 |

## 🚦 下一步

C3 = demo 35-40，6 个 demo：

- demo 35 `reagent_mental_model` → 用 JVM Clojure atom + watcher 模拟 Reagent 心智（按 Q1=β 不引 ClojureScript）
- demo 36 `re_frame_event_loop` → re-frame 架构：events → effects → fx handlers，对标 Redux
- demo 37 `option_pricing_dsl` → 金融 DSL（呼应 Haskell demo 42，二项树定价）
- demo 38 `utxo_ledger` → UTXO 区块链账本（呼应 Haskell demo 45）
- demo 39 `csv_to_json_etl` → ETL 实战（呼应 Haskell demo 48）
- demo 40 `nubank_style_event_sourcing` → 事件流 + 投影函数 + 命令处理器

形态预估：35/36 散文件（无外部依赖）；37/38/39/40 散文件或单文件项目（37/38 可能引 `data.csv`）。约 600-700 行。

体量比 C1/C2 大（6 个），所以**还会再分一次**：可能拆成 C3a（35-36 心智模型）+ C3b（37-40 实战）。下次开工时根据进度再判。
