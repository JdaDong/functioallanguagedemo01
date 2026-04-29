-- ============================================================
-- Demo 16: Streaming Pipeline —— 用最小 Haskell 手写一个小型流式管道
--
-- 为什么需要流：
--   * 普通列表 + 惰性求值 在 Haskell 里本来就能表达"无限流 / 按需计算"，
--     但一旦牵扯到 IO 副作用（从文件读、从网络读、向下游写），
--     我们就需要一个更明确的抽象，让生产、转换、消费能分开描述，
--     并且严格控制"什么时候真的执行 effect"。
--
--   * 这正是 conduit / pipes / streamly 这些库在做的事。
--     本 Demo 用最小的代码，手写一套同构结构，
--     帮助建立 fs2 / conduit 的核心直觉。
--
-- 这个 Demo 不依赖任何外部库，只用 base。
-- 运行：runhaskell 16_StreamingPipeline.hs
-- ============================================================

module Main where

import           Data.IORef
import           System.IO       (hFlush, stdout)
import           Control.Monad   (when, forM_, unless)

-- ============================================================
-- Part 1: 最小流模型 —— Stream m a
-- ============================================================
--
-- Stream m a 表示"下一个元素的来源"：
--   * Done        —— 流结束
--   * Yield a k   —— 生产出一个 a，后面还有一个 k（下一步怎么拉）
-- 所有 effect 都以 m（通常是 IO）作为容器。

data Step m a = Done | Yield a (Stream m a)
newtype Stream m a = Stream { runStep :: m (Step m a) }

-- 最基础的源：一次性把一个列表做成流
fromList :: Monad m => [a] -> Stream m a
fromList []     = Stream (pure Done)
fromList (x:xs) = Stream (pure (Yield x (fromList xs)))

-- 无限自然数流：0, 1, 2, ...
naturals :: Monad m => Stream m Int
naturals = go 0
  where
    go !n = Stream (pure (Yield n (go (n + 1))))

-- effectful 源：每次被拉动时都打一行日志
tickEvery :: Int -> Stream IO Int
tickEvery start = go start
  where
    go !n = Stream $ do
      putStrLn $ "  [tick] yield " ++ show n
      pure (Yield n (go (n + 1)))

-- ============================================================
-- Part 2: 基本组合子 —— map / filter / take / chunkN
-- ============================================================

smap :: Monad m => (a -> b) -> Stream m a -> Stream m b
smap f s = Stream $ do
  step <- runStep s
  case step of
    Done       -> pure Done
    Yield x xs -> pure (Yield (f x) (smap f xs))

sfilter :: Monad m => (a -> Bool) -> Stream m a -> Stream m a
sfilter p s = Stream $ do
  step <- runStep s
  case step of
    Done       -> pure Done
    Yield x xs ->
      if p x
        then pure (Yield x (sfilter p xs))
        else runStep (sfilter p xs)

stake :: Monad m => Int -> Stream m a -> Stream m a
stake n _ | n <= 0 = Stream (pure Done)
stake n s = Stream $ do
  step <- runStep s
  case step of
    Done       -> pure Done
    Yield x xs -> pure (Yield x (stake (n - 1) xs))

-- 分块：连续 n 个元素打成一个列表
chunkN :: Monad m => Int -> Stream m a -> Stream m [a]
chunkN size s0 = go s0
  where
    go s = Stream $ do
      (bufReversed, rest) <- take' size s
      case bufReversed of
        [] -> pure Done
        _  -> pure (Yield (reverse bufReversed) (go rest))

    take' 0 s = pure ([], s)
    take' k s = do
      step <- runStep s
      case step of
        Done       -> pure ([], Stream (pure Done))
        Yield x xs -> do
          (ys, rest) <- take' (k - 1) xs
          pure (x : ys, rest)

-- evalTap：每个元素经过时额外 run 一个 effect（用来打日志 / 写库）
evalTap :: Monad m => (a -> m ()) -> Stream m a -> Stream m a
evalTap f s = Stream $ do
  step <- runStep s
  case step of
    Done       -> pure Done
    Yield x xs -> do
      f x
      pure (Yield x (evalTap f xs))

-- ============================================================
-- Part 3: Sink —— 真正"消费流"的地方
-- ============================================================

-- 拉取整条流，忽略结果
drain :: Monad m => Stream m a -> m ()
drain s = do
  step <- runStep s
  case step of
    Done       -> pure ()
    Yield _ xs -> drain xs

-- 收集成列表（小心：对无限流会爆栈）
toListS :: Monad m => Stream m a -> m [a]
toListS s = do
  step <- runStep s
  case step of
    Done       -> pure []
    Yield x xs -> do
      rest <- toListS xs
      pure (x : rest)

-- 左折叠（流上的 foldl'），是所有"聚合"的基础
foldS :: Monad m => (b -> a -> b) -> b -> Stream m a -> m b
foldS f !z s = do
  step <- runStep s
  case step of
    Done       -> pure z
    Yield x xs -> foldS f (f z x) xs

-- ============================================================
-- Part 4: 实战 —— 一条"数据清洗 + 批处理 + 汇总"管道
-- ============================================================
-- 场景：模拟从"某个来源"不停涌进 Int 数据（带噪声），我们需要：
--   1) 过滤掉非法值
--   2) 做一次简单转换（比如放大 10 倍）
--   3) 每 3 条打一次批（模拟批量写库）
--   4) 最后统计总和和批次数

rawSource :: [Int]
rawSource = [1, -2, 3, 999999, 4, 5, -7, 6, 0, 8, 9, 10]

pipelineDemo :: IO ()
pipelineDemo = do
  let src     = fromList rawSource :: Stream IO Int
      cleaned = sfilter (\x -> x > 0 && x < 1000) src
      scaled  = smap (* 10) cleaned
      logged  = evalTap (\x -> putStrLn ("    [ok] " ++ show x)) scaled
      batched = chunkN 3 logged
  -- 边"消费"边打印每一批
  putStrLn "  >> 正在消费流..."
  batchesRef <- newIORef (0 :: Int)
  totalRef   <- newIORef (0 :: Int)
  let loop s = do
        step <- runStep s
        case step of
          Done -> pure ()
          Yield batch rest -> do
            modifyIORef' batchesRef (+ 1)
            modifyIORef' totalRef   (+ sum batch)
            putStrLn $ "  [batch] " ++ show batch
            loop rest
  loop batched
  b <- readIORef batchesRef
  t <- readIORef totalRef
  putStrLn $ "  >> 共 " ++ show b ++ " 批，总和 = " ++ show t

-- ============================================================
-- Part 5: 背压直觉 —— 下游不拉，上游就不算
-- ============================================================
--
-- 流式模型的一个核心直觉：
--   上游的每一步 Yield，都是被下游"拉"出来的。
--   下游只取 5 个，上游就只会真的执行 5 次 effect，
--   后面那些"想象中要执行"的副作用永远不会发生。
-- 这就是"惰性 + 显式 effect"带来的天然背压。

backpressureDemo :: IO ()
backpressureDemo = do
  putStrLn "  下游只消费前 5 个，观察 tickEvery 里的日志只打 5 次："
  let s = stake 5 (tickEvery 100)
  xs <- toListS s
  putStrLn $ "  最终结果 = " ++ show xs

-- ============================================================
-- Part 6: 作为对比 —— Haskell 原生"纯列表惰性流"
-- ============================================================
--
-- 纯的、没有 IO 的场景下，Haskell 其实直接用列表就能做流式。
-- 这里顺便重新体会一下它和 Demo 06 StreamingPipeline 的差异：
--   * 原生列表：语法最轻量，但混入 IO 就不那么纯净了
--   * Stream m a：把 m 抽象出来，才能接 IO / Resource / Concurrent
pureStreamDemo :: IO ()
pureStreamDemo = do
  let result =
        take 5
          . map (\x -> x * x)
          . filter odd
          $ [1 ..]    -- 无限列表，按需求值
  putStrLn $ "  前 5 个奇数的平方 = " ++ show result

-- ============================================================
-- Main
-- ============================================================

main :: IO ()
main = do
  putStrLn "=========================================="
  putStrLn "Demo 16: 手写最小流 / fs2-conduit 直觉"
  putStrLn "==========================================\n"

  putStrLn "=== 1. fromList / smap / sfilter / stake ==="
  xs <- toListS
        . stake 5
        . smap (* 10)
        . sfilter even
        $ (fromList [1 .. 20 :: Int] :: Stream IO Int)
  putStrLn $ "  stake 5 (smap (*10) (sfilter even [1..20])) = " ++ show xs
  putStrLn ""

  putStrLn "=== 2. foldS：流上的左折叠 ==="
  total <- foldS (+) 0 (fromList [1 .. 100 :: Int] :: Stream IO Int)
  putStrLn $ "  1 + 2 + ... + 100 = " ++ show total
  putStrLn ""

  putStrLn "=== 3. 实战管道：清洗 + map + 批处理 + 汇总 ==="
  pipelineDemo
  putStrLn ""

  putStrLn "=== 4. 背压直觉：下游不拉，上游就不算 ==="
  backpressureDemo
  hFlush stdout
  putStrLn ""

  putStrLn "=== 5. 纯列表版本的流（无 IO） ==="
  pureStreamDemo
  putStrLn ""

  putStrLn "=== 知识点小结 ==="
  putStrLn "  * Stream m a 是 'effectful 的惰性序列'"
  putStrLn "  * 常见组合子：smap / sfilter / stake / chunkN / evalTap"
  putStrLn "  * 消费方式：drain / toList / foldS（对应 fs2 compile.drain / toList / fold）"
  putStrLn "  * 背压由 '下游主动 run' 天然提供"
  putStrLn "  * 实际工程直接用 conduit / pipes / streamly / streaming 即可，"
  putStrLn "    本 Demo 的目的是让你相信：'流' 并不神秘，它就是一个拉式 ADT + 组合子集合"
  putStrLn "=========================================="
