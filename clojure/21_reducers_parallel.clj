;; ============================================================
;; Clojure Demo 21 — Reducers：基于 Fork/Join 的并行集合操作
;; ============================================================
;; reducers 和 transducers 是两个东西，别搞混：
;;   transducer (demo 03)：组合性，跑在任意 reducible 上，单线程
;;   reducer    (本 demo) ：基于 Java fork/join，自动把工作切片到多核
;;
;; 核心 API：
;;   r/map / r/filter ：返回的是 reducer 不是 seq（不可 take/take-last）
;;   r/fold           ：并行 reduce；要求归并函数满足结合律
;;   r/reduce         ：单线程 reduce（不并行，但比 seq 链更快——绕开中间集合）
;;
;; 何时用 reducers vs pmap vs core.async pipeline？
;;   reducers       ：纯计算 + 大集合 + 操作可结合，CPU 密集型聚合的最优
;;   pmap           ：每元素是独立慢操作（IO/外部调用），不要求保序
;;   pipeline       ：CPU 计算流水线，需要保序输出
;;   pipeline-blocking：每元素是阻塞 IO（HTTP/DB），要保序
;;
;; 运行：clojure -M clojure/21_reducers_parallel.clj
;; ============================================================

(require '[clojure.core.reducers :as r])

(println "=== 1. r/map + r/reduce：单线程但不产生中间集合 ===")
;; 普通 (reduce + (map f coll)) 会先 map 一遍生成新 seq，再 reduce
;; (r/reduce + (r/map f coll)) 中间不生成集合，零拷贝
(let [data (vec (range 1 1000001))
      t0 (System/nanoTime)
      seq-result   (reduce + (map #(* % %) data))
      t1 (System/nanoTime)
      red-result   (r/reduce + (r/map #(* % %) data))
      t2 (System/nanoTime)]
  (println "结果一致？" (= seq-result red-result))
  (println (format "seq map+reduce  = %.0f ms" (/ (- t1 t0) 1e6)))
  (println (format "r/map + r/reduce = %.0f ms" (/ (- t2 t1) 1e6))))

(println "\n=== 2. r/fold：并行 reduce，自动 fork/join ===")
;; r/fold 要求两个东西：
;;   - reducef ：把单元素加进累加器（如 +）
;;   - combinef：把两个累加器合并（也是 +；必须满足结合律）
;; 当只有一个函数（满足两种角色）时，可以只传一次
(let [data (vec (range 1 10000001))          ;; 1000 万
      t0 (System/nanoTime)
      seq-result   (reduce + data)
      t1 (System/nanoTime)
      fold-result  (r/fold + data)
      t2 (System/nanoTime)]
  (println "数据量 =" (count data))
  (println "结果一致？" (= seq-result fold-result))
  (println (format "单线程 reduce = %.0f ms" (/ (- t1 t0) 1e6)))
  (println (format "并行 r/fold   = %.0f ms" (/ (- t2 t1) 1e6))))

(println "\n=== 3. r/fold 的两个函数版本：求平均 ===")
;; 求平均要同时记 sum 和 count，需要自定义合并函数
(let [data (vec (range 1 1000001))
      ;; combinef: 合并两个 [sum count]
      combinef (fn
                 ([] [0 0])                  ;; 0 元的恒等元
                 ([[s1 c1] [s2 c2]] [(+ s1 s2) (+ c1 c2)]))
      ;; reducef: 单元素累加
      reducef  (fn [[s c] x] [(+ s x) (inc c)])
      [sum cnt] (r/fold combinef reducef data)]
  (println "sum =" sum "  count =" cnt)
  (println "平均 =" (double (/ sum cnt))))

(println "\n=== 4. r/fold + r/map + r/filter：复合并行管道 ===")
(let [data (vec (range 1 1000001))            ;; 1e6（再大平方求和会 long 溢出）
      t0 (System/nanoTime)
      ;; 找出所有"平方后是 7 倍数"的数，求和
      result (->> data
                  (r/map #(* % %))
                  (r/filter #(zero? (mod % 7)))
                  (r/fold +))
      ms (/ (- (System/nanoTime) t0) 1e6)]
  (println "1..1e6 平方后 7 倍数之和 =" result)
  (println (format "并行总耗时 %.0f ms" ms)))

(println "\n=== 5. r/foldcat：并行收集成 vector ===")
;; r/foldcat 的归并函数是把两段 vector 拼起来
;; 注意返回的是一种特殊的 vector-like 结构（cat），但和 vector 行为一致
(let [data (vec (range 1 100001))
      result (->> data
                  (r/filter even?)
                  (r/map #(* % 10))
                  r/foldcat)]
  (println "前 5 个：" (take 5 result))
  (println "总数：" (count result)))

(println "\n=== 6. 控制并行粒度：n 参数 ===")
;; r/fold 默认每片 512 元素；可以显式指定
;; 太小：调度成本盖过收益；太大：并行不充分
(let [data (vec (range 1 1000001))            ;; 1e6
      bench (fn [n]
              (let [t0 (System/nanoTime)]
                (dotimes [_ 3] (r/fold n + +  data))
                (/ (- (System/nanoTime) t0) 1e6 3)))]
  (doseq [n [128 512 4096 65536]]
    (println (format "  片大小 %6d → %.0f ms" n (bench n)))))

(println "\n=== 7. 对比 pmap：粒度太细的反例 ===")
;; pmap 每元素都启线程级别开销，对纯算术反而比单线程慢
(let [data (vec (range 1 1000001))
      t0 (System/nanoTime)
      _ (doall (pmap #(* % %) data))
      ms-pmap (/ (- (System/nanoTime) t0) 1e6)
      t1 (System/nanoTime)
      _ (mapv #(* % %) data)
      ms-mapv (/ (- (System/nanoTime) t1) 1e6)]
  (println (format "pmap 1e6 个平方 = %.0f ms（线程开销大）" ms-pmap))
  (println (format "mapv 同样      = %.0f ms（更快）" ms-mapv))
  (println "→ pmap 适合\"每元素本身耗时\"，不适合纯算术"))

(println "\n=== 一句话总结 ===")
(println "- r/reduce ：单线程，省去中间集合（比 reduce+map 快）")
(println "- r/fold   ：并行版，需要满足结合律的归并函数")
(println "- r/foldcat：并行收集成 vector")
(println "- pmap     ：每元素独立慢操作时用；纯算术别用")

;; future 和 fork/join 的 worker 是 daemon，但保险起见
(shutdown-agents)
