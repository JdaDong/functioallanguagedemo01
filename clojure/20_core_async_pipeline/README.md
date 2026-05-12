# Demo 20 — core.async Pipeline & Transducers on Channels

## 运行

```bash
cd clojure/20_core_async_pipeline
clojure -M:run
```

## 内容

- `pipe`：把一个 channel 接到另一个 channel（最简管道）
- `pipeline`：N 个并行 worker 处理一个 channel，结果按顺序输出（CPU 密集）
- `pipeline-blocking`：同上但跑在 thread 池（IO 密集，比如 HTTP/DB 请求）
- `chan` 的 transducer 参数：把 `map`/`filter`/`partition-by` 直接挂在 channel 上
- `mult` / `tap`：fan-out（一份数据广播到多个消费者）
- mini-Kafka 风格：生产者 → buffer → 多 worker → aggregator

## 关键认知

`pipeline-blocking` vs `pipeline` 的选择决定 IO 密集型业务能不能扩到几千 QPS：
- `pipeline` 走 go-block 池（核心数 + 2），适合**纯计算**
- `pipeline-blocking` 走独立线程池，**HTTP / DB / 文件 IO 必须用它**——否则会饿死 go 池
