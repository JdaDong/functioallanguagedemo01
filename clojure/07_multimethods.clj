;; ============================================================
;; Clojure Demo 07 — Multimethods：开放式多态分派
;; ============================================================
;; 关键事实：
;;   - defmulti 定义"分派函数"：输入任意数据 → 输出 dispatch value
;;   - defmethod 给不同的 dispatch value 挂不同实现
;;   - 关键词"开放"：任何人可以随时再挂一个 defmethod，不用改原文件
;;     （对比 OOP 的 switch / 模式匹配：要在同一处写死所有分支）
;;   - 分派可以基于任意东西：类型、字段值、自定义规则、derive 层级
;;
;; 运行：clojure -M clojure/07_multimethods.clj
;; ============================================================

(println "=== 1. 最基础：按 :shape 关键字分派 ===")

(defmulti area :shape)   ;; 分派函数 = 取 :shape 字段

(defmethod area :square [{:keys [side]}]
  (* side side))

(defmethod area :rectangle [{:keys [w h]}]
  (* w h))

(defmethod area :circle [{:keys [r]}]
  (* Math/PI r r))

(defmethod area :default [shape]
  (println "不认识的形状：" shape)
  0)

(println "square 4x4     =" (area {:shape :square :side 4}))
(println "rectangle 3x5  =" (area {:shape :rectangle :w 3 :h 5}))
(println "circle r=1     =" (format "%.4f" (area {:shape :circle :r 1})))
(println "未知           =" (area {:shape :blob}))

(println "\n=== 2. 开放扩展：在\"事后\"新增一种形状，不用改任何已有代码 ===")
(defmethod area :triangle [{:keys [base h]}]
  (/ (* base h) 2.0))

(println "triangle b=6 h=4 =" (area {:shape :triangle :base 6 :h 4}))

(println "\n=== 3. 分派可以不只是字段值：按 (type x) 分派 ===")
(defmulti describe type)

(defmethod describe String  [s] (str "字符串长 " (count s)))
(defmethod describe Long    [n] (str "整数 " n (if (neg? n) " (负)" " (非负)")))
(defmethod describe clojure.lang.PersistentVector [v] (str "向量含 " (count v) " 个元素"))
(defmethod describe :default [x] (str "其他：" (class x)))

(println (describe "hello"))
(println (describe 42))
(println (describe [:a :b :c]))
(println (describe 3.14))

(println "\n=== 4. 组合分派：按 [输入类型, 输出类型] 两个维度 ===")
(defmulti convert (fn [from to _value] [from to]))

(defmethod convert [:celsius :fahrenheit] [_ _ c]
  (+ (* c 9/5) 32))
(defmethod convert [:fahrenheit :celsius] [_ _ f]
  (* (- f 32) 5/9))
(defmethod convert [:km :mile] [_ _ k]
  (* k 0.621371))

(println "100°C → °F =" (convert :celsius :fahrenheit 100))   ;; 212
(println " 32°F → °C =" (convert :fahrenheit :celsius 32))     ;; 0
(println " 10km → mi =" (convert :km :mile 10))

(println "\n=== 5. derive 层级：给分派加\"继承\" ===")
;; 用 derive 建立 keyword 之间的父子关系，方法可以按父节点匹配
(derive ::cat ::animal)
(derive ::dog ::animal)
(derive ::goldfish ::animal)

(defmulti sound identity)     ;; identity 分派：x 自己就是 dispatch value
(defmethod sound ::animal [_] "某种声音")
(defmethod sound ::cat [_] "喵")
(defmethod sound ::dog [_] "汪")
;; 没给 goldfish 单独实现 → 会走 ::animal

(println "cat      →" (sound ::cat))
(println "dog      →" (sound ::dog))
(println "goldfish →" (sound ::goldfish) "（走 ::animal 的默认实现）")

(println "\n=== 一句话总结 ===")
(println "- 用 multimethod 写\"可能以后要新增一类\"的逻辑，别写 case/cond 大全")
(println "- 分派函数可以任意计算：字段、类型、组合、层级")
(println "- 代价：性能略低于 protocol（下一 demo），但灵活度换得值")
