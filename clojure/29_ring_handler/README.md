# Demo 29 — Ring：HTTP as a Function

第一个 Web 项目档（依赖：`ring/ring-core`, `ring/ring-jetty-adapter`）。

## 运行

```bash
cd clojure/29_ring_handler
clojure -M:run
```

会启动 Jetty on port 3001，自请求 5 个路径，打印响应，关闭。

## 内容

- handler = `(fn [request-map] response-map)`
- 中间件 = `(handler -> handler)`：`wrap-log` / `wrap-timing` / `wrap-content-type-default`
- 中间件链顺序：`(-> h mw1 mw2 mw3)` — mw3 最外层
- 简易路由：`case [method uri]`（下个 demo 用 compojure DSL）
- 自请求工具：`java.net.URLConnection`，无需引 clj-http

## 关键概念

**Ring 的核心抽象只有一条**：HTTP 服务就是 `request → response` 的纯函数；中间件就是包装这个函数的高阶函数。没有框架、没有状态机、没有注解。
