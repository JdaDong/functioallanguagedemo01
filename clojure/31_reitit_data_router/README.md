# Demo 31 — Reitit：Data-Driven Router

第三个 Web 项目档（依赖：`reitit`, `malli`, `ring`, `jsonista`）。

## 运行

```bash
cd clojure/31_reitit_data_router
clojure -M:run
```

启 server on 33903，跑 9 个 endpoint 自验证（含 schema coercion 失败用例）。

## 内容

- 路由表 = 普通 vector/map（**不是宏**），可序列化 / 反射 / 转 OpenAPI
- `:parameters` 用 malli schema 描述 `:body` / `:path` / `:query`
- `coerce-request-middleware`：路径参数 `:id` 自动从字符串变 `:int`
- `coerce-exceptions-middleware`：schema 失败自动 400，无需 try/catch
- `:data` 中的 `:middleware` 全表共享，单路由可覆盖
- `muuntaja` 自动 JSON in/out，不用手写 `j/read-value`

## Compojure vs Reitit 选型

| 维度 | Compojure | Reitit |
|---|---|---|
| 路由表形态 | 宏（`(GET ...)`） | 数据（vector/map） |
| 反射 | 不能（宏已展开） | 可遍历、可反查 |
| OpenAPI 自动生成 | 第三方插件 | `reitit-swagger` 一行搞定 |
| schema coercion | 手写 try/catch | 内置 malli/spec coercion |
| 学习曲线 | 低 | 中（中间件链概念多） |
| 性能 | 普通 | 编译期 trie，更快 |

**心智锚**：compojure 是"DSL 美感"路线，reitit 是"数据驱动"路线。Clojure 社区从 2020 起新项目基本默认上 reitit。
