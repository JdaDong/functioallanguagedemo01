(ns demo24
  "metosin/malli：现代版的 spec —— schema 即数据，validate / explain / coerce / generate 一站式。

   参考：https://github.com/metosin/malli
   运行：clojure -M:run"
  (:require [malli.core :as m]
            [malli.error :as me]
            [malli.transform :as mt]
            [malli.generator :as mg]
            [malli.dev :as mdev]
            [malli.dev.pretty :as mpretty]))

(defn -main [& _]

  (println "=== 1. schema 就是数据 ===")
  ;; spec 里：(s/and int? pos?) 是函数调用，编译期就执行了
  ;; malli 里：[:and :int [:> 0]] 就是个普通 vector，运行时 m/validate 才解析
  ;; 这意味着 schema 可以序列化、传输、动态构造
  (def Age [:and :int [:>= 0] [:<= 150]])
  (println "schema 本体：" Age)
  (println "validate 30 ：" (m/validate Age 30))      ;; true
  (println "validate -1 ：" (m/validate Age -1))      ;; false
  (println "validate 'a'：" (m/validate Age "a"))    ;; false

  (println "\n=== 2. m/explain + me/humanize：人类可读的错误 ===")
  (-> (m/explain Age 200) me/humanize println)
  ;; ["should be at most 150"]
  (-> (m/explain Age "x") me/humanize println)
  ;; ["should be an int"]

  (println "\n=== 3. map schema：[:map ...] 比 spec/keys 更直白 ===")
  (def User
    [:map
     [:name   :string]
     [:age    Age]
     [:email  {:optional true} [:re #".+@.+\..+"]]])

  (println (m/validate User {:name "Ada" :age 30}))                          ;; true
  (println (m/validate User {:name "Ada" :age 30 :email "a@b.com"}))         ;; true
  (println (m/validate User {:name "Ada" :age 200}))                         ;; false
  (println "缺 :name 的错误：")
  (-> (m/explain User {:age 30}) me/humanize println)
  ;; {:name ["missing required key"]}

  (println "\n=== 4. coerce：自动转换字符串 → 目标类型（spec 没有的杀手锏） ===")
  ;; HTTP / EDN / JSON 进来的常常是 "30" 这种字符串，malli 能 decode 成 30
  (println (m/decode User
                     {:name "Ada" :age "30" :email "a@b.com"}
                     mt/string-transformer))
  ;; {:name "Ada", :age 30, :email "a@b.com"}

  ;; 反过来：encode 成可序列化形式
  (println (m/encode [:tuple :keyword :int] [:foo 42] mt/json-transformer))
  ;; ["foo" 42]

  (println "\n=== 5. generator：开箱即用的 property test 数据 ===")
  (println "5 个合法 User（不含 :email，因为正则需要 test.chuck）：")
  (let [SimpleUser [:map [:name :string] [:age Age]]]
    (doseq [u (mg/sample SimpleUser {:size 5})]
      (println " " u)))
  ;; malli 知道怎么生成大部分常见 schema；正则等"约束式"需可选 test.chuck
  ;; 不像 spec 那样必须 with-gen

  (println "\n=== 6. closed map：拒绝多余的 key（spec 默认不管） ===")
  (def StrictUser
    [:map {:closed true}
     [:name :string]
     [:age  :int]])
  (println (m/validate StrictUser {:name "Ada" :age 30}))                    ;; true
  (println (m/validate StrictUser {:name "Ada" :age 30 :extra "?"}))         ;; false
  (-> (m/explain StrictUser {:name "Ada" :age 30 :extra "?"}) me/humanize println)
  ;; {:extra ["disallowed key"]}

  (println "\n=== 7. 函数 schema + instrument（对标 s/fdef） ===")
  (defn add-positive
    {:malli/schema [:=> [:cat :int :int] :int]}      ;; 等价于 (-> [int int] int)
    [a b]
    (+ a b))

  (mdev/start! {:report (mpretty/reporter)})
  (println "正常调用：" (add-positive 3 4))
  (try
    (add-positive 3 "x")                              ;; 应该被拦截
    (println "❌ 没被拦住")
    (catch Exception e
      (println "✓ instrument 拦截到非法入参（malli/dev 已打印诊断到 stderr）")))
  (mdev/stop!)

  (println "\n=== 8. 动态构造 schema（spec 做不到的事） ===")
  ;; 因为 schema 就是数据，可以根据运行时配置拼出来
  (defn build-schema [required-fields]
    (into [:map] (map (fn [k] [k :string]) required-fields)))

  (let [s (build-schema [:foo :bar :baz])]
    (println "动态生成的 schema：" s)
    (println "validate {:foo \"a\" :bar \"b\" :baz \"c\"} =" (m/validate s {:foo "a" :bar "b" :baz "c"}))
    (println "validate {:foo \"a\"} =" (m/validate s {:foo "a"})))

  (println "\n=== 一句话总结 ===")
  (println "- schema = 数据：可序列化、可传输、可动态构造（spec 做不到）")
  (println "- 5 个常用动词：validate / explain / decode / encode / generator")
  (println "- decode + transformer 一键搞定 'JSON/string → 强类型' 的脏活")
  (println "- {:closed true} 默认拒绝多余 key，比 spec 更严")
  (println "- :=> 函数 schema + malli.dev 提供 fdef 等价能力 + 漂亮诊断")
  (shutdown-agents))
