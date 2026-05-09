# OCaml 学习目录（规划中）

> 本目录**尚无 demo**，仅作为规划占位。完整学习路径见 [`ROADMAP.md`](./ROADMAP.md)。

## 为什么 OCaml 值得单独开一档

本仓库已经有 Haskell（纯函数式 + 惰性）和 Scala（JVM + 混合范式），但都没有覆盖 **ML 家族最"工业原味"的那一支**：

- **Jane Street**：全公司 OCaml 交易系统（1500+ 工程师）
- **Facebook**：Hack 语言、Flow 类型检查器、Infer 静态分析器都用 OCaml 写
- **Rust 编译器**：最早的 rustc 是 OCaml 写的（后来用 Rust 自举）
- **Coq / Rocq**：定理证明器用 OCaml 实现

OCaml 的独特卖点（Haskell/Scala 都给不了）：
1. **Module system（模块/函子）**：比 Haskell 的 typeclass 更显式、更可组合
2. **Impure 默认 + 可选纯**：不像 Haskell 那样对副作用全局管制，写起来更像"ML 版 Python"
3. **极快编译 + 原生代码**：单文件 `.ml` 用 `ocamlfind` 秒级编译

## 工具链要求

> ⚠️ 本机当前**未安装** OCaml。开工前请先：
>
> ```bash
> brew install opam
> opam init
> opam install dune utop
> ```
>
> 随后验证：
>
> ```bash
> dune --version    # 期望 >= 3.0
> ocaml -version    # 期望 >= 5.0
> ```

本目录计划采用 **dune 项目结构**（非散文件），每个 demo 独立 `bin/` + `dune` 配置。理由：OCaml 社区**标准做法**就是 dune，散文件脱离生态。

## 目录结构（规划）

```
ocaml/
├── README.md                    ← 本文件
├── ROADMAP.md                   ← 40+ demo 学习路径
├── OCAML_ECOSYSTEM.md           ← 生态盘点（待补，对齐 HASKELL_ECOSYSTEM.md）
├── dune-project                 ← 统一 dune 工程根（待建）
├── 01_basics_and_adt/
│   ├── dune
│   └── main.ml
├── 02_pattern_matching/
│   └── ...
└── ...
```

## 执行状态

| 阶段 | 状态 | 产物 |
|---|---|---|
| 阶段 1：盘点 + roadmap | ✅ 本次完成 | `README.md`、`ROADMAP.md` |
| 阶段 2：环境搭建 + 首批 demo（01-08 核心） | ⏳ 等待环境就绪 | `dune-project`、8 个 demo |
| 阶段 3：中阶 demo（09-25） | ⏳ 排期 | Functor / Effects / Core 库 |
| 阶段 4：实战 demo（26-40+） | ⏳ 排期 | Jane Street Core / MirageOS / 编译器 |
