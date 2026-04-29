{-
  Haskell 函数式编程 Demo 6: 并发与 STM 软件事务内存

  Haskell 的并发模型：
  - 轻量绿色线程 (forkIO)：运行时管理，成本极低
  - MVar：互斥变量，最基础的同步原语
  - STM (Software Transactional Memory)：事务内存，组合性强
    - TVar：事务变量
    - STM 动作可以组合，失败时自动重试，没有死锁

  关键直觉：STM 让并发变成"原子代码块"，像数据库事务一样
-}

module Main where

import Control.Concurrent
import Control.Concurrent.STM
import Control.Concurrent.MVar
import Control.Monad (forM_, replicateM_, when, forM)
import Data.List (sort)

-- ========== MVar: 互斥变量 ==========

-- MVar 是可以为空的盒子，写入/读取都是原子操作

-- 生产者-消费者 (MVar 作管道)
producerConsumer :: IO ()
producerConsumer = do
  box <- newEmptyMVar :: IO (MVar Int)
  results <- newMVar ([] :: [String])

  -- 生产者线程
  producer <- forkIO $ forM_ [1..5 :: Int] $ \n -> do
    threadDelay 10000  -- 10ms
    putMVar box n

  -- 消费者：同步读取5个值
  forM_ [1..5 :: Int] $ \_ -> do
    val <- takeMVar box
    modifyMVar_ results $ \rs ->
      return (rs ++ ["消费: " ++ show val])

  threadDelay 50000  -- 等生产者完成
  rs <- readMVar results
  mapM_ putStrLn rs

-- ========== STM: 软件事务内存 ==========

-- TVar 是事务变量，只能在 STM 块里读写
-- atomically :: STM a -> IO a  -- 原子执行 STM 动作

-- --- 例1：安全银行转账 ---

data Account = Account
  { accName    :: String
  , accBalance :: TVar Int
  }

newAccount :: String -> Int -> IO Account
newAccount name balance = Account name <$> newTVarIO balance

-- STM 转账：要么都成功，要么都不执行
transfer :: Account -> Account -> Int -> STM (Either String ())
transfer from to amount = do
  fromBal <- readTVar (accBalance from)
  if fromBal < amount
    then return (Left $ "余额不足: 需要 " ++ show amount ++ ", 实有 " ++ show fromBal)
    else do
      modifyTVar (accBalance from) (subtract amount)
      modifyTVar (accBalance to)   (+amount)
      return (Right ())

printBalances :: [Account] -> IO ()
printBalances accounts = do
  bals <- mapM (\acc -> do
    bal <- readTVarIO (accBalance acc)
    return $ accName acc ++ ": ¥" ++ show bal
    ) accounts
  mapM_ putStrLn bals

-- --- 例2：STM 的 retry (阻塞等待条件) ---

-- retry 让 STM 事务暂停，等到相关 TVar 变化后重新尝试
-- 这是 STM 最强大的特性：声明式等待，无需手写条件变量

-- 只有账户有足够余额时才转账（不够就等）
transferWhenReady :: Account -> Account -> Int -> STM ()
transferWhenReady from to amount = do
  bal <- readTVar (accBalance from)
  when (bal < amount) retry  -- 余额不够就等
  modifyTVar (accBalance from) (subtract amount)
  modifyTVar (accBalance to)   (+amount)

-- --- 例3：TQueue (STM 版本的并发队列) ---

producerConsumerSTM :: IO ()
producerConsumerSTM = do
  queue   <- newTQueueIO :: IO (TQueue Int)
  results <- newTVarIO ([] :: [Int])
  done    <- newTVarIO False

  -- 生产者
  _ <- forkIO $ do
    forM_ [1..10 :: Int] $ \n -> do
      atomically $ writeTQueue queue n
      threadDelay 5000
    atomically $ writeTVar done True

  -- 消费者
  let consume = do
        finished <- atomically $ do
          d <- readTVar done
          empty <- isEmptyTQueue queue
          return (d && empty)
        unless finished $ do
          mn <- atomically $ do
            empty <- isEmptyTQueue queue
            if empty
              then return Nothing
              else Just <$> readTQueue queue
          case mn of
            Nothing -> threadDelay 5000 >> consume
            Just n  -> do
              atomically $ modifyTVar results (++ [n])
              consume

  consume
  rs <- readTVarIO results
  putStrLn $ "STM Queue 消费结果: " ++ show rs

  where
    unless cond action = if cond then return () else action

-- --- 例4：并发计数器（展示 STM 的原子性）---

concurrentCounter :: IO ()
concurrentCounter = do
  counter <- newTVarIO (0 :: Int)
  done    <- newMVar (0 :: Int)

  let numThreads = 10
      numIncrements = 100

  -- 启动多个线程同时自增
  forM_ [1..numThreads] $ \_ ->
    forkIO $ do
      replicateM_ numIncrements $
        atomically $ modifyTVar counter (+1)
      modifyMVar_ done (return . (+1))

  -- 等待所有线程完成
  let waitAll = do
        n <- readMVar done
        when (n < numThreads) $ threadDelay 10000 >> waitAll
  waitAll

  final <- readTVarIO counter
  putStrLn $ "并发计数器最终值: " ++ show final
  putStrLn $ "期望值: " ++ show (numThreads * numIncrements)
  putStrLn $ "结果正确: " ++ show (final == numThreads * numIncrements)

-- ========== Chan: 通道（类似 Go channel）==========

chanDemo :: IO ()
chanDemo = do
  ch <- newChan :: IO (Chan String)

  -- 写入者
  _ <- forkIO $ forM_ ["Hello", "Haskell", "Concurrent", "World"] $ \msg -> do
    threadDelay 10000
    writeChan ch msg

  -- 读取4条消息
  msgs <- forM [1..4 :: Int] $ \_ -> readChan ch
  putStrLn $ "Chan 收到: " ++ show msgs

-- ========== 主函数 ==========

main :: IO ()
main = do
  putStrLn "=== MVar: 生产者-消费者 ==="
  producerConsumer

  putStrLn "\n=== STM: 银行转账 ==="
  alice <- newAccount "Alice" 1000
  bob   <- newAccount "Bob"   500
  carol <- newAccount "Carol" 200

  putStrLn "初始余额:"
  printBalances [alice, bob, carol]

  r1 <- atomically $ transfer alice bob 300
  putStrLn $ "\nAlice → Bob ¥300: " ++ show r1
  printBalances [alice, bob, carol]

  r2 <- atomically $ transfer carol alice 500  -- 余额不足
  putStrLn $ "\nCarol → Alice ¥500: " ++ show r2
  printBalances [alice, bob, carol]

  r3 <- atomically $ transfer bob carol 100
  putStrLn $ "\nBob → Carol ¥100: " ++ show r3
  printBalances [alice, bob, carol]

  let total acc = readTVarIO (accBalance acc)
  ts <- mapM total [alice, bob, carol]
  putStrLn $ "\n总资产守恒检查: " ++ show (sum ts) ++ " (初始: 1700)"

  putStrLn "\n=== STM TQueue: 并发队列 ==="
  producerConsumerSTM

  putStrLn "\n=== STM 并发计数器 (10线程 × 100次) ==="
  concurrentCounter

  putStrLn "\n=== Chan: 消息通道 ==="
  chanDemo
