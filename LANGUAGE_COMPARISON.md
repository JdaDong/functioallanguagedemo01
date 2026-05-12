# 🌐 FP 五语言跨语言对照表

> 把 **Haskell / Scala / Rust / Erlang / Elixir** 里同一类 FP 思想映射成一张速查表。
> 学完任何一门语言后，这份表能帮你在另外四门里快速"认路"。

所有表里的条目都在本仓库的 Demo 里有对应实现，括号中标注的是 Demo 编号（`H=Haskell, S=Scala, R=Rust, E=Erlang, X=Elixir`）。

Erlang 和 Elixir 共享 BEAM 运行时，很多抽象底层完全一致，但 Elixir 用 Ruby 味的语法 + `|>` 管道 + 宏系统重新包装了一遍，所以在"语言表面"上会有明显差异。

---

## 1. 核心直觉：函数、不可变、引用透明

| 概念 | Haskell | Scala | Rust | Erlang | Elixir | Clojure |
|---|---|---|---|---|---|---|
| 一等函数 | `f :: A -> B` | `def f(a: A): B` / `val f: A => B` | `fn f(a: A) -> B` / `Fn(A) -> B` | `Fun = fun(A) -> B end` | `fn a -> b end` / `&MyMod.f/1` (X-01) | `(fn [a] b)` / `#(...)` (C-01) |
| 纯函数默认 | ✅ 所有非 IO 函数都是纯的 | ⚠️ 约定，靠编程风格 | ⚠️ 没有 side-effect tracking，但所有权帮了大忙 | ✅ 单进程内纯，副作用靠消息 | ✅ 同 Erlang，副作用经进程/IO 隔离 | ⚠️ 约定；`atom`/`ref`/`agent` 显式雔離可变 |
| 不可变数据 | ✅ 默认，即"全部" | ✅ `val` / `case class` 拷贝 | ✅ 默认不可变，`mut` 是显式切换 | ✅ 变量绑定一次即定 | ✅ 一次绑定 + `Map.put`/`List.replace_at` 返回新值 | ✅ **persistent data structures 一等**，`assoc`/`conj`/`update` O(log32 N) (C-02) |
| 柯里化 | ✅ 原生 (H-03) | ✅ `def f(a)(b)` (S-03) | ⚠️ 返回 `impl Fn` 模拟 (R-05) | ⚠️ 用 fun 嵌套显式构造 | ⚠️ `fn a -> fn b -> ... end end` 或 `&add(&1, &2)` 部分应用 | ⚠️ `(partial f a)` / `#(f a %)` |
| 函数组合 | `(.)` / `(<<<)` | `compose` / `andThen` | 手写 `compose` / `.pipe()` | 手写 `fun(X)->G(F(X)) end` | `\|>` 管道 (X-01) —— 值从左向右流 | `comp` / `->` / `->>` 线程宏（多种语义） |

---

## 2. Functor / Monad / 错误处理

| 概念 | Haskell | Scala | Rust | Erlang | Elixir | Clojure |
|---|---|---|---|---|---|---|
| Functor.map | `fmap` (H-02) | `.map` (S-14) | `Option::map` / `Result::map` (R-02, R-04) | `lists:map` / 手写 | `Enum.map` / `Stream.map` | `map` 默认是 序列 / `mapv` 是 vector；同一 fn 适用于多种 collection |
| Monad.bind | `>>=` / `do` (H-02) | `.flatMap` / `for` (S-14) | `.and_then` / `?` (R-04) | 无一等抽象，靠 `case`/模式匹配 | `with` 表达式 (X-03) —— 事实上的 do-notation | `some->` / `some->>` 短路 nil；不追求一等 monad |
| 可选值 | `Maybe a` | `Option[A]` | `Option<T>` | `undefined`/`{ok, V}`/`error` | `nil` / `{:ok, v}` / `{:error, reason}` | `nil` 是唯一。`some?` 检查；`when` / `if-let` / `some->` 减少嵌套 |
| 错误类型 | `Either e a` / `ExceptT` (H-08) | `Either[E, A]` / `EitherT` (S-49) | `Result<T, E>` + `?` (R-04) | `{ok, V}` / `{error, Reason}` + try/catch | `{:ok, v}` / `{:error, reason}` + `with` (X-03) | 约定 `[:ok v]` / `[:error reason]`（C-43 DLQ）；或 `ex-info` Java 异常携带数据 |
| 应用风格校验 | `Validation` | `Validated[E, A]` (S-12) | 手写 `Result` 链或 `validator` crate | 手写 tuple 累计 | `Ecto.Changeset` 累计 errors (X-09) | `clojure.spec.alpha` / `malli` 返回结构化错误（C-22, C-24） |

---

## 3. 不可变共享 & 受控可变

| 概念 | Haskell | Scala | Rust | Erlang | Elixir | Clojure |
|---|---|---|---|---|---|---|
| 不可变分享 | 默认（可共享无需 copy） | 默认 (`val`) | `Rc<T>` / `Arc<T>` (R-07) | 所有术语都是 immutable term | 同 Erlang（共享 BEAM term） | 默认；persistent vector / hash-map 结构共享免 copy |
| 单线程内部可变 | `IORef` / `State` (H-05) | `Ref` / `AtomicCell` (S-36) | `RefCell<T>` / `Cell<T>` (R-07) | 递归参数 / 进程字典 (H-03 式) | `Agent` (X-06) / GenServer 状态字段 | `atom` + `swap!` / `reset!` (C-15) |
| 跨线程共享可变 | `TVar` (STM, H-06) / `MVar` | `Ref` + cats-effect / `AtomicReference` | `Arc<Mutex<T>>` / `Arc<RwLock<T>>` (R-07) | ETS 表 (E-06) / mnesia | `:ets` / `Registry` / 共享 `GenServer` | **`ref` + `dosync` STM 一等公民**（C-16）；`agent` 异步状态（C-17） |
| 事务性 | STM (H-06) | STM scala-stm | 无内建，手写乐观并发 | mnesia 事务 | `Ecto.Multi` (X-09) / mnesia | `dosync` 原生事务（alter/commute/ensure）；C-16 转账验证守恒 |

---

## 4. 并发 & 异步 & Actor

| 概念 | Haskell | Scala | Rust | Erlang | Elixir | Clojure |
|---|---|---|---|---|---|---|
| 轻量并发单元 | `forkIO`  / `async` (H-19) | `IO.start` / Fiber (S-26) | `tokio::spawn` / `async fn` (R-08) | `spawn` / 进程 (E-03) | `Task.async` (X-06) / `spawn_link` | `(go ...)` core.async（C-19）；`(future ...)` JVM 线程池 |
| 等待一组结果 | `mapConcurrently` / `race` | `parTraverse` / `race` (S-51) | `join!` / `try_join!` | `receive` 组合 | `Task.await_many` / `Task.Supervisor.async_stream` (X-08) | `pipeline-async` 保序（C-41）；`alts!!` 多路选择 |
| 共享状态协作 | `STM` + `TVar` / `MVar` | `Ref` + `Deferred` (S-36) | `Arc<Mutex>` / `tokio::sync` | gen_server 状态 (E-04) | `GenServer` 状态 (X-06) / `Agent` | `ref` + STM；`atom` + watcher（C-15、C-16） |
| Actor/消息传递 | Cloud Haskell | Akka | Actix / ractor / 自建 | ✅ 一等公民 (E-03/04) | ✅ 一等公民（BEAM 同源，X-06） | ⚠️ 不是一等；CSP（core.async chan）是首选（C-19/41/42/43） |
| 监督树 | 三方库 | Akka Supervisor | 无内建约定，手写 | ✅ `supervisor` 行为 (E-05) | ✅ `Supervisor` + `DynamicSupervisor` + `Registry` (X-07) | ⚠️ 无内建；Component / Integrant / Mount 等生命周期库趋近 |

---

## 5. 流 / 数据管道

| 概念 | Haskell | Scala | Rust | Erlang | Elixir | Clojure |
|---|---|---|---|---|---|---|
| 有限流 | 列表懒求值 (`[a]`) | `LazyList` (S-06) | `Iterator` (R-06) | 列表 / lists 模块 | `Enum` 即时 / `Stream` 惰性 | seq 一等；lazy-seq（C-06）；**transducers 与数据源解耦**（C-03、C-44） |
| 无穷流 | `iterate` / `repeat` | `LazyList.iterate` | 无穷 `Iterator` (R-06) | 显式递归 spawn | `Stream.iterate` / `Stream.cycle` | `(iterate f x)` / `(repeat)` / `(cycle)` lazy seq |
| 异步流 | `conduit` / `streaming` | `fs2.Stream` (S-28, S-37) | `tokio-stream` / `futures::Stream` (R-08) | 进程 + 消息驱动 | `Flow` / `GenStage` / `Broadway` (X-12) | core.async chan + `pipeline-async`（C-41）；Manifold/aleph 生态 |
| 背压 | conduit/pipes 自带 | fs2 自带 | `Stream::poll_next` + `ready!` | 通过 `gen_statem` 或自定 window | `GenStage` demand-driven 原生背压 (X-12) | 自然涌现：chan buffer 满则上游 `>!` 阻塞（C-41） |

---

## 6. 类型系统（表达力）

| 概念 | Haskell | Scala | Rust | Erlang | Elixir |
|---|---|---|---|---|---|
| 代数数据类型 | `data`、sum + product、GADT (H-07, H-42, H-43) | `sealed trait` + `case class` (S-02) | `enum` + `struct` (R-02) | atom + tuple，靠约定 | `defstruct` + atom tag 约定 (X-02) |
| 模式匹配 | ✅ 原生 | ✅ `match` | ✅ `match` | ✅ 函数头匹配 (E-01) | ✅ 函数头 + `case` / `with` (X-01) |
| Type class / Trait | ✅ `class`/`instance` (H-02) | implicit / `given` (S-07) | `trait` (R-03) | ❌ 无静态类型约束 | `defprotocol` + `defimpl`（运行时多态，X-02） |
| Phantom / Typestate | phantom type + GADT (H-15) | phantom / sealed (S-09) | `PhantomData<T>` (R-09) | ❌ | ❌（可用 atom tag + 宏近似） |
| 依值类型（近似） | DataKinds + GADTs (H-15, H-25) | Shapeless / Singletons | 有限 const generics | ❌ | ❌ |
| 泛型 | 参数多态 + type class | type param + implicit | 参数化 trait (R-03) | ❌（动态类型） | ❌（动态）/ `@spec` + Dialyzer 给出渐进类型 |

---

## 7. Effect 管理

| 概念 | Haskell | Scala | Rust | Erlang | Elixir |
|---|---|---|---|---|---|
| 把"副作用"当值 | `IO a` / `ExceptT`/`ReaderT` (H-08) | `IO[A]` cats-effect (S-26) | 没有原生抽象，手写 tagless | 没有抽象，靠进程隔离 | 同 Erlang；靠进程 + `with` + Telemetry 事件流 (X-13) |
| Free Monad | `Free f a` (H-11) | `Free[F, A]` | 少见，可用 `enum` 近似 | 不适用 | 不常用（有 `witchcraft` 这类实验库） |
| Tagless Final | `class MonadDB m` / GADT + 多解释器 (H-21, H-42) | `trait UserRepo[F[_]]` (S-22/30) | `trait Store` + generic | 不适用 | `@behaviour` + `@callback`（运行时版 tagless，X-02） |
| Algebraic Effects | polysemy / effectful / fused (H-26) | effect systems 生态 | 第三方 `keter`/自建 | 不适用 | 不适用 |

---

## 8. 资源安全

| 概念 | Haskell | Scala | Rust | Erlang | Elixir |
|---|---|---|---|---|---|
| RAII 自动释放 | `bracket` / `ResourceT` (H-19) | `Resource[F, A]` (S-18/27) | `Drop` trait ✅ 原生 | `link` + `monitor` + 进程终止清理 | 同 Erlang + `Supervisor` 重启策略 (X-07) |
| 资源组合 | `bracket`/`continue` | `.use`/`flatMap` | `?` + `Drop` 自然组合 | 进程消亡即释放 | 进程消亡即释放；或 `Task.Supervisor.async_stream` (X-08) |

---

> **Clojure 在第 6/7/8 节的取舍说明**
>
> 上面三张表（类型系统 / Effect 管理 / 资源安全）刻意保持 **5 列**，没有强加 Clojure：
>
> - **类型系统**：Clojure 是动态类型，没有 ADT/typeclass/phantom type 这套静态机器；类型检查交给 [`clojure.spec.alpha`](./clojure/22_spec_basic.clj) 和 [`metosin/malli`](./clojure/24_malli_schema/) **运行期**完成。这是一种取舍，不是缺失——动态 Lisp 的灵魂在宏（[`C-11`](./clojure/11_macros_dsl.clj)/[`C-47`](./clojure/47_macros_deep.clj)），而不是类型推导。
> - **Effect 管理**：Clojure 没有 `IO a` / cats-effect / Free monad 这套抽象。副作用通过 `atom` / `ref` / `agent` / `core.async` chan 等**显式 reified state** 隔离（[`C-15..C-21`](./clojure/STAGE_2_SUMMARY.md)）；事件总线/事件溯源等高级模式靠数据描述（[`C-40`](./clojure/40_nubank_style_event_sourcing.clj)）。
> - **资源安全**：Clojure 用 `with-open` + `try/finally`（沿用 Java try-with-resources 思路）；无 `bracket` / `Resource[F, A]` 这种 monad 化抽象。够用，但不优雅。
>
> 所以读这三张表，请在 Clojure 视角下把它们理解为「**Clojure 故意不参与的赛道**」。

---

## 9. 测试

| 概念 | Haskell | Scala | Rust | Erlang | Elixir | Clojure |
|---|---|---|---|---|---|---|
| 单元测试 | hspec / tasty | munit / scalatest (S-40) | `#[test]` | eunit | ExUnit（标准库内置，X-14） | `clojure.test` / kaocha |
| 属性测试 | QuickCheck / Hedgehog (H-12) | ScalaCheck | proptest / quickcheck | PropEr / triq / 手写 (E-08) | `StreamData` (X-14) | `clojure.test.check` (C-23, C-45)；spec/malli 自动派生 generator |
| 状态机属性测试 | hedgehog SM / qcsm (H-22) | - | - | PropEr FSM | `StreamData` + `ExUnitProperties` | test.check 手写（本仓未专项演示） |
| 集成测试 | docker/testcontainers | testcontainers | testcontainers-rs | ct (common_test) | `Mox`（行为替身，X-14）+ sandbox | testcontainers-clj / `with-redefs` mock |

---

## 10. 递归 & 抽象

| 概念 | Haskell | Scala | Rust | Erlang | Elixir | Clojure |
|---|---|---|---|---|---|---|
| 尾递归 | 编译器 + 手动 | `@tailrec` (S-05) | 手动 / `loop` 常见 | ✅ 必用（E-01） | ✅ 必用（BEAM 只保证尾调优化） | `recur` 是显式必用语法（不是优化）；`loop`/`recur` 组合（C-05） |
| Recursion schemes | recursion-schemes / 手写 (H-24) | droste / 手写 | 罕见 | 不常用 | 不常用 | 不常用；clojure.walk 提供 prewalk/postwalk 足以应付 |
| Lambda 演算 / fix | 手写 (H-17) | 手写 | 手写（闭包） | 有限（缺少原生多态 Y） | 有限（同 Erlang） | 手写 (Y combinator)；宏中可询问 `&form` |
| Comonad | class `Comonad` (H-23) | cats `Comonad` | 无内建 | 不适用 | 不适用 | 不适用 |

---

## 11. 工具箱一览

| 用途 | Haskell | Scala | Rust | Erlang | Elixir | Clojure |
|---|---|---|---|---|---|---|
| 构建 | cabal / stack | sbt / mill / scala-cli | cargo | rebar3 / mix | **mix**（官方，X-15） | **deps.edn + Clojure CLI**（官方） / lein |
| 格式化 | fourmolu / ormolu | scalafmt | rustfmt | erlfmt | `mix format` | cljfmt / zprint |
| Lint | hlint | scalafix | clippy | elvis | `credo` + Dialyzer | **clj-kondo**（社区强荐） |
| REPL | ghci | scala REPL | `cargo-script`/`evcxr` | `erl` shell | `iex` | **clj REPL（一等公民工作流）** |
| HTTP | servant / warp | http4s / akka-http | axum / actix-web / hyper | cowboy / elli | `Phoenix` / `Plug` + Cowboy (X-10) | Ring / Compojure / Reitit（C-29/30/31） |
| JSON | aeson | circe | serde | jsx / jsone | `Jason` / `Poison` | `clojure.data.json` / `metosin/jsonista`（C-39） |
| 数据库 | persistent / beam | doobie / quill (S-78~) | sqlx / diesel | epgsql / odbc | `Ecto` (X-09) | next.jdbc / HoneySQL / Datomic / DataScript（C-32） |
| 包管理 | hackage | Maven Central / sonatype | crates.io | hex（与 Elixir 共用） | **hex.pm**（BEAM 统一包仓库） | Maven Central / Clojars |

---

## 12. "一句话速记"

| 口诀 | 意思 |
|---|---|
| Haskell 是 **类型驱动**的 FP | 纯、有 Type class、Monad 是一等公民 |
| Scala 是 **工程优先**的 FP | FP + OOP 双栖，cats-effect 落地大厂 |
| Rust 是 **系统级**的 FP | 所有权 = 静态线性类型；没有 GC 的纯直觉 |
| Erlang 是 **分布式**的 FP | 不可变 + actor + OTP = 高可用之母 |
| Elixir 是 **现代化**的 BEAM FP | 继承 Erlang 容错哲学，加上宏、管道、`with`、Phoenix 生态 |
| Clojure 是 **动态 Lisp 复兴**的 FP | persistent 数据结构一等 + STM 一等 + 真 Lisp 宏 + REPL-driven；类型交给 spec/malli 运行期 |

---

## 📌 学习路径建议

1. **从 Haskell 入门（抓直觉）**：这里的抽象都"有名字"，学会了就能迁移。
2. **用 Scala 上手工程**：同样的思想在 JVM + 大厂基础设施里有完整答案。
3. **用 Rust 理解"不可变"为什么非它不可**：所有权会让你重新敬畏 FP。
4. **用 Erlang 体会"容错"哲学**："Let it crash" 是 FP 的另一种极致。
5. **用 Elixir 看"容错 + 现代语法"的结合**：同一套 OTP 抽象套上宏与 `\|>`，读起来完全不同。
6. **用 Clojure 理解"动态型 FP 怎么不乱"**：Lisp 宏 + persistent 数据结构 + REPL-driven，在 JVM 上另起一条路。

> 六种语言看完后你会发现：**FP 不是语言特性的合集，而是一种思考问题的方式。**

---

## 🔗 配套路线图

| 语言 | 深度路线图 |
|---|---|
| 🟢 Haskell | [`haskell/HASKELL_FP_ROADMAP.md`](./haskell/HASKELL_FP_ROADMAP.md) |
| 🟣 Scala | [`scala/SCALA_FP_ROADMAP.md`](./scala/SCALA_FP_ROADMAP.md) |
| 💧 Elixir | [`elixir/ELIXIR_FP_ROADMAP.md`](./elixir/ELIXIR_FP_ROADMAP.md) |
| 🦀 Rust | [`rust/`](./rust/)（Demo 25 篇，roadmap 文档尚未独立） |
| ⚡ Erlang | [`erlang/`](./erlang/)（Demo 27 篇，roadmap 文档尚未独立） |
| 🍃 Clojure | [`clojure/ROADMAP.md`](./clojure/ROADMAP.md)（Demo 50 篇 ✅，分 6 阶段，含深度补遗）|

---

## 🏭 行业场景 demo 索引（Haskell 42~48）

前面 12 张表都是按"抽象"组织的；这里反过来按"行业场景"索引，给到 Haskell 在工业界最具代表性的 7 个缩影 demo。每份原则上都是单文件、纯 `base`、`runghc` 直接可跑（46 号是唯一例外，需 `cabal run` 自动拉 `parallel` 包）。

| Demo | 行业场景 | 映射到上表哪一格 |
|---|---|---|
| [`haskell/42_OptionPricingDSL.hs`](./haskell/42_OptionPricingDSL.hs) | 金融衍生品定价（Standard Chartered / Barclays / IOHK 范式） | 第 6 节 **代数数据类型/GADT** + 第 7 节 **Tagless Final** |
| [`haskell/43_TinyLangCompiler.hs`](./haskell/43_TinyLangCompiler.hs) | 编译器 / DSL / 语言工具（GHC / Pandoc / Elm / shellcheck 的缩影） | 第 6 节 **代数数据类型** + 第 10 节 **递归 & 抽象** |
| [`haskell/44_AutoDiff.hs`](./haskell/44_AutoDiff.hs) | 机器学习 / 科学计算（PyTorch / JAX autograd 的底层原理） | 第 2 节 **Functor.map / Type class 重载** + 第 6 节 **类型系统** |
| [`haskell/45_UTXOLedger.hs`](./haskell/45_UTXOLedger.hs) | 区块链账本（Cardano / Plutus / Bitcoin 的 UTXO 模型 + Merkle 根） | 第 6 节 **代数数据类型** + 第 1 节 **不可变性** |
| [`haskell/46_ParallelStrategies.hs`](./haskell/46_ParallelStrategies.hs) | 声明式并行（金融定价 / 风险计算 / 科学模拟的多核加速；6× 实测加速比） | 第 9 节 **并发/并行**（这里强调的是并行，不是并发）|
| [`haskell/47_MinimalFRP.hs`](./haskell/47_MinimalFRP.hs) | 函数式响应式编程（Reflex / Yampa / RxJS 的古典内核） | 第 2 节 **Functor / Applicative**（Behavior 即 Reader Time）|
| [`haskell/48_CsvToJsonETL.hs`](./haskell/48_CsvToJsonETL.hs) | 数据工程 ETL（CSV→JSON 流水线 + 列类型推导 + 错误定位） | 第 6 节 **ADT** + 第 10 节 **递归下降解析** |

---

## 🧩 生态盘点：学完 demo 之后玩什么

学完抽象和 demo 之后，对应语言的"真实世界项目"盘点（SDK / 开源项目 / 应用场景）：

| 语言 | 生态盘点 |
|---|---|
| 🟢 Haskell | [`haskell/HASKELL_ECOSYSTEM.md`](./haskell/HASKELL_ECOSYSTEM.md)（Tidal Cycles / Servant / Pandoc / Plutus 等 25 个项目） |
| 🟣 Scala | [`scala/SCALA_ECOSYSTEM.md`](./scala/SCALA_ECOSYSTEM.md)（Spark / Kafka / Akka / cats-effect / Chisel 等 25 个项目） |
| 🦀 Rust | [`rust/RUST_ECOSYSTEM.md`](./rust/RUST_ECOSYSTEM.md)（ripgrep / tokio / Pingora / Solana / candle 等 25 个项目） |
| ⚡ Erlang | [`erlang/ERLANG_ECOSYSTEM.md`](./erlang/ERLANG_ECOSYSTEM.md)（RabbitMQ / EMQX / WhatsApp / CouchDB 等 25 个项目） |
| 💧 Elixir | [`elixir/ELIXIR_ECOSYSTEM.md`](./elixir/ELIXIR_ECOSYSTEM.md)（Phoenix LiveView / Nx+Bumblebee / Livebook / Nerves 等 25 个项目） |
| 🍃 Clojure | [`clojure/CLOJURE_ECOSYSTEM.md`](./clojure/CLOJURE_ECOSYSTEM.md)（Datomic / Metabase / LogSeq / Reagent / Babashka 等 25 个项目） |

---

## 🗺 其他函数式语言 & 后续扩展计划

除了本仓库已覆盖的 6 门主力外，还有一批值得了解 / 规划中的函数式语言：

| 文档 | 内容 |
|---|---|
| [`OTHER_FP_LANGUAGES.md`](./OTHER_FP_LANGUAGES.md) | OCaml / F# / Clojure / Racket / Elm / PureScript / Idris / Lean / Roc / Gleam 等 20+ 语言分梯队盘点 |
| [`ocaml/ROADMAP.md`](./ocaml/ROADMAP.md) | OCaml 40+ demo 规划（Jane Street / Facebook Flow / MirageOS 风格，等待 `opam` 环境就绪后开工） |
| [`clojure/ROADMAP.md`](./clojure/ROADMAP.md) | Clojure 50 demo（Metabase / Datomic / Nubank 风格，全部 ✅）|
