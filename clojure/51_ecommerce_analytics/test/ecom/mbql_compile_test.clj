(ns ecom.mbql-compile-test
  "MBQL 编译器测试：3 个查询输出与手算一致。"
  (:require [clojure.test :refer [deftest is testing]]
            [ecom.store.datascript :as store]
            [ecom.store.snapshot   :as snap]
            [ecom.analytics.mbql   :as mbql]))

(defn- ts [y m d]
  (.toEpochMilli (.toInstant (.atStartOfDay
                               (java.time.LocalDate/of y m d)
                               java.time.ZoneOffset/UTC))))

(defn- seed-orders!
  "造 4 张 delivered 订单；返回 conn"
  []
  (let [conn (store/fresh-conn)
        log  (snap/fresh-event-log)
        orders [{:order-id "T-1" :user-id "U-A"
                 :items [{:sku "SKU-X" :qty 2 :unit-price 50.0}]
                 :total 100.0 :placed-at (ts 2026 4 5)}
                {:order-id "T-2" :user-id "U-A"
                 :items [{:sku "SKU-Y" :qty 1 :unit-price 200.0}]
                 :total 200.0 :placed-at (ts 2026 4 6)}
                {:order-id "T-3" :user-id "U-B"
                 :items [{:sku "SKU-X" :qty 3 :unit-price 50.0}]
                 :total 150.0 :placed-at (ts 2026 4 7)}
                {:order-id "T-4" :user-id "U-B"
                 :items [{:sku "SKU-Y" :qty 2 :unit-price 200.0}]
                 :total 400.0 :placed-at (ts 2026 4 8)}]]
    (doseq [{:keys [order-id user-id items total placed-at]} orders]
      (snap/append! log conn
                    {:event/type :order.placed :order-id order-id :user-id user-id
                     :items items :total total :ts placed-at})
      (snap/append! log conn {:event/type :order.paid :order-id order-id
                              :amount total :ts (inc placed-at)})
      (snap/append! log conn {:event/type :order.delivered :order-id order-id
                              :ts (+ placed-at 2)}))
    conn))

(deftest sales-by-sku-aggregation
  (testing "按 SKU 聚合销量、销售额、订单数与手算一致"
    (let [conn (seed-orders!)
          rows (mbql/sales-by-sku @conn {})
          rowmap (into {} (map (fn [[sku qty rev cnt]]
                                 [sku {:qty qty :rev rev :cnt cnt}])
                               rows))]
      ;; 手算：
      ;;   SKU-X: 2(T-1) + 3(T-3) = 5 件，销售额 100+150 = 250，订单 2
      ;;   SKU-Y: 1(T-2) + 2(T-4) = 3 件，销售额 200+400 = 600，订单 2
      (is (= {:qty 5 :rev 250.0 :cnt 2} (get rowmap "SKU-X")))
      (is (= {:qty 3 :rev 600.0 :cnt 2} (get rowmap "SKU-Y"))))))

(deftest top-users-sort-correctness
  (testing "top-users 按总额降序，并 limit n"
    (let [conn (seed-orders!)
          rows (mbql/top-users @conn {:n 2})]
      ;; 手算：
      ;;   U-B: T-3+T-4 = 150+400 = 550, 2 单
      ;;   U-A: T-1+T-2 = 100+200 = 300, 2 单
      (is (= 2 (count rows)))
      (is (= "U-B" (first (first rows))))
      (is (= 550.0 (last (first rows)))))))

(deftest filter-by-date-range
  (testing ":between 闭区间筛选"
    (let [conn (seed-orders!)
          ;; 只取 4 月 5 日和 4 月 6 日（含） → T-1 + T-2
          rows (mbql/run-mbql @conn
                  {:filter [:and
                            [:between :order/placed-at
                             (ts 2026 4 5) (ts 2026 4 6)]]
                   :aggregate [[:sum [:* :item/qty :item/unit-price]]]})]
      ;; 手算：100 + 200 = 300
      (is (= [[300.0]] (mapv vec rows))))))
