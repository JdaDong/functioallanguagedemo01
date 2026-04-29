{-
  Haskell 函数式编程 Demo 1: 纯函数与惰性求值

  Haskell 是纯函数式语言的代表——所有函数都是纯函数（无副作用），
  并且默认使用惰性求值（Lazy Evaluation），即表达式只在需要时才被计算。
-}

module Main where

-- ========== 纯函数: 相同输入永远返回相同输出 ==========

-- 温度转换
celsiusToFahrenheit :: Double -> Double
celsiusToFahrenheit c = c * 9 / 5 + 32

fahrenheitToCelsius :: Double -> Double
fahrenheitToCelsius f = (f - 32) * 5 / 9

-- BMI 计算器
bmi :: Double -> Double -> String
bmi weight height
  | bmiValue < 18.5 = "偏瘦 (BMI: " ++ show bmiValue ++ ")"
  | bmiValue < 25.0 = "正常 (BMI: " ++ show bmiValue ++ ")"
  | bmiValue < 30.0 = "偏胖 (BMI: " ++ show bmiValue ++ ")"
  | otherwise        = "肥胖 (BMI: " ++ show bmiValue ++ ")"
  where bmiValue = weight / (height ^ 2)

-- ========== 惰性求值: 无限列表 ==========

-- 所有自然数 (无限列表!)
naturals :: [Integer]
naturals = [0..]

-- 所有偶数
evens :: [Integer]
evens = [0, 2..]

-- 斐波那契数列 (经典的惰性定义)
fibs :: [Integer]
fibs = 0 : 1 : zipWith (+) fibs (tail fibs)

-- 素数筛 (埃拉托斯特尼筛法) - 无限素数列表
primes :: [Integer]
primes = sieve [2..]
  where
    sieve (p:xs) = p : sieve [x | x <- xs, x `mod` p /= 0]

-- ========== 列表操作 ==========

-- 自定义 map
myMap :: (a -> b) -> [a] -> [b]
myMap _ []     = []
myMap f (x:xs) = f x : myMap f xs

-- 自定义 filter
myFilter :: (a -> Bool) -> [a] -> [a]
myFilter _ []     = []
myFilter p (x:xs)
  | p x       = x : myFilter p xs
  | otherwise  = myFilter p xs

-- 自定义 foldr
myFoldr :: (a -> b -> b) -> b -> [a] -> b
myFoldr _ acc []     = acc
myFoldr f acc (x:xs) = f x (myFoldr f acc xs)

-- ========== 主函数 ==========

main :: IO ()
main = do
  putStrLn "=== 纯函数 ==="
  putStrLn $ "100°F = " ++ show (fahrenheitToCelsius 100) ++ "°C"
  putStrLn $ "37°C  = " ++ show (celsiusToFahrenheit 37) ++ "°F"
  putStrLn $ "BMI(70kg, 1.75m): " ++ bmi 70 1.75

  putStrLn "\n=== 惰性求值: 无限列表 ==="
  putStrLn $ "前10个自然数: " ++ show (take 10 naturals)
  putStrLn $ "前10个偶数:   " ++ show (take 10 evens)
  putStrLn $ "前15个斐波那契: " ++ show (take 15 fibs)
  putStrLn $ "前20个素数: " ++ show (take 20 primes)

  putStrLn "\n=== 列表操作 ==="
  let nums = [1..10]
  putStrLn $ "原始列表:     " ++ show nums
  putStrLn $ "myMap (*2):   " ++ show (myMap (*2) nums)
  putStrLn $ "myFilter even:" ++ show (myFilter even nums)
  putStrLn $ "myFoldr (+):  " ++ show (myFoldr (+) 0 nums)

  putStrLn "\n=== 列表推导 ==="
  let pythagorean = [(a,b,c) | c <- [1..25], b <- [1..c], a <- [1..b],
                                a*a + b*b == c*c]
  putStrLn $ "勾股数: " ++ show pythagorean
