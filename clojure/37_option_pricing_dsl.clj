;; demo 37 — 期权定价 DSL（二项树 + Black-Scholes）
;;
;; 心智锚：金融衍生品的"形状"可以用数据表达，定价引擎吃数据吐价格。
;; 这呼应 Haskell 经典的 SPJ "Composing contracts"。
;;
;; 实现两个引擎：
;;   1) CRR 二项树（Cox-Ross-Rubinstein）：递归值函数，处理欧式 + 美式
;;   2) Black-Scholes 闭式解：仅欧式
;; 对照验证：欧式期权两种引擎结果误差应 < 5%
;;
;; 运行：clojure -M 37_option_pricing_dsl.clj

(ns demo37)

;; ───────────────────────────────────────────────────────────────────────
;; 期权数据描述
;;   {:type :call/:put
;;    :style :european/:american
;;    :K 行权价  :T 到期(年)  :S0 现价  :r 无风险利率  :sigma 年化波动率}
;; ───────────────────────────────────────────────────────────────────────

(defn payoff [{:keys [type K]} S]
  (case type
    :call (max 0.0 (- S K))
    :put  (max 0.0 (- K S))))

;; ───────────────────────────────────────────────────────────────────────
;; 二项树（CRR）
;;   每步上涨因子 u = exp(sigma sqrt(dt))
;;   下跌因子    d = 1/u
;;   风险中性概率 p = (exp(r dt) - d) / (u - d)
;; ───────────────────────────────────────────────────────────────────────
(defn binomial-price [opt n-steps]
  (let [{:keys [style T S0 r sigma]} opt
        dt (/ T n-steps)
        u  (Math/exp (* sigma (Math/sqrt dt)))
        d  (/ 1.0 u)
        a  (Math/exp (* r dt))
        p  (/ (- a d) (- u d))
        disc (Math/exp (- (* r dt)))]

    ;; 终末层 (i=n) 节点 j∈[0..n] 的资产价 = S0 * u^j * d^(n-j)，对应 payoff
    (loop [i n-steps
           values (mapv (fn [j]
                          (let [S (* S0 (Math/pow u j) (Math/pow d (- n-steps j)))]
                            (payoff opt S)))
                        (range (inc n-steps)))]
      (if (zero? i)
        (first values)
        ;; 向回退一步：节点 (i-1, j) 的延续价 = disc*(p*V[j+1] + (1-p)*V[j])
        ;; 美式：与立即行权 max
        (let [new-i (dec i)
              new-vs (mapv (fn [j]
                             (let [cont (* disc (+ (* p (nth values (inc j)))
                                                   (* (- 1.0 p) (nth values j))))]
                               (case style
                                 :european cont
                                 :american (let [S (* S0 (Math/pow u j)
                                                      (Math/pow d (- new-i j)))]
                                             (max cont (payoff opt S))))))
                           (range (inc new-i)))]
          (recur new-i new-vs))))))

;; ───────────────────────────────────────────────────────────────────────
;; Black-Scholes 闭式（仅欧式）
;;   N(x) = 0.5 * (1 + erf(x/sqrt(2)))；erf 用 Abramowitz & Stegun 7.1.26 近似
;; ───────────────────────────────────────────────────────────────────────
(defn erf [x]
  (let [a1  0.254829592
        a2 -0.284496736
        a3  1.421413741
        a4 -1.453152027
        a5  1.061405429
        p   0.3275911
        sign (if (neg? x) -1.0 1.0)
        x   (Math/abs (double x))
        t   (/ 1.0 (+ 1.0 (* p x)))
        y   (- 1.0 (* (+ (* (+ (* (+ (* (+ (* a5 t) a4) t) a3) t) a2) t) a1)
                      t (Math/exp (- (* x x)))))]
    (* sign y)))

(defn N [x] (* 0.5 (+ 1.0 (erf (/ x (Math/sqrt 2.0))))))

(defn black-scholes-price [{:keys [type K T S0 r sigma]}]
  (let [d1 (/ (+ (Math/log (/ S0 K))
                 (* (+ r (* 0.5 sigma sigma)) T))
              (* sigma (Math/sqrt T)))
        d2 (- d1 (* sigma (Math/sqrt T)))
        disc-K (* K (Math/exp (- (* r T))))]
    (case type
      :call (- (* S0 (N d1)) (* disc-K (N d2)))
      :put  (- (* disc-K (N (- d2))) (* S0 (N (- d1)))))))

;; ───────────────────────────────────────────────────────────────────────
;; 验证：欧式期权两种方法应该收敛
;; ───────────────────────────────────────────────────────────────────────
(defn rel-err [a b] (/ (Math/abs (- a b)) (Math/abs b)))

(defn -main [& _]
  (println "── case 1: ATM 欧式 call ──")
  (let [opt {:type :call :style :european
             :S0 100.0 :K 100.0 :T 1.0 :r 0.05 :sigma 0.2}
        bin (binomial-price opt 500)
        bs  (black-scholes-price opt)]
    (println (format "  binomial(500) = %.4f" bin))
    (println (format "  black-scholes = %.4f" bs))
    (println (format "  rel-err       = %.4f%%" (* 100 (rel-err bin bs))))
    (assert (< (rel-err bin bs) 0.05)))

  (println "\n── case 2: OTM 欧式 put ──")
  (let [opt {:type :put :style :european
             :S0 100.0 :K 90.0 :T 0.5 :r 0.03 :sigma 0.25}
        bin (binomial-price opt 500)
        bs  (black-scholes-price opt)]
    (println (format "  binomial(500) = %.4f" bin))
    (println (format "  black-scholes = %.4f" bs))
    (println (format "  rel-err       = %.4f%%" (* 100 (rel-err bin bs))))
    (assert (< (rel-err bin bs) 0.05)))

  (println "\n── case 3: 美式 put（无 BS 闭式，只用 binomial）──")
  (let [opt-eur {:type :put :style :european
                 :S0 100.0 :K 110.0 :T 1.0 :r 0.05 :sigma 0.3}
        opt-amer (assoc opt-eur :style :american)
        eur-bin  (binomial-price opt-eur 500)
        amer-bin (binomial-price opt-amer 500)]
    (println (format "  欧式 put binomial = %.4f" eur-bin))
    (println (format "  美式 put binomial = %.4f" amer-bin))
    (println (format "  美式溢价         = %.4f （应 ≥ 0，因为美式可提前行权）"
                     (- amer-bin eur-bin)))
    (assert (>= amer-bin eur-bin)))

  (println "\n── case 4: 收敛性 —— n 越大越接近 BS ──")
  (let [opt {:type :call :style :european
             :S0 100.0 :K 100.0 :T 1.0 :r 0.05 :sigma 0.2}
        bs (black-scholes-price opt)]
    (println (format "  BS 标准答案 = %.6f" bs))
    (doseq [n [10 50 200 1000]]
      (let [v (binomial-price opt n)]
        (println (format "  n=%-4d  binomial=%.6f  rel-err=%.4f%%"
                         n v (* 100 (rel-err v bs)))))))

  (println "\n=== 一句话总结 ===")
  (println "- 期权 = 数据 {:type :style :K :T :S0 :r :sigma}")
  (println "- 定价引擎 = (opt -> price) 的纯函数；同一份数据多个引擎可并行")
  (println "- 二项树：递归向后归纳；美式 = max(立即行权, 持有继续)")
  (println "- Black-Scholes：仅欧式，闭式解，O(1)")
  (println "- 收敛性：n→∞ 时 binomial → BS（这正是定价引擎的'交叉验证'技巧）")
  (shutdown-agents))

(-main)
