(ns ecom.analytics.window
  "时间窗口 + 同比 / 环比。

   术语：
     环比（MoM, month-over-month）：本月 vs 上月
     同比（YoY, year-over-year）：本月 vs 去年同月

   API:
     month-bounds 2026 4    → [start-ms, end-ms)
     compare-window db curr prev metric-fn → {:curr X :prev Y :delta :pct}"
  (:require [ecom.analytics.mbql :as mbql]))

(defn month-bounds
  "返回 [start-ms, end-ms-inclusive]，配合 mbql :between 闭区间使用。
   end-ms-inclusive = 下月 1 日 0 点 - 1ms"
  [year month]
  (let [start (java.time.LocalDate/of year month 1)
        end   (.plusMonths start 1)
        to-ms (fn [d] (.toEpochMilli (.toInstant (.atStartOfDay d java.time.ZoneOffset/UTC))))]
    [(to-ms start) (dec (to-ms end))]))

(defn prev-month [year month]
  (if (= month 1) [(dec year) 12] [year (dec month)]))

(defn prev-year-same-month [year month]
  [(dec year) month])

(defn total-sales
  "窗口内总销售额"
  [db [from to]]
  (let [rows (mbql/run-mbql db
               {:filter [:and [:between :order/placed-at from to]]
                :aggregate [[:sum [:* :item/qty :item/unit-price]]]})]
    (or (some-> rows first first) 0)))

(defn order-count
  [db [from to]]
  (let [rows (mbql/run-mbql db
               {:filter [:and [:between :order/placed-at from to]]
                :aggregate [[:count :order/id]]})]
    ;; 每个 order 会被 item 展开成多行；去重
    (or (some-> rows first first) 0)))

(defn- pct [curr prev]
  (if (zero? prev)
    (if (zero? curr) 0.0 nil) ; 上期为 0：增长率无意义
    (* 100.0 (/ (double (- curr prev)) (double prev)))))

(defn compare-windows
  "对比两个窗口的同一指标。metric-fn :: db -> [from to] -> number"
  [db curr-window prev-window metric-fn]
  (let [c (metric-fn db curr-window)
        p (metric-fn db prev-window)]
    {:curr c
     :prev p
     :delta (- c p)
     :pct   (pct c p)}))

(defn mom
  "环比：year/month 与上月对比 metric"
  [db year month metric-fn]
  (compare-windows db
                   (month-bounds year month)
                   (apply month-bounds (prev-month year month))
                   metric-fn))

(defn yoy
  "同比：year/month 与去年同月对比 metric"
  [db year month metric-fn]
  (compare-windows db
                   (month-bounds year month)
                   (apply month-bounds (prev-year-same-month year month))
                   metric-fn))
