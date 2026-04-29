{-# LANGUAGE FlexibleContexts    #-}
{-# LANGUAGE FlexibleInstances   #-}
{-# LANGUAGE GADTs               #-}
{-# LANGUAGE GeneralizedNewtypeDeriving #-}
{-# LANGUAGE MultiParamTypeClasses #-}
{-# LANGUAGE RankNTypes          #-}
{-# LANGUAGE ScopedTypeVariables #-}

-- ============================================================
-- Demo 21: Effect System —— MTL 风格 + 手写 Free Eff 雏形
--
-- 本 Demo 是"Haskell 做工程"的核心思路汇总：
--
-- Part A. 经典 MTL 风格：
--   * 把"能做什么"抽象成 MonadLogger / MonadDB 这样的 class
--   * 业务逻辑只写 (MonadLogger m, MonadDB m) => m ...
--   * 在不同运行时（真实 IO / 测试 mock）给出不同实例
--
-- Part B. 手写最小 Free Eff：
--   * 业务写成 "指令 + 解释器" 分离
--   * 一段逻辑可以被多种解释器解释（生产 / 测试 / 打印）
--
-- 这两种思路 + 前面的 Demo 11 (FreeMonadsAndDSL)
-- 就是 Haskell 目前三大主流 Effect 方案的入口：mtl / free / polysemy。
--
-- 不依赖任何外部库，只用 base。
-- 运行：runhaskell 21_EffectSystemPatterns.hs
-- ============================================================

module Main where

import           Control.Monad       (when)
import           Data.IORef
import qualified Data.Map.Strict     as Map
import           Data.Map.Strict     (Map)

-- ============================================================
-- Part A: MTL 风格 —— 用类型类抽象能力
-- ============================================================
--
-- 关键直觉：
--   * 业务代码只依赖 "我需要什么能力"（class 约束），
--     不依赖 "这些能力怎么实现"。
--   * 真实 IO 和 测试 Mock 只是同一 class 的不同实例。

-- 能力 1：打日志
class Monad m => MonadLogger m where
  logInfo  :: String -> m ()
  logError :: String -> m ()

-- 能力 2：简易用户存储
class Monad m => MonadUserStore m where
  getUser  :: String -> m (Maybe String)
  putUser  :: String -> String -> m ()

-- 业务：注册一个用户。注意这里"不知道"底层是 IO 还是测试环境。
registerUser
  :: (MonadLogger m, MonadUserStore m)
  => String -> String -> m Bool
registerUser uid name = do
  existing <- getUser uid
  case existing of
    Just _  -> do
      logError $ "user already exists: " ++ uid
      pure False
    Nothing -> do
      putUser uid name
      logInfo $ "registered user: " ++ uid
      pure True

-- -------- 实例 1：真实 IO 环境 --------
-- 用 IORef 假装一个数据库，用 putStrLn 当日志
newtype ProdM a = ProdM { runProdM :: IORef (Map String String) -> IO a }

instance Functor ProdM where
  fmap f (ProdM g) = ProdM (fmap (fmap f) g)

instance Applicative ProdM where
  pure a = ProdM (\_ -> pure a)
  ProdM f <*> ProdM g = ProdM $ \r -> do
    ff <- f r
    gg <- g r
    pure (ff gg)

instance Monad ProdM where
  return = pure
  ProdM m >>= k = ProdM $ \r -> do
    a <- m r
    runProdM (k a) r

instance MonadLogger ProdM where
  logInfo  s = ProdM $ \_ -> putStrLn $ "[INFO ] " ++ s
  logError s = ProdM $ \_ -> putStrLn $ "[ERROR] " ++ s

instance MonadUserStore ProdM where
  getUser uid = ProdM $ \r -> Map.lookup uid <$> readIORef r
  putUser uid name = ProdM $ \r -> modifyIORef' r (Map.insert uid name)

-- -------- 实例 2：纯测试解释器 --------
-- 用 State Monad 风格手写，不依赖 mtl
data TestState = TestState
  { tsUsers :: Map String String
  , tsLogs  :: [String]   -- 反向存储
  } deriving Show

newtype TestM a = TestM { runTestM :: TestState -> (a, TestState) }

instance Functor TestM where
  fmap f (TestM g) = TestM $ \s -> let (a, s') = g s in (f a, s')

instance Applicative TestM where
  pure a = TestM (\s -> (a, s))
  TestM mf <*> TestM ma = TestM $ \s ->
    let (f, s1) = mf s
        (a, s2) = ma s1
    in  (f a, s2)

instance Monad TestM where
  return = pure
  TestM m >>= k = TestM $ \s ->
    let (a, s1) = m s
    in  runTestM (k a) s1

instance MonadLogger TestM where
  logInfo  msg = TestM $ \s -> ((), s { tsLogs = ("INFO:"  ++ msg) : tsLogs s })
  logError msg = TestM $ \s -> ((), s { tsLogs = ("ERROR:" ++ msg) : tsLogs s })

instance MonadUserStore TestM where
  getUser uid = TestM $ \s -> (Map.lookup uid (tsUsers s), s)
  putUser uid name = TestM $ \s ->
    ((), s { tsUsers = Map.insert uid name (tsUsers s) })

-- ============================================================
-- Part B: 手写最小 Free Eff —— "指令 + 解释器"分离
-- ============================================================
--
-- 和 Demo 11 的 Free Monad 思路一致，但这里做成两个"标签"的求和，
-- 展示 Eff 风格常见的"多种能力组合"的思路。

-- 指令集：日志 + KV 存储
data Cmd a where
  CLogInfo  :: String -> Cmd ()
  CLogError :: String -> Cmd ()
  CGet      :: String -> Cmd (Maybe String)
  CPut      :: String -> String -> Cmd ()

-- 程序 = 指令序列
-- 简化的 Free：Pure 或 接一个指令再继续
data Program a
  = Pure a
  | forall x. Bind (Cmd x) (x -> Program a)

instance Functor Program where
  fmap f (Pure a)     = Pure (f a)
  fmap f (Bind c k)   = Bind c (fmap f . k)

instance Applicative Program where
  pure = Pure
  pf <*> pa = do
    f <- pf
    a <- pa
    pure (f a)

instance Monad Program where
  return = pure
  Pure a     >>= k = k a
  Bind c j   >>= k = Bind c (\x -> j x >>= k)

-- 辅助：把一条指令包装成 Program
liftCmd :: Cmd a -> Program a
liftCmd c = Bind c Pure

-- 业务逻辑（纯数据，此时没有任何 effect 被执行）
registerProgram :: String -> String -> Program Bool
registerProgram uid name = do
  m <- liftCmd (CGet uid)
  case m of
    Just _  -> do
      liftCmd (CLogError ("dup user " ++ uid))
      pure False
    Nothing -> do
      liftCmd (CPut uid name)
      liftCmd (CLogInfo ("registered " ++ uid))
      pure True

-- 解释器 1：真实 IO
runIO :: IORef (Map String String) -> Program a -> IO a
runIO _   (Pure a)   = pure a
runIO ref (Bind c k) = case c of
  CLogInfo  s   -> putStrLn ("[IO-INFO ] " ++ s) >> runIO ref (k ())
  CLogError s   -> putStrLn ("[IO-ERROR] " ++ s) >> runIO ref (k ())
  CGet uid      -> do
    m <- readIORef ref
    runIO ref (k (Map.lookup uid m))
  CPut uid nm   -> do
    modifyIORef' ref (Map.insert uid nm)
    runIO ref (k ())

-- 解释器 2：纯测试
runPure :: TestState -> Program a -> (a, TestState)
runPure s (Pure a)   = (a, s)
runPure s (Bind c k) = case c of
  CLogInfo  msg -> runPure (s { tsLogs = ("INFO:"  ++ msg) : tsLogs s }) (k ())
  CLogError msg -> runPure (s { tsLogs = ("ERROR:" ++ msg) : tsLogs s }) (k ())
  CGet uid      -> runPure s (k (Map.lookup uid (tsUsers s)))
  CPut uid nm   -> runPure (s { tsUsers = Map.insert uid nm (tsUsers s) }) (k ())

-- 解释器 3：打印执行轨迹（什么都不真的执行）
runTrace :: Program a -> IO ()
runTrace (Pure _)   = putStrLn "  [trace] done"
runTrace (Bind c k) = case c of
  CLogInfo  s -> putStrLn ("  [trace] log-info  " ++ s) >> runTrace (k ())
  CLogError s -> putStrLn ("  [trace] log-error " ++ s) >> runTrace (k ())
  CGet uid    -> putStrLn ("  [trace] get "       ++ uid) >> runTrace (k Nothing)
  CPut uid nm -> putStrLn ("  [trace] put " ++ uid ++ "=" ++ nm) >> runTrace (k ())

-- ============================================================
-- Main
-- ============================================================

main :: IO ()
main = do
  putStrLn "=========================================="
  putStrLn "Demo 21: Effect System —— MTL + Free Eff"
  putStrLn "==========================================\n"

  -- ---- MTL 风格 ----
  putStrLn "=== 1. MTL 风格：同一段业务，两套实例 ==="
  putStrLn "  (a) 真实 IO 环境："
  ref <- newIORef (Map.empty :: Map String String)
  ok1 <- runProdM (registerUser "alice" "Alice") ref
  ok2 <- runProdM (registerUser "alice" "AliceDup") ref
  putStrLn $ "  result 1 = " ++ show ok1 ++ ", result 2 = " ++ show ok2
  final <- readIORef ref
  putStrLn $ "  存储状态 = " ++ show final
  putStrLn ""

  putStrLn "  (b) 纯测试环境（不做任何 IO）："
  let initial = TestState Map.empty []
      (okA, s1) = runTestM (registerUser "bob"   "Bob")    initial
      (okB, s2) = runTestM (registerUser "bob"   "BobDup") s1
  putStrLn $ "  result A = " ++ show okA ++ ", result B = " ++ show okB
  putStrLn $ "  最终用户表 = " ++ show (tsUsers s2)
  putStrLn $ "  日志（倒序） = " ++ show (tsLogs s2)
  putStrLn ""

  -- ---- Free Eff 风格 ----
  putStrLn "=== 2. Free Eff：先构造 Program，再换解释器 ==="
  let prog = registerProgram "charlie" "Charlie"

  putStrLn "  (a) 真实 IO 解释器："
  ref2 <- newIORef Map.empty
  r1 <- runIO ref2 prog
  r2 <- runIO ref2 prog  -- 第二次跑同一个 program，会命中 dup 分支
  putStrLn $ "  结果1 = " ++ show r1 ++ ", 结果2 = " ++ show r2
  putStrLn ""

  putStrLn "  (b) 纯状态解释器（没有任何 IO）："
  let (rp, sp) = runPure (TestState Map.empty []) prog
  putStrLn $ "  结果 = " ++ show rp
  putStrLn $ "  最终状态 = " ++ show sp
  putStrLn ""

  putStrLn "  (c) Trace 解释器（只打印调用轨迹）："
  runTrace prog
  putStrLn ""

  putStrLn "=== 知识点小结 ==="
  putStrLn "  * MTL 风格：用 class 约束抽象能力，用实例替换实现"
  putStrLn "  * Free 风格：先把程序构造成纯数据，再让解释器决定语义"
  putStrLn "  * 真正工程中常见组合：ReaderT Env IO + MTL class"
  putStrLn "  * polysemy / fused-effects / eff 都是这条思路的工业版"
  putStrLn "  * 对标 Scala：Tagless Final ≈ MTL，Algebra+Interpreter ≈ Free"
  putStrLn "=========================================="
