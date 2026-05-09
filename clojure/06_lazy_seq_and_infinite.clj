;; ============================================================
;; Clojure Demo 06 — 惰性序列 & 无限序列
;; ============================================================
;; 关键事实：
;;   - Clojure 的 map/filter/range/iterate 默认返回"惰性序列"
;;   - 惰性 = 只在被消费时才计算下一个元素
;;   - 所以可以安全地操作"无限序列"，只要你最后只 take 有限个
;;   - 代价：副作用（println 等）不会立即发生；realized? 可以观察
;;
;; 运行：clojure -M clojure/06_lazy_seq_and_infinite.clj
;; ============================================================

(println "=== 1. range 是惰性的：(range) 是无限的 ===")
(println "前 5 个自然数   =" (take 5 (range)))       ;; range 不传参 = 从 0 到 ∞
(println "前 5 个 >= 100  =" (take 5 (drop 100 (range))))

(println "\n=== 2. iterate：从一个种子反复应用函数 ===")
;; iterate f x => (x, f(x), f(f(x)), ...)
(def powers-of-2 (iterate #(* 2 %) 1))
(println "2 的幂前 10 个 =" (take 10 powers-of-2))

(println "\n=== 3. 经典：无限斐波那契流 ===")
;; 注意：用 +' （带撇号）做加法。
;;   +  : Long 范围内最快，溢出抛 ArithmeticException
;;   +' : 溢出时自动升级到 BigInt（第 1000 个 Fib 有 209 位，必须用 +'）
(def fibs
  (map first
       (iterate (fn [[a b]] [b (+' a b)]) [0 1])))

(println "前 15 个斐波那契 =" (take 15 fibs))
(println "第 1000 个斐波那契的位数 ="
         (count (str (nth fibs 1000))))   ;; 只算到第 1000 个，后面永不计算

(println "\n=== 4. 惰性的副作用证据：realized? ===")
(def traced
  (map (fn [x]
         (print (str "算[" x "] "))
         (* x x))
       (range 1 6)))

(println "定义完 traced，还没有任何副作用。realized?" (realized? traced))
(print "\n现在 take 3 强制消费：")
(doall (take 3 traced))
(println "\n注意：实际算了 5 个！这是 lazy seq 的 \"chunking\"：")
(println "  - Clojure 默认按 32 个一块拉（chunk）来摊薄惰性的开销")
(println "  - 我们的源序列只有 5 个，整块被一次性消费")
(println "  - 真要严格逐元素，用 (lazy-seq ...) 自己造，或换 transducer")

(println "\n=== 5. 惰性坑：println 里的 map 可能\"没打印\" ===")
;; 错误示范：map 返回 lazy，没人消费就不会产生副作用
(print "错误示范：(map println [1 2 3]) 的返回值 REPL 层会打印它 → ")
(doall (map print [1 2 3]))   ;; 不加 doall 在脚本模式也可能不打印
(println)
(println "正确做法：想要副作用就 doseq / run! / doall")
(print "doseq 版： ")
(doseq [x [10 20 30]] (print x " "))
(println)

(println "\n=== 6. 无限序列组合：第 100 个偶数平方 ===")
(println
  (nth
    (->> (range)
         (filter even?)
         (map #(* % %)))
    99))   ;; 第 0 个是 0，第 99 个是第 100 个结果 → 198² = 39204

(println "\n=== 一句话总结 ===")
(println "- 惰性让\"无限数据\"成为现实的计算对象")
(println "- 副作用 + 惰性是经典坑：用 doseq / run! / doall 显式强制")
(println "- 超大或热点路径上慎用惰性（按块逐元素处理会有 chunking 细节），")
(println "  那种场景回头交给 transducer（见 demo 03）")
