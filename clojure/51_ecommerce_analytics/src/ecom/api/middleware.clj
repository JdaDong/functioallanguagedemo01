(ns ecom.api.middleware
  "Ring middleware：JSON 编解码 + 日志 + 异常包装。

   不做：鉴权（演示项目跳过，见 Step 6 决策）。"
  (:require [jsonista.core :as json]
            [clojure.string :as str]))

(def ^:private mapper
  (json/object-mapper {:decode-key-fn keyword
                       :encode-key-fn name}))

(defn wrap-json-body
  "若 Content-Type 含 application/json 且有 body，解析为 :body-params。
   响应 :body 若是 map/vector，自动 json 序列化。"
  [handler]
  (fn [req]
    (let [ct (or (get-in req [:headers "content-type"]) "")
          req (if (and (str/includes? ct "application/json")
                       (:body req))
                (let [body-str (slurp (:body req))]
                  (if (str/blank? body-str)
                    (assoc req :body-params {})
                    (assoc req :body-params
                           (json/read-value body-str mapper))))
                req)
          resp (handler req)
          body (:body resp)]
      (if (or (map? body) (vector? body) (seq? body))
        (-> resp
            (assoc :body (json/write-value-as-string body mapper))
            (assoc-in [:headers "Content-Type"] "application/json"))
        resp))))

(defn wrap-log
  "请求日志：method path -> status (Nms)"
  [handler]
  (fn [req]
    (let [t0 (System/nanoTime)
          resp (handler req)
          ms (/ (- (System/nanoTime) t0) 1e6)]
      (println (format "  [http] %-6s %-30s -> %d  (%.1f ms)"
                       (-> req :request-method name str/upper-case)
                       (:uri req)
                       (:status resp)
                       ms))
      resp)))

(defn wrap-exception
  "把 handler 抛出的异常转成 500 + JSON 报错 body"
  [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (println "  [http]  ✗ exception:" (.getMessage t))
        {:status 500
         :body   {:error (.getMessage t)
                  :class (str (class t))}}))))

(defn wrap-defaults
  "把以上 3 个 middleware 链起来"
  [handler]
  (-> handler
      wrap-json-body
      wrap-exception
      wrap-log))