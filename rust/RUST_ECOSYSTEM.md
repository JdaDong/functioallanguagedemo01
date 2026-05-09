# Rust 好玩的 SDK 和开源项目盘点

> 本文档整理 Rust 生态中**真正"好玩"或"工业级在用"**的 25 个项目，对应本目录 25 个 demo（[`01_iterators_and_closures.rs`](./01_iterators_and_closures.rs) ~ [`25_pin_future_executor.rs`](./25_pin_future_executor.rs)）打通"学完之后玩什么"的衔接。

Rust 的"好玩"主要来自两股力量：一是**"用 Rust 重写一切性能敏感工具"**的产业浪潮（ripgrep / uv / swc / turbopack 都是这个模式），二是**系统级 + 区块链 + AI 基础设施**三条硬核主战场。它同时吃到了 C/C++ 的性能生态位和 Go/Python 的开发者工具生态位——这是近 20 年少见的。

下面按 7 类组织，每个项目都标了 **🌟 趣味点** 和 **🔧 上手难度**。

---

## 🛠 一、开发者工具（每天都在用，但可能没意识到是 Rust 写的）

### 1. **ripgrep** (`rg`) — 全盘文本搜索
- **GitHub**: `BurntSushi/ripgrep`（50k+ stars）
- 🌟 **趣味点**：比 `grep` 快 5~10×，自动 `.gitignore`；现代 IDE（VSCode、Cursor、本工具链的 `grep_search`）底层都用它
- 作者 Andrew Gallant 一人撸出，是"Rust 重写 X"范式的奠基之作
- 🔧 难度：⭐ 用：零门槛；读源码：⭐⭐⭐（教科书级别的 Rust 工程化）
- 🔗 配合本目录 [`06_iterator_internals.rs`](./06_iterator_internals.rs) 食用

### 2. **fd** / **bat** / **zoxide** / **starship** — 现代化 CLI 小工具四件套
- **GitHub**: `sharkdp/fd`、`sharkdp/bat`、`ajeetdsouza/zoxide`、`starship/starship`
- 🌟 `fd` 替 `find`、`bat` 替 `cat`、`zoxide` 智能 cd、`starship` 跨 shell 提示符
- 共同点：**启动快到可以忽略不计**，小而美的 Unix 哲学升级
- **应用场景**：每个 Rust 开发者的 `.zshrc` 必备

### 3. **uv** / **ruff** — Python 工具链的 Rust 重写（Astral 出品）
- **GitHub**: `astral-sh/uv`（30k+ stars）、`astral-sh/ruff`（33k+ stars）
- 🌟 **uv** 替代 pip+poetry+pyenv，**ruff** 替代 flake8+black+isort；**10~100× 速度**
- 正在统一整个 Python 生态的工具链，是 2024 最火的 Rust 项目之一
- 🔧 难度：⭐ 用：一行 `uv pip install`；读源码：⭐⭐⭐⭐

### 4. **swc** / **turbopack** / **rolldown** / **oxc** — JS 工具链的 Rust 化
- **GitHub**: `swc-project/swc`（31k+ stars）、`vercel/turborepo`、`rolldown-rs/rolldown`、`oxc-project/oxc`
- 🌟 **swc** 替 Babel（被 Next.js 默认用）；**turbopack** 替 Webpack；**rolldown** 是 Vite 下一代底层；**oxc** 号称"最快的 JS parser"
- **应用场景**：整个前端构建工具链正在被 Rust 重写一遍

### 5. **biome** — JS/TS 的 Prettier+ESLint 合一
- **GitHub**: `biomejs/biome`（18k+ stars）
- 🌟 一个二进制搞定格式化 + lint，比 Prettier 快 ~25×
- Rome 项目重启后的社区接班人

---

## 🌐 二、Web / 网络基础设施

### 6. **tokio** — Rust 异步运行时的事实标准
- **GitHub**: `tokio-rs/tokio`（27k+ stars）
- 🌟 **Rust 异步生态的 JDK**，几乎所有高性能网络库底层都是它
- 🔗 配合本目录 [`08_async_streams_tokio.rs`](./08_async_streams_tokio.rs)、[`19_tokio_sync_primitives.rs`](./19_tokio_sync_primitives.rs)、[`25_pin_future_executor.rs`](./25_pin_future_executor.rs) 食用

### 7. **axum** / **actix-web** / **tower** — Web 框架三强
- **GitHub**: `tokio-rs/axum`（20k+ stars）、`actix/actix-web`（22k+ stars）、`tower-rs/tower`
- 🌟 **axum** 是 tokio 官方，类型驱动的路由；**actix-web** 是 actor 模型出身，TechEmpower 常年前三；**tower** 是"中间件抽象层"的鼻祖，axum/hyper 都建在它上面
- 🔗 配合本目录 [`18_axum_like_router.rs`](./18_axum_like_router.rs) 食用

### 8. **hyper** / **reqwest** — HTTP 协议 / 客户端
- **GitHub**: `hyperium/hyper`（14k+ stars）、`seanmonstar/reqwest`（9k+ stars）
- 🌟 **hyper** 是几乎所有 Rust HTTP 栈的底层（axum/reqwest/Cloudflare Pingora 都靠它）
- **应用场景**：一切"Rust 发 HTTP 请求"的场景

### 9. **Pingora** — Cloudflare 的 L7 反代
- **GitHub**: `cloudflare/pingora`（22k+ stars）
- 🌟 **Cloudflare 用它全量替换 NGINX**，扛 40M+ QPS；2024 年开源
- 看完会理解 Rust 在 **"边缘代理"** 这个 niche 的统治力
- 🔧 难度：⭐⭐⭐⭐ 工业级代码

### 10. **deno** — Node 之父重写的 JS/TS 运行时
- **GitHub**: `denoland/deno`（94k+ stars）
- 🌟 Ryan Dahl 重写 Node，核心用 Rust（V8 绑定层），运行时层用 TypeScript
- **应用场景**：边缘函数（Deno Deploy）、脚本工具链

---

## 🗃 三、数据库 / 搜索 / 向量

### 11. **TiKV** — 分布式 KV（TiDB 底层）
- **GitHub**: `tikv/tikv`（15k+ stars）
- 🌟 **CNCF 毕业项目**，PingCAP 出品；Raft 一致性 + MVCC，全世界最大的 Rust 数据库项目之一
- **应用场景**：金融级分布式事务、大规模配置中心

### 12. **Meilisearch** — 开箱即用搜索引擎
- **GitHub**: `meilisearch/meilisearch`（47k+ stars）
- 🌟 比 Elasticsearch 简单 10 倍，docker 一跑就有；模糊搜索、高亮、typo 容忍开箱即用
- **应用场景**：独立博客 / 小型电商 / 文档站点

### 13. **Qdrant** / **LanceDB** — 向量数据库
- **GitHub**: `qdrant/qdrant`（21k+ stars）、`lancedb/lancedb`（4k+ stars）
- 🌟 **RAG / AI 搜索基础设施**的两大 Rust 选手；Qdrant 云原生，LanceDB 文件级嵌入式
- **应用场景**：LLM 知识库、语义搜索、推荐系统

### 14. **SurrealDB** — 多模数据库
- **GitHub**: `surrealdb/surrealdb`（28k+ stars）
- 🌟 同时支持文档、图、KV、时序；单机 embed 也行，分布式也行
- 🔧 难度：⭐⭐ 查询语言 SurrealQL 要学

### 15. **sled** / **redb** — 嵌入式 KV
- **GitHub**: `spacejam/sled`、`cberner/redb`
- 🌟 现代 RocksDB 替代，**纯 Rust、无 C 依赖**；适合嵌入式/边缘场景
- **应用场景**：本地缓存、边缘节点状态

---

## 🪙 四、区块链（Rust 几乎是事实官方语言）

### 16. **Solana** — 高性能公链
- **GitHub**: `solana-labs/solana`（13k+ stars）
- 🌟 Sealevel 并行运行时，号称 65000 TPS；整个节点和智能合约都是 Rust
- **应用场景**：高频交易链、NFT、DePIN

### 17. **Polkadot / Substrate** — 跨链框架
- **GitHub**: `paritytech/polkadot-sdk`（2k+ stars）
- 🌟 用 Substrate 可以 "几周拼出一条链"；以太坊创始人之一 Gavin Wood 的项目
- **应用场景**：行业联盟链、主权区块链

### 18. **Foundry** — 以太坊开发工具链
- **GitHub**: `foundry-rs/foundry`（8k+ stars）
- 🌟 重写 Hardhat，**测试速度 10×**；2023 起以太坊开发者首选
- **应用场景**：智能合约开发 / 审计

---

## 🤖 五、AI / ML 基础设施

### 19. **Hugging Face `tokenizers`** — 分词器
- **GitHub**: `huggingface/tokenizers`（9k+ stars）
- 🌟 **几乎所有 LLM 训练都依赖它**；Python 绑定，核心是 Rust，性能吊打纯 Python 分词器几百倍
- **应用场景**：每一次 LLM 训练/推理前的预处理

### 20. **candle** — Hugging Face 原生 Rust 深度学习框架
- **GitHub**: `huggingface/candle`（15k+ stars）
- 🌟 无 Python 依赖的 Rust DL 框架，支持 CUDA/Metal；想在边缘/嵌入式跑 LLM 必选
- **应用场景**：端侧 LLM 推理、不装 PyTorch 的生产部署

### 21. **burn** — 多后端 Rust DL 框架
- **GitHub**: `tracel-ai/burn`（9k+ stars）
- 🌟 比 candle 更学术派，支持 WGPU/Candle/Tch 多后端；类型安全的张量
- **应用场景**：研究 / 跨平台模型

---

## 🖥 六、系统级 / 操作系统

### 22. **Linux kernel (Rust for Linux)**
- **GitHub**: `Rust-for-Linux/linux`
- 🌟 **2022 年首次合并进主线**，首个非 C 语言内核代码；驱动层是主要落地点
- **应用场景**：历史意义 > 实用价值（目前还在早期）

### 23. **Bottlerocket** (AWS) — 容器专用 Linux 发行版
- **GitHub**: `bottlerocket-os/bottlerocket`（9k+ stars）
- 🌟 AWS 出品，只为跑容器优化；大量用户空间工具是 Rust 写的
- **应用场景**：EKS 生产集群底层

### 24. **Redox OS** — 纯 Rust 操作系统
- **GitHub**: `redox-os/redox`
- 🌟 从内核到 shell 全 Rust，微内核设计
- **应用场景**：几乎纯研究 / 好玩；和 Asahi Linux GPU 驱动并列"Rust 最硬核 demo"

---

## 🔌 七、嵌入式 / 游戏 / 创意

### 25. **embassy** — 异步嵌入式框架 + **Bevy** 游戏引擎
- **GitHub**: `embassy-rs/embassy`（7k+ stars）、`bevyengine/bevy`（36k+ stars）
- 🌟 **embassy**：`no_std` + `async`，在单片机上跑协程；**Bevy**：ECS 架构游戏引擎，Rust 圈最活跃项目之一
- **应用场景**：embassy 做 IoT / 机器人；Bevy 做独立游戏 / 数据可视化
- 🔗 embassy 呼应本目录 [`25_pin_future_executor.rs`](./25_pin_future_executor.rs)（理解 `Pin` 后才知道它怎么在无堆环境下玩异步）

---

## 🎯 应用场景总结

| 场景 | 推荐 | 为什么 |
|---|---|---|
| **CLI 工具 / 开发者工具链** | ✅ Rust | 启动快、零依赖、单二进制分发 |
| **边缘代理 / 高性能网关** | ✅ Rust | Pingora / Cloudflare Workers 范式 |
| **区块链节点 / 智能合约** | ✅ Rust | Solana / Polkadot / Foundry 生态 |
| **向量数据库 / 搜索引擎** | ✅ Rust | Qdrant / Meilisearch / LanceDB |
| **嵌入式 / 实时系统** | ✅ Rust | `no_std` + 所有权 = 无 GC 安全内存 |
| **LLM 训练底层（tokenizer、data loader）** | ✅ Rust | Python 跑不动的地方 |
| **业务 Web 后端** | ⚠️ 够用但不是最优 | axum 能写，但 Scala/Elixir/Go 开发速度更快 |
| **数据分析 / 快速原型** | ❌ → Python / Scala | 编译器不会让你"随手试一下" |

---

## 💡 一句话总结

> **Rust 不是让你"写 FP"，而是让你"写 C 的地方也能有 FP 的直觉"。**

它的独特魅力在于把"零成本抽象 + 所有权 + 代数数据类型"揉进了一门系统级语言——你第一次发现**没有 GC 的代码也可以函数式**，会重新理解什么叫"不可变性"。

如果只挑 3 个最能体现 Rust 独特魅力的：

1. **ripgrep** — "Rust 重写 X" 范式的祖师爷，你每天都在用
2. **Pingora** — 大厂级别的证据：Cloudflare 用它替换 NGINX
3. **Solana / candle** — 区块链 + AI，两个未来 10 年不会消失的风口都在 Rust 上

---

## 🎯 推荐学习路径（对照本目录 25 个 demo）

| 已掌握 | 推荐玩什么 |
|---|---|
| 完成 01~07（FP 基础 + 所有权 + 智能指针） | **ripgrep** / **fd** 读源码；动手改一个 CLI 小工具 |
| 完成 08~14（异步 + 并发 + 生命周期） | **axum** 写 REST API；**tokio** 官方教程；读 **hyper** 源码 |
| 完成 15~21（序列化 + 错误 + 宏 + 属性测试） | **serde** 深入；**tracing** 接 OpenTelemetry；**proptest** 找 bug |
| 完成 22~25（unsafe + workspace + Pin） | **embassy** 烧 STM32；**candle** 跑 LLM 推理；读 **tokio** 内部 |

---

## 🔗 相关文档

- 跨语言对照：[`../LANGUAGE_COMPARISON.md`](../LANGUAGE_COMPARISON.md)
- 商业场景画像：[`../language_comparison_2.md`](../language_comparison_2.md)
- Haskell 同款盘点：[`../haskell/HASKELL_ECOSYSTEM.md`](../haskell/HASKELL_ECOSYSTEM.md)
- Erlang 同款盘点：[`../erlang/ERLANG_ECOSYSTEM.md`](../erlang/ERLANG_ECOSYSTEM.md)
- Elixir 同款盘点：[`../elixir/ELIXIR_ECOSYSTEM.md`](../elixir/ELIXIR_ECOSYSTEM.md)
