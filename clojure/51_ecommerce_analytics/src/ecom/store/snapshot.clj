(ns ecom.store.snapshot
  "事件流快照 + 重放。

   设计：
     - event-log 是 atom of vector，append-only
     - snapshot = {:event-count N :db-snapshot ...}
     - replay-from-empty 从空库出发逐条 apply
     - 当 (count event-log) - (snapshot 的 event-count) 超阈值，触发新快照

   demo 用途：演示 ES 的 replay 闭环，不做持久化（内存）。"
  (:require [ecom.store.datascript :as store]
            [datascript.core :as d]))

(defn fresh-event-log [] (atom []))

(defn append!
  "把 event 追加到日志，并实时投影到 conn"
  [event-log conn event]
  (swap! event-log conj event)
  (store/apply-event! conn event)
  event)

(defn replay-from-empty
  "从空库重放整段日志，返回新建 conn"
  [event-log]
  (let [conn (store/fresh-conn)]
    (doseq [e @event-log]
      (store/apply-event! conn e))
    conn))

(defn snapshot!
  "拍快照：返回当前事件数 + db datoms 序列化形式（demo 用 d/datoms）"
  [event-log conn]
  {:event-count (count @event-log)
   :datoms      (vec (d/datoms @conn :eavt))
   :ts          (System/currentTimeMillis)})

(defn restore-from-snapshot
  "从快照重建 conn（不重放快照前的事件）。
   返回 [新 conn, 应从第 event-count 条开始 replay 的尾部事件数]"
  [snapshot event-log]
  (let [conn (store/fresh-conn)
        {:keys [event-count datoms]} snapshot]
    ;; 把 datoms 直接灌回（demo 用最朴素方式：retract 重设）
    (d/transact! conn
                 (vec (for [{:keys [e a v]} datoms]
                        [:db/add e a v])))
    (let [tail (subvec @event-log event-count)]
      (doseq [e tail]
        (store/apply-event! conn e))
      conn)))

(defn equivalent?
  "两个 conn 的 :eavt datoms 集合相等"
  [conn-a conn-b]
  (= (set (map (juxt :e :a :v) (d/datoms @conn-a :eavt)))
     (set (map (juxt :e :a :v) (d/datoms @conn-b :eavt)))))
