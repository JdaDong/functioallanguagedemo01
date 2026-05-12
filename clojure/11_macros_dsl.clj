;; ============================================================
;; Clojure Demo 11 — Macros DSL：用宏构造 SQL 查询语言
;; ============================================================
;; 目标：演示"代码即数据"如何让 DSL 几乎免费。
;; 一段输入：
;;     (sql {:select [:id :name :age]
;;           :from   :users
;;           :where  [:and [:> :age 18] [:= :status "active"]]
;;           :order  [:age :desc]
;;           :limit  10})
;; 一段输出（编译期生成的字符串）：
;;     "SELECT id, name, age FROM users WHERE (age > 18) AND (status = 'active')
;;      ORDER BY age DESC LIMIT 10"
;;
;; 关键点：
;;   - 输入是普通 Clojure 数据（map + vector + keyword）
;;   - 宏在编译期把它转成字符串
;;   - 因为输入是数据，可以从配置文件 / 数据库元数据 / 用户输入构造
;;   - 这就是 HoneySQL / Korma 这类库的核心思想
;;
;; 运行：clojure -M clojure/11_macros_dsl.clj
;; ============================================================

(declare emit-where)

(defn quote-val [v]
  (cond
    (string? v)  (str "'" v "'")              ;; 真实场景必须 escape，本 demo 简化
    (keyword? v) (name v)                     ;; :age → "age"
    :else        (str v)))

(defn emit-where [w]
  (let [[op & args] w]
    (case op
      :and (str "(" (clojure.string/join " AND " (map emit-where args)) ")")
      :or  (str "(" (clojure.string/join " OR "  (map emit-where args)) ")")
      ;; 比较运算：[:= :age 18] → "(age = 18)"
      (str "(" (quote-val (first args))
           " " (name op)
           " " (quote-val (second args)) ")"))))

(defn emit-sql [{:keys [select from where order limit]}]
  (cond-> []
    select (conj (str "SELECT " (clojure.string/join ", " (map name select))))
    from   (conj (str "FROM "   (name from)))
    where  (conj (str "WHERE "  (emit-where where)))
    order  (conj (str "ORDER BY "
                      (name (first order))
                      (when (= (second order) :desc) " DESC")))
    limit  (conj (str "LIMIT " limit))
    true   (->> (clojure.string/join " "))))

;; 真正的"宏"在这里：编译期就把 map 转成字符串
(defmacro sql [query-map]
  ;; query-map 是字面 map（编译期可见），直接 emit
  ;; 如果用户传变量则不行，需要把 emit 推迟到运行期 → 见 sql-fn
  (emit-sql query-map))

(defn sql-fn [query-map]                     ;; 同样逻辑的运行期版本
  (emit-sql query-map))

(println "=== 1. 最简：select-from ===")
(println (sql {:select [:id :name]
               :from   :users}))

(println "\n=== 2. 加 where ===")
(println (sql {:select [:id :name :age]
               :from   :users
               :where  [:> :age 18]}))

(println "\n=== 3. 复合 where：and / or 嵌套 ===")
(println (sql {:select [:*]
               :from   :products
               :where  [:and
                        [:> :price 100]
                        [:or
                         [:= :category "books"]
                         [:= :category "music"]]]}))

(println "\n=== 4. order + limit ===")
(println (sql {:select [:name :score]
               :from   :leaderboard
               :order  [:score :desc]
               :limit  5}))

(println "\n=== 5. 完整查询 ===")
(println (sql {:select [:id :name :age]
               :from   :users
               :where  [:and [:> :age 18] [:= :status "active"]]
               :order  [:age :desc]
               :limit  10}))

(println "\n=== 6. 编译期 vs 运行期：区别在哪里？ ===")
;; 用宏：query 在编译期就被翻译成字符串字面量
(println "宏版本（编译期固化）：" (sql {:select [:a] :from :t}))
;; 用函数：query 在运行期才翻译——可以传变量、动态拼接
(let [dynamic-query {:select [:a :b]
                     :from   :t
                     :where  [:= :id 42]}]
  (println "函数版本（运行期）：" (sql-fn dynamic-query)))

(println "\n=== 7. 看宏展开成什么 ===")
(println "(sql {...}) 展开为字符串字面量：")
(println " " (macroexpand-1 '(sql {:select [:a] :from :t})))

(println "\n=== 一句话总结 ===")
(println "- 数据驱动 DSL：输入是普通 map/vector，谁都会写")
(println "- 宏 vs 函数：")
(println "    宏  → 编译期固化，零运行时开销，但 query 必须是字面量")
(println "    函数→ 运行期翻译，可以接受变量构造的 query")
(println "- HoneySQL / Korma 都是这个套路放大版（外加 SQL injection 防护）")
