{-# LANGUAGE FlexibleContexts   #-}
{-# LANGUAGE GeneralizedNewtypeDeriving #-}

-- ============================================================
-- Demo 28: Monad Transformers vs MTL 风格
--
-- 问题背景：
--   你有一个小业务 —— "批量处理订单"：
--     * 需要读配置（税率、最大订单金额）      -> Reader
--     * 要维护"已处理订单数"                  -> State
--     * 超过最大金额要报错                    -> Except
--   也就是一次要叠三个效果。
--
-- 两种主流写法：
--   Part 1  直接用 transformer 具体类型： ExceptT e (StateT s (ReaderT r IO)) a
--           —— 看得见每一层，但函数签名又臭又长、换顺序痛苦。
--   Part 2  用 mtl 类约束： (MonadReader r m, MonadState s m, MonadError e m) => m a
--           —— 函数体完全一样，只不过签名从"具体栈"变成"能力集合"。
--           调用方自由决定具体栈怎么叠。
--
-- 两份实现结构刻意对齐，方便你 diff 着看。
--
-- 运行：runghc 28_MonadTransformersVsMTL.hs
-- ============================================================

module Main where

import Control.Monad.Reader
import Control.Monad.State
import Control.Monad.Except
import Data.Functor.Identity (Identity, runIdentity)

-- ============================================================
-- 共享的数据模型
-- ============================================================

data Config = Config
  { taxRate       :: Double  -- 税率，如 0.1 = 10%
  , maxOrderCents :: Int     -- 单笔上限（分）
  } deriving Show

data Order = Order { orderId :: Int, amountCents :: Int } deriving Show

type Counter = Int

-- 错误类型
data BizErr = OverLimit Int Int  -- 订单 id, 金额
            deriving Show

-- 业务：把单笔订单加税，更新已处理计数，超限则报错
--   结果：含税后的金额（分）
-- 这是 Part1 和 Part2 都要实现的"同一个"函数骨架。

-- ============================================================
-- Part 1: Transformer Stack 具体类型风格
-- ============================================================
--
-- 外到内：Except 在最外 -> State -> Reader -> Identity
-- 选择 Identity 是因为本 Demo 不做真实 IO；想跑 IO 把底换成 IO 即可。

type AppT a = ExceptT BizErr (StateT Counter (ReaderT Config Identity)) a

processOrderT :: Order -> AppT Int
processOrderT o = do
  cfg <- lift . lift $ ask         -- 穿两层 lift 拿 Reader
  let amt = amountCents o
  when (amt > maxOrderCents cfg) $
    throwError (OverLimit (orderId o) amt)
  lift $ modify (+ 1)              -- 穿一层 lift 更新 State
  let taxed = round (fromIntegral amt * (1 + taxRate cfg) :: Double) :: Int
  pure taxed

runAppT :: Config -> Counter -> AppT a -> (Either BizErr a, Counter)
runAppT cfg s0 m =
  runIdentity $ runReaderT (runStateT (runExceptT m) s0) cfg

-- ============================================================
-- Part 2: MTL 类约束风格
-- ============================================================
--
-- 函数体几乎和 Part 1 一样，但：
--   * 无 lift —— ask / get / modify / throwError 直接用
--   * 签名是"能力集合"，由调用方决定具体栈
--   * 想加日志、换 IO 底座、改层序，都不用动这个函数

processOrderM ::
  ( MonadReader Config m
  , MonadState Counter m
  , MonadError BizErr m
  ) => Order -> m Int
processOrderM o = do
  cfg <- ask
  let amt = amountCents o
  when (amt > maxOrderCents cfg) $
    throwError (OverLimit (orderId o) amt)
  modify (+ 1)
  let taxed = round (fromIntegral amt * (1 + taxRate cfg) :: Double) :: Int
  pure taxed

-- 调用方按需组合。这里用和 Part 1 相同的栈，方便对比结果一致。
runAppM ::
  Config -> Counter
  -> ExceptT BizErr (StateT Counter (ReaderT Config Identity)) a
  -> (Either BizErr a, Counter)
runAppM cfg s0 m =
  runIdentity $ runReaderT (runStateT (runExceptT m) s0) cfg

-- ============================================================
-- 批处理帮手：两种风格复用同一份 traverse 逻辑
-- ============================================================

-- Part 1 版批处理
batchT :: [Order] -> AppT [Int]
batchT = traverse processOrderT

-- Part 2 版批处理 —— 约束自动向上传播
batchM ::
  ( MonadReader Config m
  , MonadState Counter m
  , MonadError BizErr m
  ) => [Order] -> m [Int]
batchM = traverse processOrderM

-- ============================================================
-- Main
-- ============================================================

main :: IO ()
main = do
  putStrLn "=========================================="
  putStrLn "Demo 28: Monad Transformers vs MTL"
  putStrLn "==========================================\n"

  let cfg = Config { taxRate = 0.10, maxOrderCents = 100000 }  -- 上限 1000 元
      good = [ Order 1 5000, Order 2 12000, Order 3 800 ]       -- 全部合法
      bad  = [ Order 1 5000, Order 2 150000, Order 3 800 ]      -- 第二笔超限

  putStrLn "=== Part 1: Transformer Stack (具体类型) ==="
  let (rOk, nOk)   = runAppT cfg 0 (batchT good)
      (rErr, nErr) = runAppT cfg 0 (batchT bad)
  putStrLn $ "  全部合法 -> " ++ show rOk  ++ " , counter=" ++ show nOk
  putStrLn $ "  含超限   -> " ++ show rErr ++ " , counter=" ++ show nErr
  putStrLn ""

  putStrLn "=== Part 2: MTL 类约束 ==="
  let (rOk2, nOk2)   = runAppM cfg 0 (batchM good)
      (rErr2, nErr2) = runAppM cfg 0 (batchM bad)
  putStrLn $ "  全部合法 -> " ++ show rOk2  ++ " , counter=" ++ show nOk2
  putStrLn $ "  含超限   -> " ++ show rErr2 ++ " , counter=" ++ show nErr2
  putStrLn ""

  putStrLn "=== 关键对比 ==="
  putStrLn "  Transformer stack (Part 1):"
  putStrLn "    + 层次清晰可见，类型精确"
  putStrLn "    - 函数签名冗长，lift 到处撒"
  putStrLn "    - 换层序 / 换底座要改所有函数签名"
  putStrLn ""
  putStrLn "  MTL 类约束 (Part 2):"
  putStrLn "    + 函数只声明\"需要什么能力\"，栈由调用方决定"
  putStrLn "    + 无 lift，换层序/底座不影响业务函数"
  putStrLn "    - 需要更多扩展（FlexibleContexts 等）"
  putStrLn "    - 超过一定层数后类型推导可能吃力"
  putStrLn ""
  putStrLn "  经验法则："
  putStrLn "    * 写库 / 跨层复用 -> MTL 风格"
  putStrLn "    * 应用最外层 / 组装具体栈时 -> transformer 具体类型"
  putStrLn "    * 真实项目常常两者混用：业务函数 MTL，入口 runXxx 具体栈"
  putStrLn "=========================================="
