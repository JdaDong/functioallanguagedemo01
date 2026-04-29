{-# LANGUAGE GeneralizedNewtypeDeriving #-}

-- ============================================================
-- Demo 08: Monad Transformers — 深度实践
--
-- 核心概念：
--   1. 为什么需要 Transformer：Monad 不支持"叠加"
--   2. MaybeT — 最常用的错误处理 Transformer
--   3. ExceptT — 带错误类型的异常处理
--   4. StateT — 可变状态封装
--   5. ReaderT + State 组合（经典 Web 应用模式）
--   6. 自定义 Transformer
-- ============================================================

module Main where

import Control.Monad.Trans.Maybe    (MaybeT(..), runMaybeT)
import Control.Monad.Trans.Except   (ExceptT(..), runExceptT, throwE)
import Control.Monad.Trans.State    (State, runState, get, put, modify)
import Control.Monad.Trans.Reader   (Reader, runReader, ask, local)
import Control.Monad.IO.Class       (liftIO)
import Control.Monad                (when, forM_)

import Data.Functor.Identity        (Identity(runIdentity))

-- ============================================================
-- Part 1: 基础回顾 — 为什么需要 Transformer
-- ============================================================

-- 问题：Maybe 和 [] 不能直接组合成 "可能为空的列表的容器"
-- 解决方案：Transformer 把一个 Monad "包装"进另一个

-- ============================================================
-- Part 2: MaybeT — 最常用的错误处理 Transformer
-- ============================================================

data User = User
  { userName    :: String
  , userAge     :: Int
  , userEmail   :: String
  , userBalance :: Double
  } deriving Show

userDB :: [(Int, User)]
userDB =
  [ (1, User "Alice"   30 "alice@example.com"  1000.0)
  , (2, User "Bob"     25 "bob@example.com"     500.5)
  , (3, User "Charlie" 35 "charlie@example.com" 2500.0)
  ]

lookupUser :: Int -> Maybe User
lookupUser uid = lookup uid userDB

-- MaybeT 版本 — 扁平链式处理
findUserEmail :: Int -> MaybeT IO String
findUserEmail uid = do
  user <- MaybeT $ pure $ lookupUser uid
  liftIO $ putStrLn $ "  [MaybeT] 找到用户: " ++ userName user
  pure (userEmail user)

-- ============================================================
-- Part 3: ExceptT — 带错误类型的异常处理
-- ============================================================

data AppError
  = UserNotFound Int
  | InsufficientFunds Double Double
  | InvalidAmount String
  deriving (Show, Eq)

transferMoney :: Int -> Int -> Double -> ExceptT AppError IO String
transferMoney fromId toId amount
  | amount <= 0 = throwE (InvalidAmount "金额必须大于零")
  | otherwise = do
      -- 步骤1: 查找转出用户
      fromUser <- case lookupUser fromId of
        Nothing -> throwE (UserNotFound fromId)
        Just u  -> pure u
      
      -- 步骤2: 查找转入用户
      toUser <- case lookupUser toId of
        Nothing -> throwE (UserNotFound toId)
        Just u  -> pure u
      
      -- 步骤3: 检查余额
      when (amount > userBalance fromUser) $
        throwE (InsufficientFunds (userBalance fromUser) amount)
      
      let newFromBal = userBalance fromUser - amount
          newToBal   = userBalance toUser + amount
      
      pure $ concat
        [ userName fromUser, " 转给 ", userName toUser
        , " ¥", show amount, ", 余额: ¥", show newFromBal
        ]

-- ============================================================
-- Part 4: State Monad — 栈操作计算器
-- ============================================================

type Stack a = [a]

push :: a -> State (Stack a) ()
push x = modify (x:)

pop :: State (Stack a) (Maybe a)
pop = do
  s <- get
  case s of
    []     -> pure Nothing
    (x:xs) -> put xs >> pure (Just x)

peek :: State (Stack a) (Maybe a)
peek = do
  s <- get
  case s of
    []     -> pure Nothing
    (x:_)  -> pure (Just x)

-- 带日志的计算器（用 State 存储结果和日志）
data CalcState = CalcState
  { calcValue :: Double
  , calcLog   :: [String]
  } deriving Show

runCalc :: State CalcState a -> (a, CalcState)
runCalc m = runState m (CalcState 0 [])

applyOp :: String -> (Double -> Double) -> State CalcState ()
applyOp opName f = do
  st <- get
  let newVal = f (calcValue st)
  put st { calcValue = newVal, calcLog = calcLog st ++ [opName ++ " => " ++ show newVal] }

-- ============================================================
-- Part 5: Reader Monad — 配置注入
-- ============================================================

data AppConfig = AppConfig
  { dbHost     :: String
  , dbPort     :: Int
  , debugMode  :: Bool
  } deriving Show

type AppM = Reader AppConfig

connectionInfo :: AppM String
connectionInfo = do
  cfg <- ask
  pure $ dbHost cfg ++ ":" ++ show (dbPort cfg)

isDebugMode :: AppM Bool
isDebugMode = debugMode <$> ask

-- local: 局部覆盖环境，不影响外部
withDebug :: AppM a -> AppM a
withDebug = local (\cfg -> cfg { debugMode = True })

-- ============================================================
-- Part 6: Reader + State 组合（简化版）
-- ============================================================

data SessionState = SessionState
  { sessionCount :: Int
  , sessionLog   :: [String]
  } deriving Show

type SessionM = Reader String  -- 用户ID作为环境

runSession :: String -> SessionM a -> a
runSession userId m = runReader m userId

sessionAction :: SessionM (Int, [String])
sessionAction = do
  userId <- ask
  let msg1 = "用户 " ++ userId ++ ": 开始会话"
      msg2 = "用户 " ++ userId ++ ": 处理请求"
      msg3 = "用户 " ++ userId ++ ": 结束会话"
  pure (3, [msg1, msg2, msg3])

-- ============================================================
-- Part 7: 自定义 CountingT Transformer
-- ============================================================

newtype CountingT m a = CountingT { runCountingT :: m (Int, Maybe a) }
  deriving (Functor)

instance Monad m => Applicative (CountingT m) where
  pure x = CountingT $ pure (0, Just x)
  CountingT mf <*> CountingT mx = CountingT $ do
    (cntF, mf') <- mf
    (cntX, mx') <- mx
    pure (cntF + cntX, mf' <*> mx')

instance Monad m => Monad (CountingT m) where
  CountingT mx >>= f = CountingT $ do
    (cntX, mx') <- mx
    case mx' of
      Nothing -> pure (cntX + 1, Nothing)  -- 记录一次失败
      Just x  -> do
        (cntF, result) <- runCountingT (f x)
        pure (cntX + cntF, result)

countingDemo :: CountingT Identity String
countingDemo = do
  a <- CountingT $ pure (0, Just "hello")
  b <- CountingT $ pure (0, Just " world")
  c <- CountingT $ pure (0, Nothing :: Maybe Int)  -- 这一步失败
  d <- CountingT $ pure (0, Just "!")
  pure (a ++ b ++ show c ++ d)

-- ============================================================
-- Main
-- ============================================================

main :: IO ()
main = do
  putStrLn "=========================================="
  putStrLn "Demo 08: Monad Transformers 深度实践"
  putStrLn "==========================================\n"

  ---- MaybeT ----
  putStrLn "=== 1. MaybeT: 用户查询 ==="
  r1 <- runMaybeT (findUserEmail 1)
  putStrLn $ "  UID=1 email: " ++ show r1
  r2 <- runMaybeT (findUserEmail 99)
  putStrLn $ "  UID=99 email: " ++ show r2
  putStrLn ""

  ---- ExceptT: 银行转账 ----
  putStrLn "=== 2. ExceptT: 银行转账 ==="
  e1 <- runExceptT (transferMoney 1 2 200.0)
  putStrLn $ "  1->2 $200:  " ++ show e1
  e2 <- runExceptT (transferMoney 1 2 99999.0)
  putStrLn $ "  1->2 $99999: " ++ show e2
  e3 <- runExceptT (transferMoney 99 1 100.0)
  putStrLn $ "  99->1 $100:  " ++ show e3
  e4 <- runExceptT (transferMoney 1 2 (-50.0))
  putStrLn $ "  1->2 $-50:  " ++ show e4
  putStrLn ""

  ---- State: 栈操作 ----
  putStrLn "=== 3. State Monad: 栈操作 ==="
  let (rPeek, _) = runState (peek) ([10, 20, 30] :: Stack Int)
  putStrLn $ "  peek [10,20,30]: " ++ show rPeek
  
  let (rPop1, s1) = runState pop ([10, 20, 30] :: Stack Int)
  let (rPop2, s2) = runState pop s1
  putStrLn $ "  pop -> " ++ show rPop1 ++ ", then pop -> " ++ show rPop2
  putStrLn $ "  final stack: " ++ show s2
  putStrLn ""

  ---- State: 计算器带日志 ----
  putStrLn "=== 4. State: 带日志计算器 ==="
  let (_, calcSt) = runCalc $ do
        applyOp "+50.0" (+50.0)
        applyOp "*2.0" (*2.0)
        applyOp "/5.0" (/5.0)
        applyOp "+10.0" (+10.0)
  mapM_ (\l -> putStrLn $ "    " ++ l) (calcLog calcSt)
  putStrLn ""

  ---- Reader: 配置注入 ----
  putStrLn "=== 5. Reader: 配置注入 ==="
  let myConfig = AppConfig { dbHost="localhost", dbPort=5432, debugMode=False }
  putStrLn $ "  DB连接: " ++ runReader connectionInfo myConfig
  putStrLn $ "  debug? " ++ show (runReader isDebugMode myConfig)
  putStrLn $ "  withDebug: " ++ show (runReader (withDebug isDebugMode) myConfig)
  putStrLn ""

  ---- Reader + State 组合 ----
  putStrLn "=== 6. Reader + State: 会话管理 ==="
  let (count, logs) = runSession "user001" sessionAction
  putStrLn $ "  调用次数: " ++ show count
  putStrLn $ "  日志:"
  mapM_ (\l -> putStrLn $ "    " ++ l) logs
  putStrLn ""

  ---- 自定义 CountingT ----
  putStrLn "=== 7. 自定义 CountingT ==="
  let (failures, finalResult) = runIdentity (runCountingT countingDemo)
  putStrLn $ "  失败次数: " ++ show failures
  putStrLn $ "  结果: " ++ show finalResult
  putStrLn ""
  
  putStrLn "=========================================="

