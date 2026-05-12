# Demo 24 — Malli：Schema-as-Data

第三个**项目档子目录**（依赖：`metosin/malli`）。

## 运行

```bash
cd clojure/24_malli_schema
clojure -M:run
```

## 内容

- schema 即数据：可序列化、可传输、可动态构造
- `validate` / `explain` / `me/humanize`：验证 + 人类可读诊断
- `decode` + transformer：JSON/string → 强类型（spec 没有）
- `mg/sample`：开箱即用的随机数据生成
- `{:closed true}` map：默认拒绝多余 key
- `:=>` 函数 schema + `malli.dev` instrument

## Malli vs Spec

| 维度 | spec.alpha | malli |
|---|---|---|
| schema 形态 | 函数调用（运行时即执行） | 普通 vector/map（运行时由 m/validate 解析） |
| 序列化 | 不能 | 可（schema 就是 EDN） |
| 动态构造 | 难 | 容易（拼 vector 即可） |
| 类型转换 | 无（只有验证） | `decode` + transformer 一站式 |
| 严格 map | 默认宽松 | `{:closed true}` 直接搞定 |
| 错误提示 | `s/explain` 输出文本 | `me/humanize` 返回结构化中文/英文 |
| 函数边界 | `s/fdef` + `instrument` | `:=>` schema + `malli.dev` |
| 生态 | 官方但更新慢 | 社区活跃，Reitit/Reagent 默认推 |
