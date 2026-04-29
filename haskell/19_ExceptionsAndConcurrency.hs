-- ============================================================
-- Demo 19: 异常、资源与并发 —— 真实工程中的 effect 管理
--
-- 这一节对标 Scala Cats Effect 中的 Resource / IO.bracket / Fiber。
-- Haskell 用 base 里的 Control.Exception + Control.Concurrent 就足以覆盖：
--
--   * try / catch / throwIO    显式异常
--   * bracket / finally / onException   资源安全获取与释放（= Resource）
--   * forkIO / MVar / Chan      轻量线程 + 进程间通信
--   * 用 MVar 实现 "互斥锁" 与 "一次性信号"
--
-- 说明：本 Demo 故意不依赖 async 库（避免额外安装），
-- 用 forkIO + MVar 手写一个最小的 "并发 + 结果同步" 模型。
-- 看过之后你就能立刻理解 async / Deferred / Fiber 的核心思想。
--
-- 运行：runhaskell 19_ExceptionsAndConcurrency.hs
-- ============================================================

module Main where

import Control.Concurrent       (forkIO, threadDelay, myThreadId)
import Control.Concurrent.MVar
import Control.Exception        ( Exception, SomeException, try, catch
                                , throwIO, bracket, bracket_, finally
                                , onException, evaluate)
import Control.Monad            (forM_, replicateM_, void)
import Data.IORef
import System.IO                (hFlush, stdout)

-- ============================================================
-- Part 1: 显式异常 —— 把"可能失败"变成值还是抛出？
-- ============================================================
--
-- Haskell 里有两种错误风格：
--   1) 纯函数失败 -> 用 Either / Maybe / ExceptT（见 Demo 02 / 08）
--   2) IO 世界里不可避免的外部异常 -> 用 Control.Exception
-- 工程建议：业务逻辑用 Either；真正的"外部意外"用 Exception。

data BusinessError
  = UserNotFound   String
  | PermissionDenied
  deriving Show

instance Exception BusinessError

lookupUser :: String -> IO String
lookupUser "alice" = pure "Alice / admin"
lookupUser "bob"   = pure "Bob / user"
lookupUser name    = throwIO (UserNotFound name)

safeLookup :: String -> IO (Either BusinessError String)
safeLookup n = try (lookupUser n)

-- ============================================================
-- Part 2: bracket —— Haskell 的 Resource
-- ============================================================
--
-- bracket :: IO a            -- acquire
--         -> (a -> IO b)     -- release
--         -> (a -> IO c)     -- use
--         -> IO c
--
-- 保证：即使 "use" 里抛异常，release 一定被执行。
-- 这就是 Scala Cats Effect Resource 的本质。

data Conn = Conn { connId :: Int }

openConn :: Int -> IO Conn
openConn i = do
  putStrLn $ "  [open]  Conn#" ++ show i
  pure (Conn i)

closeConn :: Conn -> IO ()
closeConn c = putStrLn $ "  [close] Conn#" ++ show (connId c)

-- 正常使用
useConnOk :: IO ()
useConnOk = bracket (openConn 1) closeConn $ \c -> do
  putStrLn $ "  [use]   Conn#" ++ show (connId c) ++ " doing work..."
  pure ()

-- 使用中抛异常 —— release 仍然必须执行
useConnFail :: IO ()
useConnFail =
  bracket (openConn 2) closeConn (\c -> do
    putStrLn $ "  [use]   Conn#" ++ show (connId c) ++ " will throw!"
    _ <- throwIO (UserNotFound "forced-exception")
    pure ())
  `catch` \(e :: BusinessError) ->
    putStrLn $ "  [caught outside bracket] " ++ show e

-- ============================================================
-- Part 3: MVar —— 互斥锁、阻塞队列、一次性信号
-- ============================================================
--
-- MVar a 像一个"容量为 1 的盒子"：
--   * putMVar   : 往盒子里放东西（如果已经有 -> 阻塞）
--   * takeMVar  : 从盒子里取东西（如果空 -> 阻塞）
-- 仅用这两个原语就能实现"锁 / 信号 / 通道"。

-- 用 MVar 做互斥锁：保护共享 IORef
counterDemo :: IO ()
counterDemo = do
  lock    <- newMVar ()         -- 内容=()，拿出 () 的人就是"拿到锁"
  counter <- newIORef (0 :: Int)
  let bump name i =
        bracket_ (takeMVar lock) (putMVar lock ()) $ do
          v <- readIORef counter
          threadDelay 1000      -- 故意延迟，制造竞态的机会
          writeIORef counter (v + 1)
          putStrLn $ "  [" ++ name ++ "] bump #" ++ show i
  done <- newMVar ()
  takeMVar done                 -- 上锁
  forkIO $ do
    forM_ [1 .. 5 :: Int] (bump "T1")
    putMVar done ()
  forM_ [1 .. 5 :: Int] (bump "T0")
  takeMVar done                 -- 等另一条线程完成（再次尝试拿 done）
  final <- readIORef counter
  putStrLn $ "  最终 counter = " ++ show final

-- 用 MVar 做"一次性信号"（= Cats Effect Deferred）
onceSignalDemo :: IO ()
onceSignalDemo = do
  result <- newEmptyMVar          -- 还没有结果
  forkIO $ do
    threadDelay 200_000          -- 假装算了 0.2 秒
    putMVar result (42 :: Int)
  putStrLn "  等待异步结果..."
  v <- takeMVar result           -- 阻塞直到 worker 放入
  putStrLn $ "  拿到 = " ++ show v

-- ============================================================
-- Part 4: 手写 async —— 返回 "将来可以拿到结果的句柄"
-- ============================================================
--
-- 工业界用 async 库，但它的核心 60 行不到。
-- 这里用 MVar 手写最小版本，让你看清 Fiber / Deferred 的本质。

data Async a = Async { waitAsync :: IO a }

async :: IO a -> IO (Async a)
async io = do
  slot <- newEmptyMVar
  void $ forkIO $ do
    r <- try io :: IO (Either SomeException a)
    putMVar slot r
  pure $ Async $ do
    r <- takeMVar slot
    case r of
      Right a -> pure a
      Left  e -> throwIO e

-- 一起跑两个 IO，等两边都完成后返回结果
both :: IO a -> IO b -> IO (a, b)
both ioa iob = do
  ha <- async ioa
  hb <- async iob
  a  <- waitAsync ha
  b  <- waitAsync hb
  pure (a, b)

asyncDemo :: IO ()
asyncDemo = do
  let slow label n = do
        threadDelay (n * 1000)   -- 毫秒级
        tid <- myThreadId
        putStrLn $ "  [" ++ show tid ++ "] " ++ label ++ " done"
        pure (label ++ "-result")
  (a, b) <- both (slow "A" 300) (slow "B" 150)
  putStrLn $ "  合并结果 = " ++ show (a, b)

-- ============================================================
-- Part 5: finally / onException —— 精细控制清理时机
-- ============================================================
--
-- finally     action cleanup   : 无论 action 正常/异常，都跑 cleanup
-- onException action cleanup   : 只在 action 异常时跑 cleanup
-- bracket 其实可以用这两个 + mask 组合出来。

finallyDemo :: IO ()
finallyDemo = do
  (do putStrLn "  step A"
      _ <- evaluate (div 10 0)   -- 触发 ArithException
      putStrLn "  step B")
    `catch` (\(e :: SomeException) ->
               putStrLn $ "  [caught] " ++ show e)
    `finally` putStrLn "  [finally] cleanup always runs"

-- ============================================================
-- Main
-- ============================================================

main :: IO ()
main = do
  putStrLn "=========================================="
  putStrLn "Demo 19: 异常 / 资源 / 并发"
  putStrLn "==========================================\n"

  putStrLn "=== 1. try / throwIO ==="
  r1 <- safeLookup "alice"
  r2 <- safeLookup "unknown"
  putStrLn $ "  safeLookup alice   = " ++ show r1
  putStrLn $ "  safeLookup unknown = " ++ show r2
  putStrLn ""

  putStrLn "=== 2. bracket：资源一定会释放 ==="
  useConnOk
  useConnFail
  putStrLn ""

  putStrLn "=== 3. MVar 互斥锁 ==="
  counterDemo
  putStrLn ""

  putStrLn "=== 4. MVar 一次性信号（= Deferred）==="
  onceSignalDemo
  putStrLn ""

  putStrLn "=== 5. 手写最小 async：并行两个 IO ==="
  asyncDemo
  putStrLn ""

  putStrLn "=== 6. finally：无论是否异常都清理 ==="
  finallyDemo
  hFlush stdout
  putStrLn ""

  putStrLn "=== 知识点小结 ==="
  putStrLn "  * 异常分两类：业务失败用 Either；外部意外用 Exception"
  putStrLn "  * bracket = Resource：acquire / use / release 三段式，永远安全"
  putStrLn "  * MVar 本质 = 容量 1 的盒子，可做锁 / 信号 / 通道"
  putStrLn "  * forkIO + MVar 可手写 async / Fiber"
  putStrLn "  * 工程里直接用 async / safe-exceptions / resourcet 即可"
  putStrLn "=========================================="
