;; demo 22 — clojure.spec.alpha 基础：定义、验证、解释、conform/unform
;; spec.alpha 是 Clojure 1.9+ 自带 namespace（无外部依赖）。
;; 运行：clojure -M clojure/22_spec_basic.clj

(ns demo22
  (:require [clojure.spec.alpha :as s]))

(println "=== 1. 谓词即 spec：任何 1-arg 函数都能当 spec ===")
(println (s/valid? int? 42))           ;; true
(println (s/valid? int? "42"))         ;; false
(println (s/valid? #(> % 0) 5))        ;; true
;; spec 是数据，不是类型；运行期检查，不影响编译

(println "\n=== 2. s/def 注册命名 spec（kw 必须 namespace 化） ===")
(s/def ::age      (s/and int? #(<= 0 % 150)))
(s/def ::email    (s/and string? #(re-matches #".+@.+\..+" %)))
(s/def ::name     (s/and string? #(<= 1 (count %) 50)))
(println (s/valid? ::age 30))                          ;; true
(println (s/valid? ::age -1))                          ;; false（负数）
(println (s/valid? ::email "a@b.com"))                 ;; true
(println (s/valid? ::email "not-email"))               ;; false

(println "\n=== 3. s/explain：失败时人类可读的诊断 ===")
(s/explain ::age -5)
;; 输出：-5 - failed: (<= 0 % 150) spec: :demo22/age
(s/explain ::email "no-at-sign")

(println "\n--- explain-data：拿到结构化失败信息 ---")
(let [problems (::s/problems (s/explain-data ::age -5))]
  (println (first problems)))
;; {:path [], :pred (clojure.core/<= 0 % 150), :val -5, :via [:demo22/age], :in []}

(println "\n=== 4. s/keys：map 的 spec（必填 + 选填） ===")
(s/def ::user (s/keys :req-un [::name ::email]
                      :opt-un [::age]))
;; req-un = required, unqualified（key 写 :name 而不是 :demo22/name）
(println (s/valid? ::user {:name "Ada" :email "ada@x.com"}))                ;; true
(println (s/valid? ::user {:name "Ada" :email "ada@x.com" :age 30}))        ;; true
(println (s/valid? ::user {:name "Ada"}))                                    ;; false（缺 email）
(println (s/valid? ::user {:name "" :email "ada@x.com"}))                   ;; false（name 长度 0）

(println "\n--- 看看缺字段时 explain 怎么说 ---")
(s/explain ::user {:name "Ada"})

(println "\n=== 5. s/and / s/or：组合 spec ===")
(s/def ::positive-int (s/and int? pos?))
(s/def ::id-or-name   (s/or :id ::positive-int :name ::name))
(println (s/valid? ::positive-int 5))      ;; true
(println (s/valid? ::positive-int -3))     ;; false
;; s/or 的 conform 给出 [tag value]
(println (s/conform ::id-or-name 42))      ;; [:id 42]
(println (s/conform ::id-or-name "Ada"))   ;; [:name "Ada"]

(println "\n=== 6. s/coll-of / s/map-of：集合 spec ===")
(s/def ::tags (s/coll-of string? :min-count 1 :max-count 5 :distinct true))
(println (s/valid? ::tags ["clj" "fp"]))            ;; true
(println (s/valid? ::tags []))                       ;; false（min-count）
(println (s/valid? ::tags ["a" "a"]))                ;; false（distinct）

(s/def ::scores (s/map-of keyword? int?))
(println (s/valid? ::scores {:math 90 :art 80}))    ;; true
(println (s/valid? ::scores {:math "A"}))            ;; false

(println "\n=== 7. s/conform：把数据'解析'成结构化形式 ===")
;; conform 不只是验证，还会附上"这条记录命中了哪个分支"
(s/def ::config (s/cat :host string? :port pos-int? :flags (s/* keyword?)))
;; s/cat 是"序列正则"——按位置匹配
(println (s/conform ::config ["localhost" 8080 :tls :debug]))
;; {:host "localhost", :port 8080, :flags [:tls :debug]}

(println "\n--- s/unform：反过来把 conformed 还原成原始数据 ---")
(println (s/unform ::config (s/conform ::config ["localhost" 8080 :tls])))
;; ("localhost" 8080 :tls)

(println "\n=== 8. s/fdef + instrument：函数 spec（运行期校验入参） ===")
(defn add-positive [a b] (+ a b))

(s/fdef add-positive
  :args (s/cat :a ::positive-int :b ::positive-int)
  :ret  ::positive-int)

;; instrument 只有 require clojure.spec.test.alpha 才生效，这里手动用 assert
(require '[clojure.spec.test.alpha :as st])
(st/instrument `add-positive)
(try
  (println (add-positive 3 4))                ;; 7
  (println (add-positive 3 -4))               ;; 抛 ExceptionInfo
  (catch Exception e
    (println "捕获到 instrument 异常：" (.getMessage e))))
(st/unstrument `add-positive)

(println "\n=== 9. s/assert：把 spec 当 assert 用 ===")
;; 默认关闭，开了之后违反会抛
(s/check-asserts true)
(try
  (s/assert ::positive-int -1)
  (catch Exception e
    (println "assert 失败：" (-> e ex-data ::s/problems first :pred))))
(s/check-asserts false)

(println "\n=== 一句话总结 ===")
(println "- spec 是数据：谓词 / s/and / s/or / s/keys / s/coll-of 全是函数调用")
(println "- 4 个常用动词：valid? / explain / conform / unform")
(println "- 命名 spec 的 kw 必须 namespace 化（::foo 自动展开为 :ns/foo）")
(println "- s/keys 用 :req-un/:opt-un 关心 key 是否有值，不管 key 本身的 spec")
(println "- s/fdef + instrument：函数边界的运行期校验（开发期用，生产可关）")
