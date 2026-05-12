;; ============================================================
;; Clojure Demo 18 — Future / Delay / Promise：三种"未来值"
;; ============================================================
;; 这三者长得像，区别要分清：
;;
;;   future     "现在就开干，结果以后再要"  → 后台线程立即执行
;;   delay      "等需要时再算，且只算一次"  → 调用 deref 才执行（lazy + memoize）
;;   promise    "我先占个坑，谁都能填一次"   → 解耦生产者和消费者
;;
;; 共同点：都是 IDeref（@x 取值），消费方用法一致。
;;
;; 运行：clojure -M clojure/18_futures_and_delay.clj
;; ============================================================

(println "=== 1. future：后台线程立即跑，deref 时阻塞等待 ===")
(let [t0 (System/nanoTime)
      f  (future
           (Thread/sleep 200)                ;; 模拟耗时计算
           (* 21 2))
      _  (println "future 提交后立即继续，主线程未阻塞")
      v  @f                                  ;; 这里才阻塞等结果
      ms (/ (- (System/nanoTime) t0) 1e6)]
  (println (format "结果 = %d，总耗时 %.0f ms" v ms)))

(println "\n=== 2. future 的并行：8 个并行任务 ===")
(let [t0 (System/nanoTime)
      tasks (doall
              (for [i (range 8)]
                (future
                  (Thread/sleep 100)         ;; 每个任务睡 100ms
                  (* i i))))
      results (mapv deref tasks)
      ms (/ (- (System/nanoTime) t0) 1e6)]
  (println "并行结果 =" results)
  (println (format "总耗时 %.0f ms（顺序跑应 800ms，并行 ~100ms）" ms)))

(println "\n=== 3. future 超时控制 ===")
(let [f (future
          (Thread/sleep 1000)
          :done)]
  (println "deref 带超时 200ms：" (deref f 200 :timeout!))
  (println "（注意：future 仍在后台跑，超时只是消费方放弃等待）")
  (future-cancel f)
  (println "已 cancel，cancelled? =" (future-cancelled? f)))

(println "\n=== 4. delay：懒计算 + 自动记忆化 ===")
(def expensive
  (delay
    (println "  [delay] 我开始计算了！（只会出现一次）")
    (Thread/sleep 100)
    42))

(println "定义 delay 后什么都没发生")
(println "第一次 deref：" @expensive)
(println "第二次 deref：" @expensive "（不会重新计算）")
(println "realized?      =" (realized? expensive))

(println "\n=== 5. delay 的典型用例：可选/昂贵的初始化 ===")
;; 比如 config 里有个昂贵的连接，只有真正用到才建立
(defn make-service []
  {:cheap-data 100
   :expensive (delay
                (println "  [service] 建立 DB 连接...")
                {:conn :fake-db})})

(let [svc (make-service)]
  (println "用 cheap-data：" (:cheap-data svc))
  (println "（没访问 expensive，连接不会建立）")
  (println "用 expensive：" @(:expensive svc))
  (println "再用一次：" @(:expensive svc) "（不会重连）"))

(println "\n=== 6. promise：可由\"另一个线程\"填值的占位符 ===")
;; 经典场景：跨线程通信、callback 转 future
(let [p (promise)]
  ;; 启动一个生产者线程，500ms 后填值
  (future
    (Thread/sleep 200)
    (deliver p {:status :ok :data 99}))

  (println "主线程等结果...")
  (let [t0 (System/nanoTime)
        v  @p                                ;; 阻塞直到 deliver
        ms (/ (- (System/nanoTime) t0) 1e6)]
    (println (format "拿到 %s（等了 %.0f ms）" v ms))))

(println "\n=== 7. promise：deliver 只能成功一次 ===")
(let [p (promise)]
  (println "第一次 deliver 是否生效：" (some? (deliver p :first)) "  现值 =" @p)
  (println "第二次 deliver 是否生效：" (some? (deliver p :second)) "  现值 =" @p
           "（false = deliver 被忽略）"))

(println "\n=== 8. 三者对比一句话 ===")
(println "  future  : 立即跑，deref 阻塞等结果")
(println "  delay   : 不跑，deref 时才跑，且只跑一次")
(println "  promise : 不跑，deliver 后才有值；典型用于跨线程握手")

(println "\n=== 一句话总结 ===")
(println "- 三个都是 IDeref，消费方一致；选型看\"谁来触发计算\"")
(println "- 真正的并发常常 future + promise 组合；delay 多见于配置层惰性")
(println "- 想要更复杂的异步管道？看下 demo 19/20 的 core.async")

;; future 用 agent 线程池，同 demo 17 说明
(shutdown-agents)
