(ns demo28
  "Schema 演进策略：v1 数据老在生产，v2 schema 新发布，怎么不丢数据？

   方案：写显式的 migrate-v1->v2 函数，用 property test 验证：
     - 任何合法 v1 数据 → migrate 后 → 合法 v2 数据
     - 关键字段不丢失（id / 创建时间）
     - 旧 reader 看新数据，新 reader 看老数据，都能 work

   依赖：clojure.spec.alpha（标准库）+ test.check（property test）
   运行：clojure -M:run"
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as tcg]
            [clojure.test.check.properties :as prop]))

;; ───────────────────────────────────────────────────────────────────────
;; v1：早期 User schema —— name 是单个字符串
;; ───────────────────────────────────────────────────────────────────────
(s/def :v1.user/id         pos-int?)
(s/def :v1.user/name       (s/and string? #(<= 1 (count %) 100)))
(s/def :v1.user/created-at pos-int?)
(s/def :v1/user
  (s/keys :req-un [:v1.user/id :v1.user/name :v1.user/created-at]))

;; ───────────────────────────────────────────────────────────────────────
;; v2：拆 first/last，加 schema-version 显式版本号
;; ───────────────────────────────────────────────────────────────────────
(s/def :v2.user/id             pos-int?)
(s/def :v2.user/first-name     (s/and string? #(<= 1 (count %) 50)))
(s/def :v2.user/last-name      (s/and string? #(<= 0 (count %) 50)))   ;; 可空
(s/def :v2.user/created-at     pos-int?)
(s/def :v2.user/schema-version #{2})
(s/def :v2/user
  (s/keys :req-un [:v2.user/id :v2.user/first-name :v2.user/last-name
                   :v2.user/created-at :v2.user/schema-version]))

;; ───────────────────────────────────────────────────────────────────────
;; migrate v1 → v2：朴素拆 name；保留 id / created-at
;; ───────────────────────────────────────────────────────────────────────
(defn migrate-v1->v2 [v1]
  (let [[fst & rst] (clojure.string/split (:name v1) #"\s+" 2)]
    {:id             (:id v1)
     :first-name     fst
     :last-name      (or (first rst) "")
     :created-at     (:created-at v1)
     :schema-version 2}))

;; 反向：v2 → v1（兼容老 reader）
(defn downgrade-v2->v1 [v2]
  {:id         (:id v2)
   :name       (let [{:keys [first-name last-name]} v2]
                 (if (seq last-name)
                   (str first-name " " last-name)
                   first-name))
   :created-at (:created-at v2)})

(defn -main [& _]

  (println "=== 1. 单条手工样例：直观看 migrate 做了什么 ===")
  (let [v1 {:id 1 :name "Ada Lovelace" :created-at 1700000000}
        v2 (migrate-v1->v2 v1)]
    (println "v1 ：" v1)
    (println "v2 ：" v2)
    (println "v1 合法?    ：" (s/valid? :v1/user v1))
    (println "v2 合法?    ：" (s/valid? :v2/user v2))
    (println "downgrade 回 v1 = 原 v1?" (= v1 (downgrade-v2->v1 v2))))

  (println "\n=== 2. 边界：单字 name（没有 last-name 的情况） ===")
  (let [v1 {:id 2 :name "Plato" :created-at 1700000001}
        v2 (migrate-v1->v2 v1)]
    (println "v1 ：" v1)
    (println "v2 ：" v2 "  ← last-name 是空字符串")
    (println "downgrade 还原 ：" (downgrade-v2->v1 v2))
    (println "= v1?           ：" (= v1 (downgrade-v2->v1 v2))))

  (println "\n=== 3. property test #1：任何合法 v1 → migrate → 合法 v2 ===")
  ;; ⚠️ 教学高光：这条 property 会在 ~50 个样本内失败！
  ;; 原因：v1.name 允许 1-100 字符，但 v2.first-name 只允许 1-50 字符
  ;;       → 一个 51+ 字符且不含空格的合法 v1.name，migrate 后 first-name 超长，违反 v2 spec
  ;; 这正是 property test 的杀手锏：手写 demo 时我自己都没意识到这个 schema 不兼容，
  ;; shrink 直接帮我们找到了最简反例。
  (let [prop (prop/for-all [v1 (s/gen :v1/user)]
                           (s/valid? :v2/user (migrate-v1->v2 v1)))
        result (tc/quick-check 200 prop)]
    (println "通过?" (:pass? result) " 跑了" (:num-tests result) "个样本")
    (when-not (:pass? result)
      (println "shrink 反例：" (-> result :shrunk :smallest first))
      (println "→ v1.name 是 51+ 字符的单词时，v2.first-name(<=50) 装不下；")
      (println "  真实生产里：要么 migrate 时截断 + 警告，要么放宽 v2 上限。")))

  (println "\n=== 4. property test #2：id / created-at 永不丢失 ===")
  (let [prop (prop/for-all [v1 (s/gen :v1/user)]
                           (let [v2 (migrate-v1->v2 v1)]
                             (and (= (:id v1)         (:id v2))
                                  (= (:created-at v1) (:created-at v2)))))
        result (tc/quick-check 200 prop)]
    (println "通过?" (:pass? result) " 跑了" (:num-tests result) "个样本"))

  (println "\n=== 5. property test #3：migrate 后 downgrade 应等于原 v1（针对单字 name 也成立） ===")
  ;; 但要注意：v1 的 name 可能含多空格，"Ada  Lovelace" → 拆 → "Ada Lovelace"，会丢一个空格
  ;; 所以这条 property 对"name 不含连续空格"才成立——演示如何用 such-that 收紧 generator
  (let [single-space-v1-gen
        (gen/such-that
          (fn [{:keys [name]}]
            (= name (clojure.string/replace name #"\s+" " ")))
          (s/gen :v1/user)
          100)
        prop (prop/for-all [v1 single-space-v1-gen]
                           (= v1 (-> v1 migrate-v1->v2 downgrade-v2->v1)))
        result (tc/quick-check 200 prop)]
    (println "通过?" (:pass? result) " 跑了" (:num-tests result) "个样本")
    (when-not (:pass? result)
      (println "反例 ：" (-> result :shrunk :smallest first))))

  (println "\n=== 6. 演进策略小结：版本字段 + 显式 migrate + property test ===")
  (println "1) 数据带 :schema-version（v1 没有，但 v2 起强制）")
  (println "   读端看到没有 :schema-version 的就当 v1；migrate 时打上 v2")
  (println "2) 写显式 migrate-vN->vN+1 函数，单向；不要试图自动推导")
  (println "3) 写 property：")
  (println "   - 合法 vN → migrate → 合法 vN+1")
  (println "   - 关键不变量（id / 创建时间 / 业务键）等价")
  (println "   - 可选：vN+1 → downgrade → vN，让老客户端继续读")
  (println "4) generator 可能产出'病态合法'数据（如多空格 name）：用 such-that 收紧")
  (println "   或在 spec 里直接加约束")

  (println "\n=== 一句话总结 ===")
  (println "- schema 演进 ≠ 加版本号那么简单")
  (println "- 核心：显式 migrate 函数 + property test 三大命题（合法/不变量/可逆）")
  (println "- spec/gen 自动派生 generator，property test 200 样本就能扫到大部分边界 bug")
  (println "- such-that 收紧 generator，应对'spec 太宽松'造成的伪反例")
  (println "- 真实生产：每条记录带 :schema-version，read 端按版本分派 migrate 链")
  (shutdown-agents))
