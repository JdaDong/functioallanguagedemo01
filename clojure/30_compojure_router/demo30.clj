(ns demo30
  "Compojure：Ring 之上的路由 DSL。
   核心：(GET path destruct body) / (POST ...) 等宏，把 handler 写成一棵路由树。

   参考：https://github.com/weavejester/compojure
   运行：clojure -M:run"
  (:require [ring.adapter.jetty :as jetty]
            [compojure.core :refer [defroutes GET POST PUT DELETE context routes]]
            [compojure.route :as route]
            [jsonista.core :as j])
  (:import [java.net URL HttpURLConnection]
           [java.io BufferedReader InputStreamReader]))

;; ───────────────────────────────────────────────────────────────────────
;; 内存"数据库"
;; ───────────────────────────────────────────────────────────────────────
(def users (atom {1 {:id 1 :name "Ada"  :role :admin}
                  2 {:id 2 :name "Bob"  :role :user}}))
(def next-id (atom 3))

;; ───────────────────────────────────────────────────────────────────────
;; JSON 工具
;; ───────────────────────────────────────────────────────────────────────
(def mapper (j/object-mapper {:decode-key-fn keyword}))

(defn json-resp
  ([data] (json-resp 200 data))
  ([status data]
   {:status  status
    :headers {"Content-Type" "application/json; charset=utf-8"}
    :body    (j/write-value-as-string data mapper)}))

(defn read-json-body [request]
  (when-let [body (:body request)]
    (try (j/read-value (slurp body) mapper)
         (catch Exception _ nil))))

;; ───────────────────────────────────────────────────────────────────────
;; 路由 DSL：每个 route 是一个宏，destruct 出路径参数 + 请求字段
;; ───────────────────────────────────────────────────────────────────────
(defroutes user-routes
  ;; GET /users/                    列出所有
  (GET "/" [] (json-resp (vals @users)))

  ;; GET /users/:id                 看单个；:id 自动绑定为字符串
  (GET "/:id" [id]
    (if-let [u (get @users (Integer/parseInt id))]
      (json-resp u)
      (json-resp 404 {:error "not found" :id id})))

  ;; POST /users/                   新建；从 body 解 JSON
  (POST "/" req
    (if-let [body (read-json-body req)]
      (let [id   @next-id
            _    (swap! next-id inc)
            user (assoc body :id id)]
        (swap! users assoc id user)
        (json-resp 201 user))
      (json-resp 400 {:error "invalid json"})))

  ;; DELETE /users/:id              删除
  (DELETE "/:id" [id]
    (let [id (Integer/parseInt id)]
      (if (contains? @users id)
        (do (swap! users dissoc id)
            (json-resp {:deleted id}))
        (json-resp 404 {:error "not found" :id id})))))

(defroutes app-routes
  (GET "/" [] {:status 200 :body "compojure demo"})
  (GET "/ping" [] (json-resp {:pong true}))
  ;; 用 context 给 user-routes 打前缀
  (context "/users" [] user-routes)
  ;; 兜底
  (route/not-found (json-resp 404 {:error "no such route"})))

;; ───────────────────────────────────────────────────────────────────────
;; 自请求工具（沿用 demo 29，加一个 read JSON 的便利）
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
  (let [port   33902
        server (jetty/run-jetty app-routes {:port port :join? false})
        base   (str "http://localhost:" port)]
    (try
      (println (format "=== Server up on %s ===\n" base))

      (println "→ GET /ping")
      (println "  ←" (http-call "GET" (str base "/ping")))

      (println "\n→ GET /users/  （列表）")
      (println "  ←" (http-call "GET" (str base "/users/")))

      (println "\n→ GET /users/1  （路径参数）")
      (println "  ←" (http-call "GET" (str base "/users/1")))

      (println "\n→ GET /users/999  （404 路径）")
      (println "  ←" (http-call "GET" (str base "/users/999")))

      (println "\n→ POST /users/  body={\"name\":\"Cy\",\"role\":\"user\"}")
      (let [r (http-call "POST" (str base "/users/")
                         "{\"name\":\"Cy\",\"role\":\"user\"}")]
        (println "  ←" r))

      (println "\n→ GET /users/  （应包含新建的 Cy）")
      (println "  ←" (http-call "GET" (str base "/users/")))

      (println "\n→ DELETE /users/2")
      (println "  ←" (http-call "DELETE" (str base "/users/2")))

      (println "\n→ GET /nope  （compojure not-found 兜底）")
      (println "  ←" (http-call "GET" (str base "/nope")))

      (println "\n=== 一句话总结 ===")
      (println "- Compojure 是 Ring 之上的'路由 DSL 宏'，仍输出 handler")
      (println "- (GET path [destruct] body)：path 里的 :id 自动绑定")
      (println "- (context prefix [] sub-routes)：路由分组 + 前缀")
      (println "- (route/not-found ...)：兜底 handler，永远放最后")
      (println "- 路由就是宏，宏展开后是普通 handler，可以无缝套中间件")

      (finally
        (.stop server)
        (shutdown-agents)))))
