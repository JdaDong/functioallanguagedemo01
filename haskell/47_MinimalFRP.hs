-- 47_MinimalFRP.hs — 最小函数式响应式编程（FRP）核心，从零实现
--
-- 行业背景：
--   * FRP 起源：Conal Elliott 1997 年提出，用于游戏/动画/UI 的"时间维度"建模
--   * 业界产品：Reflex（Haskell GUI/Web）、Yampa（机器人/游戏）、Sodium、bacon.js、RxJS
--   * 核心思想：把"随时间变化的值"和"离散事件"建模成一等公民，用纯函数组合
--
-- 本 demo 不引入任何 FRP 库，从零实现一个"古典 FRP"内核：
--   1. Behavior a = Time -> a              （连续随时间变化的值）
--   2. Event a    = [(Time, a)]            （离散时间点上的事件流）
--   3. 基础组合子：fmap / filterE / mergeE / accumE
--   4. 高阶 FRP：snapshot / switchB（基于事件切换 Behavior）
--   5. Demo：(a) 累积计数器 (b) 一个"按键 -> 位置"的最小交互模拟
--
-- 运行：runghc 47_MinimalFRP.hs

module Main where

import Data.List (foldl', sort)

------------------------------------------------------------
-- 0. 时间
------------------------------------------------------------
type Time = Double

------------------------------------------------------------
-- 1. Behavior：随时间变化的连续值
------------------------------------------------------------
-- 经典定义：Behavior a 就是一个 Time -> a 函数
-- 这种"语义优先"的建模是 Conal Elliott 古典 FRP 的标志

newtype Behavior a = Behavior { sampleAt :: Time -> a }

-- Behavior 是 Functor / Applicative：天然就是 Reader Time
instance Functor Behavior where
  fmap f (Behavior g) = Behavior (f . g)

instance Applicative Behavior where
  pure x = Behavior (\_ -> x)
  Behavior f <*> Behavior g = Behavior (\t -> f t (g t))

-- 常见 Behavior 构造
constB :: a -> Behavior a
constB = pure

timeB :: Behavior Time
timeB = Behavior id

-- 把两个 Behavior 用二元函数组合
liftB2 :: (a -> b -> c) -> Behavior a -> Behavior b -> Behavior c
liftB2 f a b = f <$> a <*> b

------------------------------------------------------------
-- 2. Event：离散时间点上的值流
------------------------------------------------------------
-- 经典定义：Event a = [(Time, a)]，按时间升序排列
-- 实际工业实现会用懒 list 或 push-pull，本 demo 用严格 list 即可

newtype Event a = Event { occs :: [(Time, a)] }

instance Show a => Show (Event a) where
  show (Event xs) = "Event " ++ show xs

instance Functor Event where
  fmap f (Event xs) = Event [(t, f x) | (t, x) <- xs]

-- 过滤事件（保持时间顺序）
filterE :: (a -> Bool) -> Event a -> Event a
filterE p (Event xs) = Event [(t, x) | (t, x) <- xs, p x]

-- 合并两条事件流（按时间归并）
mergeE :: Event a -> Event a -> Event a
mergeE (Event xs) (Event ys) = Event (merge xs ys)
  where
    merge [] bs = bs
    merge as [] = as
    merge (a@(ta,_):as) (b@(tb,_):bs)
      | ta <= tb  = a : merge as (b:bs)
      | otherwise = b : merge (a:as) bs

-- 把事件值用一个二元函数累积成"状态变化"事件流
-- accumE z e :  每个事件用 (acc -> a -> acc) 把状态推进，并把新状态作为事件值
accumE :: b -> (b -> a -> b) -> Event a -> Event b
accumE z f (Event xs) = Event (go z xs)
  where
    go _ []           = []
    go acc ((t,x):rest) =
      let acc' = f acc x
      in (t, acc') : go acc' rest

------------------------------------------------------------
-- 3. Behavior <-> Event 互操作
------------------------------------------------------------

-- snapshot：每次事件发生时，从 Behavior 上"拍一张照"
snapshot :: Behavior a -> Event b -> Event (a, b)
snapshot b (Event xs) = Event [(t, (sampleAt b t, x)) | (t, x) <- xs]

-- stepper：用一条事件流 + 初始值"阶梯式"地构造 Behavior
-- 在事件 t1 之前值是 z；t1..t2 之间是 v1；t2..t3 之间是 v2 ...
stepper :: a -> Event a -> Behavior a
stepper z (Event xs) = Behavior $ \t ->
  case takeWhile (\(ti, _) -> ti <= t) xs of
    [] -> z
    ys -> snd (last ys)

-- switchB：根据"包含 Behavior 的事件"，让当前 Behavior 在事件触发时切换
-- 经典 FRP 中这是高阶能力（Behavior 的值可以是 Behavior）
switchB :: Behavior a -> Event (Behavior a) -> Behavior a
switchB initial (Event xs) = Behavior $ \t ->
  case takeWhile (\(ti, _) -> ti <= t) xs of
    [] -> sampleAt initial t
    ys -> sampleAt (snd (last ys)) t

------------------------------------------------------------
-- 4. Demo (a)：累积计数器
------------------------------------------------------------
-- 一组按键事件，每次 'a' 加 1，'b' 减 1，'r' 重置为 0
-- 用 accumE 演示状态推进

data Key = KeyA | KeyB | KeyR deriving (Show, Eq)

keyEvents :: Event Key
keyEvents = Event
  [ (0.1, KeyA), (0.4, KeyA), (0.7, KeyB), (1.0, KeyA)
  , (1.3, KeyA), (1.6, KeyR), (1.9, KeyA), (2.1, KeyB)
  ]

stepKey :: Int -> Key -> Int
stepKey n KeyA = n + 1
stepKey n KeyB = n - 1
stepKey _ KeyR = 0

counterE :: Event Int
counterE = accumE 0 stepKey keyEvents

------------------------------------------------------------
-- 5. Demo (b)：键 -> 速度 -> 位置 的最小交互模拟
------------------------------------------------------------
-- 这是一段典型 FRP 模式：
--   * 输入：按键事件流（左/右/停）
--   * 中间：用 stepper 把"事件流 + 初值"提升成 Behavior（当前速度）
--   * 集成：position(t) = ∫₀ᵗ velocity(τ) dτ
-- 这里我们用"等距时间网格 + 矩形积分"近似积分。

data Move = MoveLeft | MoveRight | MoveStop deriving (Show, Eq)

moveEvents :: Event Move
moveEvents = Event
  [ (0.0, MoveRight)   -- 0.0~0.5 速度 +1
  , (0.5, MoveStop)    -- 0.5~0.8 速度 0
  , (0.8, MoveRight)   -- 0.8~1.5 速度 +1
  , (1.5, MoveLeft)    -- 1.5~2.0 速度 -1
  , (2.0, MoveStop)
  ]

moveToVel :: Move -> Double
moveToVel MoveLeft  = -1.0
moveToVel MoveRight =  1.0
moveToVel MoveStop  =  0.0

-- 把 Move 事件流转成"当前速度" Behavior
velocityB :: Behavior Double
velocityB = moveToVel <$> stepper MoveStop moveEvents

-- 数值积分：position(t) = ∑ v(t_i) * dt
integrateB :: Double -> Time -> Time -> Time -> Behavior Double -> [(Time, Double)]
integrateB x0 t0 tEnd dt v = go t0 x0 []
  where
    go !t !x acc
      | t > tEnd  = reverse ((t, x) : acc)
      | otherwise =
          let vNow = sampleAt v t
              x'   = x + vNow * dt
          in go (t + dt) x' ((t, x) : acc)

positionTrajectory :: [(Time, Double)]
positionTrajectory = integrateB 0.0 0.0 2.5 0.25 velocityB

------------------------------------------------------------
-- 6. Demo (c)：switchB —— 在事件触发时切换 Behavior
------------------------------------------------------------
-- 在 t=1.0 之前用 sin(t)，t=1.0 之后切换到 cos(t)+10

switchDemo :: Behavior Double
switchDemo =
  let bSin = Behavior sin
      bCos = Behavior (\t -> cos t + 10)
      e    = Event [(1.0, bCos)]
  in switchB bSin e

------------------------------------------------------------
-- 7. main
------------------------------------------------------------

-- 工具：snapshot 整个 Behavior 在一组采样时间点
sampleSeries :: Behavior a -> [Time] -> [(Time, a)]
sampleSeries b ts = [(t, sampleAt b t) | t <- ts]

main :: IO ()
main = do
  putStrLn "=== Minimal FRP Demo ==="

  -- ---- (a) 累积计数器 ----
  putStrLn "\n[demo a] accumulated count via accumE"
  putStrLn "  按键事件流："
  mapM_ (\(t,k) -> putStrLn $ "    t=" ++ show t ++ "  " ++ show k)
        (occs keyEvents)
  putStrLn "  累积计数（accumE）每事件后的状态："
  mapM_ (\(t,n) -> putStrLn $ "    t=" ++ show t ++ "  count=" ++ show n)
        (occs counterE)

  -- ---- (b) 速度 -> 位置 ----
  putStrLn "\n[demo b] position trajectory: integrate(velocityB)"
  putStrLn "  Move 事件流："
  mapM_ (\(t,m) -> putStrLn $ "    t=" ++ show t ++ "  " ++ show m)
        (occs moveEvents)
  putStrLn "  位置轨迹（dt=0.25）："
  mapM_ (\(t,x) -> putStrLn $
           "    t=" ++ pad 4 (show t) ++ "  x=" ++ showD 3 x)
        positionTrajectory

  -- ---- (c) switchB ----
  putStrLn "\n[demo c] switchB: t<1 用 sin t, t>=1 切到 cos t + 10"
  let ts = [0.0, 0.5, 0.9, 1.0, 1.5, 2.0]
  mapM_ (\(t, x) -> putStrLn $
           "    t=" ++ pad 4 (show t) ++ "  v=" ++ showD 3 x)
        (sampleSeries switchDemo ts)

  -- ---- (d) snapshot：事件触发时拍 Behavior 的快照 ----
  putStrLn "\n[demo d] snapshot velocityB at every key event"
  mapM_ (\(t,(v,k)) -> putStrLn $
           "    t=" ++ pad 4 (show t) ++ "  vel=" ++ showD 3 v
           ++ "  key=" ++ show k)
        (occs (snapshot velocityB keyEvents))

  putStrLn "\n=== Done ==="

-- 简易格式化
showD :: Int -> Double -> String
showD n x =
  let s   = show (fromIntegral (round (x * 10^n) :: Integer) / 10^n :: Double)
  in s

pad :: Int -> String -> String
pad n s
  | length s >= n = s
  | otherwise     = s ++ replicate (n - length s) ' '
