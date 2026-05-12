(ns demo46
  "Malli 进阶：custom registry + transform/decode + provider（schema 推断）+
   entries-walker（schema 作为数据深度操控）。

   demo 24 演示 malli 基础（validate / explain / closed map）。
   本 demo 演示 schema 作为数据被深度操控的招式。

   运行：clojure -M:run"
  (:require [malli.core :as m]
            [malli.error :as me]
            [malli.transform :as mt]
            [malli.provider :as mp]
            [malli.util :as mu]))

;; ───────────────────────────────────────────────────────────────────────
;; section 1: custom registry —— 自定义命名 schema，可被关键字引用
;; ───────────────────────────────────────────────────────────────────────
(def custom-registry
  (merge (m/default-schemas)
         {:my/email   (m/-simple-schema
                        {:type :my/email
                         :pred #(and (string? %) (boolean (re-matches #".+@.+\..+" %)))})
          :my/phone   (m/-simple-schema
                        {:type :my/phone
                         :pred #(and (string? %) (boolean (re-matches #"\d{3}-\d{4}-\d{4}" %)))})}))

(def opts {:registry custom-registry})

(defn section-1-registry []
  (println "── section 1: custom registry —— 命名 schema ──")
  (let [schema [:map [:email :my/email] [:phone :my/phone]]
        ok    {:email "ada@x.com" :phone "138-1234-5678"}
        bad   {:email "no-at"     :phone "12345"}]
    (println "  ok  valid? " (m/validate schema ok  opts))
    (println "  bad valid? " (m/validate schema bad opts))
    (println "  bad explain：" (-> (m/explain schema bad opts) me/humanize))
    (assert (m/validate schema ok  opts) "ok 不该被拒")
    (assert (not (m/validate schema bad opts)) "bad 该被拒")))

;; ───────────────────────────────────────────────────────────────────────
;; section 2: transform/decode —— string-transformer 把字符串自动转类型
;;   场景：HTTP query string → 强类型 map
;; ───────────────────────────────────────────────────────────────────────
(defn section-2-transform []
  (println "\n── section 2: decode + string-transformer ──")
  (let [User [:map
              [:id     :int]
              [:active :boolean]
              [:tags   [:vector :keyword]]]
        ;; 假设来自 HTTP query 反序列化的脏数据：所有都是字符串
        raw  {:id "42" :active "true" :tags ["admin" "ops"]}
        decoded (m/decode User raw mt/string-transformer)]
    (println "  raw    ：" raw)
    (println "  decoded：" decoded)
    (assert (= 42 (:id decoded)))
    (assert (= true (:active decoded)))
    (assert (= [:admin :ops] (:tags decoded)))
    (assert (m/validate User decoded))))

;; ───────────────────────────────────────────────────────────────────────
;; section 3: m/provider —— 从样本数据反向推断 schema
;;   场景：拿到不熟悉的 JSON，让 malli 帮你猜 schema
;; ───────────────────────────────────────────────────────────────────────
(defn section-3-provider []
  (println "\n── section 3: provider —— 从样本推断 schema ──")
  (let [samples [{:name "Ada" :age 30 :active true}
                 {:name "Bob" :age 25 :active false}
                 {:name "Cy"  :age 28 :active true}]
        inferred (mp/provide samples)]
    (println "  3 个样本推断出：" inferred)
    ;; 推断出来的 schema 应当能通过原样本
    (assert (every? #(m/validate inferred %) samples))))

;; ───────────────────────────────────────────────────────────────────────
;; section 4: entries-walker —— 遍历 schema 节点（schema 即数据）
;;   场景：自动给某 namespace 下所有 :string 字段加 :max 长度限制
;; ───────────────────────────────────────────────────────────────────────
(defn section-4-walk []
  (println "\n── section 4: schema 即数据，可遍历改写 ──")
  (let [schema [:map
                [:name :string]
                [:bio  :string]
                [:age  :int]]
        ;; 用 m/walk 改写：所有 :string 加上 [:string {:max 100}]
        rewritten
        (m/walk schema
                (fn [s _ children _]
                  (cond
                    ;; 简化：只处理叶子 :string
                    (= (m/type s) :string)
                    [:string {:max 100}]
                    :else
                    (m/-set-children s children))))]
    (println "  原 schema：" schema)
    (println "  改写后  ：" (m/form rewritten))
    ;; 改写后的 schema 应当对长字符串失败
    (let [bad {:name (apply str (repeat 200 "x")) :bio "ok" :age 1}]
      (println "  200 字符 :name valid?" (m/validate rewritten bad))
      (assert (not (m/validate rewritten bad))))))

;; ───────────────────────────────────────────────────────────────────────
;; section 5: 编译 validator —— 性能优化技巧
;;   m/validator 编译一次，反复调用比 m/validate 快
;; ───────────────────────────────────────────────────────────────────────
(defn section-5-compiled-validator []
  (println "\n── section 5: m/validator 编译一次反复用 ──")
  (let [User    [:map [:id :int] [:name :string] [:age [:and :int [:>= 0]]]]
        valid?  (m/validator User)              ;; 编译一次
        n       100000
        sample  {:id 1 :name "Ada" :age 30}
        ;; 基准：编译版 vs 每次解释
        t1 (System/nanoTime)
        _  (dotimes [_ n] (valid? sample))
        ms1 (/ (- (System/nanoTime) t1) 1e6)

        t2 (System/nanoTime)
        _  (dotimes [_ n] (m/validate User sample))
        ms2 (/ (- (System/nanoTime) t2) 1e6)]
    (println (format "  编译版 (m/validator)   %d 次 %.1f ms" n ms1))
    (println (format "  解释版 (m/validate)    %d 次 %.1f ms" n ms2))
    (println (format "  加速比 %.1fx" (/ ms2 ms1)))
    (assert (valid? sample))))

(defn -main [& _]
  (section-1-registry)
  (section-2-transform)
  (section-3-provider)
  (section-4-walk)
  (section-5-compiled-validator)
  (println "\n=== 一句话总结 ===")
  (println "- custom registry：把 :my/email :my/phone 这种业务原语注册成命名 schema")
  (println "- decode + transformer：从字符串/JSON 自动 coerce 到强类型，HTTP 入参标配")
  (println "- m/provider：从样本反推 schema，对接陌生 JSON 时省去手写")
  (println "- m/walk：schema 是数据，可遍历改写（自动加约束/批量改 spec）")
  (println "- m/validator：编译一次反复用，热路径上比 m/validate 快显著")
  (shutdown-agents))