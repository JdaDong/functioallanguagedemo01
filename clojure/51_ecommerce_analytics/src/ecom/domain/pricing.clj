(ns ecom.domain.pricing
  "价格领域：纯函数策略表。

   策略：
     - 基础合计 = sum(qty × unit-price)
     - 优惠券：
        NEW10    新人券：满 100 减 10
        VIP20    会员券：8 折
        FLASH50  限时秒杀：满 200 减 50（最多减 50）
     - 满减阶梯：满 500 再减 30（自动叠加）

   函数式做法：策略表是 data，apply-coupon 是查表 + 一句 case。")

(def coupon-rules
  "coupon-code → 应用规则函数 (subtotal -> discount-amount)"
  {"NEW10"   (fn [s] (if (>= s 100) 10 0))
   "VIP20"   (fn [s] (* s 0.20))
   "FLASH50" (fn [s] (if (>= s 200) 50 0))})

(def tier-rules
  "无券满减阶梯：[阈值 减额]"
  [[500 30]
   [1000 80]
   [2000 200]])

(defn subtotal [items]
  (reduce + 0.0 (map (fn [{:keys [qty unit-price]}]
                       (* qty unit-price))
                     items)))

(defn coupon-discount [sub coupon]
  (if-let [f (get coupon-rules coupon)]
    (double (f sub))
    0.0))

(defn tier-discount [sub]
  (->> tier-rules
       (filter (fn [[t _]] (>= sub t)))
       (map second)
       (apply max 0)))

(defn price-quote
  "items + coupon → {:subtotal :discount :total}
   discount = max(coupon-discount, tier-discount)（互斥取大）"
  ([items] (price-quote items nil))
  ([items coupon]
   (let [sub (subtotal items)
         cd  (coupon-discount sub coupon)
         td  (tier-discount sub)
         d   (max cd td)
         total (max 0.0 (- sub d))]
     {:subtotal sub
      :discount d
      :total    total
      :coupon   coupon})))
