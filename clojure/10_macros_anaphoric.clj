;; ============================================================
;; Clojure Demo 10 — Anaphoric Macros：反卫生宏
;; ============================================================
;; "Anaphoric"（回指）= 自然语言里的"它/他/这个"，指代上文。
;; 反卫生宏：故意引入一个隐式绑定（约定俗成叫 `it`），
;;          让用户在宏 body 里直接引用它，不用显式起名。
;;
;; Common Lisp 的 `aif` / `awhen` / `acond` 是经典案例。
;; Clojure *不直接支持*在 `(syntax-quote 内裸用 `it`（会被加 namespace），
;; 必须用 `(symbol "it")` 或 `~'it` 强制反卫生。
;;
;; ⚠️ 反卫生宏违反"卫生宏"原则，是少数允许的例外。
;;    它的代价是：用户必须知道 `it` 这个隐式名；不会自动重命名。
;;
;; 运行：clojure -M clojure/10_macros_anaphoric.clj
;; ============================================================

(println "=== 1. 痛点：标准 if-let 必须显式起名 ===")
(let [m {:user "alice" :age 30}]
  ;; 标准写法：必须给中间值起名 v
  (if-let [v (:user m)]
    (println "  if-let 显式：用户 =" v)
    (println "  没找到")))

(println "\n=== 2. 反卫生 aif：让 it 自动可用 ===")
;; ~'it 是关键魔法：反引号内的 ~'it 等于"裸 it 符号，不加 namespace"
(defmacro aif
  ([test then] `(aif ~test ~then nil))
  ([test then else]
   `(let [~'it ~test]                         ;; ~'it = 裸 it
      (if ~'it ~then ~else))))

(let [m {:user "alice" :age 30}]
  (aif (:user m)
       (println "  aif：用户 =" it "（it 是隐式绑定）")
       (println "  没找到")))

(println "\n=== 3. awhen：when 的反卫生版 ===")
(defmacro awhen [test & body]
  `(let [~'it ~test]
     (when ~'it ~@body)))

(awhen (some #(when (> % 100) %) [10 50 200 30])
       (println "  找到第一个 > 100 的：" it)
       (println "  it 平方 =" (* it it)))

(awhen (seq (filter pos? [-1 -2 -3]))
       (println "  这行不会跑，因为 awhen 的 test 是 nil"))
(println "  （上面 awhen test = nil，body 整体被跳过）")

(println "\n=== 4. aand：链式判断，每一步都能引用前一步 ===")
;; 标准 (and) 不能引用前面的中间值
;; aand 让每一步都把上一步结果绑定到 it
(defmacro aand
  ([] true)
  ([x] x)
  ([x & xs] `(aif ~x (aand ~@xs) nil)))     ;; 重用 aif 的 it

(let [user {:profile {:address {:city "Shanghai"}}}]
  (println "  用 aand 链式取值（每步 it = 上步结果）：")
  (println "  city =" (aand (:profile user)
                            (:address it)
                            (:city it))))

(println "\n=== 5. 反卫生的代价：必须知道 it 这个魔法名 ===")
;; 用户如果在 body 里也用了 `it`，会冲突
(let [it "我自己定义的 it"]
  ;; 这里如果 aif 是宏可能与外层 it 互动
  (aif (= 1 1)
       (println "  在 aif 内：it =" it
                "（被宏注入的 it 覆盖了外层）")))
(println "  → 这是反卫生宏的 trade-off：清爽 DSL 换隐式名")

(println "\n=== 6. macroexpand 看 it 的真面目 ===")
(println "(awhen x (println it)) 展开为：")
(println " " (macroexpand-1 '(awhen x (println it))))
(println "（注意：it 在结果里没加 namespace，正是反卫生的标志）")

(println "\n=== 7. 卫生 vs 反卫生：何时各选哪种？ ===")
(println "- 默认写卫生宏（用 gensym / # 后缀，见 demo 14）")
(println "- 仅当『隐式绑定能让 DSL 显著清爽』时反卫生")
(println "- 反卫生宏必须在 docstring 里**明确**说明引入了哪些符号")

(println "\n=== 一句话总结 ===")
(println "- 反卫生宏 = 故意引入约定符号（如 it），让 DSL 紧凑")
(println "- Clojure 下用 ~'it 绕过 syntax-quote 的自动 namespace")
(println "- 经典：aif / awhen / aand 三件套，链式判断时极爽")
