;; ============================================================
;; Clojure Demo 04 — 解构（Destructuring）
;; ============================================================
;; 目标：覆盖 Clojure 解构的 4 种主要形态
;;   (1) vector 解构：[a b & rest :as all]
;;   (2) map 解构：{:keys [...] :or {...} :as m}
;;   (3) 嵌套解构
;;   (4) 函数参数里解构（最高频场景）
;;
;; 运行：clojure -M clojure/04_destructuring.clj
;; ============================================================

(println "=== 1. vector 解构 ===")
(let [[a b c] [10 20 30]]
  (println "a=" a "  b=" b "  c=" c))

(let [[x & rest] [1 2 3 4 5]]
  (println "x=" x "  rest=" rest "  (rest 是 seq)"))

(let [[a _ c] [100 200 300]]
  (println "_ 丢弃中间值 → a=" a "  c=" c))

(let [[a [b c] d :as all] [1 [2 3] 4]]
  (println "嵌套 vector + :as 绑定整体 →"
           "a=" a "  (b,c)=(" b "," c ")  d=" d "  all=" all))

(println "\n=== 2. map 解构：:keys / :or / :as ===")
(def user {:name "Ada" :age 36 :city "London" :role "hacker"})

(let [{:keys [name age]} user]
  (println ":keys 批量提取 → name=" name "  age=" age))

(let [{name :name occupation :role} user]
  (println "重命名绑定 → name=" name "  occupation=" occupation))

(let [{:keys [name country] :or {country "UK"}} user]
  (println ":or 默认值 → name=" name "  country=" country "（user 里没 :country）"))

(let [{:keys [name] :as whole} user]
  (println ":as 保留整体 → name=" name "  whole=" whole))

(println "\n=== 3. 嵌套解构 ===")
(def order
  {:id 42
   :customer {:name "Bob" :vip? true}
   :items [{:sku "A1" :qty 2}
           {:sku "B2" :qty 5}]})

(let [{:keys [id]
       {cust-name :name vip? :vip?} :customer
       [first-item & _] :items} order]
  (println "订单" id "  客户=" cust-name "  VIP?" vip? "  首件=" first-item))

(println "\n=== 4. 函数参数里解构：最实用的场景 ===")
(defn greet [{:keys [name age] :or {age "unknown"}}]
  (println (format "Hello %s, age=%s" name age)))

(greet {:name "Ada" :age 36})
(greet {:name "Mystery"})

;; 结合 & rest 拿"可变参数"
(defn log-call [fn-name & args]
  (println (format "调用 %s，参数共 %d 个：%s"
                   fn-name (count args) (vec args))))

(log-call "add" 1 2 3)
(log-call "greet" "Ada")

;; 函数参数里用 vector 解构：惯用在 reduce/swap! 的 callback
(defn sum-pairs [pairs]
  (reduce (fn [acc [k v]]     ;; 这里的 [k v] 就是解构
            (assoc acc k (+ (get acc k 0) v)))
          {}
          pairs))

(println "sum-pairs →"
         (sum-pairs [[:a 1] [:b 2] [:a 10] [:b 3] [:c 5]]))

(println "\n=== 5. 一句话总结 ===")
(println "- 解构是 Clojure 的\"官方 API 表达手段\"：每个函数签名就自带文档")
(println "- {:keys [...]} 几乎永远对：同时是参数声明 + 使用清单")
(println "- 嵌套解构最多两层，超过两层说明数据结构该拍平了")
