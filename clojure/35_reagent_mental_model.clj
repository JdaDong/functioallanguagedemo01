;; demo 35 — Reagent 心智模型（在 JVM Clojure 上模拟）
;;
;; Reagent = "ratom + reactive renderer"。
;; - ratom（reactive atom）= 普通 atom + 订阅追踪
;; - 渲染函数 = 读 ratom 的纯函数；ratom 变 → 渲染重跑
;;
;; 真实 Reagent 跑在 ClojureScript + DOM；本 demo 用 JVM atom + 手动 watcher
;; 再现完全相同的"reactive cell + 自动重渲染"心智，零外部依赖。
;;
;; 运行：clojure -M 35_reagent_mental_model.clj

(ns demo35)

;; ───────────────────────────────────────────────────────────────────────
;; ratom：值 + 订阅者集合
;;   set! 时若值变化，把所有订阅者函数重跑一遍
;; ───────────────────────────────────────────────────────────────────────
(defn ratom [init]
  {:val      (atom init)
   :watchers (atom [])})

(defn rget [r] @(:val r))

(defn rset! [r v]
  (let [old @(:val r)]
    (reset! (:val r) v)
    (when (not= old v)
      (doseq [w @(:watchers r)] (w)))))

(defn subscribe! [r f]
  (swap! (:watchers r) conj f))

;; ───────────────────────────────────────────────────────────────────────
;; reaction：把"渲染函数 f"绑到 source-ratom 上
;;   返回一个新的 ratom（保存最新输出），可作下一层 reaction 的 source
;;   每次 source 变化 → f 重跑 → 输出 ratom 更新 → 它的订阅者也连锁重跑
;; ───────────────────────────────────────────────────────────────────────
(def render-counter (atom 0))

(defn reaction [name source f]
  (let [out (ratom nil)
        run! (fn []
               (swap! render-counter inc)
               (let [v (f (rget source))]
                 (println (format "  [render #%d] %s → %s"
                                  @render-counter name (pr-str v)))
                 (rset! out v)))]
    (run!)                              ;; 首次渲染
    (subscribe! source run!)            ;; 之后自动跟随
    out))

;; ───────────────────────────────────────────────────────────────────────
;; section 1: counter + 两个 reaction
;; ───────────────────────────────────────────────────────────────────────
(defn section-1-counter []
  (println "── section 1: counter + 两个独立 reaction ──")
  (reset! render-counter 0)
  (let [state    (ratom {:count 0})
        display  (reaction "display"  state #(str "Count is " (:count %)))
        is-even? (reaction "is-even?" state #(even? (:count %)))]

    (println "\n  → 用户点击 +1")
    (rset! state {:count 1})
    (println "\n  → 用户点击 +1")
    (rset! state {:count 2})
    (println "\n  → 用户点击 +1")
    (rset! state {:count 3})

    (println (format "\n  最终 display=%s  is-even?=%s  共渲染 %d 次"
                     (rget display) (rget is-even?) @render-counter))
    (assert (= "Count is 3" (rget display)))
    (assert (= false (rget is-even?)))
    ;; 2 次首渲染 + 3 次 dispatch × 2 reaction = 8
    (assert (= 8 @render-counter))))

;; ───────────────────────────────────────────────────────────────────────
;; section 2: 链式 reaction（reagent 的 subscribe 链）
;;   items → item-count → is-empty?
;; ───────────────────────────────────────────────────────────────────────
(defn section-2-chain []
  (println "\n── section 2: 链式 reaction（一层订一层）──")
  (reset! render-counter 0)
  (let [items      (ratom [])
        item-count (reaction "items→count" items count)
        is-empty?  (reaction "count→empty?" item-count zero?)]

    (println "\n  → 添加 :apple")
    (rset! items [:apple])
    (println "\n  → 添加 :banana :cherry")
    (rset! items [:apple :banana :cherry])
    (println "\n  → 清空")
    (rset! items [])

    (println (format "\n  最终 item-count=%s  is-empty?=%s  共渲染 %d 次"
                     (rget item-count) (rget is-empty?) @render-counter))
    (assert (= 0 (rget item-count)))
    (assert (= true (rget is-empty?)))))

;; ───────────────────────────────────────────────────────────────────────
;; section 3: 同值不重跑（reagent 的小优化）
;; ───────────────────────────────────────────────────────────────────────
(defn section-3-no-rerun []
  (println "\n── section 3: 写入相同值不触发重渲染 ──")
  (reset! render-counter 0)
  (let [s (ratom 1)
        _ (reaction "id" s identity)]   ;; 1 次首渲染

    (rset! s 1)                         ;; 同值，不重跑
    (rset! s 2)                         ;; 重跑
    (rset! s 2)                         ;; 同值，不重跑
    (rset! s 3)                         ;; 重跑
    (println (format "  共渲染 %d 次（期望 3：1 首渲染 + 2 真变化）" @render-counter))
    (assert (= 3 @render-counter))))

(defn -main [& _]
  (section-1-counter)
  (section-2-chain)
  (section-3-no-rerun)
  (println "\n=== 一句话总结 ===")
  (println "- ratom = 值 + 订阅者集合；rset! 触发所有订阅者重跑")
  (println "- reaction = 一个读 ratom 的纯函数，输出包成新 ratom（可作下层 source）")
  (println "- UI = f(state)：Reagent / React / Vue / SwiftUI 的共同心智")
  (println "- 在 ClojureScript 里 reaction 输出 hiccup → reagent 转 vDOM → React diff → DOM")
  (println "- 在 JVM 这里 reaction 输出普通值 → reactive 行为完全一致")
  (println "- 真 Reagent 用 dynamic var 隐式追踪 deref，本 demo 显式 subscribe!，揭开底层")
  (shutdown-agents))

;; 脚本入口：clojure -M file.clj 时直接调用
(-main)
