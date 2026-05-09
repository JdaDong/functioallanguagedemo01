-- 42_OptionPricingDSL.hs
-- ============================================================================
-- 行业应用场景：金融期权定价 DSL（Haskell 在工业界最知名的应用之一）
-- ----------------------------------------------------------------------------
-- 背景：渣打银行、巴克莱银行、Jane Street 等都用 Haskell 写衍生品定价引擎。
-- 核心思路 = "合约是数据，定价是解释器"：
--   1. 用 GADT 把"期权合约"表示成一棵带类型标签的树
--   2. 写多个 interpreter：解析解（Black-Scholes）、数值解（蒙特卡洛）
--   3. 同一份合约定义，可以喂给不同定价模型做交叉验证
--
-- 运行： runghc 42_OptionPricingDSL.hs
-- ============================================================================

{-# LANGUAGE GADTs #-}

module Main where

-- 手写 LCG（线性同余生成器），避免依赖 random 包
-- 参数取自 Numerical Recipes，周期 2^32
lcg :: Int -> [Double]
lcg seed = go seed
  where
    a, c, m :: Int
    a = 1664525
    c = 1013904223
    m = 2^(32 :: Int)
    go s = let s' = (a * s + c) `mod` m
           in fromIntegral s' / fromIntegral m : go s'

-- ---------------------------------------------------------------------------
-- 1. 合约 DSL（用 GADT 表示，类型标签确保"钱"和"布尔条件"不会混用）
-- ---------------------------------------------------------------------------

-- | Contract a ：一个到期时支付类型 a 的合约
data Contract a where
    -- | 零合约：啥都不支付
    Zero    :: Contract Double
    -- | 固定现金流
    Cash    :: Double -> Contract Double
    -- | 欧式看涨：到期时支付 max(S_T - K, 0)
    Call    :: Double -> Contract Double   -- strike
    -- | 欧式看跌：到期时支付 max(K - S_T, 0)
    Put     :: Double -> Contract Double
    -- | 两个合约叠加（组合策略，例如 straddle = Call K + Put K）
    And     :: Contract Double -> Contract Double -> Contract Double
    -- | 按标量缩放（方向性/头寸大小）
    Scale   :: Double -> Contract Double -> Contract Double

-- ---------------------------------------------------------------------------
-- 2. 市场参数
-- ---------------------------------------------------------------------------

data Market = Market
    { spot     :: Double   -- 当前价 S0
    , rate     :: Double   -- 无风险利率 r
    , sigma    :: Double   -- 年化波动率
    , maturity :: Double   -- 到期时间 T (年)
    }

-- ---------------------------------------------------------------------------
-- 3. 解释器 A：Black-Scholes 解析解
-- ---------------------------------------------------------------------------

-- 标准正态分布 CDF 的 Abramowitz-Stegun 近似
normCdf :: Double -> Double
normCdf x =
    let a1 =  0.254829592
        a2 = -0.284496736
        a3 =  1.421413741
        a4 = -1.453152027
        a5 =  1.061405429
        p  =  0.3275911
        sign = if x < 0 then -1 else 1
        absX = abs x / sqrt 2
        t = 1.0 / (1.0 + p * absX)
        y = 1.0 - (((((a5*t + a4)*t) + a3)*t + a2)*t + a1)*t * exp (-absX*absX)
    in 0.5 * (1.0 + sign * y)

bsCall :: Market -> Double -> Double
bsCall m k =
    let s = spot m; r = rate m; v = sigma m; t = maturity m
        d1 = (log (s/k) + (r + v*v/2) * t) / (v * sqrt t)
        d2 = d1 - v * sqrt t
    in s * normCdf d1 - k * exp (-r*t) * normCdf d2

bsPut :: Market -> Double -> Double
bsPut m k =
    -- put-call parity: P = C - S + K*exp(-rT)
    bsCall m k - spot m + k * exp (- rate m * maturity m)

-- | 解释器 A：解析解（只对单个 leg 有效，组合靠线性性递归）
priceBS :: Market -> Contract Double -> Double
priceBS _ Zero         = 0
priceBS m (Cash x)     = x * exp (- rate m * maturity m)
priceBS m (Call k)     = bsCall m k
priceBS m (Put k)      = bsPut  m k
priceBS m (And a b)    = priceBS m a + priceBS m b
priceBS m (Scale f c)  = f * priceBS m c

-- ---------------------------------------------------------------------------
-- 4. 解释器 B：蒙特卡洛
-- ---------------------------------------------------------------------------

-- Box-Muller 把两个 U(0,1) 变成一个 N(0,1)
boxMuller :: Double -> Double -> Double
boxMuller u1 u2 = sqrt (-2 * log u1) * cos (2 * pi * u2)

-- | 在给定 GBM 终值 ST 下，合约的 payoff
payoff :: Contract Double -> Double -> Double
payoff Zero         _  = 0
payoff (Cash x)     _  = x
payoff (Call k)     st = max (st - k) 0
payoff (Put  k)     st = max (k - st) 0
payoff (And a b)    st = payoff a st + payoff b st
payoff (Scale f c)  st = f * payoff c st

-- | 解释器 B：蒙特卡洛（固定种子保证可复现）
priceMC :: Int -> Market -> Contract Double -> Double
priceMC nPaths m c =
    let rs = take (2 * nPaths) (lcg 42)
        -- lcg 给出 [0,1)，但要防 log(0)
        safe x = if x <= 0 then 1e-12 else x
        zs = zipWith boxMuller (map safe (take nPaths rs))
                               (map safe (drop nPaths rs))
        s0 = spot m; r = rate m; v = sigma m; t = maturity m
        -- GBM 终值： ST = S0 * exp((r - v²/2)T + v·√T·Z)
        terminalS z = s0 * exp ((r - v*v/2) * t + v * sqrt t * z)
        payoffs = map (payoff c . terminalS) zs
        avg = sum payoffs / fromIntegral nPaths
    in exp (- r * t) * avg

-- ---------------------------------------------------------------------------
-- 5. Demo
-- ---------------------------------------------------------------------------

main :: IO ()
main = do
    let market = Market { spot = 100, rate = 0.05, sigma = 0.2, maturity = 1.0 }
        -- 合约 1：ATM 欧式看涨
        callContract = Call 100
        -- 合约 2：straddle = 同 strike 的 Call + Put（押注"动就行，方向不重要"）
        straddle     = And (Call 100) (Put 100)
        -- 合约 3：2 手 Call，减去 1 手 Put（就是个方向性策略）
        combo        = And (Scale 2 (Call 100))
                           (Scale (-1) (Put 100))

    putStrLn "=== 期权定价 DSL：BS 解析解 vs 蒙特卡洛 (100k paths) ==="
    putStrLn $ "市场: S=100, r=5%, σ=20%, T=1y"
    putStrLn ""

    mapM_ (printRow market) contracts
  where
    contracts =
        [ ("Call K=100",                      Call 100)
        , ("Put  K=100",                      Put 100)
        , ("Straddle (Call+Put, K=100)",      And (Call 100) (Put 100))
        , ("Scale 2 * Call - Put",            And (Scale 2 (Call 100))
                                                  (Scale (-1) (Put 100)))
        ]

    printRow m (label, c) = do
        let bs = priceBS m c
            mc = priceMC 100000 m c
            err = abs (bs - mc) / bs * 100
        putStrLn $ pad 32 label
                ++ "  BS=" ++ fmt bs
                ++ "   MC=" ++ fmt mc
                ++ "   偏差=" ++ fmt err ++ "%"

    pad n s = s ++ replicate (max 0 (n - length s)) ' '
    fmt x   = let s = show (fromIntegral (round (x*1000) :: Int) / 1000.0 :: Double)
              in s

-- ---------------------------------------------------------------------------
-- 关键看点
-- ---------------------------------------------------------------------------
-- 1. 合约 = 数据：Contract 是 GADT，新增期权类型（Asian、Barrier…）只需加构造子
-- 2. 定价 = 解释器：priceBS / priceMC / payoff 各管一摊，互不影响
-- 3. 组合律：And / Scale 让复杂策略（straddle、spread）组合起来天然正确
-- 4. 交叉验证：同一份 DSL 喂给两种模型，两个数差得很大立刻就知道写错了
-- 这就是渣打、巴克莱用 Haskell 写定价引擎的核心范式：**DSL + 多解释器**。
