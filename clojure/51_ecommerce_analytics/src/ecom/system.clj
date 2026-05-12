(ns ecom.system
  "Integrant 生命周期：声明 5 个组件 + init/halt 顺序

   组件依赖图：
     :store-conn   ← 无
     :event-log    ← 无
     :user-store   ← 无
     :pool         ← 无（独立工作池，可选拉起）
     :dlq          ← 无
     :handler      ← :store-conn :event-log :user-store
     :server       ← :handler  + :port :join?

   注意：Integrant 用 :ig.core/ref 表达依赖；init/halt 顺序自动拓扑。"
  (:require [integrant.core :as ig]
            [datascript.core :as d]
            [ring.adapter.jetty :as jetty]
            [ecom.store.datascript :as store]
            [ecom.store.snapshot   :as snap]
            [ecom.domain.user      :as user]
            [ecom.workers.pool     :as pool]
            [ecom.workers.dlq      :as dlq]
            [ecom.api.routes       :as routes]))

;; ─── 组件实现 ────────────────────────────────────────────────────────

(defmethod ig/init-key :store/conn      [_ _] (store/fresh-conn))
(defmethod ig/halt-key! :store/conn     [_ _] nil)

(defmethod ig/init-key :store/event-log [_ _] (snap/fresh-event-log))
(defmethod ig/halt-key! :store/event-log [_ _] nil)

(defmethod ig/init-key :user/store      [_ _] (user/fresh-store))
(defmethod ig/halt-key! :user/store     [_ _] nil)

(defmethod ig/init-key :workers/dlq     [_ _] (dlq/fresh-dlq))
(defmethod ig/halt-key! :workers/dlq    [_ _] nil)

(defmethod ig/init-key :workers/pool
  [_ {:keys [pool-size buffer]}]
  (pool/create-pool {:pool-size (or pool-size 4)
                     :buffer    (or buffer 256)
                     :process-fn (fn [_] {:ok []})}))

(defmethod ig/halt-key! :workers/pool [_ p]
  (when p (pool/stop! p)))

(defmethod ig/init-key :api/handler
  [_ {:keys [store-conn event-log user-store]}]
  (routes/make-ring-handler
    {:store-conn store-conn
     :event-log  event-log
     :user-store user-store}))

(defmethod ig/halt-key! :api/handler [_ _] nil)

(defmethod ig/init-key :api/server
  [_ {:keys [port join? handler]}]
  (jetty/run-jetty handler {:port port :join? (boolean join?)}))

(defmethod ig/halt-key! :api/server [_ server]
  (when server (.stop server)))

;; ─── 默认配置 ────────────────────────────────────────────────────────

(defn default-config
  "从端口 + pool-size 构造一份 Integrant 配置。"
  [{:keys [port pool-size] :or {port 35100 pool-size 4}}]
  {:store/conn       {}
   :store/event-log  {}
   :user/store       {}
   :workers/dlq      {}
   :workers/pool     {:pool-size pool-size :buffer 256}
   :api/handler      {:store-conn (ig/ref :store/conn)
                      :event-log  (ig/ref :store/event-log)
                      :user-store (ig/ref :user/store)}
   :api/server       {:port    port
                      :join?   false
                      :handler (ig/ref :api/handler)}})

(defn start
  "启动整个系统，返回 system map（halt 时传给 stop）"
  ([] (start {}))
  ([overrides]
   (ig/init (default-config overrides))))

(defn stop [system]
  (ig/halt! system))