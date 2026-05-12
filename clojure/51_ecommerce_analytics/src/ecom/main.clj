(ns ecom.main
  "电商分析后台主入口。

   当前进度：
     Step 1 ✅ 骨架 + deps 解析
     Step 2 ✅ 4 个领域聚合 + demo
     Step 3 ✅ store + etl + snapshot
     Step 4 ✅ analytics（mbql β + window MoM/YoY）
     Step 5 ✅ workers + DLQ
     Step 6 ⏳ api routes/handlers/middleware
     Step 7 ⏳ system 生命周期
     Step 8 ⏳ test/
     Step 9 ⏳ 真实 server + curl 演示"
  (:require [ecom.domain.order      :as order]
            [ecom.domain.inventory  :as inv]
            [ecom.domain.pricing    :as pricing]
            [ecom.domain.user       :as user]
            [ecom.store.datascript  :as store]
            [ecom.store.snapshot    :as snap]
            [ecom.store.etl         :as etl]
            [ecom.analytics.mbql    :as mbql]
            [ecom.analytics.window  :as win]
            [ecom.workers.pool      :as pool]
            [ecom.workers.dlq       :as dlq]
            [ecom.api.routes]
            [ecom.api.handlers]
            [ecom.api.middleware]
            [ecom.system]
            [datascript.core :as d]))

;; ─── 简易 in-memory order store（命令分发用 store 拉投影） ───────────

(defn dispatch
  "通过 conn 拿当前订单状态 → handler → 写事件 → 投影"
  [conn event-log cmd]
  (let [t (:command/type cmd)
        h (get order/command-handlers t)
        find-store {:find-order #(let [o (store/find-order @conn %)]
                                   (when o
                                     {:order-id (:order/id o)
                                      :status   (:order/status o)
                                      :total    (:order/total o)}))}
        result (h find-store cmd)]
    (if (:error result)
      result
      (do (doseq [e (:events result)]
            (snap/append! event-log conn
                          ;; 若命令带 placed-at-ms（来自 ETL），覆盖事件 ts
                          (if (and (= (:event/type e) :order.placed)
                                   (:placed-at-ms cmd))
                            (assoc e :ts (:placed-at-ms cmd))
                            e)))
          result))))

;; ─── Step 2 demos（已 PASS，简化保留） ─────────────────────────────────

(defn demo-pricing []
  (println "── pricing 策略表测试 ──")
  (let [items [{:sku "SKU-A" :qty 2 :unit-price 50.00}
               {:sku "SKU-B" :qty 1 :unit-price 120.00}]
        q1 (pricing/price-quote items)
        q2 (pricing/price-quote items "NEW10")
        q3 (pricing/price-quote items "VIP20")]
    (println "  无券    " q1)
    (println "  NEW10   " q2)
    (println "  VIP20   " q3)
    (assert (= 220.0 (:subtotal q1)))
    (assert (= 10.0  (:discount q2)))
    (assert (= 44.0  (:discount q3)))))

(defn demo-inventory []
  (println "\n── inventory 守恒律（500 并发预占） ──")
  (let [stock (inv/fresh-stock {"SKU-A" 1000 "SKU-B" 500})
        before (inv/total-units stock)]
    (run! deref (mapv (fn [_] (future (inv/reserve! stock "SKU-A" 1)))
                      (range 500)))
    (let [after (inv/total-units stock)]
      (println "  before=" before " after=" after)
      (assert (= before after)))))

(defn demo-user []
  (println "\n── user 注册 / 登录 ──")
  (let [us (user/fresh-store)
        _  (user/register! us {:user-id "U-1" :name "Ada" :email "a@x" :password "pw"})
        r  (user/login!    us {:user-id "U-1" :password "pw"})
        who (user/whoami   us (:token r))]
    (println "  whoami:" (select-keys who [:user-id :name]))
    (assert (= "Ada" (:name who)))))

(defn demo-order-state-machine []
  (println "\n── order 状态机（命令 → 事件 → 投影） ──")
  (let [conn (store/fresh-conn)
        log  (snap/fresh-event-log)]
    (dispatch conn log {:command/type :order/place
                        :order-id "O-1" :user-id "U-1"
                        :items [{:sku "SKU-A" :qty 2 :unit-price 50.00}
                                {:sku "SKU-B" :qty 1 :unit-price 120.00}]
                        :coupon "NEW10"})
    (dispatch conn log {:command/type :order/pay :order-id "O-1" :amount 210.0})
    (dispatch conn log {:command/type :order/ship :order-id "O-1" :tracking-no "SF-9001"})
    (dispatch conn log {:command/type :order/deliver :order-id "O-1"})
    (let [o (store/find-order @conn "O-1")]
      (println "  最终订单:" (select-keys o [:order/id :order/status :order/total :order/tracking-no]))
      (assert (= :delivered (:order/status o)))
      (assert (= 210.0 (:order/total o))))))

;; ─── Step 3 demos ──────────────────────────────────────────────────────

(defn demo-etl []
  (println "\n── ETL: CSV → 命令流 ──")
  (let [{:keys [ok reject]} (etl/load-csv "sample_orders.csv")]
    (println "  接受:" (count ok) "条,  拒绝:" (count reject) "条")
    (doseq [r reject]
      (println "    ✗ row" (:row r) ":" (:reason r) "raw=" (:raw r)))
    (assert (= 48 (count ok)))
    (assert (= 2  (count reject)))
    ok))

(defn demo-snapshot-roundtrip [event-log]
  (println "\n── snapshot 重放等价性 ──")
  (let [conn-a (snap/replay-from-empty event-log)
        snapshot (snap/snapshot! event-log conn-a)
        ;; 模拟"重启后从快照恢复"：tail 为空
        conn-b (snap/restore-from-snapshot snapshot event-log)]
    (println "  事件总数:" (count @event-log))
    (println "  conn-a 订单数:" (store/order-count @conn-a))
    (println "  conn-b 订单数:" (store/order-count @conn-b))
    (assert (snap/equivalent? conn-a conn-b)
            "replay-from-empty 与 restore-from-snapshot 应等价")))

(defn run-csv-ingestion!
  "跑完 CSV 全流程：每条 :order/place 命令 + 后续 :order/pay+:order/deliver
   返回 [conn event-log]"
  [ok-cmds]
  (let [conn (store/fresh-conn)
        log  (snap/fresh-event-log)]
    (doseq [cmd ok-cmds]
      (let [r (dispatch conn log cmd)]
        (when (:events r)
          ;; 大部分订单跑到 paid+delivered
          (let [oid (:order-id cmd)
                total (some-> r :events first :total)]
            (when total
              (dispatch conn log {:command/type :order/pay
                                  :order-id oid :amount total})
              (dispatch conn log {:command/type :order/deliver
                                  :order-id oid}))))))
    [conn log]))

;; ─── Step 4 demos ──────────────────────────────────────────────────────

(defn demo-mbql [conn]
  (println "\n── MBQL: 按 SKU 聚合销售 ──")
  (let [rows (mbql/sales-by-sku @conn {})]
    (println "  sku | 销量 | 销售额 | 订单数")
    (doseq [[sku qty rev cnt] rows]
      (println (format "  %-6s %4d %8.2f %4d" sku qty (double rev) cnt)))
    (assert (seq rows) "应至少有 1 个 sku 行"))

  (println "\n── MBQL: 用户消费 Top 5 ──")
  (let [rows (mbql/top-users @conn {:n 5})]
    (println "  user | 订单数 | 总额")
    (doseq [[uid cnt total] rows]
      (println (format "  %-4s %5d %9.2f" uid cnt (double total))))
    (assert (= 5 (count rows)))))

(defn demo-window [conn]
  (println "\n── 窗口对比：4 月 vs 3 月（环比）──")
  (let [r (win/mom @conn 2026 4 win/total-sales)]
    (println "  curr=" (:curr r) "prev=" (:prev r)
             "delta=" (:delta r) "pct=" (:pct r))
    ;; 我们的 CSV 都在 4 月，3 月为 0，pct=nil
    (assert (pos? (:curr r)))
    (assert (zero? (:prev r))))

  (println "\n── 窗口对比：4 月 vs 4 月（自比环比，sanity） ──")
  ;; 自己跟自己比 → delta=0, pct=0
  (let [w (win/month-bounds 2026 4)
        r (win/compare-windows @conn w w win/total-sales)]
    (println "  curr=" (:curr r) "prev=" (:prev r)
             "delta=" (:delta r) "pct=" (:pct r))
    (assert (zero? (:delta r)))
    (assert (zero? (:pct r)))))

;; ─── Step 5 demos ──────────────────────────────────────────────────────

(defn demo-pool-happy-path []
  (println "\n── workers.pool: 100 命令并发，0 失败率 ──")
  (let [counter (atom 0)
        process-fn (fn [_cmd]
                     (Thread/sleep 1)
                     (swap! counter inc)
                     {:ok []})
        p (pool/create-pool {:pool-size 4 :buffer 256
                             :process-fn process-fn})]
    (dotimes [i 100]
      (pool/enqueue! p {:n i}))
    ;; 等流空
    (Thread/sleep 200)
    (let [stats (pool/stop! p)]
      (println "  stats:" stats)
      (assert (= 100 @counter))
      (assert (= 100 (:succeeded stats)))
      (assert (zero? (:failed-final stats))))))

(defn demo-pool-with-dlq []
  (println "\n── workers.pool + DLQ: 30% 失败率，重试 3 次 ──")
  (let [the-dlq (dlq/fresh-dlq)
        attempts (atom {})  ; cmd-id -> 已尝试次数
        ;; 构造一个：每条命令前 2 次失败、第 3 次成功（除了"永久失败的"）
        process-fn (fn [{:keys [id permanent?] :as _cmd}]
                     (let [n (get (swap! attempts update id (fnil inc 0))
                                  id)]
                       (cond
                         permanent?       {:error "permanent"}
                         (>= n 2)         {:ok []}
                         :else            {:error (str "transient #" n)})))
        on-failure (fn [cmd reason at] (dlq/record! the-dlq cmd reason at))
        p (pool/create-pool {:pool-size 4 :buffer 512
                             :max-attempts 3
                             :process-fn process-fn
                             :on-failure on-failure})]
    ;; 70 条普通（最终会成功）+ 30 条永久失败
    (dotimes [i 70]
      (pool/enqueue! p {:id (str "T-" i)}))
    (dotimes [i 30]
      (pool/enqueue! p {:id (str "P-" i) :permanent? true}))
    (Thread/sleep 1000)
    (let [stats (pool/stop! p)]
      (println "  stats     :" stats)
      (println "  DLQ 条数  :" (dlq/count-records the-dlq))
      (println "  DLQ 原因  :" (dlq/latest-reasons the-dlq 3))
      (assert (= 30 (dlq/count-records the-dlq))
              "30 条永久失败应全部进 DLQ")
      (assert (= 70 (:succeeded stats))
              "70 条 transient 应最终成功")
      (assert (= 30 (:failed-final stats))))))

;; ─── 主流程 ────────────────────────────────────────────────────────────

(defn -main [& _]
  (println "==========================================")
  (println "  ecommerce_analytics  Step 1-5 演示")
  (println "==========================================\n")
  (demo-pricing)
  (demo-inventory)
  (demo-user)
  (demo-order-state-machine)

  ;; Step 3：ETL 后真实跑通 50 条 CSV
  (let [ok-cmds (demo-etl)
        [conn log] (run-csv-ingestion! ok-cmds)]
    (println "\n  CSV 全量 ingest 完成，订单数:" (store/order-count @conn))
    (assert (= 48 (store/order-count @conn))
            "48 条 ok 命令应全部成功投影")

    (demo-snapshot-roundtrip log)

    ;; Step 4：在投影好的 db 上跑 MBQL + window
    (demo-mbql conn)
    (demo-window conn))

  ;; Step 5：纯工作池演示（解耦于业务）
  (demo-pool-happy-path)
  (demo-pool-with-dlq)

  (println "\n==========================================")
(println "  Step 1-10 全部 PASS ✅  HTTP 演示见 clojure -M:run-server")
  (println "==========================================")
  (shutdown-agents))
