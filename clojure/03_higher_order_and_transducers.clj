;; ============================================================
;; Clojure Demo 03 — 高阶函数 & Transducers
;; ============================================================
;; 目标：
;;   (1) 经典 map/filter/reduce 链式
;;   (2) 用 comp / -> / ->> 组合
;;   (3) 同样的逻辑改用 transducer：零中间集合，一次遍历
;;   (4) 用性能对比证明 transducer 的价值
;;
;; 运行：clojure -M clojure/03_higher_order_and_transducers.clj
;; ============================================================

(println "=== 1. 经典高阶：map / filter / reduce ===")
(def nums (range 1 11))
(println "nums =" (vec nums))
(println "平方  =" (map #(* % %) nums))
(println "偶数  =" (filter even? nums))
(println "求和  =" (reduce + nums))

(println "\n=== 2. 组合：先过滤再平方再求和 ===")
(println "传统写法（3 步，2 个中间集合）："
         (reduce + (map #(* % %) (filter even? nums))))

(println "用 ->> 宏（读起来从上到下）："
         (->> nums
              (filter even?)
              (map #(* % %))
              (reduce +)))

(println "\n=== 3. Transducer：把\"做什么\"和\"作用在谁上\"解耦 ===")
;; map/filter 只传一个参数时，返回的是 transducer（一个函数），不是序列
(def xform
  (comp (filter even?)
        (map #(* % %))))

;; 同一个 xform 可以作用到不同的输出形式上：
(println "xform 输出到 vector：" (into [] xform nums))
(println "xform 输出到 set   ：" (into #{} xform nums))
(println "xform 累计求和       ：" (transduce xform + nums))

(println "\n=== 4. 性能对比：1000 万元素 filter→map→全量收成 vector ===")
;; 这里故意不用 take（早停），让两条链都必须跑完全程。
;; 这才是 transducer 真正赢的场景：不省计算，而省"中间集合"的分配。
(defn bench [label f]
  (let [t0 (System/nanoTime)
        r  (f)
        ms (/ (- (System/nanoTime) t0) 1e6)]
    (println (format "  %s → count=%d  耗时 ≈ %.2f ms" label (count r) ms))))

(let [big (range 10000000)]
  (println "\n方法 A：传统 ->> 链（lazy seq，逐步产生 2 个中间 seq）")
  (dotimes [_ 2] ;; 预热 + 正式
    (bench "result"
           #(->> big
                 (filter even?)
                 (map inc)
                 vec)))

  (println "\n方法 B：transducer（一次遍历，零中间集合）")
  (dotimes [_ 2]
    (bench "result"
           #(into []
                  (comp (filter even?) (map inc))
                  big))))

(println "\n=== 5. 什么时候用 transducer ===")
(println "- 多步 + 全量处理 → 用 transducer，省掉 N-1 个中间集合的分配")
(println "- 同一条管线要喂给 channel / vector / reduce → transducer 一次定义到处用")
(println "- 有 take 等早停 + 小数据 → lazy seq 本身就够快，不必上 transducer")
(println "- 结论：不要神化 transducer，它赢在\"分配成本\"而不是\"计算本身\"")
