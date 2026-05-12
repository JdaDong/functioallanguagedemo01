(ns demo45
  "spec 进阶：custom generator + spec-test 自动 property 测试 + s/and 联立约束 +
   多重派发 (multi-spec) + recursive spec。

   demo 22 演示 spec 基础（valid? / explain / s/keys）。
   本 demo 演示工业里真用得到的高阶招式。

   运行：clojure -M:run"
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.test.alpha :as stest]
            [clojure.test.check.generators :as tgen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check :as tc]))

;; ───────────────────────────────────────────────────────────────────────
;; section 1: 自定义 generator —— 解决"约束太紧导致默认 generator 卡死"
;; ───────────────────────────────────────────────────────────────────────
(s/def ::small-prime
  (s/with-gen
    (s/and pos-int? #(< % 100)
           (fn [n] (and (> n 1)
                        (every? #(pos? (mod n %)) (range 2 n)))))
    ;; 默认 generator 用 pos-int? 试错，10000 次可能也找不到合法值
    ;; 自定义：从已知小素数列表里选
    #(gen/elements [2 3 5 7 11 13 17 19 23 29 31 37 41 43 47 53 59 61 67 71 73 79 83 89 97])))

(defn section-1-with-gen []
  (println "── section 1: with-gen 解决紧约束生成困难 ──")
  (let [samples (gen/sample (s/gen ::small-prime) 8)]
    (println "  10 个 ::small-prime 样本：" samples)
    (assert (every? #(s/valid? ::small-prime %) samples))))

;; ───────────────────────────────────────────────────────────────────────
;; section 2: 函数 spec + stest/check 自动 property 测试
;; ───────────────────────────────────────────────────────────────────────
(defn safe-divide [a b]
  (if (zero? b) 0 (/ a b)))

(s/fdef safe-divide
  :args (s/cat :a int? :b int?)
  :ret  number?
  :fn   (fn [{{:keys [a b]} :args ret :ret}]
          (or (zero? b)
              (= ret (/ a b)))))

(defn section-2-stest-check []
  (println "\n── section 2: stest/check 自动跑 50 个随机 case 检验 :ret + :fn ──")
  (let [results (stest/check `safe-divide
                             {:clojure.spec.test.check/opts {:num-tests 50}})
        result  (first results)
        check   (:clojure.spec.test.check/ret result)]
    (println (format "  num-tests=%d  pass?=%s"
                     (:num-tests check) (:pass? check)))
    (assert (:pass? check) "随机 50 case 应全过")))

;; ───────────────────────────────────────────────────────────────────────
;; section 3: multi-spec —— 按 :type 字段动态分派 schema
;; ───────────────────────────────────────────────────────────────────────
(defmulti event-type :type)

(defmethod event-type :user/registered [_]
  (s/keys :req-un [::user-id ::email]))
(defmethod event-type :order/placed [_]
  (s/keys :req-un [::order-id ::amount]))

(s/def ::user-id  pos-int?)
(s/def ::email    string?)
(s/def ::order-id string?)
(s/def ::amount   pos?)
(s/def ::event    (s/multi-spec event-type :type))

(defn section-3-multi-spec []
  (println "\n── section 3: multi-spec 按 :type 分派 schema ──")
  (let [e1 {:type :user/registered :user-id 1 :email "ada@x.com"}
        e2 {:type :order/placed    :order-id "O-1" :amount 100.0}
        e3 {:type :user/registered :user-id 1}]   ;; 缺 :email
    (println "  e1 valid?" (s/valid? ::event e1))
    (println "  e2 valid?" (s/valid? ::event e2))
    (println "  e3 valid?" (s/valid? ::event e3))
    (assert (s/valid? ::event e1))
    (assert (s/valid? ::event e2))
    (assert (not (s/valid? ::event e3)))))

;; ───────────────────────────────────────────────────────────────────────
;; section 4: recursive spec —— 二叉树
;; ───────────────────────────────────────────────────────────────────────
(s/def ::tree
  (s/or :leaf   #{:leaf}
        :branch (s/cat :tag #{:branch}
                       :v   int?
                       :l   ::tree
                       :r   ::tree)))

(defn section-4-recursive []
  (println "\n── section 4: recursive spec ──")
  (let [t1 :leaf
        t2 [:branch 1 :leaf :leaf]
        t3 [:branch 1 [:branch 2 :leaf :leaf] :leaf]
        t4 [:branch 1 :leaf 2]]                 ;; 非法：右子树是 int 而非 tree
    (println "  :leaf valid?" (s/valid? ::tree t1))
    (println "  深度 1 valid?" (s/valid? ::tree t2))
    (println "  深度 2 valid?" (s/valid? ::tree t3))
    (println "  非法 valid?"   (s/valid? ::tree t4))
    (assert (s/valid? ::tree t1))
    (assert (s/valid? ::tree t2))
    (assert (s/valid? ::tree t3))
    (assert (not (s/valid? ::tree t4)))))

;; ───────────────────────────────────────────────────────────────────────
;; section 5: instrument 拦下错误调用
;; ───────────────────────────────────────────────────────────────────────
(s/fdef squared
  :args (s/cat :n int?)
  :ret  pos-int?)

(defn squared [n] (* n n))

(defn section-5-instrument []
  (println "\n── section 5: instrument 拦下错误调用 ──")
  (stest/instrument `squared)
  (try
    (squared "not-a-number")                    ;; 应被 instrument 拦下
    (assert false "instrument 应当抛")
    (catch Exception e
      (let [msg (.getMessage e)]
        (println "  ✓ instrument 拦下：" (subs msg 0 (min 80 (count msg)))))))
  (stest/unstrument `squared)
  ;; unstrument 后调用恢复正常（即便参数错也不报，反映"开发期 instrument，生产关掉"）
  (println "  unstrument 后调用："
           (try (squared 5) (catch Exception e (.getMessage e))))
  (assert (= 25 (squared 5))))

(defn -main [& _]
  (section-1-with-gen)
  (section-2-stest-check)
  (section-3-multi-spec)
  (section-4-recursive)
  (section-5-instrument)
  (println "\n=== 一句话总结 ===")
  (println "- with-gen：紧约束 spec 必须配自定义 generator，否则随机生成卡死")
  (println "- stest/check：把 fdef 当 property test 自动跑")
  (println "- multi-spec：按 dispatch fn 选 schema —— 异构事件流的标配")
  (println "- recursive spec：::tree 引用自身，自然表达递归结构")
  (println "- instrument：开发期开（参数错→抛），生产期关（性能开销）")
  (shutdown-agents))
