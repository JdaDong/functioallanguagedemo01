;; ============================================================
;; Clojure Demo 08 — Protocols & Records：Clojure 版 typeclass
;; ============================================================
;; 关键事实：
;;   - defprotocol 声明一组操作（像接口 / typeclass）
;;   - defrecord 是"带字段的 map"：既能像 map 一样 assoc/get，
;;     也能高性能地实现 protocol
;;   - protocol 比 multimethod 快很多（走 JVM 单派的 polymorphic inline cache）
;;   - 选型：单一维度分派（看 this 的类型）→ protocol；多维或自定义规则 → multimethod
;;
;; 运行：clojure -M clojure/08_protocols_and_records.clj
;; ============================================================

(println "=== 1. 定义 protocol：一组操作 ===")
(defprotocol Shape
  "几何形状的通用操作"
  (area [this]  "面积")
  (perim [this] "周长"))

(println "Shape 协议方法 =" (keys (:sigs Shape)))

(println "\n=== 2. defrecord：给 protocol 挂实现 ===")
(defrecord Square [side]
  Shape
  (area  [_] (* side side))
  (perim [_] (* 4 side)))

(defrecord Rectangle [w h]
  Shape
  (area  [_] (* w h))
  (perim [_] (* 2 (+ w h))))

(defrecord Circle [r]
  Shape
  (area  [_] (* Math/PI r r))
  (perim [_] (* 2 Math/PI r)))

(def shapes [(->Square 4)
             (->Rectangle 3 5)
             (->Circle 1)])

(doseq [s shapes]
  (println (format "%-10s area=%8.4f  perim=%8.4f"
                   (.getSimpleName (class s))
                   (double (area s))
                   (double (perim s)))))

(println "\n=== 3. record 同时是 map：可以 assoc / :key / 解构 ===")
(def sq (->Square 10))
(println "record sq      =" sq)
(println "(:side sq)     =" (:side sq))
(println "assoc 加字段    =" (assoc sq :color :red))
(println "  → 仍能调用 area：" (area (assoc sq :color :red)))
(println "(map? sq)      =" (map? sq) "  ← record 也是 map")

(println "\n=== 4. 事后给已有类型挂 protocol：extend-protocol ===")
;; 比如想让所有 Number 都能被当作\"一维形状\"：area = |x|，perim = |x|*2
(extend-protocol Shape
  Number
  (area  [n] (Math/abs (double n)))
  (perim [n] (* 2 (Math/abs (double n)))))

(println "area of 7   =" (area 7))
(println "area of -3  =" (area -3))
(println "perim of 5  =" (perim 5))

(println "\n=== 5. 多态派发实战：一个函数处理所有 Shape ===")
(defn ratio
  "面积 / 周长 —— 反映形状的\"紧凑程度\"（圆最大）"
  [s]
  (/ (double (area s)) (double (perim s))))

(doseq [s [(->Square 4) (->Rectangle 3 5) (->Circle 1) 10]]
  (println (format "ratio(%s) = %.4f" (pr-str s) (ratio s))))

(println "\n=== 6. 性能直观：protocol vs multimethod ===")
;; 同一个 :area 逻辑，两种实现，各跑 1000 万次
(defmulti area-mm type)
(defmethod area-mm Square    [{:keys [side]}] (* side side))
(defmethod area-mm Rectangle [{:keys [w h]}] (* w h))

(def bench-sq (->Square 7))

(defn bench [label f]
  (dotimes [_ 2]                         ;; 预热
    (let [t0 (System/nanoTime)
          _  (loop [i 0 acc 0]
               (if (< i 10000000)
                 (recur (inc i) (+ acc (long (f bench-sq))))
                 acc))
          ms (/ (- (System/nanoTime) t0) 1e6)]
      (println (format "  %-20s %.2f ms / 1000 万次" label ms)))))

(println "protocol 版：")
(bench "area (protocol)" area)

(println "multimethod 版：")
(bench "area-mm (multi)" area-mm)

(println "\n=== 一句话总结 ===")
(println "- protocol = 更快、更扁平的类型派发，record 是最常见的\"带协议实现的 map\"")
(println "- multimethod = 更灵活（任意分派函数），选型看\"分派维度\"是否单一")
(println "- extend-protocol 让你事后给 String/Number/Java 类 \"续命\"，不改原类型")
