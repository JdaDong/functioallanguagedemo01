# Demo 19 — core.async Channels 基础

第一个**项目档子目录**（外部依赖：`org.clojure/core.async`）。

## 运行

```bash
cd clojure/19_core_async_channels
clojure -M:run
```

或在仓库根目录：

```bash
clojure -M -Sdeps "$(cat clojure/19_core_async_channels/deps.edn)" -m demo19
```

## 内容

- `>!!` / `<!!`：阻塞式 put/take（线程外用）
- `>!`  / `<!` ：parking 式 put/take（go-block 内用）
- `chan` 三种 buffer：unbuffered / fixed / dropping / sliding
- `go` vs `thread`：何时各选哪种
- `alts!!`：多路选择（CSP 的 `select`）
- `close!`：关闭通道的语义（消费者收 nil）

## 关键概念

**core.async 的核心是 CSP（Communicating Sequential Processes）**——用通道解耦生产者和消费者，比 Future + Promise 表达力强一个量级。

**go vs thread 的选择**：
- `go` ：把代码"虚拟线程化"，遇到 `<!`/`>!` 自动让出线程；适合大量轻量任务（百万级也 OK）
- `thread`：开真线程；适合阻塞 I/O 或 CPU 密集（go-block 里**不要**用阻塞 I/O，会饿死整个 go 线程池）
