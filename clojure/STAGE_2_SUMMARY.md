# Clojure ROADMAP 阶段二完成总结：宏系统

> 完成时间：2026-05-09
> 范围：[`09_macros_intro.clj`](./09_macros_intro.clj) ~ [`14_macro_hygiene.clj`](./14_macro_hygiene.clj)
> 形态：6 个散文件，零外部依赖

## 📦 交付物

| Demo | 主题 | 行数 | 关键卖点 |
|---|---|---|---|
| 09 macros_intro | 宏入门 | ~80 | unless/my-when/my-dotimes 手写；macroexpand 调试 |
| 10 macros_anaphoric | 反卫生宏 | ~80 | aif/awhen/aand；`~'it` 强制裸名 |
| 11 macros_dsl | SQL DSL | ~110 | 数据驱动 DSL；宏 vs 函数选型 |
| 12 macros_state_machine | 状态机宏 | ~115 | 编译期拒绝非法状态；订单 + 红绿灯例 |
| 13 reader_macros | 读时宏 | ~95 | `#()` `#_` `#?` `#'` `#inst` `#uuid` `#=` |
| 14 macro_hygiene | 卫生宏 | ~80 | 变量捕获反例 → `name#`/`gensym` 修复 |

总计 ~560 行 Clojure 代码，全部 `clojure -M` 直接运行。

## 🪲 实跑过程中碰到 + 修复的真实坑（共 6 处）

1. **demo 09 字符串嵌中文里用了英文双引号**：`"按需求值"` 把字符串切断 → 改全角『』
2. **demo 09 第 4 节注释逻辑反了**：unless2 是"假时执行"，输出实际正确，但我注释写"不该跑" → 修正语义
3. **demo 10 aif 强制 3 参**：`(aif test then)` 缺 else 时报错 → 改成 (aif test then) / (aif test then else) 双 arity
4. **demo 12 编译期错误被 Clojure 包了一层**：`Syntax error macroexpanding...` 看不到根因 → 用 `(or (ex-cause e) e)` 取真实消息
5. **demo 13 `#?` 不允许在 `.clj` 里写**：必须 `.cljc` → 改用 `(read-string {:read-cond :allow} ...)` 演示
6. **demo 14 残留未使用的 `my-when-bad`**：清自己孤儿（守则 3）

## 📊 教学高光时刻

| 现象 | 教学意义 |
|---|---|
| demo 09 `unless` 用函数无效 / 用宏才行 | 宏的存在意义具象化 |
| demo 10 反卫生宏让外层 `result` 被覆盖（输出 `true` 而非用户字符串） | "故意捕获"陷阱可见 |
| demo 11 同一份输入数据，宏版编译期固化 vs 函数版运行期翻译 | 编译期 vs 运行期边界 |
| demo 12 `(eval '(defmachine broken ...))` 编译期就抛出 `从 :a 转移到未声明状态 :b` | 编译期检查的力量 |
| demo 13 `#"\d+"` 类型 = `java.util.regex.Pattern` | "字面量"是 reader 的工作 |
| demo 14 macroexpand 看到 `tmp__153__auto__` | auto-gensym 全宇宙不重名 |

## 🎯 状态对照

| 阶段 | 范围 | 状态 |
|---|---|---|
| 阶段一 Lisp 基础 | demo 01-08 | ✅ 已完成 |
| **阶段二 宏系统** | **demo 09-14** | **✅ 本次完成** |
| 阶段三 并发模型 | demo 15-21 | ✅ 已完成 |
| 阶段四 数据/类型 | demo 22-28 | ⏳ 待开工 |
| 阶段五 实战项目 | demo 29-40+ | ⏳ 待开工 |

## 🚦 下一步

按你 A→B→C 决策，下一阶段执行 **B：阶段四数据/类型（demo 22-28）**。预期形态：
- demo 22-23 spec：项目档（需 `org.clojure/spec.alpha`）
- demo 24 malli：项目档（需 `metosin/malli`）
- demo 25 data-oriented：散文件（仅讨论思想 + 例子）
- demo 26 edn：散文件（`clojure.edn` 标准库自带）
- demo 27 transit：项目档（需 `com.cognitect/transit-clj`）
- demo 28 schema_evolution：项目档（基于 spec/malli 演进）

5 个项目档子目录 + 2 个散文件，估算 ~1000 行 Clojure。
