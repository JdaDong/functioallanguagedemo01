;; ============================================================
;; Clojure Demo 15 — Atoms：单值的同步状态
;; ============================================================
;; Clojure 的并发模型有 4 个引用类型：
;;   - atom  ：单个值，同步、独立更新（本 demo）
;;   - ref   ：多个值，同步、协调更新（dosync 事务，demo 16）
;;   - agent ：单个值，异步、串行更新（demo 17）
;;   - var   ：动态作用域（不在本系列展开）
;;
;; atom 的核心：
;;   - swap! 用一个纯函数把旧值变成新值
;;   - 底层是 CAS（compare-and-swap），如果别的线程刚改过就重试
;;   - 所以你的更新函数 *必须是纯函数* —— 它会被重试，不能有副作用
;;
;; 运行：clojure -M clojure/15_atoms_and_state.clj
;; ============================================================

(println "=== 1. 最简：atom 当计数器 ===")
(def counter (atom 0))
(println "初始       =" @counter)            ;; @ = deref
(swap! counter inc)
(swap! counter inc)
(swap! counter + 10)                         ;; swap! 可以带额外参数
(println "swap 三次后 =" @counter)

(println "\n=== 2. reset! vs swap! ===")
(reset! counter 100)                         ;; 直接覆盖（不要在并发场景用）
(println "reset! 100  =" @counter)
(swap! counter * 2)                          ;; swap! 用纯函数算新值（线程安全）
(println "swap! *2    =" @counter)

(println "\n=== 3. atom 装复杂数据：模拟玩家状态 ===")
(def player (atom {:name "Alice" :hp 100 :pos [0 0]}))

(swap! player update :hp - 30)
(swap! player update :pos (fn [[x y]] [(inc x) y]))
(println "受伤+移动后 =" @player)

(println "\n=== 4. CAS 重试：并发安全演示 ===")
;; 100 个线程，每个线程让计数器 +1，结果必须正好是 100
(def safe-counter (atom 0))
(let [futures (doall (for [_ (range 100)]
                       (future (swap! safe-counter inc))))]
  (run! deref futures))                      ;; 等所有线程完成
(println "100 线程并发 +1，最终 =" @safe-counter
         "（必为 100；如果是 atom 用错就会少）")

(println "\n=== 5. swap! 函数会被重试：副作用是个坑 ===")
(def call-count (atom 0))
(def value (atom 0))

;; 这个 swap! 函数有副作用（每次调用 inc call-count），错误示范
(let [futures (doall (for [_ (range 50)]
                       (future
                         (swap! value
                                (fn [v]
                                  (swap! call-count inc)   ;; ❌ 副作用
                                  (Thread/sleep 1)         ;; 故意制造冲突
                                  (inc v))))))]
  (run! deref futures))

(println "value 最终  =" @value "（正确 = 50）")
(println "函数被调用了" @call-count "次（>50，多出来的是 CAS 重试）")
(println "→ 教训：swap! 的函数必须是纯函数")

(println "\n=== 6. add-watch：监听变化 ===")
(def watched (atom {}))
(add-watch watched :logger
  (fn [_key _ref old new]
    (println (format "  [watch] %s → %s" old new))))

(swap! watched assoc :a 1)
(swap! watched assoc :b 2)
(swap! watched dissoc :a)
(remove-watch watched :logger)
(swap! watched assoc :c 3)                   ;; watch 已移除，无输出
(println "最终 =" @watched)

(println "\n=== 7. compare-and-set! 手动版 CAS ===")
(def x (atom 10))
(println "尝试把 10 → 20：" (compare-and-set! x 10 20) "  现值=" @x)
(println "尝试把 10 → 30：" (compare-and-set! x 10 30) "  现值=" @x)
(println "（第二次失败因为现值已经是 20，不再是 10）")

(println "\n=== 一句话总结 ===")
(println "- atom 是最常用的状态容器：单值、同步、CAS 安全")
(println "- swap! 的函数必须是纯函数，因为可能被重试")
(println "- 多个值要协调修改时用 ref（demo 16）")

;; future 用的是 agent 线程池（非 daemon），不 shutdown 会让 JVM 多挂 60s
(shutdown-agents)
