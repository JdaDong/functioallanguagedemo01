(ns demo42
  "core.async pub/sub + mix + tap/untap：事件总线 / 多路复用核心。

   场景：
     - pub/sub : 一个事件流按 topic 分流给 N 个订阅者（消息总线）
     - mult/tap: 把一个 chan「广播」成 N 个独立副本（fan-out 复制流）
     - mix     : 把 N 个 chan 合并成一个，可在运行时增删/暂停/独占

   工业典型：领域事件分发、metrics 多路订阅、SSE 推送多客户端

   运行：clojure -M:run"
  (:require [clojure.core.async :as a
             :refer [chan go go-loop <! >! <!! >!! close! pub sub unsub
                     mult tap untap mix admix unmix toggle solo-mode timeout
                     onto-chan!! to-chan!]]))

;; ───────────────────────────────────────────────────────────────────────
;; section 1: pub / sub —— 按 topic 分流
;; ───────────────────────────────────────────────────────────────────────
(defn section-1-pub-sub []
  (println "── section 1: pub/sub —— 按 :type 分流给不同订阅者 ──")
  (let [src     (chan 10)
        publisher (pub src :type)               ;; 用 :type 字段做 topic key

        order-ch  (chan 10)
        user-ch   (chan 10)
        all-log   (chan 10)]

    ;; 三个订阅者：order 关心订单，user 关心用户，all-log 关心一切但用 :default？
    ;; 注意：pub/sub 必须给确定 topic；想要"全订阅"的方法是 mult/tap，不是 sub
    (sub publisher :order order-ch)
    (sub publisher :user  user-ch)

    ;; 派生：所有 :order 事件再额外打一份到 all-log（同一 chan 可被多个 sub 拿）
    (sub publisher :order all-log)
    (sub publisher :user  all-log)

    ;; 推送 5 条事件
    (go
      (>! src {:type :order :id 1 :amount 100})
      (>! src {:type :user  :id 42 :name "Ada"})
      (>! src {:type :order :id 2 :amount 50})
      (>! src {:type :user  :id 43 :name "Bob"})
      (>! src {:type :other :payload "ignored"})  ;; 没人订 :other → 被丢弃
      (close! src))

    ;; 等 50ms 让消息流过
    (Thread/sleep 50)

    ;; 收集（这时 src 已 close，但 order-ch / user-ch 不会自动 close）
    (println "  order 订阅者收到：")
    (loop [n 0]
      (let [v (a/poll! order-ch)]
        (when v
          (println "   "  v)
          (recur (inc n)))))
    (println "  user 订阅者收到：")
    (loop []
      (when-let [v (a/poll! user-ch)]
        (println "   " v)
        (recur)))
    (println "  all-log 看到的事件总数（应 = 4，:other 被丢）：")
    (loop [n 0]
      (if-let [_ (a/poll! all-log)]
        (recur (inc n))
        (do (println "   " n)
            (assert (= 4 n)))))))

;; ───────────────────────────────────────────────────────────────────────
;; section 2: mult / tap —— 一对多广播复制流
;; ───────────────────────────────────────────────────────────────────────
(defn section-2-mult-tap []
  (println "\n── section 2: mult/tap —— 把一个 chan 广播成 N 个独立副本 ──")
  (let [src (chan 5)
        m   (mult src)
        sub-a (chan 5)
        sub-b (chan 5)
        sub-c (chan 5)]

    (tap m sub-a)
    (tap m sub-b)
    (tap m sub-c)

    (go
      (doseq [v [:apple :banana :cherry]]
        (>! src v))
      (close! src))

    (Thread/sleep 30)

    ;; 三个 sub 应当都收到 [:apple :banana :cherry]
    (let [collect (fn [c]
                    (loop [acc []]
                      (if-let [v (a/poll! c)]
                        (recur (conj acc v))
                        acc)))
          a-vs (collect sub-a)
          b-vs (collect sub-b)
          c-vs (collect sub-c)]
      (println "  sub-a 收到：" a-vs)
      (println "  sub-b 收到：" b-vs)
      (println "  sub-c 收到：" c-vs)
      (assert (= [:apple :banana :cherry] a-vs))
      (assert (= a-vs b-vs c-vs) "三路完全相同（独立副本）"))))

;; ───────────────────────────────────────────────────────────────────────
;; section 3: mix —— 多个 chan 合并成一个，运行时可调
;; ───────────────────────────────────────────────────────────────────────
(defn section-3-mix []
  (println "\n── section 3: mix —— N 个流合并 + 运行时控制 ──")
  (let [out (chan 20)
        m   (mix out)
        ch-error (chan 5)
        ch-info  (chan 5)
        ch-debug (chan 5)]

    (admix m ch-error)
    (admix m ch-info)
    (admix m ch-debug)

    ;; 默认所有 chan 都 forward 到 out
    (>!! ch-error "ERR-1")
    (>!! ch-info  "INF-1")
    (>!! ch-debug "DBG-1")
    (Thread/sleep 30)
    (println "  默认全 forward：" (a/poll! out) (a/poll! out) (a/poll! out))

    ;; 暂停 debug 流（pause 模式不再转发，但消息仍堆积在源 chan）
    (toggle m {ch-debug {:pause true}})
    (>!! ch-error "ERR-2")
    (>!! ch-info  "INF-2")
    (>!! ch-debug "DBG-2-paused")
    (Thread/sleep 30)
    (let [collected (loop [acc []] (if-let [v (a/poll! out)] (recur (conj acc v)) acc))]
      (println "  暂停 debug 后，out 收到（应不含 DBG-2-paused）：" collected)
      (assert (= 2 (count collected)))
      (assert (not (some #{"DBG-2-paused"} collected))))

    ;; 切换 error 为 solo —— 只有它 forward，其他全噤声
    (solo-mode m :mute)                         ;; 先设 solo-mode，否则 :solo true 默认是 :pause 行为
    (toggle m {ch-error {:solo true}
               ch-info  {:pause false}
               ch-debug {:pause false}})
    (>!! ch-error "ERR-3-solo")
    (>!! ch-info  "INF-3-muted")
    (Thread/sleep 30)
    (let [collected (loop [acc []] (if-let [v (a/poll! out)] (recur (conj acc v)) acc))]
      (println "  error solo 后，out 收到（应只有 ERR-3-solo）：" collected)
      (assert (= ["ERR-3-solo"] collected)))

    ;; 移除 ch-debug
    (unmix m ch-debug)
    (>!! ch-debug "DBG-orphan")
    (Thread/sleep 30)
    (assert (nil? (a/poll! out)) "unmix 后 ch-debug 的消息不再到 out")
    (println "  unmix 后，ch-debug 的新消息不再 forward 到 out ✓")))

;; ───────────────────────────────────────────────────────────────────────
;; section 4: 真实场景拼装 —— pub + mult 组合
;; ───────────────────────────────────────────────────────────────────────
(defn section-4-real-world []
  (println "\n── section 4: 拼装：events → pub → 各 topic → mult → 多客户端 ──")
  (let [events       (chan 20)
        publisher    (pub events :topic)
        order-events (chan 10)
        order-mult   (mult order-events)
        client-1     (chan 10)
        client-2     (chan 10)]

    (sub publisher :order order-events)
    (tap order-mult client-1)
    (tap order-mult client-2)

    (go
      (>! events {:topic :order :id 1})
      (>! events {:topic :order :id 2})
      (>! events {:topic :user  :id 99})        ;; 不会到 order-events
      (close! events))

    (Thread/sleep 50)

    (let [c1 (loop [acc []] (if-let [v (a/poll! client-1)] (recur (conj acc v)) acc))
          c2 (loop [acc []] (if-let [v (a/poll! client-2)] (recur (conj acc v)) acc))]
      (println "  client-1 收到 order：" c1)
      (println "  client-2 收到 order：" c2)
      (assert (= 2 (count c1)))
      (assert (= c1 c2)))))

(defn -main [& _]
  (section-1-pub-sub)
  (section-2-mult-tap)
  (section-3-mix)
  (section-4-real-world)
  (println "\n=== 一句话总结 ===")
  (println "- pub/sub：按 topic 路由（订阅 :order 的人不会收到 :user）")
  (println "- mult/tap：一对多广播复制（所有 tap 都收到完整副本）")
  (println "- mix：多对一汇聚，运行时可 pause/solo/admix/unmix")
  (println "- 组合：events → pub(topic) → 每 topic mult → N 个 client tap，事件总线雏形")
  (shutdown-agents))
