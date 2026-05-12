;; demo 25 — Data-Oriented Programming（DOP）：用 map/vector 取代类
;; Clojure 社区的核心哲学，Rich Hickey 多次演讲（"The Value of Values" / "Maybe Not"）的主线。
;; 零依赖。
;; 运行：clojure -M clojure/25_data_oriented_programming.clj

(ns demo25)

(println "=== 1. 思维转变：'对象'被拆成三件 ===")
(println "OOP：class Order { state, behavior, identity 三合一 }")
(println "DOP：")
(println "  - 数据 ：纯 EDN（map/vector），无方法")
(println "  - 函数 ：独立模块，输入数据 → 输出数据")
(println "  - 身份 ：用 atom/ref 包住数据，需要时才有")
(println "→ 数据可序列化、可传输、可比较、不可变；函数可组合、可测试")

(println "\n=== 2. 例 A：OOP 写法 vs DOP 写法 ===")

;; ❌ OOP 思维（虚构语法）：
;; class Order {
;;   id, items, status
;;   addItem(item)  { ... }
;;   submit()       { ... }
;;   total()        { ... }
;; }
;; → 数据和行为绑定，序列化要写 toJSON，新行为要改 class

;; ✅ DOP 思维：
(defn make-order [id]
  {:id id :items [] :status :draft})

(defn add-item [order item]
  (update order :items conj item))

(defn submit [order]
  (assoc order :status :submitted))

(defn total [order]
  (->> order :items (map :price) (reduce + 0)))

(let [o  (make-order 1)
      o' (-> o
             (add-item {:sku "A" :price 30})
             (add-item {:sku "B" :price 50})
             submit)]
  (println "订单：" o')
  (println "金额：" (total o')))

(println "\n=== 3. 关键洞察：数据是值，函数是变换 ===")
(println "(-> data f1 f2 f3)  ←  这才是 Clojure 的'方法链'")
(println "每一步都是新值，原数据没变，可随时调试 / 回放 / 持久化")

(println "\n=== 4. 例 B：同一份数据，多种解读 ===")
;; 同一个 user map，根据上下文用不同的"视图函数"
(def user
  {:id 1 :name "Ada" :email "ada@x.com"
   :birthday "1815-12-10" :role :admin
   :addresses [{:type :home :city "London"}
               {:type :work :city "Cambridge"}]})

(defn public-view [u]
  (select-keys u [:id :name :role]))

(defn email-form [u]
  {:to (:email u) :greeting (str "Hi " (:name u))})

(defn home-city [u]
  (->> u :addresses (filter #(= :home (:type %))) first :city))

(println "public-view ：" (public-view user))
(println "email-form  ：" (email-form user))
(println "home-city   ：" (home-city user))
;; OOP 等价物：要写 3 个 getter / 3 个 DTO 类 / 3 套 mapper

(println "\n=== 5. 例 C：通用函数自由组合（OOP 的 Visitor 模式直接消失） ===")
;; 嵌套数据 + 通用 walk
(def org
  {:name "Acme"
   :depts [{:name "Eng"
            :people [{:name "Ada" :salary 100} {:name "Bob" :salary 90}]}
           {:name "Sales"
            :people [{:name "Cy" :salary 80}]}]})

;; 求所有人薪水合计——不需要 Visitor，update-in + map 组合即可
(defn total-payroll [org]
  (->> org :depts
       (mapcat :people)
       (map :salary)
       (reduce +)))

;; 给所有人涨 10%——同样用通用变换
(defn raise-everyone [org pct]
  (update org :depts
          (fn [ds]
            (mapv (fn [d]
                    (update d :people
                            (fn [ps]
                              (mapv #(update % :salary * (+ 1 pct)) ps))))
                  ds))))

(println "原 payroll  ：" (total-payroll org))
(println "涨 10% 后   ：" (total-payroll (raise-everyone org 0.10)))

(println "\n=== 6. 例 D：行为做成 multimethod，开放分派 ===")
;; OOP 用继承做多态；DOP 用一个"判别 key"做开放分派
(defmulti event-handler :type)

(defmethod event-handler :order/submitted [{:keys [order-id]}]
  (println "  → 通知仓库：备货" order-id))

(defmethod event-handler :user/registered [{:keys [name]}]
  (println "  → 发欢迎邮件给" name))

(defmethod event-handler :default [e]
  (println "  → 未知事件类型：" (:type e)))

;; 事件就是普通 map，可以从 Kafka/file/HTTP 来
(doseq [e [{:type :order/submitted :order-id 42}
           {:type :user/registered :name "Ada"}
           {:type :system/blah}]]
  (event-handler e))

;; 关键：要加新事件类型，只要 (defmethod event-handler :new/type ...)，
;; 不用改任何已有代码。这就是"开放扩展"。

(println "\n=== 7. 反例：什么时候 OOP/封装 仍然有意义？ ===")
(println "- 真正的状态管理（数据库连接、文件句柄）→ 用 record + protocol，但封的是'生命周期'，不是数据")
(println "- 性能关键路径（大数组、JVM 互操作）→ 用 deftype/array")
(println "- 99% 的业务逻辑：不要造类，用 map + 函数")

(println "\n=== 一句话总结 ===")
(println "- OOP 把数据/行为/身份强行三合一；DOP 让它们解耦")
(println "- 数据 = 不可变 map/vector；函数 = 输入 → 输出；身份 = atom/ref 包数据")
(println "- 'getter setter visitor builder' 在 DOP 里通通消失，剩 select-keys/update-in/multimethod")
(println "- Rich Hickey 'The Value of Values'：值是惰性的、可比较的、可传输的")
(println "- 这是 Clojure 区别于 Scala/Kotlin 的核心心智，不仅是语法")
