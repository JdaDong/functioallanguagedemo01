(ns demo34
  "MBQL-style pipeline：把'查询的形状'写成数据，编译成 Datalog 执行。
   Metabase MBQL 的精神：用户描述 *what*（哪张表、聚合什么、过滤什么、按什么分组），
   不写 *how*（不写 SQL/Datalog）。前端 GUI 拼出 MBQL，后端编译执行。

   本 demo 实现一个最小但完整的 MBQL→Datalog 编译器。

   运行：clojure -M:run"
  (:require [datascript.core :as d]))

;; ───────────────────────────────────────────────────────────────────────
;; 复用 demo 33 的电商数据（用户 / 商品 / 订单 / 订单行）
;; ───────────────────────────────────────────────────────────────────────
(def schema
  {:user/email     {:db/unique :db.unique/identity}
   :product/sku    {:db/unique :db.unique/identity}
   :order/buyer    {:db/valueType :db.type/ref}
   :order/lines    {:db/valueType :db.type/ref :db/cardinality :db.cardinality/many}
   :line/product   {:db/valueType :db.type/ref}})

(defn seed []
  (let [conn (d/create-conn schema)]
    (d/transact! conn
      [{:db/id -1 :user/email "ada@x.com" :user/name "Ada" :user/region "US"}
       {:db/id -2 :user/email "bob@x.com" :user/name "Bob" :user/region "US"}
       {:db/id -3 :user/email "cy@x.com"  :user/name "Cy"  :user/region "EU"}
       {:db/id -10 :product/sku "P-A" :product/name "apple"  :product/price 5}
       {:db/id -11 :product/sku "P-B" :product/name "banana" :product/price 3}
       {:db/id -12 :product/sku "P-C" :product/name "cherry" :product/price 8}
       {:db/id -100 :order/no "O-001" :order/buyer -1
        :order/lines [{:db/id -101 :line/product -10 :line/qty 2}
                      {:db/id -102 :line/product -11 :line/qty 3}]}
       {:db/id -200 :order/no "O-002" :order/buyer -2
        :order/lines [{:db/id -201 :line/product -12 :line/qty 1}]}
       {:db/id -300 :order/no "O-003" :order/buyer -3
        :order/lines [{:db/id -301 :line/product -10 :line/qty 10}]}])
    @conn))

;; ───────────────────────────────────────────────────────────────────────
;; MBQL 描述形如：
;; {:source-table :order-line                       ;; 起点
;;  :fields       [:line/qty :product/name :user/region]
;;  :filter       [:and [:>= :line/qty 2] [:= :user/region "US"]]
;;  :aggregations [[:sum :subtotal]]                ;; 计算列名 → 聚合
;;  :breakout     [:user/region]                    ;; group by
;;  :order-by     [[:subtotal :desc]]
;;  :limit        10}
;;
;; 我们把它编译成 Datalog 的 :find / :where / 后处理 (sort + take)
;; ───────────────────────────────────────────────────────────────────────

;; "schema-of-shapes"：从 :line 出发能 join 到的 attribute → where 子句模板
;; 这是最朴素版（全部以 :line 为锚点），真 MBQL 是 graph-based join planner
(def joins-from-line
  ;; attribute → 编译时插入的 where 子句（用 ?l 表示当前订单行）
  {:line/qty       '[[?l :line/qty ?line-qty]]
   :line/product   '[[?l :line/product ?p]]
   :product/name   '[[?l :line/product ?p]   [?p :product/name ?p-name]]
   :product/price  '[[?l :line/product ?p]   [?p :product/price ?p-price]]
   :user/name      '[[?o :order/lines ?l]   [?o :order/buyer ?u] [?u :user/name ?u-name]]
   :user/region    '[[?o :order/lines ?l]   [?o :order/buyer ?u] [?u :user/region ?u-region]]
   :order/no       '[[?o :order/lines ?l]   [?o :order/no ?o-no]]})

;; attribute → 它绑出的 var（用于 :find / 谓词 / 聚合）
(def attr->var
  {:line/qty      '?line-qty
   :product/name  '?p-name
   :product/price '?p-price
   :user/name     '?u-name
   :user/region   '?u-region
   :order/no      '?o-no})

;; 计算列：name → (vars -> datalog-clause)
(def computed
  {:subtotal {:vars   '[?line-qty ?p-price]
              :clause '[(* ?line-qty ?p-price) ?subtotal]
              :var    '?subtotal
              ;; 它依赖 :line/qty 和 :product/price，编译器需要确保两者已 join
              :deps   [:line/qty :product/price]}})

(defn ->var
  "field 关键字 / 计算列 → datalog 变量"
  [field]
  (or (attr->var field)
      (get-in computed [field :var])
      (throw (ex-info "unknown field" {:field field}))))

;; ───────────────────────────────────────────────────────────────────────
;; filter 编译：[:= a v] / [:>= a v] / [:and ...] / [:or ...]
;; ───────────────────────────────────────────────────────────────────────
(defn compile-filter [f]
  (let [op (first f)]
    (case op
      :and (mapcat compile-filter (rest f))
      :or  ;; or 在 Datalog 是 (or-clause)，简化处理：要求 :or 内仅含 :=
           [(cons 'or (for [sub (rest f)
                            :let [[_ a v] sub]]
                        ['_ '?l (->var a) v]))]
      ;; 比较类
      (let [[_ field v] f
            var (->var field)
            sym (case op := '= :>= '>= :<= '<= :> '> :< '< :not= 'not=)]
        [(list (list sym var v))]))))

;; ───────────────────────────────────────────────────────────────────────
;; 主编译器：mbql → {:query datalog-q, :post (fn [rows] rows)}
;; ───────────────────────────────────────────────────────────────────────
(defn compile-mbql [{:keys [source-table fields filter aggregations breakout
                            order-by limit]
                     :as mbql}]
  (assert (= source-table :order-line) "demo 只支持 :order-line 起点")

  ;; 1. 收集所有需要 join 的 attribute（直接出现的 + 计算列依赖的 + filter 中的 + 聚合直接命中的）
  (let [filter-form filter                       ;; 避免 shadow clojure.core/filter
        agg-cols   (map second aggregations)
        agg-deps   (mapcat #(or (get-in computed [% :deps])
                                (when (attr->var %) [%]))
                           agg-cols)
        filter-attrs (when filter-form
                       (->> (tree-seq sequential? rest filter-form)
                            (clojure.core/filter sequential?)
                            (keep #(when (and (= 3 (count %))
                                              (#{:= :>= :<= :> :< :not=} (first %)))
                                     (second %)))))
        all-attrs (distinct (concat fields breakout agg-deps filter-attrs))

        ;; 2. join 子句
        join-clauses (mapcat joins-from-line all-attrs)

        ;; 3. 计算列子句
        computed-clauses (for [agg aggregations
                               :let [col (second agg)
                                     spec (computed col)]
                               :when spec]
                           (:clause spec))

        ;; 4. filter 子句
        filter-clauses (when filter-form (compile-filter filter-form))

        where (vec (concat '[[?l :line/qty _]]   ;; 锚点：从所有订单行开始
                           join-clauses
                           computed-clauses
                           filter-clauses))

        ;; 5. :find —— breakout 列 + aggregation
        find-vars (concat (map ->var breakout)
                          (for [[op col] aggregations]
                            (list (symbol (name op)) (->var col))))
        ;; 没 breakout 没 agg 时，输出 fields
        find-vars (if (seq find-vars)
                    find-vars
                    (map ->var fields))

        ;; 6. :with —— 聚合时保留行身份
        with-clause (when (seq aggregations) '[?l])

        query (cond-> {:find (vec find-vars)
                       :where where}
                with-clause (assoc :with with-clause))

        ;; 7. 后处理：sort + limit
        post (fn [rows]
               (cond->> rows
                 order-by (sort-by
                           (fn [row]
                             (let [col      (-> order-by first first)
                                   dir      (-> order-by first second)
                                   col-idx  (.indexOf (vec (concat breakout
                                                                   (map second aggregations)))
                                                      col)
                                   v        (nth row col-idx)]
                               (if (= dir :desc) (- v) v))))
                 limit  (take limit)))]

    {:query query :post post}))

;; ───────────────────────────────────────────────────────────────────────
;; 执行器
;; ───────────────────────────────────────────────────────────────────────
(defn run-mbql [db mbql]
  (let [{:keys [query post]} (compile-mbql mbql)]
    {:datalog query
     :result  (vec (post (d/q query db)))}))

;; ───────────────────────────────────────────────────────────────────────
;; 三个 use-case
;; ───────────────────────────────────────────────────────────────────────
(defn -main [& _]
  (let [db (seed)]

    (println "── case 1: 列出所有订单行的（商品名, qty, 用户区域）──")
    (let [r (run-mbql db
              {:source-table :order-line
               :fields       [:product/name :line/qty :user/region]})]
      (println "  编译后 Datalog：" (:datalog r))
      (println "  结果：")
      (doseq [row (sort (:result r))] (println "   " row)))

    (println "\n── case 2: 仅美国地区，qty >= 2 ──")
    (let [r (run-mbql db
              {:source-table :order-line
               :fields       [:product/name :line/qty]
               :filter       [:and [:>= :line/qty 2] [:= :user/region "US"]]})]
      (println "  编译后 Datalog：" (:datalog r))
      (println "  结果：")
      (doseq [row (sort (:result r))] (println "   " row)))

    (println "\n── case 3: 按区域分组 sum subtotal，金额倒序 ──")
    (let [r (run-mbql db
              {:source-table :order-line
               :aggregations [[:sum :subtotal]]
               :breakout     [:user/region]
               :order-by     [[:subtotal :desc]]})]
      (println "  编译后 Datalog：" (:datalog r))
      (println "  结果（按金额倒序）：")
      (doseq [row (:result r)] (println "   " row)))

    (println "\n── case 4: 按商品分组 sum qty，前 2 名 ──")
    (let [r (run-mbql db
              {:source-table :order-line
               :aggregations [[:sum :line/qty]]   ;; 直接对 attribute 聚合
               :breakout     [:product/name]
               :order-by     [[:line/qty :desc]]
               :limit        2})]
      (println "  编译后 Datalog：" (:datalog r))
      (println "  结果：")
      (doseq [row (:result r)] (println "   " row))))

  (println "\n=== 一句话总结 ===")
  (println "- MBQL = 用数据描述查询形状（fields/filter/aggregations/breakout/order-by）")
  (println "- 编译器把 MBQL 转成 Datalog（或 SQL，对 Metabase 就是各种 dialect）")
  (println "- 因为查询是数据，所以可序列化、可可视化、可前端 GUI 拼接")
  (println "- 这就是 Metabase Question Builder / Looker 的内核思想")
  (println "- Clojure 写编译器特别舒服：模式匹配 + 数据结构变换"))
