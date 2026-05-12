# Clojure ROADMAP 阶段七完成总结：电商分析后台综合实战

> 完成时间：2026-05-11
> 范围：demo 51（1 个综合项目，分 4 轮迭代完成）
> 形态：单项目档（17 个源文件 + 4 个测试文件 + 2 个 bash 脚本 + 2 个 md），10 个 deps
> 主旨：把前 50 个 demo 的"原子能力"集成进一个真实可跑的、有 HTTP 接口的、有持久化投影的、有工作池 + DLQ 的 ES/CQRS 后台，让单点知识沉淀为系统能力。

## 📦 交付物

| Demo | 主题 | 形态 | 关键看点 |
|---|---|---|---|
| 51 [ecommerce_analytics](./51_ecommerce_analytics/) | 电商分析后台 | 项目档（综合） | 4 个 domain 聚合 / DataScript 投影 / MBQL β 编译器 / Integrant 7 组件 / core.async 工作池 + DLQ / 8 个 HTTP 端点 / 9 测试 18 断言全过 |

合计：**~1900 行**（src 1654 + test 191 + 脚本 ~80），**全 PASS** ✅

## 🛠 4 轮迭代过程

| 轮次 | 范围 | 行数累计 | 关键交付 |
|---|---|---|---|
| 第一轮 | Step 1（骨架）+ Step 2（4 个 domain） | 566 | 状态机 / STM 守恒 / 策略表 / 用户 token |
| 第二轮 | Step 3（store/etl/snapshot）+ Step 4（mbql/window）+ Step 5（pool/dlq） | 1277 | event→tx 投影 / replay 等价性 / MBQL β / 同环比 / 100 命令并发 + 30 DLQ |
| 第三轮 | Step 6（HTTP）+ Step 7（Integrant）+ Step 8（test） | 1795 | 11 handler / 8 端点 / 7 组件拓扑 / 9 tests 18 assertions |
| **第四轮** | **Step 9（演示）+ Step 10（收尾）** | **~1900** | main_server / demo.sh + demo_full.sh / README / 本文件 |

## 🪲 实跑过程中的真实坑（共 6 处）

1. **第一轮 demo-pricing assert 失败**：原以为 `(* 2 50.00) = 100`，实测 100.0；assert 写成 `(= 100 ...)`(int) 不通过 → 改 `=` 为 `100.0`，意识到 Clojure `=` 不跨数值类型。
2. **第一轮 `'(symbol)` 误用 quote** ：`compile-filter` 多方法里写 `'(?o)` 又传给 datascript，被解释成 special form。改用 `(symbol "?o")`。
3. **第二轮 `parse-double` 与 `clojure.core/parse-double` 同名 warning**：Clojure 1.11+ 把 parse-double 升进了 core，自定义同名报 redefined。改名为 `parse-num`。
4. **第二轮 `(swap! a update k f)` 返回整 map 不是 k 对应的值**：在 demo-pool-with-dlq 里把 swap! 结果当数字比较，触发 ClassCastException "PersistentHashMap cannot be cast to Number"。改成 `(get (swap! ...) k)`。
5. **第二轮闭区间 vs 半开区间不一致**：mbql `:between` 是 `<=` 闭区间，但 window 的 `month-bounds` 返回 `[from, next-month-1日)` 半开。导致 4 月窗口意外包含 4 月 1 日 0 点的边界事件。改 month-bounds 让 end = `next - 1ms`，与 mbql 闭区间对齐。
6. **第三轮 `mapv #(deref (future ...)) (range 5)`**：`#(...)` 是零参函数（没用 `%`），mapv 给它传 1 个参数 → ArityException。改成显式 `(fn [_] @(future ...))`。

## 📊 教学高光（实测数据）

| 维度 | 实测 | 启示 |
|---|---|---|
| 1000 并发 STM reserve! 守恒 | on-hand+reserved 总数与初始一致 | ref+dosync 真的提供了"事务流水线"语义 |
| 96 事件 replay 等价性 | conn-a 与 conn-b 的 datoms 集合全等 | ES 的 replay 闭环可证 |
| ETL 50 行 → 48 ok + 2 reject | 脏数据明确分流（sku 空 / qty=abc） | 清洗与执行严格分层 |
| 100 命令并发 0 失败 | 100 succeeded，0 retry | core.async 工作池的 baseline |
| 100 命令含 30% 永久失败 + 70% 2 次重试 | 70 succeeded（130 retries 流转） + 30 进 DLQ | retry + DLQ 的工业语义跑通 |
| Integrant init/halt 3 次循环 | 254ms / 65ms / 68ms | jetty 可被反复启停干净，资源不泄漏 |
| 9 tests / 18 assertions | 0 fail / 0 error | 守恒律 + MBQL 编译 + e2e fixture |

## 🎯 与前 50 demo 的能力映射

| 来源 demo | 用到点 |
|---|---|
| 02 状态/不可变  | 全项目基础 |
| 04 多方法       | `event->tx` / `compile-filter` |
| 13 atom         | inventory.snapshot / event-log |
| 14 ref + STM    | inventory.reserve! / reserve-many! 回滚 |
| 16 core.async   | workers.pool（go-loop + chan + timeout） |
| 30 datascript   | store.datascript 全部 |
| 33 reitit       | api.routes 8 端点 |
| 34 metabase MBQL | analytics.mbql β 形态 |
| 36 ring/jetty   | api.middleware + system |
| 39 integrant    | system.clj 7 组件 |
| 41 ES/CQRS      | order.clj + snapshot.clj |
| 47 worker pool  | workers.pool 工业化版 |

## 🎯 状态对照

| 阶段 | 范围 | 状态 |
|---|---|---|
| 阶段一 ~ 阶段六 | demo 01-50 | ✅ 已完成 |
| **阶段七：电商分析后台综合项目** | **demo 51** | **✅ 本次完成** |

至此 **Clojure 51 demo 全线 ✅**，主线 + 综合项目结束。

## 🚦 后续可选方向

- 把 51 拆成系列文档（架构演化博客）
- 加 PostgreSQL 持久化层替换 in-memory（让 ES 真正产生工业意义）
- 加压测脚本（k6 或 vegeta）打 35100 端口拿真实 RPS
- 跨语言对比：用 Elixir 或 Erlang 实现等价后台
