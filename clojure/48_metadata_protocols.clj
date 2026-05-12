;; demo 48 — Metadata + Protocols 进阶：reify / extend-protocol / extend-via-metadata
;; 零依赖。
;; 运行：clojure -M 48_metadata_protocols.clj

(ns demo48)

;; ───────────────────────────────────────────────────────────────────────
;; 心智锚：
;;   - metadata 是附在数据上的「带外信息」，不影响 = 比较
;;   - protocol 提供"类型 → 实现"的多态分派
;;   - extend-protocol：给现有类型补实现（突破继承壁垒）
;;   - extend-via-metadata：给 instance 级（不是类型级）的实现，按需开光
;;
;; demo 08 演示了基础 defprotocol/defrecord。本 demo 演示真工业里用得到的招式。
;; ───────────────────────────────────────────────────────────────────────

;; ───────────────────────────────────────────────────────────────────────
;; section 1: metadata 基础 —— 不影响 = 比较，但保留信息
;; ───────────────────────────────────────────────────────────────────────
(defn section-1-metadata-basics []
  (println "── section 1: metadata 基础 ──")
  (let [v1 [1 2 3]
        v2 (with-meta [1 2 3] {:source :user/upload :ts 1700000000})]
    (println "  v1 =" v1 "  meta =" (meta v1))
    (println "  v2 =" v2 "  meta =" (meta v2))
    (println "  v1 = v2 ?" (= v1 v2) "（meta 不影响 =）")
    (assert (= v1 v2))
    (assert (nil? (meta v1)))
    (assert (= :user/upload (-> v2 meta :source)))

    ;; conj 后元数据保留
    (let [v3 (conj v2 4)]
      (println "  conj 后 meta：" (meta v3))
      (assert (= :user/upload (-> v3 meta :source))))))

;; ───────────────────────────────────────────────────────────────────────
;; section 2: metadata 在函数上 —— ^:private / ^:dynamic / 自定义 hint
;; ───────────────────────────────────────────────────────────────────────
(defn ^{:doc      "演示 docstring metadata"
        :ratelimit 100}
  api-call [req]
  (str "OK: " req))

(defn section-2-fn-metadata []
  (println "\n── section 2: 函数 var 上的 metadata ──")
  (let [m (meta #'api-call)]
    (println "  :doc       =" (:doc m))
    (println "  :ratelimit =" (:ratelimit m))
    (println "  :line      =" (:line m))
    (assert (= "演示 docstring metadata" (:doc m)))
    (assert (= 100 (:ratelimit m)))))

;; ───────────────────────────────────────────────────────────────────────
;; section 3: protocol + extend-protocol —— 给已有类型补实现
;; ───────────────────────────────────────────────────────────────────────
(defprotocol Renderable
  (render [x]))

;; 给标准类型挂 render 实现
(extend-protocol Renderable
  String       (render [s] (str "[str:" s "]"))
  Long         (render [n] (str "[num:" n "]"))
  clojure.lang.IPersistentVector
               (render [v] (str "[vec:" (clojure.string/join "," (map render v)) "]"))
  nil          (render [_] "[nil]"))

(defn section-3-extend-protocol []
  (println "\n── section 3: extend-protocol 给标准类型挂方法 ──")
  (println "  render \"hi\"      =" (render "hi"))
  (println "  render 42         =" (render 42))
  (println "  render [1 \"a\"]    =" (render [1 "a"]))
  (println "  render nil        =" (render nil))
  (assert (= "[str:hi]"        (render "hi")))
  (assert (= "[num:42]"        (render 42)))
  (assert (= "[vec:[num:1],[str:a]]" (render [1 "a"])))
  (assert (= "[nil]"           (render nil))))

;; ───────────────────────────────────────────────────────────────────────
;; section 4: reify —— 临时匿名实例实现 protocol
;; ───────────────────────────────────────────────────────────────────────
(defprotocol Counter
  (cinc [c])
  (cval [c]))

(defn section-4-reify []
  (println "\n── section 4: reify 创建匿名实例 ──")
  (let [;; 用 atom 持状态，reify 出一个有 protocol 实现的对象
        state (atom 0)
        ctr   (reify Counter
                (cinc [_] (swap! state inc))
                (cval [_] @state))]
    (cinc ctr) (cinc ctr) (cinc ctr)
    (println "  cinc 三次后 cval =" (cval ctr))
    (assert (= 3 (cval ctr)))
    ;; reify 结合多 protocol：可以同时实现 Counter + java 接口
    ))

;; ───────────────────────────────────────────────────────────────────────
;; section 5: extend-via-metadata —— 实例级 protocol 实现
;;   场景：不污染类型；给某个具体 map 临时加 protocol 行为
;; ───────────────────────────────────────────────────────────────────────
(defprotocol Greeter
  :extend-via-metadata true
  (greet [g]))

(defn section-5-extend-via-metadata []
  (println "\n── section 5: extend-via-metadata —— 实例级实现 ──")
  ;; 给 map 加 metadata，里面带 protocol 实现函数
  (let [polite-ada (with-meta {:name "Ada"}
                              {`greet (fn [{:keys [name]}] (str "Good day, " name))})
        casual-bob (with-meta {:name "Bob"}
                              {`greet (fn [{:keys [name]}] (str "yo " name))})]
    (println "  polite-ada：" (greet polite-ada))
    (println "  casual-bob：" (greet casual-bob))
    (assert (= "Good day, Ada" (greet polite-ada)))
    (assert (= "yo Bob"        (greet casual-bob)))

    ;; 同一个原 map，meta 不同 → 行为不同；类型上没有挂 protocol 实现
    (println "  裸 map 调 greet 会抛（没 meta）：")
    (try
      (greet {:name "Cy"})
      (assert false)
      (catch Exception e
        (println "  ✓ 捕获：" (subs (or (ex-message e) (str e)) 0 (min 60 (count (or (ex-message e) (str e))))))))))

(defn -main [& _]
  (section-1-metadata-basics)
  (section-2-fn-metadata)
  (section-3-extend-protocol)
  (section-4-reify)
  (section-5-extend-via-metadata)
  (println "\n=== 一句话总结 ===")
  (println "- metadata 是附在数据上的带外信息，不影响 = 比较；conj/assoc 保留")
  (println "- 函数 var 的 metadata 携带 :doc / :line / 任意自定义 key")
  (println "- protocol = 接口契约；extend-protocol = 给已有类型补实现，无侵入")
  (println "- reify = 即兴匿名实例；常配 atom 做封装状态")
  (println "- extend-via-metadata = 实例级实现：同类型不同 instance 行为可不同")
  (shutdown-agents))

(-main)
