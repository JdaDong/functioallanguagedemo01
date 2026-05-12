;; demo 26 — EDN：Clojure 版的 JSON，但内置类型更丰富 + 可扩展
;; clojure.edn 是标准库，零外部依赖。
;; 运行：clojure -M clojure/26_edn_format.clj

(ns demo26
  (:require [clojure.edn :as edn]
            [clojure.pprint :refer [pprint]]))

(println "=== 1. EDN 基础类型：比 JSON 多一截 ===")
(println "JSON 有：null / bool / number / string / array / object（6 种）")
(println "EDN  有：nil / bool / number / string / vector / list / map / set ")
(println "       + keyword / symbol / char / instant / uuid（11+ 种）")
(println)

;; pr-str：写出 EDN 字符串
;; edn/read-string：读回 Clojure 数据
(let [data {:name "Ada"
            :age 30
            :tags #{:admin :clj}                ;; ← JSON 没法直接表示 set
            :birthday #inst "1815-12-10"        ;; ← JSON 要靠字符串约定
            :id #uuid "550e8400-e29b-41d4-a716-446655440000"
            :ratio 22/7                          ;; ← JSON 没有有理数
            :tuple '(1 2 3)}                     ;; ← list 与 vector 是不同类型
      s    (pr-str data)]
  (println "原始：" data)
  (println "EDN  ：" s)
  (println "回读 ：" (edn/read-string s))
  (println "等价?：" (= data (edn/read-string s))))

(println "\n=== 2. EDN vs JSON 对照（同一份数据） ===")
(println "JSON: {\"tags\": [\"admin\",\"clj\"], \"id\": \"550e8400-...\"}")
(println "      → 客户端要自己约定 'tags 是 set'、'id 是 uuid'")
(println "EDN : {:tags #{:admin :clj}, :id #uuid \"550e8400-...\"}")
(println "      → 类型直接体现在字面量里，不需要 schema 约定")

(println "\n=== 3. tagged literal：扩展自己的类型 ===")
;; 内置 #inst / #uuid 之外，你可以注册任意 tag
(defrecord Money [amount currency])

(defn money->edn [^Money m]
  (str "#myapp/money [" (:amount m) " " (pr-str (:currency m)) "]"))

(defmethod print-method Money [m ^java.io.Writer w]
  (.write w (money->edn m)))

(let [m (->Money 100 "USD")
      s (pr-str m)]
  (println "Money 序列化：" s)
  ;; 反序列化时通过 :readers 告诉 edn 怎么读这个 tag
  (let [back (edn/read-string
               {:readers {'myapp/money (fn [[amt cur]] (->Money amt cur))}}
               s)]
    (println "Money 反序列化：" back)
    (println "类型：" (type back))
    (println "等价?：" (= m back))))

(println "\n=== 4. 安全性：edn/read-string 不会执行任意代码（不像 read-string） ===")
;; clojure.core/read-string 会读 #= 和宏，能 RCE
;; clojure.edn/read-string 默认禁用所有动态执行，是网络/磁盘读取的唯一安全选择

(try
  ;; 危险：core/read-string 能执行 #=( ... )
  (println "core/read-string 行为：" (read-string "#=(+ 1 2)"))
  (catch Exception e
    (println "core/read-string 抛错（可能默认禁了 *read-eval*）：" (.getMessage e))))

(try
  (println "edn/read-string 读 #=(...) ：" (edn/read-string "#=(+ 1 2)"))
  (catch Exception e
    (println "edn/read-string 拒绝执行（这正是我们要的）：" (.getMessage e))))

(println "\n→ 规则：从外部源（HTTP/文件/Kafka）读 EDN，必须用 clojure.edn，永远不要用 read-string")

(println "\n=== 5. 处理未知 tag：:default 兜底，不让数据丢失 ===")
;; 上游可能引入了你不认识的 tag，比如 #partner/foo
;; 用 :default 把未知 tag 包进 tagged-literal，保留原貌
(let [s   "{:hello #partner/foo [1 2 3]}"
      ;; 没配 :default 会抛
      r1  (try (edn/read-string s) (catch Exception e (.getMessage e)))
      ;; 配了 :default 就保留下来
      r2  (edn/read-string {:default tagged-literal} s)]
  (println "无 :default：" r1)
  (println "有 :default：" r2)
  (println "回写出来  ：" (pr-str r2))
  (println "保留 tag  ：" (= r2 (edn/read-string {:default tagged-literal} (pr-str r2)))))

(println "\n=== 6. 实战：把 EDN 当配置文件 / 数据交换格式 ===")
;; 比 YAML 简单：没有缩进陷阱
;; 比 JSON 强大：有 keyword / set / 自定义 tag
(def config
  {:server   {:host "localhost" :port 8080}
   :db       {:url "jdbc:postgresql://..." :pool-size 10}
   :features #{:auth :metrics :tracing}
   :launch   #inst "2026-06-01T00:00:00.000-00:00"})

(println "config 写到 EDN：")
(pprint config)

;; 模拟从文件读
(let [s (with-out-str (pr config))
      back (edn/read-string s)]
  (println "回读后 = 原始?  ：" (= config back)))

(println "\n=== 一句话总结 ===")
(println "- EDN = Extensible Data Notation：JSON 的'语言无关超集'")
(println "- 内置 keyword/set/uuid/instant/ratio，类型直接写在字面量里")
(println "- 任意类型用 #my/tag 扩展，反序列化通过 :readers 注册")
(println "- 必用 clojure.edn/read-string，永不要 clojure.core/read-string（RCE）")
(println "- 未知 tag 用 :default tagged-literal 保留，不要让数据丢失")
