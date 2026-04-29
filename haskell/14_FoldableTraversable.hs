-- ============================================================
-- Demo 14: Foldable & Traversable — 遍历与聚合的统一抽象
--
-- 为什么重要：
--   * Functor     告诉我们："结构不变，只变内容"         —— fmap
--   * Foldable    告诉我们："任何结构都能被聚合成一个值" —— foldr / foldMap
--   * Traversable 告诉我们："结构化遍历 + effect 编织"   —— traverse / sequenceA
--
--   Traversable 是 FP 里被低估的核心抽象：
--     [IO a]  -> IO [a]
--     [Maybe a] -> Maybe [a]
--     Tree (Either e a) -> Either e (Tree a)
--   都只是同一个 sequence / traverse 的不同特例。
--
-- 这个 Demo 不依赖任何外部库，只用 base 和 Data.Foldable / Data.Traversable。
-- 运行：  runhaskell 14_FoldableTraversable.hs
-- ============================================================

module Main where

import           Data.Foldable    (Foldable (..), toList, for_)
import           Data.Traversable (Traversable (..), for)
import           Data.Monoid      (Sum (..), Product (..), All (..), Any (..))
import qualified Data.Map.Strict  as Map

-- ============================================================
-- Part 1: 回顾 Foldable —— "能够被压扁成一个值的容器"
-- ============================================================
--
-- 核心方法（只要实现 foldr 或 foldMap 其中之一，其他都能派生出来）：
--   foldr    :: (a -> b -> b) -> b -> t a -> b
--   foldMap  :: Monoid m => (a -> m) -> t a -> m
--   toList   :: t a -> [a]
--   length / null / elem / sum / product / maximum / minimum
--
-- 直觉：Foldable 表达的其实就是 "遍历并用 Monoid 组合"。

-- 用 Monoid 实现各种统计：
sumViaFoldMap :: (Foldable t, Num a) => t a -> a
sumViaFoldMap = getSum . foldMap Sum

productViaFoldMap :: (Foldable t, Num a) => t a -> a
productViaFoldMap = getProduct . foldMap Product

allPositive :: (Foldable t, Num a, Ord a) => t a -> Bool
allPositive = getAll . foldMap (All . (> 0))

anyNegative :: (Foldable t, Num a, Ord a) => t a -> Bool
anyNegative = getAny . foldMap (Any . (< 0))

-- 统计平均值（需要同时拿到 sum 和 count）：
-- 这里体现 foldMap 的威力：一遍遍历同时算多个聚合量。
avg :: (Foldable t, Fractional a) => t a -> a
avg xs =
  let (Sum s, Sum n) = foldMap (\x -> (Sum x, Sum 1)) xs
  in  s / n

-- ============================================================
-- Part 2: 自定义 Foldable —— 二叉树
-- ============================================================

data Tree a = Leaf | Node (Tree a) a (Tree a)
  deriving Show

-- Functor 实例：结构不变，每个值用 f 映射
instance Functor Tree where
  fmap _ Leaf         = Leaf
  fmap f (Node l x r) = Node (fmap f l) (f x) (fmap f r)

-- Foldable 实例：中序遍历并用 Monoid 组合
instance Foldable Tree where
  foldMap _ Leaf         = mempty
  foldMap f (Node l x r) = foldMap f l <> f x <> foldMap f r

-- 测试用的小树：
--        5
--       / \
--      3   8
--     / \   \
--    1   4   9
sample :: Tree Int
sample =
  Node
    (Node (Node Leaf 1 Leaf) 3 (Node Leaf 4 Leaf))
    5
    (Node Leaf 8 (Node Leaf 9 Leaf))

-- ============================================================
-- Part 3: Traversable —— 在遍历中"编织"effect
-- ============================================================
--
-- 核心方法：
--   traverse  :: Applicative f => (a -> f b) -> t a -> f (t b)
--   sequenceA ::                   t (f a) -> f (t a)
--
-- 直觉：
--   * fmap     :: (a -> b)   -> t a -> t b           只变内容
--   * traverse :: (a -> f b) -> t a -> f (t b)       变内容 + 把 effect 提出来
--
-- 经典例子：
--   traverse safeDiv [10, 20, 30]  ==> Just [..] 或者 Nothing（只要有一个失败就整体失败）

safeDiv :: Int -> Int -> Maybe Int
safeDiv _ 0 = Nothing
safeDiv x y = Just (x `div` y)

-- 对列表里每个除数都尝试除 100，只要一个失败整条失败
divAll :: [Int] -> Maybe [Int]
divAll = traverse (safeDiv 100)

-- Traversable 也适用于自定义结构
instance Traversable Tree where
  traverse _ Leaf         = pure Leaf
  traverse f (Node l x r) = Node <$> traverse f l <*> f x <*> traverse f r

-- 把一棵"可能失败"的树，变成"整棵树要么全部成功要么整体失败"
validateTree :: Tree Int -> Maybe (Tree Int)
validateTree = traverse onlyPositive
  where
    onlyPositive n
      | n > 0     = Just n
      | otherwise = Nothing

-- ============================================================
-- Part 4: Traversable + 状态 —— 给树节点编号
-- ============================================================
--
-- 这是函数式编程里非常经典的题目。
-- 用手写状态（不引入 Control.Monad.State）也能做：
--   把 "编号" 做成一个 (Int -> (Int, b)) 的小状态函数，
--   用 Applicative 组合起来就是 Traversable 的本质。
--
-- 这里直接用 IO / (,) 都可以；下面用 IO Ref 风格也行，
-- 但最能体现 FP 的是：用 State Monad。为了让本 Demo 不依赖 mtl，
-- 下面用"手写 State"来体现核心原理。

newtype S s a = S { runS :: s -> (a, s) }

instance Functor (S s) where
  fmap f (S g) = S $ \s -> let (a, s') = g s in (f a, s')

instance Applicative (S s) where
  pure a      = S $ \s -> (a, s)
  S fs <*> S xs = S $ \s ->
    let (f, s1) = fs s
        (x, s2) = xs s1
    in  (f x, s2)

fresh :: S Int Int
fresh = S $ \n -> (n, n + 1)

-- 给树的每个节点配一个递增编号：O(n)，一次中序遍历搞定。
labelTree :: Tree a -> Tree (Int, a)
labelTree t = fst (runS (traverse withLabel t) 0)
  where
    withLabel x = (,) <$> fresh <*> pure x

-- ============================================================
-- Part 5: Traversable + IO —— 批量 effect
-- ============================================================
-- 常见模式：
--   传一堆 id，每个去拉详情，最终得到一堆详情。
--   这正是 traverse 的典型场景，不需要手写 mapM_ + accumulator。

fakeFetch :: Int -> IO String
fakeFetch n = do
  -- 假装去"远程"拉一条记录
  putStrLn $ "  [fetch] id=" ++ show n
  pure ("record-" ++ show n)

fetchAll :: [Int] -> IO [String]
fetchAll = traverse fakeFetch

-- ============================================================
-- Part 6: 与 Map / 元组 / Either 等常见结构配合
-- ============================================================
-- 关键记忆点：
--   * Map k  是 Functor / Foldable / Traversable —— 只看 value 这一侧
--   * (e, _) 是 Foldable / Traversable，但只遍历 snd
--   * Either e 类似，只在 Right 方向遍历
--   * Map.traverseWithKey 在需要同时用到 key 时非常有用

validateScores :: Map.Map String Int -> Either String (Map.Map String Int)
validateScores =
  Map.traverseWithKey $ \name score ->
    if score >= 0 && score <= 100
      then Right score
      else Left ("score out of range for " ++ name ++ ": " ++ show score)

-- ============================================================
-- Main
-- ============================================================

main :: IO ()
main = do
  putStrLn "=========================================="
  putStrLn "Demo 14: Foldable & Traversable"
  putStrLn "==========================================\n"

  -- ---- Foldable 基础 ----
  let xs = [1, 2, 3, 4, 5] :: [Int]
  putStrLn "=== 1. Foldable: 用 foldMap 做各种聚合 ==="
  putStrLn $ "  sumViaFoldMap     = " ++ show (sumViaFoldMap xs)
  putStrLn $ "  productViaFoldMap = " ++ show (productViaFoldMap xs)
  putStrLn $ "  allPositive       = " ++ show (allPositive xs)
  putStrLn $ "  anyNegative       = " ++ show (anyNegative xs)
  putStrLn $ "  avg               = " ++ show (avg (map fromIntegral xs :: [Double]))
  putStrLn ""

  -- ---- 自定义 Foldable 实例 ----
  putStrLn "=== 2. 自定义 Tree 的 Foldable ==="
  putStrLn $ "  toList sample     = " ++ show (toList sample)
  putStrLn $ "  sum    sample     = " ++ show (sum sample)
  putStrLn $ "  length sample     = " ++ show (length sample)
  putStrLn $ "  maximum sample    = " ++ show (maximum sample)
  putStrLn $ "  elem 4 sample     = " ++ show (elem 4 sample)
  putStrLn ""

  -- ---- Traversable: 批量 Maybe ----
  putStrLn "=== 3. Traversable: 失败短路 ==="
  putStrLn $ "  divAll [2,5,10]   = " ++ show (divAll [2, 5, 10])
  putStrLn $ "  divAll [2,0,10]   = " ++ show (divAll [2, 0, 10])
  putStrLn ""

  -- ---- Traversable + 自定义结构 ----
  putStrLn "=== 4. Traversable: 验证整棵树 ==="
  putStrLn $ "  validateTree sample         = " ++ show (validateTree sample)
  let bad = Node Leaf (-1) (Node Leaf 2 Leaf)
  putStrLn $ "  validateTree bad            = " ++ show (validateTree bad)
  putStrLn ""

  -- ---- Traversable + State: 编号 ----
  putStrLn "=== 5. Traversable + 手写 State: 给树节点编号 ==="
  putStrLn $ "  labelTree sample = " ++ show (labelTree sample)
  putStrLn ""

  -- ---- Traversable + IO: 批量拉数据 ----
  putStrLn "=== 6. Traversable + IO: 批量 effect ==="
  records <- fetchAll [101, 102, 103]
  putStrLn $ "  records = " ++ show records
  putStrLn ""

  -- ---- Map traverseWithKey ----
  putStrLn "=== 7. Map.traverseWithKey: 带 key 的校验 ==="
  let good = Map.fromList [("alice", 90), ("bob", 75)]
      badM = Map.fromList [("alice", 90), ("bob", 150)]
  putStrLn $ "  validateScores good = " ++ show (validateScores good)
  putStrLn $ "  validateScores bad  = " ++ show (validateScores badM)
  putStrLn ""

  -- ---- 小结 ----
  putStrLn "=== 知识点小结 ==="
  putStrLn "  * Foldable    = 用 Monoid 把结构聚合成单个值"
  putStrLn "  * Traversable = 在保持结构的前提下，把 effect 从内部抽到外部"
  putStrLn "  * traverse f  = fmap f 的带 effect 版本"
  putStrLn "  * sequenceA   = traverse id"
  putStrLn "  * 只要你写过 mapM / forM / sequence，你就已经在用 Traversable 了"
  putStrLn "=========================================="
