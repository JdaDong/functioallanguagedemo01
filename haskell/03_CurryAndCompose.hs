{-
  Haskell 函数式编程 Demo 3: 柯里化与函数组合

  Haskell 中所有函数默认都是柯里化的（每个函数只接受一个参数）。
  函数组合 (.) 和管道式编程是 Haskell 最优雅的特性之一。
  "Point-free style" (无参风格) 是 Haskell 程序员的标志性写法。
-}

module Main where

import Data.Char (toUpper, toLower, isAlpha, isDigit, ord)
import Data.List (sort, group, sortBy, groupBy, intercalate)
import Data.Ord (comparing, Down(..))

-- ========== 柯里化 (Currying) ==========

-- Haskell 中所有函数天然就是柯里化的
-- add :: Int -> Int -> Int  实际上是  add :: Int -> (Int -> Int)
add :: Int -> Int -> Int
add x y = x + y

-- 部分应用: 固定一个参数，得到新函数
add5 :: Int -> Int
add5 = add 5

add10 :: Int -> Int
add10 = add 10

-- 更多部分应用的例子
isPositive :: (Ord a, Num a) => a -> Bool
isPositive = (> 0)

double :: Num a => a -> a
double = (* 2)

halve :: Fractional a => a -> a
halve = (/ 2)

-- ========== 函数组合 (.) ==========

-- (.) :: (b -> c) -> (a -> b) -> a -> c
-- (f . g) x = f (g x)

-- 将字符串中的单词首字母大写
capitalize :: String -> String
capitalize [] = []
capitalize (x:xs) = toUpper x : xs

-- 使用函数组合构建处理管道
titleCase :: String -> String
titleCase = unwords . map capitalize . words

-- 统计文本中的字母频率 (完全用函数组合实现)
letterFrequency :: String -> [(Char, Int)]
letterFrequency = sortBy (comparing (Down . snd))  -- 按频率降序
               . map (\g -> (head g, length g))     -- 转为 (字符, 频率)
               . group                               -- 分组
               . sort                                -- 排序
               . map toLower                         -- 转小写
               . filter isAlpha                      -- 只保留字母

-- ========== Point-Free Style ==========

-- 有参数的写法
sumOfSquaresVerbose :: [Int] -> Int
sumOfSquaresVerbose xs = sum (map (^2) xs)

-- Point-free 写法 (去掉参数)
sumOfSquares :: [Int] -> Int
sumOfSquares = sum . map (^2)

-- 更多 point-free 示例
countEvens :: [Int] -> Int
countEvens = length . filter even

average :: [Double] -> Double
average = (/) <$> sum <*> (fromIntegral . length)

-- ========== 高阶函数组合器 ==========

-- on: 对两个参数先做相同变换，再用二元函数合并
on :: (b -> b -> c) -> (a -> b) -> a -> a -> c
on f g x y = f (g x) (g y)

-- 按绝对值比较
compareByAbs :: Int -> Int -> Ordering
compareByAbs = compare `Main.on` abs

-- flip: 翻转参数顺序
myFlip :: (a -> b -> c) -> b -> a -> c
myFlip f x y = f y x

-- 实用示例: 用 flip 创建新函数
contains :: Eq a => [a] -> a -> Bool
contains = myFlip elem

-- ========== 实战: 文本处理管道 ==========

-- Caesar 密码 (函数组合实现)
caesarEncrypt :: Int -> String -> String
caesarEncrypt shift = map encrypt
  where
    encrypt c
      | isAlpha c = let base = if c < 'a' then ord 'A' else ord 'a'
                        shifted = (ord c - base + shift) `mod` 26 + base
                    in toEnum shifted
      | otherwise = c

caesarDecrypt :: Int -> String -> String
caesarDecrypt shift = caesarEncrypt (26 - shift)

-- ========== 主函数 ==========

main :: IO ()
main = do
  putStrLn "=== 柯里化与部分应用 ==="
  putStrLn $ "add5(3)  = " ++ show (add5 3)
  putStrLn $ "add10(3) = " ++ show (add10 3)
  putStrLn $ "map add5 [1..5] = " ++ show (map add5 [1,2,3,4,5])
  putStrLn $ "filter isPositive [-3..3] = " ++ show (filter isPositive [-3..3])

  putStrLn "\n=== 函数组合 ==="
  putStrLn $ "titleCase: " ++ titleCase "hello functional world"
  putStrLn $ "letterFrequency 'hello world': " ++ show (letterFrequency "hello world")

  putStrLn "\n=== Point-Free Style ==="
  putStrLn $ "sumOfSquares [1..5] = " ++ show (sumOfSquares [1,2,3,4,5])
  putStrLn $ "countEvens [1..10]  = " ++ show (countEvens [1..10])

  putStrLn "\n=== 高阶组合器 ==="
  let nums = [3, -1, 4, -1, 5, -9, 2, 6]
  putStrLn $ "原始列表:     " ++ show nums
  putStrLn $ "按绝对值排序: " ++ show (sortBy compareByAbs nums)
  putStrLn $ "[1..5] contains 3? " ++ show (contains [1..5] 3)
  putStrLn $ "[1..5] contains 7? " ++ show (contains [1..5] 7)

  putStrLn "\n=== Caesar 密码 ==="
  let message = "Hello Haskell"
  let encrypted = caesarEncrypt 3 message
  let decrypted = caesarDecrypt 3 encrypted
  putStrLn $ "原文:   " ++ message
  putStrLn $ "加密:   " ++ encrypted
  putStrLn $ "解密:   " ++ decrypted
