;; ============================================================
;; Clojure Demo 02 — 不可变数据结构 & 结构共享
;; ============================================================
;; 目标：展示 Clojure 持久化数据结构的 3 个关键事实
;;   (1) 所有"修改"都返回新集合，原集合不变
;;   (2) 修改并非全量拷贝，而是"结构共享"（HAMT / Trie）
;;   (3) assoc/conj/update 的复杂度是 O(log32 N)——实际 ≈ O(1)
;;
;; 运行：clojure -M clojure/02_immutable_data_structures.clj
;; ============================================================

(println "=== 1. 不可变的第一个冲击：修改原集合根本做不到 ===")
(def v [1 2 3])
(def v2 (conj v 4))
(println "原始  v  =" v)
(println "conj 后 v2=" v2)
(println "原始 v 依然 =" v "（没被改）")

(println "\n=== 2. assoc 对 vector：按索引改 ===")
(def v3 (assoc v 1 999))
(println "assoc v 1 999 →" v3 "  原 v =" v)

(println "\n=== 3. assoc-in / update-in 对嵌套 map ===")
(def user {:name "Ada"
           :addr {:city "London"
                  :zip  "EC1"}
           :scores [80 90 100]})

(println "原 user =" user)
(println "assoc-in 改 zip     →" (assoc-in user [:addr :zip] "EC2"))
(println "update-in 改 scores →" (update-in user [:scores 1] + 5))
(println "原 user 依然 =" user)

(println "\n=== 4. 结构共享的直接证据：identity 判断 ===")
;; 改一个字段时，未被改的嵌套结构会被直接复用（同一对象）
(def user2 (assoc user :name "Bob"))
(println ":addr 在 user / user2 里是同一对象？ ="
         (identical? (:addr user) (:addr user2)))
(println "  ↑ true 说明没拷贝 :addr，只是共享它")

(println "\n=== 5. 性能直观：10 万元素 vector 连续 assoc 1 万次 ===")
(let [big (vec (range 100000))
      t0  (System/nanoTime)
      _   (loop [i 0 v big]
            (if (< i 10000)
              (recur (inc i) (assoc v i (- i)))
              v))
      ms  (/ (- (System/nanoTime) t0) 1e6)]
  (println (format "1 万次 assoc 耗时 ≈ %.2f ms（每次 O(log32 N)）" ms)))

(println "\n=== 6. transient：显式开绿灯，拿可变的性能，交出不可变的结果 ===")
;; 持久化结构的"逃生舱"：局部可变，对外仍返回持久化结构
(let [t0 (System/nanoTime)
      result (persistent!
              (reduce (fn [acc i] (conj! acc i))
                      (transient [])
                      (range 1000000)))
      ms (/ (- (System/nanoTime) t0) 1e6)]
  (println (format "transient 建 100 万元素 vector ≈ %.2f ms" ms))
  (println "结果 count =" (count result) "（仍是不可变 vector）"))

(println "\n=== 一句话总结 ===")
(println "不可变 ≠ 慢。Clojure 的集合是 persistent + structural sharing，")
(println "日常用就当 O(1)；需要榨性能时套一层 transient 即可。")
