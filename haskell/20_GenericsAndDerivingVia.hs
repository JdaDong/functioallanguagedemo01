{-# LANGUAGE DeriveGeneric       #-}
{-# LANGUAGE DeriveFunctor       #-}
{-# LANGUAGE DerivingStrategies  #-}
{-# LANGUAGE DerivingVia         #-}
{-# LANGUAGE GeneralizedNewtypeDeriving #-}
{-# LANGUAGE StandaloneDeriving  #-}
{-# LANGUAGE FlexibleInstances   #-}
{-# LANGUAGE TypeApplications    #-}
{-# LANGUAGE ScopedTypeVariables #-}

-- ============================================================
-- Demo 20: Generics / DerivingVia / newtype 抽象
--          —— 类型驱动的工程化技巧
--
-- Haskell 在工程里最强的一点：
--   * 数据类型一旦定义，很多能力（Show, Eq, Ord, JSON, 数据库行...）
--     都可以由类型结构自动派生出来，不必一行行手写。
--
-- 本 Demo 覆盖：
--   1) 四种 deriving 策略：stock / newtype / anyclass / via
--   2) GHC.Generics：从类型结构反射，做"结构即编码"
--   3) 用 newtype + DerivingVia 组合多种语义
--   4) Identity / Const / Compose 三大"抽象适配器"
--
-- 不依赖任何外部库，只用 base + GHC.Generics。
-- 运行：runhaskell 20_GenericsAndDerivingVia.hs
-- ============================================================

module Main where

import GHC.Generics
import Data.Functor.Identity (Identity (..))
import Data.Functor.Const    (Const (..))
import Data.Functor.Compose  (Compose (..))

-- ============================================================
-- Part 1: 四种 deriving 策略
-- ============================================================
--
-- Haskell 的 deriving 不止一种！
--
--   deriving stock       —— 编译器内置的经典派生（Eq/Ord/Show/Generic/...）
--   deriving newtype     —— 把底层类型的实例"平移"给 newtype 包装
--   deriving anyclass    —— 完全用类型类的默认方法（常配合 Generic）
--   deriving via T       —— "我想以 T 的实例语义来实现这个 class"
--
-- 一旦理解这四种，你就能读懂任何现代 Haskell 工程代码。

-- ============================================================
-- Part 2: stock / newtype —— 基础派生
-- ============================================================

-- stock：经典派生
data Point = Point { px :: Double, py :: Double }
  deriving stock (Eq, Show, Generic)

-- newtype 派生：Age 是 Int 的一层包装，但我们希望 Age 也能直接 +、比较、Show
newtype Age = Age Int
  deriving newtype (Show, Eq, Ord, Num)  -- 直接借用 Int 的实例

-- ============================================================
-- Part 3: GHC.Generics —— "类型即数据"
-- ============================================================
--
-- 派生 Generic 后，一个类型会"展开"成一个通用结构（sum / product / field）。
-- 我们可以写一次通用函数，对所有派生 Generic 的类型都适用。

-- 例：通用的"字段数"统计（只数叶子 field 的个数）
class GCountFields f where
  gCount :: f p -> Int

instance GCountFields U1 where                -- 无字段（空构造器）
  gCount _ = 0

instance GCountFields (K1 i c) where          -- 一个叶子字段
  gCount _ = 1

instance (GCountFields a, GCountFields b) => GCountFields (a :*: b) where
  gCount (x :*: y) = gCount x + gCount y

instance (GCountFields a, GCountFields b) => GCountFields (a :+: b) where
  gCount (L1 x) = gCount x
  gCount (R1 y) = gCount y

instance GCountFields a => GCountFields (M1 i c a) where
  gCount (M1 x) = gCount x

-- 对用户可见的接口：
countFields :: (Generic a, GCountFields (Rep a)) => a -> Int
countFields = gCount . from

-- 这样以下所有类型都"免费"获得 countFields：
data Person = Person { pname :: String, page :: Int, paddr :: String }
  deriving (Generic, Show)

data Shape = Circle Double | Square Double | Triangle Double Double Double
  deriving (Generic, Show)

-- ============================================================
-- Part 4: DerivingVia —— "借用别人的实例来实现我的 class"
-- ============================================================
--
-- 场景：我想让 Money 直接支持 Show/Eq/Ord（从 Int 借），
-- 但 Semigroup 不要走 Int 的乘法或加法——
-- 我想明确用 "累加" 还是 "求最大"。

newtype Sum'  a = Sum'  { getSum'  :: a } deriving (Show, Eq)
newtype Max'  a = Max'  { getMax'  :: a } deriving (Show, Eq)

instance Num a => Semigroup (Sum' a) where
  Sum' x <> Sum' y = Sum' (x + y)

instance Ord a => Semigroup (Max' a) where
  Max' x <> Max' y = Max' (max x y)

-- DerivingVia 让我可以"选择"Money 的 Semigroup 要走哪套语义
newtype Money    = Money    Int deriving newtype (Show, Eq, Ord)
  deriving Semigroup via (Sum' Int)   -- 走累加语义

newtype HighScore = HighScore Int deriving newtype (Show, Eq, Ord)
  deriving Semigroup via (Max' Int)   -- 走取最大语义

-- ============================================================
-- Part 5: Identity / Const / Compose —— 抽象适配器三剑客
-- ============================================================
--
-- 这三个东西看起来平平无奇，但它们是 FP 里最重要的"胶水"：
--
--   Identity a        : "只是一层包装"，用于把"有 effect 的接口"
--                      套到"没有 effect"的值上，让同一套代码能复用。
--   Const e a         : "只保留 e，忽略 a"，用于把 Applicative 变成
--                      一个 Monoid 收集器（这就是 view/foldMap 的实现原理）。
--   Compose f g a     : 把两个 Functor 复合成一个 Functor。
--
-- 典型例子：Haskell 的 Lens
--     set  : 用 Identity 复用 traverse
--     view : 用 Const    复用 traverse
--   一套 traverse 分别用不同 Functor 解释就得到了完全不同的行为。

-- 小例：用同一个"遍历函数"，通过换 Functor 得到 sum / head / toList
traverseList :: Applicative f => (a -> f a) -> [a] -> f [a]
traverseList _ []     = pure []
traverseList f (x:xs) = (:) <$> f x <*> traverseList f xs

-- 用 Identity 复用 -> 得到一次 "map"
mapViaTraverse :: (a -> a) -> [a] -> [a]
mapViaTraverse f xs = runIdentity (traverseList (Identity . f) xs)

-- 用 Const [a] 复用 -> 得到一次 "toList / foldMap"
sumViaTraverse :: Num a => [a] -> a
sumViaTraverse = getSum' . getConst . traverseList (\x -> Const (Sum' x))

-- Compose：先 Maybe 再 List（或反之）
type MaybeList a = Compose Maybe [] a

exCompose :: MaybeList Int
exCompose = Compose (Just [1, 2, 3])

-- 可以统一用 fmap，完全不用管下面嵌套了几层
bumpAll :: MaybeList Int -> MaybeList Int
bumpAll = fmap (+ 100)

-- ============================================================
-- Part 6: Deriving 策略组合的典型工程套路
-- ============================================================
--
-- 工程里很常见的一个写法：
--   newtype AppM a = AppM { runAppM :: ReaderT Env IO a }
--     deriving newtype
--       (Functor, Applicative, Monad, MonadReader Env, MonadIO)
--
-- 这就是在说：
--   "AppM 的 Functor/Monad 等等，全部照搬 ReaderT Env IO 的实现。"
-- 这行代码的威力相当于手写几十行实例代码。
--
-- 在我们这个 Demo 里没引入 mtl，不展开，但这是你在真实项目
-- 一定会看到的用法，请记住这个"行"。

-- ============================================================
-- Main
-- ============================================================

main :: IO ()
main = do
  putStrLn "=========================================="
  putStrLn "Demo 20: Generics / DerivingVia / newtype"
  putStrLn "==========================================\n"

  putStrLn "=== 1. stock / newtype 派生 ==="
  let p = Point 1.0 2.0
      a = Age 18 + Age 2
  putStrLn $ "  Point           : " ++ show p
  putStrLn $ "  Age 18 + Age 2  : " ++ show a   -- 因为 Num 实例被借来
  putStrLn ""

  putStrLn "=== 2. GHC.Generics：对任意派生类型通用 ==="
  putStrLn $ "  countFields (Person \"Alice\" 30 \"X\") = "
              ++ show (countFields (Person "Alice" 30 "X-town"))
  putStrLn $ "  countFields (Circle 3.0)   = "
              ++ show (countFields (Circle 3.0))
  putStrLn $ "  countFields (Triangle 3 4 5) = "
              ++ show (countFields (Triangle 3 4 5))
  putStrLn ""

  putStrLn "=== 3. DerivingVia：同一底层类型，不同 Semigroup 语义 ==="
  let m = Money 10 <> Money 20 <> Money 5          -- 累加 -> 35
      h = HighScore 10 <> HighScore 99 <> HighScore 42  -- 取最大 -> 99
  putStrLn $ "  Money  10 <> 20 <> 5       = " ++ show m
  putStrLn $ "  HighScore 10 <> 99 <> 42   = " ++ show h
  putStrLn ""

  putStrLn "=== 4. Identity / Const：一套 traverse 两种解释 ==="
  let xs = [1, 2, 3, 4, 5] :: [Int]
  putStrLn $ "  mapViaTraverse (+10) xs = " ++ show (mapViaTraverse (+ 10) xs)
  putStrLn $ "  sumViaTraverse       xs = " ++ show (sumViaTraverse xs)
  putStrLn ""

  putStrLn "=== 5. Compose：两层 Functor 当一层用 ==="
  let Compose mxs = bumpAll exCompose
  putStrLn $ "  bumpAll (Just [1,2,3]) = " ++ show mxs
  putStrLn ""

  putStrLn "=== 知识点小结 ==="
  putStrLn "  * stock / newtype / anyclass / via —— 四种 deriving 策略要分清"
  putStrLn "  * Generic  让 '类型结构' 本身变成可编程的对象"
  putStrLn "  * DerivingVia 是 Haskell 代码复用的核武器"
  putStrLn "  * Identity / Const / Compose：lens / foldMap / generic JSON 的幕后英雄"
  putStrLn "  * 工程套路：newtype AppM = ... deriving newtype (Monad, MonadReader, MonadIO)"
  putStrLn "=========================================="
