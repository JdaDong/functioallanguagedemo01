(ns ecom.domain.order
  "订单领域：command/event/state machine。

   订单状态机：
     :created → :paid → :shipped → :delivered
                    ↘ :cancelled       ↘ :refunded

   命令（用户意图，可拒）：
     :order/place         {:order-id :user-id :items :coupon}
     :order/pay           {:order-id :amount}
     :order/ship          {:order-id :tracking-no}
     :order/deliver       {:order-id}
     :order/cancel        {:order-id :reason}
     :order/refund        {:order-id :amount :partial?}

   事件（已发生事实，append-only）：
     :order.placed        {:order-id :user-id :items :total :coupon :ts}
     :order.paid          {:order-id :amount :ts}
     :order.shipped       {:order-id :tracking-no :ts}
     :order.delivered     {:order-id :ts}
     :order.cancelled     {:order-id :reason :ts}
     :order.refunded      {:order-id :amount :partial? :ts}"
  (:require [ecom.domain.pricing :as pricing]))

(def state-machine
  "states → 允许的下一状态集合"
  {:created   #{:paid :cancelled}
   :paid      #{:shipped :cancelled :refunded}
   :shipped   #{:delivered :refunded}
   :delivered #{:refunded}
   :cancelled #{}
   :refunded  #{}})

(defn can-transition? [from to]
  (contains? (get state-machine from #{}) to))

;; ─── command handlers ──────────────────────────────────────────────────
;; 签名：(handle-command store cmd) -> {:events [...]} | {:error msg}
;; 这里的 store 只用来读当前 order 状态；写由调用方做（dispatch-and-apply）

(defn now-ms [] (System/currentTimeMillis))

(defn- ev [type m]
  (assoc m :event/type type :ts (now-ms)))

(defn handle-place
  "下单：根据 items + coupon 计算 total，发出 :order.placed"
  [_store {:keys [order-id user-id items coupon]}]
  (cond
    (or (empty? items)) {:error "items 不能为空"}
    (some #(or (not (pos-int? (:qty %)))
               (not (pos? (:unit-price %)))) items)
    {:error "item qty/unit-price 非法"}
    :else
    (let [{:keys [total discount]} (pricing/price-quote items coupon)]
      {:events [(ev :order.placed
                    {:order-id order-id
                     :user-id  user-id
                     :items    items
                     :coupon   coupon
                     :discount discount
                     :total    total})]})))

(defn handle-pay
  [{:keys [find-order]} {:keys [order-id amount]}]
  (let [o (find-order order-id)]
    (cond
      (nil? o)                    {:error (str "no such order: " order-id)}
      (not (can-transition? (:status o) :paid))
      {:error (str "状态 " (:status o) " 不允许 pay")}
      (not= amount (:total o))    {:error (str "金额不符 expected=" (:total o))}
      :else
      {:events [(ev :order.paid {:order-id order-id :amount amount})]})))

(defn handle-ship
  [{:keys [find-order]} {:keys [order-id tracking-no]}]
  (let [o (find-order order-id)]
    (cond
      (nil? o) {:error "no such order"}
      (not (can-transition? (:status o) :shipped))
      {:error (str "状态 " (:status o) " 不允许 ship")}
      :else
      {:events [(ev :order.shipped {:order-id order-id :tracking-no tracking-no})]})))

(defn handle-deliver
  [{:keys [find-order]} {:keys [order-id]}]
  (let [o (find-order order-id)]
    (cond
      (nil? o) {:error "no such order"}
      (not (can-transition? (:status o) :delivered))
      {:error (str "状态 " (:status o) " 不允许 deliver")}
      :else
      {:events [(ev :order.delivered {:order-id order-id})]})))

(defn handle-cancel
  [{:keys [find-order]} {:keys [order-id reason]}]
  (let [o (find-order order-id)]
    (cond
      (nil? o) {:error "no such order"}
      (not (can-transition? (:status o) :cancelled))
      {:error (str "状态 " (:status o) " 不允许 cancel")}
      :else
      {:events [(ev :order.cancelled {:order-id order-id :reason (or reason "user-request")})]})))

(defn handle-refund
  [{:keys [find-order]} {:keys [order-id amount partial?]}]
  (let [o (find-order order-id)]
    (cond
      (nil? o) {:error "no such order"}
      (not (can-transition? (:status o) :refunded))
      {:error (str "状态 " (:status o) " 不允许 refund")}
      (and (not partial?) (not= amount (:total o)))
      {:error "全额退款金额不符"}
      (and partial? (> amount (:total o)))
      {:error "退款额超过订单总额"}
      :else
      {:events [(ev :order.refunded {:order-id order-id
                                     :amount   amount
                                     :partial? (boolean partial?)})]})))

(def command-handlers
  {:order/place   handle-place
   :order/pay     handle-pay
   :order/ship    handle-ship
   :order/deliver handle-deliver
   :order/cancel  handle-cancel
   :order/refund  handle-refund})

;; ─── projection（apply event → order state） ───────────────────────────

(defmulti apply-event (fn [_o e] (:event/type e)))

(defmethod apply-event :order.placed [_ e]
  {:order-id (:order-id e)
   :user-id  (:user-id e)
   :items    (:items e)
   :coupon   (:coupon e)
   :discount (:discount e)
   :total    (:total e)
   :status   :created
   :placed-at (:ts e)})

(defmethod apply-event :order.paid    [o e]
  (assoc o :status :paid :paid-at (:ts e)))
(defmethod apply-event :order.shipped [o e]
  (assoc o :status :shipped :tracking-no (:tracking-no e) :shipped-at (:ts e)))
(defmethod apply-event :order.delivered [o e]
  (assoc o :status :delivered :delivered-at (:ts e)))
(defmethod apply-event :order.cancelled [o e]
  (assoc o :status :cancelled :cancel-reason (:reason e) :cancelled-at (:ts e)))
(defmethod apply-event :order.refunded [o e]
  (assoc o :status :refunded :refund-amount (:amount e) :refunded-at (:ts e)))
