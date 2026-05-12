(ns ecom.domain.inventory
  "库存领域：用 STM ref 保证并发扣减原子性 + 守恒律。

   核心数据：
     stock = (ref {sku -> {:on-hand n :reserved n}})

   规则：
     reserve!  原子扣 :on-hand → +:reserved （下单时）
     release!  原子返还 :reserved → :on-hand（取消/退款时）
     ship!     原子扣 :reserved（发货时，sku 永久消失）
     restock!  增加 :on-hand（采购入库）

   守恒律：在任意瞬间
     sum(on-hand + reserved) + sum(shipped) + sum(restocked-out)
       = sum(initial) + sum(restocked-in)

   demo 16 STM 教过 dosync/alter；本 module 把它落到工业语义。")

(defn fresh-stock
  "用初始库存 map {sku → on-hand} 创建 stock ref"
  [init-map]
  (ref (into {} (for [[sku n] init-map]
                  [sku {:on-hand n :reserved 0}]))))

;; ─── pure helpers ─────────────────────────────────────────────────────

(defn- can-reserve? [{:keys [on-hand]} qty]
  (>= on-hand qty))

(defn- do-reserve [m qty]
  (-> m (update :on-hand - qty) (update :reserved + qty)))

(defn- do-release [m qty]
  (let [qty (min qty (:reserved m))]
    (-> m (update :reserved - qty) (update :on-hand + qty))))

(defn- do-ship [m qty]
  (let [qty (min qty (:reserved m))]
    (update m :reserved - qty)))

;; ─── STM 入口 ─────────────────────────────────────────────────────────

(defn reserve!
  "原子尝试预占 N 个 sku。返回 :ok 或 [:error reason]。"
  [stock-ref sku qty]
  (dosync
    (let [s (get @stock-ref sku)]
      (cond
        (nil? s)             [:error :sku-not-found]
        (not (can-reserve? s qty)) [:error :insufficient-stock]
        :else
        (do (alter stock-ref update sku do-reserve qty)
            :ok)))))

(defn release!
  "把 reserved 还回 on-hand"
  [stock-ref sku qty]
  (dosync
    (when-let [s (get @stock-ref sku)]
      (when (pos? (:reserved s))
        (alter stock-ref update sku do-release qty)))
    :ok))

(defn ship!
  "发货：从 reserved 永久扣减"
  [stock-ref sku qty]
  (dosync
    (when-let [s (get @stock-ref sku)]
      (when (>= (:reserved s) qty)
        (alter stock-ref update sku do-ship qty)
        :ok))))

(defn restock!
  "入库：增加 on-hand"
  [stock-ref sku qty]
  (dosync
    (alter stock-ref update sku
           (fn [s] (-> (or s {:on-hand 0 :reserved 0})
                       (update :on-hand + qty))))
    :ok))

;; ─── 多 sku 原子处理（订单一次扣多个） ────────────────────────────────

(defn reserve-many!
  "对一组 [sku qty] 原子预占。任一失败则全部回滚（dosync 自动）。
   返回 :ok 或 [:error sku-failed reason]"
  [stock-ref items]
  (dosync
    (loop [pending items
           acc     []]
      (if-let [{:keys [sku qty] :as it} (first pending)]
        (let [s (get @stock-ref sku)]
          (cond
            (nil? s) (throw (ex-info "sku-not-found" {:sku sku}))
            (not (can-reserve? s qty))
            (throw (ex-info "insufficient-stock" {:sku sku :need qty :have (:on-hand s)}))
            :else
            (do (alter stock-ref update sku do-reserve qty)
                (recur (rest pending) (conj acc it)))))
        :ok))))

(defn release-many! [stock-ref items]
  (dosync
    (doseq [{:keys [sku qty]} items]
      (when (get @stock-ref sku)
        (alter stock-ref update sku do-release qty)))
    :ok))

(defn ship-many! [stock-ref items]
  (dosync
    (doseq [{:keys [sku qty]} items]
      (when (get @stock-ref sku)
        (alter stock-ref update sku do-ship qty)))
    :ok))

;; ─── 守恒律检查（测试用） ─────────────────────────────────────────────

(defn total-units
  "当前 on-hand + reserved 总数（未发货库存）"
  [stock-ref]
  (reduce + 0 (mapcat (fn [[_ {:keys [on-hand reserved]}]]
                        [on-hand reserved])
                      @stock-ref)))

(defn snapshot [stock-ref] @stock-ref)
