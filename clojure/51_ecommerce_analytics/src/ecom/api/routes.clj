(ns ecom.api.routes
  "reitit 路由表 + ring handler 装配。"
  (:require [reitit.ring :as rring]
            [ecom.api.handlers   :as h]
            [ecom.api.middleware :as mw]))

(defn make-routes [deps]
  (let [hs (h/make-handlers deps)]
    [["/health"          {:get  (fn [_] {:status 200 :body {:ok true}})}]

     ["/users/register"  {:post (:register-user hs)}]
     ["/users/login"     {:post (:login-user hs)}]

     ["/orders"
      ["" {:post (:place-order hs)}]
      ["/:id"           {:get (:get-order hs)}]
      ["/:id/pay"       {:post (:pay-order hs)}]
      ["/:id/ship"      {:post (:ship-order hs)}]
      ["/:id/deliver"   {:post (:deliver-order hs)}]
      ["/:id/cancel"    {:post (:cancel-order hs)}]]

     ["/analytics"
      ["/sales-by-sku"  {:get (:sales-by-sku hs)}]
      ["/top-users"     {:get (:top-users hs)}]
      ["/window/mom"    {:get (:window-mom hs)}]]]))

(defn make-ring-handler
  "返回一个 ring handler：所有 middleware 包好"
  [deps]
  (-> (rring/ring-handler
        (rring/router (make-routes deps))
        (rring/create-default-handler))
      mw/wrap-defaults))