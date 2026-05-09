;; ============================================================
;; Clojure Demo 05 — recur 与 loop：JVM 没 TCO，Clojure 的显式补偿
;; ============================================================
;; 关键事实：
;;   - JVM 字节码没有 tail-call-optimization（尾调用优化）
;;   - 所以 Clojure 不给 "自动 TCO"，而是给你一个显式工具：recur
;;   - recur 只能出现在"尾位置"，编译器保证它变成 goto（循环跳转）
;;   - 不是尾调用的地方写 recur → 编译期直接报错（防你误用）
;;
;; 运行：clojure -M clojure/05_recur_and_loop.clj
;; ============================================================

(println "=== 1. 最简对比：朴素递归 vs recur ===")

;; 朴素递归：每次调用都压一个栈帧，大 N 会爆栈
(defn sum-naive [n acc]
  (if (zero? n)
    acc
    (sum-naive (dec n) (+ acc n))))   ;; 看似尾调用，但 JVM 不会优化

;; recur 版：编译后是 goto，单栈帧跑到底
(defn sum-recur [n]
  (loop [i n, acc 0]              ;; loop 建立"跳回目标"
    (if (zero? i)
      acc
      (recur (dec i) (+ acc i))))) ;; recur 不是函数调用，是"跳回 loop"

(println "小数据两种都行：sum 1..1000")
(println "  naive =" (sum-naive 1000 0))
(println "  recur =" (sum-recur 1000))

(println "\n=== 2. 大数据：recur 安全，朴素递归爆栈 ===")
(println "sum 1..100000 recur 版 =" (sum-recur 100000))

(print "sum 1..100000 naive 版 = ")
(try
  (println (sum-naive 100000 0))
  (catch StackOverflowError _
    (println "💥 StackOverflowError（符合预期）")))

(println "\n=== 3. recur 必须在尾位置，编译器帮你把关 ===")
;; 下面这段是"反面教材"，故意用 try 包住让它编译报错
(print "把 recur 写在非尾位置会怎样？→ ")
(try
  (eval '(defn bad-fn [n]
           (if (zero? n)
             0
             (+ 1 (recur (dec n)))))) ;; recur 前还要 +1，不是尾位置
  (println "（竟然过了？不应该）")
  (catch Exception e
    (println "✅ 编译期报错："
             (-> e Throwable->map :cause))))

(println "\n=== 4. loop 本身就是带绑定的 recur 跳转点 ===")
;; 经典：把 List 反转（不借助 reverse）
(defn my-reverse [xs]
  (loop [remain xs, acc '()]
    (if (empty? remain)
      acc
      (recur (rest remain) (cons (first remain) acc)))))

(println "my-reverse [1 2 3 4 5] =" (my-reverse [1 2 3 4 5]))

(println "\n=== 5. 函数也能直接 recur 回自己（不用 loop）===")
(defn countdown [n]
  (when (pos? n)
    (print n " ")
    (recur (dec n))))   ;; 直接 recur 回 countdown

(print "countdown 5 → ")
(countdown 5)
(println)

(println "\n=== 一句话总结 ===")
(println "- 想写递归？别用自调用，用 recur / loop")
(println "- 编译器是你的栈安全检查员：写错位置直接拒绝编译")
(println "- 需要\"真递归\"（非尾）时用 trampoline（后续 demo 讲）")
