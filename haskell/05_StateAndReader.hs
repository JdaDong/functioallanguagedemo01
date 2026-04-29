{-
  Haskell 函数式编程 Demo 5: State Monad 与 Reader Monad

  两个极其实用的 Monad：

  State s a:
    - 表示"需要读写一个状态 s，并返回 a"的计算
    - 本质是  s -> (a, s)  的包装
    - 让"有状态的计算"保持纯函数形式

  Reader r a:
    - 表示"需要读取一个环境 r，并返回 a"的计算
    - 本质是  r -> a  的包装
    - Haskell 原生依赖注入：把配置/环境显式传递，但不写进每个参数列表
-}

module Main where

import Control.Monad.State
import Control.Monad.Reader
import Control.Monad (replicateM)
import Data.List (intercalate)

-- ========== State Monad ==========

-- State s a  ≈  s -> (a, s)
-- runState  :: State s a -> s -> (a, s)
-- execState :: State s a -> s -> s
-- evalState :: State s a -> s -> a

-- --- 例1：栈操作 ---

type Stack a = [a]

push :: a -> State (Stack a) ()
push x = modify (x:)

pop :: State (Stack a) (Maybe a)
pop = do
  s <- get
  case s of
    []     -> return Nothing
    (x:xs) -> do
      put xs
      return (Just x)

peek :: State (Stack a) (Maybe a)
peek = gets (\s -> case s of [] -> Nothing; (x:_) -> Just x)

stackProgram :: State (Stack Int) String
stackProgram = do
  push 10
  push 20
  push 30
  top  <- peek
  a    <- pop
  b    <- pop
  push 100
  s    <- get
  return $ "peek=" ++ show top
        ++ ", pop1=" ++ show a
        ++ ", pop2=" ++ show b
        ++ ", final stack=" ++ show s

-- --- 例2：计算器 (累积日志 + 状态) ---

data CalcState = CalcState
  { csValue :: Double
  , csLog   :: [String]
  } deriving (Show)

type Calc a = State CalcState a

calcAdd :: Double -> Calc ()
calcAdd n = modify $ \s ->
  s { csValue = csValue s + n
    , csLog   = csLog s ++ ["+" ++ show n ++ " = " ++ show (csValue s + n)]
    }

calcMul :: Double -> Calc ()
calcMul n = modify $ \s ->
  s { csValue = csValue s * n
    , csLog   = csLog s ++ ["*" ++ show n ++ " = " ++ show (csValue s * n)]
    }

calcDiv :: Double -> Calc ()
calcDiv 0 = modify $ \s ->
  s { csLog = csLog s ++ ["除零错误，跳过"] }
calcDiv n = modify $ \s ->
  s { csValue = csValue s / n
    , csLog   = csLog s ++ ["/" ++ show n ++ " = " ++ show (csValue s / n)]
    }

runCalc :: Double -> Calc () -> (Double, [String])
runCalc initial prog =
  let finalState = execState prog (CalcState initial [])
  in (csValue finalState, csLog finalState)

-- --- 例3：生成唯一 ID (State 作计数器) ---

type IdGen a = State Int a

freshId :: IdGen Int
freshId = do
  n <- get
  modify (+1)
  return n

generateIds :: Int -> [Int]
generateIds count = evalState (replicateM count freshId) 0

-- ========== Reader Monad ==========

-- Reader r a  ≈  r -> a
-- runReader :: Reader r a -> r -> a
-- ask       :: Reader r r         (获取整个环境)
-- asks      :: (r -> a) -> Reader r a  (获取环境的某个字段)
-- local     :: (r -> r) -> Reader r a -> Reader r a  (局部覆盖环境)

-- --- 应用配置 ---

data AppConfig = AppConfig
  { appName    :: String
  , dbHost     :: String
  , dbPort     :: Int
  , maxRetries :: Int
  , debugMode  :: Bool
  } deriving (Show)

type App a = Reader AppConfig a

-- 获取数据库连接字符串
connectionString :: App String
connectionString = do
  host <- asks dbHost
  port <- asks dbPort
  return $ host ++ ":" ++ show port

-- 生成欢迎信息
welcomeMessage :: String -> App String
welcomeMessage user = do
  name    <- asks appName
  debug   <- asks debugMode
  retries <- asks maxRetries
  let msg = "[" ++ name ++ "] 欢迎, " ++ user ++ "！(最大重试: " ++ show retries ++ ")"
  return $ if debug then "[DEBUG] " ++ msg else msg

-- 模拟带重试的服务调用
serviceCall :: String -> App [String]
serviceCall endpoint = do
  retries <- asks maxRetries
  debug   <- asks debugMode
  let attempts = map (\n ->
        (if debug then "[DEBUG] " else "") ++
        "尝试 " ++ show n ++ ": GET " ++ endpoint
        ) [1..retries]
  return attempts

-- 用 local 覆盖部分配置（不影响外部）
withDebug :: App a -> App a
withDebug = local (\cfg -> cfg { debugMode = True })

-- ========== Reader + State 组合: ReaderT Config (State s) ==========
-- 一个需要配置同时需要可变状态的业务流程

data SessionState = SessionState
  { sessionUser  :: String
  , sessionCalls :: Int
  , sessionLog   :: [String]
  } deriving (Show)

type Session a = ReaderT AppConfig (State SessionState) a

logSession :: String -> Session ()
logSession msg = lift $ modify $ \s ->
  s { sessionCalls = sessionCalls s + 1
    , sessionLog   = sessionLog s ++ [msg]
    }

callApi :: String -> Session String
callApi path = do
  cfg  <- ask
  user <- gets sessionUser
  let url = dbHost cfg ++ path
  logSession $ "[" ++ user ++ "] GET " ++ url
  return $ "200 OK: " ++ url

runSession :: AppConfig -> String -> Session a -> (a, SessionState)
runSession cfg user prog =
  runState (runReaderT prog cfg) (SessionState user 0 [])

-- ========== 主函数 ==========

main :: IO ()
main = do
  -- State Monad
  putStrLn "=== State Monad: 栈操作 ==="
  let (result, finalStack) = runState stackProgram []
  putStrLn $ "结果: " ++ result

  putStrLn "\n=== State Monad: 带日志的计算器 ==="
  let (value, logs) = runCalc 100 $ do
        calcAdd 50
        calcMul 2
        calcDiv 5
        calcAdd 10
  putStrLn $ "计算步骤:"
  mapM_ (\l -> putStrLn $ "  " ++ l) logs
  putStrLn $ "最终值: " ++ show value

  putStrLn "\n=== State Monad: ID 生成器 ==="
  putStrLn $ "生成8个ID: " ++ show (generateIds 8)

  -- Reader Monad
  let cfg = AppConfig
        { appName    = "HaskellDemo"
        , dbHost     = "localhost"
        , dbPort     = 5432
        , maxRetries = 3
        , debugMode  = False
        }

  putStrLn "\n=== Reader Monad: 配置注入 ==="
  putStrLn $ "DB连接: " ++ runReader connectionString cfg
  putStrLn $ runReader (welcomeMessage "Alice") cfg
  putStrLn "服务调用 (普通模式):"
  mapM_ (\l -> putStrLn $ "  " ++ l) $
    runReader (serviceCall "/api/users") cfg
  putStrLn "服务调用 (debug模式, 用 local 覆盖):"
  mapM_ (\l -> putStrLn $ "  " ++ l) $
    runReader (withDebug (serviceCall "/api/orders")) cfg

  -- ReaderT + State
  putStrLn "\n=== ReaderT + State 组合 ==="
  let (_, sess) = runSession cfg "Bob" $ do
        callApi "/api/profile"
        callApi "/api/orders"
        callApi "/api/cart"
  putStrLn $ "用户: " ++ sessionUser sess
  putStrLn $ "调用次数: " ++ show (sessionCalls sess)
  putStrLn "会话日志:"
  mapM_ (\l -> putStrLn $ "  " ++ l) (sessionLog sess)
