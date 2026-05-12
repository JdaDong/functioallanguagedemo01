(ns demo32
  "DataScript = 纯内存版的 Datomic（同 API，不要钱、不要服务）。
   核心心智：
   1. 数据库 = 不可变的 fact 集合，每条 fact = [entity attribute value tx]
   2. transact 不修改 db，而是返回**新 db**（持久化数据结构，结构共享）
   3. 时间旅行：任意历史 db-value 都还活着，可查询，可对比

   参考：https://github.com/tonsky/datascript
   运行：clojure -M:run"
  (:require [datascript.core :as d]))

;; ───────────────────────────────────────────────────────────────────────
;; schema：声明哪些 attribute 是 ref / many / unique
;; 不在 schema 里的 attribute 默认 :db.cardinality/one + 标量
;; ───────────────────────────────────────────────────────────────────────
(def schema
  {:user/email   {:db/unique :db.unique/identity}
   :user/friends {:db/cardinality :db.cardinality/many
                  :db/valueType   :db.type/ref}
   :post/author  {:db/valueType :db.type/ref}})

;; ───────────────────────────────────────────────────────────────────────
;; section 1: conn 是一个 atom-of-db；transact! 原子地把新 db swap 进去
;; ───────────────────────────────────────────────────────────────────────
(defn section-1-basic []
  (println "── section 1: 基础 transact + 查询 ──")
  (let [conn (d/create-conn schema)]
    ;; 第一批 fact：两个 user
    (d/transact! conn
      [{:db/id -1 :user/name "Ada" :user/email "ada@x.com" :user/age 30}
       {:db/id -2 :user/name "Bob" :user/email "bob@x.com" :user/age 25}])

    (println "  全部 user：")
    (doseq [[name age] (d/q '[:find ?n ?a
                              :where [?e :user/name ?n] [?e :user/age ?a]]
                            @conn)]
      (println "   " name age))

    ;; 用 :user/email 这种 unique-identity 作为 lookup ref
    (println "  按 email 查 Ada：" (d/pull @conn '[*] [:user/email "ada@x.com"]))
    conn))

;; ───────────────────────────────────────────────────────────────────────
;; section 2: 时间旅行 —— 历史 db-value 可以保留，对比新旧
;; ───────────────────────────────────────────────────────────────────────
(defn section-2-time-travel []
  (println "\n── section 2: 时间旅行（不可变历史）──")
  (let [conn  (d/create-conn schema)
        ;; t0：空库
        db-t0 @conn]

    (d/transact! conn [{:user/name "Ada" :user/email "ada@x.com" :user/age 30}])
    (let [db-t1 @conn]

      (d/transact! conn [{:user/email "ada@x.com" :user/age 31}])  ;; Ada 过生日
      (let [db-t2 @conn]

        (d/transact! conn [{:user/email "ada@x.com" :user/age 32}])
        (let [db-t3 @conn
              ages-at (fn [db]
                        (d/q '[:find ?a .
                               :in $ ?email
                               :where [?e :user/email ?email] [?e :user/age ?a]]
                             db "ada@x.com"))]
          (println "  t0 (空库) Ada.age =" (ages-at db-t0))
          (println "  t1 创建后        =" (ages-at db-t1))
          (println "  t2 第一次过生日  =" (ages-at db-t2))
          (println "  t3 第二次过生日  =" (ages-at db-t3))
          (println "  → 4 个 db-value 同时存在内存里，结构共享，互不影响"))))))

;; ───────────────────────────────────────────────────────────────────────
;; section 3: db-with —— 不修改 conn，纯函数式"假设性 transact"
;;   对标 Excel "what-if"：试算一笔加薪，但不真的写库
;; ───────────────────────────────────────────────────────────────────────
(defn section-3-db-with []
  (println "\n── section 3: db-with（假设性事务，不污染 conn）──")
  (let [conn (d/create-conn schema)]
    (d/transact! conn
      [{:user/name "Ada" :user/email "ada@x.com" :user/salary 100}
       {:user/name "Bob" :user/email "bob@x.com" :user/salary 80}])

    (let [db    @conn
          ;; 试算：所有人涨薪 50%，看新工资分布，但不 commit
          raises (for [[e s] (d/q '[:find ?e ?s
                                    :where [?e :user/salary ?s]] db)]
                   {:db/id e :user/salary (long (* s 1.5))})
          db-hypo (:db-after (d/with db raises))

          query  '[:find ?n ?s
                   :where [?e :user/name ?n] [?e :user/salary ?s]]]

      (println "  原始薪资：" (sort (d/q query db)))
      (println "  假设涨薪：" (sort (d/q query db-hypo)))
      (println "  conn 实际状态（未变）：" (sort (d/q query @conn))))))

;; ───────────────────────────────────────────────────────────────────────
;; section 4: ref 和 cardinality/many —— 关系建模
;; ───────────────────────────────────────────────────────────────────────
(defn section-4-refs []
  (println "\n── section 4: ref 关系 + cardinality/many ──")
  (let [conn (d/create-conn schema)]
    (d/transact! conn
      [{:db/id -1 :user/name "Ada" :user/email "ada@x.com"}
       {:db/id -2 :user/name "Bob" :user/email "bob@x.com"}
       {:db/id -3 :user/name "Cy"  :user/email "cy@x.com"}
       ;; Ada 的朋友 = [Bob Cy]
       [:db/add -1 :user/friends -2]
       [:db/add -1 :user/friends -3]
       ;; 一篇 post，作者是 Ada
       {:post/title "hello" :post/author -1}])

    (println "  Ada 的朋友（pull 跟 ref）：")
    (println "   "
      (d/pull @conn
              '[:user/name {:user/friends [:user/name]}]
              [:user/email "ada@x.com"]))

    (println "  反向查：Ada 写的 post（:post/_author 反指）：")
    (println "   "
      (d/pull @conn
              '[:user/name {:post/_author [:post/title]}]
              [:user/email "ada@x.com"]))))

(defn -main [& _]
  (section-1-basic)
  (section-2-time-travel)
  (section-3-db-with)
  (section-4-refs)
  (println "\n=== 一句话总结 ===")
  (println "- DataScript = 不可变 fact 集合 + Datalog 查询，纯内存版 Datomic")
  (println "- conn = atom-of-db；transact! 原子换 db，但旧 db-value 仍活着")
  (println "- db-with：纯函数式 what-if 事务，不污染 conn")
  (println "- ref + cardinality/many + lookup ref + 反向 pull (_author)")
  (println "- 这一切的基础：数据库就是值（database as a value），不是位置"))
