;; ============================================================
;; Clojure Demo 13 — Reader Macros：读时宏（# 系列符号）
;; ============================================================
;; Clojure 有两层"宏"：
;;   1. 编译期宏（defmacro）：语法树 → 语法树
;;   2. 读时宏（reader macros）：字符 → 数据；在 read 阶段就生效
;;
;; 读时宏不能用户自定义（只有官方钦定），但日常每天在用：
;;   #(...)   ：匿名函数 lambda
;;   #_x      ：丢弃下一个 form（注释代码用）
;;   #?(...)  ：cljc 跨平台条件读取（Clojure / ClojureScript）
;;   #=(...)  ：读时求值（高危，默认禁用，只有信任的源开启）
;;   #'sym    ：var 引用
;;   #"regex" ：正则字面量
;;   #inst    ：日期字面量
;;   #uuid    ：UUID 字面量
;;
;; 运行：clojure -M clojure/13_reader_macros.clj
;; ============================================================

(println "=== 1. #() 匿名函数 ===")
;; #(...) 等价于 (fn [%] ...)；%、%1、%2、%& 是参数占位符
(println "((fn [x] (* x 2)) 5)         =" ((fn [x] (* x 2)) 5))
(println "(#(* % 2) 5)                 =" (#(* % 2) 5)         "（同上，更短）")
(println "(#(+ %1 %2 %3) 1 2 3)        =" (#(+ %1 %2 %3) 1 2 3))
(println "(#(apply + %&) 1 2 3 4 5)    =" (#(apply + %&) 1 2 3 4 5))

;; 限制：#() 不能嵌套
;; (#(#(+ % %) 5) 10)   ;; ❌ 编译错误
(println "限制：#() 不能嵌套，嵌套就老老实实 (fn [...])")

(println "\n=== 2. #_ 丢弃下一个 form：调试好帮手 ===")
;; 比注释 ;; 强：丢弃整个 form，不是行级
(println "(+ 1 #_ (println \"会跑吗？\") 2 3)")
(println "  实际值 =" (+ 1 #_ (println "我不会跑") 2 3))
(println "  → \"我不会跑\" 没输出，被 #_ 整体吃掉")

;; 嵌套：#_#_ 丢弃两个，#_#_#_ 丢弃三个
(println "\n#_ 可以叠加：(+ 1 #_#_ 2 3 4) → 把 2 和 3 都丢掉")
(println "  =" (+ 1 #_#_ 2 3 4))

(println "\n=== 3. #\"regex\" 正则字面量 ===")
(let [pat #"\d+"]
  (println "pat                 =" pat "  (类型 =" (class pat) ")")
  (println "(re-find pat \"abc123def\") =" (re-find pat "abc123def"))
  (println "(re-seq  pat \"a1 b22 c333\") =" (re-seq pat "a1 b22 c333")))

(println "\n=== 4. #inst / #uuid 字面量 ===")
;; #inst 直接读取 RFC 3339 时间字符串
(let [t #inst "2024-06-15T08:30:00Z"
      u #uuid "12345678-1234-5678-1234-567812345678"]
  (println "#inst 类型 =" (class t) "  值 =" t)
  (println "#uuid 类型 =" (class u) "  值 =" u))

(println "\n=== 5. #' var 引用 ===")
;; 'foo 拿到符号；#'foo 拿到 var 对象本身（带元数据）
(defn greet [name] (str "Hi, " name))

(println "'greet            =" 'greet            "  (符号)")
(println "#'greet           =" #'greet           "  (var 对象)")
(println "(meta #'greet)    =")
(doseq [[k v] (select-keys (meta #'greet) [:name :ns :file :arglists])]
  (println " " k "→" v))

(println "\n=== 6. #?(:clj ...) 跨平台条件读取（仅 .cljc 文件） ===")
;; ⚠️ 限制：#? 只允许出现在 .cljc 文件里。在 .clj/.cljs 中直接写会报
;;        "Conditional read not allowed"。
;; 我们这是 .clj 文件，所以用 read-string + 选项手动演示
(let [src "#?(:clj  :running-on-jvm
              :cljs :running-on-js
              :default :unknown)"]
  (println "源代码：" src)
  (println "用 read-string + :read-cond :allow 解析：")
  (println "  结果 =" (read-string {:read-cond :allow} src))
  (println "（在 JVM 上 :clj 分支命中；同样代码在浏览器上 :cljs 命中）"))

(println "\n=== 7. read-string：手动调用 reader ===")
;; reader 通常隐藏在 load 流程里；read-string 让你手动看 reader 输出
(println "(read-string \"#(* % 2)\") =" (read-string "#(* % 2)"))
(println "  → 字符串被 reader 转成了一个 fn* form")
(println "(read-string \"#\\\"\\\\d+\\\"\") =" (read-string "#\"\\d+\""))
(println "  → 字符串被 reader 转成了真正的 java.util.regex.Pattern")

(println "\n=== 8. 危险品：#=( ) 读时求值 ===")
;; #=(...) 在 read 阶段就 eval；意味着读个文件就能执行任意代码
;; 默认 *read-eval* = false 时禁用（防御反序列化攻击）
(binding [*read-eval* true]
  (println "(read-string \"#=(+ 1 2)\") =" (read-string "#=(+ 1 2)")))
(println "（生产环境永远 *read-eval* false，否则 EDN 反序列化 = RCE 漏洞）")

(println "\n=== 一句话总结 ===")
(println "- 读时宏 = 在 read 阶段把字符串转成数据，用户不能自定义")
(println "- #()/#_/#?/#\"\" 是日常每天在用")
(println "- #=(...) 默认禁用，是 reader 不被信任输入污染的安全开关")
