(ns ecom.workers.pool
  "core.async 工作池：命令总线 + N 个 worker。

   流程：
     enqueue! → in-chan → N worker 各 go-loop 取 → handler → 投影
     失败的 (cmd, error, attempts) 由调用者（dlq.clj）决定 retry/送 DLQ

   设计：
     - in-chan 容量来自 config（默认 1024）
     - worker 数量由 :pool-size 决定
     - process-fn :: cmd -> {:ok evs} | {:error msg}
     - on-success / on-failure 回调由组装方注入（保持 pool 与领域解耦）

   stop! 通过关闭 in-chan + 等待所有 worker go-block 完成实现优雅退出。"
  (:require [clojure.core.async :as a]))

(defn create-pool
  "创建工作池。
   opts:
     :pool-size      并发 worker 数
     :buffer         in-chan 缓冲
     :process-fn     (fn [cmd] {:ok evs} | {:error msg})
     :on-success     (fn [cmd evs])
     :on-failure     (fn [cmd error attempts])
     :max-attempts   每条命令最多尝试次数（默认 3）"
  [{:keys [pool-size buffer process-fn on-success on-failure max-attempts]
    :or   {pool-size 4 buffer 1024 max-attempts 3}}]
  (let [in-chan   (a/chan buffer)
        done-chan (a/chan)
        stats     (atom {:processed 0 :succeeded 0 :failed-final 0 :retried 0})
        workers
        (vec
          (for [i (range pool-size)]
            (a/go-loop []
              (if-let [{:keys [cmd attempts] :as job} (a/<! in-chan)]
                (let [attempt (or attempts 1)
                      r (try (process-fn cmd)
                             (catch Throwable t {:error (.getMessage t)}))]
                  (swap! stats update :processed inc)
                  (cond
                    (:ok r)
                    (do (swap! stats update :succeeded inc)
                        (when on-success (on-success cmd (:ok r))))

                    (< attempt max-attempts)
                    (do (swap! stats update :retried inc)
                        ;; 简单 backoff：sleep 10ms × attempt
                        (a/<! (a/timeout (* 10 attempt)))
                        (a/>! in-chan {:cmd cmd :attempts (inc attempt)}))

                    :else
                    (do (swap! stats update :failed-final inc)
                        (when on-failure (on-failure cmd (:error r) attempt))))
                  (recur))
                ;; in-chan 关闭：告知本 worker 退出
                (a/>! done-chan i)))))]
    {:in-chan in-chan
     :done-chan done-chan
     :workers workers
     :stats stats
     :pool-size pool-size}))

(defn enqueue!
  "把命令放入工作池。非阻塞（put! 异步）"
  [{:keys [in-chan]} cmd]
  (a/put! in-chan {:cmd cmd :attempts 1}))

(defn stop!
  "关闭工作池：close in-chan，等待所有 worker 完成。返回最终 stats。"
  [{:keys [in-chan done-chan pool-size stats]}]
  (a/close! in-chan)
  ;; 等所有 worker 报告完成
  (dotimes [_ pool-size] (a/<!! done-chan))
  @stats)

(defn drain-and-stop!
  "等队列里所有 pending 命令处理完再停。
   demo 用的简化策略：开始 stop 后给 max-wait-ms 让 retry 流转完。"
  [pool & {:keys [max-wait-ms] :or {max-wait-ms 2000}}]
  ;; 给一点时间让 retry 流转
  (Thread/sleep max-wait-ms)
  (stop! pool))
