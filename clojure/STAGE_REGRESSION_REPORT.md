# Clojure 全 50 Demo 回归测试报告

> 执行时间：2026-05-11
> 工具：[`regression_run.sh`](../regression_run.sh)
> 环境：Clojure CLI 1.12.0.1517 / OpenJDK 26 / macOS

---

## 总体结果

| 指标 | 值 |
|---|---|
| 总 demo 数 | **50** |
| **PASS** | **50 ✅** |
| FAIL | 0 |
| 全量耗时（系统 timer 累加） | 48s |
| 平均每 demo 耗时 | ~1.0s（含 JVM 启动） |
| Sanity check（log 行数 ≥ 3） | 50/50 ✅ |
| Sanity check（无 REPL prompt 误启） | 50/50 ✅ |

**结论**：50 个 demo 在干净环境下全部 headless 跑通，无任何依赖缺失、运行时报错、死锁、超时。

---

## 形态分布

| 形态 | 数量 | 跑法 |
|---|---|---|
| 散文件 (`NN_xxx.clj`) | 32 | `clojure -M clojure/NN_xxx.clj` |
| 项目档（子目录 + `deps.edn`） | 18 | `cd clojure/NN_xxx && clojure -M:run` |

---

## 详细结果

### 散文件（32 个）

| Demo | 主题 | 耗时 | log 行数 |
|---|---|---|---|
| 01 | basics_and_collections | 0s | 35 |
| 02 | immutable_data_structures | 1s | 28 |
| 03 | higher_order_and_transducers | 1s | 30 |
| 04 | destructuring | 1s | 26 |
| 05 | recur_and_loop | 0s | 22 |
| 06 | lazy_seq_and_infinite | 0s | 33 |
| 07 | multimethods | 1s | 30 |
| 08 | protocols_and_records | 0s | 38 |
| 09 | macros_intro | 1s | 33 |
| 10 | macros_anaphoric | 0s | 33 |
| 11 | macros_dsl | 1s | 29 |
| 12 | macros_state_machine | 0s | 27 |
| 13 | reader_macros | 0s | 55 |
| 14 | macro_hygiene | 1s | 31 |
| 15 | atoms_and_state | 0s | 34 |
| 16 | refs_and_stm | 1s | 33 |
| 17 | agents_async | 0s | 30 |
| 18 | futures_and_delay | 2s | 44 |
| 21 | reducers_parallel | 3s | 39 |
| 22 | spec_basic | 1s | 59 |
| 25 | data_oriented_programming | 0s | 41 |
| 26 | edn_format | 0s | 48 |
| 35 | reagent_mental_model | 1s | 49 |
| 36 | re_frame_event_loop | 0s | 47 |
| 37 | option_pricing_dsl | 1s | 28 |
| 38 | utxo_ledger | 0s | 27 |
| 40 | nubank_style_event_sourcing | 0s | 45 |
| 44 | transducers_advanced | 1s | 28 |
| 47 | macros_deep | 0s | 29 |
| 48 | metadata_protocols | 1s | 32 |
| 49 | reducers_fold | 0s | 28 |
| 50 | java_interop_advanced | 2s | 31 |

### 项目档（18 个）

| Demo | 主题 | 耗时 | log 行数 | 主要外部依赖 |
|---|---|---|---|---|
| 19 | core_async_channels | 2s | 41 | core.async |
| 20 | core_async_pipeline | 2s | 33 | core.async |
| 23 | spec_generators | 1s | 42 | test.check |
| 24 | malli_schema | 1s | 76 | malli |
| 27 | transit_format | 1s | 44 | transit-clj |
| 28 | schema_evolution | 1s | 42 | malli |
| 29 | ring_handler | 1s | 36 | ring + jetty |
| 30 | compojure_router | 1s | 35 | compojure |
| 31 | reitit_data_router | 3s | 41 | reitit |
| 32 | datomic_mini | 1s | 30 | datascript |
| 33 | datalog_query | 1s | 32 | datascript |
| 34 | metabase_style_pipeline | 2s | 32 | datascript |
| 39 | csv_to_json_etl | 0s | 54 | data.csv + jsonista |
| 41 | core_async_pipeline_async | 4s | 23 | core.async |
| 42 | core_async_pubsub_mix | 3s | 30 | core.async |
| 43 | core_async_error_dlq | 2s | 27 | core.async |
| 45 | spec_advanced | 1s | 27 | test.check |
| 46 | malli_advanced | 1s | 28 | malli |

---

## 跑通过程中遇到的脚本 bug（与 demo 无关）

> 这部分是为了回归脚本 [`regression_run.sh`](../regression_run.sh) 的可复现性记录的，不影响 demo 本身。

1. **第一版脚本 `local label="$1" cmd="$2" log="/tmp/regression_${label}.log"` 同行声明**：bash 中 `local` 同行多变量赋值时，右侧 `${label}` 引用为空。所有 log 都写到 `/tmp/regression_.log` 互相覆盖。
   - 修复：local 分行声明
2. **`bash -c "timeout $TIMEOUT $cmd"` 让 `cd` builtin 失效**：`timeout 120 cd xxx` 在 timeout 子进程里执行 cd（无效），rc=0；`&&` 通过；`clojure -M:run` 在 ROOT 目录跑（找不到 `:run` alias）→ 进 REPL → 没真跑 demo 但被记为 PASS（**伪阳性**）。
   - 修复：`timeout` 套在外层 → `timeout "$TIMEOUT" bash -c "$cmd </dev/null"`
3. **`</dev/null` 不可省**：`nohup` 给的 stdin 会被 `clojure` 误读为 REPL 输入。

> 这两个坑非常隐蔽——第一次跑结果"50/50 PASS、总耗时 30s"，看起来漂亮但完全是假的。直到查 `/tmp/regression_demo41.log` 发现里面只有 `user=>` REPL prompt，才确认伪阳性。CLAUDE.md 守则 1（"不假设、自检验证"）这次救命。

---

## 各 demo 关键输出节选

### 阶段四性能数据（非空跑）

- **demo 18 future + delay**：`pmap` 4 路 vs 单路对比，实测加速比 3-3.5×
- **demo 21 reducers**：r/fold 1e6 求和约 30ms（vs reduce 60ms）
- **demo 50 type hint vs 反射**：258× 加速（4.1ms vs 1046.7ms，一致复现 STAGE_6 报告）
- **demo 41 pipeline-async**：50 路并发 HTTP 453ms（vs 串行 ~2500ms）

### 教学反例都触发到位

- **demo 14 macro_hygiene**：not-hygienic 宏的变量捕获 bug 真演示出来
- **demo 16 refs_and_stm**：500 并发转账后总额恒等于初始总额（守恒律）
- **demo 24 malli**：closed map 拒绝额外字段、coercion 转字符串到 int
- **demo 27 transit**：Transit 比 EDN 序列化快约 2-3×（实测）
- **demo 43 core_async DLQ**：`[:ok v]` / `[:error reason]` 分流 + 取消令牌 100ms 后停在 16/100

---

## 复现命令

```bash
cd /Users/jiangdadong/CodeBuddy/functioallanguagedemo01
bash regression_run.sh
# 等约 60s
cat /tmp/regression_summary.tsv
```

每个 demo 完整输出：`/tmp/regression_demo<NN>.log`

---

## 一句话总结

**Clojure 50 demo，50/50 PASS，48 秒跑完，零失败、零依赖缺失、零超时。** 主线学习路径自洽闭环。
