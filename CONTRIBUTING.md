# Contributing

感谢你对本仓库（**函数式编程多语言学习手册**）的兴趣！本项目以 **Demo 为单位** 系统演示 Scala / Akka / Erlang / Elixir / Haskell / Rust 六种语言下的函数式编程思想，主要用途是 **学习 / 对照 / 速查**，而不是生产级库。

因此我们对 PR 的要求围绕三点：**可自学、可运行、可横向对照**。

---

## 1. 贡献类型

欢迎以下方向的 PR：

| 类别 | 例子 |
|---|---|
| 📖 新增 Demo | 覆盖某个尚未演示的 FP 概念（见下一节命名约定） |
| 🐛 修正 Bug | 某个 Demo 无法运行、注释与代码不符、依赖版本过期 |
| 📝 文档改进 | 补充 roadmap、跨语言对照、学习路径说明 |
| 🧪 补充测试 | 给已有 Demo 加 property / 单元冒烟测试 |
| 🔗 交叉链接 | 把不同语言里表达"同一思想"的 Demo 互相关联 |

**不太欢迎**：生产级 feature、破坏"单文件可跑"特性的大规模重构、引入大量二进制/构建产物。

---

## 2. Demo 命名约定

每种语言都按 **`NN_英文名.扩展名`** 组织，`NN` 为两位数字序号：

| 语言 | 目录 | 扩展名 | 例 |
|---|---|---|---|
| Scala | `scala/` | `.scala` | `12_ValidatedRegistration.scala` |
| Akka | `scala/akka/` | `.scala` | `01_ActorBasics.scala` |
| Erlang | `erlang/` | `.erl` | `03_actor_model.erl` |
| Elixir | `elixir/` | `.exs` | `06_genserver_agent_task.exs` |
| Haskell | `haskell/` | `.hs` | `09_LensAndOptics.hs` |
| Rust | `rust/` | `.rs` | `04_error_handling_result_chain.rs` |

**序号规则**：追加式分配，续接当前目录最大编号。不要回头改已有序号（会破坏 roadmap 里的引用）。

**命名规则（按语言惯例）**：

- Scala / Haskell：`PascalCase` —— 如 `FunctorApplicativeMonad`
- Erlang / Elixir / Rust：`snake_case` —— 如 `pattern_matching`

---

## 3. 单文件可运行（硬性要求）

本仓库一个核心约定是：**每个 Demo 都是一个独立可运行的文件**，不依赖多文件工程结构。具体落地方式：

| 语言 | 运行方式 | 依赖处理 |
|---|---|---|
| Scala | `scala xxx.scala` 或 `scala-cli run xxx.scala` | `//> using dep` 指令声明，scala-cli 自动拉取 |
| Haskell | `runghc xxx.hs` 或 `cabal v2-run` | 基础 Demo 只用 `base`；高级 Demo 注释里说明需要哪些包 |
| Rust | `rustc xxx.rs && ./demo` | 基础 Demo 只用 `std`；需要 crate 的在注释里说明 |
| Erlang | `erlc xxx.erl && erl -noshell -s xxx main -s init stop` | 只用 OTP 自带行为 |
| Elixir | `elixir xxx.exs` | 基础 Demo 零依赖；高级 Demo 用 `Mix.install/1` 嵌入拉取 |

> **不要为一个 Demo 建立独立的 `Cargo.toml` / `cabal.project` / `mix.exs` 工程**（除非是专门演示工程化的那一类 Demo）。

---

## 4. 注释风格

本仓库的 Demo 以 **"给人读"** 为首要目的，不是以"最优代码"为目的。因此：

- **中文注释优先**：README、roadmap、Demo 内注释统一用中文；代码标识符用英文。
- **顶部说明**：每个 Demo 文件开头用注释块写清楚"本 Demo 演示什么 FP 概念 / 对应其他语言的哪个 Demo"。
- **分节分段**：长 Demo 用 `// ===== 1. 概念 A =====` 风格分节，方便对照 roadmap。
- **刻意重复**：如果一个概念在多个 Demo 中出现，**不要为了 DRY 而抽取共享代码** —— 单文件自包含更适合学习。

---

## 5. 本地验证

提交前请手动跑一次你加的 Demo：

```bash
# Elixir
elixir/run.sh <N>

# Erlang
erlang/run.sh <N>

# Haskell
runghc haskell/NN_Xxx.hs

# Rust
rustc rust/NN_xxx.rs -o /tmp/demo && /tmp/demo

# Scala
scala scala/NN_Xxx.scala
# 或需要依赖的 Demo：
scala-cli run scala/NN_Xxx.scala
```

如果你的 PR 影响"零依赖"那批 Demo（例如 Elixir 01~08、Haskell 01~07、Scala 01~20），请确保 CI 的 `smoke-fast` job 能通过。

---

## 6. 同步更新文档

新增或移动 Demo 时，**务必同步更新以下 3 处**：

1. **根 `README.md`**：
    - 项目结构树（按语言分的列表）
    - 核心概念一览表（按语言的列）
    - 推荐学习路径（如果新 Demo 属于路径关键一步）
2. **该语言的 `ROADMAP.md`**：
    - 进度表中加一行
    - 学习路径里找到合适的位置插入
3. **`LANGUAGE_COMPARISON.md`**：
    - 如果新 Demo 在跨语言对照表里能找到对位（例如"Monad bind"在多语言里都有），给对应单元格加上编号引用

---

## 7. Commit / PR 风格

- Commit 标题用现在时祈使句，前缀表明影响范围：
    - `elixir:` / `scala:` / `haskell:` / `rust:` / `erlang:` / `akka:`
    - `docs:` / `ci:` / `chore:`
- PR 描述至少包含：
    - **动机**：要演示的 FP 概念 / 修复的问题
    - **对照**：这个概念在其他语言里的对应 Demo（如有）
    - **本地验证**：你是怎么跑过的

---

## 8. 许可证

本仓库使用 **MIT License**（见根目录 `LICENSE`）。提交 PR 即视为你同意你的贡献以同一许可证发布。

---

**感谢贡献！期待一起把这个"跨语言 FP 学习档案"越堆越完整。** 🎉
