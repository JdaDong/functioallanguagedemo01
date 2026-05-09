-- ============================================================
-- Demo 37: 惰性求值的实战小套路
--
-- 01 号讲过惰性的基础（无限列表、惰性只在需要时计算）。
-- 本 Demo 聚焦四个真正用得上的惰性技巧：
--
--   (1) 无限流 Fibonacci —— 用 zipWith 自引用递推，O(n) 生成前 n 项
--   (2) 牛顿迭代开平方    —— 生成"越来越精确的近似值"无穷流，按精度截断
--   (3) foldr 的短路      —— any/all 在找到结果后立即停，无须遍历全部
--   (4) 空间泄漏示例      —— 朴素 sum 会堆积 thunks，foldl' 才能常数空间
--
-- 关键思想：在 Haskell 里，"生成 + 消费"可以分两处写，中间靠惰性连起来。
-- 你写得像在操作一个完整的无穷数据结构，运行时只算到被实际需要的那一部分。
--
-- 跑法：
--   runghc 37_LazyEvaluationTricks.hs
-- ============================================================

module Main where

import Data.List (foldl')

-- ============================================================
-- (1) 自引用的无限 Fibonacci 流
-- ============================================================
--
-- 这是 Haskell 最有名的一行：
--   fibs = 0 : 1 : zipWith (+) fibs (tail fibs)
-- 读法：
--   fibs 本身是 0, 1, (fibs!!0 + fibs!!1), (fibs!!1 + fibs!!2), ...
-- 之所以能编译通过并高效运行，靠的就是惰性 —— 运行时按需展开。

fibs :: [Integer]
fibs = 0 : 1 : zipWith (+) fibs (tail fibs)

demoFibs :: IO ()
demoFibs = do
  putStrLn "\n--- (1) infinite fibs stream ---"
  putStrLn $ "  take 10: " <> show (take 10 fibs)
  putStrLn $ "  fibs!!50 = " <> show (fibs !! 50)

-- ============================================================
-- (2) 牛顿迭代开平方 —— 把"收敛过程"当数据结构
-- ============================================================
--
-- 对 y = sqrt x，迭代公式 y' = (y + x/y) / 2
-- iterate f y0 生成 [y0, f y0, f (f y0), ...] 的无穷流，
-- 我们只需要"相邻两项差 < eps"时截断。
--
-- 把"怎么迭代"和"何时停"解耦：迭代逻辑 f 完全不知道 eps。

newtonSqrt :: Double -> Double -> Double
newtonSqrt eps x =
  let improve y = (y + x / y) / 2
      stream    = iterate improve 1.0     -- 无穷流
      pairs     = zip stream (tail stream) -- 相邻两项
      (_, y)    = head (dropWhile (\(a, b) -> abs (a - b) > eps) pairs)
  in y

demoNewton :: IO ()
demoNewton = do
  putStrLn "\n--- (2) Newton's method on an infinite stream ---"
  let x = 2.0
      y = newtonSqrt 1e-12 x
  putStrLn $ "  sqrt " <> show x <> " ~ " <> show y
  putStrLn $ "  y*y - x = " <> show (y * y - x)

-- ============================================================
-- (3) foldr 的短路 —— any/all 不会遍历全表
-- ============================================================
--
-- any p 用 foldr (\x acc -> p x || acc) False 实现。
-- 因为 (||) 对左参数惰性，一旦 p x == True，acc 这个 thunk 根本不会被触发，
-- 所以 any 对一个"有元素靠前满足"的列表能瞬间停下 —— 就算列表后面是无穷。

demoShortCircuit :: IO ()
demoShortCircuit = do
  putStrLn "\n--- (3) foldr short-circuits over infinite list ---"
  let found = any (> 100) [1 ..]            -- [1..] 是无穷流
  putStrLn $ "  any (>100) [1..] = " <> show found
  let all10 = all (< 10) [1 .. 9]
  putStrLn $ "  all (<10) [1..9] = " <> show all10

-- ============================================================
-- (4) 惰性陷阱：foldl 的 thunks 堆积
-- ============================================================
--
-- 这是 Haskell 新手最容易踩的坑："惰性 + 大数据 = 爆内存"。
-- sum 用 foldl (+) 0 在不开严格的情况下，会一路堆 (((0+1)+2)+3)+... 的 thunks，
-- 到真正 print 时再统一算，峰值内存 O(n)。
-- 换成 foldl'（严格左折）就 O(1) 额外空间。

naiveSum :: [Int] -> Int
naiveSum = foldl (+) 0           -- 惰性：堆 thunks

strictSum :: [Int] -> Int
strictSum = foldl' (+) 0         -- 严格：常数空间

demoSpaceLeak :: IO ()
demoSpaceLeak = do
  putStrLn "\n--- (4) foldl (lazy) vs foldl' (strict) ---"
  let xs = [1 .. 1000000 :: Int]
  putStrLn $ "  naiveSum  (foldl ) = " <> show (naiveSum  xs)
  putStrLn $ "  strictSum (foldl') = " <> show (strictSum xs)
  putStrLn   "  （naiveSum 在更大数据上会栈溢出或爆内存；foldl' 不会）"

-- ============================================================
-- Main
-- ============================================================

main :: IO ()
main = do
  putStrLn "=========================================="
  putStrLn "Demo 37: Lazy Evaluation Tricks"
  putStrLn "=========================================="
  demoFibs
  demoNewton
  demoShortCircuit
  demoSpaceLeak
  putStrLn "\nTakeaway:"
  putStrLn "  * 惰性让\"生成\"和\"消费\"彻底解耦"
  putStrLn "  * 但要小心 foldl 的 thunks 堆积 —— 累加类操作用 foldl'"
  putStrLn "=========================================="
