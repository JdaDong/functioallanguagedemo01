;; ============================================================
;; Clojure Demo 14 — Macro Hygiene：卫生宏与变量捕获
;; ============================================================
;; 经典陷阱：宏内部 let 绑定的临时变量名，可能撞上调用者已有的变量。
;; Clojure 给了 *自动卫生* 工具：`name#`（auto-gensym）和 (gensym)。
;; 反卫生宏（demo 10）是少数允许例外，必须有意识地走 ~'sym。
;;
;; 本 demo 的方法：
;;   先写"故意带 bug"的版本暴露问题
;;   再用 # / gensym 修复
;;   最后看 macroexpand 对比
;;
;; 运行：clojure -M clojure/14_macro_hygiene.clj
;; ============================================================

(println "=== 1. 反例：未卫生的 my-when，会捕获用户的 result ===")
;; 用 ~'result 强制裸名（反卫生写法），故意捕获用户的 result
(defmacro my-when-bad-truly [test & body]
  `(let [~'result ~test]                     ;; 故意捕获：反卫生写法
     (if ~'result (do ~@body) nil)))

(println "调用 (my-when-bad-truly true (...))，body 里的 result 会被宏覆盖：")
(let [result "用户外层定义的 result"]
  (my-when-bad-truly true
    (println "  body 看到的 result =" result
             "（被宏内的 ~'result 覆盖了！）"))) 

(println "\n=== 2. 修复一：用 auto-gensym（name#） ===")
(defmacro my-when-good [test & body]
  `(let [tmp# ~test]                         ;; tmp# 自动展开成 tmp__123__auto__
     (if tmp# (do ~@body) nil)))

(let [tmp "用户的 tmp"]
  (my-when-good (= 1 1)
    (println "  body 看到的 tmp =" tmp
             "（用户值原封不动，宏内的 tmp# 没干扰）")))

(println "\n=== 3. 看 macroexpand 对比两种写法 ===")
(println "坏版本展开：")
(println "  " (macroexpand-1 '(my-when-bad-truly x (println :hi))))
(println "好版本展开：")
(println "  " (macroexpand-1 '(my-when-good x (println :hi))))
(println "→ 区别：好版本里 tmp# 变成 tmp__N__auto__，全宇宙不重名")

(println "\n=== 4. auto-gensym 的限定：只在同一 ` 内一致 ===")
;; 同一个 syntax-quote 内 name# 都展开成同一个 gensym
;; 跨 syntax-quote 是不同的 gensym
(defmacro test-auto []
  `(let [x# 1]
     (println "本宏内多次 x# =" x# "和" x#)))   ;; 同一个 x__N__auto__

(test-auto)

(println "\n=== 5. 显式 gensym：跨 ` 共享名字时用 ===")
;; 当宏要在多段 syntax-quote 间共用一个临时名时，用 (gensym)
(defmacro accumulate-into [target & forms]
  (let [acc (gensym "acc_")]                 ;; 在宏期就生成名字，所有 ` 共享
    `(let [~acc (atom ~target)]
       ~@(for [f forms]
           `(swap! ~acc conj ~f))
       @~acc)))

(println "(accumulate-into [] 1 2 3 4) =" (accumulate-into [] 1 2 3 4))
(println "展开为：")
(println "  " (macroexpand-1 '(accumulate-into [] 1 2)))

(println "\n=== 6. 卫生 vs 反卫生回顾 ===")
;; 默认追求卫生（用 # / gensym）
;; 仅当 DSL 需要"约定隐式名"时反卫生（~'sym），且 docstring 必须说明
;; 例：demo 10 的 aif 用 ~'it 是合理的；普通宏永远卫生

(println "\n=== 7. 真实坑：宏的参数命名也要小心 ===")
;; 即使你用了 #，参数名 `body` 在 (defmacro [body])  里不算 quoted symbol
;; 但只要它不出现在 syntax-quote 内的"插入"位置，就没问题
(defmacro safe-once [body]
  `(do ~body))                               ;; ~body 是 unquote，没问题

(let [body :user-body]
  (safe-once (println "  user body =" body)))

(println "\n=== 一句话总结 ===")
(println "- 默认用 name# 写卫生宏；跨段共用名字时用 (gensym)")
(println "- 故意捕获要用 ~'sym（反卫生），且 docstring 必须显式说明")
(println "- 不放心就 (macroexpand-1 '(...)) 看一眼")
