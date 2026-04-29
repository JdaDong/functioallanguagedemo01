{-# LANGUAGE GADTs #-}
{-# LANGUAGE FlexibleContexts #-}

-- ============================================================
-- Demo 11: Free Monads & DSL — 自由 Monad 与领域语言
--
-- 核心概念：
--   1. 什么是 Free Monad：最"自由"（最少约束）的 Monad
--   2. 为什么需要：将副作用与解释器分离
--   3. 语法层 vs 语义层
--   4. 多种 Interpreter 模式
--   5. 实际应用：命令式 DSL、测试 mock、日志系统
-- ============================================================

module Main where

import Control.Monad       (when, forM_)

-- ============================================================
-- Part 1: 手动实现 Free Monad
-- ============================================================
-- 
-- Free f a 是一个由 functor f 生成的自由 Monad。
-- 它是"所有以 f 为基础构造的 Monad 中，结构最简单的那个"
--
-- 数据定义：
--   Pure a        — 纯值（Monad 的 return）
--   Free (f (Free f a)) — 嵌套的 functor 层

data Free' f a
  = Pure' a                    -- 纯值 / return
  | Free' (f (Free' f a))     -- 绑定 / 一层效果

instance Functor f => Functor (Free' f) where
  fmap f (Pure' x)    = Pure' (f x)
  fmap f (Free' ffa)  = Free' (fmap (fmap f) ffa)

instance Functor f => Applicative (Free' f) where
  pure = Pure'
  Pure' fn <*> Pure' x    = Pure' (fn x)
  Pure' fn <*> Free' ffa  = Free' (fmap (fn `fmap`) ffa)
  Free' ffa <*> fx       = Free' (fmap (\g -> g <*> fx) ffa)

instance Functor f => Monad (Free' f) where
  Pure' x >>= f = f x
  Free' ffa >>= f = Free' (fmap (>>= f) ffa)

-- 提升一层 functor 操作到 Free Monad
liftF' :: Functor f => f a -> Free' f a
liftF' fa = Free' (fmap Pure' fa)

-- ============================================================
-- Part 2: 示例 — 控制台 IO DSL
-- ============================================================
-- 
-- 将 Console 操作抽象为数据类型，
-- 可以有不同的 interpreter：真实 IO、日志记录、测试

-- 语法：定义可用的操作
data ConsoleF next
  = PutStrLn String next      -- 输出字符串
  | GetLine (String -> next)   -- 读取输入
  | ExitCode Int next          -- 退出码
  deriving Functor

-- 便捷提升函数
putStrLn' :: String -> Free' ConsoleF ()
putStrLn' s = liftF' (PutStrLn s ())

getLine' :: Free' ConsoleF String
getLine' = liftF' (GetLine id)

exitWith' :: Int -> Free' ConsoleF ()
exitWith' code = liftF' (ExitCode code ())

-- DSL 程序：纯声明式的交互流程
consoleProgram :: Free' ConsoleF ()
consoleProgram = do
  putStrLn' "你好！请输入你的名字:"
  name <- getLine'
  putStrLn' $ "欢迎, " ++ name ++ "!"
  putStrLn' "请输入你的年龄:"
  ageStr <- getLine'
  let msg = if read ageStr > 18 then "你是成年人" else "你还未成年"
  putStrLn' msg
  exitWith' 0

-- ---- Interpreter 1: 真实 IO 执行 ----
runConsoleIO :: Free' ConsoleF a -> IO a
runConsoleIO (Pure' x)    = pure x
runConsoleIO (Free' ffa) = case ffa of
  PutStrLn s next -> do
    putStrLn s
    runConsoleIO next
  GetLine k      -> do
    line <- getLine
    runConsoleIO (k line)
  ExitCode code next ->
    -- 不真正退出，只记录
    runConsoleIO next

-- ---- Interpreter 2: 日志收集（纯函数！）----
runConsoleLog :: Free' ConsoleF a -> [String]
runConsoleLog (Pure' _)   = []
runConsoleLog (Free' ffa) = case ffa of
  PutStrLn s next -> ("OUT: " ++ s) : runConsoleLog next
  GetLine k      -> ("IN: <user input>") : runConsoleLog (k "test-input")
  ExitCode c next -> ("EXIT: " ++ show c) : runConsoleLog next

-- ============================================================
-- Part 3: 示例 — 文件系统 DSL
-- ============================================================

data FileSystemF next
  = ReadFile  String (String -> next)
  | WriteFile String String next
  | DeleteFile String next
  deriving Functor

readFileF :: String -> Free' FileSystemF String
readFileF path = liftF' (ReadFile path id)

writeFileF :: String -> String -> Free' FileSystemF ()
writeFileF path content = liftF' (WriteFile path content ())

deleteFileF :: String -> Free' FileSystemF ()
deleteFileF path = liftF' (DeleteFile path ())

-- DSL 程序：文件备份流程
backupProgram :: [String] -> Free' FileSystemF [(String, Bool)]
backupProgram paths = mapM backupOne paths
  where
    backupOne srcPath = do
      content <- readFileF srcPath
      let destPath = srcPath ++ ".bak"
      writeFileF destPath content
      pure (srcPath, True)

-- 解释器：用模拟文件系统运行
type MockFS = [(String, String)]  -- 路径 -> 内容

runFSMock :: MockFS -> Free' FileSystemF a -> (a, MockFS)
runFSMock fs (Pure' x)         = (x, fs)
runFSMock fs (Free' action) = case action of
  ReadFile path k ->
    case lookup path fs of
      Just content -> runFSMock fs (k content)
      Nothing      -> runFSMock fs (k "")
  WriteFile path content next ->
    let newFs = (path, content) : filter ((/= path).fst) fs
    in runFSMock newFs next
  DeleteFile path next ->
    let newFs = filter ((/= path).fst) fs
    in runFSMock newFs next

-- ============================================================
-- Part 4: 示例 — 键值存储 DSL (Tea/Redux 风格)
-- ============================================================

data KVStoreF next
  = KVGet   String (Maybe String -> next)
  | KVPut   String String next
  | KVDel   String next
  | KVLog   String next
  deriving Functor

kvGet :: String -> Free' KVStoreF (Maybe String)
kvGet key = liftF' (KVGet key id)

kvPut :: String -> String -> Free' KVStoreF ()
kvPut key value = liftF' (KVPut key value ())

kvDel :: String -> Free' KVStoreF ()
kvDel key = liftF' (KVDel key ())

kvLog :: String -> Free' KVStoreF ()
kvLog msg = liftF' (KVLog msg ())

-- 业务程序：用户会话管理
sessionManager :: String -> Free' KVStoreF String
sessionManager userId = do
  kvLog $ "处理用户: " ++ userId
  
  session <- kvGet ("session:" ++ userId)
  case session of
    Just sid -> do
      kvLog $ "已有会话: " ++ sid
      pure sid
    Nothing -> do
      kvLog "创建新会话"
      let newSid = "sess-" ++ userId ++ "-" ++ "12345"
      kvPut ("session:" ++ userId) newSid
      pure newSid

-- 解释器：内存 KV Store + 日志
runKVStore :: [(String, String)] 
           -> Free' KVStoreF a 
           -> (a, [(String, String)], [String])
runKVStore store (Pure' x) = (x, store, [])
runKVStore store (Free' op) = case op of
  KVGet key k ->
    let result = lookup key store
        (val, store', logs) = runKVStore store (k result)
    in (val, store', logs)
  
  KVPut key val next ->
    let (result, store', logs) = 
          runKVStore ((key,val):filter((/=key).fst)store) next
    in (result, store', logs)
  
  KVDel key next ->
    let (result, store', logs) = 
          runKVStore (filter((/=key).fst)store) next
    in (result, store', logs)
  
  KVLog msg next ->
    let (result, store', logs) = runKVStore store next
    in (result, store', msg : logs)

-- ============================================================
-- Main
-- ============================================================

main :: IO ()
main = do
  putStrLn "=========================================="
  putStrLn "Demo 11: Free Monads & DSL"
  putStrLn "==========================================\n"

  -- ---- Console DSL ----
  putStrLn "=== 1. Console DSL ==="
  putStrLn "  [Interpreter: 日志模式]"
  let consoleLogs = runConsoleLog consoleProgram
  mapM_ (\l -> putStrLn $ "    " ++ l) consoleLogs
  putStrLn ""

  -- ---- File System DSL ----
  putStrLn "=== 2. File System DSL (模拟) ==="
  let mockFiles =
        [ ("/etc/config", "debug=true\nport=8080")
        , ("/var/data", "users=100\nitems=500")
        ]
      (results', finalFS) = runFSMock mockFiles (backupProgram ["/etc/config", "/var/data"])
  putStrLn $ "  备份结果:"
  mapM_ (\(path, ok) -> putStrLn $
    if ok then "    ✅ " ++ path ++ " → 备份成功"
          else "    ❌ " ++ path ++ " → 失败") results'
  putStrLn $ "  最终文件系统: " ++ show (length finalFS) ++ " 个文件"
  putStrLn ""

  -- ---- KV Store DSL ----
  putStrLn "=== 3. KV Store DSL: 会话管理 ==="
  let initialStore = [("session:user001", "old-session-abc")]
  
  -- 第一次调用：已有会话
  (sid1, store1, logs1) <- pure $! runKVStore initialStore (sessionManager "user001")
  putStrLn $ "  user001 会话: " ++ show sid1
  putStrLn $ "  日志:"
  mapM_ (\l -> putStrLn $ "    - " ++ l) (reverse logs1)
  
  -- 第二次调用：新用户
  (sid2, _, logs2) <- pure $! runKVStore store1 (sessionManager "user002")
  putStrLn $ "\n  user002 会话: " ++ show sid2
  putStrLn $ "  日志:"
  mapM_ (\l -> putStrLn $ "    - " ++ l) (reverse logs2)
  putStrLn ""
  
  -- ---- Free Monad 核心理念总结 ----
  putStrLn "=== 4. Free Monad 核心价值 ==="
  putStrLn "  1. 描述 WHAT（做什么），不关心 HOW（怎么做）"
  putStrLn "  2. 同一个程序可以有多种解释器"
  putStrLn "  3. 测试时用 Mock 解释器，生产用真实解释器"
  putStrLn "  4. 天然支持日志记录和审计追踪"
  putStrLn ""
  
  putStrLn "=========================================="

