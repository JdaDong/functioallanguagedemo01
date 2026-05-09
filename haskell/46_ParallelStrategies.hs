{- cabal:
build-depends: base, parallel, deepseq, time
ghc-options: -threaded -rtsopts -with-rtsopts=-N
-}
-- 46_ParallelStrategies.hs — Haskell 并行（非并发）：par / pseq / Strategies
--
-- 行业背景：
--   * 并发（concurrency）：多个任务交错运行，强调"同时进行多件事"——10/19/33/34 号 demo 已覆盖
--   * 并行（parallelism）：单个任务被切成多块同时跑在多核 CPU 上，强调"算得更快"
--   * Haskell 是少有的"声明式并行"语言：业务代码不变，只在末尾标几个 `par` 就能多核加速
--   * 工业上，金融定价/风险计算/科学模拟在 Haskell 端常用 Strategies 库做并行
--
-- 运行方式（关键，与其他 demo 不同）：
--     cabal run 46_ParallelStrategies.hs
--   cabal block 里已经写了 `-threaded -with-rtsopts=-N`，启动就自动用所有核。
--   首次运行 cabal 会自动从 Hackage 拉 parallel + deepseq + time 包。
--   想观察 spark 详情可加：cabal run 46_ParallelStrategies.hs -- +RTS -s -RTS

{-# LANGUAGE BangPatterns #-}

module Main where

import Control.DeepSeq        (NFData, deepseq, force)
import Control.Parallel       (par, pseq)
import Control.Parallel.Strategies
  ( Strategy, parList, parListChunk, rdeepseq, rpar
  , using, withStrategy, runEval
  )
import Data.List              (foldl')
import Data.Time.Clock        (getCurrentTime, diffUTCTime)
import System.CPUTime         (getCPUTime)
import Text.Printf            (printf)

------------------------------------------------------------
-- 1. 计时工具：拿 CPUTime 简单测一下"挂钟时间"近似
------------------------------------------------------------
-- 注意：getCPUTime 测的是"消耗 CPU 时间"（多核会累加多个核的时间），
-- 看"加速比"需要看挂钟时间。我们用 getCurrentTime 做挂钟。

-- 强制把结果计算到 normal form 再返回，避免惰性"假跑"
timeIt :: NFData a => String -> IO a -> IO a
timeIt label act = do
  t0 <- getCurrentTime
  !x <- act
  let !y = force x
  t1 <- y `deepseq` getCurrentTime
  printf "  [%-22s] %.3f s\n" label (realToFrac (diffUTCTime t1 t0) :: Double)
  return y

------------------------------------------------------------
-- 2. 经典玩具：并行 Fibonacci
------------------------------------------------------------
-- 故意写成指数复杂度的 naive 版，让单线程也跑得够久，便于看出加速。

fib :: Int -> Integer
fib 0 = 0
fib 1 = 1
fib n = fib (n - 1) + fib (n - 2)

-- 串行版：直接相加
fibSeq :: Int -> Integer
fibSeq n
  | n < 2     = fromIntegral n
  | otherwise = fibSeq (n - 1) + fibSeq (n - 2)

-- 并行版：用 par 提示"左子树可以并行算"，pseq 强制先算右子树再相加
-- par a b 的语义：把 a 的求值"火花"（spark）扔给运行时调度器，立即返回 b
-- pseq a b：先求 a 到 WHNF，再返回 b（保证求值顺序）
fibPar :: Int -> Int -> Integer
fibPar cutoff = go
  where
    go n
      | n < cutoff = fib n            -- 任务太小不值得 par，直接串行
      | otherwise  =
          let a = go (n - 1)
              b = go (n - 2)
          in a `par` b `pseq` a + b

------------------------------------------------------------
-- 3. 并行 map：Strategies 风格
------------------------------------------------------------
-- 用法是"业务代码 + 策略"分离：先写业务计算，最后用 `using` 套上策略

-- 一个稍微有点份量的 worker：判断质数（trial division）
isPrime :: Int -> Bool
isPrime n
  | n < 2     = False
  | n < 4     = True
  | even n    = False
  | otherwise = go 3
  where
    go i
      | i * i > n      = True
      | n `mod` i == 0 = False
      | otherwise      = go (i + 2)

-- 串行：在 [lo..hi] 区间里数质数
countPrimesSeq :: Int -> Int -> Int
countPrimesSeq lo hi = length (filter isPrime [lo..hi])

-- 并行 (a)：parList rdeepseq —— 每个元素一个 spark
-- 简单粗暴，适合"每个任务足够大、任务数不太多"的场景
countPrimesParList :: Int -> Int -> Int
countPrimesParList lo hi =
  let bs = map isPrime [lo..hi] `using` parList rdeepseq
  in length (filter id bs)

-- 并行 (b)：parListChunk —— 把列表切成 chunk，每个 chunk 一个 spark
-- 工业首选：避免 spark 数量爆炸（每个 spark 都有调度开销）
countPrimesChunk :: Int -> Int -> Int -> Int
countPrimesChunk chunk lo hi =
  let bs = map isPrime [lo..hi] `using` parListChunk chunk rdeepseq
  in length (filter id bs)

------------------------------------------------------------
-- 4. 并行 map-reduce：手写 fold 模板
------------------------------------------------------------
-- 经典分治：把列表切两半，左右并行求和，再合并。

-- 业务函数：对每个 Int 跑一个"假装很贵"的计算
-- 用 fib 作为 cost-amplifier，让 reduce 阶段有意义
expensive :: Int -> Integer
expensive x = fib (18 + x `mod` 4)   -- 18~21 之间，单次 ~30ms 量级

-- 串行 map-reduce
mapReduceSeq :: [Int] -> Integer
mapReduceSeq xs = foldl' (+) 0 (map expensive xs)

-- 并行 map-reduce：用 parListChunk 并行 map，再串行求和
mapReducePar :: Int -> [Int] -> Integer
mapReducePar chunk xs =
  let ys = map expensive xs `using` parListChunk chunk rdeepseq
  in foldl' (+) 0 ys

-- 真·分治（Eval monad 版）：手写并行 fold
-- 演示 runEval + rpar 的更底层用法
parFoldSum :: [Integer] -> Integer
parFoldSum []  = 0
parFoldSum [x] = x
parFoldSum xs  = runEval $ do
  let (l, r) = splitAt (length xs `div` 2) xs
  a <- rpar (force (parFoldSum l))
  b <- rpar (force (parFoldSum r))
  _ <- rdeepseq a
  _ <- rdeepseq b
  return (a + b)

------------------------------------------------------------
-- 5. main：跑三组对比
------------------------------------------------------------

main :: IO ()
main = do
  putStrLn "=== Parallel Strategies Demo ==="
  putStrLn "提示：cabal block 已设置 -threaded -with-rtsopts=-N，自动使用所有核"
  putStrLn ""

  -- ---- 组 1：Fibonacci 并行 ----
  let n = 38   -- fib 38 大约需要 1~2 秒（具体看机器）
  putStrLn ("[组 1] fib " ++ show n ++ "（指数复杂度的 naive 版）")
  rseq <- timeIt "seq        " (return (fibSeq n))
  rpar1 <- timeIt "par (cutoff=20)" (return (fibPar 20 n))
  rpar2 <- timeIt "par (cutoff=15)" (return (fibPar 15 n))
  putStrLn $ "  结果一致性：" ++ show (rseq == rpar1 && rpar1 == rpar2)
  putStrLn ""

  -- ---- 组 2：质数计数（parList vs parListChunk）----
  let primesUpper = 200000
  putStrLn ("[组 2] count primes in [2.." ++ show primesUpper ++ "]")
  cseq <- timeIt "countPrimes seq " (return (countPrimesSeq 2 primesUpper))
  cpar <- timeIt "parList         " (return (countPrimesParList 2 primesUpper))
  cchk <- timeIt "parListChunk 500" (return (countPrimesChunk 500 2 primesUpper))
  putStrLn $ "  结果（应当全部相等）：seq=" ++ show cseq
             ++ "  par=" ++ show cpar
             ++ "  chunk=" ++ show cchk
  putStrLn ""

  -- ---- 组 3：并行 map-reduce ----
  let inputs = [0..127] :: [Int]
  putStrLn "[组 3] map-reduce: sum (expensive xs)，xs = [0..127]"
  msq <- timeIt "mapReduce seq   " (return (mapReduceSeq inputs))
  mpr <- timeIt "mapReduce par/16" (return (mapReducePar 16 inputs))
  let mid = map expensive inputs
      mfd = parFoldSum mid
  mfd' <- timeIt "parFoldSum (Eval)" (return mfd)
  putStrLn $ "  结果一致性：" ++ show (msq == mpr && mpr == mfd')
  putStrLn ""

  -- ---- 速度对比小结 ----
  -- 这里我们不强行 assert "并行一定快"——单核机器 / -N1 时不会更快
  -- 但 +RTS -N4 在 4 核机器上典型可见 2-3x 加速
  putStrLn "[小结]"
  putStrLn "  - par/pseq：原始低层接口，需要手动放置火花点"
  putStrLn "  - Strategies（using parListChunk rdeepseq）：工业首选，业务/策略分离"
  putStrLn "  - Eval monad（runEval/rpar/rdeepseq）：写显式分治时更自然"
  putStrLn ""
  putStrLn "  没看到加速？检查你是否带了 +RTS -N（或 -N4 等）。"
  putStrLn "=== Done ==="
