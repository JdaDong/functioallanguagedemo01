(ns demo43
  "core.async 错误传播 + DLQ + 取消 + 死锁陷阱。

   核心问题：go-block 里 throw 不会冒到外层；handler 异常会被 core.async 默默吞掉。
   工业范式：
     1) 用 [tag value] 包装值（:ok / :error），错误也是数据
     2) 死信队列（DLQ）：处理失败的消息单独路由
     3) 取消令牌：用 close-chan 作 cancellation token
     4) 死锁陷阱：unbuffered chan + 同 go-block 自 put/take

   运行：clojure -M:run"
  (:require [clojure.core.async :as a
             :refer [chan go go-loop <! >! <!! >!! close! pipeline-blocking
                     timeout alts! alts!! to-chan!]]))

;; ───────────────────────────────────────────────────────────────────────
;; section 1: handler 异常会被吞，必须显式包装
;; ───────────────────────────────────────────────────────────────────────
(defn section-1-silent-throw []
  (println "── section 1: 反例：go-block 里抛异常会被吞 ──")
  (let [out (chan 5)]
    (go
      (try
        (throw (ex-info "boom" {}))             ;; 这个异常被 core.async 默默吞
        (>! out :should-not-reach)
        (catch Throwable t
          (println "  [trap] 自己捕获了：" (ex-message t)))))
    (Thread/sleep 30)
    (println "  out 应该没值（异常被默默吞前 catch 拦下）：" (a/poll! out))
    (assert (nil? (a/poll! out))))

  (println "\n  正例：用 [:ok v] / [:error e] 包装（railway-oriented programming）")
  (let [out (chan 5)
        risky-step (fn [x]
                     (try
                       (when (zero? (mod x 3))
                         (throw (ex-info (str "div-by-3: " x) {:x x})))
                       [:ok (* x x)]
                       (catch Throwable t
                         [:error (ex-message t)])))]
    (go-loop [i 0]
      (when (< i 5)
        (>! out (risky-step i))
        (recur (inc i))))
    (Thread/sleep 30)
    (let [results (loop [acc []] (if-let [v (a/poll! out)] (recur (conj acc v)) acc))]
      (println "  结果：" results)
      (assert (= 5 (count results)))
      (assert (some #(= :error (first %)) results) "至少一个 :error")
      (assert (some #(= :ok (first %))    results) "至少一个 :ok"))))

;; ───────────────────────────────────────────────────────────────────────
;; section 2: DLQ 模式 —— 处理失败的消息单独路由
;; ───────────────────────────────────────────────────────────────────────
(defn section-2-dlq []
  (println "\n── section 2: DLQ 模式 ──")
  (let [in    (to-chan! [1 2 3 4 5 6 7 8])
        ok    (chan 10)
        dlq   (chan 10)
        process (fn [x]
                  ;; 偶数 ok，奇数失败
                  (if (even? x)
                    [:ok (* x 10)]
                    [:error {:input x :reason "odd not allowed"}]))]
    ;; 工作者 go-loop：从 in 拿，分流到 ok / dlq
    (go-loop []
      (if-let [x (<! in)]
        (do
          (let [[tag v] (process x)]
            (case tag
              :ok    (>! ok v)
              :error (>! dlq v)))
          (recur))
        (do (close! ok) (close! dlq))))

    ;; 收集
    (let [oks  (loop [acc []] (if-let [v (<!! ok)]  (recur (conj acc v)) acc))
          bads (loop [acc []] (if-let [v (<!! dlq)] (recur (conj acc v)) acc))]
      (println "  ok 队列：" oks)
      (println "  dlq 队列：" bads)
      (assert (= [20 40 60 80] oks))
      (assert (= 4 (count bads))))))

;; ───────────────────────────────────────────────────────────────────────
;; section 3: 取消令牌（cancel chan + alts!）
;;   取消 = close 一个共享 chan；worker 用 alts! 同时听数据和取消
;; ───────────────────────────────────────────────────────────────────────
(defn section-3-cancellation []
  (println "\n── section 3: 取消令牌 ──")
  (let [in     (chan 10)
        cancel (chan)
        out    (chan 100)                       ;; 大 buffer，避免 worker 在 >! out 卡住
        done   (chan)
        processed (atom 0)]

    ;; worker
    (go-loop [n 0]
      (let [[v port] (alts! [in cancel])]
        (cond
          (= port cancel)
          (do (println (format "  worker 收到取消信号，处理了 %d 个就停" n))
              (reset! processed n)
              (close! out)
              (close! done))

          (nil? v)                              ;; in 被关
          (do (reset! processed n) (close! out) (close! done))

          :else
          (do (>! out (* v v))
              (recur (inc n))))))

    ;; producer：推 100 个，但慢慢推（每 5ms 一个）
    (go
      (loop [xs (range 100)]
        (when-let [x (first xs)]
          (let [[_ port] (alts! [[in x] cancel])]
            (when-not (= port cancel)
              (<! (timeout 5))
              (recur (rest xs))))))
      (close! in))

    (Thread/sleep 100)
    (close! cancel)                             ;; 取消！
    (<!! done)

    (let [done-cnt (loop [n 0] (if-let [_ (<!! out)] (recur (inc n)) n))]
      (println (format "  out 收到 %d 个结果（远小于 100）" done-cnt))
      (assert (< done-cnt 100) "应被取消提前停止")
      (assert (pos? done-cnt) "至少处理过若干个"))))

;; ───────────────────────────────────────────────────────────────────────
;; section 4: 死锁陷阱
;; ───────────────────────────────────────────────────────────────────────
(defn section-4-deadlock []
  (println "\n── section 4: 死锁陷阱（教学反例） ──")

  (println "  陷阱 A：unbuffered chan + 同 go-block 自 put/take")
  ;; 错误代码（注释保留为教学）：
  ;;   (let [c (chan)]
  ;;     (go (>! c 1) (println (<! c))))   ;; >! 阻塞等消费者，永远到不了 <!
  ;; 这不是写在代码里跑（会真死锁），写在注释里说明。

  ;; 用一个 timeout 包装的"会卡但能逃出"版本来演示
  (let [c (chan)                                ;; 容量 0
        result (atom :pending)]
    (go
      (let [[v _] (alts! [(go (>! c :payload) :put-done)
                          (timeout 100)])]
        (reset! result (or v :timeout))))
    (Thread/sleep 200)
    (println (format "  无消费者 + unbuffered 自 put：result = %s（卡 100ms 超时）"
                     @result))
    ;; 不 assert 具体值（race）；只 assert 不 nil
    (assert (some? @result)))

  (println "\n  陷阱 B：阻塞 IO 用 <!（应改 <!!）—— 会饿死 go 线程池")
  ;; 教学：go 线程池只有 8 个 OS 线程，<! 不让出会拖死整池
  ;; 这里用 channel-as-result 的范式正确写法（不 demo 阻塞死锁，避免真卡）
  (let [c (chan)]
    (go
      ;; 正确：模拟"阻塞工作"应该用 a/thread 而不是 a/go
      (a/thread
        (Thread/sleep 50)                       ;; 阻塞工作
        (>!! c :ok))
      (println "  正确做法：阻塞 IO 用 (a/thread ...) 而不是 (go ...)，结果 =" (<! c))))

  (Thread/sleep 100))

(defn -main [& _]
  (section-1-silent-throw)
  (section-2-dlq)
  (section-3-cancellation)
  (section-4-deadlock)
  (println "\n=== 一句话总结 ===")
  (println "- go-block 里抛异常 = 默默吞，必须 try/catch 包成 [:error reason]")
  (println "- DLQ 模式：ok 队列 + dlq 队列，错误也是数据，不是控制流")
  (println "- 取消令牌：close 一个共享 chan，worker 用 alts! 同时听")
  (println "- 死锁三大陷阱：1) unbuffered 自 put/take 同 go；2) 阻塞 IO 用 <! 不用 <!! 拖死线程池；3) 嵌套 go 闭锁未关")
  (shutdown-agents))
