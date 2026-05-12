(ns demo20
  "core.async Pipeline & Transducers on Channels：构建可扩展的事件流。

   运行：clojure -M:run"
  (:require [clojure.core.async :as a
             :refer [chan thread <!! >!! close!
                     pipe pipeline pipeline-blocking
                     mult tap onto-chan!!]]))

(defn drain
  "把 channel 里的所有值收成 vector（同步阻塞）"
  [ch]
  (<!! (a/into [] ch)))

(defn -main [& _]
  (println "=== 1. pipe：把一个 channel 接到另一个 ===")
  ;; 最简管道：上游关闭，下游也跟着关闭
  (let [in  (chan 5)
        out (chan 5)]
    (pipe in out)
    (onto-chan!! in [1 2 3 4 5])             ;; 自动 close in
    (println "下游收到：" (drain out)))

  (println "\n=== 2. transducer 挂在 channel 上 ===")
  ;; chan 第二个参数是 transducer：数据进 channel 时即被转换
  (let [c (chan 10 (comp (map #(* % %))
                         (filter even?)))]
    (onto-chan!! c (range 1 11))
    (println "1..10 平方后偶数：" (drain c)))

  (println "\n=== 3. pipeline：N 个并行 worker，结果按顺序输出 ===")
  ;; 关键不变量：即使 worker 处理速度不同，输出顺序 = 输入顺序
  (let [in  (chan 100)
        out (chan 100)
        n-workers 4]
    (pipeline n-workers
              out
              (map (fn [x]
                     ;; 模拟变化的处理时间
                     (Thread/sleep (rand-int 5))
                     {:input x :squared (* x x)}))
              in)
    (onto-chan!! in (range 20))
    (let [results (drain out)]
      (println "20 个输入，结果数 =" (count results))
      (println "前 5 个 input："  (mapv :input    (take 5 results)))
      (println "顺序保留？" (= (mapv :input results) (range 20)))))

  (println "\n=== 4. pipeline-blocking：IO 密集时的正确选择 ===")
  ;; 模拟"调外部 HTTP 接口"，每个 100ms
  (let [in  (chan 100)
        out (chan 100)
        fake-http (fn [url]
                    (Thread/sleep 100)       ;; 模拟网络
                    (str url " → 200"))
        t0 (System/nanoTime)]
    (pipeline-blocking 8                     ;; 8 并发
                       out
                       (map fake-http)
                       in)
    (onto-chan!! in (mapv #(str "/api/" %) (range 16)))
    (let [results (drain out)
          ms (/ (- (System/nanoTime) t0) 1e6)]
      (println "16 个 HTTP 调用，8 并发，总耗时" (long ms) "ms")
      (println "（顺序需 1600ms，8 并发约 200ms）")
      (println "示例：" (take 3 results))))

  (println "\n=== 5. mult / tap：广播一份数据到多个消费者 ===")
  ;; fan-out 的标准做法：所有 tap 都收到完整副本
  (let [src (chan 10)
        m   (mult src)
        out-a (chan 10)
        out-b (chan 10)
        out-c (chan 10)]
    (tap m out-a)
    (tap m out-b)
    (tap m out-c)
    (onto-chan!! src [:event1 :event2 :event3])
    (println "消费者 A 收到：" (drain out-a))
    (println "消费者 B 收到：" (drain out-b))
    (println "消费者 C 收到：" (drain out-c)))

  (println "\n=== 6. mini-Kafka：生产者 → buffer → 多 worker → 汇总 ===")
  ;; 综合演示：一个真实事件流系统的缩影
  (let [n-events  100
        n-workers 5
        ;; 生产者把原始事件丢入 raw
        raw       (chan 50)
        ;; transducer 在 channel 入口做轻量过滤+解析
        parsed    (chan 50 (comp (filter (fn [e] (pos? (:value e))))
                                 (map    (fn [e] (assoc e :parsed? true)))))
        ;; 多 worker 处理 parsed → results
        results   (chan 50)
        t0        (System/nanoTime)]

    ;; 1) 生产者：100 个事件，部分负数会被 transducer 过滤
    (thread
      (dotimes [i n-events]
        (>!! raw {:id i :value (- (rand-int 200) 100)}))
      (close! raw))

    ;; 2) raw → parsed（transducer 自动跑）
    (pipe raw parsed)

    ;; 3) parsed → results 用 N worker 并行处理（模拟重计算）
    (pipeline-blocking n-workers
                       results
                       (map (fn [e]
                              (Thread/sleep 5)
                              (assoc e :final (* (:value e) 2))))
                       parsed)

    ;; 4) 汇总
    (let [out (drain results)
          ms  (/ (- (System/nanoTime) t0) 1e6)]
      (println (format "处理完成：%d 事件入 → %d 事件出（部分被过滤掉），耗时 %d ms"
                       n-events (count out) (long ms)))
      (println "示例事件：" (first out))
      (println "全部 :parsed? = true？"
               (every? :parsed? out))
      (println "全部 :value > 0？"
               (every? #(pos? (:value %)) out))))

  (println "\n=== 一句话总结 ===")
  (println "- pipe          ：最简管道，上游关下游跟着关")
  (println "- chan + xform  ：把 transducer 挂在 channel 上，数据流进来即被转")
  (println "- pipeline      ：N 并行 worker，结果保序；CPU 密集用它")
  (println "- pipeline-blocking：同上但跑独立线程；IO 密集必须用它")
  (println "- mult/tap      ：fan-out 广播；典型用例：日志同时写多个目的地")

  (shutdown-agents))
