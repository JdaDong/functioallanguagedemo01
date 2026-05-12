# Demo 30 — Compojure：路由 DSL

第二个 Web 项目档（依赖：`ring`, `compojure`, `metosin/jsonista`）。

## 运行

```bash
cd clojure/30_compojure_router
clojure -M:run
```

启 server on 33902，跑 8 个 endpoint 自验证（含 CRUD 完整闭环）。

## 内容

- `(GET path [destruct] body)` / `POST` / `DELETE`：路由宏
- `(context "/users" [] user-routes)`：路由分组 + 前缀
- `(route/not-found ...)`：兜底 handler
- 路径参数：`/users/:id` 中 `:id` 自动绑定
- JSON in/out：`metosin/jsonista`（`object-mapper {:decode-key-fn keyword}`）
- 内存"数据库"：`atom`（单进程并发安全）

## 关键概念

Compojure 不是新的运行时，**路由宏展开后还是 Ring handler**。所以 demo 29 的中间件套法（`(-> app wrap-log wrap-timing)`）原封不动适用。

下个 demo（reitit）会做"数据驱动路由"——路由表是 EDN 数据而非宏，能反射、内省、自动生成 OpenAPI。
