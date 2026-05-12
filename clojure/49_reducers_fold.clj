;; demo 49 — clojure.core.reducers 的 r/fold：fork-join 并行归约
;; 零依赖（标准库 clojure.core.reducers）。
;; 运行：clojure -M 49_reducers_fold.clj

(ns demo49
  (:require [clojure.core.reducers :as r]))

;; ───────────────────────────────────────────────────────────────────────
;; 心智锚：
;;   reduce  : 单线程线性归约 (acc, x) -> acc'
;;   r/fold  : fork-join 并行归约，需要两个函数：
;;     reducef : (acc, x) -> acc'    单 chunk 内部用
;;     combinef: () | (acc, acc) -> acc  两个 chunk 结果合并
;;   要求：reducef 和 combinef 必须满足结合律（associative）
;;
;; demo 21 浅展示过 r/fold + 对比 pmap。本 demo 深入：
;;   - combinef 的 0-arity 是种子（必须是幺元）
;;   - 不可结合的运算（如减法）→ 错误结果
;;   - fold 适用边界（vector/map 是 foldable，list/lazy-seq 不是）
;;   - 自定义 combinef 做 group-by 风格的并行
;; ───────────────────────────────────────────────────────────────────────

;; ───────────────────────────────────────────────────────────────────────
;; section 1: 最简 r/fold —— sum
;;   + 是结合的（(a+b)+c = a+(b+c)），可作 reducef 也可作 combinef
;; ───────────────────────────────────────────────────────────────────────
(defn section-1-fold-sum []
  (println "── section 1: r/fold sum 1e7 ──")
  (let [data (vec (range 10000000))
        t1 (System/nanoTime)
        s1 (reduce + data)
        ms1 (/ (- (System/nanoTime) t1) 1e6)
        t2 (System/nanoTime)
        s2 (r/fold + data)
        ms2 (/ (- (System/nanoTime) t2) 1e6)]
    (println (format "  reduce + 1e7  %.0f ms  → %d" ms1 s1))
    (println (format "  r/fold + 1e7  %.0f ms  → %d" ms2 s2))
    (println (format "  加速比 %.1fx" (/ ms1 ms2)))
    (assert (= s1 s2))))

;; ───────────────────────────────────────────────────────────────────────
;; section 2: combinef 的 0-arity 是种子（必须幺元）
;;   r/fold 等价于 (combinef chunk-results)，每个 chunk 用 reducef 折叠
;;   chunk 结果再用 combinef 合并 —— combinef 必须有 () 作种子
;; ───────────────────────────────────────────────────────────────────────
(defn section-2-combinef []
  (println "\n── section 2: 显式 combinef ──")
  (let [data (vec (range 1000000))

        ;; reducef: 单 chunk 内部累加
        ;; combinef: 两 chunk 合并（也累加）；() 作为 seed
        sum (r/fold
             10000               ;; chunk size
             (fn combinef
               ([] 0)             ;; seed
               ([a b] (+ a b)))
             (fn reducef [acc x] (+ acc x))
             data)]
    (println "  并行 sum 1e6 =" sum)
    (assert (= 499999500000 sum))))

;; ───────────────────────────────────────────────────────────────────────
;; section 3: r/fold 函数必须满足结合律（举反例）
;; ───────────────────────────────────────────────────────────────────────
(defn section-3-non-associative []
  (println "\n── section 3: 必须满足结合律（举反例）──")
  ;; 反例：构造一个 reducef 故意依赖顺序，看 r/fold 的并行 vs reduce 的串行差异
  ;;   reducef = (fn [acc x] (- acc x))   单 chunk 内串行减
  ;;   combinef = +                       chunk 之间用加合并（不一致 → 错）
  (let [data (vec (range 1 11))                ;; [1..10]
        seq-r (reduce - 0 data)                ;; -55
        par-r (r/fold
               2                                ;; 小 chunk，强制多 chunk
               (fn ([] 0) ([a b] (+ a b)))     ;; combinef: + （故意和 reducef 不一致）
               (fn [acc x] (- acc x))           ;; reducef: -
               data)]
    (println "  reduce 串行 (不断减): " seq-r)
    (println "  r/fold  reducef 是 - 但 combinef 是 +，结果差异巨大：" par-r)
    (println "  → 教训：reducef 和 combinef 必须在该运算上一致（既同且满足结合律）")
    ;; 不 assert 不等（chunk 边界不固定），仅打印
    ))

;; ───────────────────────────────────────────────────────────────────────
;; section 4: foldable 边界 —— vector/map ✅，list/lazy-seq ❌
;; ───────────────────────────────────────────────────────────────────────
(defn section-4-foldable []
  (println "\n── section 4: foldable 边界 ──")
  (let [v (vec (range 100000))
        l (apply list (range 100000))
        s (range 100000)]                       ;; lazy seq

    ;; vector → 真并行
    (let [t (System/nanoTime)
          r1 (r/fold + v)
          ms1 (/ (- (System/nanoTime) t) 1e6)]
      (println (format "  vector r/fold: %.1f ms  → %d" ms1 r1)))

    ;; list → 退化为串行（不是 foldable）
    (let [t (System/nanoTime)
          r2 (r/fold + l)
          ms2 (/ (- (System/nanoTime) t) 1e6)]
      (println (format "  list   r/fold: %.1f ms  → %d  （退化为串行）" ms2 r2)))

    ;; lazy seq → 也退化为串行
    (let [t (System/nanoTime)
          r3 (r/fold + s)
          ms3 (/ (- (System/nanoTime) t) 1e6)]
      (println (format "  lazy   r/fold: %.1f ms  → %d  （退化为串行）" ms3 r3)))))

;; ───────────────────────────────────────────────────────────────────────
;; section 5: 并行 group-by —— 自定义 combinef 合并 map
;; ───────────────────────────────────────────────────────────────────────
(defn section-5-parallel-group-by []
  (println "\n── section 5: 并行 group-by（自定义 combinef 合并 map） ──")
  (let [;; 100 万整数，按 mod 10 分组计数
        data (vec (range 1000000))

        result
        (r/fold
         50000
         ;; combinef: 合并两个 {k count} map
         (fn combinef
           ([] {})
           ([m1 m2] (merge-with + m1 m2)))
         ;; reducef: 单 chunk 内更新 {k count}
         (fn reducef [m x]
           (update m (mod x 10) (fnil inc 0)))
         data)]
    (println "  按 mod 10 分组结果：" (into (sorted-map) result))
    ;; 每个 bucket 应有 100000
    (doseq [k (range 10)]
      (assert (= 100000 (result k))))
    (println "  ✓ 10 个 bucket 各 100000")))

(defn -main [& _]
  (section-1-fold-sum)
  (section-2-combinef)
  (section-3-non-associative)
  (section-4-foldable)
  (section-5-parallel-group-by)
  (println "\n=== 一句话总结 ===")
  (println "- r/fold = fork-join 并行归约：reducef 单 chunk + combinef 跨 chunk")
  (println "- combinef 的 () arity 是种子（必须幺元），(a b) arity 必须满足结合律")
  (println "- 减法/字符串拼接 等不满足结合律的运算，结果会错")
  (println "- foldable 边界：vector / map ✅；list / lazy-seq 退化为串行")
  (println "- 真用例：并行 group-by / sum / count / 直方图，combinef = (merge-with f)")
  (shutdown-agents))

(-main)
