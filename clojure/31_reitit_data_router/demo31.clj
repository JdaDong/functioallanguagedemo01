(ns demo31
  "Reitit：数据驱动路由（路由表 = 普通 vector/map，可反射、可转 OpenAPI）。
   核心区别于 compojure：宏 → 数据。

   参考：https://github.com/metosin/reitit
   运行：clojure -M:run"
  (:require [ring.adapter.jetty :as jetty]
            [reitit.core :as r]
            [reitit.ring :as ring]
            [reitit.coercion.malli]
            [reitit.ring.coercion :as rrc]
            [reitit.ring.middleware.parameters :as parameters]
            [reitit.ring.middleware.muuntaja :as muuntaja-mw]
            [muuntaja.core :as muuntaja]
            [jsonista.core :as j])
  (:import [java.net URL HttpURLConnection]
           [java.io BufferedReader InputStreamReader]))

;; ───────────────────────────────────────────────────────────────────────
;; 业务"数据库"
;; ───────────────────────────────────────────────────────────────────────
(def items (atom {1 {:id 1 :name "apple"  :price 5}
                  2 {:id 2 :name "banana" :price 3}}))
(def next-id (atom 3))

;; ───────────────────────────────────────────────────────────────────────
;; 路由表 = 数据：每条 = [path {method {:handler ... :parameters ... :responses ...}}]
;; reitit 把这 vector 喂进 router，自动按 method+path 分派
;; ───────────────────────────────────────────────────────────────────────
(def routes
  [["/ping"
    {:get {:handler (fn [_] {:status 200 :body {:pong true}})}}]

   ["/items"
    {:get  {:summary "list items"
            :handler (fn [_] {:status 200 :body (vec (vals @items))})}

     :post {:summary    "create item"
            ;; malli schema 描述入参，reitit 自动 coerce
            :parameters {:body [:map
                                [:name :string]
                                [:price [:and :int [:>= 0]]]]}
            :handler    (fn [{{body :body} :parameters}]
                          (let [id   @next-id
                                _    (swap! next-id inc)
                                item (assoc body :id id)]
                            (swap! items assoc id item)
                            {:status 201 :body item}))}}]

   ["/items/:id"
    {:parameters {:path [:map [:id :int]]}    ;; 路径参数自动 coerce 为 int

     :get {:summary "get item by id"
           :handler (fn [{{{:keys [id]} :path} :parameters}]
                      (if-let [it (get @items id)]
                        {:status 200 :body it}
                        {:status 404 :body {:error "not found" :id id}}))}

     :delete {:summary "delete item"
              :handler (fn [{{{:keys [id]} :path} :parameters}]
                         (if (contains? @items id)
                           (do (swap! items dissoc id)
                               {:status 200 :body {:deleted id}})
                           {:status 404 :body {:error "not found" :id id}}))}}]])

;; ───────────────────────────────────────────────────────────────────────
;; 路由器 = (ring/router routes options)
;; options 里的 :data 是"全表共享"：所有路由都拿到这套中间件 + coercion
;; ───────────────────────────────────────────────────────────────────────
(def app
  (ring/ring-handler
    (ring/router
      routes
      {:data {:coercion   reitit.coercion.malli/coercion
              :muuntaja   muuntaja/instance              ;; 自动 JSON in/out
              :middleware [parameters/parameters-middleware
                           muuntaja-mw/format-middleware
                           rrc/coerce-exceptions-middleware
                           rrc/coerce-request-middleware
                           rrc/coerce-response-middleware]}})
    (ring/create-default-handler)))

;; ───────────────────────────────────────────────────────────────────────
;; 自请求工具（同 demo 30）
;; ───────────────────────────────────────────────────────────────────────
(defn http-call [method url & [body]]
  (let [conn ^HttpURLConnection (.openConnection (URL. url))]
    (.setRequestMethod conn method)
    (.setDoInput conn true)
    (when body
      (.setDoOutput conn true)
      (.setRequestProperty conn "Content-Type" "application/json")
      (with-open [os (.getOutputStream conn)]
        (.write os (.getBytes body "UTF-8"))))
    (let [status (.getResponseCode conn)
          stream (if (>= status 400) (.getErrorStream conn) (.getInputStream conn))
          body   (with-open [r (BufferedReader. (InputStreamReader. stream "UTF-8"))]
                   (apply str (line-seq r)))]
      {:status status :body body})))

(defn -main [& _]
  (let [port   33903
        server (jetty/run-jetty app {:port port :join? false})
        base   (str "http://localhost:" port)]
    (try
      (println (format "=== Server up on %s ===\n" base))

      (println "→ GET /ping")
      (println "  ←" (http-call "GET" (str base "/ping")))

      (println "\n→ GET /items")
      (println "  ←" (http-call "GET" (str base "/items")))

      (println "\n→ GET /items/1  （路径参数自动 coerce 为 int）")
      (println "  ←" (http-call "GET" (str base "/items/1")))

      (println "\n→ GET /items/abc  （非法 int → 400 coercion error）")
      (println "  ←" (http-call "GET" (str base "/items/abc")))

      (println "\n→ POST /items  body={\"name\":\"cherry\",\"price\":8}")
      (println "  ←" (http-call "POST" (str base "/items")
                                "{\"name\":\"cherry\",\"price\":8}"))

      (println "\n→ POST /items  body={\"name\":\"bad\",\"price\":-1}  （schema 验证失败）")
      (println "  ←" (http-call "POST" (str base "/items")
                                "{\"name\":\"bad\",\"price\":-1}"))

      (println "\n→ POST /items  body={\"name\":\"missing-price\"}  （缺字段）")
      (println "  ←" (http-call "POST" (str base "/items")
                                "{\"name\":\"missing-price\"}"))

      (println "\n→ DELETE /items/2")
      (println "  ←" (http-call "DELETE" (str base "/items/2")))

      (println "\n→ 反射：路由表是数据，可以打印出来")
      (let [router (ring/router routes)]
        (println "  全部路由 path：")
        (doseq [[path data] (r/routes router)]
          (println "   " path)))
      ;; 注：上面只是粗略反射，reitit 的真正强项是直接喂给 reitit-swagger 转 OpenAPI

      (println "\n=== 一句话总结 ===")
      (println "- Reitit 路由表 = 普通 vector/map（不是宏），可序列化、可反射、可转 OpenAPI")
      (println "- :parameters {:body schema, :path schema, :query schema} 用 malli 描述")
      (println "- coerce-request-middleware 自动把字符串路径参数 / JSON body 转成强类型")
      (println "- coerce-exceptions-middleware 把 schema 失败转 400，无需 try/catch")
      (println "- 全表共享中间件写在 :data，单路由覆盖在该路由的 map 上")

      (finally
        (.stop server)
        (shutdown-agents)))))
