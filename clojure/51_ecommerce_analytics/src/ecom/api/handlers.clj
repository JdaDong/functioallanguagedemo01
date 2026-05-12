(ns ecom.api.handlers
  "8 个 ring handler，按业务域分组：
     orders:     POST /orders   GET /orders/:id   POST /orders/:id/{pay,ship,deliver,cancel}
     users:      POST /users/register   POST /users/login
     analytics:  GET /analytics/sales-by-sku   GET /analytics/top-users   GET /analytics/window/mom

   设计：
     - 每个 handler 接 (deps, req)，返回 ring response map
     - deps 由 Integrant 在 Step 7 注入：{:store-conn :event-log :user-store}
     - body-params 已被 middleware 解析为 keyword keys"
  (:require [ecom.domain.order     :as order]
            [ecom.domain.user      :as user]
            [ecom.store.datascript :as store]
            [ecom.store.snapshot   :as snap]
            [ecom.analytics.mbql   :as mbql]
            [ecom.analytics.window :as win]))

;; ─── 通用响应 ─────────────────────────────────────────────────────────

(defn ok    [body] {:status 200 :body body})
(defn bad   [msg]  {:status 400 :body {:error msg}})
(defn nf    [msg]  {:status 404 :body {:error msg}})

;; ─── 命令分发（同步） ─────────────────────────────────────────────────

(defn- dispatch
  "复用第二轮 main.clj 的同步分发：拉投影 → handler → 投影"
  [{:keys [store-conn event-log]} cmd]
  (let [t (:command/type cmd)
        h (get order/command-handlers t)
        find-store {:find-order
                    #(let [o (store/find-order @store-conn %)]
                       (when o
                         {:order-id (:order/id o)
                          :status   (:order/status o)
                          :total    (:order/total o)}))}
        result (h find-store cmd)]
    (when (:events result)
      (doseq [e (:events result)]
        (snap/append! event-log store-conn e)))
    result))

(defn- pulled-order [{:keys [store-conn]} oid]
  (when-let [o (store/find-order @store-conn oid)]
    {:order-id    (:order/id o)
     :user-id     (:user/id o)
     :status      (:order/status o)
     :total       (:order/total o)
     :discount    (:order/discount o)
     :tracking-no (:order/tracking-no o)
     :items       (mapv #(select-keys % [:item/sku :item/qty :item/unit-price])
                        (:order/items o))}))

;; ─── orders handlers ─────────────────────────────────────────────────

(defn place-order [deps req]
  (let [{:keys [order-id user-id items coupon]} (:body-params req)]
    (cond
      (or (nil? order-id) (nil? user-id) (empty? items))
      (bad "order-id, user-id, items required")
      :else
      (let [r (dispatch deps {:command/type :order/place
                              :order-id order-id
                              :user-id  user-id
                              :items    (mapv #(update % :unit-price double) items)
                              :coupon   coupon})]
        (if (:error r)
          (bad (:error r))
          (ok (pulled-order deps order-id)))))))

(defn get-order [deps req]
  (let [oid (-> req :path-params :id)]
    (if-let [o (pulled-order deps oid)]
      (ok o)
      (nf (str "no such order: " oid)))))

(defn pay-order [deps req]
  (let [oid (-> req :path-params :id)
        {:keys [amount]} (:body-params req)
        r (dispatch deps {:command/type :order/pay
                          :order-id oid :amount (double amount)})]
    (if (:error r) (bad (:error r)) (ok (pulled-order deps oid)))))

(defn ship-order [deps req]
  (let [oid (-> req :path-params :id)
        {:keys [tracking-no]} (:body-params req)
        r (dispatch deps {:command/type :order/ship
                          :order-id oid :tracking-no tracking-no})]
    (if (:error r) (bad (:error r)) (ok (pulled-order deps oid)))))

(defn deliver-order [deps req]
  (let [oid (-> req :path-params :id)
        r   (dispatch deps {:command/type :order/deliver :order-id oid})]
    (if (:error r) (bad (:error r)) (ok (pulled-order deps oid)))))

(defn cancel-order [deps req]
  (let [oid (-> req :path-params :id)
        {:keys [reason]} (:body-params req)
        r (dispatch deps {:command/type :order/cancel
                          :order-id oid :reason reason})]
    (if (:error r) (bad (:error r)) (ok (pulled-order deps oid)))))

;; ─── users handlers ──────────────────────────────────────────────────

(defn register-user [{:keys [user-store]} req]
  (let [r (user/register! user-store (:body-params req))]
    (if (:error r) (bad (:error r)) (ok {:user-id (:ok r)}))))

(defn login-user [{:keys [user-store]} req]
  (let [r (user/login! user-store (:body-params req))]
    (if (:error r) (bad (:error r))
        (ok (update r :token #(str (subs % 0 (min 12 (count %))) "...truncated"))))))

;; ─── analytics handlers ──────────────────────────────────────────────

(defn- ->long [s] (when s (try (Long/parseLong (str s)) (catch Exception _ nil))))

(defn sales-by-sku [{:keys [store-conn]} req]
  (let [from (->long (get-in req [:query-params "from"]))
        to   (->long (get-in req [:query-params "to"]))
        rows (mbql/sales-by-sku @store-conn {:from-ms from :to-ms to})]
    (ok {:rows (mapv (fn [[sku qty rev cnt]]
                       {:sku sku :qty qty :revenue rev :order-count cnt})
                     rows)})))

(defn top-users [{:keys [store-conn]} req]
  (let [n (or (->long (get-in req [:query-params "n"])) 5)
        rows (mbql/top-users @store-conn {:n n})]
    (ok {:rows (mapv (fn [[uid cnt total]]
                       {:user-id uid :order-count cnt :total total})
                     rows)})))

(defn window-mom [{:keys [store-conn]} req]
  (let [year  (->long (get-in req [:query-params "year"]))
        month (->long (get-in req [:query-params "month"]))]
    (if (and year month)
      (ok (win/mom @store-conn year month win/total-sales))
      (bad "year & month required"))))

;; ─── handler 表（routes.clj 用） ─────────────────────────────────────

(defn make-handlers [deps]
  {:place-order     #(place-order deps %)
   :get-order       #(get-order deps %)
   :pay-order       #(pay-order deps %)
   :ship-order      #(ship-order deps %)
   :deliver-order   #(deliver-order deps %)
   :cancel-order    #(cancel-order deps %)
   :register-user   #(register-user deps %)
   :login-user      #(login-user deps %)
   :sales-by-sku    #(sales-by-sku deps %)
   :top-users       #(top-users deps %)
   :window-mom      #(window-mom deps %)})