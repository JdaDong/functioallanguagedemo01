# Elixir FP 学习路线图

## 你现在的位置

你现在这套仓库里的 `Elixir FP` Demo，已经正式进入 **工程化收官阶段**。当前 `01` 到 `15` 号 Demo 已经系统串起了：

- 基础语法：模块、函数、私有函数
- 管道操作符 `|>` 把嵌套调用拍平成数据流
- 模式匹配与守卫 `when`
- `defstruct` 带默认值的记录 + 编译期字段检查
- `defprotocol` / `defimpl` —— 运行时 type class 风格的开放多态
- `@behaviour` + `@callback` —— 接口契约 + `@impl true` 静态检查
- `{:ok, value}` / `{:error, reason}` 作为事实上的 Result
- `with` 表达式 —— Monad do-notation 的 Elixir 方言
- `quote` / `unquote` / `defmacro` —— AST 即数据
- 卫生宏（hygienic macro）与变量捕获避免
- 自制路由 DSL —— `@before_compile` 在编译期生成 `match/2`
- `GenServer` —— OTP 的同步/异步 server 行为
- `Agent` —— 轻量状态持有者
- `Task` / `Task.async` / `Task.await` —— 一次性异步任务
- 静态 `Supervisor` + `:one_for_one` / `:rest_for_one` 重启策略
- `DynamicSupervisor` —— 按需动态拉起子进程
- `Registry` —— 进程命名服务 `{:via, Registry, {Reg, key}}`
- `Task.Supervisor` 统一托管异步任务生命周期
- `async_stream/3` —— 有监督的并发迭代器，自带限流、超时、`ordered`
- `Ecto.Schema` + `Ecto.Changeset` —— 函数式 ORM + 校验管道
- `Ecto.Repo` + `Ecto.Multi` —— 单事务内编排多条操作
- Plug + Cowboy —— Phoenix 风格的 Web 管道
- `Plug.Router` DSL —— 路由、中间件、日志都是 `fn conn -> conn end`
- LiveView 心智模型 —— `mount/3` + `handle_event/3` + diff 推送
- `GenStage` / `Flow` / `Broadway` —— 带背压的流式管道三件套
- `:telemetry` + OpenTelemetry —— 结构化事件 + 标准 trace/metric 桥接
- ExUnit + doctest —— 异步单测 + 文档即测试
- Mox + StreamData —— 基于 behaviour 的 mock 与 property-based 测试
- mix / umbrella / releases —— 构建工具、多应用 monorepo、自包含发布包

这意味着你已经不只是会写 `|>` 和 `Enum.map/2`，而是开始把这些思想真正推进到：**用管道把业务流拍平、用 `with` 把失败短路、用 Protocol/Behaviour 表达多态、用宏把重复的样板代码升到语法层、用 OTP 三件套表达并发与隔离、用 Supervisor 树表达可靠性、用 Ecto 表达函数式数据库访问、用 Phoenix/LiveView 表达服务端渲染的实时界面、用 GenStage/Flow/Broadway 表达带背压的数据流、用 Telemetry/OpenTelemetry 把可观测性接进标准生态、用 ExUnit/Mox/StreamData 把测试基础设施彻底工程化，再用 mix/umbrella/releases 把整套东西打包成可发布的系统**。

---

## 总体学习路线

| 阶段 | 目标 | 关键词 | 建议技术栈 |
|---|---|---|---|
| **入门** | 理解 Elixir 的函数式风格与管道心智 | 管道 `|>`、模式匹配、递归、`Enum`/`Stream` | Elixir 标准库 |
| **类型与多态** | 用 struct / protocol / behaviour 三件套表达能力 | `defstruct`、`defprotocol`、`@behaviour` | Elixir 标准库 |
| **控制流** | 把失败显式放进 tuple，用 `with` 拍平短路 | `{:ok, _}` / `{:error, _}`、`with` | Elixir 标准库 |
| **元编程** | 理解 "代码即数据" 并能写出小 DSL | `quote` / `unquote` / `defmacro`、`@before_compile` | Elixir 标准库 |
| **OTP 行为** | 掌握并发与状态的三件套 | `GenServer`、`Agent`、`Task` | Elixir 标准库 / OTP |
| **监督与命名** | 用监督树保证可靠性，用 Registry 做 key→pid | `Supervisor`、`DynamicSupervisor`、`Registry` | Elixir 标准库 / OTP |
| **并发迭代** | 用 `async_stream` 把集合变成有监督的并行管道 | `Task.Supervisor`、`async_stream/3` | Elixir 标准库 / OTP |
| **数据库** | 用函数式 ORM 表达模式、校验、事务 | `Ecto.Schema` / `Changeset` / `Repo` / `Multi` | `ecto`、`postgrex` |
| **Web 服务** | 把请求组织成 Plug 管道 | `Plug`、`Plug.Router`、Cowboy | `plug`、`plug_cowboy` |
| **实时界面** | 理解 LiveView 的服务器端状态机心智 | `mount/3`、`handle_event/3`、diff | `phoenix_live_view`（心智模型版本不强依赖） |
| **流式管道** | 补齐带背压的数据流生态 | `GenStage`、`Flow`、`Broadway` | `gen_stage`、`flow`、`broadway` |
| **可观测性** | 把运行时事件接进标准 trace/metric 生态 | `:telemetry`、OpenTelemetry | `telemetry`、`opentelemetry_*` |
| **测试基础设施** | 把 async unit test、doctest、mock、property test 一起打包 | `ExUnit`、`doctest`、`Mox`、`StreamData` | `ex_unit`、`mox`、`stream_data` |
| **工程化** | 把所有东西打包成可发布的系统 | `mix`、`umbrella`、`releases`、`runtime.exs` | `mix`、`distillery`（可选）、Erlang/OTP ERTS |

---

## 第一阶段：入门

### 目标

这一阶段的重点是：**从"会写 Enum.map/2"变成"能用 Elixir 的管道心智组织数据流"。**

### 重点学习内容

- **管道 `|>`**：把嵌套调用 `c(b(a(x)))` 写成 `x |> a() |> b() |> c()`；规则是"左侧值自动作为右侧函数的第一个参数"
- **模式匹配**：不是"比较相等"，而是**按结构进行分解和绑定**；`{:ok, v}` / `[h | t]` / `%User{name: n}` 都是一等公民
- **守卫 `when`**：在匹配层做类型与范围过滤，比 `if/else` 更声明式
- **递归 + 尾递归**：没有传统循环；BEAM 保证尾递归零栈增长，所以 `defp loop(n, acc)` 是主力武器
- **`Enum` vs `Stream`**：`Enum` 立即求值返回新集合，`Stream` 惰性求值组合到 `Enum.to_list/Enum.reduce` 时才推动

### 建议学习顺序

1. **Demo 01** 基础语法 + 管道 + 模式匹配 —— 建立最核心的手感
2. **Demo 02** struct / protocol / behaviour —— 把"自定义类型"三个层次分清楚
3. **Demo 03** `with` × Result 流程 —— 学会用 tuple + `with` 代替一层层的 `case`

### 学完应该能做

- 看到嵌套调用能自觉想到用 `|>` 重写
- 看到 `if/else` 能判断能不能改写成模式匹配 + 守卫
- 看到一串 `case {:ok, _} -> ... end` 能自觉想到用 `with` 拍平
- 看到相似的"字段 + 方法"结构，能决定该用 struct、protocol 还是 behaviour

---

## 第二阶段：元编程

### 目标

**从"会用别人的宏"变成"能写自己的小 DSL"。**

Elixir 最独特的一张牌就是宏 —— Phoenix Router、Ecto Schema、ExUnit `test "..." do ... end` 本质都是宏生成的。理解宏机制，等于把这些框架的"黑盒"打开。

### 重点学习内容

- **AST 即数据**：`quote do: 1 + 2` 返回的是 `{:+, [], [1, 2]}` 这样的三元组
- **`unquote` 注入变量**：`quote do: unquote(x) + 1` 把外部 `x` 的值拼进模板
- **`unquote_splicing`**：处理列表的展开
- **卫生宏**：Elixir 的宏默认会把宏内引入的变量重命名，避免捕获调用方的变量
- **`@before_compile`**：模块编译完成前最后一步生成代码 —— Router/Schema 都在这一步把"收集起来的声明"变成真正的函数
- **`Macro.expand/2`**：在 IEx 里观察宏展开的结果，是调试宏的唯一救命稻草

### 建议学习顺序

1. **Demo 04** 宏入门 —— `quote` / `unquote` / `defmacro`，建立"代码是数据"的直觉
2. **Demo 05** 宏进阶：自制路由 DSL —— 用 `@before_compile` + 模块属性累加，把声明式 DSL 编译成 `match/2`

### 学完应该能做

- 看到 Phoenix Router `get "/users", UserController, :index` 不再觉得是魔法
- 能写一个属于自己的 `deflog` / `defroute` 小宏
- 能用 `Macro.expand/2` 在 IEx 里调试宏
- 知道"宏能做的事 99% 场景下普通函数也能做"，从而克制地使用宏

---

## 第三阶段：OTP 并发基石

### 目标

**从"会用 spawn"变成"会用 OTP 行为抽象组织并发系统"。**

这是 Elixir / Erlang 最独特的一张牌：进程不是 OS 线程的封装，而是 **BEAM 虚拟机层面的轻量级隔离单元**。一个 BEAM 节点可以跑几百万进程，每个进程独占自己的堆和消息邮箱。OTP 在这个基础上抽出了三件套：`GenServer` / `Agent` / `Task`。

### 重点学习内容

- **GenServer**：同步 `handle_call` + 异步 `handle_cast` + 自发 `handle_info`；状态 = 每次回调返回的第二个元素，天然就是 `fold` 消息流
- **Agent**：只持有状态 + `get/update`，不需要自定义消息；非常适合计数器、缓存、配置
- **Task / Task.async / Task.await**：一次性异步任务；用 `Task.yield` / `Task.await/2` 做超时
- **进程就是最小隔离单元**：一个 crash 不会带倒另一个
- **消息传递**：`send(pid, msg)` + `receive do ... end`，但 OTP 行为帮你把这层封装好了

### 建议学习顺序

1. **Demo 06** GenServer / Agent / Task —— 三件套同场对比，理解各自的适用场景

### 学完应该能做

- 看到"需要保持一份可变状态"能立刻判断：单值用 `Agent`，行为复杂用 `GenServer`
- 看到"一次性把某段工作丢到后台"能立刻想到 `Task.async` + `Task.await`
- 理解"状态 = fold 消息流"这个函数式精髓：`GenServer` 的 `handle_call(msg, _, state)` 本质就是 `(msg, state) -> (reply, new_state)`
- 看到 Scala cats-effect 的 `Ref` / Akka Typed Actor 能立刻类比到 Elixir 的 Agent / GenServer

---

## 第四阶段：监督树与命名

### 目标

**从"会启动单个进程"变成"会用监督树保证整套系统自愈"。**

OTP 的另一张招牌是 **"let it crash" 哲学**：不要在业务代码里写 try/catch 防御一切异常；让进程崩溃，让监督者按策略重启。这条路径最终长出了 Supervisor 树 + DynamicSupervisor + Registry 这三件套。

### 重点学习内容

- **Supervisor**：静态子进程列表 + 重启策略（`:one_for_one` / `:one_for_all` / `:rest_for_one`）
- **DynamicSupervisor**：运行时动态 `start_child`；非常适合"每个房间/每个用户会话一个进程"
- **Registry**：`{:via, Registry, {MyReg, key}}` 把进程按 key 命名，崩溃自动注销
- **重启策略的三种语义**：出现故障是只重启我、重启同组、还是重启我和后面所有兄弟
- **max_restarts / max_seconds**：熔断抖动，避免无限重启风暴

### 建议学习顺序

1. **Demo 07** Supervisor / DynamicSupervisor / Registry —— 三件套组合，演示"按 key 开房间 + 崩溃重启 + 自动清理"

### 学完应该能做

- 看到"每个聊天室一个进程"能立刻想到 DynamicSupervisor + Registry 的经典组合
- 看到"系统需要在某个子模块崩溃时自愈"能立刻画出监督树
- 能解释为什么 Elixir / Erlang 的监控不只是"日志记录"，而是"结构化的重启决策"
- 理解 Akka Cluster Sharding / Kubernetes Pod 其实都是在不同层次上模仿监督树

---

## 第五阶段：并发迭代

### 目标

**从"会启动并发任务"变成"会用 `async_stream` 把集合变成并行管道"。**

### 重点学习内容

- **Task.Supervisor**：统一托管异步任务的生命周期，任何一个 task crash 不会传染主进程
- **async_stream/3**：一个有监督的 `pmap`；带 `max_concurrency` 限流、`timeout` 超时、`ordered: false` 打破顺序加速
- **Stream.map + async_stream 的组合拳**：上游 `Stream.map` 做纯计算，下游 `async_stream` 做并发副作用
- **失败隔离**：单个元素处理失败不会中断整条管道，可用 `:on_timeout` / `on_exit` 配置行为

### 建议学习顺序

1. **Demo 08** Task.Supervisor × async_stream —— 理解"有监督的并发迭代"这个核心模式

### 学完应该能做

- 看到"给一批 URL 并发拉数据"能立刻想到 `Task.Supervisor.async_stream_nolink/4`
- 看到"fs2 parEvalMap" / "Rust buffer_unordered" 能立刻类比到 async_stream
- 理解并发限流（`max_concurrency`）和超时（`timeout`）比无脑 `Enum.map(&Task.async/1)` 重要一百倍

---

## 第六阶段：数据库（Ecto）

### 目标

**从"用 SQL 字符串"变成"用 Changeset 管道表达校验 + 持久化"。**

### 重点学习内容

- **Ecto.Schema**：声明表结构和字段类型，是纯数据定义，不带 session/connection
- **Ecto.Changeset**：`cast` / `validate_required` / `validate_format` / `unique_constraint` 的函数式管道；错误结构化地累积
- **Ecto.Repo**：`insert/update/delete/all` 的最小持久化 API；Repo 本身是一个 OTP 应用，启动在监督树里
- **Ecto.Multi**：把多条操作打包成**一个事务**，中间任何一步失败整体回滚；非常适合"创建用户 + 发欢迎邮件记录 + 扣库存"这类跨表流程
- **Changeset 是纯函数**：可以在 controller、test 层任意构造、断言，不需要真 Repo

### 建议学习顺序

1. **Demo 09** Ecto Repo / Schema / Changeset / Multi —— 一次性把四大件串起来

### 学完应该能做

- 看到"表单校验" / "导入校验"能立刻想到用 Changeset 作为"结构化的 Result"
- 看到"跨表事务"能立刻想到 Multi，而不是手写 `Repo.transaction(fn -> ... end)`
- 理解 Ecto 的"数据结构驱动" 哲学：Schema 和 Changeset 都是可传递的值，不带连接状态

---

## 第七阶段：Web 服务与实时界面

### 目标

**从"能返回 JSON"变成"能设计一条 Plug 管道 + 理解 LiveView 的服务端状态"。**

### 重点学习内容

- **Plug**：Web 层的基石抽象 `fn conn -> conn end`；路由、日志、CORS、鉴权都是 Plug 管道上的节点
- **Plug.Router DSL**：`get` / `post` / `match` / `forward` 在背后是宏生成的 `match/2`
- **LiveView 心智模型**：`mount/3` 给初始 assigns，`handle_event/3` 把事件映射成新 assigns；服务端自动做 diff 推送到客户端，不需要你手写 JSON
- **进程对应连接**：每一条 LiveView 连接背后都是一个 GenServer；断线重连 = 重启 + 恢复 assigns
- **Plug × LiveView 的分层**：Plug 处理一次性 HTTP，LiveView 处理长连接状态机

### 建议学习顺序

1. **Demo 10** Phoenix 风格 Plug 路由 —— 起一个最小 Cowboy server + 自测路由
2. **Demo 11** LiveView 心智模型（本地版） —— 不依赖真实浏览器，用纯 Elixir 模拟 LiveView 循环

### 学完应该能做

- 看到"鉴权 / 日志 / CORS"能立刻想到"再加一个 Plug"，而不是去改业务 controller
- 看到"表单实时校验 / 股票行情推送" 能立刻想到 LiveView，而不是前端 JS 轮询
- 理解 Phoenix Channel / LiveView / Presence 背后都共享同一套"长连接 + GenServer" 心智

---

## 第八阶段：流式管道

### 目标

**从"能 `Stream.map` 处理一批数据"变成"能用 Broadway 对接真实消息中间件"。**

### 重点学习内容

- **GenStage**：带背压的生产者-消费者协议（demand-driven）；下游主动向上游"要多少条"
- **Flow**：基于 GenStage 的数据并行 DSL，支持 `partition` / `window` / `reduce`
- **Broadway**：面向真实消息中间件（Kafka / RabbitMQ / SQS / GCP PubSub）的生产级管道；内置 ack、重试、批处理、dead-letter 队列
- **背压是一等公民**：下游消费慢，上游自动放缓，而不是内存爆掉
- **三者的分层**：业务集合用 `Stream`，并行计算用 `Flow`，消息系统对接用 `Broadway`

### 建议学习顺序

1. **Demo 12** Flow / GenStage / Broadway 流式管道 —— 三者同场对比

### 学完应该能做

- 看到"消费 Kafka 做实时聚合"能立刻想到 Broadway 而不是手写 consumer group 逻辑
- 看到"批量处理文件 + 并行计算 + 按 key 聚合"能立刻想到 Flow 的 `partition` + `reduce`
- 理解"背压"和"限流"是两件事：背压让慢的一方控制快的一方；限流是全局闸门

---

## 第九阶段：可观测性

### 目标

**从"println / Logger 看日志"变成"发结构化事件，让标准生态翻译成 trace 和 metric"。**

### 重点学习内容

- **`:telemetry.execute/3`**：发出结构化事件 `event_name + measurements + metadata`
- **`:telemetry.attach/4`**：运行时挂载 handler；业务代码发事件，handler 决定"日志 / metric / trace" 的最终形态
- **OpenTelemetry 桥接**：把 Elixir 事件翻译成标准 span / metric，导到 Jaeger / Prometheus / OTel Collector
- **全生态共用同一套 hook**：Phoenix / Ecto / Broadway 默认都发 telemetry 事件；只需要 attach 一次就能监控整条栈
- **metadata 是开放的**：在事件里加什么字段完全由业务决定，handler 侧按需筛选

### 建议学习顺序

1. **Demo 13** Telemetry + OpenTelemetry —— 看"发事件 + 挂 handler + OTel 桥接"的全链路

### 学完应该能做

- 看到"想埋一个监控点"能立刻想到 `:telemetry.execute/3`，而不是在业务里拼字符串日志
- 看到"想给整套业务加分布式 trace"能立刻想到 opentelemetry_telemetry 桥接
- 理解"事件总线"和"日志"的本质区别：事件有结构、有语义、可二次处理；日志只是给人看的字符串

---

## 第十阶段：测试基础设施

### 目标

**从"写 `assert` 单测"变成"把 async 单测、doctest、mock、property test 打包成完整闭环"。**

### 重点学习内容

- **ExUnit 默认 async**：测试可以并发跑，速度比串行快得多（要确保测试之间不共享状态）
- **doctest**：把 `@doc` 里的交互式示例直接升级为可执行测试，文档即测试、文档即契约
- **Mox**：基于 `@behaviour` 的静态 mock —— 编译期就检查 mock 实现是否符合契约，杜绝"mock 和真实接口漂移"
- **StreamData**：属性测试（property-based）；声明"对任意输入都应该成立的性质"，框架自动 shrink 反例
- **测试金字塔在 Elixir 里的表达**：doctest（最便宜）→ ExUnit unit（async）→ property test → Mox 集成

### 建议学习顺序

1. **Demo 14** ExUnit + doctest + Mox + StreamData —— 四位一体演示

### 学完应该能做

- 看到"这个函数应该对任意 list 都保序"能立刻想到用 StreamData 写 property
- 看到"下游依赖一个外部服务"能立刻想到定义 behaviour + Mox，而不是 `Process.put/2` 魔法
- 看到"文档写了示例"能立刻用 doctest 自动验证，避免文档腐化
- 理解 Elixir 的 async 测试为什么快：因为进程隔离天然不共享可变状态

---

## 第十一阶段：工程化

### 目标

**从"单脚本 `elixir xx.exs`"变成"能把多模块系统打包成自包含的发布包"。**

### 重点学习内容

- **mix**：Elixir 的 Cargo/sbt；`mix.exs` 里声明依赖、编译、任务
- **umbrella**：`apps/*` 多子应用的 monorepo 结构；共享同一套依赖版本，但保持模块边界
- **releases**：`mix release` 打出自包含、带 ERTS 的生产发布包；不需要在目标机器装 Elixir
- **runtime.exs**：运行时配置（区别于编译期的 `config/config.exs`）；支持在启动时读环境变量
- **热升级（hot code upgrade）**：Erlang/OTP 的独门绝技；Elixir 里通过 `:systools` / appup 也可以，但现代实践更倾向蓝绿/滚动发布

### 建议学习顺序

1. **Demo 15** mix / umbrella / releases 骨架 —— 演示从单 app 到 umbrella 再到 release 的工程结构

### 学完应该能做

- 看到"新项目"能立刻决定：单 app 还是 umbrella，是用 `mix release` 还是容器化
- 看到"配置要按环境区分"能立刻想到 `config/runtime.exs` 而不是编译期 config
- 理解 `mix release` 的关键优势：部署产物自带 ERTS，目标机器只需 libc

---

## 后续扩展

这份 roadmap 里的 15 个 Demo 是 Elixir 的**核心心智模型地图**。真实工程里还有大量方向可以继续深入：

- **Phoenix 全家桶**：Controller / Channel / LiveView / Presence / PubSub 的真实分层
- **Ecto 高级**：`preload` 策略、动态查询、migration 治理、多 repo
- **分布式**：`Node.connect`、`:global`、`:pg`、libcluster、Horde
- **事件溯源 / CQRS**：commanded、eventstore 库
- **性能剖析**：`:observer`、`:recon`、`eprof` / `fprof`
- **Gradient / Dialyzer**：静态类型检查与 `@spec` 系统
- **Nerves**：把 Elixir 跑到嵌入式设备（树莓派、BEAM-on-RTOS）

结合根目录的 [`SCALA_FP_ROADMAP.md`](../scala/SCALA_FP_ROADMAP.md) 和根 `README.md` 的核心概念一览表，可以横向对比 Scala cats-effect、Haskell、Rust tokio 生态里的同构抽象 —— 最终你会发现，**同一套函数式工程原则，只是换了一种语法外壳**。

---

## 🔗 See also — 跨语言 FP 学习路线

本仓库还提供了其它语言同一套 FP 思想的系统路线图，强烈建议配合阅读，纵向学 Elixir 的同时横向打通"同一个抽象在别处叫什么"：

| 语言 | 路线图 | 侧重点 |
|---|---|---|
| 🟣 Scala | [`../scala/SCALA_FP_ROADMAP.md`](../scala/SCALA_FP_ROADMAP.md) | JVM 工程化 FP，`cats-effect` / `fs2` / `http4s` 全栈实战 |
| 🟢 Haskell | [`../haskell/HASKELL_FP_ROADMAP.md`](../haskell/HASKELL_FP_ROADMAP.md) | 纯 FP、Monad transformers、Free/Tagless、Lens/Optics |
| 🦀 Rust | *（Demo 在 [`../rust/`](../rust/)，roadmap 文档尚未独立成册）* | 所有权 × FP、`Iterator` trait、`tokio` 异步流 |
| ⚡ Erlang | *（Demo 在 [`../erlang/`](../erlang/)，与本路线 BEAM 原理共享）* | OTP 原生、`gen_server` / `supervisor` / `mnesia` |

横向速查表：[`../LANGUAGE_COMPARISON.md`](../LANGUAGE_COMPARISON.md) —— 把"Functor / Monad / 资源安全 / 流 / Actor" 等概念在 5 种语言里一一映射。

> **对照建议**：读完 Elixir 04~05（macro）→ 回头看 Scala `@main` / macro + Haskell Template Haskell；
> 读完 Elixir 06~08（GenServer / Task）→ 对照 Scala 26 `IOApp`、Haskell 06 STM、Rust 08 tokio、Erlang 03~04；
> 读完 Elixir 09（Ecto）→ 对照 Scala 78~80 Doobie；
> 读完 Elixir 13（Telemetry）→ 对照 Rust 17 `tracing` + Haskell `co-log`。
