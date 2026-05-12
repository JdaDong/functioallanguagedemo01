# OCaml 学习目录

> 阶段 1 + 阶段 2 + 阶段 3 + 阶段 4 + 阶段 5 的 demo 01-37 + 40 **已全部编译通过 + 运行通过**（8 + 7 + 7 + 8 + 8 = 38 个）（OCaml 5.4.1 / dune 3.23 / Jane Street v0.17 套装）。demo 38/39 需 mirage/irmin 全家桶，已跳过。完整学习路径见 [`ROADMAP.md`](./ROADMAP.md)。

## 为什么 OCaml 值得单独开一档

本仓库已经有 Haskell（纯函数式 + 惰性）和 Scala（JVM + 混合范式），但都没有覆盖 **ML 家族最"工业原味"的那一支**：

- **Jane Street**：全公司 OCaml 交易系统（1500+ 工程师）
- **Facebook**：Hack 语言、Flow 类型检查器、Infer 静态分析器都用 OCaml 写
- **Rust 编译器**：最早的 rustc 是 OCaml 写的（后来用 Rust 自举）
- **Coq / Rocq**：定理证明器用 OCaml 实现

OCaml 的独特卖点（Haskell/Scala 都给不了）：
1. **Module system（模块/函子）**：比 Haskell 的 typeclass 更显式、更可组合
2. **Impure 默认 + 可选纯**：不像 Haskell 那样对副作用全局管制，写起来更像「ML 版 Python」
3. **极快编译 + 原生代码**：单文件 `.ml` 用 `ocamlfind` 秒级编译

## 工具链要求

> ⚠️ 本机当前**未安装** OCaml。开工前请先：
>
> ```bash
> brew install opam
> opam init -a -y --shell-setup
> eval $(opam env)
> opam install -y dune utop
> ```
>
> 阶段 4 额外需要 Jane Street 套装：
>
> ```bash
> opam install -y core async bin_prot ppx_bin_prot incremental ppx_expect ppx_jane
> ```
>
> 随后验证：
>
> ```bash
> dune --version    # 期望 >= 3.0
> ocaml -version    # 期望 >= 5.0
> ```

本目录采用 **dune 项目结构**（非散文件），每个 demo 一个子目录 + 自己的 `dune` 文件，根目录有 `dune-project`。

## 目录结构（已落盘）

```
ocaml/
├── README.md
├── ROADMAP.md
├── dune-project                 ← 工程根（lang dune 3.0）
├── 01_basics_and_adt/           ✓ 已写
├── 02_pattern_matching/         ✓ 已写
├── 03_higher_order_and_currying/ ✓ 已写
├── 04_variants_and_records/     ✓ 已写
├── 05_exceptions_vs_result/     ✓ 已写
├── 06_tail_recursion/           ✓ 已写
├── 07_mutable_refs_and_arrays/  ✓ 已写
├── 08_io_and_channels/          ✓ 已写
├── 09_modules_and_signatures/   ✓ 已写（含 main.mli）
├── 10_functors_basic/           ✓ 已写
├── 11_functors_advanced/        ✓ 已写
├── 12_first_class_modules/      ✓ 已写
├── 13_abstract_types/           ✓ 已写
├── 14_include_and_extension/    ✓ 已写
├── 15_polymorphic_variants/     ✓ 已写
├── 16_effects_handlers/         ✓ 已写（OCaml 5）
├── 17_effects_as_generators/    ✓ 已写（OCaml 5）
├── 18_domains_parallel/         ✓ 已写（OCaml 5 + unix）
├── 19_atomic_and_lockfree/      ✓ 已写（OCaml 5）
├── 20_gadt_interpreter/         ✓ 已写
├── 21_polymorphism_and_variance/ ✓ 已写
├── 22_typeclass_via_modules/    ✓ 已写
├── 23_core_basics/              ✓ 已写（+ core）
├── 24_async_basics/             ✓ 已写（+ async）
├── 25_async_rpc/                ✓ 已写（+ async_rpc_kernel）
├── 26_bin_prot_serialization/   ✓ 已写（+ bin_prot）
├── 27_incremental_compute/      ✓ 已写（+ incremental）
├── 28_command_line/             ✓ 已写（+ core_unix.command_unix）
├── 29_expect_tests/             ✓ 已写（+ ppx_expect）
├── 30_ppx_deriving/             ✓ 已写（+ ppx_jane）
├── 31_mini_lang_interpreter/    ✓ 已写（闭包 + let rec）
├── 32_option_pricing_dsl/       ✓ 已写（BS + MC）
├── 33_ad_autodiff/              ✓ 已写（前向+反向）
├── 34_etl_pipeline/             ✓ 已写（Async Pipe）
├── 35_utxo_ledger/              ✓ 已写（UTXO + 双花拒绝）
├── 36_frp_minimal/              ✓ 已写（push FRP）
├── 37_hindley_milner_inference/ ✓ 已写（Algorithm W）
└── 40_raytracer_multicore/      ✓ 已写（Domain 并行 + PPM）
```

每个 demo 跑法（装好环境后）：

```bash
cd ocaml/01_basics_and_adt && dune exec ./main.exe
```

或在 `ocaml/` 根批量构建：`dune build`

## 执行状态

| 阶段 | 状态 | 产物 |
|---|---|---|
| 阶段 1：盘点 + roadmap | ✅ 完成 | `README.md`、`ROADMAP.md` |
| 阶段 2-1：基础 demo（01-08） | ✅ 完成（编译+运行 8/8） | 8 个 demo + `dune-project` |
| 阶段 2-2：模块系统（09-15） | ✅ 完成（编译+运行 7/7） | functor / first-class / 抽象类型 |
| 阶段 3：OCaml 5 现代特性（16-22） | ✅ 完成（编译+运行 7/7） | effects / domains / GADT / typeclass |
| 阶段 4：Jane Street 工业（23-30） | ✅ 完成（编译+运行 8/8） | core / async / bin_prot / incremental / Command / expect_tests / ppx_jane |
| 阶段 5：实战项目（31-37+40） | ✅ 完成（编译+运行 8/8） | 解释器 / 期权 DSL / 自动微分 / ETL / UTXO / FRP / HM 推导 / 多核光追。**38/39 需 mirage/irmin，已跳过** |

