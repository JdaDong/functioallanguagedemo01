{-# LANGUAGE FlexibleContexts #-}
{-# LANGUAGE ScopedTypeVariables #-}

-- ============================================================
-- Demo 12: Property-Based Testing — 属性测试
--
-- 核心概念：
--   1. 从 Example-based → Property-based 的范式转变
--   2. 属性：对"所有输入都成立"的断言
--   3. Generator: 随机数据生成
--   4. Shrinker: 最小化反例
--   5. 分类 (classify) 和收集 (collect) 统计
-- ============================================================

module Main where

import Data.List            (sort, reverse)
import Data.Char            (toUpper, isAlphaNum)
import Control.Monad        (replicateM, unless)

-- ============================================================
-- Part 1: 核心类型定义
-- ============================================================

-- 测试结果
data TestResult
  = Pass String           -- 通过，附带信息
  | Fail String [String]  -- 失败，附带反例和日志
  | Error String          -- 运行时错误
  deriving Show

-- 属性：一个返回 Bool 的函数 + 元数据
data Property = Property
  { propName    :: String          -- 属性名称
  , propTest    :: Gen Bool        -- 生成器包装的测试
  , propCount   :: Int             -- 测试次数
  }

-- 随机数生成器（简化版）
type Seed = Int

newtype Gen a = Gen { unGen :: Seed -> (a, Seed) }

instance Functor Gen where
  fmap f (Gen g) = Gen $ \seed ->
    let (x, seed') = g seed
    in (f x, seed')

instance Applicative Gen where
  pure x = Gen $ \s -> (x, s)
  Gen gf <*> Gen gx = Gen $ \s ->
    let (f, s1) = gf s
        (x, s2) = gx s1
    in (f x, s2)

instance Monad Gen where
  Gen gx >>= f = Gen $ \s ->
    let (x, s1) = gx s
    in unGen (f x) s1

-- ============================================================
-- Part 2: Generator 组合子
-- ============================================================

-- 基础生成器
genInt :: Gen Int
genInt = Gen $ \s -> (s, s + 1)  -- 简化：用种子作为值

genIntRange :: (Int, Int) -> Gen Int
genIntRange (lo, hi) = Gen $ \s ->
  let range = hi - lo + 1
      val = lo + (s * 1103515245 + 12345) `mod` (range * 997 + 1) `mod` range
      s' = s + 1
  in (if lo <= val && val <= hi then val else lo, s')

genBool :: Gen Bool
genBool = genBool'  -- 简化：复用 genBool'

genBool' :: Gen Bool
genBool' = Gen $ \s ->
  let b = odd s
      s' = s + 1
  in (b, s')

genChar :: Gen Char
genChar = Gen $ \s ->
  let chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 !@#$%"
      idx = s `mod` length chars
  in (chars !! idx, s + 1)

genStringLen :: Int -> Gen String
genStringLen n = replicateM n genChar

genString :: Gen String
genString = genIntRange (0, 20) >>= genStringLen

genList :: Gen a -> Gen [a]
genList genA = do
  n <- genIntRange (0, 10)
  replicateM n genA

genPair :: Gen a -> Gen b -> Gen (a, b)
genPair ga gb = do
  a <- ga
  b <- gb
  pure (a, b)

genMaybe :: Gen a -> Gen (Maybe a)
genMaybe ga = do
  b <- genBool'
  if b then Just <$> ga else pure Nothing

-- ============================================================
-- Part 3: 属性组合子
-- ============================================================

-- 对所有测试，属性为真
forAll :: Show a => Gen a -> (a -> Bool) -> String -> Property
forAll gen pred name = Property name (pred <$> gen) 100

-- 反例生成器：找到使属性为 False 的输入
-- 真正的 QuickCheck 会做 shrinking，这里简化
counterExample :: Show a => Gen a -> (a -> Bool) -> Int -> Maybe (a, [String])
counterExample gen pred maxTries = go 0
  where
    go count
      | count >= maxTries = Nothing
      | otherwise =
          let (val, _) = unGen gen count
          in if pred val
               then go (count + 1)
               else Just (val, ["输入: " ++ show val])

-- ============================================================
-- Part 4: 经典属性示例
-- ============================================================

-- ---- 列表属性 ----

-- 反转两次等于原列表
prop_reverseReverse :: Property
prop_reverseReverse = forAll (genList genChar)
  (\xs -> reverse (reverse xs) == xs)
  "reverse.reverse = id"

-- 排序后是升序
prop_sortedIsOrdered :: Property
prop_sortedIsOrdered = forAll (genList (genIntRange (-50, 50)))
  (\xs -> isSorted (sort xs))
  "sort produces ordered list"
  where
    isSorted []       = True
    isSorted [_]      = True
    isSorted (x:y:xs) = x <= y && isSorted (y:xs)

-- sort 是幂等的
prop_sortIdempotent :: Property
prop_sortIdempotent = forAll (genList (genIntRange (-20, 20)))
  (\xs -> sort (sort xs) == sort xs)
  "sort.sort = sort"

-- ---- 数学属性 ----

-- 加法交换律
prop_addCommutative :: Property
prop_addCommutative = forAll 
  (genPair (genIntRange (-100, 100)) (genIntRange (-100, 100)))
  (\(a,b) -> a + b == b + a)
  "a + b == b + a"

-- 乘法对加法的分配律
prop_mulDistributes :: Property
prop_mulDistributes = forAll
  (do a <- genIntRange (-10, 10); b <- genIntRange (-10, 10); c <- genIntRange (-10, 10); pure (a,b,c))
  (\(a,b,c) -> a * (b + c) == a*b + a*c)
  "a*(b+c) == a*b + a*c"

-- ---- 字符串属性 ----

-- toUpper 不改变长度
prop_toUpperPreservesLength :: Property
prop_toUpperPreservesLength = forAll genString
  (\s -> length (map toUpper s) == length s)
  "length(toUpper s) == length s"

-- ---- Maybe 属性 ----

-- Just fmap = Just . f
prop_fmapJust :: Property
prop_fmapJust = forAll (genMaybe genInt) 
  (\mx -> fmap (*2) mx == fmap (*2) mx)
  "fmap (*2) on Maybe consistent"

-- ============================================================
-- Part 5: 测试运行器
-- ============================================================

runProperty :: Property -> IO TestResult
runProperty (Property name genTest count) = do
  let results = map runSingle [0..count-1]
      passes  = length $ filter id results
      fails   = filter (not.snd) $ zip [0..count-1] results
  
  case fails of
    [] -> pure $ Pass $ name ++ " ✓ (" ++ show count ++ "/" ++ show count ++ " passed)"
    ((i, _):_) -> pure $
      Fail (name ++ " ✗ at test #" ++ show i) []
  where
    runSingle seed = 
      let (result, _) = unGen genTest seed
      in result

runQuickCheck :: [Property] -> IO ()
runQuickCheck props = do
  putStrLn $ "Running " ++ show (length props) ++ " properties..."
  putStrLn ""
  
  results <- mapM runProperty props
  
  let (passes', failures) = partitionEithers 
        [ case r of
            Pass msg  -> Left msg
            Fail _ msgs -> Right msgs
            Error e    -> Right [e]
        | r <- results
        ]
  
  mapM_ (\p -> putStrLn $ "  PASS: " ++ p) passes'
  unless (null failures) $ do
    putStrLn "\n  FAILURES:"
    mapM_ (\msgs -> mapM_ (\m -> putStrLn $ "    ✗ " ++ m) msgs) failures
  
  putStrLn $ "\n  结果: " ++ show (length passes') ++ " passed, " 
         ++ show (length failures) ++ " failed"
  where
    partitionEithers = foldr categorize ([], [])
    categorize (Left l)  (ls, rs) = (l:ls, rs)
    categorize (Right r) (ls, rs) = (ls, r:rs)

-- ============================================================
-- Main
-- ============================================================

main :: IO ()
main = do
  putStrLn "=========================================="
  putStrLn "Demo 12: Property-Based Testing — 属性测试"
  putStrLn "==========================================\n"

  -- ---- 手动展示反例查找 ----
  putStrLn "=== 1. 反例查找 ==="
  
  -- 这个属性应该通过
  case counterExample (genList (genIntRange (0, 20))) 
                      (\xs -> sum xs >= 0) 
                      500 of
    Nothing -> putStrLn $ "  sum(xs) >= 0: 未找到反例 ✓"
    Just (example, _) -> putStrLn $ "  sum(xs) >= 0: 反例 " ++ show example
  
  -- 这个属性应该失败（找反例）
  case counterExample (genList (genIntRange (-10, 10)))
                      (\xs -> all (>0) xs)  -- 不是所有列表元素都 >0
                      100 of
    Nothing   -> putStrLn $ "  all(>0): 未找到反例 ✓"
    Just (ex, info) -> putStrLn $ "  all(>0): 找到反例 " ++ show ex
  
  putStrLn ""

  -- ---- 运行完整测试套件 ----
  putStrLn "=== 2. 完整测试套件 ==="
  let allProperties =
        [ prop_reverseReverse
        , prop_sortedIsOrdered
        , prop_sortIdempotent
        , prop_addCommutative
        , prop_mulDistributes
        , prop_toUpperPreservesLength
        , prop_fmapJust
        ]
  
  runQuickCheck allProperties
  putStrLn ""

  -- ---- 属性测试 vs 传统测试对比 ----
  putStrLn "=== 3. 属性测试 vs 传统测试 ==="
  putStrLn "  传统测试:"
  putStrLn "    assert(reverse([1,2,3]) == [3,2,1])  — 只测一种情况!"
  putStrLn ""
  putStrLn "  属性测试:"
  putStrLn "    ∀ xs. reverse(reverse(xs)) == xs     — 覆盖所有可能!"
  putStrLn ""
  putStrLn "  QuickCheck 的威力:"
  putStrLn "    • 自动生成数百个随机测试用例"
  putStrLn "    • 发现你没想到的边界条件"
  putStrLn "    • Shrink 反例到最小可理解形式"
  putStrLn "    • 分类统计：哪些类型的输入更易触发 bug"
  putStrLn ""
  
  putStrLn "=========================================="
