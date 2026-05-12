# Demo 34 — Metabase-Style MBQL Pipeline

依赖：`datascript/datascript`。

把"查询的形状"写成数据，编译成 Datalog 执行。这就是 Metabase / Looker 的 Question Builder 内核思想。

## 运行

```bash
cd clojure/34_metabase_style_pipeline
clojure -M:run
```

## MBQL 描述形态

```clojure
{:source-table :order-line                      ;; 起点
 :fields       [:line/qty :product/name :user/region]
 :filter       [:and [:>= :line/qty 2]
                     [:= :user/region "US"]]
 :aggregations [[:sum :subtotal]]               ;; 计算列也支持
 :breakout     [:user/region]                   ;; group by
 :order-by     [[:subtotal :desc]]
 :limit        10}
```

## 编译器干了什么

```
mbql 数据
   ↓ compile-mbql
{:find  [?u-region (sum ?subtotal)]
 :where [[?l :line/qty _]
         [?o :order/lines ?l]
         [?o :order/buyer ?u]
         [?u :user/region ?u-region]
         [?l :line/qty ?line-qty]
         [?l :line/product ?p]
         [?p :product/price ?p-price]
         [(* ?line-qty ?p-price) ?subtotal]]
 :with  [?l]}
   ↓ d/q
[[EU 50] [US 27]]
   ↓ post-process (sort + limit)
[[EU 50] [US 27]]
```

## 4 个 case

| Case | 演示 |
|---|---|
| 1 | 多列 select |
| 2 | `:and` filter + 多种比较运算符 |
| 3 | 计算列 `:subtotal = qty * price` + groupby + sort |
| 4 | 直接对 attribute 聚合 `[:sum :line/qty]` + limit |

## 为什么这是 Metabase 内核

- **查询=数据**：可序列化、可存数据库、可前端 GUI 拼接（Question Builder 就是把 MBQL 一字段一字段拼出来）
- **多 dialect**：同一份 MBQL 编译目标可以是 Datalog / SQL（Postgres/MySQL/BQ） / Mongo aggregation pipeline
- **Clojure 写编译器特别舒服**：模式匹配 + 数据结构变换。本 demo 100 行就把核心讲清楚

## 局限（坦白）

- 锚点写死 `:order-line`（真 MBQL 是 graph-based join planner）
- `:or` 简化处理，只支持 `:= a v` 列表
- 没做 schema 校验（真 MBQL 用 malli/spec）
- 后处理 sort 仅按单列、`order-by` 取第一项

这些都是**有意省略**——目标是讲清楚 MBQL 心智，不是写 Metabase 替代品。
