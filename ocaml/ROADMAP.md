# OCaml 40+ Demo 学习路线图

> 对齐 [`haskell/HASKELL_FP_ROADMAP.md`](../haskell/HASKELL_FP_ROADMAP.md) 的粒度。
> 状态图例：⏳ 待做 / 🚧 进行中 / ✅ 完成。当前全部 ⏳。

---

## 🧱 阶段一：ML 基础（demo 01-08）

| # | Demo | 卖点 | 状态 |
|---|---|---|---|
| 01 | basics_and_adt | `let` / `match` / `type` 三件套，打印 Fibonacci | ⏳ |
| 02 | pattern_matching | 深度嵌套模式、`when` 守卫、`as` 绑定 | ⏳ |
| 03 | higher_order_and_currying | `|>` / `@@` / 偏应用 | ⏳ |
| 04 | variants_and_records | 标签型 variant、行多态 record | ⏳ |
| 05 | exceptions_vs_result | `exception` vs `('a, 'b) result`，何时用哪个 | ⏳ |
| 06 | tail_recursion | `@tail_mod_cons`，对比 Haskell 惰性 | ⏳ |
| 07 | mutable_refs_and_arrays | `ref` / `mutable field` / `Array`，ML 的可变一面 | ⏳ |
| 08 | io_and_channels | `Printf` / `Scanf` / `in_channel`，最小文件 IO | ⏳ |

---

## 🧬 阶段二：ML 模块系统（demo 09-15，OCaml 的灵魂）

| # | Demo | 卖点 | 状态 |
|---|---|---|---|
| 09 | modules_and_signatures | `module type` + `module M : SIG = struct ... end` | ⏳ |
| 10 | functors_basic | 参数化模块，手写一个 `Map.Make(Key)` | ⏳ |
| 11 | functors_advanced | 多参 functor + sharing constraint | ⏳ |
| 12 | first_class_modules | `module M = (val m : SIG)`，模块当值传 | ⏳ |
| 13 | abstract_types | `type t` 抽象 + 信息隐藏 | ⏳ |
| 14 | include_and_extension | `include` + 模块合成模式 | ⏳ |
| 15 | polymorphic_variants | `` `Red | `Green | `Blue ``，开放 variant | ⏳ |

---

## ⚡ 阶段三：现代 OCaml 5 特性（demo 16-22）

| # | Demo | 卖点 | 状态 |
|---|---|---|---|
| 16 | effects_handlers | OCaml 5 代数效应系统（对标 Koka） | ⏳ |
| 17 | effects_as_generators | 用 effects 实现 Python/JS 的 generator | ⏳ |
| 18 | domains_parallel | `Domain.spawn`，多核并行 | ⏳ |
| 19 | atomic_and_lockfree | `Atomic.t`，lock-free 数据结构 | ⏳ |
| 20 | gadt_interpreter | OCaml 的 GADT（对标 `haskell/38_GADTInterpreter.hs`） | ⏳ |
| 21 | polymorphism_and_variance | `+'a` / `-'a`，协变逆变 | ⏳ |
| 22 | typeclass_via_modules | 用 functor 模拟 Haskell typeclass | ⏳ |

---

## 🧰 阶段四：Jane Street 工业风（demo 23-30）

> 对标 Jane Street 在 OCaml 上的工业实践。**需要 `core` / `async` 包**。

| # | Demo | 卖点 | 状态 |
|---|---|---|---|
| 23 | core_basics | Jane Street `Core` 库：Map / List / Option 的工业版 | ⏳ |
| 24 | async_basics | `Async` 库：Deferred + Ivar | ⏳ |
| 25 | async_rpc | Async RPC：Jane Street 内部 RPC 协议的缩影 | ⏳ |
| 26 | bin_prot_serialization | 高性能二进制序列化 | ⏳ |
| 27 | incr_dom | `Incremental` 库：增量计算（对标 FRP） | ⏳ |
| 28 | command_line | `Command` 库：CLI 工具 | ⏳ |
| 29 | expect_tests | 快照测试 | ⏳ |
| 30 | ppx_deriving | 预处理器扩展（对标 Haskell GHC extensions） | ⏳ |

---

## 🏭 阶段五：实战项目（demo 31-40+）

| # | Demo | 行业映射 | 状态 |
|---|---|---|---|
| 31 | tiny_interpreter | Lex + Parse + Eval（对标 `haskell/43_TinyLangCompiler.hs`） | ⏳ |
| 32 | option_pricing_dsl | 金融 DSL（对标 `haskell/42_OptionPricingDSL.hs`） | ⏳ |
| 33 | utxo_ledger | 区块链账本（对标 `haskell/45_UTXOLedger.hs`） | ⏳ |
| 34 | auto_diff | 自动微分（对标 `haskell/44_AutoDiff.hs`） | ⏳ |
| 35 | csv_to_json_etl | ETL（对标 `haskell/48_CsvToJsonETL.hs`） | ⏳ |
| 36 | minimal_frp | FRP（对标 `haskell/47_MinimalFRP.hs`） | ⏳ |
| 37 | flow_type_inference | Hindley-Milner 推导（呼应 Facebook Flow） | ⏳ |
| 38 | mirage_os_mini | 单进程 TCP echo（呼应 MirageOS unikernel） | ⏳ |
| 39 | irmin_merkle_store | Merkle + Git-like 存储（呼应 Irmin） | ⏳ |
| 40 | multicore_raytracer | Domain 并行 + 简单光追，展示 OCaml 5 威力 | ⏳ |

---

## 📦 产出形式约定

- 每个 demo 一个子目录，内含 `dune` + `main.ml`（+ 可选 `.mli`）
- 根 `dune-project` 声明统一版本和 dependency
- 每个 demo 顶端写运行命令注释：`(* dune exec ./main.exe *)`
- 外部依赖必须在 `dune` 里显式声明，确保 `dune build` 一次通过

---

## ⚠️ 启动条件

执行阶段 2 前需先安装环境（见 [`README.md`](./README.md) "工具链要求"）。
