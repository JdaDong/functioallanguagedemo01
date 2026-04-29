# Scala FP 学习路线图

## 你现在的位置

你现在这套仓库里的 `Scala FP` Demo，已经正式进入 **有界上下文地图集成阶段**。当前 `01` 到 `135` 号 Demo 已经系统串起了：

- 高阶函数
- 模式匹配与 ADT
- 不可变性
- `Option` / `Either` / `Try`
- 递归与尾递归
- 惰性求值
- Type Class 风格
- `Validated`
- `Semigroup` / `Monoid`
- `Functor` / `Applicative` / `Monad`
- `Reader` / `State`
- 手写 `IO` / `Resource`
- 并发组合
- 流处理直觉
- 最小 HTTP 服务建模
- Tagless Final / Algebra 设计
- Kleisli / ReaderT 风格请求管道
- Retry / Backoff 重试策略
- 测试解释器
- 真实 `cats-effect IO` / `Fiber`
- 真实 `cats-effect Resource`
- 真实 `fs2 Stream`
- 真实 `http4s Routes` / `Middleware`
- 真实 `cats-effect` Tagless 解释器
- `circe` JSON 编解码
- `http4s` JSON API
- `http4s Client`
- `fs2 Queue` 工作流
- effect 的 timeout / cancel / finalizer
- `Ref` / `Deferred` 协作
- `fs2 Topic` 发布订阅
- Bearer 鉴权中间件
- Ember Server / Client 联调
- `munit-cats-effect` 测试化
- `Semaphore` 并发限流
- fs2 `parEvalMap` 并行流
- `http4s` 领域错误映射
- Tagless + `http4s` 模块装配
- `munit-cats-effect` 路由集成测试
- `Supervisor` 后台任务托管
- fs2 多路流 `merge`
- 官方 `AuthMiddleware` / `AuthedRoutes`
- `EitherT` 错误编排
- `AuthMiddleware` 集成测试
- `race` 竞速与自动取消
- fs2 流式错误恢复
- `http4s client` 中间件与 trace 透传
- 下游服务聚合编排
- 调用方编排测试
- `uncancelable` / `poll` 取消边界
- fs2 停流信号
- `http4s client` 重试治理
- `ContextRoutes` / 请求上下文注入
- client 重试策略测试
- `Dispatcher` 回调边界桥接
- fs2 `groupWithin` 批处理窗口
- `http4s` 流式响应 API
- Ember 流式 client
- 流式路由测试
- `IOLocal` fiber 本地上下文
- fs2 `Pull` 自定义流变换
- `http4s` Server-Sent Events
- Ember SSE client
- SSE 路由测试
- `MapRef` 按 key 分片状态
- Topic 房间广播枢纽
- `http4s` WebSocket 聊天路由
- JDK WebSocket callback 桥接
- WebSocket 路由测试
- `http4s` Multipart 文件上传
- fs2 固定分块处理大文件流
- Doobie `Transactor` 资源与事务回滚
- Tagless Repository + Doobie 解释器
- Repository 集成测试
- Doobie 流式导出数据库报表
- fs2 CSV 导入解析与分批管道
- `http4s` CSV 下载接口
- Tagless 批量导入模块
- 批量导入 + CSV 导出集成测试
- cats-effect 并发幂等门闩
- fs2 重复请求流去重
- `http4s` `Idempotency-Key` 写接口
- Doobie 持久化幂等写入
- 幂等写接口集成测试
- cats-effect Outbox 协调器
- fs2 Outbox 重试发布流
- `http4s` Webhook + Outbox 发布边界
- Doobie 事务 Outbox
- 事务 Outbox 集成测试
- cats-effect Inbox 协调器
- fs2 Inbox 重试消费流
- `http4s` Webhook Inbox 接收边界
- Doobie 事务 Inbox
- 事务 Inbox 集成测试
- cats-effect Saga 协调器
- fs2 Saga 超时补偿流
- `http4s` Saga 工作流边界
- Doobie 事务 Saga 状态
- Saga 集成测试
- cats-effect 读模型投影协调器
- fs2 读模型回放流
- `http4s` 读模型查询边界
- Doobie 事务投影 checkpoint
- 读模型回放集成测试
- cats-effect 命令总线
- fs2 命令路由流
- `http4s` CQRS 命令/查询双边界
- Doobie 事务命令写入
- CQRS 集成测试
- cats-effect 事件溯源聚合根
- fs2 事件追加流
- `http4s` Event Store 端点
- Doobie 事件存储仓库
- 事件溯源集成测试
- cats-effect 进程管理器状态机
- fs2 事件路由到进程管理器
- `http4s` 进程管理器边界
- Doobie 进程管理器仓库
- 进程管理器集成测试
- cats-effect 防腐层翻译器
- fs2 上游事件翻译流
- `http4s` ACL 适配端点
- Doobie ACL 翻译日志
- 防腐层集成测试
- cats-effect 有界上下文地图装配
- fs2 跨上下文事件总线
- `http4s` 统一网关
- Doobie 多上下文事务协调
- 有界上下文地图端到端集成测试

这意味着你已经不只是会写 `map` / `filter`，而是开始把这些思想真正推进到：**用类型建模、用组合表达流程、把副作用变成显式对象、把业务能力抽象成代数，并逐步落到真实函数式库的工程 API、协议层、并发协作、后台任务生命周期、服务边界、服务联通、恢复策略、上下文治理、边界桥接、流式接口、fiber 本地上下文、双向实时连接、上传边界、数据库事务、批量导入导出、幂等写入治理、事务消息、消费端重放保护、跨服务补偿编排、查询侧 checkpoint 治理、命令查询职责分离、事件聚合根重建与乐观锁、跨上下文工作流协调、上游模型翻译与本地领域保护、完整有界上下文系统装配与端到端验证和测试基础设施上**。

---

## 总体学习路线

| 阶段 | 目标 | 关键词 | 建议技术栈 |
|---|---|---|---|
| **入门** | 理解 Scala 里的函数式风格 | 高阶函数、ADT、不可变性、错误处理 | Scala 标准库 |
| **进阶** | 能用 FP 风格解决小型业务问题 | 表单校验、状态机、表达式树、递归数据结构 | Scala 标准库 |
| **中级** | 掌握常见 FP 抽象 | `Functor`、`Monad`、`Applicative`、`Validated`、`Reader`、`State` | Scala 标准库 / `cats-core` |
| **高级前置** | 建立真实函数式程序直觉 | `IO`、`Resource`、并发、流处理 | 手写微型抽象 + Scala 标准库 |
| **服务化桥接** | 理解服务组织与代数设计 | HTTP 路由、中间件、Tagless Final、Algebra | Scala 标准库 |
| **工程化补充** | 补齐真实项目中的常见模式 | Kleisli / ReaderT、Retry / Backoff、测试解释器 | Scala 标准库 |
| **高级实战** | 能写真实函数式服务 | `cats-effect`、`fs2`、`http4s`、Tagless Final | `cats-effect`、`fs2`、`http4s` |
| **高级收束** | 补齐服务边界、并发治理与测试闭环 | `Semaphore`、`parEvalMap`、错误映射、模块装配、路由测试 | `cats-effect`、`fs2`、`http4s`、`munit` |
| **工程化深化** | 把服务生命周期、认证抽象与错误编排补齐 | `Supervisor`、`merge`、`AuthMiddleware`、`EitherT`、鉴权测试 | `cats-effect`、`fs2`、`http4s`、`munit` |
| **服务联通与恢复** | 补齐竞速读取、流恢复、调用方上下文与下游编排 | `race`、fs2 错误恢复、client middleware、下游聚合、编排测试 | `cats-effect`、`fs2`、`http4s`、`munit` |
| **服务治理与上下文** | 补齐取消边界、停流信号、调用方重试和请求上下文 | `uncancelable`、`poll`、SignallingRef、client retry、ContextRoutes、重试测试 | `cats-effect`、`fs2`、`http4s`、`munit` |
| **边界桥接与流式服务** | 把回调接缝、窗口聚合、持续响应与流式消费补齐 | `Dispatcher`、groupWithin、streaming API、Ember streaming client、流式测试 | `cats-effect`、`fs2`、`http4s`、`munit` |
| **本地上下文与协议化事件流** | 把 fiber-local 上下文、自定义流变换和 SSE 推送补齐 | `IOLocal`、Pull、SSE、Ember SSE client、SSE 测试 | `cats-effect`、`fs2`、`http4s`、`munit` |
| **房间状态与双向实时通信** | 把按 key 状态、房间广播与 WebSocket 双向连接补齐 | `MapRef`、Topic hub、WebSocket、JDK WebSocket client、WebSocket 测试 | `cats-effect`、`fs2`、`http4s`、`munit` |
| **上传边界与数据库集成** | 把文件上传、分块处理、事务边界和真实仓储测试补齐 | Multipart、chunked processing、Doobie、Repository、集成测试 | `cats-effect`、`fs2`、`http4s`、`doobie`、`munit` |
| **批量导入导出与流式报表** | 把数据库流式导出、CSV 导入校验、下载接口和批量写库闭环补齐 | query.stream、CSV pipeline、CSV download、batch import、integration suite | `cats-effect`、`fs2`、`http4s`、`doobie`、`munit` |
| **幂等写入与重复请求治理** | 把并发去重、消息重放过滤、HTTP 幂等键与数据库持久化防重补齐 | idempotency gate、dedup stream、Idempotency-Key、persistent idempotency、integration suite | `cats-effect`、`fs2`、`http4s`、`doobie`、`munit` |
| **事务 Outbox 与最终一致性** | 把写库后发事件、后台重试投递、Webhook 边界和事务 outbox 闭环补齐 | outbox coordinator、retry stream、webhook dispatch、transactional outbox、integration suite | `cats-effect`、`fs2`、`http4s`、`doobie`、`munit` |
| **事务 Inbox 与消费端幂等** | 把重复投递防护、Webhook 接收保护、processed_event 记录和事务 inbox 闭环补齐 | inbox coordinator、retry consumer、webhook inbox、transactional inbox、integration suite | `cats-effect`、`fs2`、`http4s`、`doobie`、`munit` |
| **Saga 补偿与跨服务工作流** | 把跨步骤状态推进、支付超时补偿、工作流回调边界和事务 saga 状态闭环补齐 | saga coordinator、timeout compensation stream、workflow boundary、transactional saga state、integration suite | `cats-effect`、`fs2`、`http4s`、`doobie`、`munit` |
| **读模型投影与事件回放** | 把查询侧 checkpoint 推进、后台 replay、读模型查询边界和事务投影闭环补齐 | projection coordinator、replay stream、read-model query、transactional checkpoint、replay suite | `cats-effect`、`fs2`、`http4s`、`doobie`、`munit` |
| **CQRS 命令查询职责分离** | 把命令总线、批量命令路由、HTTP 写读双边界、事务命令写入和 CQRS 测试闭环补齐 | command bus、command router stream、CQRS boundary、transactional command write、integration suite | `cats-effect`、`fs2`、`http4s`、`doobie`、`munit` |
| **事件溯源** | 把聚合根事件 fold 重建、版本乐观锁、Event Store HTTP 边界、数据库事件存储和测试闭环补齐 | event-sourced aggregate、event append stream、event store endpoint、event store repository、replay suite | `cats-effect`、`fs2`、`http4s`、`doobie`、`munit` |
| **进程管理器** | 把跨上下文事件路由、进程状态机推进、命令 Outbox、幂等推进和测试闭环补齐 | process manager、event router stream、PM boundary、PM repository、integration suite | `cats-effect`、`fs2`、`http4s`、`doobie`、`munit` |
| **防腐层（ACL）** | 把上游模型翻译、拒绝日志、幂等接收、翻译持久化和防腐层测试闭环补齐 | ACL translator、translation stream、ACL adapter endpoint、translation log、integration suite | `cats-effect`、`fs2`、`http4s`、`doobie`、`munit` |
| **有界上下文地图集成** | 把四个有界上下文装配成完整系统、跨上下文事件总线、统一网关、多上下文事务和端到端验证 | context map assembly、cross-context event bus、context map gateway、multi-context transaction、end-to-end suite | `cats-effect`、`fs2`、`http4s`、`doobie`、`munit` |

---

## 第一阶段：进阶

### 目标

这一阶段的重点是：**从“看懂函数式写法”变成“能用函数式方式建模问题”。**

### 重点学习内容

- **ADT 建模**
  - 用 `sealed trait` + `case class` / `case object` 表达状态与结构
  - 用类型而不是字符串和布尔标志描述业务

- **错误处理升级**
  - 进一步熟悉 `Option`、`Either`、`Try` 的适用场景
  - 把“预期失败”显式放进类型中

- **递归处理复杂结构**
  - 学会递归处理树、表达式、嵌套对象
  - 理解“数据结构决定递归结构”的思路

- **不可变状态流转**
  - 用返回新对象代替原地修改
  - 把状态变更写成纯函数

### 推荐 Demo

- **`08_FormValidation.scala`**
  - 用 `Either[String, A]` 做注册表单校验
  - 包含用户名、邮箱、密码、年龄验证

- **`09_OrderStateMachine.scala`**
  - 用 ADT 建模订单状态流转
  - 展示合法状态迁移与非法状态错误

- **`10_ExprEvaluator.scala`**
  - 扩展表达式树，加入变量、除法、局部绑定
  - 用递归 + `Either` 做安全求值

- **`11_RecursiveJson.scala`**
  - 手写 JSON ADT
  - 递归实现 pretty print、节点统计、字段查找

### 完成标准

如果你能独立写出下面这些东西，就说明已经从入门进入进阶：

- 一个用 `ADT + match` 建模的小业务
- 一个返回 `Either` 的校验器
- 一个递归处理树形结构的小程序

---

## 第二阶段：中级

### 目标

这一阶段开始进入真正的函数式抽象：**不只会写 Demo，而是开始理解这些抽象为什么反复出现。**

### 重点学习内容

- **Type Class 系统化理解**
  - 不只是会写 `Show` / `Eq`
  - 要开始理解 Type Class 为什么能支持高度组合

- **`Functor / Applicative / Monad`**
  - `Functor`：在上下文中变换值
  - `Monad`：依赖上一步结果继续计算
  - `Applicative`：组合独立上下文

- **`Semigroup / Monoid`**
  - 抽象“可合并”
  - 适合统计、日志、聚合

- **`Validated`**
  - 与 `Either` 不同，适合累积多个错误
  - 特别适合表单与配置校验

- **`Reader / State`**
  - `Reader`：依赖注入 / 环境传递
  - `State`：纯函数式状态演进

### 推荐 Demo

- **`12_ValidatedRegistration.scala`**
- **`13_SemigroupAndMonoid.scala`**
- **`14_FunctorApplicativeMonad.scala`**
- **`15_ReaderConfig.scala`**
- **`16_StateCalculator.scala`**

### 建议

这个阶段建议开始接触 **`cats-core`**，但前提是你已经真的理解了标准库里的 `Option`、`Either`、递归、Type Class 风格。

---

## 第三阶段：高级前置

### 目标

把函数式编程从“理解抽象”推进到“开始理解真实程序为什么需要 effect system”。

### 重点学习内容

- **`IO` 与副作用管理**
  - 区分“描述副作用”和“执行副作用”
  - 明白为什么打印、读配置、访问网络都应该被包进 effect

- **`Resource`**
  - 正确管理文件、连接、句柄等资源
  - 把 acquire / use / release 绑定成一个整体

- **并发与恢复**
  - 理解串行与并行的组合差异
  - 学会把独立任务并行起来，并在失败时恢复

- **流处理直觉**
  - 一次只处理一小段数据，而不是一次性全部装入内存
  - 理解按需计算、无限流与管道式处理

### 推荐 Demo

- **`17_IOBasics.scala`**
  - 手写极简 `IO[A]`
  - 先建立 effect 作为值的直觉

- **`18_ResourceDemo.scala`**
  - 手写极简 `Resource[A]`
  - 观察成功 / 失败两条路径都能正确释放资源

- **`19_ConcurrencyDemo.scala`**
  - 用 `Future` 对比串行与并行
  - 观察批量并发和失败恢复

- **`20_FS2Pipeline.scala`**
  - 用 `Iterator` / `LazyList` 建立流处理直觉
  - 为真正学习 `fs2` 先打底

### 完成标准

如果你已经能比较顺畅地理解下面这些点，就说明你已经站在高级实战门口了：

- 为什么副作用最好不要散落在业务代码里
- 为什么资源释放不能靠“记得 close”
- 为什么并发更适合建模成“组合计算”而不是“共享变量”
- 为什么流处理要强调按需、分段和管道

---

## 第四阶段：服务化桥接

### 目标

把 effect、资源、并发这些直觉进一步推进到“服务如何组织”的层面，但仍然保持**标准库可运行、概念透明**。

### 重点学习内容

- **HTTP 服务建模**
  - 理解请求、响应、路由、中间件这些对象本质上都可以是普通数据或函数
  - 理解一个 Web 服务并不神秘，核心仍然是函数组合

- **Tagless Final / Algebra 设计**
  - 先抽象业务能力，再决定如何解释执行
  - 让业务逻辑不直接依赖数据库、日志、网络等具体实现

### 推荐 Demo

- **`21_Http4sMiniService.scala`**
  - 用最小 `http4s` 风格模型组织路由和中间件
  - 理解服务入口、组合方式与统一封装

- **`22_TaglessUserService.scala`**
  - 用 `UserRepo`、`IdGenerator`、`Logger` 构建 algebra
  - 观察解释器替换时，业务逻辑本身不需要改动

---

## 第五阶段：工程化补充

### 目标

在不引入真实大库的前提下，把你会在实际函数式项目里频繁遇到的几个常见模式单独练熟。

### 重点学习内容

- **Kleisli / ReaderT**
  - 处理“既依赖环境，又带 effect”的请求管道
  - 在服务层特别常见

- **Retry / Backoff**
  - 把失败恢复策略从业务代码中分离出来
  - 用数据化方式描述重试次数和退避时间

- **测试解释器**
  - 不连真实数据库、不写真实日志，也能验证业务逻辑
  - 让代数设计真正落到可测试架构上

### 推荐 Demo

- **`23_KleisliRequestPipeline.scala`**
  - 用 `R => IO[A]` 形式组织鉴权、查用户、权限检查和响应构造

- **`24_RetryBackoff.scala`**
  - 把重试策略抽象成独立逻辑，观察成功与失败两种路径

- **`25_TaglessTestInterpreter.scala`**
  - 让同一套业务逻辑跑在测试解释器里，观察纯状态如何模拟外部系统

### 完成标准

如果你已经可以比较自然地理解这些点，就说明你已经准备好从“微型抽象练习”进入“真实函数式库落地”了：

- 为什么 `Reader` 到服务层常常会自然长成 `Kleisli`
- 为什么 retry、backoff 这种逻辑值得单独抽象
- 为什么 Tagless Final 的价值要结合“测试解释器”才会更明显

---

## 第六阶段：高级实战

### 目标

把前面手写微型抽象建立起来的直觉，真正迁移到 `cats-effect`、`fs2`、`http4s` 这些实际工程里会长期使用的函数式库上。

### 重点学习内容

- **`cats-effect IO` / `Fiber`**
  - 真正可控、可取消、可并发的 effect system
  - 学会并行组合、后台任务、错误捕获和时间控制

- **`Resource` 的生产级用法**
  - 管理数据库连接池、HTTP 客户端、文件句柄等
  - 让 acquire / use / release 进入统一生命周期

- **`fs2`**
  - 处理大文件、无限流、消息流、背压场景
  - 把“按需拉取、逐步处理、批量消费”写成真正的数据管道

- **`http4s`**
  - 用纯函数式方式搭建 HTTP 服务
  - 理解真实 `Routes`、`HttpApp`、`Middleware` 的函数形状

- **Tagless Final / Algebra 设计**
  - 将业务能力抽象出来，再决定如何解释执行
  - 把服务层组织方式和 effect system 真正结合起来

### 已新增 Demo

- **`26_CatsEffectIOApp.scala`**
  - 从手写 `IO` 进入真实 `cats-effect IO`
  - 演示串行 / 并行、`Fiber`、`attempt`

- **`27_CatsEffectResource.scala`**
  - 用真实 `Resource.make` 管理连接生命周期
  - 观察成功 / 失败路径下的释放行为

- **`28_FS2StreamWorkflow.scala`**
  - 用真实 `Stream` 表达读取、解析、过滤、批处理、汇总
  - 观察 `evalMap`、`evalTap`、`chunkN` 的真实写法

- **`29_Http4sRoutes.scala`**
  - 用真实 `HttpRoutes[IO]` 和中间件组织服务
  - 不起服务器，直接把请求喂给应用验证结果

- **`30_TaglessCatsEffect.scala`**
  - 让 Tagless Final 业务逻辑跑在真实 `IO` 上
  - 用 `Ref` 实现更接近工程风格的解释器

- **`31_CirceJsonCodec.scala`**
  - 用真实 `circe` 为领域模型提供 `Encoder` / `Decoder`
  - 观察 JSON 编码成功和解码失败两种路径

- **`32_Http4sJsonApi.scala`**
  - 用 `http4s + circe` 组织真实 JSON API
  - 演示请求体解析、响应体编码、状态码映射

- **`33_Http4sClientDemo.scala`**
  - 用 `Client.fromHttpApp` 理解服务调用方的结构
  - 观察成功响应与错误响应的 JSON 解码

- **`34_FS2QueueWorker.scala`**
  - 用 Queue 表达生产者 / 消费者协作
  - 用多个 worker 并发处理异步任务

- **`35_CatsEffectTimeoutAndCancel.scala`**
  - 演示 timeout、timeoutTo、cancel、guaranteeCase
  - 观察 effect 在取消时仍然会执行 finalizer

- **`36_CatsEffectDeferredRef.scala`**
  - 用 `Ref` 保存共享状态，用 `Deferred` 表达一次性完成信号
  - 观察异步任务编排时，状态和结果信号如何配合

- **`37_FS2TopicPubSub.scala`**
  - 用 Topic 演示广播式事件流
  - 对照 Queue，更清楚地理解“分摊任务”和“广播消息”的差异

- **`38_Http4sBearerAuth.scala`**
  - 用 Bearer Token 中间件处理认证和权限控制
  - 观察未登录、非法 token、权限不足等分支如何返回不同响应

- **`39_EmberServerClientRoundTrip.scala`**
  - 真正启动本地 HTTP 服务，再用真实 client 发起请求
  - 把 server、client、JSON 协议和 Resource 生命周期串起来

- **`40_MUnitCatsEffectSuite.scala`**
  - 用 `munit-cats-effect` 把带 IO 的逻辑放进真实测试框架
  - 观察函数式服务如何进入更工程化的测试形态

### 完成标准

如果你已经可以比较自然地理解这些点，就说明你已经从“会写 FP Demo”迈进了“能看懂真实 FP 服务骨架与协议层”的阶段：

- 为什么真实 effect system 会把并发、取消、时间统一建模
- 为什么 `Resource` 是连接池、客户端、句柄管理的基础设施
- 为什么 `fs2` 不只适合流处理，也适合表达 Queue、异步任务协作
- 为什么 `http4s` / `circe` / Tagless Final 常常一起出现，形成可组合、可测试的服务结构
- 为什么 client 端、协议层、超时与取消控制也应该纳入统一的 effect 设计

---

## 第七阶段：高级收束

### 目标

把前面已经具备的 effect、流、HTTP、鉴权、联调和测试能力，继续推进到更接近真实服务收尾阶段的几个关键话题：**并发治理、流式并行、错误边界、模块装配、路由级测试**。

### 重点学习内容

- **`Semaphore` 并发限流**
  - 控制同一时刻最多有多少任务进入关键区
  - 用统一的 effect 模型保护数据库、磁盘、下游接口等有限资源

- **fs2 并行流处理**
  - 区分 `evalMap`、`parEvalMap`、`parEvalMapUnordered`
  - 理解顺序保证、吞吐量和结果输出顺序之间的权衡

- **领域错误映射**
  - 让业务逻辑先返回领域错误，而不是直接拼 HTTP 响应
  - 在协议边界统一翻译状态码和 JSON 错误体

- **模块装配**
  - 把 algebra、service、解释器和 routes 组装成清晰模块
  - 让业务能力、状态实现和协议层可以分别演进

- **路由集成测试**
  - 不只测试 service，还要测试请求 / 响应边界
  - 让 HTTP 入口的参数校验、状态码、返回体进入自动化验证

### 已新增 Demo

- **`41_CatsEffectSemaphore.scala`**
  - 用 `Semaphore` 限制上传任务的并发执行数
  - 观察关键区保护和 permit 自动归还

- **`42_FS2ParEvalMap.scala`**
  - 对比 `evalMap`、`parEvalMap`、`parEvalMapUnordered`
  - 观察有序并行与无序并行的效果差异

- **`43_Http4sErrorHandling.scala`**
  - 让业务逻辑返回领域错误，再由 HTTP 层统一映射
  - 演示参数错误、商品不存在、商品下架等响应分支

- **`44_TaglessHttp4sUserModule.scala`**
  - 把 Tagless Final、`Ref` 解释器和 http4s routes 组装进完整用户模块
  - 更清楚地观察 algebra / service / routes 的分层关系

- **`45_MUnitHttp4sRouteSuite.scala`**
  - 用 `munit-cats-effect` 直接测试 http4s 路由
  - 把测试范围从 effect 服务推进到 HTTP 边界

### 完成标准

如果你已经能比较自然地理解这些点，就说明你已经不只是会“写一个 FP Demo”，而是开始具备整理真实函数式服务边界的能力：

- 为什么并发不只是“开更多 fiber”，还要考虑资源保护和限流
- 为什么流里的并行处理一定要区分“顺序保证”和“吞吐优先”
- 为什么业务错误和 HTTP 错误响应最好明确分层
- 为什么完整服务模块需要同时考虑 algebra、解释器、协议层和测试入口

---

## 第八阶段：工程化深化

### 目标

把前面已经掌握的 effect、流、HTTP、鉴权、测试和服务边界能力，再继续推进到几个更接近真实工程收尾阶段的话题：**后台任务生命周期、多路事件流汇总、官方认证抽象、错误上下文叠加、鉴权边界测试**。

### 重点学习内容

- **`Supervisor` 后台任务托管**
  - 让后台 fiber 在某个作用域里被统一管理
  - 主流程结束时自动取消和回收后台任务

- **fs2 多路流 `merge`**
  - 把多个不同来源的事件流汇总进同一条管道
  - 理解“合并来源”和“并行处理元素”是两个不同关注点

- **`AuthMiddleware` / `AuthedRoutes`**
  - 用官方认证抽象把“认证”和“路由业务”分开
  - 让当前用户上下文通过类型安全方式进入后续处理

- **`EitherT` 错误编排**
  - 把 `IO[Either[E, A]]` 收敛成更可组合的业务流程
  - 让多步校验、查重、保存的错误短路更清晰

- **鉴权边界测试**
  - 不只测试路由逻辑，还要验证认证失败和权限分支
  - 让 401 / 403 / 200 等关键路径进入自动化断言

### 已新增 Demo

- **`46_CatsEffectSupervisor.scala`**
  - 用 `Supervisor` 统一托管后台同步任务
  - 观察作用域结束时后台任务如何被自动取消

- **`47_FS2MergeStreams.scala`**
  - 用 `merge` 把指标流和订单流合并到同一条管道
  - 观察多路来源的事件如何交错输出

- **`48_Http4sAuthMiddleware.scala`**
  - 用 http4s 官方 `AuthMiddleware` 和 `AuthedRoutes` 组织鉴权
  - 让失败处理和用户上下文注入变成明确结构

- **`49_EitherTUserFlow.scala`**
  - 用 `EitherT` 组合校验、查重、保存等带错误的 effect 步骤
  - 把 `IO[Either[...]]` 的嵌套收敛成一条清晰业务流

- **`50_MUnitAuthMiddlewareSuite.scala`**
  - 用 `munit-cats-effect` 测试认证中间件和鉴权路由
  - 把缺 token、普通用户、管理员三类路径都纳入自动化断言

### 完成标准

如果你已经可以比较自然地理解这些点，就说明你已经不只是能搭出函数式服务骨架，而是开始具备整理真实工程边界与生命周期的能力：

- 为什么后台任务必须有明确的托管作用域，而不是随手 `start`
- 为什么多个事件来源要先考虑“怎么汇总”，再考虑“怎么处理”
- 为什么认证上下文和值得失败原因最好用统一抽象进入路由
- 为什么 `EitherT` 这类工具能显著降低 effectful 业务流程的样板代码
- 为什么鉴权、权限和错误边界必须进入测试闭环

---

## 第九阶段：服务联通与恢复

### 目标

把前面已经具备的 effect、流、HTTP、鉴权、测试和下游调用基础，再继续推进到几个更贴近真实分布式服务的话题：**竞速读取、流式错误恢复、client 中间件、下游聚合、调用方编排测试**。

### 重点学习内容

- **`race` 竞速与自动取消**
  - 让多个来源并发竞争，谁先返回就采用谁
  - 自动取消输掉的一方，避免无效等待和资源浪费

- **fs2 流式错误恢复**
  - 区分 error channel 与 value channel
  - 明确什么时候应该让整条流失败，什么时候应该保留坏数据继续推进

- **`http4s client` 中间件**
  - 在调用方统一注入 traceId、租户头、认证头和日志
  - 理解 server middleware 和 client middleware 在结构上的对称性

- **下游服务聚合**
  - 连续调用多个 HTTP 接口，再把结果拼装成上层视图
  - 用 `EitherT` 组织失败短路和业务校验

- **调用方编排测试**
  - 不只测试路由和鉴权，还要验证“作为调用方时”的编排逻辑
  - 让短路、错误映射、quota 调用次数等行为进入自动化断言

### 已新增 Demo

- **`51_CatsEffectRace.scala`**
  - 用 `IO.race` 让 cache / remote、primary / replica 等来源并发竞争
  - 观察输掉的一方如何被自动取消

- **`52_FS2ErrorRecovery.scala`**
  - 对比严格失败和韧性恢复两种 fs2 流处理策略
  - 观察错误是如何进入 error channel，或被转回普通值继续消费

- **`53_Http4sClientMiddleware.scala`**
  - 用 client middleware 自动注入 `X-Request-Id` 并打印请求 / 响应日志
  - 观察上下文如何在服务间被统一透传

- **`54_Http4sClientAggregation.scala`**
  - 组合 profile 和 quota 两个下游 HTTP 接口，拼出 dashboard 视图
  - 用 `EitherT` 把下游失败和业务短路组织成清晰流程

- **`55_MUnitClientOrchestrationSuite.scala`**
  - 用 `munit-cats-effect` 测试调用方编排逻辑
  - 验证资料缺失、套餐停用、quota 服务失败等分支是否正确短路

### 完成标准

如果你已经可以比较自然地理解这些点，就说明你已经不只是能写单体函数式服务，而是开始具备整理跨服务联通与恢复边界的能力：

- 为什么多个来源并发读取时要关注赢家和输家的生命周期
- 为什么流里的坏数据有时应该终止处理，有时应该被吸收到普通值通道
- 为什么 client 侧上下文透传与统一日志通常值得独立抽象
- 为什么下游聚合逻辑需要像服务本身一样被测试和验证

---

## 第十阶段：服务治理与上下文

### 目标

把前面已经具备的 effect、流、HTTP、下游调用与编排能力，再继续推进到更接近真实生产治理的话题：**取消边界、长生命周期流停止信号、调用方重试策略、请求上下文注入，以及这些治理逻辑本身的测试闭环**。

### 重点学习内容

- **`uncancelable` 与 `poll`**
  - 让关键提交区不可取消，但把等待外部确认的阶段重新打开为可取消
  - 适合理解支付、状态提交、offset 提交这类“不能做到一半”的流程

- **fs2 停流信号**
  - 用 `SignallingRef` 把停止条件显式建模出来
  - 用 `interruptWhen` 驱动轮询流、心跳流、守护流优雅退出

- **`http4s client` 重试策略**
  - 区分可恢复失败和确定性失败
  - 把次数、退避、失败判定从业务逻辑中抽离成统一策略

- **`ContextMiddleware` / `ContextRoutes`**
  - 先提取 `requestId`、`tenantId`、`userId` 等上下文
  - 再让后续路由直接以类型安全方式消费这些上下文值

- **治理逻辑测试闭环**
  - 不只测试业务成功路径，还要测试 503 重试、404 不重试、重试耗尽等治理行为
  - 让“策略本身”也进入自动化回归

### 已新增 Demo

- **`56_CatsEffectUncancelable.scala`**
  - 用 `IO.uncancelable` 划出不可取消关键区
  - 用 `poll` 保留等待阶段的可取消能力

- **`57_FS2InterruptAndSignallingRef.scala`**
  - 用 `SignallingRef` 和 `interruptWhen` 驱动长生命周期流优雅停止
  - 观察停止信号如何显式传播到整条流

- **`58_Http4sClientRetry.scala`**
  - 在真实 http4s client 场景里实现 503 重试、404 直接失败、重试耗尽返回明确错误
  - 把调用方容错策略从业务逻辑中解耦出来

- **`59_Http4sContextRoutes.scala`**
  - 用 `ContextMiddleware` 和 `ContextRoutes` 组织请求上下文
  - 让路由直接拿到 requestId、tenant、userId 等类型安全数据

- **`60_MUnitClientRetrySuite.scala`**
  - 用 `munit-cats-effect` 测试 client 重试策略
  - 把临时失败、确定性失败和重试耗尽三条路径都纳入断言

### 完成标准

如果你已经可以比较自然地理解这些点，就说明你已经不只是能写“可运行的函数式服务”，而是开始具备整理生产治理边界和上下文模型的能力：

- 为什么取消边界必须精细区分“可等待”和“不可中断提交”
- 为什么长生命周期流最好有显式停止信号，而不是靠隐式共享状态
- 为什么 client 重试、退避、错误分类值得被单独抽象和测试
- 为什么请求上下文注入不应该依赖手动层层传值

---

## 第十一阶段：边界桥接与流式服务

### 目标

把前面已经具备的 effect、流、HTTP、上下文与调用方治理能力，再继续推进到更贴近真实系统边界的话题：**旧式回调接缝、窗口聚合、服务端持续响应、真实网络下的流式消费，以及这些流式接口本身的测试闭环**。

### 重点学习内容

- **`Dispatcher` 回调桥接**
  - 把老 SDK、事件总线、监听器里的 `A => Unit` 回调重新接回 `IO`
  - 让副作用继续受 effect runtime、队列和流式管道管理

- **fs2 `groupWithin` 窗口聚合**
  - 把零散到来的元素按“数量上限”或“时间窗口”聚成批次
  - 适合理解批量写库、批量发送、批量刷盘这类吞吐量优化模式

- **`http4s` 流式响应**
  - 路由不只会一次性返回完整 JSON，也可以持续返回 `Stream[IO, Byte]`
  - 适合理解行情流、日志流、导出流、进度流等持续产出场景

- **真实流式 client 消费**
  - 用 Ember client 在真实网络下边接收、边解码、边处理 body
  - 帮助建立“客户端不必等完整响应结束再统一消费”的直觉

- **流式接口测试闭环**
  - 不只验证普通路由，也验证 limit 截断、symbol 前缀和 404 等流式场景
  - 让持续响应接口一样进入自动化回归

### 已新增 Demo

- **`61_CatsEffectDispatcher.scala`**
  - 用 `Dispatcher` 把旧式回调桥接回 `IO` / `Queue` / `Stream`
  - 观察 callback 世界如何重新接回函数式运行时

- **`62_FS2GroupWithin.scala`**
  - 用 `groupWithin` 演示按数量或时间窗口输出批次
  - 观察零散事件如何被聚合成更适合批量提交的块

- **`63_Http4sStreamingApi.scala`**
  - 用 `http4s` 返回按行持续输出的 tick 流
  - 观察服务端如何持续生成并输出响应体

- **`64_EmberStreamingClient.scala`**
  - 真正启动本地 Ember server / client，消费流式订单事件
  - 观察真实网络下的边接收边处理行为

- **`65_MUnitStreamingRouteSuite.scala`**
  - 用 `munit-cats-effect` 测试流式路由的截断、前缀和 404 分支
  - 把流式接口本身纳入自动化验证

### 完成标准

如果你已经可以比较自然地理解这些点，就说明你已经不只是能写“普通函数式服务”，而是开始具备处理真实系统边界和持续数据流的能力：

- 为什么旧式 callback 接缝最好被重新接回 effect runtime，而不是直接散落副作用
- 为什么窗口聚合往往是高吞吐流处理里很常见的优化手段
- 为什么服务端持续响应和客户端渐进消费需要一起理解
- 为什么流式接口同样需要明确、稳定的自动化测试边界

---

## 第十二阶段：本地上下文与协议化事件流

### 目标

把前面已经具备的上下文注入、边界桥接和流式服务能力，再继续推进到更贴近真实实时系统的话题：**fiber-local 上下文隔离、自定义流变换，以及基于 SSE 的协议化事件推送、真实消费和测试闭环**。

### 重点学习内容

- **`IOLocal` fiber 本地上下文**
  - 把 traceId、tenant、审计信息放进 fiber-local 上下文，而不是共享可变状态
  - 理解 fork 时上下文副本的继承，以及父子 fiber 修改互不反向污染

- **fs2 `Pull` 自定义流变换**
  - 手动消费输入、保留残留片段、再决定何时输出完整记录
  - 适合理解日志流、SSE、socket 文本帧、批量导入等协议边界

- **SSE 协议化事件流**
  - 用 `text/event-stream` 组织 `id`、`event`、`data` 三类关键信息
  - 适合理解浏览器友好的单向实时推送模型

- **真实 SSE client 消费**
  - 在真实网络下边接收边解析 `data:` 事件
  - 帮助建立监控面板、通知流、订单进度、AI 输出流式展示的直觉

- **SSE 测试闭环**
  - 验证事件数量截断、symbol 前缀和 404 等协议行为
  - 让实时推送接口同样具备自动化回归能力

### 已新增 Demo

- **`66_CatsEffectIOLocal.scala`**
  - 用 `IOLocal` 保存 traceId，并演示作用域内覆盖与自动恢复
  - 观察父 fiber 和子 fiber 如何各自持有上下文副本

- **`67_FS2PullLineDecoder.scala`**
  - 用 `Pull` 手动拼接跨 chunk 的残缺片段，再切成完整记录
  - 观察自定义流变换如何处理协议边界

- **`68_Http4sServerSentEvents.scala`**
  - 用 `http4s` 返回标准 `text/event-stream` 事件流
  - 观察 `id`、`event`、`data` 如何组织成协议输出

- **`69_EmberSseClient.scala`**
  - 真正启动本地 Ember server / client，消费实时 SSE 事件
  - 观察真实网络下的边到达边解析行为

- **`70_MUnitServerSentEventsSuite.scala`**
  - 用 `munit-cats-effect` 测试 SSE 的数量截断、data 前缀和 404 分支
  - 把协议化事件流纳入自动化验证

### 完成标准

如果你已经可以比较自然地理解这些点，就说明你已经不只是能写“流式服务”，而是开始具备整理实时上下文和事件协议边界的能力：

- 为什么 `IOLocal` 更适合表达 fiber-local 上下文，而不是跨 fiber 共享状态
- 为什么 `Pull` 往往是处理协议边界和残缺 chunk 的关键工具
- 为什么 SSE 是很多单向实时推送场景里比 WebSocket 更轻量的选择
- 为什么协议化事件流同样值得被自动化测试覆盖

---

## 第十三阶段：本地上下文与协议化事件流

### 目标

把前面已经具备的流式服务能力，继续推进到更贴近协议层与上下文隔离的话题：**fiber-local 上下文、自定义流变换、SSE 推送，以及协议化事件流的自动化测试闭环**。

### 重点学习内容

- **`IOLocal` fiber 本地上下文**
  - 让 traceId、租户、请求标签这类信息在 fiber 作用域内安全传播
  - 区分“本地上下文”和“全局共享状态”的边界

- **fs2 `Pull` 自定义流变换**
  - 手动拼接跨 chunk 的残缺数据，再切成完整记录
  - 理解协议边界为什么经常需要自己管理缓存和切分

- **SSE 协议化事件流**
  - 用 `http4s` 返回 `text/event-stream`
  - 把 `id`、`event`、`data` 组织成标准 SSE 格式

- **真实 SSE client 与测试闭环**
  - 在真实网络下消费协议化事件流
  - 把 SSE 数量、前缀和路由分支纳入自动化回归

### 已新增 Demo

- **`66_CatsEffectIOLocal.scala`**
- **`67_FS2PullLineDecoder.scala`**
- **`68_Http4sServerSentEvents.scala`**
- **`69_EmberSseClient.scala`**
- **`70_MUnitServerSentEventsSuite.scala`**

---

## 第十四阶段：房间状态与双向实时通信

### 目标

把本地上下文和协议化事件流继续推进到更贴近实时协作系统的话题：**按 key 拆分状态、房间广播枢纽、WebSocket 双向通信，以及真实客户端和自动化测试闭环**。

### 重点学习内容

- **`MapRef` 按 key 分片状态**
  - 把房间人数、租户配额、会话计数这类状态按 key 原子更新
  - 避免手写整张共享 Map 的读改写竞争

- **Topic 房间广播枢纽**
  - 用同一个广播总线给多个订阅者分发同一条消息
  - 在消费端按 room / tenant / symbol 做过滤

- **`http4s` WebSocket 双向路由**
  - 让 server 在同一条连接里持续发送和接收消息
  - 观察 WebSocket 与 SSE 在交互模型上的根本区别

- **JDK WebSocket callback 桥接**
  - 把 Java listener 风格的客户端重新接回 `IO` / `Queue`
  - 保持真实网络客户端逻辑仍然可组合、可测试

- **WebSocket 测试闭环**
  - 验证 welcome、回声广播和双客户端互通
  - 让双向实时通信一样进入自动化回归

### 已新增 Demo

- **`71_CatsEffectMapRef.scala`**
- **`72_FS2TopicHub.scala`**
- **`73_Http4sWebSocketChat.scala`**
- **`74_JdkWebSocketBridgeClient.scala`**
- **`75_MUnitWebSocketChatSuite.scala`**

---

## 第十五阶段：上传边界与数据库集成

### 目标

把实时通信之后最常见的服务端接缝也补齐：**Multipart 上传、分块处理、Doobie 事务边界、Tagless Repository，以及真实数据库集成测试**。

### 重点学习内容

- **Multipart 文件上传**
  - 在同一请求里同时解析表单字段和文件流
  - 理解 boundary、part、文件体和文本字段的服务端解码方式

- **fs2 固定分块处理**
  - 把连续字节流重组为固定大小的处理块
  - 为 hash、重试、断点续传、对象存储上传打基础

- **Doobie `Transactor` + `Resource`**
  - 把数据库连接生命周期和 `ConnectionIO` 动作分离
  - 理解事务成功与回滚到底发生在什么边界上

- **Tagless Repository + SQL 解释器**
  - 让 service 只依赖仓储代数，不直接依赖 SQL
  - 在边界处再把仓储动作解释成真实数据库访问

- **数据库集成测试**
  - 让 service + repository 跑在真实 H2 数据库上
  - 把去重、过滤、校验和写库行为纳入自动化回归

### 已新增 Demo

- **`76_Http4sMultipartUpload.scala`**
- **`77_FS2ChunkedFileProcessor.scala`**
- **`78_DoobieTransactorResource.scala`**
- **`79_DoobieRepositoryTagless.scala`**
- **`80_MUnitRepositoryIntegrationSuite.scala`**

---

## 第十六阶段：批量导入导出与流式报表

### 目标

把数据库边界继续推进到更贴近真实业务系统的导入导出链路：**Doobie 流式导出、fs2 CSV 导入解析、http4s 下载接口、Tagless 批量导入模块，以及导入导出一体化集成测试**。

### 重点学习内容

- **Doobie `query.stream` 流式导出**
  - 把数据库结果逐行导出成报表流，而不是一次性全部读入内存
  - 理解 `to[List]` 与流式导出在资源占用上的边界差异

- **fs2 CSV 导入解析与分批管道**
  - 从字节流开始做解码、切行、校验和分批组织
  - 让坏数据在进入数据库之前就被显式拒绝

- **`http4s` CSV 下载接口**
  - 让路由直接返回 `Stream[IO, Byte]` 形式的报表下载
  - 补齐下载头、筛选参数和流式响应这条服务边界

- **Tagless 批量导入模块**
  - 让 service 只依赖仓储代数，统一处理新增、更新和拒绝记录
  - 在边界处再把批量导入动作解释成真实 Doobie SQL

- **导入导出一体化集成测试**
  - 让真实 H2 数据库、导入 service 和导出路由一起进入自动化回归
  - 验证写库结果和最终导出报表是否一致

### 已新增 Demo

- **`81_DoobieStreamingExport.scala`**
- **`82_FS2CsvImportPipeline.scala`**
- **`83_Http4sCsvExport.scala`**
- **`84_DoobieBatchImportTagless.scala`**
- **`85_MUnitBatchImportExportSuite.scala`**

---

## 第十七阶段：幂等写入与重复请求治理

### 目标

把真实写接口里最常见的“重复提交 / 客户端重试 / 消息重放”问题补齐：**并发幂等门闩、流式去重、`Idempotency-Key` 写接口、Doobie 持久化幂等写入，以及幂等写接口集成测试**。

### 重点学习内容

- **cats-effect 并发幂等门闩**
  - 让同一个 requestId 在同一进程里只由首个 fiber 真正执行
  - 后续并发重复请求直接等待并复用 leader 的结果

- **fs2 重复请求流去重**
  - 在流里显式过滤消息重放和网络重试带来的重复 requestId
  - 让下游批处理只消费真正唯一的命令

- **`http4s` `Idempotency-Key` 写接口**
  - 让客户端安全重试同一个 POST，而不会重复创建业务结果
  - 对同 key 不同 payload 的错误复用做显式冲突处理

- **Doobie 持久化幂等写入**
  - 把 requestId 和首次结果一起落到数据库里，保证重启后仍然有效
  - 把长期幂等真正下沉到数据库事务边界

- **幂等写接口集成测试**
  - 让 HTTP 路由和真实 H2 数据库一起进入自动化回归
  - 验证同 key 同体只落库一次、同 key 异体返回冲突、缺少头部返回错误

### 已新增 Demo

- **`86_CatsEffectIdempotencyGate.scala`**
- **`87_FS2DedupReservationStream.scala`**
- **`88_Http4sIdempotencyKey.scala`**
- **`89_DoobieIdempotentReservation.scala`**
- **`90_MUnitIdempotencyIntegrationSuite.scala`**

---

## 第十八阶段：事务 Outbox 与最终一致性

### 目标

把真实系统里“写库成功之后还要可靠发事件”这条链路补齐：**cats-effect Outbox 协调器、fs2 重试发布流、http4s Webhook + Outbox 发布边界、Doobie 事务 Outbox，以及事务 Outbox 集成测试**。

### 重点学习内容

- **cats-effect Outbox 协调器**
  - 先在进程内讲清楚业务写入和 outbox 事件为何要一起落下
  - 模拟发布失败时事件继续保留，等待后续重试

- **fs2 Outbox 重试发布流**
  - 用定时扫描和状态推进表达 pending 事件的后台发布循环
  - 让失败事件只增加 attempts，而不是直接丢失

- **`http4s` Webhook + Outbox 发布边界**
  - 把 outbox 事件真正送到 HTTP 回调边界
  - 用下游返回码决定事件是否继续保持 pending

- **Doobie 事务 Outbox**
  - 把订单写入和 outbox 插入放进同一个数据库事务
  - 让异步发布动作和事务写入解耦，但仍然可恢复、可重试

- **事务 Outbox 集成测试**
  - 把同事务创建、失败重试和发布成功后的状态推进纳入自动化回归
  - 验证 pending / published / attempts 这些关键状态变化是否符合预期

### 已新增 Demo

- **`91_CatsEffectOutboxCoordinator.scala`**
- **`92_FS2OutboxRetryStream.scala`**
- **`93_Http4sWebhookOutbox.scala`**
- **`94_DoobieTransactionalOutbox.scala`**
- **`95_MUnitTransactionalOutboxSuite.scala`**

---

## 第十九阶段：事务 Inbox 与消费端幂等

### 目标

把真实系统里“事件已经可靠发到下游，但下游会收到重复投递”这条链路补齐：**cats-effect Inbox 协调器、fs2 Inbox 重试消费流、http4s Webhook Inbox 接收边界、Doobie 事务 Inbox，以及事务 Inbox 集成测试**。

### 重点学习内容

- **cats-effect Inbox 协调器**
  - 先在进程内讲清楚为什么同一个 `eventId` 只能真正应用一次
  - 模拟首次处理失败时不写业务结果，也不提前记录 processed 状态

- **fs2 Inbox 重试消费流**
  - 用定时扫描和状态推进表达 pending 投递的后台消费循环
  - 区分 `deliveryId` 和 `eventId`，理解重复投递和重复业务执行不是一回事

- **`http4s` Webhook Inbox 接收边界**
  - 把消费端幂等真正推进到 HTTP webhook 接收边界
  - 对缺少事件头、重复重放和相同 `eventId` 不同 payload 做显式处理

- **Doobie 事务 Inbox**
  - 把 projection 写入和 `processed_event` 记录放进同一个数据库事务
  - 让消费者即使在中途崩溃，重试时也不会留下半条脏数据

- **事务 Inbox 集成测试**
  - 把 webhook 接收、事务回滚、重复投递保护和成功重试一起纳入自动化回归
  - 验证重复消息不重复落库、失败后可安全重试、缺少头部返回错误

### 已新增 Demo

- **`96_CatsEffectInboxCoordinator.scala`**
- **`97_FS2InboxRetryConsumer.scala`**
- **`98_Http4sWebhookInbox.scala`**
- **`99_DoobieTransactionalInbox.scala`**
- **`100_MUnitTransactionalInboxSuite.scala`**

---

## 第二十阶段：Saga 补偿与跨服务工作流

### 目标

把真实系统里“多个步骤跨服务推进，一步失败后要显式补偿前序动作”这条链路补齐：**cats-effect Saga 协调器、fs2 Saga 超时补偿流、http4s Saga 工作流边界、Doobie 事务 Saga 状态，以及 Saga 集成测试**。

### 重点学习内容

- **cats-effect Saga 协调器**
  - 先在进程内讲清楚“先预留库存、再扣款、失败后补偿释放”这条最小业务链路
  - 强调 Saga 不是神奇回滚，而是显式执行反向动作

- **fs2 Saga 超时补偿流**
  - 用周期性扫描和状态推进表达“支付超时 → 触发补偿 → 失败后下轮再试”
  - 帮助理解长生命周期工作流为什么适合交给后台流管理

- **`http4s` Saga 工作流边界**
  - 把创建工作流、支付回调和状态查询真正推进到 HTTP 服务边界
  - 对重复 callbackId 做幂等保护，避免同一个回调重复推进状态

- **Doobie 事务 Saga 状态**
  - 把库存预留、补偿释放和 Saga 状态推进一起放进数据库事务
  - 验证即使在补偿中途崩溃，也不会留下半条脏数据

- **Saga 集成测试**
  - 把 HTTP 创建、回调推进、补偿回滚和成功完成路径一起纳入自动化回归
  - 验证拒绝支付后的补偿释放、事务中途失败后的安全重试、成功支付后的完成状态

### 已新增 Demo

- **`101_CatsEffectSagaCoordinator.scala`**
- **`102_FS2SagaTimeoutCompensationStream.scala`**
- **`103_Http4sSagaWorkflow.scala`**
- **`104_DoobieTransactionalSagaState.scala`**
- **`105_MUnitSagaIntegrationSuite.scala`**

---

## 第二十一阶段：读模型投影与事件回放

### 目标

把真实系统里“事件已经可靠落下，查询侧还要持续追赶、可观测、可重建”这条链路补齐：**cats-effect 读模型投影协调器、fs2 读模型回放流、http4s 读模型查询边界、Doobie 事务投影 checkpoint，以及读模型回放集成测试**。

### 重点学习内容

- **cats-effect 读模型投影协调器**
  - 先在进程内讲清楚事件应用成功后才能推进 checkpoint 这条最小语义
  - 理解为什么某个事件失败时，后续事件必须停住等待续跑

- **fs2 读模型回放流**
  - 用后台流表达持续 catch-up、失败后下轮重试和管理员触发 replay
  - 帮助理解查询侧重建为什么天然适合交给长生命周期流托管

- **`http4s` 读模型查询边界**
  - 把事件写入、读模型查询、lag / checkpoint 状态和 replay 管理真正推进到 HTTP 边界
  - 让查询侧治理信息变得可观测、可操作，而不是只藏在进程内部

- **Doobie 事务投影 checkpoint**
  - 把读模型更新和 checkpoint 推进一起放进数据库事务
  - 验证即使在推进 checkpoint 前崩溃，也不会留下“模型更新了但 offset 没记住”的脏状态

- **读模型回放集成测试**
  - 把 HTTP 事件写入、查询侧 catch-up、checkpoint 回滚和 replay 重建一起纳入自动化回归
  - 验证 lag 清零、失败后安全续跑，以及 replay 后不会重复累计业务结果

### 已新增 Demo

- **`106_CatsEffectProjectionCoordinator.scala`**
- **`107_FS2ProjectionReplayStream.scala`**
- **`108_Http4sReadModelQuery.scala`**
- **`109_DoobieTransactionalProjectionCheckpoint.scala`**
- **`110_MUnitProjectionReplaySuite.scala`**

---

## 推荐学习顺序

1. **先做进阶**
   - `08_FormValidation.scala`
   - `09_OrderStateMachine.scala`
   - `10_ExprEvaluator.scala`
   - `11_RecursiveJson.scala`

2. **再做中级**
   - `12_ValidatedRegistration.scala`
   - `13_SemigroupAndMonoid.scala`
   - `14_FunctorApplicativeMonad.scala`
   - `15_ReaderConfig.scala`
   - `16_StateCalculator.scala`

3. **再做高级前置**
   - `17_IOBasics.scala`
   - `18_ResourceDemo.scala`
   - `19_ConcurrencyDemo.scala`
   - `20_FS2Pipeline.scala`

4. **然后做服务化桥接**
   - `21_Http4sMiniService.scala`
   - `22_TaglessUserService.scala`

5. **再补工程化模式**
   - `23_KleisliRequestPipeline.scala`
   - `24_RetryBackoff.scala`
   - `25_TaglessTestInterpreter.scala`

6. **先进入真实库高级实战骨架**
   - `26_CatsEffectIOApp.scala`
   - `27_CatsEffectResource.scala`
   - `28_FS2StreamWorkflow.scala`
   - `29_Http4sRoutes.scala`
   - `30_TaglessCatsEffect.scala`

7. **再补协议层与并发控制细节**
   - `31_CirceJsonCodec.scala`
   - `32_Http4sJsonApi.scala`
   - `33_Http4sClientDemo.scala`
   - `34_FS2QueueWorker.scala`
   - `35_CatsEffectTimeoutAndCancel.scala`

8. **最后深化并发协作、认证与测试化**
   - `36_CatsEffectDeferredRef.scala`
   - `37_FS2TopicPubSub.scala`
   - `38_Http4sBearerAuth.scala`
   - `39_EmberServerClientRoundTrip.scala`
   - `40_MUnitCatsEffectSuite.scala`

9. **再做高级收束与服务边界补齐**
   - `41_CatsEffectSemaphore.scala`
   - `42_FS2ParEvalMap.scala`
   - `43_Http4sErrorHandling.scala`
   - `44_TaglessHttp4sUserModule.scala`
   - `45_MUnitHttp4sRouteSuite.scala`

10. **最后做工程化深化与鉴权闭环**
   - `46_CatsEffectSupervisor.scala`
   - `47_FS2MergeStreams.scala`
   - `48_Http4sAuthMiddleware.scala`
   - `49_EitherTUserFlow.scala`
   - `50_MUnitAuthMiddlewareSuite.scala`

11. **继续做服务联通与恢复**
   - `51_CatsEffectRace.scala`
   - `52_FS2ErrorRecovery.scala`
   - `53_Http4sClientMiddleware.scala`
   - `54_Http4sClientAggregation.scala`
   - `55_MUnitClientOrchestrationSuite.scala`

12. **最后补服务治理与上下文**
   - `56_CatsEffectUncancelable.scala`
   - `57_FS2InterruptAndSignallingRef.scala`
   - `58_Http4sClientRetry.scala`
   - `59_Http4sContextRoutes.scala`
   - `60_MUnitClientRetrySuite.scala`

13. **再补边界桥接与流式服务**
   - `61_CatsEffectDispatcher.scala`
   - `62_FS2GroupWithin.scala`
   - `63_Http4sStreamingApi.scala`
   - `64_EmberStreamingClient.scala`
   - `65_MUnitStreamingRouteSuite.scala`

14. **最后补本地上下文与协议化事件流**
   - `66_CatsEffectIOLocal.scala`
   - `67_FS2PullLineDecoder.scala`
   - `68_Http4sServerSentEvents.scala`
   - `69_EmberSseClient.scala`
   - `70_MUnitServerSentEventsSuite.scala`

15. **再补房间状态与双向实时通信**
   - `71_CatsEffectMapRef.scala`
   - `72_FS2TopicHub.scala`
   - `73_Http4sWebSocketChat.scala`
   - `74_JdkWebSocketBridgeClient.scala`
   - `75_MUnitWebSocketChatSuite.scala`

16. **再补上传边界与数据库集成**
   - `76_Http4sMultipartUpload.scala`
   - `77_FS2ChunkedFileProcessor.scala`
   - `78_DoobieTransactorResource.scala`
   - `79_DoobieRepositoryTagless.scala`
   - `80_MUnitRepositoryIntegrationSuite.scala`

17. **再补批量导入导出与流式报表**
   - `81_DoobieStreamingExport.scala`
   - `82_FS2CsvImportPipeline.scala`
   - `83_Http4sCsvExport.scala`
   - `84_DoobieBatchImportTagless.scala`
   - `85_MUnitBatchImportExportSuite.scala`

18. **再补幂等写入与重复请求治理**
   - `86_CatsEffectIdempotencyGate.scala`
   - `87_FS2DedupReservationStream.scala`
   - `88_Http4sIdempotencyKey.scala`
   - `89_DoobieIdempotentReservation.scala`
   - `90_MUnitIdempotencyIntegrationSuite.scala`

19. **再补事务 Outbox 与最终一致性**
   - `91_CatsEffectOutboxCoordinator.scala`
   - `92_FS2OutboxRetryStream.scala`
   - `93_Http4sWebhookOutbox.scala`
   - `94_DoobieTransactionalOutbox.scala`
   - `95_MUnitTransactionalOutboxSuite.scala`

20. **再补事务 Inbox 与消费端幂等**
   - `96_CatsEffectInboxCoordinator.scala`
   - `97_FS2InboxRetryConsumer.scala`
   - `98_Http4sWebhookInbox.scala`
   - `99_DoobieTransactionalInbox.scala`
   - `100_MUnitTransactionalInboxSuite.scala`

21. **再补 Saga 补偿与跨服务工作流**
   - `101_CatsEffectSagaCoordinator.scala`
   - `102_FS2SagaTimeoutCompensationStream.scala`
   - `103_Http4sSagaWorkflow.scala`
   - `104_DoobieTransactionalSagaState.scala`
   - `105_MUnitSagaIntegrationSuite.scala`

22. **再补读模型投影与事件回放**
   - `106_CatsEffectProjectionCoordinator.scala`
   - `107_FS2ProjectionReplayStream.scala`
   - `108_Http4sReadModelQuery.scala`
   - `109_DoobieTransactionalProjectionCheckpoint.scala`
   - `110_MUnitProjectionReplaySuite.scala`

23. **再补 CQRS 命令查询职责分离**
   - `111_CatsEffectCommandBus.scala`
   - `112_FS2CommandRouterStream.scala`
   - `113_Http4sCQRSBoundary.scala`
   - `114_DoobieTransactionalCommandWrite.scala`
   - `115_MUnitCQRSIntegrationSuite.scala`

24. **再补事件溯源**
   - `116_CatsEffectEventSourcedAggregate.scala`
   - `117_FS2EventAppendStream.scala`
   - `118_Http4sEventStoreEndpoint.scala`
   - `119_DoobieEventStoreRepository.scala`
   - `120_MUnitEventSourcingSuite.scala`

25. **再补进程管理器**
   - `121_CatsEffectProcessManager.scala`
   - `122_FS2ProcessManagerEventRouter.scala`
   - `123_Http4sProcessManagerBoundary.scala`
   - `124_DoobieProcessManagerRepository.scala`
   - `125_MUnitProcessManagerSuite.scala`

26. **再补防腐层（ACL）**
   - `126_CatsEffectACLTranslator.scala`
   - `127_FS2ACLTranslationStream.scala`
   - `128_Http4sACLAdapterEndpoint.scala`
   - `129_DoobieACLTranslationLog.scala`
   - `130_MUnitACLIntegrationSuite.scala`

27. **再补有界上下文地图集成**
   - `131_CatsEffectContextMapAssembly.scala`
   - `132_FS2CrossContextEventBus.scala`
   - `133_Http4sContextMapGateway.scala`
   - `134_DoobieMultiContextTransaction.scala`
   - `135_MUnitContextMapEndToEndSuite.scala`

---

## 第二十二阶段：CQRS 命令查询职责分离

### 目标

把真实系统里"写操作和读操作应该走完全不同的路径"这一架构约定补齐：**cats-effect 命令总线、fs2 命令路由流、http4s CQRS 双边界、Doobie 事务命令写入，以及 CQRS 集成测试**。

### 重点学习内容

- **cats-effect 命令总线**
  - 先讲清楚"命令 → 总线路由 → Handler 执行 → DomainEvent"这条最小写侧链路
  - 理解 Handler 只返回事件，不返回最新读模型，是 CQRS 的核心职责约定

- **fs2 命令路由流**
  - 用流表达批量命令处理：失败进 DLQ，成功发布事件
  - 帮助理解命令批处理为什么天然适合流

- **`http4s` CQRS 双边界**
  - 把 `/commands/*` 和 `/queries/*` 显式分成两条路由，写和读不对称
  - 命令返回 202 Accepted，不返回最新状态；查询永远不产生副作用

- **Doobie 事务命令写入**
  - 把写模型、读模型投影和命令日志三步放进同一事务
  - 任何一步失败整体回滚，不留脏数据

- **CQRS 集成测试**
  - 把命令接受/拒绝、写后读一致性、事务回滚和命令日志审计纳入自动化回归
  - 验证查询接口不产生副作用（幂等查询）

### 已新增 Demo

- **`111_CatsEffectCommandBus.scala`**
- **`112_FS2CommandRouterStream.scala`**
- **`113_Http4sCQRSBoundary.scala`**
- **`114_DoobieTransactionalCommandWrite.scala`**
- **`115_MUnitCQRSIntegrationSuite.scala`**

---

## 第二十三阶段：事件溯源

### 目标

把真实系统里"不存储当前状态，只存储发生了什么，当前状态从事件 fold 重建"这一核心数据层思想补齐：**cats-effect 事件溯源聚合根、fs2 事件追加流、http4s Event Store 端点、Doobie 事件存储仓库，以及事件溯源集成测试**。

### 重点学习内容

- **cats-effect 事件溯源聚合根**
  - 理解 `状态 = fold(空状态, 事件列表)`，不直接存状态字段
  - 命令处理：当前状态 + 命令 → 新事件（或错误），不改任何状态
  - 重放同一事件序列永远得到相同状态（确定性）

- **fs2 事件追加流**
  - 用乐观锁检测版本冲突，防止并发写入丢失更新
  - 成功追加后通过 fs2 Topic 扇出给多个下游（投影更新器、Outbox）

- **`http4s` Event Store 端点**
  - `POST /aggregates/{id}/commands/{type}` 追加事件，返回新版本号
  - `GET /aggregates/{id}/events` 返回完整事件序列
  - `GET /aggregates/{id}/state` 返回从事件 fold 出来的当前状态（派生视图）

- **Doobie 事件存储仓库**
  - `UNIQUE(aggregate_id, version)` 由数据库保证乐观锁
  - 事件 JSON 序列化存储，`rehydrate = loadEvents + foldLeft`

- **事件溯源集成测试**
  - 验证 fold 确定性、HTTP 命令追加、写后读一致性、无效状态转换
  - 时间旅行：取事件前 N 条重建历史时刻状态

### 已新增 Demo

- **`116_CatsEffectEventSourcedAggregate.scala`**
- **`117_FS2EventAppendStream.scala`**
- **`118_Http4sEventStoreEndpoint.scala`**
- **`119_DoobieEventStoreRepository.scala`**
- **`120_MUnitEventSourcingSuite.scala`**

---

## 第二十四阶段：进程管理器

### 目标

把真实系统里"跨越多个有界上下文的长生命周期工作流协调"这一架构层补齐：**cats-effect 进程管理器状态机、fs2 事件路由流、http4s 进程管理器边界、Doobie 进程管理器仓库，以及进程管理器集成测试**。

### 重点学习内容

- **cats-effect 进程管理器状态机**
  - 进程管理器自身也是事件溯源：状态从收到的事件 fold 重建
  - `nextCommands(state, event)` 是纯函数：当前状态 + 新事件 → 要发出的命令列表
  - 与 Saga 的区别：进程管理器是持久化状态机，可跨越多个有界上下文

- **fs2 事件路由流**
  - 来自多个有界上下文的混合事件流按 orderId 自动路由到对应进程实例
  - 不存在的进程 ID 自动初始化，命令路由结果统一收集

- **`http4s` 进程管理器边界**
  - `POST /processes/{id}/events` 提交事件（幂等）
  - `GET /processes/{id}/state` 查询进程状态
  - `GET /processes/commands/pending` 查询待执行命令（供调度器拉取）

- **Doobie 进程管理器仓库**
  - 三步原子事务：幂等检查 + 状态更新 + 命令写入
  - `UNIQUE(order_id, event_id)` 防重，`markPublished` 解耦执行与状态推进

- **进程管理器集成测试**
  - 验证完整履约路径、幂等性、补偿路径、多进程隔离和命令队列累积

### 已新增 Demo

- **`121_CatsEffectProcessManager.scala`**
- **`122_FS2ProcessManagerEventRouter.scala`**
- **`123_Http4sProcessManagerBoundary.scala`**
- **`124_DoobieProcessManagerRepository.scala`**
- **`125_MUnitProcessManagerSuite.scala`**

---

## 第二十五阶段：防腐层（ACL）

### 目标

把真实系统里"有界上下文之间的翻译、验证和保护"这一集成层补齐：**cats-effect 防腐层翻译器、fs2 上游事件翻译流、http4s ACL 适配端点、Doobie ACL 翻译日志，以及防腐层集成测试**。

### 重点学习内容

- **cats-effect 防腐层翻译器**
  - 每个上游系统有独立的 ACL 翻译器（纯函数）
  - 翻译失败返回 `TranslationRejected`，不抛异常
  - 内部领域完全感知不到上游模型的存在

- **fs2 上游事件翻译流**
  - 混合上游消息按来源路由到对应翻译器
  - 翻译失败写入 Dead Letter，不阻塞成功消息

- **`http4s` ACL 适配端点**
  - 上游回调始终返回 200，防止重试风暴
  - 相同 messageId 幂等处理，内部使用本地领域概念

- **Doobie ACL 翻译日志**
  - 原始消息、翻译后事件和拒绝记录在同一事务内原子写入
  - 翻译日志支持后续人工介入和规则修正后重处理

- **防腐层集成测试**
  - 验证翻译正确性、200 语义、幂等性、多源事件、纯函数性质

### 已新增 Demo

- **`126_CatsEffectACLTranslator.scala`**
- **`127_FS2ACLTranslationStream.scala`**
- **`128_Http4sACLAdapterEndpoint.scala`**
- **`129_DoobieACLTranslationLog.scala`**
- **`130_MUnitACLIntegrationSuite.scala`**

---

## 第二十六阶段：有界上下文地图集成

### 目标

把前面所有阶段的成果组合成一个完整运行的多上下文系统，实现真正的端到端验证：**cats-effect 上下文地图装配、fs2 跨上下文事件总线、http4s 统一网关、Doobie 多上下文事务协调，以及端到端集成测试**。这也是整个 Scala FP Demo 系列的收口阶段。

### 重点学习内容

- **cats-effect 上下文地图装配**
  - 四个有界上下文（Order/Payment/Inventory/Logistics）通过进程管理器协调
  - 三条完整路径：正常履约、支付失败、库存不足

- **fs2 跨上下文事件总线**
  - fs2 Topic 实现广播，每个上下文处理器独立订阅感兴趣的事件
  - 链式触发：OrderPlaced → PaymentCompleted → InventoryReserved

- **`http4s` 统一网关**
  - 对外聚合多个上下文的接口：创建订单/支付回调/履约视图/健康检查
  - `GET /orders/{id}/fulfillment` 跨上下文聚合视图

- **Doobie 多上下文事务协调**
  - 每次状态更新同时写入集成事件（Outbox 模式）
  - `LEFT JOIN` 跨上下文拼接报表视图

- **端到端集成测试**
  - 7 个测试验证完整系统行为：正常履约/履约视图/404/重复支付/健康检查/多订单隔离
  - 这是整个 135 个 Scala Demo 的最终收口测试

### 已新增 Demo

- **`131_CatsEffectContextMapAssembly.scala`**
- **`132_FS2CrossContextEventBus.scala`**
- **`133_Http4sContextMapGateway.scala`**
- **`134_DoobieMultiContextTransaction.scala`**
- **`135_MUnitContextMapEndToEndSuite.scala`**

---

## 最后的建议

- **进阶前期先别急着上库**：先把标准库和建模思想练扎实。
- **中级开始接触 `cats-core`**：这样你学到的是抽象本身，不只是 API。
- **高级前置先用手写微型抽象建立直觉**：这样你上 `cats-effect`、`fs2` 时不会只是死记 API。
- **进入高级实战后，重点不再是背 API**：而是看清这些真实库如何把 effect、资源、流、协议层、调用方和并发控制统一起来。
