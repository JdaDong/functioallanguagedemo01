-- ============================================================
-- Demo 22: Haskell FP 工程化收口 —— 属性测试状态机 + 最佳实践
--
-- 这是 Haskell 系列的最后一站，目标是把前面 01–21 的所有抽象
-- "钉死在真实工程直觉里"：
--
--   Part A. 一个最小"基于状态机的属性测试"例子：
--           用随机命令序列测试一个可变计数器的不变量，
--           这正是 hedgehog / quickcheck-state-machine 的核心玩法，
--           也是银行、数据库、分布式系统做健壮性测试的标准武器。
--
--   Part B. Haskell 工程最佳实践清单：
--           * 项目结构、模块化、导入规范
--           * 常用语言扩展组合
--           * 运行时建议：ReaderT Env IO 模式 + tagless class
--           * 性能与并发注意点（严格求值、Text/ByteString、STM 粒度）
--           * 测试金字塔：unit / property / state-machine / integration
--
-- 不依赖外部库，用 base 的 System.Random 做最小随机驱动。
-- 运行：runhaskell 22_FPBestPracticesAndStateMachineTests.hs
-- ============================================================

module Main where

import           Data.IORef
import           Control.Monad     (forM_, replicateM)
import           System.Random     (randomRIO)

-- ============================================================
-- Part A: 基于状态机的属性测试
-- ============================================================
--
-- 思路：
--   * 待测系统（SUT）= 一个可变计数器（IORef Int）
--   * 定义一组命令：Inc / Dec / Reset / Read
--   * 同时在"纯模型"和"真实 SUT"上执行同一序列命令，
--     对比最终状态；随机生成上百/上千条序列自动跑，
--     一有差异立刻暴露出来。
--
-- 这是 FP 世界里非常地道的一种测试方法。

-- ---- 系统与模型 ----

-- 真实被测系统：一个 IORef 计数器
newtype Counter = Counter (IORef Int)

newCounter :: IO Counter
newCounter = Counter <$> newIORef 0

inc :: Counter -> IO ()
inc (Counter r) = modifyIORef' r (+ 1)

-- 故意埋一个"在值 == 7 的时候 dec 不生效"的 bug 演示
-- （把 False 换成 True 就能看到属性测试立刻发现它）
dec :: Counter -> IO ()
dec (Counter r) = do
  v <- readIORef r
  if injectBug && v == 7
    then pure ()                              -- <-- 故意跳过 (bug!)
    else writeIORef r (v - 1)

reset_ :: Counter -> IO ()
reset_ (Counter r) = writeIORef r 0

readC :: Counter -> IO Int
readC (Counter r) = readIORef r

injectBug :: Bool
injectBug = False   -- 改成 True 可以复现一次 "属性测试发现 bug" 的效果

-- 纯模型：就是一个 Int
data Cmd = Inc | Dec | Reset deriving (Show, Eq)

-- 模型如何响应每个命令
stepModel :: Cmd -> Int -> Int
stepModel Inc   n = n + 1
stepModel Dec   n = n - 1
stepModel Reset _ = 0

-- 真实 SUT 如何响应每个命令
stepSUT :: Cmd -> Counter -> IO ()
stepSUT Inc   c = inc c
stepSUT Dec   c = dec c
stepSUT Reset c = reset_ c

-- ---- 随机命令生成器 ----

genCmd :: IO Cmd
genCmd = do
  k <- randomRIO (0, 9 :: Int)
  pure $ case k of
    n | n < 5 -> Inc         -- 50% Inc
      | n < 8 -> Dec         -- 30% Dec
      | otherwise -> Reset   -- 20% Reset

genCmds :: Int -> IO [Cmd]
genCmds n = replicateM n genCmd

-- ---- 属性：模型和真实系统最终状态永远一致 ----

propModelMatchesSUT :: [Cmd] -> IO Bool
propModelMatchesSUT cmds = do
  c <- newCounter
  forM_ cmds (`stepSUT` c)
  real  <- readC c
  let model = foldl (flip stepModel) 0 cmds
  pure (real == model)

-- 跑 N 轮随机命令序列，任何一轮失败都直接汇报
runStateMachineTest :: Int -> Int -> IO ()
runStateMachineTest rounds perRound = do
  results <- mapM (\i -> do
    cmds <- genCmds perRound
    ok   <- propModelMatchesSUT cmds
    pure (i, ok, cmds)) [1 .. rounds]
  let failures = [ (i, cmds) | (i, ok, cmds) <- results, not ok ]
  if null failures
    then putStrLn $ "  所有 " ++ show rounds ++ " 轮 x " ++ show perRound
                    ++ " 条命令的随机测试全部通过"
    else do
      putStrLn $ "  X 属性测试发现反例！共 " ++ show (length failures) ++ " 条"
      let (i, cmds) = head failures
      putStrLn $ "  第一条反例 (round " ++ show i ++ "): " ++ show cmds

-- ============================================================
-- Part B: 最佳实践清单（纯文档化）
-- ============================================================

printBestPractices :: IO ()
printBestPractices = do
  putStrLn "=== Haskell 工程最佳实践清单 ==="
  putStrLn ""
  putStrLn "1) 项目结构"
  putStrLn "   * 用 cabal / stack 管理；每个"领域"一个 module"
  putStrLn "   * Domain / UseCase / Infra 分层（类似 Clean Architecture）"
  putStrLn "   * 在 module 顶部 explicit import list，便于重构和 code review"
  putStrLn ""
  putStrLn "2) 常用语言扩展组合"
  putStrLn "   * 工程起步三件套：OverloadedStrings, LambdaCase, TupleSections"
  putStrLn "   * 类型驱动：DeriveGeneric, DerivingVia, DerivingStrategies"
  putStrLn "   * 记录语法：RecordWildCards, NamedFieldPuns"
  putStrLn "   * 类型级：GADTs, DataKinds, TypeFamilies, RankNTypes"
  putStrLn ""
  putStrLn "3) 运行时架构"
  putStrLn "   * 推荐默认模式：newtype AppM a = AppM { runAppM :: ReaderT Env IO a }"
  putStrLn "     + tagless class (MonadDB / MonadCache / ...) 描述能力"
  putStrLn "   * 业务函数签名：(MonadDB m, MonadLogger m) => Input -> m Output"
  putStrLn "   * 测试时替换实例，生产时用 IO 实例"
  putStrLn ""
  putStrLn "4) 性能与并发"
  putStrLn "   * 字符串优先用 Text / ByteString，避免 String (= [Char])"
  putStrLn "   * 大数据折叠用 foldl' / foldMap' 保持严格求值"
  putStrLn "   * 容器优先用 Data.Map.Strict / Data.HashMap.Strict"
  putStrLn "   * 并发优先用 STM；必须共享且细粒度时才用 MVar/IORef"
  putStrLn "   * 异步用 async 库的 race / concurrently / mapConcurrently"
  putStrLn ""
  putStrLn "5) 测试金字塔"
  putStrLn "   * 单元测试      : hspec / tasty"
  putStrLn "   * 属性测试      : QuickCheck / hedgehog"
  putStrLn "   * 状态机测试    : quickcheck-state-machine / hedgehog-quickcheck"
  putStrLn "   * 集成/合同测试 : 用真实 IO 或 docker 依赖"
  putStrLn ""
  putStrLn "6) 避免的反模式"
  putStrLn "   * 到处用 String 表示领域类型 -> 用 newtype 打标签"
  putStrLn "   * 到处用 error / undefined -> 用 Either / Maybe / throwIO"
  putStrLn "   * 业务代码里直接 IO -> 用 tagless class / Free 抽象"
  putStrLn "   * 过早 lazy：foldl (+) 0 会吞内存，改 foldl'"
  putStrLn ""

-- ============================================================
-- Main
-- ============================================================

main :: IO ()
main = do
  putStrLn "=========================================="
  putStrLn "Demo 22: 状态机属性测试 + 工程最佳实践"
  putStrLn "==========================================\n"

  putStrLn "=== 1. 基于状态机的属性测试 ==="
  putStrLn "  (SUT=可变计数器，模型=Int，验证 1000 条随机命令下两者状态一致)"
  runStateMachineTest 50 20     -- 50 轮，每轮 20 条命令
  putStrLn ""
  putStrLn "  提示：把顶部 injectBug 改成 True，"
  putStrLn "        属性测试会在值达到 7 时立刻发现 dec 不正确的反例"
  putStrLn ""

  printBestPractices

  putStrLn "=== 总收口 ==="
  putStrLn "  至此，Haskell FP 知识圈已覆盖："
  putStrLn "    入门        : 01 纯函数 / 02 Monad / 03 柯里化"
  putStrLn "    副作用      : 04 IO / 05 State&Reader / 19 异常&并发"
  putStrLn "    并发        : 06 STM / 19 MVar&async"
  putStrLn "    数据建模    : 07 ADT / 15 GADT&DataKinds / 20 Generics"
  putStrLn "    Monad 进阶  : 08 Transformer / 11 Free / 14 Traversable / 18 MonadPlus"
  putStrLn "    抽象终点    : 13 Arrow&Profunctor / 09 Lens / 17 λ演算&fix"
  putStrLn "    工程实战    : 10 Parser / 12 QuickCheck / 16 Stream"
  putStrLn "                  21 Effect System / 22 状态机测试&最佳实践"
  putStrLn "=========================================="
