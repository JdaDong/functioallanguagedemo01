;; demo 38 — UTXO 账本（Bitcoin / Cardano 账本核心模型）
;;
;; 模型：
;;   utxo-id   = {:tx tx-hash :idx output-index}
;;   utxo      = {:owner addr :amount n}
;;   tx        = {:id "T-001"
;;                :inputs  [utxo-id ...]    ;; 必须存在于 utxo-set，否则非法
;;                :outputs [{:owner :amount} ...]
;;                :sigs    {addr :ok ...}}  ;; demo 简化：所有 input owner 都给签名即可
;;
;; 不变量（守恒律）：sum(inputs) = sum(outputs) + fee；fee >= 0
;;
;; 运行：clojure -M 38_utxo_ledger.clj

(ns demo38)

;; ───────────────────────────────────────────────────────────────────────
;; ledger 状态：utxo-set + 已收 fee 总额
;; ───────────────────────────────────────────────────────────────────────
(defn empty-ledger []
  {:utxo-set {}                        ;; utxo-id -> {:owner :amount}
   :fees     0
   :tx-log   []})                      ;; 已确认的 tx，按顺序

;; ───────────────────────────────────────────────────────────────────────
;; 工具
;; ───────────────────────────────────────────────────────────────────────
(defn sum [coll] (reduce + 0 coll))

(defn lookup-utxo [ledger uid]
  (get-in ledger [:utxo-set uid]))

;; ───────────────────────────────────────────────────────────────────────
;; 校验 + 应用 tx；返回 [新 ledger, nil] 或 [ledger, error-msg]
;; ───────────────────────────────────────────────────────────────────────
(defn apply-tx [ledger {:keys [id inputs outputs sigs] :as tx}]
  (let [in-utxos (map #(lookup-utxo ledger %) inputs)]
    (cond
      ;; 1. 双花 / 引用不存在
      (some nil? in-utxos)
      [ledger (str "tx " id ": input 不在 utxo-set（双花或不存在）")]

      ;; 2. 签名缺失
      (let [owners (set (map :owner in-utxos))]
        (not= owners (set (filter sigs owners))))
      [ledger (str "tx " id ": 签名不完整")]

      ;; 3. 守恒律：sum-in >= sum-out
      (let [in-sum  (sum (map :amount in-utxos))
            out-sum (sum (map :amount outputs))]
        (< in-sum out-sum))
      [ledger (str "tx " id ": 输出超过输入（凭空印钞）")]

      :else
      (let [in-sum  (sum (map :amount in-utxos))
            out-sum (sum (map :amount outputs))
            fee     (- in-sum out-sum)
            ;; 移除花掉的 utxo
            utxo'   (reduce dissoc (:utxo-set ledger) inputs)
            ;; 加入新产生的 utxo（id = {:tx id :idx i}）
            utxo''  (reduce-kv (fn [m i out]
                                 (assoc m {:tx id :idx i} out))
                               utxo'
                               (vec outputs))]
        [{:utxo-set utxo''
          :fees     (+ (:fees ledger) fee)
          :tx-log   (conj (:tx-log ledger) tx)}
         nil]))))

;; ───────────────────────────────────────────────────────────────────────
;; 创世：直接给地址凭空发钱（仅创世 tx 允许），后续都必须从已有 utxo 流转
;; ───────────────────────────────────────────────────────────────────────
(defn genesis [ledger txid issuances]
  ;; issuances: [{:owner :amount} ...]
  (let [utxo (reduce-kv (fn [m i out]
                          (assoc m {:tx txid :idx i} out))
                        (:utxo-set ledger)
                        (vec issuances))]
    (assoc ledger :utxo-set utxo
           :tx-log (conj (:tx-log ledger) {:id txid :type :genesis :outputs issuances}))))

;; ───────────────────────────────────────────────────────────────────────
;; 查询
;; ───────────────────────────────────────────────────────────────────────
(defn balance-of [ledger addr]
  (sum (->> (:utxo-set ledger) vals (filter #(= addr (:owner %))) (map :amount))))

(defn total-supply [ledger]
  (+ (sum (map :amount (vals (:utxo-set ledger))))
     (:fees ledger)))

;; ───────────────────────────────────────────────────────────────────────
;; demo
;; ───────────────────────────────────────────────────────────────────────
(defn show [tag ledger]
  (println (format "  [%s] Alice=%d Bob=%d Cy=%d  fees=%d  total-supply=%d"
                   tag
                   (balance-of ledger :alice)
                   (balance-of ledger :bob)
                   (balance-of ledger :cy)
                   (:fees ledger)
                   (total-supply ledger))))

(defn -main [& _]
  (println "── 0. 创世：给 Alice 100 ──")
  (let [L0 (genesis (empty-ledger) "G" [{:owner :alice :amount 100}])]
    (show "G" L0)
    (assert (= 100 (total-supply L0)))

    (println "\n── 1. Alice → Bob 30，找零回 Alice 70 - 1 fee = 69 ──")
    ;; Alice 的 utxo id：{:tx "G" :idx 0}
    (let [tx1 {:id "T-001"
               :inputs  [{:tx "G" :idx 0}]
               :outputs [{:owner :bob :amount 30}
                         {:owner :alice :amount 69}]
               :sigs    {:alice :ok}}
          [L1 err] (apply-tx L0 tx1)]
      (assert (nil? err) err)
      (show "T-001" L1)
      (assert (= 100 (total-supply L1)) "总量守恒")

      (println "\n── 2. 双花尝试：Alice 再次花掉 G:0（已被花掉）→ 应被拒绝 ──")
      (let [tx-bad {:id "T-BAD"
                    :inputs  [{:tx "G" :idx 0}]
                    :outputs [{:owner :cy :amount 50}]
                    :sigs    {:alice :ok}}
            [L-same err] (apply-tx L1 tx-bad)]
        (println "  ←" err)
        (assert (some? err))
        (assert (= L-same L1) "状态未变"))

      (println "\n── 3. Bob → Cy 25（fee=5）──")
      ;; Bob 的 utxo：T-001:0
      (let [tx2 {:id "T-002"
                 :inputs  [{:tx "T-001" :idx 0}]
                 :outputs [{:owner :cy :amount 25}]
                 :sigs    {:bob :ok}}
            [L2 err] (apply-tx L1 tx2)]
        (assert (nil? err) err)
        (show "T-002" L2)
        (assert (= 100 (total-supply L2)))

        (println "\n── 4. 凭空印钞尝试：Cy 输入 25，输出 100 → 应被拒绝 ──")
        (let [tx-bad {:id "T-INFLATE"
                     :inputs  [{:tx "T-002" :idx 0}]
                     :outputs [{:owner :cy :amount 100}]
                     :sigs    {:cy :ok}}
              [_ err] (apply-tx L2 tx-bad)]
          (println "  ←" err)
          (assert (some? err)))

        (println "\n── 5. 缺签名：Alice 想花 Cy 的钱 → 应被拒绝 ──")
        (let [tx-bad {:id "T-NOSIG"
                     :inputs  [{:tx "T-002" :idx 0}]    ;; 这是 Cy 的 utxo
                     :outputs [{:owner :alice :amount 25}]
                     :sigs    {:alice :ok}}             ;; 但只有 Alice 的签名
              [_ err] (apply-tx L2 tx-bad)]
          (println "  ←" err)
          (assert (some? err)))

        (println "\n── 6. 多 input 合并：Alice 70 - 1 = 69，再来一个创世给 Alice 31 → 合并花 100 ──")
        (let [L3a (genesis L2 "G2" [{:owner :alice :amount 31}])
              tx3 {:id "T-003"
                   :inputs  [{:tx "T-001" :idx 1} {:tx "G2" :idx 0}]
                   :outputs [{:owner :cy :amount 100}]
                   :sigs    {:alice :ok}}
              [L3 err] (apply-tx L3a tx3)]
          (assert (nil? err) err)
          (show "T-003" L3)
          ;; total-supply 应当是 100 + 31 = 131
          (assert (= 131 (total-supply L3)))))))

  (println "\n=== 一句话总结 ===")
  (println "- UTXO = unspent transaction output；账户余额 = 拥有的所有 UTXO 之和")
  (println "- tx 的输入必须是当前 utxo-set 的成员，且都被花掉（消失），双花在协议层被拒")
  (println "- sum(inputs) = sum(outputs) + fee；fee >= 0；总量守恒")
  (println "- 状态转换是纯函数 (ledger, tx) → (ledger', err?)，可以并行验证、可以 replay")
  (println "- Bitcoin / Cardano / Ergo 都用这个模型；Ethereum 用 account-balance 模型反例")
  (shutdown-agents))

(-main)
