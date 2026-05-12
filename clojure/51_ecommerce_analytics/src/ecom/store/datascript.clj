(ns ecom.store.datascript
  "DataScript schema + 投影引擎。

   设计：事件即写（每个 event → 一行 transact）。

   schema 关键点：
     - :order/id        unique identity（订单主键）
     - :order/user      ref → user 实体
     - :order/items     cardinality/many ref → order-item 实体
     - :user/id         unique identity
     - 状态字段直接挂 :order/status，每次 apply-event 通过 retract+assert 改写

   导出：
     fresh-conn       创建新连接（带 schema）
     apply-event!     事件 → 一行 transact
     find-order       工具：按 order-id 拉投影 map
     find-orders-all  工具：列全部订单
     all-events       供 snapshot 用：当前已落库的 event 数（投影计数）"
  (:require [datascript.core :as d]))

(def schema
  {:order/id          {:db/unique :db.unique/identity}
   :order/user        {:db/valueType :db.type/ref}
   :order/items       {:db/cardinality :db.cardinality/many
                       :db/valueType   :db.type/ref}
   :order/status      {}
   :order/total       {}
   :order/discount    {}
   :order/coupon      {}
   :order/placed-at   {}
   :order/paid-at     {}
   :order/shipped-at  {}
   :order/delivered-at {}
   :order/cancelled-at {}
   :order/refunded-at {}
   :order/cancel-reason {}
   :order/tracking-no {}
   :order/refund-amount {}

   :item/sku          {:db/index true}
   :item/qty          {}
   :item/unit-price   {}

   :user/id           {:db/unique :db.unique/identity}})

(defn fresh-conn []
  (d/create-conn schema))

;; ─── 投影：事件 → tx data ─────────────────────────────────────────────

(defn- ensure-user-tx [user-id]
  [{:user/id user-id}])

(defn- placed-tx [{:keys [order-id user-id items coupon discount total ts]}]
  (let [item-tempids (map-indexed (fn [i _] (str "item-" i)) items)
        item-txs     (map (fn [tid {:keys [sku qty unit-price]}]
                            {:db/id        tid
                             :item/sku     sku
                             :item/qty     qty
                             :item/unit-price unit-price})
                          item-tempids items)]
    (concat
      (ensure-user-tx user-id)
      item-txs
      [{:order/id        order-id
        :order/user      [:user/id user-id]
        :order/items     (vec item-tempids)
        :order/status    :created
        :order/total     total
        :order/discount  (or discount 0)
        :order/coupon    (or coupon "")
        :order/placed-at ts}])))

(defmulti event->tx :event/type)

(defmethod event->tx :order.placed [e]
  (placed-tx e))

(defmethod event->tx :order.paid [{:keys [order-id ts]}]
  [{:order/id order-id :order/status :paid :order/paid-at ts}])

(defmethod event->tx :order.shipped [{:keys [order-id tracking-no ts]}]
  [{:order/id order-id :order/status :shipped
    :order/tracking-no tracking-no :order/shipped-at ts}])

(defmethod event->tx :order.delivered [{:keys [order-id ts]}]
  [{:order/id order-id :order/status :delivered :order/delivered-at ts}])

(defmethod event->tx :order.cancelled [{:keys [order-id reason ts]}]
  [{:order/id order-id :order/status :cancelled
    :order/cancel-reason (or reason "") :order/cancelled-at ts}])

(defmethod event->tx :order.refunded [{:keys [order-id amount ts]}]
  [{:order/id order-id :order/status :refunded
    :order/refund-amount amount :order/refunded-at ts}])

(defn apply-event!
  "事件即写：每个 event 一次 transact。返回 tx-report。"
  [conn event]
  (d/transact! conn (event->tx event)))

;; ─── 查询工具 ──────────────────────────────────────────────────────────

(defn find-order [db order-id]
  (when-let [eid (d/q '[:find ?e .
                        :in $ ?oid
                        :where [?e :order/id ?oid]]
                      db order-id)]
    (let [order (d/pull db '[* {:order/items [*]
                                :order/user [:user/id]}] eid)]
      (-> order
          (update :order/user :user/id)))))

(defn find-orders-all [db]
  (mapv #(d/pull db '[* {:order/items [*]
                         :order/user [:user/id]}] %)
        (d/q '[:find [?e ...]
               :where [?e :order/id]]
             db)))

(defn order-count [db]
  (or (d/q '[:find (count ?e) .
             :where [?e :order/id]] db) 0))
