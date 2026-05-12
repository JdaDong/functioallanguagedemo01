(ns demo27
  "Transit：跨语言、自描述、缓存压缩的高性能数据格式。
   场景：浏览器 ↔ 后端（替代 JSON）；Datomic 客户端协议；高频小消息。

   参考：https://github.com/cognitect/transit-format
   运行：clojure -M:run"
  (:require [cognitect.transit :as transit]
            [clojure.edn :as edn])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(defn ->bytes
  "把 Clojure 数据写成 transit bytes（fmt = :json / :json-verbose / :msgpack）"
  [data fmt]
  (let [out (ByteArrayOutputStream.)
        w   (transit/writer out fmt)]
    (transit/write w data)
    (.toByteArray out)))

(defn <-bytes
  "把 transit bytes 读回 Clojure 数据"
  [bs fmt]
  (let [in (ByteArrayInputStream. bs)
        r  (transit/reader in fmt)]
    (transit/read r)))

(defrecord Money [amount currency])

(defn -main [& _]

  (println "=== 1. Transit 三种格式：json / json-verbose / msgpack ===")
  (let [data {:name "Ada" :age 30}]
    (doseq [fmt [:json :json-verbose :msgpack]]
      (let [bs (->bytes data fmt)]
        (println (format "  %-15s %3d bytes  →  %s"
                         (str fmt)
                         (alength bs)
                         (if (= fmt :msgpack)
                           (apply str "<binary " (count bs) " bytes>")
                           (String. bs)))))))
  ;; json：紧凑、可读；json-verbose：完整 JSON 兼容（无 cache）；msgpack：二进制最小

  (println "\n=== 2. 复杂数据：keyword/set/uuid/instant 全保真 ===")
  (let [rich {:tags    #{:admin :clj}
              :id      #uuid "550e8400-e29b-41d4-a716-446655440000"
              :when    #inst "2026-01-01T00:00:00.000-00:00"
              :ratio   22/7
              :nested  {:items [1 2 3]}}
        bs   (->bytes rich :json)
        back (<-bytes bs :json)]
    (println "原始 ：" rich)
    (println "JSON ：" (String. bs))
    (println "回读 ：" back)
    (println "等价?：" (= rich back)))
  ;; 注意：纯 JSON 没法表示 set/uuid/instant，Transit 用 ~# 前缀编码这些

  (println "\n=== 3. 缓存：重复 key 自动压缩（Transit 杀手锏） ===")
  ;; 100 条同结构 record，每条都有 :name / :age / :email
  ;; JSON 会把这三个 key 写 100 遍；Transit 第一次写完后，后面用 "^N" 引用
  (let [records (vec (for [i (range 100)]
                       {:name (str "user-" i)
                        :age  (+ 20 (mod i 50))
                        :email (str "u" i "@x.com")}))
        json-bytes    (->bytes records :json)
        verbose-bytes (->bytes records :json-verbose)
        msgpack-bytes (->bytes records :msgpack)]
    (println (format "100 条记录："))
    (println (format "  json         : %5d bytes（用了 cache）" (alength json-bytes)))
    (println (format "  json-verbose : %5d bytes（无 cache，纯 JSON）" (alength verbose-bytes)))
    (println (format "  msgpack      : %5d bytes（二进制+cache）" (alength msgpack-bytes)))
    (println (format "  EDN pr-str   : %5d bytes" (count (pr-str records))))
    (let [edn-bytes (.getBytes (pr-str records) "UTF-8")]
      (println (format "  压缩比 :json vs :json-verbose = %.2fx"
                       (double (/ (alength verbose-bytes)
                                  (alength json-bytes)))))))

  (println "\n=== 4. 自定义类型：write/read handler ===")
  ;; 类似 EDN 的 tagged literal，但 Transit 走的是 handler 注册
  (let [write-handlers
        {Money (transit/write-handler
                 "money"
                 (fn [^Money m] [(:amount m) (:currency m)]))}

        read-handlers
        {"money" (transit/read-handler
                   (fn [[amt cur]] (->Money amt cur)))}

        out  (ByteArrayOutputStream.)
        _    (transit/write
               (transit/writer out :json {:handlers write-handlers})
               (->Money 100 "USD"))
        bs   (.toByteArray out)
        back (transit/read
               (transit/reader (ByteArrayInputStream. bs) :json
                               {:handlers read-handlers}))]
    (println "Money 序列化：" (String. bs))
    (println "Money 反序列化：" back)
    (println "类型：" (type back))
    (println "等价?：" (= (->Money 100 "USD") back)))

  (println "\n=== 5. Transit vs EDN vs JSON 选型 ===")
  (println "                | EDN     | JSON   | Transit")
  (println "  跨语言         | ❌仅 Clj | ✅     | ✅ JS/Py/Java/Rb/.Net")
  (println "  类型保真       | ✅      | ❌      | ✅")
  (println "  缓存压缩       | ❌      | ❌      | ✅（重复 key/value）")
  (println "  二进制         | ❌      | ❌      | ✅ msgpack")
  (println "  人类可读       | ✅      | ✅      | ⚠️  json-verbose 才纯 JSON")
  (println "  浏览器友好     | ❌      | ✅      | ✅（有 transit-js）")

  (println "\n→ 用 Transit：浏览器/移动端 ↔ Clojure 后端，高频小消息")
  (println "→ 用 EDN    ：Clojure 内部、配置文件、调试日志")
  (println "→ 用 JSON   ：和外部第三方 API 互通时（无选择余地）")

  (println "\n=== 一句话总结 ===")
  (println "- Transit = 跨语言版的 EDN：保留富类型 + 跨 JS/Java/Py/Ruby")
  (println "- 三种 wire format：:json / :json-verbose / :msgpack，各有取舍")
  (println "- 杀手锏：重复 key/string 自动 cache 引用，100 条同结构 record 省 ~10-15%体积")
  (println "- handler API（write-handler / read-handler）扩展自定义类型")
  (println "- Datomic Cloud / re-frame-http-fx 等都默认用 Transit 做线缆协议")
  (shutdown-agents))
