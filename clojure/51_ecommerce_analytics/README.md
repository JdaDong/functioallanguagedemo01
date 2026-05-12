# 51 — 电商分析后台（Clojure 综合实战）

> Step 1-10 完整版。把前 50 个 demo 的"原子能力"集成进一个真实可跑的、有 HTTP 接口的、有持久化投影的、有工作池 + DLQ 的 ES/CQRS 后台。

---

## 30 秒上手

```bash
# 进入本目录
cd clojure/51_ecommerce_analytics

# 1) 跑全套教学 demo（Step 1-5：领域 + 投影 + 分析 + 工作池）
clojure -M:run

# 2) 跑测试套（9 tests / 18 assertions）
clojure -M:test

# 3) 启 HTTP 服务（端口 35100，已自动 ingest 48 张 CSV 演示订单）
clojure -M:run-server

# 4) 另起一个终端，跑 11 次 curl 演示
bash demo.sh

# 或一键启停（后台启 server，等就绪，跑 demo.sh，自动关）
bash demo_full.sh
```

---

## 架构

```
┌──────────── HTTP（reitit + jetty + ring） ────────────┐
│  /health  /users/*  /orders/*  /analytics/*           │
└────────────────────┬───────────────────────────────────┘
                     │
                     ▼
┌──────────── 命令分发（同步 dispatch） ────────────────┐
│  command → handler → events                           │
│  domain/order.clj 状态机 6 个事件                     │
└────────────────────┬───────────────────────────────────┘
                     │
       ┌─────────────┴──────────────┐
       ▼                            ▼
┌──── 投影 ────────┐        ┌──── 事件日志 ────┐
│ DataScript conn  │        │ event-log atom    │
│ (in-memory db)   │        │ append-only       │
└────────┬─────────┘        └─────┬─────────────┘
         │                        │
         │ MBQL β 编译 → datalog   │ snapshot/replay
         ▼                        ▼
┌──── 分析 ───────────────────────────────────┐
│  sales-by-sku / top-users / MoM·YoY window  │
└─────────────────────────────────────────────┘

旁路：workers.pool（core.async 命令总线 + N worker + retry）
      workers.dlq  （max-attempts 失败后落库 + replay-all!）
```

依赖通过 **Integrant** 编排，7 个组件、拓扑依赖、3 次 init/halt 循环验证干净启停。

---

## 8 个 HTTP 端点

| 方法 | 路径 | 入参 | 返回 |
|---|---|---|---|
| GET  | `/health` | - | `{:ok true}` |
| POST | `/users/register` | `{user-id name email password}` | `{user-id}` |
| POST | `/users/login` | `{user-id password}` | `{token...}` |
| POST | `/orders` | `{order-id user-id items coupon?}` | 投影后的订单 |
| GET  | `/orders/:id` | - | 投影 |
| POST | `/orders/:id/pay` | `{amount}` | 投影（status=paid） |
| POST | `/orders/:id/ship` | `{tracking-no}` | 投影（status=shipped） |
| POST | `/orders/:id/deliver` | - | 投影（status=delivered） |
| POST | `/orders/:id/cancel` | `{reason}` | 投影（status=cancelled） |
| GET  | `/analytics/sales-by-sku` | `?from=ms&to=ms` | 各 SKU 销量/销售额/订单数 |
| GET  | `/analytics/top-users` | `?n=5` | 用户消费 Top N |
| GET  | `/analytics/window/mom` | `?year=&month=` | 环比 {curr prev delta pct} |

---

## 目录结构

```
51_ecommerce_analytics/
├── deps.edn                  ;; 10 个 deps，3 个 alias
├── README.md                 ;; 本文件
├── STAGE_7_SUMMARY.md        ;; 第 7 阶段（综合项目）总结
├── demo.sh                   ;; 11 次 curl 演示
├── demo_full.sh              ;; 启停一体
├── resources/
│   ├── config.edn            ;; 默认配置
│   └── sample_orders.csv     ;; 50 行（48 ok + 2 dirty）演示数据
├── src/ecom/
│   ├── main.clj              ;; -main：Step 1-5 教学 demo
│   ├── main_server.clj       ;; -main：HTTP 服务入口（含 CSV ingest）
│   ├── system.clj            ;; Integrant 7 组件
│   ├── domain/
│   │   ├── order.clj         ;; 状态机：6 个事件 + 4 个命令
│   │   ├── inventory.clj     ;; STM ref + 守恒律
│   │   ├── pricing.clj       ;; 策略表 + 优惠券
│   │   └── user.clj          ;; 注册/登录/token
│   ├── store/
│   │   ├── datascript.clj    ;; schema + event->tx 多方法
│   │   ├── snapshot.clj      ;; event-log + snapshot/replay
│   │   └── etl.clj           ;; CSV 清洗（reject 列表）
│   ├── analytics/
│   │   ├── mbql.clj          ;; β 形态：filter/group/agg/sort/limit
│   │   └── window.clj        ;; MoM/YoY 同环比
│   ├── workers/
│   │   ├── pool.clj          ;; core.async 工作池 + retry
│   │   └── dlq.clj           ;; 失败队列 + replay-all!
│   └── api/
│       ├── routes.clj        ;; reitit 路由表
│       ├── handlers.clj      ;; 11 个 handler
│       └── middleware.clj    ;; json + log + exception
└── test/ecom/
    ├── conservation_test.clj ;; STM 守恒律 3 例
    ├── mbql_compile_test.clj ;; MBQL 编译器 3 例
    ├── api_e2e_test.clj      ;; Integrant 启停 + ring e2e 3 例
    └── test_runner.clj
```

---

## 跑通的关键数据

| 项 | 值 |
|---|---|
| `clojure -M:run`     | Step 1-5 全 PASS（守恒律、状态机、ETL 48/2、replay 等价、MBQL、worker pool 100/0、DLQ 30） |
| `clojure -M:test`    | 9 tests / 18 assertions / 0 fail / 0 error |
| `clojure -M:run-server` | 35100 起，自动 ingest 48 单到 :delivered |
| `bash demo.sh`       | 11 次请求全 200/404 符合预期 |
| Integrant init/halt 3 次 | 254ms / 65ms / 68ms（首次冷启） |

---

## 教学映射：51 demo 用到了前 50 demo 的哪些原子能力？

| 来源 | 用到点 |
|---|---|
| 02 状态/不可变 | order/inventory 全部基于 ref/atom |
| 04 多方法     | `event->tx` / `compile-filter` |
| 05 records    | （未用，刻意保持 map） |
| 06 lazy seq   | `mapcat order->rows` |
| 07 reduce     | mbql aggregate |
| 08 transducers | etl 清洗（可改写） |
| 13 atom       | inventory.snapshot |
| 14 ref + STM  | inventory.reserve! / reserve-many! |
| 15 agent      | （未用，因为 STM 已够） |
| 16 core.async | workers.pool（go-loop + chan + timeout） |
| 24 spec/malli | （Step 6 跳过严格验证，由业务 cond 把关） |
| 30 datascript | store.datascript 全部 |
| 33 reitit     | api.routes |
| 34 metabase MBQL | analytics.mbql β 形态 |
| 36 ring/jetty | api.middleware + system.clj |
| 39 integrant  | system.clj 7 组件 |
| 41 ES/CQRS    | order.clj + snapshot.clj 完整闭环 |
| 47 worker pool | workers.pool 工业化版 |

---

## 命令速查

```bash
clojure -M:run            # 教学 demo
clojure -M:test           # 测试套
clojure -M:run-server     # 启服务
clojure -M:run-server 8080  # 自定义端口

bash demo.sh              # 假设服务已启
bash demo_full.sh         # 一键启停
bash demo_full.sh 8080    # 自定义端口
```
