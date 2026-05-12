(ns ecom.conservation-test
  "守恒律测试：1000 个并发预占后，on-hand + reserved 之和不变。
   这是 STM ref dosync 工业语义的核心。"
  (:require [clojure.test :refer [deftest is testing]]
            [ecom.domain.inventory :as inv]))

(deftest stock-conservation-under-concurrent-reserve
  (testing "1000 个并发 reserve! 后总数守恒"
    (let [stock (inv/fresh-stock {"SKU-A" 2000 "SKU-B" 1000})
          before (inv/total-units stock)
          n 1000
          ths (mapv (fn [_]
                      (future (inv/reserve! stock "SKU-A" 1)))
                    (range n))]
      (run! deref ths)
      (is (= before (inv/total-units stock))
          "守恒律：on-hand + reserved 总和 = 初始总数")
      (let [snap (inv/snapshot stock)]
        (is (= 1000 (-> snap (get "SKU-A") :reserved)))
        (is (= 1000 (-> snap (get "SKU-A") :on-hand)))))))

(deftest insufficient-stock-rejected
  (testing "库存不足时返回 :insufficient-stock"
    (let [stock (inv/fresh-stock {"SKU-A" 5})
          ;; 5 个 reserve 1 应全成功
          ok-results (mapv (fn [_] @(future (inv/reserve! stock "SKU-A" 1)))
                           (range 5))
          ;; 第 6 个应失败
          fail (inv/reserve! stock "SKU-A" 1)]
      (is (every? #{:ok} ok-results))
      (is (= [:error :insufficient-stock] fail)))))

(deftest reserve-many-rollback-on-partial-failure
  (testing "多 sku 原子预占：任一 sku 不足 → 全部回滚"
    (let [stock (inv/fresh-stock {"SKU-A" 100 "SKU-B" 5})
          before (inv/snapshot stock)]
      (is (thrown? Throwable
                   (inv/reserve-many!
                     stock
                     [{:sku "SKU-A" :qty 10}    ;; 这个本来能预占
                      {:sku "SKU-B" :qty 100}]))) ;; 这个会触发 throw
      (is (= before (inv/snapshot stock))
          "dosync 自动回滚：库存与异常前一致"))))
