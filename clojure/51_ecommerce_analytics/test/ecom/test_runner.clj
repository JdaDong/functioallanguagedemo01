(ns ecom.test-runner
  "clojure.test 入口：clojure -M:test 执行此 ns 的 -main，
   显式 require 3 个测试 ns 后 run-tests。零依赖（纯 clojure.test）。"
  (:require [clojure.test :as t]
            [ecom.conservation-test]
            [ecom.mbql-compile-test]
            [ecom.api-e2e-test]))

(defn -main [& _]
  (let [{:keys [fail error] :as r}
        (t/run-tests 'ecom.conservation-test
                     'ecom.mbql-compile-test
                     'ecom.api-e2e-test)]
    (println "\n=== run-tests 汇总 ===")
    (println r)
    (shutdown-agents)
    (System/exit (if (and (zero? (or fail 0)) (zero? (or error 0)))
                   0 1))))
