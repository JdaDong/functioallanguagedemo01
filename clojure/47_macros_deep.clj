;; demo 47 — Macros 进阶：&env / &form / 编译期计算 / macroexpand 三层
;; 零依赖。
;; 运行：clojure -M 47_macros_deep.clj

(ns demo47)

;; ───────────────────────────────────────────────────────────────────────
;; 心智锚：宏运行在编译期，输入是 Form (代码即数据)，输出是新 Form。
;;   defmacro 的隐式参数：
;;     &form  — 当前调用点的整个 form（含 metadata）
;;     &env   — 编译期 locals 环境（symbol → LocalBinding 对象）
;;
;; demo 09-14 已覆盖 quote / unquote / hygiene / state-machine。
;; 本 demo 演示 &env / &form / 编译期计算 三招及组合。
;; ───────────────────────────────────────────────────────────────────────

;; ───────────────────────────────────────────────────────────────────────
;; section 1: 编译期常量折叠 —— 宏内做计算，运行时只剩结果
;; ───────────────────────────────────────────────────────────────────────
(defmacro pow-of-2
  "编译期算 2^n，输出常量"
  [n]
  ;; n 在编译期就是 long，可以直接算
  (assert (and (integer? n) (>= n 0)) "n 必须是字面量非负整数")
  (long (Math/pow 2 n)))

(defn section-1-compile-time-const []
  (println "── section 1: 编译期常量折叠 ──")
  (println "  (pow-of-2 10) 编译展开成： " (macroexpand-1 '(pow-of-2 10)))
  (println "  运行时直接拿到常量： " (pow-of-2 10))
  (assert (= 1024 (pow-of-2 10)))
  ;; 运行期值不能传：(pow-of-2 (read)) 会编译失败
  )

;; ───────────────────────────────────────────────────────────────────────
;; section 2: &env 探测 —— 宏看到当前 lexical scope 中的 locals
;; ───────────────────────────────────────────────────────────────────────
(defmacro who-is-here
  "打印调用点处可见的 local symbols（编译期）"
  []
  `(prn :compile-time-locals '~(vec (keys &env))))

(defn section-2-env []
  (println "\n── section 2: &env 探测 lexical scope ──")
  (let [a 1 b 2 c 3]
    (who-is-here)                               ;; 应打印 [a b c]
    (let [d 4]
      (who-is-here)))                           ;; 应打印 [a b c d]
  )

;; ───────────────────────────────────────────────────────────────────────
;; section 3: &form 拿到调用点元数据 —— 实战用于报错
;; ───────────────────────────────────────────────────────────────────────
(defmacro must-be-positive
  "如果 n 不是字面量正整数，抛出带 line/column 的编译错误"
  [n]
  (let [{:keys [line column]} (meta &form)]
    (cond
      (not (integer? n))
      (throw (ex-info (format "must-be-positive 期望整数字面量，得到 %s 在 line %s col %s"
                              (class n) line column)
                      {:form &form}))
      (not (pos? n))
      (throw (ex-info (format "must-be-positive 要求 > 0，得到 %d 在 line %s col %s"
                              n line column)
                      {:form &form}))
      :else
      n)))

(defn section-3-form-meta []
  (println "\n── section 3: &form 拿到行号 / 编译期检查 ──")
  (println "  正常调用：" (must-be-positive 42))
  (assert (= 42 (must-be-positive 42)))

  ;; 反例只能用 eval（直接写非整数会编译失败）
  (println "  非法调用 (must-be-positive -1) 编译期错：")
  (try
    (eval '(must-be-positive -1))
    (catch Throwable t
      (println "  捕获：" (or (ex-message (ex-cause t)) (ex-message t))))))

;; ───────────────────────────────────────────────────────────────────────
;; section 4: macroexpand 三层 —— 看清宏展开的真实形态
;; ───────────────────────────────────────────────────────────────────────
(defmacro when-not-empty
  "拼一个 (when (seq coll) body...)"
  [coll & body]
  `(when (seq ~coll)
     ~@body))

(defn section-4-macroexpand []
  (println "\n── section 4: macroexpand-1 / macroexpand / clojure.walk ──")
  (let [form '(when-not-empty xs (println :got xs) (count xs))]
    (println "  原 form  ：" form)
    (println "  expand-1 ：" (macroexpand-1 form))    ;; 只展开外层一层
    (println "  expand   ：" (macroexpand form))       ;; 重复展开直到不变
    ;; 注：macroexpand 只展外层；要全树展开用 walk
    (println "  walk     ：" (clojure.walk/macroexpand-all form))))

;; ───────────────────────────────────────────────────────────────────────
;; section 5: 综合 —— 编译期 schema 检查 + 运行时类型保留
;; ───────────────────────────────────────────────────────────────────────
(defmacro typed-let
  "(typed-let [name :string  age :int]  body...)
   编译期检查类型 keyword 是否合法；运行期不做检查（零成本）"
  [bindings & body]
  (let [pairs (partition 2 bindings)
        valid-types #{:string :int :double :boolean :keyword}]
    (doseq [[sym tk] pairs]
      (when-not (valid-types tk)
        (throw (ex-info (format "typed-let: 未知类型 %s（仅支持 %s）"
                                tk valid-types)
                        {:sym sym :given tk}))))
    ;; 输出：(let [name nil age nil] body...)（仅占位，实际场景会编译期注入 deftype 等）
    `(let [~@(mapcat (fn [[sym _]] [sym nil]) pairs)]
       ~@body)))

(defn section-5-typed-let []
  (println "\n── section 5: 编译期 schema 检查 ──")
  (typed-let [n :int s :string]                 ;; 合法
    (println "  合法 typed-let 通过编译"))
  (try
    (eval '(demo47/typed-let [x :foo] :body))   ;; :foo 非法
    (catch Throwable t
      (println "  ✓ 编译期捕获：" (or (ex-message (ex-cause t)) (ex-message t))))))

(defn -main [& _]
  (section-1-compile-time-const)
  (section-2-env)
  (section-3-form-meta)
  (section-4-macroexpand)
  (section-5-typed-let)
  (println "\n=== 一句话总结 ===")
  (println "- 宏在编译期跑：常量折叠 / schema 校验 / 错误带行号 全靠它")
  (println "- &env：local symbols 集合，可探测当前 scope")
  (println "- &form：当前 form 含 metadata（line/column），用于精确报错")
  (println "- macroexpand-1 单层 / macroexpand 重复至不变 / walk/macroexpand-all 全树")
  (println "- 编译期检查 + 运行期零开销 = Clojure 宏在 DSL/性能敏感路径的杀手锏")
  (shutdown-agents))

(-main)
