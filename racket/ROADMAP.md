# 🛣️ Racket 学习路线图

本路线把 15 个 demo 按"学完会的能力"分阶段，每阶段给出明确的"应该能独立做出什么"，方便自检。

---

## 阶段 1：Scheme 基础（demo 01~04）

**学完应能独立做：**
- 写出递归遍历列表 / 树的纯函数
- 用 `match` 做模式匹配，含 `(list a b ...)` 解构与 `?` 谓词
- 定义并使用 `struct`（带 `#:transparent` 与字段访问器）
- 区分 `let` / `let*` / `letrec` / `named let`

**关键 demo：**
- 01: 基础语法、`cond` / `case` / `if`、列表 `(map f xs)` / `(filter pred xs)` / `(foldl f init xs)`
- 02: 高阶函数（`compose`、`curry`、闭包工厂）
- 03: 真尾递归（Racket 语言层面保证）+ named let 写循环
- 04: `struct` 与 `match` 配合（含 ADT 风格用法）

**与 Clojure / OCaml 的对比：**
| | Racket | Clojure | OCaml |
|---|---|---|---|
| 列表字面量 | `'(1 2 3)` 或 `(list 1 2 3)` | `[1 2 3]` 或 `'(1 2 3)` | `[1; 2; 3]` |
| 模式匹配 | `match` 库 | `core.match` 库 | 语法层 `match`（最强） |
| 数据建模 | `struct` | `defrecord` / map | `type t = ...` |
| 不可变性 | 默认（`set!` 仍可用）| 默认 | 默认 |

---

## 阶段 2：宏三连击（demo 05、06、15）

**学完应能独立做：**
- 用 `syntax-rules` 写简单的卫生模板宏（如 `swap!`、`my-if`）
- 用 `syntax-parse` 写带类型检查的工业级宏（如自定义 `for/list-with-index`）
- 把一个领域问题（如自制 SQL DSL、状态机 DSL）通过宏编译成普通 Racket 代码

**关键 demo：**
- 05: `syntax-rules` 入门（不变量 / 省略号模式 / 嵌套）
- 06: ⭐ `syntax-parse` —— 检查 syntax class、自定义错误信息、`pattern` / `splicing` / `~optional`
- 15: 综合 —— 写一个完整 DSL（建议方向：mini 状态机或 mini SQL）

**Racket 宏的独特之处：**
- **真正卫生**：不需要手写 `gensym`，`syntax-rules` 默认卫生
- **编译期 phase 分离**：`begin-for-syntax`、`for-syntax` 让宏可以调用任意 Racket 代码
- **错误消息工程化**：`syntax-parse` 能像类型系统一样给出"期待 X 得到 Y"的错误

---

## 阶段 3：契约与类型（demo 07、08）

**学完应能独立做：**
- 给函数加 `(-> Number Number Number)` 契约，理解 blame 责任在哪一方
- 用 `define/contract` 在边界处自动校验
- 用 Typed Racket 给一个 Racket 模块加渐进类型，并理解 `Listof`、`Pair` 等内置类型构造器
- 理解契约（运行期）与 Typed Racket（编译期）的取舍

**关键 demo：**
- 07: ⭐ `racket/contract` —— `->`、`->*`、`->i`、`flat-contract`、`provide/contract`
- 08: ⭐ `#lang typed/racket` —— `Real`、`Listof`、`U`（union）、`define:`、`require/typed`

**与其它语言的对比：**
- Clojure spec/malli：动态类型 + 运行期检查（运行时 spec 校验）
- Haskell：纯静态类型
- TypeScript：渐进类型 + 类型擦除
- **Racket 同时拥有**：契约（动态、可在边界处加）+ Typed Racket（静态、与无类型代码可互操作）

---

## 阶段 4：第一类延续（demo 09）

**学完应能独立做：**
- 用 `call/cc` 实现 generator（迭代器）
- 用 `call/cc` 实现 amb（非确定性选择 + 回溯搜索）
- 理解"延续 = 当前栈快照"的心智模型

**关键 demo：**
- 09: ⭐ `call/cc` 三大经典应用：
  1. `(generator)` —— 把递归遍历变成可暂停/恢复的迭代器
  2. `(amb)` —— 非确定性求值（解八皇后/逻辑谜题）
  3. 协程 —— 用延续实现协作多任务

**注意：**`call/cc` 在 Scheme/Racket 是一等公民，但在 OCaml/Haskell 需要 monad（continuation monad），在 Java/Python 几乎做不到。

---

## 阶段 5："语言工作台"（demo 11）

**学完应能独立做：**
- 写一个最小自定义 `#lang my-lang`，让 `.rkt` 文件用你的语法
- 理解 reader / expander / runtime 三层职责
- 看懂 Beautiful Racket 那本书前 3 章

**关键 demo：**
- 11: ⭐ 自定义 #lang —— 用 `#lang reader` 拦截解析，用 `#%module-begin` 改变求值

**为什么这是杀手锏：**
- Pollen（出版排版）、Scribble（文档）、HtDP（教学语言）、Pie（依值类型教学语言）—— 都是用同一套机制实现的不同 #lang，共享同一个 IDE / 包系统 / REPL。
- Clojure 做不到（要改 ClojureScript）、OCaml 做不到（PPX 只能改语法不能改语义）、Haskell 做不到（QuasiQuoter 受限）。

---

## 阶段 6：工程化标准库（demo 10、12、13、14）

**学完应能独立做：**
- 用 parser combinator 写一个简易语言解析器
- 用 `web-server/insta` 起一个最小 HTTP 服务
- 用 `thread` + `channel` + `sync` 做 CSP 风格并发
- 用手写 mini quickcheck 或 rackcheck 做基于性质的测试

**关键 demo：**
- 10: parser combinators（`bind` / `>>=` / `choice` / `many`）
- 12: web-server（`serve/servlet`，端口 8080，自动 GET 自测后退出）
- 13: 并发三件套（`thread` / `make-channel` / `sync` / `sync/timeout`）
- 14: 性质测试（手写迷你 + rackcheck 对照）

---

## 后续扩展方向（不在本仓库 demo 内）

- **Pollen**：Matthew Butterick 用 Racket 做的出版语言，写过《Practical Typography》
- **Scribble**：Racket 自己的文档系统（你正在读的所有 Racket 官方文档都用它写）
- **DrRacket IDE**：自带宏 stepper / contract violation 可视化 / 代码画图（demo 用 `(require pict)`）
- **Pie / cur**：依值类型教学语言（"Little Typer" 那本书）
- **Slideshow**：用 Racket 写演示幻灯片
- **Redex**：操作语义实验框架（论文工具链）

学完本仓库 15 demo 后，建议读：
1. *Beautiful Racket* 前 5 章（自定义 #lang 实战）
2. 《The Little Schemer》《The Seasoned Schemer》（建立 Scheme 心智）
3. *The Racket Reference* 中 `racket/contract` 与 `syntax/parse` 章节（工程必备）
