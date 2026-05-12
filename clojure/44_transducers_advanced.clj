;; demo 44 — Transducers 进阶：自定义 stateful transducer + 性能基准
;; 零依赖。
;; 运行：clojure -M 44_transducers_advanced.clj

(ns demo44)

;; ───────────────────────────────────────────────────────────────────────
;; transducer 的契约：
;;   xform = fn (rf : reducer) -> rf'
;;   rf 三 arity：
;;     ()       —— 初始化（很少用，给 transduce）
;;     (acc)    —— completing（一次终结，flush 状态）
;;     (acc x)  —— 处理一个元素，返回新 acc 或 (reduced acc)
;;
;; 大多数无状态 transducer 直接 (rf acc (f x))；有状态的要持闭包变量
;; ───────────────────────────────────────────────────────────────────────

;; ───────────────────────────────────────────────────────────────────────
;; section 1: 自定义无状态 transducer —— take-evens
;; ───────────────────────────────────────────────────────────────────────
(defn take-evens
  "transducer：保留偶数。等价于 (filter even?)，但展示从零写"
  [rf]
  (fn
    ([]      (rf))
    ([acc]   (rf acc))
    ([acc x] (if (even? x) (rf acc x) acc))))

(defn section-1-stateless []
  (println "── section 1: 自定义无状态 transducer ──")
  (let [r (into [] take-evens (range 10))]
    (println "  take-evens (range 10) =" r)
    (assert (= [0 2 4 6 8] r))))

;; ───────────────────────────────────────────────────────────────────────
;; section 2: 自定义 stateful transducer —— running-sum
;;   每个输入产出一个"截至当前的累计和"
;; ───────────────────────────────────────────────────────────────────────
(defn running-sum
  "transducer：把 [a b c] 变成 [a (+a b) (+a b c)]"
  [rf]
  (let [s (volatile! 0)]                        ;; volatile! 给 transducer 用，比 atom 更轻
    (fn
      ([]      (rf))
      ([acc]   (rf acc))
      ([acc x] (let [new-s (vswap! s + x)]
                 (rf acc new-s))))))

(defn section-2-stateful []
  (println "\n── section 2: 自定义 stateful transducer ──")
  (let [r (into [] running-sum [1 2 3 4 5])]
    (println "  running-sum [1 2 3 4 5] =" r)
    (assert (= [1 3 6 10 15] r))))

;; ───────────────────────────────────────────────────────────────────────
;; section 3: 自定义 transducer —— take-while-counted
;;   有状态 + 早停：用 (reduced acc) 触发 short-circuit
;; ───────────────────────────────────────────────────────────────────────
(defn take-until-sum-exceeds
  "transducer：累加直到 > 阈值，然后早停。早停的最后一个元素也包含在内。"
  [threshold]
  (fn [rf]
    (let [s (volatile! 0)]
      (fn
        ([]      (rf))
        ([acc]   (rf acc))
        ([acc x]
         (let [new-s (vswap! s + x)
               acc'  (rf acc x)]
           (if (> new-s threshold)
             (reduced acc')                     ;; 早停信号
             acc')))))))

(defn section-3-early-stop []
  (println "\n── section 3: 自定义 transducer 早停（reduced）──")
  (let [r (into [] (take-until-sum-exceeds 10) [1 2 3 4 5 6 7 8 9])]
    (println "  take-until-sum>10 of [1..9] =" r "（停在 1+2+3+4+5=15>10）")
    (assert (= [1 2 3 4 5] r))
    ;; 即使源序列还有 6/7/8/9，xform 已经早停，没浪费
    ))

;; ───────────────────────────────────────────────────────────────────────
;; section 4: 组合 transducer —— comp 是核心
;; ───────────────────────────────────────────────────────────────────────
(defn section-4-composition []
  (println "\n── section 4: comp 组合 transducer ──")
  (let [xf (comp (filter odd?)
                 (map #(* % %))
                 (take 5))
        ;; 注意：transducer comp 阅读顺序是从左到右（与函数 comp 相反！）
        ;; (filter odd?) 先执行；(take 5) 最后
        r (into [] xf (range 100))]
    (println "  comp (filter odd?)→(map sqr)→(take 5) of (range 100) =" r)
    (assert (= [1 9 25 49 81] r))))

;; ───────────────────────────────────────────────────────────────────────
;; section 5: 性能对比 —— transduce vs lazy seq vs reduce
;; ───────────────────────────────────────────────────────────────────────
(defn bench [label f]
  (let [t0 (System/nanoTime)
        r  (f)
        ms (/ (- (System/nanoTime) t0) 1e6)]
    (println (format "  %-30s %.1f ms  (result = %s)" label ms r))
    ms))

(defn section-5-perf []
  (println "\n── section 5: 性能基准 1e6 元素 ──")
  (let [n 1000000
        ;; 三种风格做同一件事：filter even? + map *2 + reduce +
        lazy-fn   #(reduce + (map (partial * 2) (filter even? (range n))))
        trans-fn  #(transduce (comp (filter even?) (map (partial * 2))) + 0 (range n))
        loop-fn   #(loop [i 0 acc 0]
                     (if (< i n)
                       (recur (inc i) (if (even? i) (+ acc (* 2 i)) acc))
                       acc))]
    ;; warm-up
    (lazy-fn) (trans-fn) (loop-fn)
    (let [a (bench "lazy seq (map+filter+reduce)" lazy-fn)
          b (bench "transduce (zero alloc)        " trans-fn)
          c (bench "manual loop                   " loop-fn)]
      ;; 一般 b/c < a，但实测有 JIT 噪声；不做 < 断言，只 assert 三者结果一致
      (assert (= (lazy-fn) (trans-fn) (loop-fn)) "三种实现结果应一致"))))

;; ───────────────────────────────────────────────────────────────────────
;; section 6: 把 transducer 用到 chan 上（多场景复用）
;; ───────────────────────────────────────────────────────────────────────
(defn section-6-chan-xform []
  (println "\n── section 6: 同一份 xform 复用到 collection / chan / sequence ──")
  (let [xf (comp (filter odd?) (map #(* % %)) (take 3))]
    (println "  on collection:" (into [] xf (range 100)))
    (println "  on sequence  :" (sequence xf (range 100)))
    ;; 用到 core.async chan 的话需要 require core.async；这里用 transduce 替代
    (println "  on transduce :" (transduce xf conj [] (range 100)))
    (assert (= (into [] xf (range 100))
               (sequence xf (range 100))
               (transduce xf conj [] (range 100))))))

(defn -main [& _]
  (section-1-stateless)
  (section-2-stateful)
  (section-3-early-stop)
  (section-4-composition)
  (section-5-perf)
  (section-6-chan-xform)
  (println "\n=== 一句话总结 ===")
  (println "- transducer 是与数据源解耦的「transformation recipe」")
  (println "- 三 arity 契约：() / (acc) / (acc x)")
  (println "- stateful 用 volatile! 持局部状态；早停用 (reduced acc)")
  (println "- comp 顺序：从左到右执行（与函数 comp 直觉相反）")
  (println "- 性能：transduce 零中间集合分配，常比 lazy 快；同 xform 复用到 collection/seq/chan/transduce")
  (shutdown-agents))

(-main)
