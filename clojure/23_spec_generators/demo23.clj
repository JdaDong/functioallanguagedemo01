(ns demo23
  "spec + test.check：property-based testing（对标 QuickCheck）。

   参考：https://clojure.org/guides/spec#_generators
   运行：clojure -M:run"
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.test.alpha :as st]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as tcg]
            [clojure.test.check.properties :as prop]))

(defn -main [& _]

  (println "=== 1. s/exercise：从 spec 自动派生数据生成器 ===")
  (s/def ::age (s/and int? #(<= 0 % 150)))
  ;; exercise 返回 [生成的样本 conformed后的样本]
  (println "10 个合法的 ::age 样本：")
  (doseq [[v _] (s/exercise ::age 10)]
    (print v " "))
  (println)

  (println "\n=== 2. 复合 spec 的生成 ===")
  (s/def ::name  (s/and string? #(<= 1 (count %) 20)))
  (s/def ::email (s/and string? #(re-matches #".+@.+\..+" %)))
  (s/def ::user  (s/keys :req-un [::name ::age]
                         :opt-un [::email]))
  ;; ⚠️ 注意：::email 的正则是"约束式 spec"，spec 不知道怎么逆向生成，
  ;; 所以 ::email 默认会卡住。我们这里只演示能自动生成的那部分（::name+::age）。
  (println "3 个合法的 ::user 样本（无 email）：")
  (doseq [[v _] (s/exercise (s/keys :req-un [::name ::age]) 3)]
    (println " " v))

  (println "\n=== 3. with-gen：给约束式 spec 配自定义生成器 ===")
  ;; 解决上面 ::email 卡死的问题
  (def email-gen
    (gen/fmap (fn [[u d]] (str u "@" d ".com"))
              (gen/tuple
                (gen/such-that #(seq %) (gen/string-alphanumeric))
                (gen/such-that #(seq %) (gen/string-alphanumeric)))))

  (s/def ::email-good
    (s/with-gen
      (s/and string? #(re-matches #".+@.+\..+" %))
      (fn [] email-gen)))

  (println "5 个生成的 email：")
  (doseq [v (gen/sample (s/gen ::email-good) 5)]
    (println " " v))

  (println "\n=== 4. property-based test：核心动词是 'forall' ===")
  ;; 命题：reverse 两次等于自身
  (let [prop-rev-twice
        (prop/for-all [v (tcg/vector tcg/small-integer)]
          (= v (-> v reverse reverse)))
        result (tc/quick-check 100 prop-rev-twice)]
    (println "reverse∘reverse = id：" (:pass? result)
             "（跑了" (:num-tests result) "个随机样本）"))

  (println "\n=== 5. 故意写错的命题：看 shrink 自动找到最小反例 ===")
  ;; 错命题：所有 vector 都比长度 5 短（明显假）
  (let [bad-prop (prop/for-all [v (tcg/vector tcg/small-integer)]
                   (< (count v) 5))
        result   (tc/quick-check 100 bad-prop)]
    (println "结果通过？" (:pass? result))
    (println "随机找到的反例：" (-> result :fail first))
    (println "shrink 后的最小反例：" (-> result :shrunk :smallest first))
    (println "shrink 步数：" (-> result :shrunk :total-nodes-visited)))

  (println "\n=== 6. stest/check：对带 fdef 的函数做 property test ===")
  ;; 给个有 spec 的函数
  (defn add [a b] (+ a b))
  (s/fdef add
    :args (s/cat :a int? :b int?)
    :ret  int?
    :fn   #(= (:ret %) (+ (-> % :args :a)
                          (-> % :args :b))))
  ;; check 自动按 :args spec 生成入参，跑函数，验 :ret 和 :fn
  (let [result (st/check `add {:clojure.spec.test.check/opts {:num-tests 50}})]
    (println "stest/check `add 通过？"
             (-> result first :clojure.spec.test.check/ret :pass?))
    (println "样本数：" (-> result first :clojure.spec.test.check/ret :num-tests)))

  (println "\n=== 7. 实战：给一个'有 bug'的函数做 property test ===")
  (defn buggy-abs
    "故意的 bug：忘了处理 Long/MIN_VALUE 这种边界（这里用一个简化版）"
    [n]
    (if (and (neg? n) (> n -100))           ;; ← bug：对 < -100 的数返回原值
      (- n)
      n))

  (let [prop-abs-non-neg
        (prop/for-all [n tcg/small-integer]
          (>= (buggy-abs n) 0))
        result (tc/quick-check 200 prop-abs-non-neg)]
    (println "buggy-abs 总返回非负？" (:pass? result))
    (println "shrink 后的最小反例 n =" (-> result :shrunk :smallest first))
    (println "→ shrink 引导我们发现：n < -100 时函数返回了负数（bug 定位到边界条件）"))

  (println "\n=== 一句话总结 ===")
  (println "- spec 自带 generator：每条 spec 都能生成符合约束的随机数据")
  (println "- 约束式 spec（如正则）需 with-gen 配自定义 generator")
  (println "- property test 的本质：forall input ∈ generator, property(f input)")
  (println "- shrink：发现反例后自动'缩小'到最简形式，定位 bug 边界")
  (println "- stest/check：对有 fdef 的函数自动跑 1-2 行 property test")
  (shutdown-agents))
