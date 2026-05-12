;; demo 40 — Event Sourcing（Nubank 风格）
;;
;; 核心三件套：
;;   1) Command   = 用户意图   (data, 可拒绝)
;;   2) Event     = 已发生事实 (data, 不可改, append-only)
;;   3) Projection = 把事件流折叠成查询视图（state = (reduce apply-event init events)）
;;
;; 关键不变量：state 完全由 events 决定，可任意 prefix replay。
;; "状态 ≠ 真相，事件 = 真相"
;;
;; 这是 Nubank/Datomic/Kafka 阵营的标准心智。
;;
;; 运行：clojure -M 40_nubank_style_event_sourcing.clj

(ns demo40)

;; ───────────────────────────────────────────────────────────────────────
;; 投影器：apply-event :: (state, event) -> state'
;; 这里的 state = {:accounts {acct -> balance}}
;; ───────────────────────────────────────────────────────────────────────
(defn empty-state [] {:accounts {} :tx-count 0})

(defmulti apply-event (fn [_ e] (:type e)))

(defmethod apply-event :account-opened
  [s {:keys [account]}]
  (-> s
      (assoc-in [:accounts account] 0M)
      (update :tx-count inc)))

(defmethod apply-event :money-deposited
  [s {:keys [account amount]}]
  (-> s
      (update-in [:accounts account] + amount)
      (update :tx-count inc)))

(defmethod apply-event :money-withdrawn
  [s {:keys [account amount]}]
  (-> s
      (update-in [:accounts account] - amount)
      (update :tx-count inc)))

(defmethod apply-event :money-transferred
  [s {:keys [from to amount]}]
  (-> s
      (update-in [:accounts from] - amount)
      (update-in [:accounts to]   + amount)
      (update :tx-count inc)))

(defn project [events]
  (reduce apply-event (empty-state) events))

;; ───────────────────────────────────────────────────────────────────────
;; 命令处理器：handle-command :: (state, cmd) -> [events] | error
;; 命令可拒，事件不可逆
;; ───────────────────────────────────────────────────────────────────────
(defmulti handle-command (fn [_ c] (:type c)))

(defmethod handle-command :open-account
  [s {:keys [account]}]
  (if (contains? (:accounts s) account)
    {:error (str "account already exists: " account)}
    {:events [{:type :account-opened :account account}]}))

(defmethod handle-command :deposit
  [s {:keys [account amount]}]
  (cond
    (not (contains? (:accounts s) account))
    {:error (str "no such account: " account)}
    (not (pos? amount))
    {:error "amount must be > 0"}
    :else
    {:events [{:type :money-deposited :account account :amount amount}]}))

(defmethod handle-command :withdraw
  [s {:keys [account amount]}]
  (cond
    (not (contains? (:accounts s) account))
    {:error (str "no such account: " account)}
    (not (pos? amount))
    {:error "amount must be > 0"}
    (< (get-in s [:accounts account]) amount)
    {:error (str "insufficient funds in " account)}
    :else
    {:events [{:type :money-withdrawn :account account :amount amount}]}))

(defmethod handle-command :transfer
  [s {:keys [from to amount]}]
  (cond
    (not (contains? (:accounts s) from)) {:error (str "no such from: " from)}
    (not (contains? (:accounts s) to))   {:error (str "no such to: " to)}
    (not (pos? amount))                  {:error "amount must be > 0"}
    (< (get-in s [:accounts from]) amount)
    {:error (str "insufficient funds in " from)}
    :else
    ;; 注意：用单一 :money-transferred 事件而不是两个，保证原子性
    {:events [{:type :money-transferred :from from :to to :amount amount}]}))

;; ───────────────────────────────────────────────────────────────────────
;; CommandBus：状态 = events 持久存储 + 当前投影
;;   每次 dispatch：
;;     1. 用当前 state 校验命令
;;     2. 命令通过则 append events 到日志，重投影
;;     3. 拒绝则不变
;; ───────────────────────────────────────────────────────────────────────
(defn make-bus []
  (atom {:events [] :state (empty-state)}))

(defn dispatch! [bus cmd]
  (let [{:keys [state]} @bus
        result (handle-command state cmd)]
    (if-let [err (:error result)]
      (do (println (format "  ✗ %s rejected: %s" (:type cmd) err))
          {:rejected err})
      (let [new-events (concat (:events @bus) (:events result))
            new-state  (project new-events)]
        (reset! bus {:events new-events :state new-state})
        (println (format "  ✓ %s → %s" (:type cmd)
                         (mapv :type (:events result))))
        {:events (:events result)}))))

;; ───────────────────────────────────────────────────────────────────────
;; demo
;; ───────────────────────────────────────────────────────────────────────
(defn show-balances [bus]
  (println "  balances:" (into (sorted-map) (:accounts (:state @bus))))
  (println "  事件总数:" (count (:events @bus))))

(defn -main [& _]
  (println "── 1. 命令流：开户 / 充值 / 取款 / 转账 ──")
  (let [bus (make-bus)]
    (dispatch! bus {:type :open-account :account "alice"})
    (dispatch! bus {:type :open-account :account "bob"})
    (dispatch! bus {:type :deposit :account "alice" :amount 100M})
    (dispatch! bus {:type :deposit :account "bob"   :amount 50M})
    (dispatch! bus {:type :transfer :from "alice" :to "bob" :amount 30M})
    (dispatch! bus {:type :withdraw :account "bob" :amount 20M})
    (show-balances bus)

    ;; 守恒律：alice + bob = 100 + 50 - 20 = 130
    (let [bal (:accounts (:state @bus))]
      (assert (= 70M (bal "alice")))
      (assert (= 60M (bal "bob")))
      (assert (= 130M (+ (bal "alice") (bal "bob")))))

    (println "\n── 2. 拒绝：余额不够 / 重复开户 / 负金额 ──")
    (dispatch! bus {:type :open-account :account "alice"})            ;; dup
    (dispatch! bus {:type :withdraw :account "alice" :amount 9999M})  ;; insufficient
    (dispatch! bus {:type :deposit  :account "alice" :amount -5M})    ;; bad amount
    (dispatch! bus {:type :transfer :from "ghost" :to "bob" :amount 1M}) ;; no such
    (show-balances bus)
    ;; 拒绝命令不应产生事件
    (assert (= 6 (count (:events @bus))) "rejects must not append events")

    (println "\n── 3. Replay 任意 prefix 还原历史状态 ──")
    (let [all-events (:events @bus)]
      (doseq [n [0 2 3 5 6]]
        (let [s (project (take n all-events))]
          (println (format "  events[..%d) → %s   tx-count=%d"
                           n
                           (into (sorted-map) (:accounts s))
                           (:tx-count s)))))
      ;; 完整 replay 必须等于当前 state
      (assert (= (:state @bus) (project all-events))
              "replay must reproduce exact state"))

    (println "\n── 4. 事件流可审计：导出全部事件 ──")
    (doseq [[i e] (map-indexed vector (:events @bus))]
      (println (format "  #%d %s" i e)))

    (println "\n── 5. What-if：不污染主流的「假设性」命令 ──")
    (let [hypothetical (dispatch! (atom @bus)
                                  {:type :withdraw :account "alice" :amount 50M})]
      (println "  hypothetical 派发结果：" hypothetical)
      (println "  主 bus alice 余额（未变）：" (get-in @bus [:state :accounts "alice"]))
      (assert (= 70M (get-in @bus [:state :accounts "alice"])))))

  (println "\n=== 一句话总结 ===")
  (println "- Command（意图，可拒） vs Event（事实，append-only）")
  (println "- state = (reduce apply-event init events)")
  (println "- 想知道任意时刻的状态？replay 该 prefix")
  (println "- 想加新视图？写新的 projection，从头 replay")
  (println "- Nubank 全部账务系统就是这套，加上 Kafka 做事件分发")
  (println "- '状态不是真相，事件才是真相' —— event sourcing 的口号")
  (shutdown-agents))

(-main)
