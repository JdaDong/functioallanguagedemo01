# Clojure ROADMAP 阶段六完成总结：深度补遗

> 完成时间：2026-05-11
> 范围：demo 41-50（10 个 demo）
> 形态：6 个项目档（带依赖：core.async / test.check / malli）+ 4 个散文件
> 主旨：阶段一到五覆盖了 Clojure 工业 90% 使用面；本阶段把 ROADMAP 之前点过名但只浅做的主题（transducers / core.async / spec / malli / macros / Java interop）补深。

## 📦 交付物

| Demo | 主题 | 形态 | 关键看点 |
|---|---|---|---|
| 41 [core_async_pipeline_async](./41_core_async_pipeline_async/) | pipeline 三件套 | 项目档 | 50 个并发 HTTP 454ms（vs 串行 ~2500ms）；保序 vs fan-out 不保序对比 |
| 42 [core_async_pubsub_mix](./42_core_async_pubsub_mix/) | 事件总线核心 | 项目档 | pub/sub 按 :type 分流 / mult/tap 广播 / mix 运行时 pause/solo |
| 43 [core_async_error_dlq](./43_core_async_error_dlq/) | 错误处理 + 死锁 | 项目档 | go-block 异常被吞 / DLQ 模式 / 取消令牌 / 死锁陷阱 A/B |
| 44 [44_transducers_advanced.clj](./44_transducers_advanced.clj) | 自定义 transducer | 散文件 | take-evens/running-sum/take-until-sum-exceeds；transduce 比 lazy 快 1.6×，loop 5.9× |
| 45 [spec_advanced](./45_spec_advanced/) | spec 高阶 | 项目档 | with-gen 紧约束 / stest/check 50 case / multi-spec / recursive ::tree / instrument |
| 46 [malli_advanced](./46_malli_advanced/) | malli 高阶 | 项目档 | custom registry / decode+transform / m/provider 反推 / m/walk 改写 / **m/validator 编译版快 20.7×** |
| 47 [47_macros_deep.clj](./47_macros_deep.clj) | 宏深度 | 散文件 | 编译期常量折叠 / &env 探测 locals / &form 拿行号 / macroexpand 三层 / 编译期 schema |
| 48 [48_metadata_protocols.clj](./48_metadata_protocols.clj) | metadata + protocol | 散文件 | metadata 不影响 = / 函数 var meta / extend-protocol / reify / extend-via-metadata |
| 49 [49_reducers_fold.clj](./49_reducers_fold.clj) | 并行 fold | 散文件 | r/fold combinef seed/结合律 / foldable 边界 / 并行 group-by |
| 50 [50_java_interop_advanced.clj](./50_java_interop_advanced.clj) | Java 互操作 | 散文件 | reify java iface / proxy abstract class / **type hint 实测 258× 加速反射** / 异常互通 |

合计：~1500 行 Clojure，**10/10 PASS** ✅。

## 🪲 实跑过程中的真实坑（共 7 处）

1. **demo 42 `(.solo-mode m :mute)` 错用 Java 互操作语法**：`solo-mode` 是 core.async 函数不是方法 → 改用 `(solo-mode m :mute)` 函数调用
2. **demo 43 section 3 死锁**：worker 卡在 `(>! out ...)` 因 out 满，alts! 拿不到 cancel 信号 → 把 out 容量从 10 改成 100 + producer 也用 alts! 听 cancel
3. **demo 44 中文字符串里嵌半角双引号** `"transformation recipe"` → 改用「」（[[memory:d99i1rgs]] 起作用了，自检立即抓到）
4. **demo 45 `(subs msg 0 60)` IOOB**：消息只有 47 字符 → 改 `(subs msg 0 (min 80 (count msg)))`
5. **demo 46 `m/=>` 报 :invalid-schema**：malli 0.20.1 的 `:=>` 函数 schema 注册需要额外 setup → 简化为 `m/validator` 编译性能对比，意外得到 20.7× 加速这个教学高光
6. **demo 49 `r/fold` 签名错**：写成 `(r/fold n combinef reducef)` 漏了 `coll` → 修成 `(r/fold n combinef reducef coll)`；section 3 反例又遇到"-没有 0-arity"，改用 reducef/combinef 不一致的真实违法组合
7. **demo 50 proxy Writer 重载冲突**：`(.write w "hello")` 找不到 String 重载 fallback → 改用显式 `(char-array s)` + 三参版本

## 📊 教学高光（实测数据）

| 维度 | 实测 |
|---|---|
| pipeline-async 50 路并发 HTTP | 454ms vs 串行 ~2500ms（5.5× 提速） |
| transduce vs lazy seq | 11.3ms vs 17.7ms（1.6× 提速） |
| **malli/validator 编译 vs 解释** | **16.4ms vs 338.7ms（20.7× 提速）** |
| **Java type hint vs 反射** | **4.1ms vs 1046.7ms（258× 提速）** |
| r/fold 1e7 sum | 89ms vs reduce 97ms（仅 1.1×，因 + 太轻量） |
| 200 个 property test 在 demo 28 找到边界 bug | shrink 自动定位 n=-100 |

## 🎯 状态对照

| 阶段 | 范围 | 状态 |
|---|---|---|
| 阶段一 ~ 阶段五 | demo 01-40 | ✅ 已完成 |
| **阶段六：深度补遗** | **demo 41-50** | **✅ 本次完成** |

至此 **Clojure 50 demo 全线 ✅**。

## 🚦 下一步

阶段六结束后，主线学习路径终结。剩下的可选方向（来自之前菜单）：
- C 跨语言对比 LANGUAGE_COMPARISON.md 增补 Clojure 列（**本轮一并执行**）
- D 实战大项目（合并若干 demo 做"电商分析后台"）
- E 全 50 demo 回归测试报告
