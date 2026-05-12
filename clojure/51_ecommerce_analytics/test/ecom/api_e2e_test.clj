(ns ecom.api-e2e-test
  "API E2E 测试：起 Integrant 系统 → in-process 调 ring handler →
   关系统。3 个核心场景：下单/付款/查投影。"
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [ecom.system :as sys]
            [jsonista.core :as json]))

(def ^:dynamic *system* nil)

(defn- json-req [method uri body]
  {:request-method method
   :uri uri
   :headers {"content-type" "application/json"}
   :body (when body (java.io.ByteArrayInputStream.
                      (.getBytes (json/write-value-as-string body) "UTF-8")))})

(defn- read-body [resp]
  (let [b (:body resp)]
    (if (string? b)
      (json/read-value b (json/object-mapper {:decode-key-fn keyword}))
      b)))

(defn- handler [] (:api/handler *system*))

(defn with-system [f]
  (binding [*system* (sys/start {:port 0})]   ;; port=0 让 jetty 选随机端口
    (try (f)
         (finally (sys/stop *system*)))))

(use-fixtures :each with-system)

(deftest place-order-roundtrip
  (testing "POST /orders → 200 + total 计算"
    (let [resp ((handler)
                (json-req :post "/orders"
                  {:order-id "E2E-1" :user-id "U-X"
                   :items [{:sku "SKU-A" :qty 2 :unit-price 50.0}]
                   :coupon nil}))]
      (is (= 200 (:status resp)))
      (is (= 100.0 (:total (read-body resp)))))))

(deftest pay-after-place
  (testing "下单 → 付款，状态从 :created 到 :paid"
    (let [_ ((handler)
             (json-req :post "/orders"
               {:order-id "E2E-2" :user-id "U-Y"
                :items [{:sku "SKU-B" :qty 1 :unit-price 120.0}]}))
          pay ((handler)
                (json-req :post "/orders/E2E-2/pay" {:amount 120.0}))]
      (is (= 200 (:status pay)))
      (is (= "paid" (name (or (:status (read-body pay)) "")))))))

(deftest get-unknown-order-404
  (testing "查不存在的订单返回 404"
    (let [resp ((handler) (json-req :get "/orders/NO-SUCH" nil))]
      (is (= 404 (:status resp))))))
