;; ============================================================
;; Clojure Demo 12 — Macros State Machine：用宏定义状态机
;; ============================================================
;; 目标：让用户用声明式 DSL 定义状态机，宏在编译期生成转移表 + 运行函数
;;
;; 用户期望的 API：
;;     (defmachine traffic-light
;;       :initial :red
;;       :transitions
;;       {:red    {:next :green}
;;        :green  {:next :yellow}
;;        :yellow {:next :red}})
;;
;; 编译后效果：
;;   - 自动生成 init / transition / valid? 等函数
;;   - 编译期检查：转移目标必须是已定义的状态
;;
;; 这正是 Erlang gen_statem、Elixir router 宏的同款套路。
;;
;; 运行：clojure -M clojure/12_macros_state_machine.clj
;; ============================================================

;; --- 状态机定义宏 ---

(defmacro defmachine
  "定义一个状态机。
   options 接受：:initial 初始状态  :transitions 转移图

   编译期会做：
   1. 检查所有转移目标都是已声明状态
   2. 生成两个函数：<name>-init / <name>-step"
  [name & {:keys [initial transitions]}]
  ;; 编译期检查：所有转移的目标 state 必须出现在 keys 里
  (let [valid-states (set (keys transitions))]
    (doseq [[from edges] transitions
            [_evt to] edges]
      (when-not (valid-states to)
        (throw (ex-info
                (format "状态机 %s：从 %s 转移到未声明状态 %s"
                        name from to)
                {:from from :to to :known valid-states})))))
  (let [init-name (symbol (str name "-init"))
        step-name (symbol (str name "-step"))]
    `(do
       (defn ~init-name [] ~initial)
       (defn ~step-name [state# event#]
         (let [transitions# ~transitions]
           (or (get-in transitions# [state# event#])
               (throw (ex-info
                       (format "状态 %s 不接受事件 %s"
                               state# event#)
                       {:state state# :event event#})))))
       (def ~name {:initial ~initial
                   :transitions ~transitions
                   :init  ~init-name
                   :step  ~step-name}))))

;; --- 用宏定义两个状态机 ---

(println "=== 1. 定义红绿灯 ===")
(defmachine traffic-light
  :initial :red
  :transitions
  {:red    {:next :green}
   :green  {:next :yellow}
   :yellow {:next :red}})

(println "初始：" (traffic-light-init))
(let [s0 (traffic-light-init)
      s1 (traffic-light-step s0 :next)
      s2 (traffic-light-step s1 :next)
      s3 (traffic-light-step s2 :next)]
  (println "step :next ×3 =" s0 "→" s1 "→" s2 "→" s3))

(println "\n=== 2. 订单状态机：多事件分支 ===")
(defmachine order
  :initial :pending
  :transitions
  {:pending   {:pay :paid     :cancel :cancelled}
   :paid      {:ship :shipped :refund :refunded}
   :shipped   {:deliver :delivered}
   :delivered {}
   :cancelled {}
   :refunded  {}})

(let [s0 :pending
      s1 (order-step s0 :pay)
      s2 (order-step s1 :ship)
      s3 (order-step s2 :deliver)]
  (println "正常路径：" s0 "→" s1 "→" s2 "→" s3))

(let [s0 :pending
      s1 (order-step s0 :cancel)]
  (println "取消路径：" s0 "→" s1))

(println "\n=== 3. 运行期：非法事件抛错 ===")
(print "  从 :delivered 试图 :ship → ")
(try
  (order-step :delivered :ship)
  (catch Exception e (println "✅ 抛错：" (ex-message e))))

(print "  从 :pending 试图 :ship（必须先 pay）→ ")
(try
  (order-step :pending :ship)
  (catch Exception e (println "✅ 抛错：" (ex-message e))))

(println "\n=== 4. 编译期：非法转移目标，宏会拒绝编译 ===")
(println "尝试定义有未声明状态的状态机：")
(try
  (eval '(defmachine broken
           :initial :a
           :transitions
           {:a {:go :b}                       ;; :b 没声明，应该被宏检查抓出
            :c {:go :a}}))
  (println "  （不应到这里）")
  (catch Exception e
    ;; Clojure 把宏期异常包了一层 "Syntax error macroexpanding..."
    ;; 真正的根因在 ex-cause 里
    (let [root (or (ex-cause e) e)]
      (println "  ✅ 编译期拒绝：" (ex-message root)))))

(println "\n=== 5. 看宏展开 ===")
(println "(defmachine traffic-light ...) 展开为：")
(let [expanded (macroexpand-1
                '(defmachine x
                   :initial :a
                   :transitions {:a {:e :b} :b {}}))]
  (doseq [form expanded]
    (println " " form)))

(println "\n=== 一句话总结 ===")
(println "- 宏在编译期能跑任意 Clojure 代码 → 可做静态检查")
(println "- 状态机 / 路由 / GraphQL schema 这类\"声明式定义\"是宏的甜点场景")
(println "- 编译期拒绝错误优于运行期：feedback loop 短一个量级")
