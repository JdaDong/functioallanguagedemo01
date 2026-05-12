# Demo 28 — Schema 演进策略

第五个**项目档子目录**（依赖：`org.clojure/test.check`；spec.alpha 是标准库）。

## 运行

```bash
cd clojure/28_schema_evolution
clojure -M:run
```

## 内容

- v1 / v2 两套 spec 共存（用 namespaced kw 隔离：`:v1.user/...` vs `:v2.user/...`）
- `migrate-v1->v2` + `downgrade-v2->v1`：双向迁移
- 三条 property test 命题：
  1. 任何合法 v1 → migrate → 合法 v2
  2. 关键不变量（id / created-at）永不丢失
  3. 部分 v1 可双向往返（migrate∘downgrade = id）
- `gen/such-that` 收紧 generator，应对"spec 太宽松"导致的伪反例

## 演进策略要点

1. **每条数据带 `:schema-version`**——v2 起强制；读端看到无版本号的当 v1
2. **写显式 `migrate-vN->vN+1` 函数**——单向，不要自动推导
3. **三类 property** 覆盖：合法 / 不变量 / 可逆
4. **generator 病态数据**用 `such-that` 收紧或在 spec 加约束

## 相关 demo

- demo 22 / 23：spec 基础和 property test 起步
- demo 24：malli（更现代的 schema 库，演进同理）
