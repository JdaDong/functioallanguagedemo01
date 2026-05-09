# Elixir 好玩的 SDK 和开源项目盘点

> 本文档整理 Elixir/BEAM 生态中**真正"好玩"或"工业级在用"**的 25 个项目，对应本目录 15 个 demo（[`01_basics_pipeline.exs`](./01_basics_pipeline.exs) ~ [`15_mix_umbrella_releases.exs`](./15_mix_umbrella_releases.exs)）打通"学完之后玩什么"的衔接。

Elixir 是"老兵换上现代装备"——和 Erlang 共享 BEAM 虚拟机，但叠了 Ruby 风格语法、强大的宏系统、现代化工具链（Mix/Hex/ExDoc）。过去 5 年 BEAM 生态的"复活"几乎全靠它：Phoenix LiveView 让前端圈重新关注 BEAM、Nx 让 Elixir 第一次能搞机器学习、Nerves 把"高可用 + 热升级"延伸到嵌入式硬件。

下面按 6 类组织，每个项目都标了 **🌟 趣味点** 和 **🔧 上手难度**。

> 📎 与 [`../erlang/ERLANG_ECOSYSTEM.md`](../erlang/ERLANG_ECOSYSTEM.md) 部分项目（Phoenix / Nerves / Elixir 自身）有交集，本文站在 Elixir 视角重新展开，并补全 Erlang 文档没覆盖的 ML / 工具链生态。

---

## 🌐 一、Web 后端（Elixir 的主场，最大就业市场）

### 1. **Phoenix Framework** — 旗舰 Web 框架
- **GitHub**: `phoenixframework/phoenix`（21k+ stars）
- 🌟 **趣味点**：Elixir 版的 Rails，但**单机能扛 200 万 WebSocket 长连接**——这是 Rails 永远做不到的
- 路由 + 控制器 + 模板 + Channels（实时） + LiveView（响应式）一站式
- 🔧 难度：⭐⭐ 写 CRUD 易，理解 Plug/Channel/LiveView 架构需要时间
- **应用场景**：实时 Web、聊天、协作工具、SaaS 后端
- **谁在用**：Discord、Pinterest（部分服务）、Bleacher Report、Heroku 部分服务
- 🔗 配合本目录 [`10_phoenix_router_plug.exs`](./10_phoenix_router_plug.exs) 食用

### 2. **Phoenix LiveView** 🔥 这两年最火
- **GitHub**: `phoenixframework/phoenix_live_view`（6.4k+ stars）
- 🌟 **不写一行 JS 做现代交互前端**：服务端渲染 HTML + WebSocket diff 推送
- 等于 React + Redux + WebSocket 的体验，但全在 Elixir 写
- 🔧 难度：⭐⭐ 写 Phoenix 之后顺手就会
- **应用场景**：内部 admin、SaaS dashboard、实时协作；近两年大量项目从 React 迁回来
- 🔗 配合本目录 [`11_liveview_mental_model.exs`](./11_liveview_mental_model.exs) 食用

### 3. **Absinthe** — Elixir 的 GraphQL
- **GitHub**: `absinthe-graphql/absinthe`（4.4k+ stars）
- 🌟 GraphQL 服务端实现，订阅（Subscription）天然走 Phoenix Channels（其它语言要专门搭一套 WebSocket）
- **应用场景**：移动端 BFF、需要复杂查询的 API

### 4. **Plug** — Elixir 的中间件抽象
- **GitHub**: `elixir-plug/plug`（3k+ stars）
- 🌟 类似 Ruby Rack / Go net/http middleware；Phoenix 底层就是 Plug
- 简洁到 100 行就能跑一个 Web 服务
- 🔗 配合本目录 [`10_phoenix_router_plug.exs`](./10_phoenix_router_plug.exs) 食用

### 5. **Ecto** — 数据库工具包
- **GitHub**: `elixir-ecto/ecto`（6.3k+ stars）
- 🌟 不是"ORM"，是 query builder + changeset（数据校验/转换 DSL） + multi（事务编排）
- changeset 比所有 ORM 的 validation 思路都干净
- 🔗 配合本目录 [`09_ecto_repo_changeset_multi.exs`](./09_ecto_repo_changeset_multi.exs) 食用

---

## 🔢 二、机器学习 / 数据科学（Elixir 这两年最大惊喜）

### 6. **Nx** — Elixir 的 NumPy/JAX
- **GitHub**: `elixir-nx/nx`（2.7k+ stars）
- 🌟 José Valim（Elixir 之父）2021 年开始亲自带头做的项目
- 张量计算 + GPU/TPU 后端（通过 EXLA 走 XLA），可以跑 PyTorch/JAX 的活
- 🔧 难度：⭐⭐ 用过 NumPy 就会用
- **应用场景**：在 Phoenix Web 服务里直接嵌入推理、不用切 Python

### 7. **Axon** — Elixir 的 Keras
- **GitHub**: `elixir-nx/axon`（1.6k+ stars）
- 🌟 神经网络高层 API，构建在 Nx 之上；Keras 风格的层堆叠 + 训练循环
- **应用场景**：训练小模型、Phoenix 应用内嵌 ML

### 8. **Bumblebee** — Hugging Face 模型直接跑 🔥
- **GitHub**: `elixir-nx/bumblebee`（1.4k+ stars）
- 🌟 **直接加载 Hugging Face 上的预训练模型**（BERT / Stable Diffusion / Whisper），10 行代码做推理
- **应用场景**：在 Elixir 后端做文本分类、图片生成、语音转写，不用部署 Python 微服务

### 9. **Explorer** — Elixir 的 pandas
- **GitHub**: `elixir-nx/explorer`（1.4k+ stars）
- 🌟 DataFrame 库，底层是 Rust 的 Polars（性能爆炸）
- **应用场景**：数据清洗、ETL，在 Elixir 里干 pandas 的活

### 10. **Livebook** 🔥 最好玩的一个
- **GitHub**: `livebook-dev/livebook`（4.7k+ stars）
- 🌟 **Elixir 版的 Jupyter Notebook**，但比 Jupyter 多两个杀器：
  - 协作编辑（多人实时编辑同一个 notebook，Google Docs 体验）
  - 内置完整 Phoenix LiveView 渲染（图表、表单、Kino 交互组件）
- **应用场景**：数据探索、教学、做内部工具的 dashboard
- 🌟🌟 José Valim 演讲里经常拿 Livebook 现场写代码做演示，体验非常好

---

## 📡 三、嵌入式 / IoT（Nerves 生态）

### 11. **Nerves** — Elixir 烧硬件
- **GitHub**: `nerves-project/nerves`（2k+ stars）
- 🌟 用 Elixir 在 Raspberry Pi、BeagleBone、各种嵌入式板子上跑应用
- 自带 OTA 升级、A/B 分区、热代码升级
- **应用场景**：智能农业（Farmbot 用它）、工业网关、家用 NAS、车载 ECU

### 12. **NervesHub** — 设备 OTA 管理平台
- **GitHub**: `nerves-hub/nerves_hub_web`（500+ stars）
- 🌟 配套 Nerves 的"设备管理控制台"，几万台设备可控
- **应用场景**：商业 IoT 产品（卖硬件的公司）

### 13. **Scenic** — Elixir 原生 GUI
- **GitHub**: `boydm/scenic`（2k+ stars）
- 🌟 跨平台 GUI 框架，专注**嵌入式设备的小屏幕 UI**（不是桌面应用）
- **应用场景**：车载 HMI、工业仪表盘、Raspberry Pi 触屏

---

## 💬 四、消息 / 实时 / 多媒体

### 14. **Discord 的 Elixir 服务**（闭源但有公开技术博客）
- 🌟 几百万并发语音房间靠 Elixir + Rust 扛
- 公开博客《Using Rust to Scale Elixir for 11 Million Concurrent Users》是经典阅读

### 15. **Membrane Framework** — Elixir 多媒体框架
- **GitHub**: `membraneframework/membrane_core`（1.1k+ stars）
- 🌟 流媒体处理（音视频转码、WebRTC、RTMP），思路像 GStreamer
- **应用场景**：直播平台、视频会议、流媒体 CDN

### 16. **Broadway** — 多阶段数据管道
- **GitHub**: `dashbitco/broadway`（2.5k+ stars）
- 🌟 Plataformatec/Dashbit（Elixir 团队）做的 Kafka/SQS/RabbitMQ 消费者框架
- **应用场景**：日志处理、ETL、消息消费
- 🔗 配合本目录 [`12_flow_genstage_broadway.exs`](./12_flow_genstage_broadway.exs) 食用

### 17. **GenStage / Flow** — 背压式数据流
- **GitHub**: `elixir-lang/gen_stage`（1.7k+ stars） / `dashbitco/flow`（1.6k+ stars）
- 🌟 OTP 风格的"生产者-消费者"抽象，自动背压；Flow 在其上做并行集合操作
- **应用场景**：流式数据处理、限流、并行 map/reduce
- 🔗 配合本目录 [`12_flow_genstage_broadway.exs`](./12_flow_genstage_broadway.exs) 食用

---

## 🛠 五、工具链 / 测试 / 发布

### 18. **Mix** — Elixir 的构建工具
- 🌟 Elixir 自带，**比 Erlang 的 Rebar3 现代得多**：依赖管理、任务运行、测试、发布全在一起
- 体验接近 Cargo / npm
- 🔗 配合本目录 [`15_mix_umbrella_releases.exs`](./15_mix_umbrella_releases.exs) 食用

### 19. **Hex.pm** — 包管理仓库
- 🌟 Elixir/Erlang 共用的包仓库（类似 crates.io / npm registry）
- 17000+ 个包，质量比 Erlang 时代高一个量级

### 20. **ExUnit + Mox + StreamData** — 测试三件套
- 🌟 ExUnit 是 Elixir 自带，**异步测试默认开启**（每个测试模块独立进程并行跑）
- Mox：基于 behaviour 的显式 mock；StreamData：性质测试
- 🔗 配合本目录 [`14_exunit_doctest_mox_streamdata.exs`](./14_exunit_doctest_mox_streamdata.exs) 食用

### 21. **Credo** — 静态分析 / Lint
- **GitHub**: `rrrene/credo`（4.9k+ stars）
- 🌟 代码风格检查 + 复杂度分析；几乎所有 Elixir 项目都在用

### 22. **Dialyxir** — 类型推断检查
- **GitHub**: `jeremyjh/dialyxir`（1.9k+ stars）
- 🌟 把 Erlang 的 Dialyzer 包装得好用了；动态类型语言里的"半静态保险"

### 23. **Telemetry + OpenTelemetry** — 可观测性
- **GitHub**: `beam-telemetry/telemetry`（1.1k+ stars）
- 🌟 BEAM 生态统一的指标/事件标准；Phoenix/Ecto/Broadway 都暴露 telemetry 事件
- 🔗 配合本目录 [`13_telemetry_opentelemetry.exs`](./13_telemetry_opentelemetry.exs) 食用

---

## 🚀 六、新方向 / 周边创意

### 24. **Oban** — Postgres 上的后台任务
- **GitHub**: `sorentwo/oban`（3.4k+ stars）
- 🌟 用 Postgres 当队列做定时任务/重试/优先级；不用额外起 Redis/RabbitMQ
- **应用场景**：SaaS 系统里的邮件发送、定时报表、Webhook 重试

### 25. **Gleam**（BEAM 上的静态类型语言）
- **GitHub**: `gleam-lang/gleam`（17k+ stars）
- 🌟 **2024 最热门的 BEAM 语言**：Erlang 的并发 + Rust 风格的类型系统
- 和 Elixir 互相调用无缝；想要静态类型时的下一站
- ⚠️ 严格说不是 Elixir 项目，但和 Elixir 共享生态、可以混用

---

## 🎯 应用场景总结（对比 Erlang / Haskell）

| 场景 | 推荐 | 理由 |
|---|---|---|
| **现代 Web 后端 / SaaS** | ✅ Elixir (Phoenix) | Rails 体验 + BEAM 并发 |
| **实时交互前端（不想写 React）** | ✅ Elixir (LiveView) | 独门绝技 |
| **服务端嵌 ML 推理** | ✅ Elixir (Nx + Bumblebee) | 不用切 Python 微服务 |
| **嵌入式 / IoT 商业产品** | ✅ Elixir (Nerves) | 比裸 Erlang 更现代 |
| **千万长连接电信级中间件** | ⚖️ Elixir 或 Erlang 都行 | Erlang 更稳，Elixir 更易招人 |
| **跨语言系统底层组件（Rabbit/EMQX）** | ✅ Erlang | 这些项目历史就是 Erlang |
| **类型安全 / 金融建模** | ❌ → Haskell | 动态类型不擅长 |

---

## 💡 一句话总结

> **Erlang 是"老兵的瑞士军刀"，Elixir 是"老兵换上现代装备 + 新一代年轻人"。同一个 BEAM 虚拟机，谁更适合取决于你团队招谁更容易。**

如果只挑 3 个最能体现 Elixir 独特魅力的：

1. **Phoenix LiveView** — 不写 JS 做现代交互前端，前端圈这两年的话题
2. **Livebook + Nx + Bumblebee** — 让 Elixir 第一次能搞 ML，不用羡慕 Python
3. **Nerves** — 把 BEAM 的"高可用 + 热升级"用在硬件上，独此一家

---

## 🎯 推荐学习路径（对照本目录 15 个 demo）

| 已掌握 | 推荐玩什么 |
|---|---|
| 完成 01~05（基础 + 宏 + DSL） | 读 **Plug** 源码，体会"中间件链"；用 **Mix** 起一个新项目 |
| 完成 06~08（GenServer + Supervisor + Task） | **Oban** 做后台任务；**Broadway** 接 Kafka/RabbitMQ |
| 完成 09~11（Ecto + Phoenix + LiveView） | 用 **Phoenix + LiveView** 写一个实时聊天室；**Absinthe** 搭 GraphQL |
| 完成 12~13（数据流 + 可观测） | **Livebook** 跑 **Nx** + **Bumblebee** 做一次模型推理 |
| 完成 14~15（测试 + 发布） | **Nerves** 烧一片 Raspberry Pi；**Gleam** 试试静态类型 BEAM |

---

## 🔗 相关文档

- 学习路线：[`ELIXIR_FP_ROADMAP.md`](./ELIXIR_FP_ROADMAP.md)（15 个 demo 详解）
- 跨语言对照：[`../LANGUAGE_COMPARISON.md`](../LANGUAGE_COMPARISON.md)
- 商业场景画像：[`../language_comparison_2.md`](../language_comparison_2.md)
- Erlang 同款盘点：[`../erlang/ERLANG_ECOSYSTEM.md`](../erlang/ERLANG_ECOSYSTEM.md)
- Haskell 同款盘点：[`../haskell/HASKELL_ECOSYSTEM.md`](../haskell/HASKELL_ECOSYSTEM.md)
