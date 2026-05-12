# Demo 23 — spec + test.check：Property-Based Testing

第二个**项目档子目录**（依赖：`org.clojure/test.check`）。

## 运行

```bash
cd clojure/23_spec_generators
clojure -M:run
```

## 内容

- `s/exercise`：从 spec 自动派生数据
- `s/with-gen`：给约束式 spec（如正则）配自定义 generator
- `tc/quick-check` + `prop/for-all`：核心 property test API
- **shrink** 自动定位最小反例（QuickCheck 灵魂）
- `stest/check`：对带 `s/fdef` 的函数自动 property test
- 实战：用 property test 在 200 个样本里捕获边界 bug

## 关键概念

**"property test 而非 example test"**：传统 unit test 写若干"输入→预期输出"的样例，property test 写一条普适命题（比如"reverse∘reverse = id"），由 generator 喂随机数据。

**shrink** 是 property-based testing 的杀手锏：发现反例后**自动缩小**到最小可复现形式，让 bug 定位从"偶发的大输入"变成"最简的小输入"。
