# Clojure ROADMAP 阶段五 Part 3 完成总结：前端心智 + 综合实战

> 完成时间：2026-05-09
> 范围：[`35_reagent_mental_model.clj`](./35_reagent_mental_model.clj) → [`36_re_frame_event_loop.clj`](./36_re_frame_event_loop.clj) → [`37_option_pricing_dsl.clj`](./37_option_pricing_dsl.clj) → [`38_utxo_ledger.clj`](./38_utxo_ledger.clj) → [`39_csv_to_json_etl/`](./39_csv_to_json_etl/) → [`40_nubank_style_event_sourcing.clj`](./40_nubank_style_event_sourcing.clj)
> 形态：5 个单文件（无外部依赖）+ 1 个项目档（demo 39 用 data.csv + jsonista）
> 注：阶段五 C3 段——前端心智模型 + 4 个综合实战。**至此 Clojure 主线 demo 01-40 全部完成。**

## 📦 交付物

| Demo | 主题 | 行数 | 关键卖点 |
|---|---|---|---|
| 35 reagent_mental_model | Reagent reactive cell | ~110 | ratom + subscribe! 模拟 reactive；3 section（counter / 链式 / 同值不重跑） |
| 36 re_frame_event_loop | re-frame 事件循环 | ~135 | dispatch → interceptors → handler → effects → fx；含 :http / :dispatch 链式 |
| 37 option_pricing_dsl | 期权定价 | ~115 | CRR 二项树 + Black-Scholes 闭式；4 case 含收敛性证明（n=10→1000，误差 1.89%→0.02%） |
| 38 utxo_ledger | UTXO 区块链账本 | ~135 | 守恒律 + 双花/凭空印钞/缺签拒绝；6 case 全过 |
| 39 csv_to_json_etl | ETL 流水线 | ~110 + CSV | 清洗/校验/聚合三段；错误也是数据（rejected 行带原因写入 JSON 报告） |
| 40 nubank_event_sourcing | 事件溯源 | ~155 | command → events → projection；replay 任意 prefix；what-if 不污染主流 |

总计 ~760 行 Clojure，**6/6 PASS** ✅。

## 🪲 实跑过程中碰到 + 修复的真实坑（共 4 处）

1. **demo 35 第一版 section-2 过度复杂**：用 protocol + reify + 手动 atom 桥接 derived ratom，自检发现"读起来比真 Reagent 还难懂"——直接删掉重写为 `reaction` 返回新 ratom 的递归形态。守则 2 自检发现，**没等运行就重写**
2. **demo 35 `clojure -M file.clj` 不会自动调 `-main`**：Clojure 脚本模式只 load 文件，不执行 `-main`。在文件末尾加 `(-main)` 就行（替代方案：用 `:main-opts ["-m" "demo35"]` 但需要 deps.edn）
3. **demo 38 case 5 错误信息和描述不符**：以为会触发"签名不完整"，实际触发"input 不在 utxo-set"——因为 case 3 已经把 Bob 的 utxo 花了。改成 Alice 试图花 Cy 的 utxo，case 5 立即触发预期错误
4. **demo 39 + 40 中文字符串里嵌半角双引号**：`"事务流水线"`、`"假设性"` 让 ns / println 解析爆炸。这是第三、四次踩同一个坑（demo 33 也踩过），**已写入项目记忆**：今后所有中文字符串里禁止用半角 `"` 包裹，改用 `「...」` 或转义

## 📊 教学高光时刻

| 现象 | 教学意义 |
|---|---|
| demo 35 同值不重跑：3 次 set! 中 2 次同值，仅 3 次渲染 | reagent 的 ratom 内置 `=` 比较优化，不是傻乎乎触发 |
| demo 36 `:http` fx 模拟同步返回 → 自动派发 `:user-loaded` | "副作用是数据"：业务 handler 100% 纯，副作用全在 fx-handlers，可单元测试 |
| demo 37 binomial(n=10)→error 1.89%, n=1000→error 0.02% | 二项树渐进收敛到 BS 闭式，**两种独立引擎交叉验证** = 金融工程的金标准 |
| demo 37 美式 put 比欧式贵 0.96 美元 | 提前行权权利溢价 —— 教科书结论被代码量化 |
| demo 38 总量守恒 100→131 in/out 全部对账 | 协议层不变量 = sum-in ≥ sum-out + fee，每笔 tx 自动检查 |
| demo 39 错误行带 :error 流过整个 pipeline | 函数式 ETL 的灵魂：错误是数据不是异常，最后并入 JSON 报告供下游消费 |
| demo 40 events[..3) → alice=100 bob=0；events[..6) → alice=70 bob=60 | replay 任意 prefix 还原历史状态，account-balance 系统办不到 |
| demo 40 what-if `(atom @bus)` 派发不污染主流 | 因为 events 是不可变向量，`@bus` 拿到的就是值快照 |

## 🎯 状态对照

| 阶段 | 范围 | 状态 |
|---|---|---|
| 阶段一 ~ 阶段四 | demo 01-28 | ✅ 已完成（具体见 STAGE_2 / STAGE_4 总结档） |
| 阶段五 Part 1（C1） | demo 29-31 Web 三件套 | ✅ |
| 阶段五 Part 2（C2） | demo 32-34 数据/查询 | ✅ |
| **阶段五 Part 3（C3）** | **demo 35-40 前端心智 + 综合实战** | **✅ 本次完成** |
| **Clojure 主线 demo 01-40** | — | **✅ 全部完成** |

## 🪟 一处坦白（守则 1）

ROADMAP.md 中 demo 01-08 的状态列还显示 ⏳，但实际上它们在阶段一已经完成。这是 ROADMAP 文档本身的预存在 inconsistency，我**没有顺手修**（守则 3 — 只改与本次相关的）。如果你想把 ROADMAP 状态全部对齐当前实际进度，告诉我一声，我专门做一次 ROADMAP 状态校对就行，不混在功能 demo 里。

## 🚦 下一步选项

Clojure 主线 40 demo 全部完成。可选方向：

- **A. ROADMAP 状态校对**：把 demo 01-28 的 ⏳ 全部改 ✅，添加项目级 README 串起所有 STAGE 总结
- **B. 回头补遗**：之前阶段如果有想加深的 demo（比如 transducers、core.async、spec/malli）可以加 demo 41+
- **C. 跨语言对比**：用 `LANGUAGE_COMPARISON.md`（已有）增补 Clojure 章节，对比 Haskell / Erlang / Scala 视角下的同一问题
- **D. 实战大项目**：合并若干 demo 做一个完整的"小型电商分析后台"——demo 31 reitit + demo 32 datascript + demo 34 mbql + demo 39 etl + demo 40 event-sourcing
- **E. 暂停审视**：你先看 C3 这 6 个 demo，给反馈

请选一项。
