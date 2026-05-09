# 其他常用函数式编程语言盘点

> 本仓库当前主力覆盖 6 门：Haskell / Scala / Rust / Erlang / Elixir / （规划中）OCaml、Clojure。
> 本文用来**归档剩余值得认识的函数式语言**，防止口径散落在历次对话里。
> 原则：只收录有**真实工业落地**或**被大厂/顶级开源项目当作主力语言**的函数式/多范式 FP 友好语言。

---

## 🏛 第一梯队：血统纯正的 ML 家族（已规划，见本仓库相应目录）

| 语言 | 本仓库状态 | 一句话 |
|---|---|---|
| Haskell | ✅ 48 demo + roadmap + 生态盘点 | 学术 & 工业两开花，类型系统最强 |
| OCaml | 📋 `ocaml/ROADMAP.md`（规划中） | Jane Street / Facebook Flow / Rust 编译器雏形 |
| F# | ⏳ 待补 | .NET 上的 OCaml 方言，金融、博弈论工业首选之一 |

---

## 🧩 第二梯队：Lisp 家族

| 语言 | 本仓库状态 | 一句话 | 代表项目 |
|---|---|---|---|
| Clojure | 📋 `clojure/ROADMAP.md`（规划中） | JVM 上的 Lisp，持久化数据结构 + 宏 + STM | Metabase / Datomic / LogSeq |
| Racket | ⏳ 待补 | 教学/研究用 Scheme 超集，内置语言工作台 | Racket Pollen、Nanopass 编译器 |
| Common Lisp | ⏳ 待补 | 古老但至今仍被 Grammarly / 商用 CAD 使用 | SBCL、CLOS |
| Scheme / Guile | ⏳ 待补 | MIT 6.001 文化遗产；Guile 是 GNU 扩展脚本 | SICP / GnuCash |

---

## 🌀 第三梯队：Web / 前端领域的 FP 专业户

| 语言 | 核心卖点 | 代表项目 |
|---|---|---|
| Elm | "零运行时异常"的纯 FP 前端；TEA 架构影响了 Redux | NoRedInk 生产线 |
| PureScript | Haskell-in-the-browser，类型系统完整 | purescript-halogen、Lumi 商用 |
| ReScript（原 BuckleScript / ReasonML） | OCaml → JS，Facebook Messenger Web 版曾全栈 | Draftbit、Ahrefs |
| ClojureScript | Clojure → JS，配合 Re-frame 做 SPA | CircleCI 前端、很多 Kanban 工具 |

---

## 🔬 第四梯队：强类型/依赖类型研究型语言

| 语言 | 一句话 |
|---|---|
| Idris 2 | 依赖类型的"人话版" Agda，有自己的运行时 |
| Agda | Haskell 血统的定理证明器 |
| Coq / Rocq | CompCert（可验证 C 编译器）、iris 并发逻辑 |
| Lean 4 | 微软研究院 → 数学家的定理证明平台；也能当编程语言 |
| ATS | 带线性类型+依赖类型，可写内核 |

> 这些的"demo 化"价值低，但**知道它们存在**对理解 Haskell 高端特性（singletons、GADT）非常有帮助。

---

## 🚀 第五梯队：新兴实用派

| 语言 | 本仓库是否有类似生态 | 卖点 |
|---|---|---|
| Roc | 无 | Elm 作者新作，编译到原生，目标是函数式版 Go |
| Unison | 无 | 按内容寻址的代码存储，分布式计算语义 |
| Gleam | 无 | BEAM 上的类型安全语言（Erlang/Elixir 生态的静态类型版） |
| Koka | 无 | 微软研究院，代数效应系统一等公民 |
| Flix | 无 | 多范式 + 一等效应 + 时序逻辑 |

---

## 📊 与本仓库 6 门主力的对比定位

| 你已经在本仓库学过 | 想进一步看哪一门 | 理由 |
|---|---|---|
| Haskell | Idris / Agda / Lean | 把类型系统从工业级推到证明级 |
| Haskell | PureScript / Elm | 想把 FP 带到前端 |
| Scala | F# / OCaml | 想脱离 JVM 但保留 ML 家族思维 |
| Erlang | Gleam | BEAM + 静态类型 |
| Elixir | Gleam / Unison | 分布式 FP 的下一代尝试 |
| Rust | Roc / Koka | 想看"FP 原教旨但编译到原生" |

---

## 🗂 本仓库对这些语言的覆盖计划

- **OCaml**：✅ 已排期，见 [`ocaml/ROADMAP.md`](./ocaml/ROADMAP.md)
- **Clojure**：✅ 已排期，见 [`clojure/ROADMAP.md`](./clojure/ROADMAP.md)
- **其他语言**：暂不排期。若未来需要，按本文梯队优先级（F# → Elm → Racket → Gleam → Lean）增补。

---

## 参考资料

- [State of Functional Programming 2023/2024](https://www.jetbrains.com/lp/devecosystem-2023/)
- Serokell、Well-Typed、Jane Street、Tarides 等公司的技术博客
- 各语言官网的 "Who's using X" 页面
