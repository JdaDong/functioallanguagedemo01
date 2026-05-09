;; ============================================================
;; Clojure Demo 01 — 基础 & 四大集合
;; ============================================================
;; 目标：一次看清 Clojure 的四类核心集合 + 基础语法
;;   list  ()  ← 代码即列表（Lisp 本质）
;;   vector [] ← 索引随机访问
;;   map    {} ← 键值对
;;   set   #{} ← 去重集合
;;
;; 运行：clojure -M clojure/01_basics_and_collections.clj
;; ============================================================

(println "=== 1. 基础语法：def / defn / let ===")

(def pi 3.14159)
(defn square [x] (* x x))
(let [r 5
      area (* pi (square r))]
  (println "半径" r "的圆面积 =" area))

(println "\n=== 2. list：() 是代码，'() 是数据 ===")
(def xs '(1 2 3 4 5))
(println "list xs =" xs)
(println "first  =" (first xs) " rest =" (rest xs) " count =" (count xs))
(println "conj 到 list 是前插 →" (conj xs 0))

(println "\n=== 3. vector：[] 索引随机访问 ===")
(def v [10 20 30 40])
(println "vector v =" v)
(println "get v 2 =" (get v 2) " (等价 (v 2)) =" (v 2))
(println "conj 到 vector 是后追加 →" (conj v 50))

(println "\n=== 4. map：{} 键值对 ===")
(def person {:name "Ada" :age 36 :lang "Haskell"})
(println "map =" person)
(println ":name =" (:name person) " (关键字当函数用)")
(println "assoc 新增 →" (assoc person :city "London"))
(println "dissoc 删除 →" (dissoc person :lang))

(println "\n=== 5. set：#{} 天然去重 ===")
(def s #{:red :green :blue})
(println "set s =" s)
(println "包含 :red ? =" (contains? s :red))
(println "conj 已存在 →" (conj s :red) "（不变）")

(println "\n=== 6. 四者互转 ===")
(println "vector→list :" (apply list v))
(println "list→vector :" (vec xs))
(println "map→seq    :" (seq person))
(println "set→vector  :" (vec s))

(println "\n=== 7. 一句话总结 ===")
(println "list  前插快  — 天然适合函数参数 / 代码")
(println "vector 尾加快 — 日常默认选它")
(println "map   查找快  — 结构化数据首选")
(println "set   去重快  — 成员关系判断")
