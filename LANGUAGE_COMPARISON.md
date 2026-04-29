# 🌐 FP 四语言跨语言对照表

> 把 **Haskell / Scala / Rust / Erlang** 里同一类 FP 思想映射成一张速查表。
> 学完任何一门语言后，这份表能帮你在另外三门里快速"认路"。

所有表里的条目都在本仓库的 Demo 里有对应实现，括号中标注的是 Demo 编号（`H=Haskell, S=Scala, R=Rust, E=Erlang`）。

---

## 1. 核心直觉：函数、不可变、引用透明

| 概念 | Haskell | Scala | Rust | Erlang |
|---|---|---|---|---|
| 一等函数 | `f :: A -> B` | `def f(a: A): B` / `val f: A => B` | `fn f(a: A) -> B` / `Fn(A) -> B` | `Fun = fun(A) -> B end` |
| 纯函数默认 | ✅ 所有非 IO 函数都是纯的 | ⚠️ 约定，靠编程风格 | ⚠️ 没有 side-effect tracking，但所有权帮了大忙 | ✅ 单进程内纯，副作用靠消息 |
| 不可变数据 | ✅ 默认，即"全部" | ✅ `val` / `case class` 拷贝 | ✅ 默认不可变，`mut` 是显式切换 | ✅ 变量绑定一次即定 |
| 柯里化 | ✅ 原生 (H-03) | ✅ `def f(a)(b)` (S-03) | ⚠️ 返回 `impl Fn` 模拟 (R-05) | ⚠️ 用 fun 嵌套显式构造 |
| 函数组合 | `(.)` / `(<<<)` | `compose` / `andThen` | 手写 `compose` / `.pipe()` | 手写 `fun(X)->G(F(X)) end` |

---

## 2. Functor / Monad / 错误处理

| 概念 | Haskell | Scala | Rust | Erlang |
|---|---|---|---|---|
| Functor.map | `fmap` (H-02) | `.map` (S-14) | `Option::map` / `Result::map` (R-02, R-04) | `lists:map` / 手写 |
| Monad.bind | `>>=` / `do` (H-02) | `.flatMap` / `for` (S-14) | `.and_then` / `?` (R-04) | 无一等抽象，靠 `case`/模式匹配 |
| 可选值 | `Maybe a` | `Option[A]` | `Option<T>` | `undefined`/`{ok, V}`/`error` |
| 错误类型 | `Either e a` / `ExceptT` (H-08) | `Either[E, A]` / `EitherT` (S-49) | `Result<T, E>` + `?` (R-04) | `{ok, V}` / `{error, Reason}` + try/catch |
| 应用风格校验 | `Validation` | `Validated[E, A]` (S-12) | 手写 `Result` 链或 `validator` crate | 手写 tuple 累计 |

---

## 3. 不可变共享 & 受控可变

| 概念 | Haskell | Scala | Rust | Erlang |
|---|---|---|---|---|
| 不可变分享 | 默认（可共享无需 copy） | 默认 (`val`) | `Rc<T>` / `Arc<T>` (R-07) | 所有术语都是 immutable term |
| 单线程内部可变 | `IORef` / `State` (H-05) | `Ref` / `AtomicCell` (S-36) | `RefCell<T>` / `Cell<T>` (R-07) | 递归参数 / 进程字典 (H-03 式) |
| 跨线程共享可变 | `TVar` (STM, H-06) / `MVar` | `Ref` + cats-effect / `AtomicReference` | `Arc<Mutex<T>>` / `Arc<RwLock<T>>` (R-07) | ETS 表 (E-06) / mnesia |
| 事务性 | STM (H-06) | STM scala-stm | 无内建，手写乐观并发 | mnesia 事务 |

---

## 4. 并发 & 异步 & Actor

| 概念 | Haskell | Scala | Rust | Erlang |
|---|---|---|---|---|
| 轻量并发单元 | `forkIO`  / `async` (H-19) | `IO.start` / Fiber (S-26) | `tokio::spawn` / `async fn` (R-08) | `spawn` / 进程 (E-03) |
| 等待一组结果 | `mapConcurrently` / `race` | `parTraverse` / `race` (S-51) | `join!` / `try_join!` | `receive` 组合 |
| 共享状态协作 | `STM` + `TVar` / `MVar` | `Ref` + `Deferred` (S-36) | `Arc<Mutex>` / `tokio::sync` | gen_server 状态 (E-04) |
| Actor/消息传递 | Cloud Haskell | Akka | Actix / ractor / 自建 | ✅ 一等公民 (E-03/04) |
| 监督树 | 三方库 | Akka Supervisor | 无内建约定，手写 | ✅ `supervisor` 行为 (E-05) |

---

## 5. 流 / 数据管道

| 概念 | Haskell | Scala | Rust | Erlang |
|---|---|---|---|---|
| 有限流 | 列表懒求值 (`[a]`) | `LazyList` (S-06) | `Iterator` (R-06) | 列表 / lists 模块 |
| 无穷流 | `iterate` / `repeat` | `LazyList.iterate` | 无穷 `Iterator` (R-06) | 显式递归 spawn |
| 异步流 | `conduit` / `streaming` | `fs2.Stream` (S-28, S-37) | `tokio-stream` / `futures::Stream` (R-08) | 进程 + 消息驱动 |
| 背压 | conduit/pipes 自带 | fs2 自带 | `Stream::poll_next` + `ready!` | 通过 `gen_statem` 或自定 window |

---

## 6. 类型系统（表达力）

| 概念 | Haskell | Scala | Rust | Erlang |
|---|---|---|---|---|
| 代数数据类型 | `data`、sum + product (H-07) | `sealed trait` + `case class` (S-02) | `enum` + `struct` (R-02) | atom + tuple，靠约定 |
| 模式匹配 | ✅ 原生 | ✅ `match` | ✅ `match` | ✅ 函数头匹配 (E-01) |
| Type class / Trait | ✅ `class`/`instance` (H-02) | implicit / `given` (S-07) | `trait` (R-03) | ❌ 无静态类型约束 |
| Phantom / Typestate | phantom type + GADT (H-15) | phantom / sealed (S-09) | `PhantomData<T>` (R-09) | ❌ |
| 依值类型（近似） | DataKinds + GADTs (H-15, H-25) | Shapeless / Singletons | 有限 const generics | ❌ |
| 泛型 | 参数多态 + type class | type param + implicit | 参数化 trait (R-03) | ❌（动态类型） |

---

## 7. Effect 管理

| 概念 | Haskell | Scala | Rust | Erlang |
|---|---|---|---|---|
| 把"副作用"当值 | `IO a` / `ExceptT`/`ReaderT` (H-08) | `IO[A]` cats-effect (S-26) | 没有原生抽象，手写 tagless | 没有抽象，靠进程隔离 |
| Free Monad | `Free f a` (H-11) | `Free[F, A]` | 少见，可用 `enum` 近似 | 不适用 |
| Tagless Final | `class MonadDB m` (H-21) | `trait UserRepo[F[_]]` (S-22/30) | `trait Store` + generic | 不适用 |
| Algebraic Effects | polysemy / effectful / fused (H-26) | effect systems 生态 | 第三方 `keter`/自建 | 不适用 |

---

## 8. 资源安全

| 概念 | Haskell | Scala | Rust | Erlang |
|---|---|---|---|---|
| RAII 自动释放 | `bracket` / `ResourceT` (H-19) | `Resource[F, A]` (S-18/27) | `Drop` trait ✅ 原生 | `link` + `monitor` + 进程终止清理 |
| 资源组合 | `bracket`/`continue` | `.use`/`flatMap` | `?` + `Drop` 自然组合 | 进程消亡即释放 |

---

## 9. 测试

| 概念 | Haskell | Scala | Rust | Erlang |
|---|---|---|---|---|
| 单元测试 | hspec / tasty | munit / scalatest (S-40) | `#[test]` | eunit |
| 属性测试 | QuickCheck / Hedgehog (H-12) | ScalaCheck | proptest / quickcheck | PropEr / triq / 手写 (E-08) |
| 状态机属性测试 | hedgehog SM / qcsm (H-22) | - | - | PropEr FSM |
| 集成测试 | docker/testcontainers | testcontainers | testcontainers-rs | ct (common_test) |

---

## 10. 递归 & 抽象

| 概念 | Haskell | Scala | Rust | Erlang |
|---|---|---|---|---|
| 尾递归 | 编译器 + 手动 | `@tailrec` (S-05) | 手动 / `loop` 常见 | ✅ 必用（E-01） |
| Recursion schemes | recursion-schemes / 手写 (H-24) | droste / 手写 | 罕见 | 不常用 |
| Lambda 演算 / fix | 手写 (H-17) | 手写 | 手写（闭包） | 有限（缺少原生多态 Y） |
| Comonad | class `Comonad` (H-23) | cats `Comonad` | 无内建 | 不适用 |

---

## 11. 工具箱一览

| 用途 | Haskell | Scala | Rust | Erlang |
|---|---|---|---|---|
| 构建 | cabal / stack | sbt / mill / scala-cli | cargo | rebar3 / mix |
| 格式化 | fourmolu / ormolu | scalafmt | rustfmt | erlfmt |
| Lint | hlint | scalafix | clippy | elvis |
| REPL | ghci | scala REPL | `cargo-script`/`evcxr` | `erl` shell |
| HTTP | servant / warp | http4s / akka-http | axum / actix-web / hyper | cowboy / elli |
| JSON | aeson | circe | serde | jsx / jsone |
| 数据库 | persistent / beam | doobie / quill (S-78~) | sqlx / diesel | epgsql / odbc |

---

## 12. "一句话速记"

| 口诀 | 意思 |
|---|---|
| Haskell 是 **类型驱动**的 FP | 纯、有 Type class、Monad 是一等公民 |
| Scala 是 **工程优先**的 FP | FP + OOP 双栖，cats-effect 落地大厂 |
| Rust 是 **系统级**的 FP | 所有权 = 静态线性类型；没有 GC 的纯直觉 |
| Erlang 是 **分布式**的 FP | 不可变 + actor + OTP = 高可用之母 |

---

## 📌 学习路径建议

1. **从 Haskell 入门（抓直觉）**：这里的抽象都"有名字"，学会了就能迁移。
2. **用 Scala 上手工程**：同样的思想在 JVM + 大厂基础设施里有完整答案。
3. **用 Rust 理解"不可变"为什么非它不可**：所有权会让你重新敬畏 FP。
4. **用 Erlang 体会"容错"哲学**："Let it crash" 是 FP 的另一种极致。

> 四种语言看完后你会发现：**FP 不是语言特性的合集，而是一种思考问题的方式。**

