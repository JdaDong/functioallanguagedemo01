# Erlang 好玩的 SDK 和开源项目盘点

> 本文档整理 Erlang/BEAM 生态中**真正"好玩"或"工业级在用"**的 25 个项目，对应本目录 27 个 demo（[`01_pattern_matching.erl`](./01_pattern_matching.erl) ~ [`27_erl_trace_and_dbg.erl`](./27_erl_trace_and_dbg.erl)）打通"学完之后玩什么"的衔接。

Erlang 是个"低调的巨人"——你每天用的 WhatsApp、微信、Discord、RabbitMQ 背后都有它的影子，但开发者圈外几乎没人提它。它的"好玩"和 Haskell 完全不同：**Haskell 玩的是类型系统和数学优雅，Erlang 玩的是"让一台机器顶一万台、99.9999999% 可用、热更新不停机"**。

下面按 6 类组织，每个项目都标了 **🌟 趣味点** 和 **🔧 上手难度**。

---

## 📡 一、消息中间件 / 通信基础设施（Erlang 真正的统治区）

### 1. **RabbitMQ** — 最有名的消息队列
- **GitHub**: `rabbitmq/rabbitmq-server`（12k+ stars）
- 🌟 **趣味点**：业界标准的 AMQP 消息队列，**全世界几百万台服务器在跑它**，背后是 Erlang/OTP
- 单节点轻松扛住几十万消息/秒，集群天然支持
- 🔧 难度：⭐ 用：极易（apt install 就能跑）；改源码：⭐⭐⭐⭐
- **应用场景**：微服务解耦、异步任务队列、事件驱动架构（几乎所有大公司都用）
- 🔗 配合本目录 [`26_gen_event_pubsub.erl`](./26_gen_event_pubsub.erl) 食用

### 2. **ejabberd** — XMPP 即时通讯服务器
- **GitHub**: `processone/ejabberd`（5.6k+ stars）
- 🌟 **WhatsApp 早期就是 ejabberd 改的**，2 个工程师扛住 9 亿用户
- 支持 XMPP/MQTT/SIP，单机百万长连接
- **应用场景**：IM、推送、IoT 接入层

### 3. **MongooseIM** — 现代 XMPP 服务器
- **GitHub**: `esl/MongooseIM`（1.7k+ stars）
- 🌟 ejabberd 的"现代化分叉"，专注大规模即时通讯
- ESL（Erlang Solutions）团队维护，文档比 ejabberd 好很多

### 4. **VerneMQ** — 高性能 MQTT broker
- **GitHub**: `vernemq/vernemq`（3.3k+ stars）
- 🌟 主打 IoT，单机千万级连接，集群无单点
- **应用场景**：车联网、智能家居、工业 IoT

### 5. **EMQX** — 中国团队主导的 MQTT 巨头
- **GitHub**: `emqx/emqx`（14k+ stars）
- 🌟 **国产 Erlang 项目之光**，杭州映云科技；汽车厂、能源、工业自动化大量在用
- 单集群可扩展到 1 亿连接
- **应用场景**：蔚来/小鹏车联网、华为云 IoT、几乎所有新能源车企

---

## 💬 二、聊天 / 游戏 / 社交（你每天在用的）

### 6. **WhatsApp**（闭源，但是 Erlang 的代表作）
- 🌟 2011 年 32 个工程师 + Erlang，扛住 4.5 亿用户；2 个 Erlang 工程师就能维护一台机器扛 200 万长连接
- 这是 Facebook 花 190 亿美金买它的原因之一
- 业界永恒的"小团队大产品"传奇

### 7. **Discord**（闭源，混合架构）
- 🌟 用 Elixir（Erlang 虚拟机上的语言）做实时消息分发；几百万并发语音/文字房间
- 公开技术博客里讲过怎么用 GenServer + Phoenix Channels 扛流量

### 8. **Cowboy** — 高性能 HTTP/2 + WebSocket 服务器
- **GitHub**: `ninenines/cowboy`（7.5k+ stars）
- 🌟 Erlang 生态最快的 HTTP 服务器，原生支持 HTTP/2 和 WebSocket
- **应用场景**：作为底层组件被 Phoenix、ChicagoBoss 等框架用
- 🔗 配合本目录 [`13_gen_tcp_echo_server.erl`](./13_gen_tcp_echo_server.erl) 食用

### 9. **Wings 3D** — 3D 建模软件
- **GitHub**: `dgud/wings`（1.5k+ stars）
- 🌟 **居然有人用 Erlang 写 3D 建模工具**，专注细分曲面建模，跨平台
- 和 Blender 同期的老牌软件，有忠实粉丝

---

## 💾 三、分布式数据库（Erlang 强项）

### 10. **CouchDB** — 文档型数据库
- **GitHub**: `apache/couchdb`（6.1k+ stars）
- 🌟 Apache 顶级项目，HTTP/JSON 接口，多主复制天然分布式
- **应用场景**：离线优先 app、移动端同步（PouchDB 配合）

### 11. **Riak** — 分布式 KV 数据库
- **GitHub**: `basho/riak`（已停更但仍有用户）
- 🌟 Amazon Dynamo 论文的开源实现，无单点、CRDT 数据类型
- 历史价值高：教科书级别的分布式系统案例

### 12. **AntidoteDB** — 地理分布式 CRDT 数据库
- **GitHub**: `AntidoteDB/antidote`（900+ stars）
- 🌟 学术界的"理想分布式数据库"参考实现，强一致 + 高可用
- 看完想真懂 CAP 定理就读它

### 13. **Mnesia** — Erlang 自带的分布式数据库
- 🌟 **OTP 标准库自带**，无需安装；表可以分布在多节点，事务、热备一应俱全
- 适合"一个 Erlang 集群自己就是数据库"的小型系统
- 🔗 你已经在 [`11_mnesia_transactional_store.erl`](./11_mnesia_transactional_store.erl) 玩过了

---

## 🎮 四、游戏服务器（小众但出彩）

### 14. **gen_rpc** — 高性能 RPC
- **GitHub**: `priestjim/gen_rpc`（700+ stars）
- 🌟 Erlang 集群间 RPC，比内置 `rpc` 快 5-10 倍
- 游戏公司常用作内部通信层
- 🔗 配合本目录 [`07_distributed_nodes.erl`](./07_distributed_nodes.erl) 食用

### 15. **MMORPG 后端**（多个商业项目使用）
- 🌟 国内代表：网龙的几款 MMO、巨人网络早期项目
- 国外：《英雄联盟》早期聊天系统是 Erlang
- **应用场景**：玩家实时同步、房间匹配、战斗服

### 16. **Nakama**（Go 写的，但思想从 Erlang 偷的）
- 提一下作为对比：很多现代游戏后端用 Go/Rust 重写了 Erlang 的 actor 模型

---

## 🔬 五、Erlang/Elixir 生态语言

### 17. **Elixir** — Erlang VM 上的现代语言
- **GitHub**: `elixir-lang/elixir`（24k+ stars）
- 🌟 José Valim 创造，Ruby 风格 + Erlang 内核；过去 5 年 BEAM 生态实际靠它复活
- 你仓库里 [`../elixir/`](../elixir) 已经有 demo
- **应用场景**：Web（Phoenix）、嵌入式（Nerves）、机器学习（Nx）
- 🔗 本目录 [`25_elixir_vs_erlang.erl`](./25_elixir_vs_erlang.erl) 已经做过对比

### 18. **Phoenix Framework** — Elixir 的 Rails
- **GitHub**: `phoenixframework/phoenix`（21k+ stars）
- 🌟 **LiveView 是这两年前端圈的话题**：服务端渲染 + WebSocket，不写 JS 也能做交互
- 单机轻松扛 200 万 WebSocket 连接

### 19. **Nerves** — Erlang 跑嵌入式
- **GitHub**: `nerves-project/nerves`（2k+ stars）
- 🌟 用 Elixir 烧 Raspberry Pi、工业网关；OTP 的"99.99% 可用"用在硬件上
- **应用场景**：智能农业、工业控制、家用 NAS

### 20. **Gleam** — BEAM 上的静态类型语言
- **GitHub**: `gleam-lang/gleam`（17k+ stars）
- 🌟 **2024 最热门的 BEAM 语言**：Erlang 的并发 + Rust 风格的类型系统
- 上手最快、最现代化的 BEAM 语言

### 21. **LFE (Lisp Flavored Erlang)** — Erlang 上的 Lisp
- **GitHub**: `lfe/lfe`（1.5k+ stars）
- 🌟 在 Erlang 虚拟机上写 Lisp，宏系统 + actor，好玩程度爆表

---

## 🛠 六、工具 / 监控 / DevOps

### 22. **Observer / observer_cli** — 在线诊断
- **GitHub**: `zhongwencool/observer_cli`（2.1k+ stars）
- 🌟 终端版进程监控（GUI Observer 的 CLI 版），生产环境必备
- 实时看每个 Actor 的 mailbox、内存、CPU——**别的语言根本做不到这种粒度**
- 🔗 配合本目录 [`19_recon_observer_introspect.erl`](./19_recon_observer_introspect.erl)、[`27_erl_trace_and_dbg.erl`](./27_erl_trace_and_dbg.erl) 食用

### 23. **Rebar3 / Mix** — 构建工具
- **GitHub**: `erlang/rebar3`（1.4k+ stars）
- 🌟 Erlang 版的 Cargo/npm；Elixir 的 Mix 更现代
- 🔗 你已经在 [`24_rebar3_project_skeleton.erl`](./24_rebar3_project_skeleton.erl) 玩过了

### 24. **Concuerror** — 并发 bug 检测
- **GitHub**: `parapluu/Concuerror`（700+ stars）
- 🌟 学术界做的"穷举所有进程交错可能"工具，用来找死锁/竞态
- 看完会感叹"原来并发还能这么测"

### 25. **PropEr / QuickCheck** — 性质测试
- **GitHub**: `proper-testing/proper`（900+ stars）
- 🌟 Erlang 的 QuickCheck 实现；"给个性质，工具帮你生成上千测试用例找反例"
- John Hughes 的传奇演讲《Testing the Hard Stuff and Staying Sane》就是讲它
- 🔗 你已经在 [`08_property_testing_proper.erl`](./08_property_testing_proper.erl) 玩过了

---

## 🎯 应用场景总结（对比 Haskell）

| 场景 | 推荐 | 为什么 |
|---|---|---|
| **千万级长连接（IM/IoT/游戏）** | ✅ Erlang | actor 模型 + 抢占式调度，C10M 是基本盘 |
| **消息中间件 / 队列** | ✅ Erlang | RabbitMQ/EMQX 行业标准 |
| **高可用电信级系统** | ✅ Erlang | "9 个 9" 可用性的祖师爷（爱立信电信交换机） |
| **分布式数据库** | ✅ Erlang | CouchDB/Riak/Mnesia |
| **嵌入式 / IoT 网关** | ✅ Erlang (Nerves) | 单机长跑、热更新 |
| **类型安全 / 数学建模** | ❌ → Haskell | Erlang 是动态类型 |
| **金融定价 / 编译器** | ❌ → Haskell | 强类型才是这块的杀器 |

---

## 💡 一句话总结

> **Haskell 是"用类型证明你不会出错"，Erlang 是"出错了系统也不挂"。**

如果只挑 3 个最能体现 Erlang 独特魅力的：

1. **RabbitMQ / EMQX** — 你每天在用 Erlang 写的东西却不知道
2. **WhatsApp 故事** — 32 个工程师 + Erlang = 4.5 亿用户，业界传奇
3. **Phoenix LiveView** — 不写 JS 做现代交互前端，颠覆思路

---

## 🎯 推荐学习路径（对照本目录 27 个 demo）

| 已掌握 | 推荐玩什么 |
|---|---|
| 完成 01~06（基础 + actor + GenServer） | **RabbitMQ** 跑起来发消息；读 **Cowboy** 源码 |
| 完成 07~12（分布式 + 热升级） | **CouchDB** / **Riak** 集群；**gen_rpc** 替换内置 RPC |
| 完成 13~18（网络 + 工程化） | **EMQX** 跑 MQTT 接入；**Phoenix LiveView** 写聊天室 |
| 完成 19~27（诊断 + 跨语言） | **observer_cli** 接生产；**Gleam** 试静态类型；**Nerves** 烧 Raspberry Pi |

---

## 🔗 相关文档

- 跨语言对照：[`../LANGUAGE_COMPARISON.md`](../LANGUAGE_COMPARISON.md)
- 商业场景画像：[`../language_comparison_2.md`](../language_comparison_2.md)
- Haskell 同款盘点：[`../haskell/HASKELL_ECOSYSTEM.md`](../haskell/HASKELL_ECOSYSTEM.md)
- Elixir demo 目录：[`../elixir/`](../elixir)
