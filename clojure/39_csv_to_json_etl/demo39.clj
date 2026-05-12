(ns demo39
  "ETL 实战：CSV 入 → 清洗/校验/聚合 → JSON 出
   呼应 Haskell demo 48 的「事务流水线」思路。

   流水线：
     read-csv → header-keywordize → trim → coerce → validate
              → split (good / bad) → aggregate good
              → write JSON (汇总 + 拒绝行附带原因)

   运行：clojure -M:run"
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [jsonista.core :as j]))

;; ───────────────────────────────────────────────────────────────────────
;; 1. 读 CSV → seq of map
;; ───────────────────────────────────────────────────────────────────────
(defn read-orders [path]
  (with-open [r (io/reader path)]
    (let [[header & rows] (doall (csv/read-csv r))
          ks (mapv keyword header)]
      (mapv #(zipmap ks %) rows))))

;; ───────────────────────────────────────────────────────────────────────
;; 2. 清洗 + 校验：每行 → {:row clean-map :error nil} 或 {:row m :error reason}
;; ───────────────────────────────────────────────────────────────────────
(defn parse-int  [s] (try (Long/parseLong s)         (catch Exception _ nil)))
(defn parse-decimal [s] (try (BigDecimal. s)         (catch Exception _ nil)))

(defn clean-row [{:keys [order_id user product qty price date] :as row}]
  (let [trim (fn [s] (when s (.trim ^String s)))
        product*  (trim product)
        qty*      (parse-int (trim qty))
        price*    (parse-decimal (trim price))]
    (cond
      (or (nil? product*) (= "" product*))
      {:row row :error "missing product"}

      (nil? qty*)
      {:row row :error (str "qty not a number: " qty)}

      (nil? price*)
      {:row row :error (str "price not a number: " price)}

      (neg? qty*)
      {:row row :error "qty must be >= 0"}

      :else
      {:row {:order-id (trim order_id)
             :user     (trim user)
             :product  product*
             :qty      qty*
             :price    price*
             :date     (trim date)
             :subtotal (.doubleValue (.multiply price* (BigDecimal. (long qty*))))}
       :error nil})))

;; ───────────────────────────────────────────────────────────────────────
;; 3. 聚合（在已清洗的 good rows 上）
;;    by-user:    {user -> {:n :total}}
;;    by-product: {product -> {:n :qty :revenue}}
;; ───────────────────────────────────────────────────────────────────────
(defn group-sum [rows key-fn val-fn]
  (reduce (fn [m row]
            (let [k (key-fn row)]
              (-> m
                  (update-in [k :n]   (fnil inc 0))
                  (update-in [k :sum] (fnil + 0) (val-fn row)))))
          {} rows))

(defn aggregate [rows]
  {:total-orders   (count rows)
   :total-revenue  (->> rows (map :subtotal) (reduce + 0.0))
   :by-user        (group-sum rows :user :subtotal)
   :by-product     (->> rows
                        (group-by :product)
                        (reduce-kv (fn [m p ps]
                                     (assoc m p {:n        (count ps)
                                                 :qty      (reduce + 0 (map :qty ps))
                                                 :revenue  (reduce + 0.0 (map :subtotal ps))}))
                                   {}))})

;; ───────────────────────────────────────────────────────────────────────
;; 4. 流水线主函数
;; ───────────────────────────────────────────────────────────────────────
(def mapper (j/object-mapper {:pretty true}))

(defn run-etl [in-path out-path]
  (let [raw      (read-orders in-path)
        cleaned  (map clean-row raw)
        good     (->> cleaned (remove :error) (map :row))
        bad      (->> cleaned (filter :error) (map (fn [{:keys [row error]}]
                                                     {:row row :error error})))
        summary  (aggregate good)
        report   {:summary  summary
                  :rejected bad
                  :good-rows good}]
    (with-open [w (io/writer out-path)]
      (.write w ^String (j/write-value-as-string report mapper)))
    {:total       (count raw)
     :good        (count good)
     :bad         (count bad)
     :revenue     (:total-revenue summary)
     :rejected    bad}))

(defn -main [& _]
  (let [in  "resources/orders.csv"
        out "out.json"
        result (run-etl in out)]
    (println "── ETL 完成 ──")
    (println (format "  读取 %d 行，清洗后 %d 通过，%d 被拒绝"
                     (:total result) (:good result) (:bad result)))
    (println (format "  总营收 = %.2f" (:revenue result)))
    (println "  被拒行：")
    (doseq [{:keys [row error]} (:rejected result)]
      (println "   - " (:order_id row) "→" error))

    (println "\n  输出文件 out.json （前 600 字符）：")
    (println "  ----")
    (let [content (slurp out)]
      (println (subs content 0 (min 600 (count content))))
      (println "  ...")
      (println "  ---- (full size:" (count content) "bytes)"))

    (assert (= 8 (:total result)))
    (assert (= 6 (:good result)))                      ;; 6 通过
    (assert (= 2 (:bad result)))                       ;; O-006 缺 product, O-007 qty=abc
    (println "\n=== 一句话总结 ===")
    (println "- ETL 流水线 = 一连串纯函数：read → clean → split → aggregate → serialize")
    (println "- 失败行不抛异常，而是带 :error 元数据并行流动，最后并入报告")
    (println "- 这是函数式 ETL 的核心心智：错误也是数据，不是控制流"))
  (shutdown-agents))
