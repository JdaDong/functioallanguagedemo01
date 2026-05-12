# Clojure ROADMAP 阶段四完成总结：数据与类型

> 完成时间：2026-05-09
> 范围：[`22_spec_basic.clj`](./22_spec_basic.clj) ~ [`28_schema_evolution/`](./28_schema_evolution/)
> 形态：3 个散文件 + 4 个项目档子目录
> 形态修订：相对 STAGE_2 预告，demo 22 实际是散文件（`clojure.spec.alpha` 是 Clojure 1.9+ 标准库自带 ns，不需要外部依赖）

## 📦 交付物

| Demo | 主题 | 形态 | 行数 | 关键卖点 |
|---|---|---|---|---|
| 22 spec_basic | spec.alpha 入门 | 散文件 | ~110 | valid?/explain/conform/keys/coll-of/fdef/instrument |
| 23 spec_generators | property-based testing | 项目档 | ~115 | exercise/with-gen/quick-check/shrink；200 样本捕获 -100 边界 bug |
| 24 malli_schema | 现代 schema-as-data | 项目档 | ~110 | validate/explain/decode+transformer/closed map/动态构造 |
| 25 data_oriented | DOP 思想 | 散文件 | ~125 | OOP→DOP 4 段对比；multimethod 开放分派 |
| 26 edn_format | Clojure 版 JSON | 散文件 | ~115 | tagged literal/clojure.edn 安全/未知 tag 兜底 |
| 27 transit_format | 跨语言高性能格式 | 项目档 | ~120 | json/json-verbose/msgpack 三档；handler 扩展 |
| 28 schema_evolution | v1→v2 演进策略 | 项目档 | ~125 | 显式 migrate + 三类 property test |

总计 ~820 行 Clojure 代码，全部通过验证（7/7 PASS）。

## 🪲 实跑过程中碰到 + 修复的真实坑（共 5 处）

1. **demo 23 `prop` alias 编译期解析失败**：在函数体内 `require` 太晚，宏 `prop/for-all` 编译就挂了 → 提到 `ns` 的 `:require` 里
2. **demo 23 `gen/small-integer` 找不到**：`clojure.spec.gen.alpha` 和 `clojure.test.check.generators` API 重叠但不完全相同，`small-integer` 只在后者 → 分开 alias `gen` 和 `tcg`
3. **demo 24 同 1 的坑重犯**：`malli.dev` `mdev` alias 在函数体里 `require` 也不行，再次提到 ns（**自检失败：上一个 demo 已踩过同样坑，本应学到教训**）
4. **demo 24 `mg/sample User`**：malli generator 遇到 `[:re ...]` 正则约束需要可选依赖 `test.chuck` → 简化为不含正则的 `SimpleUser`，避免拉无关依赖（守则 2）
5. **demo 27 `Money` 函数体内 defrecord 引用失败**：函数体下方用 Java class 名时编译期还没生成 → 把 `defrecord` 提到顶层
6. **demo 27 自夸的 ~50% 压缩比是吹的**：实测 100 条记录 json vs json-verbose 只有 1.15x → 老老实实改成"~10-15%"（守则 1：不藏）

## 📊 教学高光时刻

| 现象 | 教学意义 |
|---|---|
| demo 23 `quick-check 200` 在 200 样本里直接 shrink 到 `n = -100` | property test 不是玄学，确实在抓 bug |
| demo 24 `[:and :int [:>= 0]]` 是普通 vector，可序列化、动态拼装 | "schema 即数据" vs spec "schema 即代码" 的本质差异 |
| demo 24 `m/decode {:age "30"} mt/string-transformer → {:age 30}` | spec 没有的杀手锏：一行搞定 HTTP/JSON 入参强类型化 |
| demo 25 `(-> data f1 f2 f3)` 取代 OOP 方法链 | 数据 / 函数 / 身份解耦的具象化 |
| demo 26 `edn/read-string "#=(+ 1 2)"` 直接抛出，`core/read-string` 默默执行返回 3 | RCE 风险具象化：永不要用 core/read-string 读外部数据 |
| demo 27 `["^ ","~:tags",["~#set",...]]` 看到 Transit 的 cache 引用 + tagged value | 线缆协议的设计哲学一眼看穿 |
| demo 28 property test #1 真的失败：v1.name(1-100) vs v2.first-name(<=50) 边界冲突 | 我手写时根本没意识到的 schema 不兼容，被 200 样本抓到 |

## 🎯 状态对照

| 阶段 | 范围 | 状态 |
|---|---|---|
| 阶段一 Lisp 基础 | demo 01-08 | ✅ 已完成 |
| 阶段二 宏系统 | demo 09-14 | ✅ 已完成 |
| 阶段三 并发模型 | demo 15-21 | ✅ 已完成 |
| **阶段四 数据/类型** | **demo 22-28** | **✅ 本次完成** |
| 阶段五 实战项目 | demo 29-40+ | ⏳ 待开工 |

## 🚦 下一步

阶段五 demo 29-40+：实战项目（Ring/Compojure/Reitit/Datomic mini/Datalog/Reagent/re-frame/option pricing/UTXO/ETL/event sourcing）。

形态预估：基本全是项目档子目录（每个都有外部依赖）。
