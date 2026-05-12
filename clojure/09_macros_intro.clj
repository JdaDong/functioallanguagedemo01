;; ============================================================
;; Clojure Demo 09 — Macros Intro：宏 = "代码生成器"
;; ============================================================
;; 关键事实：
;;   - 宏在 *编译期* 跑，输入是 Clojure 数据（list/symbol/keyword）
;;     输出是另一段 Clojure 数据，编译器拿这段数据继续编译
;;   - 函数：传值；宏：传"未求值的代码片段"
;;   - 三个工具：' (quote) / ` (syntax-quote) / ~ (unquote) / ~@ (splice)
;;   - 调试两件套：(macroexpand-1 '(...))  / (macroexpand '(...))
;;
;; 运行：clojure -M clojure/09_macros_intro.clj
;; ============================================================

(println "=== 1. 函数 vs 宏：什么时候非宏不可？ ===")
;; 想要一个 (unless cond body) 表达式：cond 假时执行 body
;; 用函数能做到吗？答：不能——函数会先求值 body，达不到"假时才跑"

(defn unless-fn [cond body]
  (if cond nil body))

;; (unless-fn (= 1 1) (println "永远会跑"))   ;; 即使 cond 真，println 也会执行

;; 用宏：body 不会被求值，原封不动塞进 if 里
(defmacro unless [cond & body]
  (list 'if cond nil (cons 'do body)))

(unless (= 1 2) (println "  ✓ 1≠2 时这行会跑"))
(unless (= 1 1) (println "  ✗ 1=1 时这行不应该跑"))   ;; 没输出
(println "（unless 用宏才能『按需求值』，这是宏的核心存在意义）")

(println "\n=== 2. 用 macroexpand-1 看宏到底展开成什么 ===")
(println "源代码：" '(unless (= 1 2) (println "hi") (println "world")))
(println "展开后：" (macroexpand-1 '(unless (= 1 2) (println "hi") (println "world"))))

(println "\n=== 3. quote ' / syntax-quote ` / unquote ~ / splice ~@ ===")
;; '   完全字面化（不解析符号到 namespace）
;; `   syntax-quote：自动给符号加上完整 namespace（例：x → user/x）
;; ~   在 ` 内打洞，把表达式求值后嵌入
;; ~@  在 ` 内"撒入"列表元素（不是嵌入列表本身）

(let [x 10
      xs [1 2 3]]
  (println "'(a b ~x ~@xs)   =" '(a b ~x ~@xs)
           "  ;; quote 不识别 ~")
  (println "`(a b ~x ~@xs)   =" `(a b ~x ~@xs)
           "  ;; ~x 嵌入 10，~@xs 撒入 1 2 3"))

(println "\n=== 4. 用 syntax-quote 重写 unless（更清晰） ===")
(defmacro unless2 [cond & body]
  `(if ~cond
     nil
     (do ~@body)))

(unless2 (zero? 5) (println "  ✓ 5 不是 0（cond 假），这行该跑"))
(unless2 (zero? 0) (println "  ✗ 0 是 0（cond 真），这行不应跑"))
(println "展开 unless2：" (macroexpand-1 '(unless2 x (println :a) (println :b))))

(println "\n=== 5. 经典示例：手写一个 my-when ===")
;; clojure.core/when 就是 (if cond (do body...) nil)
(defmacro my-when [cond & body]
  `(if ~cond (do ~@body) nil))

(my-when (> 5 3) (println "  5 > 3 → 进入") (println "  body 多语句"))
(my-when (> 3 5) (println "  这里不应输出"))

(println "\n=== 6. 更复杂：手写一个 dotimes ===")
(defmacro my-dotimes [[sym n] & body]
  `(let [n# ~n]                              ;; n# 是 auto-gensym（demo 14 详讲）
     (loop [~sym 0]
       (when (< ~sym n#)
         ~@body
         (recur (inc ~sym))))))

(print "my-dotimes 5 次： ")
(my-dotimes [i 5] (print i " "))
(println)

(println "\n=== 7. 调试：macroexpand 看完整展开 ===")
;; macroexpand-1：展开一次（外层）
;; macroexpand：递归展开直到不再是宏调用
(println "展开 (my-when (> x 0) (do-something))：")
(println "  -1 :" (macroexpand-1 '(my-when (> x 0) (do-something))))
(println "   * :" (macroexpand   '(my-when (> x 0) (do-something))))

(println "\n=== 一句话总结 ===")
(println "- 宏 = 编译期的代码生成器；输入数据，输出数据")
(println "- 'quote / `syntax-quote / ~unquote / ~@splice 四件套")
(println "- 卡住时第一反应：(macroexpand-1 '(...))")
(println "- 99% 的场景能用函数就别上宏（demo 11/12 才是宏真正发光的地方）")
