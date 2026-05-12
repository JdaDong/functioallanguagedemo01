;; ============================================================
;; Clojure Demo 17 — Agents：异步、串行的状态更新
;; ============================================================
;; 选型对照（再确认一遍）：
;;   atom   ：同步 + 单值 + 协调多线程（CAS 重试）
;;   ref    ：同步 + 多值 + STM 事务
;;   agent  ：*异步* + 单值 + 串行（每个 agent 自己的更新永远顺序进行）
;;
;; agent 的杀手用例：
;;   - "把状态推过去"，调用方不阻塞
;;   - 该状态的所有更新必须按顺序，但和外部不同步
;;   - 比如：日志 buffer、写盘队列、监控指标聚合、DB 连接的 in-flight 请求计数
;;
;; 运行：clojure -M clojure/17_agents_async.clj
;; ============================================================

(println "=== 1. send vs send-off：两个线程池 ===")
;; send     : 用固定大小线程池（CPU 密集型动作）
;; send-off : 用可扩张线程池（IO/阻塞动作）

(def counter (agent 0))
(send counter inc)
(send counter + 100)
;; 注意：send 是异步的！下一行立即返回，可能还没执行
(println "刚 send 完立即看 = " @counter "（可能 = 0，也可能 = 101）")
(await counter)                              ;; 等当前线程已发出的所有动作完成
(println "await 后        = " @counter)

(println "\n=== 2. 经典演示：日志聚合（异步 + 顺序保证） ===")
(def log-buffer (agent []))

;; 1000 个线程并发写日志，agent 自己保证它们顺序进入 buffer
(let [futures
      (doall
        (for [i (range 1000)]
          (future
            (send log-buffer conj (str "msg-" i)))))]
  (run! deref futures)
  (await log-buffer))

(println "总共写入" (count @log-buffer) "条日志")
(println "前 5 条 =" (take 5 @log-buffer))
(println "后 5 条 =" (take-last 5 @log-buffer))
(println "（注意：1000 个线程并发，但 buffer 内部顺序由 agent 串行化）")

(println "\n=== 3. 错误处理：默认会让 agent 进入失败态 ===")
(def buggy (agent 0))
(send buggy (fn [_] (throw (ex-info "boom!" {}))))
(Thread/sleep 100)

(print "agent 状态：")
(if (agent-error buggy)
  (do
    (println "❌ 失败：" (-> (agent-error buggy) ex-message))
    (println "尝试再 send → 会立即抛错")
    (try
      (send buggy inc)
      (catch RuntimeException e
        (println "  ✅" (.getMessage e))))
    (restart-agent buggy 0)                  ;; 修复
    (println "restart-agent 后："
             (do (send buggy inc) (await buggy) @buggy)))
  (println "（未失败）"))

(println "\n=== 4. 错误模式 :continue：不停摆 ===")
(def lenient (agent 0 :error-mode :continue
                      :error-handler (fn [_a e]
                                       (println "  [handler] 捕获：" (ex-message e)))))
(send lenient (fn [v] (/ v 0)))              ;; 触发错误
(Thread/sleep 50)
(send lenient inc)                           ;; 仍可继续
(send lenient inc)
(await lenient)
(println "lenient 最终 =" @lenient "（错误被吞掉，agent 继续工作）")

(println "\n=== 5. agent 数据流：把任务串成管道 ===")
;; 一个简化场景：原始数据 → 解析 → 累加汇总
(def parsed-sum (agent 0))

(defn parse-and-add [acc raw]
  ;; 假设 raw 是字符串，里面藏着一个数字
  (let [n (Integer/parseInt raw)]
    (+ acc n)))

(doseq [raw ["1" "2" "3" "10" "20" "30"]]
  (send parsed-sum parse-and-add raw))

(await parsed-sum)
(println "解析+累加结果 =" @parsed-sum "（应为 66）")

(println "\n=== 6. agent 与 STM：可以在事务里 send ===")
;; 事务里 send 的动作，会等到事务成功提交后才真正发出
;; 这个保证非常贴心：避免事务被重试时副作用 send 多次
(def x (ref 0))
(def writes (agent 0))

(dotimes [_ 10]
  (dosync
    (alter x inc)
    (send writes inc)))                      ;; 即使事务重试，这行也只生效一次

(await writes)
(println "x =" @x "  事务确认计数 =" @writes "（必须 = 10，不会因重试翻倍）")

(println "\n=== 一句话总结 ===")
(println "- agent = 异步 + 单值 + 自动串行化；适合\"推过去就忘\"的状态更新")
(println "- send 用计算池，send-off 用 IO 池；await 等\"我已发出的\"动作完成")
(println "- 出错会进入失败态，需 restart-agent；或用 :error-mode :continue")

;; ⚠️ 这一行是 agent demo 的"必须项"：
;; agent 内部是非 daemon 线程池，main 跑完不会让 JVM 退出，需要 60s 超时才回收。
;; shutdown-agents 主动关线程池，让 clojure -M 立即返回。
(shutdown-agents)
