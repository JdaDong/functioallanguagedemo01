(ns demo41
  "core.async pipeline-async 高级用法。

   核心区别：
     - pipeline           ：纯计算函数，并行 N（CPU bound）
     - pipeline-blocking  ：阻塞 IO 函数，并行 N（如 jdbc/file）
     - pipeline-async     ：异步函数 (in result-chan) -> ()，并行度为吞吐控制

   工业典型场景：N 路并发 HTTP 调用，每路返回 future，结果归并到 out chan。

   运行：clojure -M:run"
  (:require [clojure.core.async
             :refer [chan go go-loop <! >! <!! >!! close! pipeline-async pipeline-blocking
                     onto-chan!! into to-chan! timeout]]))

;; ───────────────────────────────────────────────────────────────────────
;; 模拟 HTTP 客户端：传入 url，模拟 IO 延迟，返回响应
;; 真实接口签名：(req result-chan) -> ()  —— 把结果 put 到 result-chan 然后 close
;; ───────────────────────────────────────────────────────────────────────
(defn fake-http
  "异步 fake：sleep 一会儿模拟 IO，然后把响应 put 到 result-chan 并 close。
   注意：不能阻塞当前线程，必须在 go/thread 里做"
  [url result-chan]
  (go
    (<! (timeout (rand-int 100)))                 ;; 模拟 0-100ms 延迟
    (let [resp {:url url :status 200 :body (str "DATA:" url)}]
      (>! result-chan resp)
      (close! result-chan))))

;; ───────────────────────────────────────────────────────────────────────
;; section 1: pipeline-async 并发 HTTP
;; ───────────────────────────────────────────────────────────────────────
(defn section-1-basic-pipeline-async []
  (println "── section 1: pipeline-async N=8 并发 HTTP，输入 50 个 URL ──")
  (let [urls (mapv #(str "/api/item/" %) (range 50))
        in   (chan 50)
        out  (chan 50)
        t0   (System/nanoTime)]

    (onto-chan!! in urls)

    ;; pipeline-async: 8 路并发，af 是异步 IO 函数
    (pipeline-async 8 out fake-http in)

    (let [results (<!! (into [] out))
          ms      (/ (- (System/nanoTime) t0) 1e6)]
      (println (format "  完成 %d 个请求，耗时 %.0f ms" (count results) ms))
      (println "  示例前 3 个：" (take 3 results))
      ;; 50 个请求 × 平均 50ms = 2500ms 串行；并发 8 路 → 期望 ~300ms
      (assert (= 50 (count results)))
      (assert (< ms 1500) (format "并发应远快于 2500ms 串行，实测 %.0f ms" ms)))))

;; ───────────────────────────────────────────────────────────────────────
;; section 2: pipeline-blocking —— 用于阻塞式 jdbc/file IO
;; ───────────────────────────────────────────────────────────────────────
(defn section-2-pipeline-blocking []
  (println "\n── section 2: pipeline-blocking（阻塞 IO 应用）──")
  (let [items (range 20)
        in    (to-chan! items)
        out   (chan 20)
        t0    (System/nanoTime)]

    ;; 阻塞函数（模拟 jdbc 查询）：直接 Thread/sleep 是 OK 的
    (pipeline-blocking 4 out
                       (map (fn [i]
                              (Thread/sleep 50)
                              (* i i)))
                       in)

    (let [results (<!! (into [] out))
          ms      (/ (- (System/nanoTime) t0) 1e6)]
      (println (format "  完成 %d 个，耗时 %.0f ms（4 路并发，期望 ~250ms）" (count results) ms))
      (println "  结果：" results)
      ;; 20 个 × 50ms = 1000ms 串行；4 路并发 → ~250ms
      (assert (= 20 (count results)))
      (assert (< ms 800) (format "并发应远快于 1000ms 串行，实测 %.0f ms" ms))
      (assert (= (mapv #(* % %) (range 20)) results) "保序"))))

;; ───────────────────────────────────────────────────────────────────────
;; section 3: 自然背压 —— in chan 满了上游就阻塞
;;   把 in chan 容量调小，证明 producer 被自动限速
;; ───────────────────────────────────────────────────────────────────────
(defn section-3-backpressure []
  (println "\n── section 3: 背压：慢消费者 → in chan 容量限速上游 ──")
  (let [in   (chan 4)                           ;; 小 buffer，强制背压
        out  (chan 4)
        produced (atom 0)
        t0   (System/nanoTime)]

    ;; 慢消费者：每个任务 100ms
    (pipeline-blocking 2 out
                       (map (fn [i] (Thread/sleep 100) i))
                       in)

    ;; 生产者：在 thread 里 push 20 个，每 push 一个就计数
    (future
      (doseq [i (range 20)]
        (>!! in i)
        (swap! produced inc))
      (close! in))

    ;; 边消费边观察 produced 计数
    (Thread/sleep 200)
    (let [snap @produced]
      (println (format "  消费 200ms 后，生产者只 push 出 %d 个（被背压拦在 in 满）" snap))
      (assert (< snap 20) "若没背压，生产者瞬间应该全 push 完"))

    (let [_  (<!! (into [] out))
          ms (/ (- (System/nanoTime) t0) 1e6)]
      (println (format "  全部完成，耗时 %.0f ms" ms))
      (assert (= 20 @produced)))))

;; ───────────────────────────────────────────────────────────────────────
;; section 4: pipeline-async 不保序的反例
;;   pipeline / pipeline-blocking / pipeline-async **保序**输出（这是 core.async 设计）
;;   但 go-block + 多路独立 fan-out 不保序。对比一下
;; ───────────────────────────────────────────────────────────────────────
(defn section-4-ordering []
  (println "\n── section 4: 保序 vs 不保序 ──")
  ;; pipeline-async 保序：用模拟"前面慢，后面快"的输入，输出仍按输入顺序
  (let [in  (to-chan! (range 8))
        out (chan 8)]
    (pipeline-async 8 out
                    (fn [i result-chan]
                      (go
                        ;; 越靠前的越慢
                        (<! (timeout (* 30 (- 8 i))))
                        (>! result-chan (str "task-" i))
                        (close! result-chan)))
                    in)
    (let [r (<!! (into [] out))]
      (println "  pipeline-async 输出（保序）：" r)
      (assert (= (mapv #(str "task-" %) (range 8)) r))))

  ;; 对比：纯 fan-out go-block 不保序
  (let [in  (to-chan! (range 8))
        out (chan 8)]
    (go-loop []
      (when-let [i (<! in)]
        (go
          (<! (timeout (* 30 (- 8 i))))
          (>! out (str "task-" i)))
        (recur)))
    (Thread/sleep 400)
    (close! out)
    (let [r (<!! (into [] out))]
      (println "  纯 go-block fan-out（不保序）：" r)
      (assert (= 8 (count r)))
      ;; 大概率不等于原序
      (println "    → 同一组结果，pipeline-async 保序，go-block 不保序"))))

(defn -main [& _]
  (section-1-basic-pipeline-async)
  (section-2-pipeline-blocking)
  (section-3-backpressure)
  (section-4-ordering)
  (println "\n=== 一句话总结 ===")
  (println "- pipeline / pipeline-blocking / pipeline-async：N 路并行 + 自动背压 + 保序")
  (println "- 何时选哪个：CPU 用 pipeline；阻塞 IO 用 pipeline-blocking；返回 chan 的异步 fn 用 pipeline-async")
  (println "- 背压自然涌现：in chan 满了上游 >! 阻塞，无需手写 token bucket")
  (println "- 想要不保序但更快的 fan-out？go-loop + 独立 go 即可（牺牲保序）")
  (shutdown-agents))
