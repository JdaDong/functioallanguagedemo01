# Clojure ROADMAP 阶段五 Part 1 完成总结：Web 三件套

> 完成时间：2026-05-09
> 范围：[`29_ring_handler/`](./29_ring_handler/) → [`30_compojure_router/`](./30_compojure_router/) → [`31_reitit_data_router/`](./31_reitit_data_router/)
> 形态：3 个项目档子目录（全部含外部依赖）
> 注：阶段五拆为 C1（29-31）/ C2（32-34）/ C3（35-40）三段；本档为 C1。

## 📦 交付物

| Demo | 主题 | 行数 | 关键卖点 |
|---|---|---|---|
| 29 ring_handler | Ring 基础 + 中间件 | ~120 | handler=fn / middleware=(handler→handler) / 链顺序 / 5 endpoint 自验证 |
| 30 compojure_router | 路由 DSL 宏 | ~135 | (GET/POST/DELETE) / context 分组 / not-found 兜底 / users CRUD 闭环 |
| 31 reitit_data_router | 数据驱动路由 | ~130 | 路由表=数据 / malli coercion / 路径&body 自动 coerce / humanized 错误 |

总计 ~385 行 Clojure，全部 3/3 PASS。

## 🪲 实跑过程中碰到 + 修复的真实坑（共 3 处）

1. **demo 29 端口 3001 被本机已有 node 进程占用**：BindException → 守则 3 不去碰用户进程，把端口改到 33901；同步把 30/31 用 33902/33903 错开
2. **demo 30 POST id 自增逻辑错了**：原写 `(let [id (swap! next-id inc) id (dec id)] ...)` —— swap! 已经把全局值 inc 了，再 dec 只是回退本地变量，next-id 仍然递增了；修成显式 `@next-id` + 后续 `(swap! next-id inc)`。守则 1 自检发现，**没等到运行错才改**
3. **demo 31 用了 `reitit.core/routes` 但没在 ns 里 require**：编译期会找不到 alias；提到 `:require` 加 `[reitit.core :as r]`

## 📊 教学高光时刻

| 现象 | 教学意义 |
|---|---|
| demo 29 `(-> router wrap-content-type-default wrap-timing wrap-log)` | 中间件链"从下往上读 = 从外往内执行"的具象化，最外层先看 request 后看 response |
| demo 29 `X-Elapsed-Ms = 55.07` 对应 `Thread/sleep 50` | timing 中间件确实测到了真实耗时 |
| demo 30 POST → GET 列表能看到 Cy id=3 | 路由 DSL 宏展开后只是普通 handler，atom 状态串得很自然 |
| demo 31 `GET /items/abc` 返回 humanized `{"id":["should be an integer"]}` | "路由表是数据 → 错误也是数据"的彻底贯彻 |
| demo 31 `POST {:price -1}` 直接被 schema 拦下 400 | 业务 handler 完全不需要写 try/catch，coerce-exceptions-middleware 包了 |
| demo 31 反射 `(r/routes router)` 打印出 `/ping /items /items/:id` | 路由表是数据，所以可遍历、可转 OpenAPI（compojure 不能） |

## 🎯 状态对照

| 阶段 | 范围 | 状态 |
|---|---|---|
| 阶段一 ~ 阶段四 | demo 01-28 | ✅ 已完成 |
| **阶段五 Part 1（C1）** | **demo 29-31 Web 三件套** | **✅ 本次完成** |
| 阶段五 Part 2（C2） | demo 32-34 数据/查询 | ⏳ 待开工 |
| 阶段五 Part 3（C3） | demo 35-40 前端心智 + 综合实战 | ⏳ 待开工 |

## 🚦 下一步

C2 = demo 32-34：

- demo 32 `datomic_mini` → 用 **DataScript**（纯内存 Datomic 同 API），演示事实存储 + `db-with` 时间旅行
- demo 33 `datalog_query` → 在 demo 32 的 DataScript 上演示 Datalog 查询语法（呼应 Metabase）
- demo 34 `metabase_style_pipeline` → 简化版 MBQL：`{:source-table :orders, :aggregations [...]}` → 编译成 Datalog → 执行

形态预估：3 个项目档（都依赖 datascript），约 400 行。
