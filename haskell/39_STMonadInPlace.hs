{-# LANGUAGE RankNTypes #-}

-- ============================================================
-- Demo 39: ST Monad 局部可变（批次 4）
--
-- ST 是 Haskell 的"局部可变逃生舱"：
--   * 内部可以 new/read/write STRef、STArray，做原地更新写高性能算法
--   * runST 的签名是 runST :: (forall s. ST s a) -> a
--     —— 那个"显眼"的 forall s. 就是秘诀：它让编译器拒绝任何
--        把 STRef / STArray 逃出 runST 外面的程序。
--   * 所以对外看 runST (...) 就是一个**纯**值，但内部实际是原地算的。
--
-- 本 Demo 三件事：
--   1) IORef / STRef 基础对比
--   2) 原地计数排序：O(n+k)，对外纯
--   3) 原地快排：可变数组 + 分区，对外纯
--
-- 依赖：base + array（GHC boot 自带）
-- 运行：runghc 39_STMonadInPlace.hs
-- ============================================================

module Main where

import Control.Monad           (forM_, when, unless)
import Control.Monad.ST        (ST, runST)
import Data.STRef              (STRef, newSTRef, readSTRef, writeSTRef, modifySTRef')
import Data.Array.ST           ( STArray, newArray, readArray, writeArray
                               , getBounds, freeze)
import Data.Array              (Array, (!), bounds, elems)

-- ============================================================
-- Part 1: STRef —— "纯外壳下的局部可变单元"
-- ============================================================
-- 对比 IORef：
--   IORef 只能活在 IO 里，读出来永远是 IO a
--   STRef 活在 ST s 里，配合 runST，整个计算对外纯

-- 纯求和，但内部用了原地累加
sumPure :: [Int] -> Int
sumPure xs = runST $ do
  acc <- newSTRef 0
  forM_ xs $ \x -> modifySTRef' acc (+ x)
  readSTRef acc

-- 纯计数：一次遍历同时数个数和总和
countAndSum :: [Int] -> (Int, Int)
countAndSum xs = runST $ do
  cnt <- newSTRef 0
  acc <- newSTRef 0
  forM_ xs $ \x -> do
    modifySTRef' cnt (+ 1)
    modifySTRef' acc (+ x)
  (,) <$> readSTRef cnt <*> readSTRef acc

-- ============================================================
-- Part 2: 原地计数排序
--   * 输入 [Int]，所有元素在 [0..k] 范围内
--   * O(n + k) 时间，O(k) 辅助空间
--   * 对外签名是 [Int] -> [Int]（纯！）
-- ============================================================

countingSort :: Int -> [Int] -> [Int]
countingSort k xs = runST $ do
  -- 分配一个可变数组做桶
  buckets <- newArray (0, k) 0 :: ST s (STArray s Int Int)
  -- 计数
  forM_ xs $ \x -> do
    c <- readArray buckets x
    writeArray buckets x (c + 1)
  -- 按桶顺序展开成结果列表（也在 ST 里拼）
  resultRef <- newSTRef []
  forM_ [k, k - 1 .. 0] $ \i -> do
    c <- readArray buckets i
    when (c > 0) $
      modifySTRef' resultRef (replicate c i ++)
  readSTRef resultRef

-- ============================================================
-- Part 3: 原地快排（in-place Lomuto partition）
-- ============================================================

-- 把 [a] 搬进 STArray，原地排完后 freeze 出不可变 Array，再转 list
quicksortST :: Ord a => [a] -> [a]
quicksortST xs = elems $ runST $ do
  let n = length xs
  arr <- newArrayFromList xs
  when (n > 1) $ qsort arr 0 (n - 1)
  freeze arr

-- 辅助：从列表建 STArray
newArrayFromList :: [a] -> ST s (STArray s Int a)
newArrayFromList ys = do
  let n = length ys
  arr <- newArray (0, n - 1) (error "unfilled")
  forM_ (zip [0 ..] ys) $ \(i, y) -> writeArray arr i y
  pure arr

qsort :: Ord a => STArray s Int a -> Int -> Int -> ST s ()
qsort arr lo hi =
  when (lo < hi) $ do
    p <- partition arr lo hi
    qsort arr lo (p - 1)
    qsort arr (p + 1) hi

partition :: Ord a => STArray s Int a -> Int -> Int -> ST s Int
partition arr lo hi = do
  pivot <- readArray arr hi
  iRef  <- newSTRef (lo - 1)
  forM_ [lo .. hi - 1] $ \j -> do
    v <- readArray arr j
    unless (v > pivot) $ do
      modifySTRef' iRef (+ 1)
      i <- readSTRef iRef
      swap arr i j
  modifySTRef' iRef (+ 1)
  i <- readSTRef iRef
  swap arr i hi
  pure i

swap :: STArray s Int a -> Int -> Int -> ST s ()
swap arr i j = do
  x <- readArray arr i
  y <- readArray arr j
  writeArray arr i y
  writeArray arr j x

-- ============================================================
-- Part 4: 为什么不会"泄露"？—— 类型系统的保证
-- ============================================================
-- 下面这个函数若取消注释，会被 GHC 拒绝：
--
--   leak :: STRef ? Int     -- 我们找不到一个 s 能写下去
--   leak = runST (newSTRef 42)
--
-- 理由：runST :: (forall s. ST s a) -> a
-- 里面的 s 是被 runST "锁死"的幻影类型，STRef s Int 根本逃不出去。

main :: IO ()
main = do
  putStrLn "Demo 39: ST Monad 局部可变"
  putStrLn "-----------------------------------"
  let xs = [5, 1, 4, 2, 8, 3, 7, 6, 2, 5]

  putStrLn "Part 1: STRef 纯外壳下累加"
  putStrLn $ "  sumPure "        ++ show xs ++ " = " ++ show (sumPure xs)
  putStrLn $ "  countAndSum "    ++ show xs ++ " = " ++ show (countAndSum xs)

  putStrLn ""
  putStrLn "Part 2: 原地计数排序"
  putStrLn $ "  countingSort 10 " ++ show xs ++ " = " ++ show (countingSort 10 xs)

  putStrLn ""
  putStrLn "Part 3: 原地快排"
  putStrLn $ "  quicksortST "     ++ show xs ++ " = " ++ show (quicksortST xs)
  putStrLn $ "  quicksortST [\"banana\",\"apple\",\"cherry\"] = "
           ++ show (quicksortST ["banana", "apple", "cherry"])

  putStrLn ""
  putStrLn "对外签名都是纯函数 [a] -> [a]，但内部是原地更新。"
  putStrLn "runST :: (forall s. ST s a) -> a 的 rank-2 类型把 STRef 关在里面。"
