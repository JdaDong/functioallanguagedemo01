{-# LANGUAGE NumericUnderscores #-}

-- ============================================================
-- Demo 35: IORef vs MVar vs TVar —— 同一场景三种写法
--
-- Haskell 有三种最常用的"可变引用"，新手最困惑的就是"到底用哪个"。
-- 本 Demo 用同一个场景（并发计数器 + "读取→修改→写回"）分别写三遍，
-- 把差别摊在一屏里。
--
-- 选型速查：
--   IORef  —— 单线程 / 本来就只 write-once；读写快，无锁，无事务
--   MVar   —— 跨线程互斥、经典 producer/consumer；empty/full 状态
--   TVar   —— 跨线程、需要组合多个引用的原子读写；retry/orElse
--
-- 要点：这里的 IORef 并发测试会演示**数据丢失**（经典 RMW race），
-- 所以不要把 IORef 当线程安全的东西用。
--
-- 跑法：
--   runghc 35_IoRefVsMVarVsTVar.hs
-- ============================================================

module Main where

import Control.Concurrent        (forkIO)
import Control.Concurrent.MVar
import Control.Concurrent.STM
import Control.Monad             (forM_, replicateM_)
import Data.IORef

-- 用 MVar 当"所有 worker 完成"的计数栅栏
waitN :: MVar Int -> Int -> IO ()
waitN done n = do
  k <- takeMVar done
  if k >= n
    then putMVar done k  -- 还回去，保持不变
    else do putMVar done k; waitN done n

markDone :: MVar Int -> IO ()
markDone done = modifyMVar_ done (pure . (+ 1))

-- 并发跑 workers 个线程，每个执行 iters 次 body，最后等全部跑完
spawn :: Int -> Int -> IO () -> IO ()
spawn workers iters body = do
  done <- newMVar 0
  forM_ [1 .. workers] $ \_ ->
    forkIO $ do
      replicateM_ iters body
      markDone done
  waitN done workers

-- ============================================================
-- Version 1: IORef —— 单线程 OK，并发下会丢更新
-- ============================================================
--
-- readIORef + writeIORef 不是原子的。即使用 modifyIORef，
-- 非严格版本还会因 thunks 堆积出其它麻烦。
-- atomicModifyIORef' 是存在的，但那其实就是 TVar 的弱化版，
-- 见下面的"TVar 版本"。

counterIORef :: IO ()
counterIORef = do
  putStrLn "\n--- [1] IORef (NOT thread-safe under RMW) ---"
  ref <- newIORef (0 :: Int)
  spawn 10 1000 $ do
    v <- readIORef ref        -- ← race window 开始
    writeIORef ref (v + 1)    -- ← 另一个线程可能已经读过同样的 v
  final <- readIORef ref
  putStrLn $ "  expected 10000, got " <> show final
  putStrLn   "  (多半小于 10000；如果刚好相等也只是调度运气，这组合本就不安全)"

-- ============================================================
-- Version 2: MVar —— 互斥锁语义，正确但串行化
-- ============================================================
--
-- modifyMVar_ 是"取出 + 修改 + 放回"的原子组合。
-- 效果正确，但所有 worker 必须排队，拿不到锁会阻塞。

counterMVar :: IO ()
counterMVar = do
  putStrLn "\n--- [2] MVar (correct, mutually exclusive) ---"
  ref <- newMVar (0 :: Int)
  spawn 10 1000 $ modifyMVar_ ref (pure . (+ 1))
  final <- readMVar ref
  putStrLn $ "  expected 10000, got " <> show final

-- ============================================================
-- Version 3: TVar —— STM 事务，正确且可与其它事务组合
-- ============================================================
--
-- 和 MVar 相比，TVar 的真正优势不在单 counter，而在
-- "多个 TVar 一起原子更新"（见 33 号的转账）和 retry/orElse。
-- 这里只做等价演示。

counterTVar :: IO ()
counterTVar = do
  putStrLn "\n--- [3] TVar (correct, composable transactions) ---"
  ref <- newTVarIO (0 :: Int)
  spawn 10 1000 $ atomically (modifyTVar' ref (+ 1))
  final <- atomically (readTVar ref)
  putStrLn $ "  expected 10000, got " <> show final

-- ============================================================
-- Main
-- ============================================================

main :: IO ()
main = do
  putStrLn "=========================================="
  putStrLn "Demo 35: IORef vs MVar vs TVar"
  putStrLn "=========================================="
  counterIORef
  counterMVar
  counterTVar
  putStrLn "\nTakeaway:"
  putStrLn "  * IORef  = 单线程, 无锁, 最快; 并发 RMW 会丢数据"
  putStrLn "  * MVar   = 互斥锁; 正确但会串行化"
  putStrLn "  * TVar   = STM 事务; 正确 + 可组合 (retry/orElse 见 33 号)"
  putStrLn "=========================================="
