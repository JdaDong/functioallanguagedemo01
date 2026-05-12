(ns ecom.store.etl
  "CSV → :order/place 命令流。

   读 sample_orders.csv，输出：
     {:ok    [cmd ...]
      :reject [{:row n :raw 原始行 :reason str} ...]}

   清洗规则：
     - sku 空、qty 非正整数、unit-price 非数 → reject
     - placed-at 解析为毫秒时间戳（注入到命令）"
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- parse-int [s]
  (try (let [s (str/trim (str s))]
         (when (re-matches #"\d+" s)
           (Long/parseLong s)))
       (catch Exception _ nil)))

(defn- parse-num [s]
  (try (Double/parseDouble (str/trim (str s)))
       (catch Exception _ nil)))

(defn- parse-date-ms
  "yyyy-MM-dd → epoch ms (UTC midnight)"
  [s]
  (try
    (let [d (java.time.LocalDate/parse (str/trim (str s)))
          inst (.toInstant (.atStartOfDay d (java.time.ZoneOffset/UTC)))]
      (.toEpochMilli inst))
    (catch Exception _ nil)))

(defn- row->cmd
  "1 行 csv → 命令 或 [:reject reason]
   注意：每行是一条订单，items 只有 1 个 SKU"
  [row-idx headers row]
  (let [m (zipmap headers row)
        {:strs [order_id user_id sku qty unit_price coupon placed_at]} m
        sku       (str/trim (str sku))
        qty-int   (parse-int qty)
        price     (parse-num unit_price)
        ts        (parse-date-ms placed_at)
        coupon    (when-not (str/blank? coupon) (str/trim coupon))]
    (cond
      (str/blank? sku)        [:reject (str "sku blank")]
      (or (nil? qty-int) (not (pos? qty-int)))
      [:reject (str "qty invalid: " qty)]
      (or (nil? price) (not (pos? price)))
      [:reject (str "unit_price invalid: " unit_price)]
      (nil? ts)
      [:reject (str "placed_at invalid: " placed_at)]
      :else
      [:ok {:command/type :order/place
            :order-id     (str/trim order_id)
            :user-id      (str/trim user_id)
            :items        [{:sku sku :qty qty-int :unit-price price}]
            :coupon       coupon
            :placed-at-ms ts}])))

(defn load-csv
  "从 classpath 资源读 csv，返回 {:ok [...] :reject [...]}"
  [resource-name]
  (with-open [r (io/reader (io/resource resource-name))]
    (let [[h & rows] (csv/read-csv r)
          headers (mapv str/trim h)]
      (reduce
        (fn [acc [idx row]]
          (let [[tag v] (row->cmd idx headers row)]
            (case tag
              :ok     (update acc :ok conj v)
              :reject (update acc :reject conj
                              {:row (inc idx) :raw row :reason v}))))
        {:ok [] :reject []}
        (map-indexed vector rows)))))
