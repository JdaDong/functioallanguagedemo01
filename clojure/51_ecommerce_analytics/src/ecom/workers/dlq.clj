(ns ecom.workers.dlq
  "Dead Letter Queue：收集 max-attempts 仍失败的命令。

   提供：
     fresh-dlq          创建空 DLQ（atom of vector）
     record!            记录失败命令
     count-records      DLQ 长度
     replay-all!        把 DLQ 整体回灌工作池（修复后重试）
     latest-reasons     按 reason 频次统计

   与 pool 的协作：
     创建 pool 时把 dlq.clj/record! 包成 on-failure 回调注入，
     pool 不依赖 dlq，dlq 也不依赖 pool。")

(defn fresh-dlq [] (atom []))

(defn record!
  "失败命令进 DLQ"
  [dlq cmd reason attempts]
  (swap! dlq conj {:cmd cmd
                   :reason reason
                   :attempts attempts
                   :ts (System/currentTimeMillis)}))

(defn count-records [dlq]
  (count @dlq))

(defn snapshot [dlq] @dlq)

(defn latest-reasons
  "前 n 个失败原因频次"
  [dlq n]
  (->> @dlq
       (map :reason)
       frequencies
       (sort-by val >)
       (take n)
       vec))

(defn replay-all!
  "把 DLQ 全部命令重新入池，DLQ 清空"
  [dlq enqueue-fn]
  (let [pending @dlq]
    (reset! dlq [])
    (doseq [{:keys [cmd]} pending]
      (enqueue-fn cmd))
    (count pending)))