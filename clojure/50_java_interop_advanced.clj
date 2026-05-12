;; demo 50 — Java 互操作进阶：proxy / reify java interface / .. / doto / 类型 hint
;; 零依赖（用 java.* 标准库）。
;; 运行：clojure -M 50_java_interop_advanced.clj

(ns demo50)

;; ───────────────────────────────────────────────────────────────────────
;; 心智锚：Clojure 跑在 JVM 上，互操作是工业刚需。
;;   入门级：(.method obj args)、(Klass/staticMethod)、(Klass.) 构造
;;   进阶：
;;     - reify  ：实现 Java interface 做匿名实例
;;     - proxy  ：扩展 Java 类（被广泛误用，能用 reify 不要用 proxy）
;;     - ..     ：链式调用糖
;;     - doto   ：连续无返回值方法
;;     - 类型 hint ^Type 防反射，热路径必备
;;
;; demo 47 demo 48 略过 Java 互操作；本 demo 集中演示。
;; ───────────────────────────────────────────────────────────────────────

;; ───────────────────────────────────────────────────────────────────────
;; section 1: 基础语法回顾
;; ───────────────────────────────────────────────────────────────────────
(defn section-1-basics []
  (println "── section 1: 基础语法 ──")
  ;; 静态方法
  (println "  Math/PI =" Math/PI)
  (println "  (Math/sqrt 2) =" (Math/sqrt 2))
  ;; 构造 + 实例方法
  (let [sb (StringBuilder. "hello")]
    (.append sb " world")
    (.reverse sb)
    (println "  StringBuilder.reverse →" (str sb)))
  (assert (= 25 (.length (StringBuilder. "0123456789012345678901234"))))

  ;; .. 链式糖：(.. x m1 m2) = (.m2 (.m1 x))
  (println "  (.. \"hello\" toUpperCase reverse) =" (.. "hello" toUpperCase (toString)))
  ;; doto：连续无返回值方法（每个返回 obj 给下一步）
  (let [m (doto (java.util.HashMap.)
            (.put "a" 1)
            (.put "b" 2)
            (.put "c" 3))]
    (println "  doto HashMap：" (into {} m))
    (assert (= 3 (count m)))))

;; ───────────────────────────────────────────────────────────────────────
;; section 2: reify Java interface —— 匿名实现 Comparator / Runnable
;; ───────────────────────────────────────────────────────────────────────
(defn section-2-reify-iface []
  (println "\n── section 2: reify 实现 Java interface ──")
  ;; 用 reify 实现 java.util.Comparator，按字符串长度排
  (let [by-length (reify java.util.Comparator
                    (compare [_ a b] (- (count a) (count b))))
        sorted (sort by-length ["xxxxx" "x" "xxx" "xx"])]
    (println "  按长度排：" sorted)
    (assert (= ["x" "xx" "xxx" "xxxxx"] sorted)))

  ;; 用 reify 实现 java.lang.Runnable，在线程里跑
  (let [done (atom false)
        r (reify Runnable (run [_] (reset! done :ok)))
        t (Thread. ^Runnable r)]
    (.start t)
    (.join t)
    (println "  Runnable 跑完，done =" @done)
    (assert (= :ok @done))))

;; ───────────────────────────────────────────────────────────────────────
;; section 3: 类型 hint 防反射 —— 性能差异实测
;; ───────────────────────────────────────────────────────────────────────
(set! *warn-on-reflection* true)              ;; 开反射警告

(defn slow-len [s] (.length s))                ;; ⚠️ 编译期会有反射警告
(defn fast-len [^String s] (.length s))        ;; ^String hint 消除反射

(set! *warn-on-reflection* false)

(defn section-3-type-hint []
  (println "\n── section 3: 类型 hint ^String 消除反射 ──")
  (let [n      1000000
        s      "hello world"
        warm   (dotimes [_ 1000] (slow-len s) (fast-len s))

        t1 (System/nanoTime)
        _  (dotimes [_ n] (slow-len s))
        ms1 (/ (- (System/nanoTime) t1) 1e6)

        t2 (System/nanoTime)
        _  (dotimes [_ n] (fast-len s))
        ms2 (/ (- (System/nanoTime) t2) 1e6)]
    (println (format "  无 hint  (反射) 1e6 次：%.1f ms" ms1))
    (println (format "  有 hint        1e6 次：%.1f ms" ms2))
    (println (format "  加速比 %.1fx" (/ ms1 ms2)))
    (assert (< ms2 ms1))))

;; ───────────────────────────────────────────────────────────────────────
;; section 4: proxy —— 扩展 Java 类（少用，但有时不可避免）
;;   reify 只能实现 interface，不能扩展 class
;;   要扩展 abstract class，只能 proxy
;; ───────────────────────────────────────────────────────────────────────
(defn section-4-proxy []
  (println "\n── section 4: proxy 扩展 abstract class ──")
  ;; 扩展 java.io.Writer（abstract class），把所有写都收集到 atom
  (let [collected (atom (StringBuilder.))
        w (proxy [java.io.Writer] []
            (write [^chars cs off len]
              (.append ^StringBuilder @collected
                       (String. ^chars cs ^int off ^int len)))
            (flush [])
            (close []))]
    (let [chunk1 (char-array "hello")
          chunk2 (char-array " world")
          ^java.io.Writer w w]
      (.write w chunk1 (int 0) (int (alength chunk1)))
      (.write w chunk2 (int 0) (int (alength chunk2)))
      (.flush w)
      (.close w))
    (println "  proxy Writer 收到：" (str @collected))
    (assert (= "hello world" (str @collected)))))

;; ───────────────────────────────────────────────────────────────────────
;; section 5: 异常互操作 —— Java 异常 = Clojure ex-info 共生
;; ───────────────────────────────────────────────────────────────────────
(defn section-5-exceptions []
  (println "\n── section 5: 异常互操作 ──")
  ;; catch Java 类异常
  (try
    (Long/parseLong "not-a-num")
    (catch NumberFormatException e
      (println "  caught NumberFormatException：" (.getMessage e))))

  ;; catch ex-info（Clojure 自家）
  (try
    (throw (ex-info "domain error" {:user-id 42}))
    (catch clojure.lang.ExceptionInfo e
      (println "  caught ex-info： msg =" (ex-message e)
               "  data =" (ex-data e))))

  ;; finally 仍然按 Java 语义执行
  (let [cleanup (atom 0)]
    (try
      (try
        (throw (ex-info "boom" {}))
        (finally (swap! cleanup inc)))
      (catch Exception _ nil))
    (println "  finally 执行 1 次，cleanup =" @cleanup)
    (assert (= 1 @cleanup))))

(defn -main [& _]
  (section-1-basics)
  (section-2-reify-iface)
  (section-3-type-hint)
  (section-4-proxy)
  (section-5-exceptions)
  (println "\n=== 一句话总结 ===")
  (println "- (.method obj) / (Klass/static) / (Klass.) 构造：互操作三件套")
  (println "- reify > proxy：能 reify 实现接口就别 proxy 扩展类")
  (println "- 类型 hint ^String / ^int 防反射，热路径性能差距 5-10×")
  (println "- doto + .. 让连续 setter / fluent API 写起来不丑")
  (println "- 异常：catch Java class 或 clojure.lang.ExceptionInfo 互通")
  (shutdown-agents))

(-main)
