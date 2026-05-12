(ns ecom.main-server
  "HTTP server 入口：启 Integrant 系统 + 加载 CSV 演示数据 + 阻塞等待。

   启动后：
     - 端口 35100 监听
     - 已 ingest 48 张演示订单（来自 sample_orders.csv），analytics 端点立即可查
     - Ctrl+C 触发 shutdown hook，干净 halt 系统

   配套：bash demo.sh 调 8 个端点；或 bash demo_full.sh 一键启停 + 调用。"
  (:require [ecom.system :as sys]
            [ecom.store.etl :as etl]
            [ecom.store.snapshot :as snap]
            [ecom.store.datascript :as store]
            [ecom.domain.order :as order]))

(defn- dispatch [{:keys [store-conn event-log]} cmd]
  (let [t (:command/type cmd)
        h (get order/command-handlers t)
        find-store {:find-order
                    #(let [o (store/find-order @store-conn %)]
                       (when o {:order-id (:order/id o)
                                :status   (:order/status o)
                                :total    (:order/total o)}))}
        result (h find-store cmd)]
    (when (:events result)
      (doseq [e (:events result)]
        (snap/append! event-log store-conn
                      (if (and (= (:event/type e) :order.placed)
                               (:placed-at-ms cmd))
                        (assoc e :ts (:placed-at-ms cmd))
                        e))))
    result))

(defn- ingest-csv-into-system!
  "把 CSV 的 48 条 ok 命令灌入运行中的 system，
   并把它们推进到 :delivered 状态，让 analytics 端点立即出有意义数据。"
  [system]
  (let [{:keys [ok]} (etl/load-csv "sample_orders.csv")
        deps (select-keys system [:store/conn :store/event-log])
        ;; 转 keyword 适配 dispatch 的入参
        deps {:store-conn (:store/conn deps)
              :event-log  (:store/event-log deps)}]
    (doseq [cmd ok]
      (let [r (dispatch deps cmd)]
        (when-let [evs (:events r)]
          (let [oid   (:order-id cmd)
                total (some-> evs first :total)]
            (when total
              (dispatch deps {:command/type :order/pay
                              :order-id oid :amount total})
              (dispatch deps {:command/type :order/deliver
                              :order-id oid}))))))
    (println (format "  ingested %d orders → :delivered" (count ok)))))

(defn -main [& args]
  (let [port (if-let [p (first args)] (Long/parseLong p) 35100)
        system (sys/start {:port port})]
    (println (str "  ✅ HTTP server listening on http://localhost:" port))
    (ingest-csv-into-system! system)
    (println "  endpoints:")
    (doseq [e ["GET  /health"
               "POST /users/register"
               "POST /users/login"
               "POST /orders"
               "GET  /orders/:id"
               "POST /orders/:id/{pay,ship,deliver,cancel}"
               "GET  /analytics/sales-by-sku"
               "GET  /analytics/top-users?n=5"
               "GET  /analytics/window/mom?year=2026&month=4"]]
      (println (str "    " e)))
    ;; shutdown hook：Ctrl+C 优雅退出
    (.addShutdownHook (Runtime/getRuntime)
      (Thread. (fn []
                 (println "\n  shutdown hook → halting system...")
                 (sys/stop system))))
    (println "\n  press Ctrl+C to stop\n")
    ;; 阻塞主线程
    @(promise)))
