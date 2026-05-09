-- 44_AutoDiff.hs
-- ============================================================================
-- 行业应用场景：自动微分（机器学习 / 科学计算）
-- ----------------------------------------------------------------------------
-- 背景：PyTorch / TensorFlow 的 autograd 本质就是自动微分。
-- Haskell 能用 ~20 行核心代码实现前向模式 AD（对偶数法），
-- 是"函数式为什么强"的绝佳示范 —— 只靠类型类重载，就能把任意数值函数
-- 自动提升为"同时算出值和导数"的版本。
--
-- 运行： runghc 44_AutoDiff.hs
-- ============================================================================

module Main where

-- ---------------------------------------------------------------------------
-- 1. 对偶数 Dual Number
--    D a b 表示一个带无穷小分量的数：a + b·ε，其中 ε² = 0
--    乘法展开：(a + b·ε)(c + d·ε) = ac + (ad + bc)·ε
--    所以 b 分量自动就是一阶导数。
-- ---------------------------------------------------------------------------

data D = D Double Double  -- 值, 导数
    deriving Show

-- 把对偶数变成 Num / Fractional / Floating 的实例，
-- 所有用 +, *, /, sin, exp, ... 写的"普通数值代码"都能自动微分。

instance Num D where
    D a a' + D b b' = D (a + b) (a' + b')
    D a a' - D b b' = D (a - b) (a' - b')
    D a a' * D b b' = D (a * b) (a'*b + a*b')          -- 乘积法则
    negate (D a a') = D (negate a) (negate a')
    abs (D a a')    = D (abs a) (signum a * a')
    signum (D a _)  = D (signum a) 0
    fromInteger n   = D (fromInteger n) 0

instance Fractional D where
    D a a' / D b b' = D (a / b) ((a'*b - a*b') / (b*b))  -- 商法则
    fromRational r  = D (fromRational r) 0

instance Floating D where
    pi               = D pi 0
    exp (D a a')     = D (exp a) (exp a * a')
    log (D a a')     = D (log a) (a' / a)
    sqrt (D a a')    = let s = sqrt a in D s (a' / (2 * s))
    sin (D a a')     = D (sin a) (cos a * a')
    cos (D a a')     = D (cos a) (-sin a * a')
    -- 其余方法用不上，省略
    asin  = error "not needed"
    acos  = error "not needed"
    atan  = error "not needed"
    sinh  = error "not needed"
    cosh  = error "not needed"
    asinh = error "not needed"
    acosh = error "not needed"
    atanh = error "not needed"

-- ---------------------------------------------------------------------------
-- 2. 把一个 (D -> D) 的函数，喂入变量 + 种子，拿到 (值, 导数)
-- ---------------------------------------------------------------------------

-- | diff f x = f'(x)
diff :: (D -> D) -> Double -> Double
diff f x = let D _ dy = f (D x 1) in dy

-- | valAndDiff f x = (f(x), f'(x))
valAndDiff :: (D -> D) -> Double -> (Double, Double)
valAndDiff f x = let D y dy = f (D x 1) in (y, dy)

-- ---------------------------------------------------------------------------
-- 3. 梯度下降：用自动微分求出的导数直接迭代
-- ---------------------------------------------------------------------------

-- | 一步梯度下降： x ← x − lr · f'(x)
stepGD :: Double -> (D -> D) -> Double -> Double
stepGD lr f x = x - lr * diff f x

-- | 多步，返回轨迹
runGD :: Int -> Double -> (D -> D) -> Double -> [Double]
runGD n lr f x0 = take (n + 1) (iterate (stepGD lr f) x0)

-- ---------------------------------------------------------------------------
-- 4. Demo
-- ---------------------------------------------------------------------------

-- 例 1：f(x) = x^3 + 2x，解析导数 f'(x) = 3x^2 + 2
f1 :: D -> D
f1 x = x*x*x + 2*x

-- 例 2：f(x) = sin(x) · exp(x)
--   f'(x) = cos(x) exp(x) + sin(x) exp(x) = (cos x + sin x) * exp x
f2 :: D -> D
f2 x = sin x * exp x

-- 例 3：(x - 3)^2 + 1 ，极小值点 x* = 3，最小值 1
f3 :: D -> D
f3 x = (x - 3)*(x - 3) + 1

main :: IO ()
main = do
    putStrLn "=== 自动微分：对偶数法 (20 行核心) ==="
    putStrLn ""

    putStrLn "-- f1(x) = x^3 + 2x，解析 f'(x) = 3x^2 + 2 --"
    mapM_ (check f1 (\x -> 3*x*x + 2)) [0, 1, 3, 5]
    putStrLn ""

    putStrLn "-- f2(x) = sin(x) * exp(x) --"
    let (v, d) = valAndDiff f2 1.0
    putStrLn $ "  x=1.0:  f=" ++ show v ++ "   f'=" ++ show d
    putStrLn $ "  解析对照 f'(1) = (cos1 + sin1) * exp1 = "
            ++ show ((cos 1 + sin 1) * exp 1)
    putStrLn ""

    putStrLn "-- 梯度下降求 f3(x) = (x-3)^2 + 1 的极小值 --"
    let traj = runGD 15 0.3 f3 (-5)
    putStrLn $ "  初始 x0 = -5, 学习率 = 0.3, 迭代 15 步"
    putStrLn $ "  x 轨迹: " ++ show (map (roundTo 3) traj)
    putStrLn $ "  最终 x = " ++ show (roundTo 4 (last traj))
                ++ " (真值 3)"
  where
    check :: (D -> D) -> (Double -> Double) -> Double -> IO ()
    check f fp x = do
        let d = diff f x
            e = fp x
        putStrLn $ "  x=" ++ show x
                ++ "   AD f'=" ++ show d
                ++ "   analytic=" ++ show e
                ++ "   match=" ++ show (abs (d - e) < 1e-9)

    roundTo :: Int -> Double -> Double
    roundTo k v = fromIntegral (round (v * 10^k) :: Int) / 10^k

-- ---------------------------------------------------------------------------
-- 关键看点
-- ---------------------------------------------------------------------------
-- 1. 只定义了一个 ADT `D` 和三个 Num/Fractional/Floating 实例
-- 2. 写好的 f1/f2/f3 看起来和普通数学公式一模一样，没有任何 "框架标记"
-- 3. 编译期类型类分派就自动挑中对偶数的乘法/链式法则，零运行时开销
-- 4. 这就是 Haskell `ad`、JAX、PyTorch autograd 共同的核心思想；
--    区别只在：反向模式 AD 还需要记录计算图，这里是前向模式
