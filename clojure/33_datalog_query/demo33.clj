(ns demo33
  "Datalog 查询的七种核心模式（在 DataScript 上演练）。
   建一个'电商订单'微型库（用户 / 商品 / 订单 / 订单行），
   依次演示：基础查询 / join / 谓词 / 聚合 / 规则递归 / 参数化 / pull 嵌套。

   参考：https://docs.datomic.com/query/query-data-reference.html
   运行：clojure -M:run"
  (:require [datascript.core :as d]))

;; ───────────────────────────────────────────────────────────────────────
;; 数据建模：user / product / order / order-line
;; ───────────────────────────────────────────────────────────────────────
(def schema
  {:user/email     {:db/unique :db.unique/identity}
   :product/sku    {:db/unique :db.unique/identity}
   :order/buyer    {:db/valueType :db.type/ref}
   :order/lines    {:db/valueType :db.type/ref :db/cardinality :db.cardinality/many}
   :line/product   {:db/valueType :db.type/ref}
   :user/manager   {:db/valueType :db.type/ref}})        ;; 用于规则递归

(defn seed-db []
  (let [conn (d/create-conn schema)]
    (d/transact! conn
      [;; 用户：Cy 是 Bob 的下属，Bob 是 Ada 的下属
       {:db/id -1 :user/email "ada@x.com" :user/name "Ada"}
       {:db/id -2 :user/email "bob@x.com" :user/name "Bob" :user/manager -1}
       {:db/id -3 :user/email "cy@x.com"  :user/name "Cy"  :user/manager -2}

       ;; 商品
       {:db/id -10 :product/sku "P-APPLE"  :product/name "apple"  :product/price 5}
       {:db/id -11 :product/sku "P-BANANA" :product/name "banana" :product/price 3}
       {:db/id -12 :product/sku "P-CHERRY" :product/name "cherry" :product/price 8}

       ;; 订单：Ada 买 [apple x2, banana x3]
       {:db/id -100
        :order/no "O-001"
        :order/buyer -1
        :order/lines [{:db/id -101 :line/product -10 :line/qty 2}
                      {:db/id -102 :line/product -11 :line/qty 3}]}
       ;; Bob 买 [cherry x1]
       {:db/id -200
        :order/no "O-002"
        :order/buyer -2
        :order/lines [{:db/id -201 :line/product -12 :line/qty 1}]}
       ;; Cy 买 [apple x10]（大客户）
       {:db/id -300
        :order/no "O-003"
        :order/buyer -3
        :order/lines [{:db/id -301 :line/product -10 :line/qty 10}]}])
    conn))

;; ───────────────────────────────────────────────────────────────────────
;; pattern 1: 基础三元组查询
;; ───────────────────────────────────────────────────────────────────────
(defn p1-basic [db]
  (println "── 1. 基础查询：所有商品名 ──")
  (println "  ans:" (sort (d/q '[:find [?n ...]
                                  :where [_ :product/name ?n]] db))))

;; ───────────────────────────────────────────────────────────────────────
;; pattern 2: join —— 同一变量出现在多个 clause
;; ───────────────────────────────────────────────────────────────────────
(defn p2-join [db]
  (println "\n── 2. JOIN：每张订单的买家姓名 + 订单号 ──")
  (doseq [[ono name] (sort (d/q '[:find ?ono ?name
                                  :where
                                  [?o :order/no ?ono]
                                  [?o :order/buyer ?u]
                                  [?u :user/name ?name]] db))]
    (println "  " ono "←" name)))

;; ───────────────────────────────────────────────────────────────────────
;; pattern 3: 谓词 + or + not
;; ───────────────────────────────────────────────────────────────────────
(defn p3-predicate [db]
  (println "\n── 3. 谓词 + not：找出订单行 qty >= 5 的「贵客」（按用户去重）──")
  (let [vips (d/q '[:find [?name ...]
                    :where
                    [?o :order/buyer ?u]
                    [?u :user/name ?name]
                    [?o :order/lines ?l]
                    [?l :line/qty ?q]
                    [(>= ?q 5)]] db)]
    (println "  ans:" (sort (distinct vips)))))

;; ───────────────────────────────────────────────────────────────────────
;; pattern 4: 聚合（sum/count/max）
;; ───────────────────────────────────────────────────────────────────────
(defn p4-aggregate [db]
  (println "\n── 4. 聚合：每个用户的订单总金额 ──")
  ;; sum( qty * price )，按用户分组
  (doseq [[name total]
          (sort-by second >
            (d/q '[:find ?name (sum ?subtotal)
                   :with ?l                      ;; :with 防止行级被去重合并
                   :where
                   [?o :order/buyer ?u]
                   [?u :user/name ?name]
                   [?o :order/lines ?l]
                   [?l :line/qty ?q]
                   [?l :line/product ?p]
                   [?p :product/price ?price]
                   [(* ?q ?price) ?subtotal]] db))]
    (println "  " name "→" total)))

;; ───────────────────────────────────────────────────────────────────────
;; pattern 5: 规则 + 递归（who reports to Ada，传递闭包）
;; ───────────────────────────────────────────────────────────────────────
(def reports-rule
  '[;; 直属下级
    [(reports-to ?employee ?boss)
     [?employee :user/manager ?boss]]
    ;; 传递：?employee → ?mid → ?boss
    [(reports-to ?employee ?boss)
     [?employee :user/manager ?mid]
     (reports-to ?mid ?boss)]])

(defn p5-rule [db]
  (println "\n── 5. 规则递归：所有（直接+间接）汇报给 Ada 的人 ──")
  (let [ans (d/q '[:find [?name ...]
                   :in $ % ?boss-email
                   :where
                   [?boss :user/email ?boss-email]
                   (reports-to ?e ?boss)
                   [?e :user/name ?name]]
                 db reports-rule "ada@x.com")]
    (println "  ans:" (sort ans) "  （Bob 直接，Cy 间接，传递闭包都拿到）")))

;; ───────────────────────────────────────────────────────────────────────
;; pattern 6: 参数化 :in —— 把查询当成函数
;; ───────────────────────────────────────────────────────────────────────
(defn buyers-of-product [db sku]
  (d/q '[:find [?name ...]
         :in $ ?sku
         :where
         [?p :product/sku ?sku]
         [?l :line/product ?p]
         [?o :order/lines ?l]
         [?o :order/buyer ?u]
         [?u :user/name ?name]]
       db sku))

(defn p6-param [db]
  (println "\n── 6. 参数化查询（查询即函数）──")
  (println "  apple 的买家："  (sort (distinct (buyers-of-product db "P-APPLE"))))
  (println "  cherry 的买家：" (sort (distinct (buyers-of-product db "P-CHERRY")))))

;; ───────────────────────────────────────────────────────────────────────
;; pattern 7: pull —— 从单个 entity 出发的"嵌套取数"
;; ───────────────────────────────────────────────────────────────────────
(defn p7-pull [db]
  (println "\n── 7. PULL：以 Ada 的订单为根，嵌套取数 ──")
  (let [ada-orders (d/q '[:find [?o ...]
                          :where
                          [?u :user/email "ada@x.com"]
                          [?o :order/buyer ?u]] db)]
    (doseq [oid ada-orders]
      (println "  "
        (d/pull db
                '[:order/no
                  {:order/buyer [:user/name]}
                  {:order/lines
                   [:line/qty
                    {:line/product [:product/name :product/price]}]}]
                oid)))))

(defn -main [& _]
  (let [db @(seed-db)]
    (p1-basic db)
    (p2-join db)
    (p3-predicate db)
    (p4-aggregate db)
    (p5-rule db)
    (p6-param db)
    (p7-pull db))
  (println "\n=== 一句话总结 ===")
  (println "- Datalog = 三元组模式匹配 + 同名变量 join + 谓词 + 聚合 + 规则递归")
  (println "- :find [?x ...] 单列扁平 / :find ?x .  单值 / :find ?x ?y  关系")
  (println "- :with 用于'防去重'（聚合时保留行身份）")
  (println "- 规则 % 是数据，可传参，可递归 → SQL 难写的传递闭包，Datalog 5 行搞定")
  (println "- pull 是'从一个 entity 出发的 GraphQL'，和 q 互补"))
