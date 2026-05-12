# Demo 33 — Datalog 查询的 7 种模式

依赖：`datascript/datascript`（沿用 demo 32 的内核）。

## 运行

```bash
cd clojure/33_datalog_query
clojure -M:run
```

## 7 种模式

| # | 模式 | 演示 |
|---|---|---|
| 1 | 基础三元组 | 列出所有商品名 |
| 2 | JOIN（同名变量） | 订单 → 买家姓名 |
| 3 | 谓词 + 去重 | qty ≥ 5 的「贵客」 |
| 4 | 聚合 + `:with` | 每个用户的订单总金额（sum qty*price） |
| 5 | 规则递归 | reports-to 传递闭包（Ada → Bob → Cy） |
| 6 | 参数化 `:in` | 查询即函数 `(buyers-of-product db "P-APPLE")` |
| 7 | `pull` 嵌套 | 从 order 根开始，嵌套 buyer + lines + product |

## 关键概念

- **数据模型**：电商订单（user / product / order / order-line / manager）
- **`:with`**：聚合时保留行身份，防止 `[Ada 100] [Ada 100]` 被去重合并成一行
- **规则 `%`** 是数据：作为查询的额外参数传入，可递归。SQL 里要写 CTE+UNION ALL 才能搞定的传递闭包，Datalog 5 行：
  ```clojure
  [(reports-to ?e ?b) [?e :user/manager ?b]]
  [(reports-to ?e ?b) [?e :user/manager ?m] (reports-to ?m ?b)]
  ```
- **q vs pull**：q 是"模式匹配 + join"，pull 是"从 entity 出发的 GraphQL"。互补。

下个 demo 34 把这些查询的"形状"参数化成 MBQL-style 数据描述。
