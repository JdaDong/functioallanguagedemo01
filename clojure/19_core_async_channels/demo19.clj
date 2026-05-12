(ns demo19
  "core.async Channels 基础：CSP 模型在 Clojure 的工业实现。

   参考：https://clojure.github.io/core.async/
   运行：clojure -M:run"
  (:require [clojure.core.async :as a
             :refer [chan go thread <! >! <!! >!! close! alts!! timeout
                     dropping-buffer sliding-buffer]]))

(defn -main [& _]
  (println "=== 1. 最简 channel：阻塞 put/take ===")
  ;; >!! / <!! 在普通线程使用（"bang-bang" 表示阻塞）
  (let [c (chan)]
    (thread (>!! c :hello))                  ;; 后台线程 put（不放到 thread 会死锁）
    (println "收到：" (<!! c)))

  (println "\n=== 2. unbuffered channel：put 阻塞直到有人 take ===")
  (let [c (chan)
        t0 (System/nanoTime)]
    (thread
      (Thread/sleep 100)
      (println "  消费者 200ms 后才出现")
      (println "  收到：" (<!! c)))
    (>!! c :payload)                         ;; 这行阻塞了 ~100ms 等消费者
    (println (format "生产者总耗时 %.0f ms（被消费者拖住）"
                     (/ (- (System/nanoTime) t0) 1e6))))

  (println "\n=== 3. buffered channel：put 不阻塞（buffer 未满） ===")
  (let [c (chan 5)                           ;; 容量 5
        t0 (System/nanoTime)]
    (>!! c :a) (>!! c :b) (>!! c :c)
    (println (format "3 次 put 总耗时 %.0f ms（buffer 未满，秒过）"
                     (/ (- (System/nanoTime) t0) 1e6)))
    (println "依次取出：" (<!! c) (<!! c) (<!! c)))

  (println "\n=== 4. dropping-buffer：满了丢新的（保留旧值） ===")
  (let [c (chan (dropping-buffer 2))]
    (>!! c :a) (>!! c :b)
    (>!! c :c)                               ;; 被丢弃
    (>!! c :d)                               ;; 被丢弃
    (close! c)
    (println "保留：" (<!! c) (<!! c))
    (println "再读：" (<!! c) "（关闭后返回 nil）"))

  (println "\n=== 5. sliding-buffer：满了丢旧的（保留新值） ===")
  (let [c (chan (sliding-buffer 2))]
    (>!! c :a) (>!! c :b) (>!! c :c) (>!! c :d)
    (close! c)
    (println "保留：" (<!! c) (<!! c)))

  (println "\n=== 6. go-block：parking 式让出线程 ===")
  ;; 1000 个 go-block 并发，但只占用少数 OS 线程
  ;; 关键：<! 不阻塞 OS 线程，而是把 go-block 挂起，OS 线程被释放给别的 go
  (let [out (chan)
        n   1000
        t0 (System/nanoTime)]
    (dotimes [i n]
      (go
        (<! (timeout (rand-int 50)))         ;; 模拟一点点 IO 等待
        (>! out i)))
    (let [results (loop [acc [] left n]
                    (if (zero? left)
                      acc
                      (recur (conj acc (<!! out)) (dec left))))]
      (println (format "1000 个 go-block 全部完成，耗时 %.0f ms"
                       (/ (- (System/nanoTime) t0) 1e6)))
      (println "结果数 =" (count results) "  示例前 10 =" (take 10 results))))

  (println "\n=== 7. alts!! 多路选择（CSP 的 select） ===")
  ;; 等多个 channel，谁先到就处理谁；常用于"超时"/"取消"
  (let [data    (chan)
        cancel  (chan)]
    (thread
      (Thread/sleep 80)
      (>!! data :result))
    (let [[v port] (alts!! [data cancel (timeout 200)])]
      (println "拿到值 =" v "  来自 port =" (cond
                                              (= port data)   :data
                                              (= port cancel) :cancel
                                              :else           :timeout))))

  (println "\n=== 8. alts!! 超时退出 ===")
  (let [slow (chan)
        [v port] (alts!! [slow (timeout 100)])]
    (println "v =" v "  超时？" (not= port slow)))

  (println "\n=== 9. close! 的语义：消费者读到 nil 就停 ===")
  (let [c (chan 10)]
    (>!! c :a) (>!! c :b) (>!! c :c)
    (close! c)
    ;; 关闭后已 put 的还能读出来，读完后开始返回 nil
    (loop []
      (when-let [v (<!! c)]
        (println "  读到：" v)
        (recur)))
    (println "  通道关闭，循环退出"))

  (println "\n=== 一句话总结 ===")
  (println "- chan/>!!/<!!  ：线程外的阻塞通信")
  (println "- go + <!/>!    ：百万级轻量协程，<! 不占 OS 线程")
  (println "- alts!!        ：CSP 的 select；最常配 timeout 做超时")
  (println "- close!        ：消费者读到 nil 自然停")
  (println "- 三种 buffer   ：unbuffered / dropping / sliding，按场景选")

  ;; 关闭 core.async 的 dispatch 线程池，让 JVM 立即退出
  (shutdown-agents))
