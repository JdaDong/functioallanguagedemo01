# Demo 32 — DataScript：纯内存 Datomic

第一个数据档（依赖：`datascript/datascript`）。

## 运行

```bash
cd clojure/32_datomic_mini
clojure -M:run
```

## 内容（4 个 section）

1. **基础 transact + 查询**：schema / lookup ref / `d/q` Datalog
2. **时间旅行**：t0..t3 四个 db-value 同时存活，结构共享
3. **db-with 假设性事务**：what-if 试算涨薪，conn 不变
4. **ref + cardinality/many**：关系建模 + 反向 pull `_author`

## 关键概念

> **数据库就是值，不是位置**（Database as a Value）

- conn 是一个 `atom-of-db`，`transact!` 把新 db 换进去
- 但**旧 db-value 还活着**：可以查、可以对比、可以 diff
- `db-with` 进一步：连 conn 都不动，纯函数式探索新分支
- 这就是 Rich Hickey "Value of Values" 那场 talk 的工业实现

下个 demo 31 在这个 db 上写更深入的 Datalog 查询。
