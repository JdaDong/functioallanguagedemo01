{-# LANGUAGE NumericUnderscores #-}

-- ============================================================
-- Demo 33: STM 细粒度转账 + retry / orElse
--
-- 06 号已经初步介绍过 STM，本 Demo 聚焦 STM 最能打的两个语义：
--
--   1. retry   —— 余额不够就阻塞，直到有 TVar 发生变化再醒来重试
--   2. orElse  —— "先试 A，A 挂起就试 B"，把两个事务组合成一个
--
-- 这两个原语在 Scala Cats-STM / Elixir Agent / Go channel 里都没有
-- 原生对等物：你拿 Mutex/Channel 手搓出来要写很多代码且很容易出 bug。
--
-- 场景：
--   * 三个账户 A / B / C，初始余额 1000 / 500 / 0
--   * worker1: 从 A 向 C 转 600（够，立刻成）
--   * worker2: 从 B 向 C 转 800（不够 → retry 阻塞）
--   * worker3: 1 秒后给 B 存 500（唤醒 worker2，它重试后成功）
--   * worker4: 用 orElse 演示"优先 A 付款，A 不够回退 B 付款"
--
-- 跑法：
--   runghc 33_StmBankTransferRetry.hs
-- ============================================================

module Main where

import Control.Concurrent        (forkIO, threadDelay)
import Control.Concurrent.MVar   (newEmptyMVar, putMVar, takeMVar)
import Control.Concurrent.STM
import Control.Monad             (forM_)

-- ============================================================
-- Part 1: 账户 & 基本操作
-- ============================================================

type Account = TVar Int

newAccount :: Int -> IO Account
newAccount = newTVarIO

-- 不足就 retry：整个事务会阻塞，直到 TVar 被别的线程改写后再醒来。
-- 关键点：你不需要手写"监听/通知"，STM 运行时基于 TVar 读集自动做。
withdraw :: Account -> Int -> STM ()
withdraw acc amt = do
  bal <- readTVar acc
  if bal < amt
    then retry                               -- 阻塞等待
    else writeTVar acc (bal - amt)

deposit :: Account -> Int -> STM ()
deposit acc amt = modifyTVar' acc (+ amt)

-- 原子转账：withdraw + deposit 放同一个 atomically 里，要么都成要么都退。
transfer :: Account -> Account -> Int -> STM ()
transfer from to amt = do
  withdraw from amt
  deposit  to   amt

-- ============================================================
-- Part 2: orElse 组合
-- ============================================================
--
-- transferFromEither a b to amt：
--   先试从 a 付款；如果 a 余额不够 (retry)，自动回退试 b。
--   注意这不是 "a 失败了再跑 b"，而是 "a 挂起了就当作这次选 b"。

transferFromEither :: Account -> Account -> Account -> Int -> STM String
transferFromEither a b to amt =
  (transfer a to amt >> pure "paid by A")
  `orElse`
  (transfer b to amt >> pure "paid by B")

-- ============================================================
-- Part 3: 观察器
-- ============================================================

snapshot :: [(String, Account)] -> STM [(String, Int)]
snapshot accs = mapM (\(n, a) -> (,) n <$> readTVar a) accs

printSnapshot :: String -> [(String, Account)] -> IO ()
printSnapshot tag accs = do
  xs <- atomically (snapshot accs)
  putStrLn $ tag <> " " <> show xs

-- ============================================================
-- Main
-- ============================================================

main :: IO ()
main = do
  putStrLn "=========================================="
  putStrLn "Demo 33: STM Transfer with retry / orElse"
  putStrLn "=========================================="

  a <- newAccount 1000
  b <- newAccount 500
  c <- newAccount 0
  let accs = [("A", a), ("B", b), ("C", c)]

  printSnapshot "[t0] init   :" accs

  -- Worker 1: A -> C 600，立刻成
  done1 <- newEmptyMVar
  _ <- forkIO $ do
    atomically (transfer a c 600)
    putStrLn "[w1] A --600--> C  OK"
    putMVar done1 ()

  -- Worker 2: B -> C 800，B 只剩 500，retry 阻塞
  done2 <- newEmptyMVar
  _ <- forkIO $ do
    putStrLn "[w2] trying B --800--> C (should block on retry)"
    atomically (transfer b c 800)
    putStrLn "[w2] B --800--> C  OK (woken up by w3's deposit)"
    putMVar done2 ()

  threadDelay 300_000  -- 让 w1 先稳稳完成
  printSnapshot "[t1] after w1:" accs

  -- Worker 3: 1 秒后给 B 存 500，预期唤醒 w2
  _ <- forkIO $ do
    threadDelay 1_000_000
    putStrLn "[w3] depositing 500 into B (will unblock w2)"
    atomically (deposit b 500)

  takeMVar done2
  printSnapshot "[t2] after w2:" accs

  -- Worker 4: orElse 场景 —— 试从 A(已空) 付 200，A 不够时自动走 B
  putStrLn ""
  putStrLn "--- orElse demo ---"
  forM_ ([1, 2, 3] :: [Int]) $ \i -> do
    tag <- atomically (transferFromEither a b c 200)
    putStrLn $ "[orElse] round " <> show i <> ": " <> tag

  takeMVar done1
  printSnapshot "[t3] final  :" accs
  putStrLn "=========================================="
