{-# LANGUAGE NumericUnderscores #-}

-- ============================================================
-- Demo 34: async 库的 concurrently / race / mapConcurrently
--
-- 06 号用的是裸 forkIO + MVar 自己手搓同步；生产代码几乎没人这么写。
-- Haskell 社区的事实标准是 `async` 库（随 GHC 分发）：
--
--   concurrently     两个 IO 同时跑，都成才成，任一抛异常则取消另一个
--   race             两个 IO 竞速，谁先完谁赢，输家会被自动取消
--   mapConcurrently  [IO a] -> IO [a]，并发执行列表里每个操作
--   withAsync        作用域化并发：离开 scope 自动取消，杜绝线程泄漏
--
-- 对比：
--   * Scala cats-effect 有对应 (both / race / parTraverse / Resource)，
--     但语法要繁琐些
--   * Elixir Task.async_stream 很像 mapConcurrently，但没有类型级异常保证
--   * Go goroutine 泄漏极易发生，这里 withAsync 在类型层强制不泄漏
--
-- 跑法：
--   runghc 34_AsyncConcurrentlyRace.hs
-- ============================================================

module Main where

import Control.Concurrent        (threadDelay)
import Control.Concurrent.Async
import Control.Exception         (SomeException, try)
import Data.Time.Clock           (diffUTCTime, getCurrentTime)

-- ============================================================
-- 工具：模拟一个需要 n 毫秒的 "下游服务调用"
-- ============================================================

fetch :: String -> Int -> IO String
fetch name ms = do
  threadDelay (ms * 1_000)
  pure $ name <> "(" <> show ms <> "ms)"

-- 会抛异常的版本，用于演示 concurrently 的异常传播
fetchFail :: String -> Int -> IO String
fetchFail name ms = do
  threadDelay (ms * 1_000)
  error $ "boom from " <> name

-- 简单计时器
timed :: String -> IO a -> IO a
timed tag io = do
  t0 <- getCurrentTime
  r  <- io
  t1 <- getCurrentTime
  let ms = round ((realToFrac (diffUTCTime t1 t0) :: Double) * 1000) :: Int
  putStrLn $ "  [" <> tag <> "] took " <> show ms <> "ms"
  pure r

-- ============================================================
-- Demo 1: concurrently —— 两个都等
-- ============================================================
--
-- 总耗时 ≈ max(300, 500) ≈ 500ms，而不是 800ms。
-- 如果 fetch 里任何一个抛异常，另一个会被立刻取消。

demoConcurrently :: IO ()
demoConcurrently = do
  putStrLn "\n--- (1) concurrently: run 2 IOs, wait for both ---"
  (a, b) <- timed "concurrently" $
    concurrently (fetch "user"  300)
                 (fetch "orders" 500)
  putStrLn $ "  result = (" <> a <> ", " <> b <> ")"

-- ============================================================
-- Demo 2: race —— 谁先完谁赢
-- ============================================================
--
-- 典型用法：主查询 + 超时。
-- 这里我们把一个 200ms 的成功 query 和一个 1000ms 的 "timeout" 对跑。

demoRace :: IO ()
demoRace = do
  putStrLn "\n--- (2) race: first finisher wins, loser gets cancelled ---"
  result <- timed "race" $
    race (fetch "fast"   200)
         (fetch "slow"  1000)
  case result of
    Left  winner -> putStrLn $ "  left won:  " <> winner
    Right winner -> putStrLn $ "  right won: " <> winner

-- ============================================================
-- Demo 3: mapConcurrently —— 并发 traverse
-- ============================================================
--
-- 扇出 5 个"下游"调用，总耗时 ≈ max(latencies) 而不是 sum。

demoMapConcurrently :: IO ()
demoMapConcurrently = do
  putStrLn "\n--- (3) mapConcurrently: fan-out 5 calls in parallel ---"
  let calls = [("s1", 200), ("s2", 350), ("s3", 150), ("s4", 400), ("s5", 250)]
  results <- timed "mapConcurrently" $
    mapConcurrently (uncurry fetch) calls
  mapM_ (\r -> putStrLn $ "  -> " <> r) results

-- ============================================================
-- Demo 4: 异常传播 —— concurrently 一方挂，另一方自动取消
-- ============================================================

demoExceptionPropagation :: IO ()
demoExceptionPropagation = do
  putStrLn "\n--- (4) exception in one branch cancels the other ---"
  r <- try $ concurrently (fetch      "good" 500)
                          (fetchFail  "bad"  100) :: IO (Either SomeException (String, String))
  case r of
    Left e  -> putStrLn $ "  caught: " <> show e
    Right _ -> putStrLn   "  (unexpected: should have thrown)"

-- ============================================================
-- Demo 5: withAsync —— 作用域化，绝不泄漏线程
-- ============================================================
--
-- 离开 withAsync 的作用域时，子任务如果还没结束会被自动取消。
-- 这条保证是在类型层给的：你不可能 "忘记调用 cancel"。

demoWithAsync :: IO ()
demoWithAsync = do
  putStrLn "\n--- (5) withAsync: auto-cancel on scope exit ---"
  withAsync (fetch "bg-job" 5_000) $ \bg -> do
    putStrLn "  main: doing something quick (100ms)..."
    threadDelay 100_000
    putStrLn "  main: done, bg-job will be cancelled automatically"
    _ <- poll bg   -- 非阻塞地看一眼状态，不等待
    pure ()

-- ============================================================
-- Main
-- ============================================================

main :: IO ()
main = do
  putStrLn "=========================================="
  putStrLn "Demo 34: async — concurrently / race / mapConcurrently"
  putStrLn "=========================================="
  demoConcurrently
  demoRace
  demoMapConcurrently
  demoExceptionPropagation
  demoWithAsync
  putStrLn "\n=========================================="
