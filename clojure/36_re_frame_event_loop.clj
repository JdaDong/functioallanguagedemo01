;; demo 36 — re-frame 事件循环（最小可运行版）
;;
;; re-frame 架构：
;;   dispatch(event) → interceptor 链 → event-handler(db, event) → effects map
;;                                                  → fx-handler 执行副作用
;;
;; 心智锚：和 Redux 同源（unidirectional data flow），但
;;   - effect 不局限于改 state；可以是 :db/:dispatch/:http/:log/...
;;   - interceptor 是 before/after 双向链，比 Redux middleware 表达力强
;;
;; 运行：clojure -M 36_re_frame_event_loop.clj

(ns demo36)

;; ───────────────────────────────────────────────────────────────────────
;; 注册表：全局只读 map，运行时被各种 reg-* 写
;; ───────────────────────────────────────────────────────────────────────
(def app-db        (atom {}))                ;; 全局状态
(def event-handlers (atom {}))               ;; event-id → (db event) → effects
(def fx-handlers    (atom {}))               ;; effect-id → (value) → side effect
(def interceptors   (atom []))               ;; 全局 interceptor 链

;; ───────────────────────────────────────────────────────────────────────
;; 注册 API
;; ───────────────────────────────────────────────────────────────────────
(defn reg-event-fx [id handler] (swap! event-handlers assoc id handler))
(defn reg-fx       [id handler] (swap! fx-handlers   assoc id handler))
(defn reg-interceptor [ic]      (swap! interceptors  conj ic))

;; ───────────────────────────────────────────────────────────────────────
;; 事件循环
;; ───────────────────────────────────────────────────────────────────────
(defn run-fx [effects]
  (doseq [[fx-id v] effects]
    (if-let [h (get @fx-handlers fx-id)]
      (h v)
      (println "  [WARN] no fx-handler for" fx-id))))

(defn dispatch [event]
  (let [event-id (first event)
        handler  (get @event-handlers event-id)
        ;; 1. before 阶段
        ctx0     {:db @app-db :event event}
        ctx1     (reduce (fn [c ic]
                           (if-let [b (:before ic)] (b c) c))
                         ctx0 @interceptors)
        ;; 2. event-handler：返回 effects map
        effects  (handler (:db ctx1) (:event ctx1))
        ;; 3. after 阶段（拿到 effects 再 transform 一次）
        ctx2     (reduce (fn [c ic]
                           (if-let [a (:after ic)] (a c) c))
                         (assoc ctx1 :effects effects)
                         (reverse @interceptors))
        ;; 4. 执行副作用
        final-fx (:effects ctx2)]
    (run-fx final-fx)))

;; ───────────────────────────────────────────────────────────────────────
;; fx-handlers
;; ───────────────────────────────────────────────────────────────────────
(reg-fx :db       (fn [new-db] (reset! app-db new-db)))
(reg-fx :log      (fn [msg]    (println "  [LOG]" msg)))
(reg-fx :dispatch (fn [ev]     (dispatch ev)))
(reg-fx :http     (fn [{:keys [url on-success]}]
                    ;; demo 用：模拟 http，立刻同步派发 success
                    (println "  [HTTP] GET" url "(模拟同步返回)")
                    (dispatch (conj on-success {:status 200 :body "FAKE-OK"}))))

;; ───────────────────────────────────────────────────────────────────────
;; interceptor: trace（before 打事件，after 打 effects）
;; ───────────────────────────────────────────────────────────────────────
(reg-interceptor
 {:id     :trace
  :before (fn [ctx]
            (println (format "\n>>> dispatch %s  (db before = %s)"
                             (:event ctx) (:db ctx)))
            ctx)
  :after  (fn [ctx]
            (println (format "<<< effects = %s" (:effects ctx)))
            ctx)})

;; ───────────────────────────────────────────────────────────────────────
;; interceptor: enrich-with-timestamp
;;   在 event 后追加一个时间戳，方便 handler 用
;; ───────────────────────────────────────────────────────────────────────
(reg-interceptor
 {:id     :enrich-ts
  :before (fn [ctx]
            (update ctx :event conj {:ts 1715251200000}))})    ;; 固定时间方便断言

;; ───────────────────────────────────────────────────────────────────────
;; 业务 event-handlers
;; ───────────────────────────────────────────────────────────────────────
(reg-event-fx :init
              (fn [_db _event]
                {:db {:counter 0 :user nil :log []}}))

(reg-event-fx :inc
              (fn [db [_ _meta]]
                {:db  (update db :counter inc)
                 :log "incremented"}))

(reg-event-fx :load-user
              (fn [_db [_ user-id _meta]]
                {:http {:url        (str "/api/users/" user-id)
                        :on-success [:user-loaded user-id]}}))

(reg-event-fx :user-loaded
              (fn [db [_ user-id resp _meta]]
                {:db  (assoc db :user {:id user-id :data (:body resp)})
                 :log (str "user " user-id " loaded")}))

(reg-event-fx :inc-twice
              (fn [_db _event]
                ;; 派发两个子事件
                {:dispatch [:inc]
                 :log      "(actually only dispatches one :inc here; will chain)"}))

;; ───────────────────────────────────────────────────────────────────────
;; 跑
;; ───────────────────────────────────────────────────────────────────────
(defn -main [& _]
  (println "── re-frame 事件循环 demo ──")

  (dispatch [:init])
  (assert (= 0 (:counter @app-db)))

  (dispatch [:inc])
  (dispatch [:inc])
  (dispatch [:inc])
  (assert (= 3 (:counter @app-db)))

  (println "\n--- 异步 http 流程：dispatch :load-user → :http → :user-loaded → :db ---")
  (dispatch [:load-user 42])
  (assert (= 42 (-> @app-db :user :id)))
  (assert (= "FAKE-OK" (-> @app-db :user :data)))

  (println "\n--- 链式 dispatch（fx 触发 fx）---")
  (dispatch [:inc-twice])
  (assert (= 4 (:counter @app-db)))     ;; inc-twice 实际只 +1

  (println "\n=== 最终 app-db ===")
  (println " " @app-db)
  (println "\n=== 一句话总结 ===")
  (println "- dispatch event → interceptor before → event-handler → effects → interceptor after → fx-handlers")
  (println "- event-handler 是纯函数 (db, event) → effects-map，不直接改世界")
  (println "- effects-map 是数据：{:db ... :http ... :dispatch ... :log ...}")
  (println "- fx-handler 是 (value) → 副作用；可注册任意业务 fx（:firebase, :metrics, :nav）")
  (println "- interceptor before/after 双向链，做 logging / timing / coercion / undo 都很自然")
  (println "- 业务逻辑 100% 是纯函数 + 数据，副作用全部隔离在 fx-handlers，可测试性极强")
  (shutdown-agents))

(-main)
