# 🎩 Racket 函数式编程 Demo

> 用 15 个经典 demo，系统性认识 Racket 这门"语言工作台"——Scheme 系最现代化的工业派 Lisp。

Racket 是 PLT 团队从 PLT-Scheme 重命名而来的现代 Scheme 方言，但它远不止是一个 Lisp 实现：
**它把"创造编程语言"做成了一等公民**。每个 `.rkt` 文件可以指定自己的 `#lang`，从教学语言（Beginner Student）到 Typed Racket、Lazy Racket，再到完全自定义的 DSL，都生活在同一套工具链里。

如果你已经看过本仓库的 [Clojure 50 demo](../clojure/) 与 [OCaml 38 demo](../ocaml/)，本目录会让你看到两条互补线：
- 与 **Clojure** 的对照：同为 Lisp 家族，但 Racket 选择了「学术血统 + 真宏 + 渐进类型 + 自定义 #lang」而非 Clojure 的「JVM 实用主义 + persistent 数据 + STM」
- 与 **OCaml** 的对照：同样追求"做小语言"，但 OCaml 用 GADT + functor，Racket 用 macro + #lang，对底层语言学的两种解法

---

## 📋 Demo 索引

| # | 主题 | 关键概念 | #lang |
|---|------|---------|-------|
| 01 | basics_and_lists | 基础语法 / cond / match / 列表 | racket |
| 02 | higher_order | map / filter / foldl / 闭包 | racket |
| 03 | recursion_and_tail | 真尾递归 + named let | racket |
| 04 | structs_and_match | struct + match 模式 | racket |
| 05 | macros_intro | define-syntax + syntax-rules | racket |
| 06 | macros_syntax_parse ⭐ | syntax-parse 卫生宏（Racket 杀手锏）| racket |
| 07 | contracts ⭐ | 一等公民契约系统 | racket |
| 08 | typed_racket ⭐ | Typed Racket 渐进类型 | typed/racket |
| 09 | continuations ⭐ | call/cc 第一类延续 + generator + amb | racket |
| 10 | parser_combinators | 手写 parser combinator 库 | racket |
| 11 | racket_lang ⭐ | 自定义 #lang（真"语言工作台"） | racket + reader |
| 12 | web_server | 内置 web-server（8080 端口） | racket |
| 13 | concurrency_threads | thread + channel + sync (CSP) | racket |
| 14 | property_testing | 手写 mini quickcheck + rackcheck 对照 | racket |
| 15 | macros_dsl | 综合：用宏写 DSL（与 Clojure 11/47 对照）| racket |

⭐ = Racket 独门特性，其它语言较难找到对应物。

---

## 🚀 运行

```bash
# 需要安装 Racket >= 8.0 (macOS: brew install --cask racket)
cd racket

./run.sh           # 跑全部（部分 demo 会启 web 服务/线程，会自动退出）
./run.sh 5         # 只跑编号 05
./run.sh list      # 列出全部
```

也可以手动跑：

```bash
racket 01_basics_and_lists/main.rkt
racket 09_continuations/main.rkt
```

demo 14 的 rackcheck 对照部分需要：
```bash
raco pkg install rackcheck
```
（不装也能跑 demo 14 的"手写 mini 版"部分，自动 skip 真 rackcheck 段。）

---

## 🧠 学习路径建议

```
入门 (1h):    01 → 02 → 03 → 04
   建立 Racket = "Scheme + 工业级标准库"的直觉

宏三步走 (2h): 05 → 06 → 15
   syntax-rules 入门 → syntax-parse 工业级 → DSL 综合实战
   理解为什么 Racket 是教 macro 的最好语言

类型与契约 (1h): 07 → 08
   契约（运行期）+ Typed Racket（编译期）的渐进组合

延续 (1h):    09
   call/cc 是 FP 的"另一面"。这一个 demo 顶你看一本《SICP》第 4 章

真"语言工作台" (1h): 11
   自定义 #lang 是 Racket 的杀手锏。这是 Clojure / OCaml / Haskell 都做不到的事

工程化补充 (2h): 10 → 12 → 13 → 14
   parser / web / 并发 / 性质测试，证明 Racket 也能做"真东西"
```

---

## 📚 延伸阅读

- 官方教程：[Quick: An Introduction to Racket with Pictures](https://docs.racket-lang.org/quick/)
- 名著：*Beautiful Racket* by Matthew Butterick — 用 Racket 做 DSL / 排版语言
- 论文：*The Racket Manifesto* (POPL 2015) — 解释为什么 Racket 把"创造语言"作为一等公民
- 对比阅读：本仓库 [`clojure/ROADMAP.md`](../clojure/ROADMAP.md)（同为 Lisp 家族）与 [`ocaml/ROADMAP.md`](../ocaml/ROADMAP.md)（同为"做小语言"的 ML 派）
