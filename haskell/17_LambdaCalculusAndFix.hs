-- ============================================================
-- Demo 17: Lambda 演算 / fix / 折叠同构
--          —— FP 的"数学底座"，所有语法糖都能追到这里
--
-- 这个 Demo 面向想彻底理解 "函数式到底在做什么" 的人：
--   * 为什么说 "函数就是一切"（Church encoding）
--   * 匿名递归：没有 let rec 也能递归（fix / Y-combinator）
--   * catamorphism / anamorphism：把"结构的递归"和"递归的逻辑"分开
--   * 为什么 foldr 是 List 的"唯一范畴论正统 eliminator"
--
-- 运行：runhaskell 17_LambdaCalculusAndFix.hs
-- ============================================================

module Main where

import Data.Function (fix)

-- ============================================================
-- Part 1: Church Encoding —— "只有函数" 也够用了
-- ============================================================
--
-- 在纯 lambda 演算里，连数字、布尔值、pair 都不存在，
-- 所有东西都必须用 "函数" 编码出来。
-- 下面这几行就是 Alonzo Church 在 1930 年代做的事。

-- 布尔值：true/false 本质是"二选一"的策略
type CBool = forall r. r -> r -> r
-- Haskell 里没有 ImpredicativeTypes 默认开，所以用具体 Int 例子：
cTrue, cFalse :: a -> a -> a
cTrue  t _ = t
cFalse _ f = f

-- 把 Church 布尔值变回 Haskell Bool
toBool :: (Bool -> Bool -> Bool) -> Bool
toBool b = b True False

-- 自然数：数字 n 就是 "把 f 连用 n 次"
cZero, cOne, cTwo, cThree :: (a -> a) -> a -> a
cZero  _ x = x
cOne   f x = f x
cTwo   f x = f (f x)
cThree f x = f (f (f x))

cSucc :: ((a -> a) -> a -> a) -> (a -> a) -> a -> a
cSucc n f x = f (n f x)

cAdd, cMul :: ((a -> a) -> a -> a)
          -> ((a -> a) -> a -> a)
          -> (a -> a) -> a -> a
cAdd m n f x = m f (n f x)
cMul m n f x = m (n f) x

-- 把 Church 数字变回 Haskell Int
toInt :: ((Int -> Int) -> Int -> Int) -> Int
toInt n = n (+ 1) 0

-- Pair：一个 pair 就是"接收选择器"的函数
cPair :: a -> b -> ((a -> b -> c) -> c)
cPair a b = \sel -> sel a b

cFst :: ((a -> b -> a) -> c) -> c
cFst p = p (\a _ -> a)

cSnd :: ((a -> b -> b) -> c) -> c
cSnd p = p (\_ b -> b)

-- ============================================================
-- Part 2: 没有 let rec 也能递归 —— fix 与 Y 组合子
-- ============================================================
--
-- 纯 lambda 演算里函数是匿名的，没办法自己引用自己。
-- 解决方案：让 "递归" 成为一个高阶函数的参数 —— 这就是 fix。
--
--   fix :: (a -> a) -> a
--   fix f = let x = f x in x
--
-- 直觉：fix 给你一个"回头引用自己的名字"。

-- 不使用显式递归写阶乘：
factFix :: Int -> Int
factFix = fix $ \rec n -> if n <= 1 then 1 else n * rec (n - 1)

-- 不使用显式递归写 fib：
fibFix :: Int -> Int
fibFix = fix $ \rec n ->
  if n < 2 then n else rec (n - 1) + rec (n - 2)

-- 不使用显式递归写 map：
mapFix :: (a -> b) -> [a] -> [b]
mapFix f = fix $ \rec xs -> case xs of
  []     -> []
  (y:ys) -> f y : rec ys

-- Y 组合子（用 Haskell 类型系统要一点点 trick，这里演示等价写法）：
--   Y = λf. (λx. f (x x)) (λx. f (x x))
-- Haskell 里类型系统不允许自我应用的 x x，所以我们用 newtype 绕开：
newtype Mu a = Mu (Mu a -> a)

y :: (a -> a) -> a
y f = (\(Mu x) -> f (x (Mu x))) (Mu (\(Mu x) -> f (x (Mu x))))

factY :: Int -> Int
factY = y (\rec n -> if n <= 1 then 1 else n * rec (n - 1))

-- ============================================================
-- Part 3: 折叠同构 —— foldr 是 List 的"范畴论 eliminator"
-- ============================================================
--
-- List 的定义可以看成：
--   data List a = Nil | Cons a (List a)
--
-- 所以要"消费"一个 List，只需提供两件事：
--   * Nil 时返回什么？（一个 b）
--   * Cons x xs 时如何把 x 和已经折完的 xs 合起来？（一个 a -> b -> b）
-- 这就是 foldr 的签名：foldr :: (a -> b -> b) -> b -> [a] -> b
--
-- 几乎所有 List 操作都能用 foldr 写出来，说明 foldr 是"足够强的 eliminator"。

sumF, prodF, lenF :: Num a => [a] -> a
sumF  = foldr (+) 0
prodF = foldr (*) 1
lenF  = foldr (\_ acc -> acc + 1) 0

mapF :: (a -> b) -> [a] -> [b]
mapF f = foldr (\x acc -> f x : acc) []

filterF :: (a -> Bool) -> [a] -> [a]
filterF p = foldr (\x acc -> if p x then x : acc else acc) []

-- 甚至 foldl 也能用 foldr 写（经典题 "left fold via right fold"）
foldlViaFoldr :: (b -> a -> b) -> b -> [a] -> b
foldlViaFoldr f z xs = foldr (\x g acc -> g (f acc x)) id xs z

-- ============================================================
-- Part 4: catamorphism / anamorphism —— "消费 / 生产"的对偶
-- ============================================================
--
-- * catamorphism (cata): 把一个递归结构"折"成一个值（= generalised foldr）
-- * anamorphism  (ana):  从一个种子"展开"出一个递归结构（= generalised unfold）
-- * hylomorphism (hylo): ana 后立刻 cata，中间结构不落地（融合）
--
-- 这里只用 List 举例，理解完后可以推广到任意 Functor / Fixpoint。

-- 用 unfold 生成 [n, n-1, ..., 1]
countDown :: Int -> [Int]
countDown = ana
  where
    ana 0 = []
    ana n = n : ana (n - 1)

-- hylo: n -> [n, n-1, ..., 1] -> n!
-- 注意：Haskell 会把中间列表"流式"消费掉，不会真的分配 n 个 cons。
factHylo :: Int -> Int
factHylo = product . countDown

-- ============================================================
-- Part 5: eta-reduction / beta-reduction 的实用痕迹
-- ============================================================
--
-- * beta:  (\x -> e) a   ==>  e[x := a]
-- * eta:   \x -> f x     ==>  f     （当 x 不在 f 中自由出现）
--
-- 写 point-free 风格时你就是在做 eta-reduction。

sumOfSquares :: [Int] -> Int
sumOfSquares = sum . map (^ (2 :: Int))    -- 这本身就是 eta-reduced 的

-- ============================================================
-- Main
-- ============================================================

main :: IO ()
main = do
  putStrLn "=========================================="
  putStrLn "Demo 17: Lambda 演算 / fix / 折叠同构"
  putStrLn "==========================================\n"

  -- ---- Church encoding ----
  putStrLn "=== 1. Church Encoding：只用函数就够了 ==="
  putStrLn $ "  cTrue  True False  = " ++ show (cTrue  True False)
  putStrLn $ "  cFalse True False  = " ++ show (cFalse True False)
  putStrLn $ "  toInt cZero        = " ++ show (toInt cZero)
  putStrLn $ "  toInt cThree       = " ++ show (toInt cThree)
  putStrLn $ "  toInt (cSucc cTwo) = " ++ show (toInt (cSucc cTwo))
  putStrLn $ "  toInt (cAdd cTwo cThree) = " ++ show (toInt (cAdd cTwo cThree))
  putStrLn $ "  toInt (cMul cTwo cThree) = " ++ show (toInt (cMul cTwo cThree))
  let p = cPair (10 :: Int) "hello"
  putStrLn $ "  cFst pair = " ++ show (cFst p)
  putStrLn $ "  cSnd pair = " ++ show (cSnd p)
  putStrLn ""

  -- ---- fix ----
  putStrLn "=== 2. fix：匿名递归 ==="
  putStrLn $ "  factFix 6 = " ++ show (factFix 6)
  putStrLn $ "  fibFix 10 = " ++ show (fibFix 10)
  putStrLn $ "  mapFix (*2) [1..5] = " ++ show (mapFix (* 2) [1 .. 5 :: Int])
  putStrLn $ "  factY 6 (手写 Y)  = " ++ show (factY 6)
  putStrLn ""

  -- ---- foldr 作为唯一 eliminator ----
  putStrLn "=== 3. 用 foldr 派生一切 ==="
  let xs = [1, 2, 3, 4, 5] :: [Int]
  putStrLn $ "  sumF         = " ++ show (sumF xs)
  putStrLn $ "  prodF        = " ++ show (prodF xs)
  putStrLn $ "  lenF         = " ++ show (lenF xs)
  putStrLn $ "  mapF (*10)   = " ++ show (mapF (* 10) xs)
  putStrLn $ "  filterF even = " ++ show (filterF even xs)
  putStrLn $ "  foldlViaFoldr (+) 0 [1..100] = "
              ++ show (foldlViaFoldr (+) 0 [1 .. 100 :: Int])
  putStrLn ""

  -- ---- ana / hylo ----
  putStrLn "=== 4. ana / cata / hylo：生产 / 消费 / 融合 ==="
  putStrLn $ "  countDown 5  = " ++ show (countDown 5)
  putStrLn $ "  factHylo 6   = " ++ show (factHylo 6)
  putStrLn ""

  -- ---- eta-reduction ----
  putStrLn "=== 5. point-free = eta-reduction 的实用化 ==="
  putStrLn $ "  sumOfSquares [1..5] = " ++ show (sumOfSquares [1 .. 5])
  putStrLn ""

  -- ---- 知识点小结 ----
  putStrLn "=== 知识点小结 ==="
  putStrLn "  * Church encoding ：函数 + application 足以编码一切数据"
  putStrLn "  * fix            ：匿名递归的"官方做法"，也是 let rec 的语义基础"
  putStrLn "  * foldr          ：List 的范畴论 eliminator，其它操作都是它的特例"
  putStrLn "  * ana / cata     ：生产 / 消费 递归结构；hylo = 两者融合"
  putStrLn "  * eta / beta     ：所有 FP 语法糖归根到底是这两步 reduction"
  putStrLn "=========================================="
