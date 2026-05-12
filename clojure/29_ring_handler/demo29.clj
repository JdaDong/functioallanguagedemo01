(ns demo29
  "Ring：Clojure 的 HTTP 抽象。
   核心模型：handler 是普通函数 request-map → response-map。
   中间件：包装 handler 的高阶函数 (handler -> handler)。

   参考：https://github.com/ring-clojure/ring/wiki
   运行：clojure -M:run"
  (:require [ring.adapter.jetty :as jetty])
  (:import [java.net URL]
           [java.io BufferedReader InputStreamReader]))

;; ───────────────────────────────────────────────────────────────────────
;; 1. handler = (fn [request] response)
;;    request  = {:request-method :get/:post, :uri "/x", :headers {...}, :body InputStream}
;;    response = {:status 200, :headers {...}, :body "string-or-stream"}
;; ───────────────────────────────────────────────────────────────────────
(defn hello-handler [request]
  {:status  200
   :headers {"Content-Type" "text/plain"}
   :body    (str "Hello from " (:uri request))})

;; ───────────────────────────────────────────────────────────────────────
;; 2. 中间件 = (fn [handler] (fn [request] ...))
;;    它包了一层，能在请求/响应前后做事
;; ───────────────────────────────────────────────────────────────────────
(defn wrap-log [handler]
  (fn [request]
    (println (format "  [LOG] %s %s"
                     (-> request :request-method name .toUpperCase)
                     (:uri request)))
    (let [response (handler request)]
      (println (format "  [LOG] -> %d" (:status response)))
      response)))

(defn wrap-timing [handler]
  (fn [request]
    (let [t0 (System/nanoTime)
          response (handler request)
          ms (/ (- (System/nanoTime) t0) 1e6)]
      (assoc-in response [:headers "X-Elapsed-Ms"]
                (format "%.2f" ms)))))

(defn wrap-content-type-default [handler]
  (fn [request]
    (let [resp (handler request)]
      (if (get-in resp [:headers "Content-Type"])
        resp
        (assoc-in resp [:headers "Content-Type"] "text/plain")))))

;; ───────────────────────────────────────────────────────────────────────
;; 3. 简易路由：直接在 handler 里 case 分派（demo 30 才用 compojure）
;; ───────────────────────────────────────────────────────────────────────
(defn router [request]
  (case [(:request-method request) (:uri request)]
    [:get "/"]       {:status 200 :body "root"}
    [:get "/hello"]  (hello-handler request)
    [:get "/slow"]   (do (Thread/sleep 50) {:status 200 :body "slow done"})
    [:post "/echo"]  {:status 200
                      :headers {"Content-Type" "text/plain"}
                      :body (slurp (:body request))}
    {:status 404 :body "not found"}))

;; 中间件链：从下往上读 = 从外往内执行
;; wrap-log 最外层（先打日志），wrap-timing 中间，wrap-content-type-default 最里
(def app
  (-> router
      wrap-content-type-default
      wrap-timing
      wrap-log))

;; ───────────────────────────────────────────────────────────────────────
;; 4. 自请求工具：用 java.net 直接发 HTTP，避免再拉 clj-http 依赖
;; ───────────────────────────────────────────────────────────────────────
(defn http-request
  "发起 HTTP 请求，返回 {:status :body :elapsed-ms}"
  [method url & [body]]
  (let [conn (.openConnection (URL. url))]
    (.setRequestMethod conn method)
    (.setDoInput conn true)
    (when body
      (.setDoOutput conn true)
      (with-open [os (.getOutputStream conn)]
        (.write os (.getBytes body "UTF-8"))))
    (let [status (.getResponseCode conn)
          stream (if (>= status 400) (.getErrorStream conn) (.getInputStream conn))
          body   (with-open [r (BufferedReader. (InputStreamReader. stream "UTF-8"))]
                   (apply str (line-seq r)))
          elapsed (.getHeaderField conn "X-Elapsed-Ms")]
      {:status status :body body :elapsed-ms elapsed})))

(defn -main [& _]
  (let [port   33901
        server (jetty/run-jetty app {:port port :join? false})]
    (try
      (println (format "=== Server up on http://localhost:%d ===\n" port))

      (println "→ GET /")
      (println "  ←" (http-request "GET" (str "http://localhost:" port "/")))
      (println)

      (println "→ GET /hello")
      (println "  ←" (http-request "GET" (str "http://localhost:" port "/hello")))
      (println)

      (println "→ GET /slow（看 X-Elapsed-Ms 中间件起效）")
      (let [r (http-request "GET" (str "http://localhost:" port "/slow"))]
        (println "  ← status =" (:status r) " body =" (:body r))
        (println "    X-Elapsed-Ms =" (:elapsed-ms r) "（应 ≥ 50ms）"))
      (println)

      (println "→ POST /echo  body=hi")
      (println "  ←" (http-request "POST" (str "http://localhost:" port "/echo") "hi"))
      (println)

      (println "→ GET /nope（404）")
      (println "  ←" (http-request "GET" (str "http://localhost:" port "/nope")))
      (println)

      (println "=== 一句话总结 ===")
      (println "- handler = pure fn: request-map → response-map")
      (println "- 中间件 = (handler -> handler)，从外往内包")
      (println "- 链顺序：(-> h mw1 mw2 mw3)，mw3 最外层（先看到 request，后看到 response）")
      (println "- 不依赖框架的状态机/注解，纯函数组合就能撑起 HTTP 服务")

      (finally
        (.stop server)
        (shutdown-agents)))))
