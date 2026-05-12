(ns ecom.analytics.mbql
  "MBQL → DataScript Datalog 编译器（β 形态）。

   支持的 MBQL：
   {:source :order
    :filter [:and
             [:= :order/status :delivered]
             [:between :order/placed-at start-ms end-ms]
             [:in :item/sku [\"SKU-A\" \"SKU-B\"]]]
    :group-by [:item/sku]
    :aggregate [[:sum :item/qty]
                [:sum [:* :item/qty :item/unit-price]]
                [:count :order/id]]
    :order-by [[:desc 1]]   ; 按聚合输出第 1 列
    :limit 10}

   设计：
   - 编译为 datascript 的 :find / :where / :in 三段，运行结果再 in-memory
     做 group/agg/sort/limit（DataScript 0.x 不支持 group-by 内建）。

   产出：(run-mbql db mbql-map) → [[group-vals... agg-vals...] ...]"
  (:require [datascript.core :as d]
            [clojure.string :as str]))

;; ─── filter 编译 ───────────────────────────────────────────────────────

(defmulti compile-filter (fn [f] (when (vector? f) (first f))))

(defmethod compile-filter :and [[_ & fs]]
  (vec (mapcat compile-filter fs)))

(defmethod compile-filter := [[_ attr v]]
  [[(symbol "?o") attr v]])

(defmethod compile-filter :between [[_ attr lo hi]]
  [[(symbol "?o") attr (symbol "?_v")]
   [(list '<= lo (symbol "?_v") hi)]])

(defmethod compile-filter :in [[_ attr vs]]
  ;; 对 :item/sku 这类，需要走 item 实体
  [[(symbol "?o") :order/items (symbol "?i")]
   [(symbol "?i") attr (symbol "?sku-v")]
   [(list 'contains? (set vs) (symbol "?sku-v"))]])

(defmethod compile-filter :default [_] [])

;; ─── 拉数据 ────────────────────────────────────────────────────────────
;; 策略：先用 filter 筛 order 实体集合，再对每个 order 抽出需要的字段
;; （包括 items 展开），形成 in-memory rows 给 group/agg 用。

(defn- filter->order-eids [db filter-spec]
  (let [where (compile-filter filter-spec)
        q     (concat '[:find [?o ...]
                        :where [?o :order/id _]]
                      where)]
    (d/q (vec q) db)))

(defn- order->rows
  "把一个 order 拉成行（每个 item 一行，包含订单字段 + item 字段）"
  [db eid]
  (let [o (d/pull db '[:order/id :order/user :order/status :order/total
                       :order/discount :order/coupon :order/placed-at
                       :order/paid-at :order/shipped-at :order/delivered-at
                       {:order/items [:item/sku :item/qty :item/unit-price]}
                       {:order/user [:user/id]}] eid)
        items (or (:order/items o) [{}])
        flat-base (-> o
                      (dissoc :order/items :order/user)
                      (assoc :user/id (get-in o [:order/user :user/id])))]
    (map #(merge flat-base %) items)))

(defn- collect-rows [db filter-spec]
  (mapcat #(order->rows db %)
          (filter->order-eids db filter-spec)))

;; ─── group + aggregate ─────────────────────────────────────────────────

(defn- agg-fn [op]
  (case op
    :sum   (fn [xs] (reduce + 0 xs))
    :count (fn [xs] (count xs))
    :avg   (fn [xs] (if (seq xs) (/ (reduce + 0.0 xs) (count xs)) 0))
    :min   (fn [xs] (when (seq xs) (apply min xs)))
    :max   (fn [xs] (when (seq xs) (apply max xs)))))

(defn- expr-val [row expr]
  (cond
    (keyword? expr) (get row expr)
    (and (vector? expr) (= :* (first expr)))
    (apply * (map #(expr-val row %) (rest expr)))
    (and (vector? expr) (= :- (first expr)))
    (apply - (map #(expr-val row %) (rest expr)))
    :else expr))

(defn- agg-spec->fn
  "[:sum :item/qty] 或 [:sum [:* :item/qty :item/unit-price]] → (fn [rows] number)"
  [[op expr]]
  (let [f (agg-fn op)]
    (fn [rows]
      (if (= op :count)
        (f rows)
        (f (map #(expr-val % expr) rows))))))

(defn- group-rows [rows group-keys]
  (if (empty? group-keys)
    {[] rows}
    (group-by (apply juxt group-keys) rows)))

(defn run-mbql
  "执行 MBQL，返回向量化结果（每行 = group-vals + agg-vals 拼接）"
  [db {:keys [filter group-by aggregate order-by limit]
       :or {filter [:and] group-by [] aggregate [] order-by []}}]
  (let [rows (collect-rows db filter)
        grouped (group-rows rows group-by)
        agg-fns (mapv agg-spec->fn aggregate)
        result (for [[gvals subs] grouped]
                 (vec (concat gvals
                              (mapv (fn [f] (f subs)) agg-fns))))
        sorted (reduce
                 (fn [acc [dir col]]
                   (let [cmp (if (= dir :desc) #(compare %2 %1) compare)]
                     (sort-by #(nth % col) cmp acc)))
                 result
                 (reverse order-by))]
    (cond->> sorted
      limit (take limit)
      true  vec)))

;; ─── 友好包装：常用查询模板 ───────────────────────────────────────────

(defn sales-by-sku
  "各 SKU 的销售额 + 销量 + 订单数"
  [db {:keys [from-ms to-ms statuses]
       :or   {statuses [:delivered :shipped :paid]}}]
  (run-mbql db
    {:filter [:and
              [:between :order/placed-at (or from-ms 0) (or to-ms Long/MAX_VALUE)]]
     :group-by [:item/sku]
     :aggregate [[:sum :item/qty]
                 [:sum [:* :item/qty :item/unit-price]]
                 [:count :order/id]]
     :order-by [[:desc 2]]}))   ;; 按销售额降序

(defn top-users
  "用户消费 Top N（按订单总额）"
  [db {:keys [from-ms to-ms n] :or {n 5}}]
  (run-mbql db
    {:filter [:and
              [:between :order/placed-at (or from-ms 0) (or to-ms Long/MAX_VALUE)]]
     :group-by [:user/id]
     :aggregate [[:count :order/id]
                 [:sum :order/total]]
     :order-by [[:desc 2]]
     :limit n}))
