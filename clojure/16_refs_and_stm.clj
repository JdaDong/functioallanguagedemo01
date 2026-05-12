;; ============================================================
;; Clojure Demo 16 — Refs & STM：协调多个值的事务性更新
;; ============================================================
;; 核心问题：
;;   atom 是单值原子的，但很多业务需要"两个账户同时增减"。
;;   这种"几件事必须一起成功，否则一起失败"的语义，atom 给不了。
;;
;; Clojure 的解法：Software Transactional Memory（STM）
;;   - ref 是支持事务的引用类型
;;   - dosync 把对多个 ref 的操作打包成一个事务
;;   - 事务自动隔离 + 回滚 + 重试（类似数据库的 ACI，没有 D）
;;   - 这是 Clojure 对学界 STM 研究的工业级落地，全球独此一家
;;
;; 运行：clojure -M clojure/16_refs_and_stm.clj
;; ============================================================

(println "=== 1. 最经典：转账 ===")
(def alice (ref 100))
(def bob   (ref 50))

(defn transfer [from to amount]
  (dosync                                    ;; 必须在事务里改 ref
    (alter from - amount)
    (alter to   + amount)))

(transfer alice bob 30)
(println "Alice =" @alice "  Bob =" @bob "  总和 =" (+ @alice @bob))

(println "\n=== 2. 没有 dosync 直接改 ref → 报错 ===")
(print "尝试在事务外 alter：")
(try
  (alter alice + 1)
  (println "（不应到这里）")
  (catch IllegalStateException e
    (println "✅" (.getMessage e))))

(println "\n=== 3. 并发转账：100 笔随机交易，总和必须不变 ===")
;; 注：本 demo 不做余额检查（透支允许），只验证守恒。
;; 真实业务里会在事务里先 ensure 余额 >= amount，否则回滚。
(def accounts
  (vec (repeatedly 5 #(ref 1000))))          ;; 5 个账户，每个 1000
(def initial-total (reduce + (map deref accounts)))
(println "初始总额 =" initial-total)

(let [futures
      (doall
        (for [_ (range 1000)]
          (future
            (let [from-idx (rand-int 5)
                  to-idx   (rand-int 5)
                  amt      (rand-int 100)]
              (when (not= from-idx to-idx)
                (transfer (accounts from-idx) (accounts to-idx) amt))))))]
  (run! deref futures))

(let [final-total (reduce + (map deref accounts))]
  (println "1000 笔并发后总额 =" final-total)
  (println "守恒？" (= initial-total final-total) "  ← STM 保证"))

(doseq [[i a] (map-indexed vector accounts)]
  (println (format "  账户 %d: %d" i @a)))

(println "\n=== 4. alter vs commute：何时可以「放松」事务 ===")
;; alter   ：严格 —— 事务读到的值，提交时必须仍是那个值，否则重试
;; commute ：宽松 —— 这个操作"可交换"（结果与顺序无关），允许在提交时再算一次
;; 用 commute 当且仅当函数是可交换的（如 +、conj、assoc 不冲突的 key）

(def counter (ref 0))
(let [t0 (System/nanoTime)]
  (dotimes [_ 5000]
    (dosync (alter counter inc)))
  (println (format "alter   5000 次 = %.1f ms"
                   (/ (- (System/nanoTime) t0) 1e6))))

(dosync (ref-set counter 0))

(let [t0 (System/nanoTime)]
  (dotimes [_ 5000]
    (dosync (commute counter inc)))
  (println (format "commute 5000 次 = %.1f ms"
                   (/ (- (System/nanoTime) t0) 1e6))))

(println "（注：单线程顺序场景两者持平；commute 的优势在高并发冲突时才显现）")
(println "（commute 仅适用于可交换操作如 +、conj、assoc 不冲突的 key）")

(println "\n=== 5. 事务自动重试演示 ===")
(def retries (atom 0))
(def x (ref 0))
(def y (ref 0))

(let [f1 (future
           (dosync
             (swap! retries inc)             ;; 计数：本事务被尝试了几次
             (alter x inc)
             (Thread/sleep 50)               ;; 拉长事务，让另一边来插一脚
             (alter y inc)))
      _  (Thread/sleep 10)
      f2 (future
           (dosync
             (alter y + 100)))]              ;; 这个会让 f1 看到的 y 失效，f1 必须重试
  (deref f1) (deref f2))

(println "f1 事务被尝试了" @retries "次（>=2 表示真的发生了重试）")
(println "x =" @x "  y =" @y)

(println "\n=== 6. ensure：读保护，比 alter 轻 ===")
;; 如果只是\"读 ref 但要保证读期间它不被改\"，不要 alter（写锁）也不要裸 deref（不安全）
;; 用 ensure：声明\"我依赖这个 ref 的值在事务内不变\"
(def config (ref {:rate 1.5}))
(def total  (ref 0))

(dosync
  (let [r (:rate (ensure config))]           ;; ensure 而不是 alter，因为我们不改它
    (alter total + (* r 100))))

(println "ensure 后 total =" @total)

(println "\n=== 一句话总结 ===")
(println "- ref + dosync 是 Clojure STM：跨多个值的 ACID（无 D）事务")
(println "- alter 严格、commute 宽松（仅可交换操作）、ensure 只读保护")
(println "- 事务内的代码可能被重试，**绝不能有副作用**（同 atom）")

;; future 用的是 agent 线程池（非 daemon），不 shutdown 会让 JVM 多挂 60s
(shutdown-agents)
