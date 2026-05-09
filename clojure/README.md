# Clojure 学习目录（规划中）

> 本目录**尚无 demo**，仅作为规划占位。完整学习路径见 [`ROADMAP.md`](./ROADMAP.md)。

## 为什么 Clojure 值得单独开一档

本仓库 Scala 覆盖了 JVM 上的**静态类型 FP**，但完全没覆盖 **JVM 上的动态类型 Lisp 方言**：

- **Metabase**（开源 BI，GitHub 35k+ star）后端主力
- **Datomic**（不可变数据库，Cognitect 出品）
- **Nubank**（巴西最大数字银行，全栈 Clojure）
- **Walmart / CircleCI / Funding Circle** 核心交易系统

Clojure 的独特卖点（Scala/Haskell 都给不了）：
1. **Persistent data structures 一等公民**：`assoc` / `conj` / `update` 全是 O(log32 N) 的持久化结构
2. **宏系统（真 Lisp 宏）**：代码即数据（homoiconic），编译期任意变形
3. **STM 一等公民**：`ref` + `dosync`，比 Haskell STM 更早进入工业
4. **REPL-driven 开发**：**边运行边改的交互式体验，JVM 上无人能敌**

## 工具链要求

> ✅ 本机已安装：
> ```
> Clojure CLI 1.12.0.1517
> OpenJDK 26
> ```

本目录采用**分档形态**（与仓库其他语言风格一致）：

- **阶段一（demo 01-18，纯语言特性）**：散文件 `NN_name.clj`，`clojure -M` 直接跑，零依赖
- **阶段二起（demo 19+，需 core.async / spec / reagent 等）**：切换到 `deps.edn` 项目结构

## 目录结构（规划）

```
clojure/
├── README.md                       ← 本文件
├── ROADMAP.md                      ← 40+ demo 学习路径
├── CLOJURE_ECOSYSTEM.md            ← 生态盘点（待补，对齐 HASKELL_ECOSYSTEM.md）
├── 01_basics_and_collections.clj   ← 散文件档（阶段一）
├── 02_immutable_data_structures.clj
├── ...
├── deps.edn                        ← 项目档起点（demo 19 时创建）
└── 19_core_async_channels/
    └── src/main.clj
```

## 执行状态

| 阶段 | 状态 | 产物 |
|---|---|---|
| 阶段 1：盘点 + roadmap | ✅ 本次完成 | `README.md`、`ROADMAP.md` |
| 阶段 2：首批 demo（01-08 核心） | ⏳ 排期 | 根 `deps.edn` + 8 个 demo |
| 阶段 3：中阶 demo（09-25） | ⏳ 排期 | macros / core.async / spec |
| 阶段 4：实战 demo（26-40+） | ⏳ 排期 | Datomic mini / Metabase 查询 / Reagent UI |
