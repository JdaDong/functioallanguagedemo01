# Scala FP 学习成果：练习场景与开源项目推荐

> 基于你已完成的 135 个 Demo（cats-effect / fs2 / http4s / doobie / munit 体系），
> 以下练习场景和开源项目完全契合你的技术栈，可以直接上手。

---

## 一、练习场景 Demo

### 初级练习（1–2 周）

| 场景 | 核心技术 |
|------|------|
| **CLI 任务管理器**：增删改查 Todo，持久化到文件 | `IO`、`Ref`、文件 IO |
| **汇率换算服务**：调外部 API、缓存结果、定时刷新 | `http4s client`、`Ref`、`fs2 Stream` |
| **日志聚合器**：多个日志文件流式合并、按级别过滤输出 | `fs2 Stream`、`merge`、`Pull` |

### 中级练习（2–4 周）

| 场景 | 核心技术 |
|------|------|
| **简易消息队列**：HTTP 发布、HTTP 拉取、确认消费、超时重投 | `fs2 Queue`、`Ref`、`http4s`、`Doobie` |
| **文件批处理平台**：上传 CSV → 校验 → 分批写库 → 下载结果报表 | `Multipart`、`fs2`、`Doobie`、`http4s` |
| **实时聊天室**：多房间 WebSocket、历史消息、在线人数统计 | `WebSocket`、`Topic`、`MapRef`、`Doobie` |
| **限流 API 网关**：按 IP 限流、熔断、降级、日志追踪 | `Semaphore`、`Ref`、`IOLocal`、`http4s middleware` |

### 高级练习（1–2 个月）

| 场景 | 核心技术 |
|------|------|
| **电商订单系统**（完整）：下单→支付→库存→物流，事件驱动 | Demo `101–135` 全部技术 |
| **简易 CDC 系统**：监听数据库变更、发布事件、下游消费 | `Doobie stream`、`fs2`、`Outbox`、`Inbox` |
| **分布式任务调度器**：任务定义、触发、重试、幂等、结果存储 | `Saga`、`ProcessManager`、`EventSourcing` |

---

## 二、推荐 GitHub 开源项目

### cats-effect / fs2 官方生态

| 项目 | GitHub 地址 | 关联 Demo |
|------|------|------|
| **cats-effect** 官方示例 | `typelevel/cats-effect` | `17–36`、`41–66` |
| **fs2** 官方文档代码 | `typelevel/fs2` | `20`、`28`、`34`、`37`、`62`、`67` |
| **http4s** 官方示例 | `http4s/http4s` | `29–39`、`48–59`、`63–68`、`73` |
| **doobie** 官方文档 | `tpolecat/doobie` | `78–84`、`89`、`94`、`99` |

### 真实生产级项目（架构参考）

| 项目 | GitHub 地址 | 特点 | 关联 Demo |
|------|------|------|------|
| **scala-pet-store** | `pauljamescleary/scala-pet-store` | 完整 http4s + doobie + circe 项目 | `29–84` 的真实组合 |
| **pfps-shopping-cart** | `gvolpe/pfps-shopping-cart` | 《Practical FP in Scala》配套代码，Redis + PostgreSQL | `86–110` 的实际参考 |
| **trading** | `gvolpe/trading` | Gabriel Volpe 的事件驱动交易系统，完整 DDD 架构 | `111–135` 的真实形态 |
| **skunk** | `tpolecat/skunk` | 纯 FP PostgreSQL 客户端，比 doobie 更激进 | `78–84` 的进阶版 |

### DDD / 事件驱动参考（对应 Demo 101–135）

| 项目 | 特点 | 对应 Demo |
|------|------|------|
| **zio-saga** | ZIO 版 Saga 实现，可对比架构思路 | `101–105` |
| **ecommerce-microservices**（Scala 版） | 微服务电商，Event Sourcing + CQRS | `111–135` |

---

## 三、推荐书籍

| 书名 | 对应 Demo 层级 | 备注 |
|---|---|---|
| 《Functional Programming in Scala》（红书） | `01–20` | Scala FP 圣经，偏理论 |
| 《Scala with Cats》（Underscore，免费） | `13–25` | 讲 Functor / Monad / Validated 等抽象 |
| 《Practical FP in Scala》（Gabriel Volpe） | `26–85` | 与你体系最匹配，强烈推荐 |
| 《Domain Modeling Made Functional》（F# 但思想通用） | `101–135` | DDD + 函数式建模的最佳入门书 |

---

## 四、推荐学习路径

```
Step 1：做「简易消息队列」（中级练习）
   把 Queue + Outbox + Inbox + Doobie 真正在一个项目里跑通
         ↓
Step 2：读 pfps-shopping-cart 源码
   对照 Demo 86–110，理解真实项目如何组织模块和状态
         ↓
Step 3：读 trading 项目
   对照 Demo 111–135，这就是你这套体系的真实生产形态
```

---

## 五、最值得现在就去看的项目

> **`pfps-shopping-cart`（gvolpe/pfps-shopping-cart）**
>
> - 技术栈与你的体系几乎完全一致：`cats-effect` + `fs2` + `http4s` + `doobie` + `Redis`
> - 作者 Gabriel Volpe 是 `cats-effect` 核心贡献者，代码风格即是最佳实践
> - 书《Practical FP in Scala》第二版与这个项目配套，可以书代码对照阅读
> - 你看完这个项目，Demo `26–110` 里的每个概念都能在真实项目里找到对应位置
