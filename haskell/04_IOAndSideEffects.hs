{-
  Haskell 函数式编程 Demo 4: IO Monad 与副作用管理

  Haskell 用类型系统把"有副作用的计算"和"纯计算"彻底分开：
  - 纯函数：返回 a
  - 有副作用：返回 IO a  (描述一个动作，但不立即执行)

  IO 是 Monad，所以可以用 do 表示法把多个 IO 动作串起来。
  关键直觉：IO a 是一个"待执行的配方"，不是立刻执行的结果。
-}

module Main where

import Control.Exception (try, SomeException, evaluate)
import Data.IORef
import Data.List (intercalate)
import System.IO (hSetBuffering, stdout, BufferMode(..))

-- ========== IO 基础：描述副作用 ==========

-- greet :: String -> IO ()
-- 接受名字，返回一个"打招呼"的 IO 动作
greet :: String -> IO ()
greet name = putStrLn ("你好, " ++ name ++ "！")

-- readAndDouble :: IO ()
-- 读入一个数字，翻倍后打印
-- (这里用纯值模拟，避免交互阻塞)
doubleAndPrint :: Int -> IO ()
doubleAndPrint n = do
  let result = n * 2
  putStrLn $ show n ++ " * 2 = " ++ show result

-- ========== IO 组合：do 表示法 ==========

-- 购物车结账流程
checkout :: [String] -> [Double] -> IO ()
checkout items prices = do
  putStrLn "=== 购物车 ==="
  mapM_ (\(item, price) ->
    putStrLn $ "  " ++ item ++ ": ¥" ++ show price
    ) (zip items prices)
  let total = sum prices
  putStrLn $ "合计: ¥" ++ show total
  if total > 100
    then putStrLn "满100元，享受9折优惠！折后: ¥" >> print (total * 0.9)
    else putStrLn "继续购物满100元可享折扣"

-- ========== IORef: 可变状态（局部副作用）==========

-- 用 IORef 模拟可变计数器
counterDemo :: IO ()
counterDemo = do
  putStrLn "\n=== IORef 可变状态 ==="
  counter <- newIORef (0 :: Int)        -- 创建可变引用，初值为 0

  -- 模拟 5 次自增
  mapM_ (\_ -> modifyIORef counter (+1)) [1..5 :: Int]
  val1 <- readIORef counter
  putStrLn $ "自增5次后: " ++ show val1

  -- 累加 1..10
  mapM_ (\n -> modifyIORef counter (+n)) [1..10 :: Int]
  val2 <- readIORef counter
  putStrLn $ "再累加1..10后: " ++ show val2

  -- 重置
  writeIORef counter 0
  val3 <- readIORef counter
  putStrLn $ "重置后: " ++ show val3

-- ========== mapM / forM / sequence ==========

-- mapM :: (a -> IO b) -> [a] -> IO [b]
-- 把 IO 动作列表变成单个 IO 动作，收集所有结果

processNumbers :: [Int] -> IO [Int]
processNumbers ns = mapM processOne ns
  where
    processOne n = do
      let result = n * n + 1
      return result  -- 纯计算包进 IO

-- sequence: [IO a] -> IO [a]
sequenceDemo :: IO ()
sequenceDemo = do
  putStrLn "\n=== mapM 与 sequence ==="
  results <- processNumbers [1..5]
  putStrLn $ "processNumbers [1..5] = " ++ show results

  -- mapM_ 不收集结果，只运行副作用
  putStrLn "打印1到3的平方:"
  mapM_ (\n -> putStrLn $ "  " ++ show n ++ "^2 = " ++ show (n*n)) [1..3 :: Int]

-- ========== 异常处理 ==========

-- try :: IO a -> IO (Either SomeException a)
safeDiv :: Int -> Int -> IO (Either String Int)
safeDiv _ 0 = return (Left "除零错误")
safeDiv a b = do
  result <- try (evaluate (a `div` b)) :: IO (Either SomeException Int)
  case result of
    Left  e -> return (Left (show e))
    Right v -> return (Right v)

exceptionDemo :: IO ()
exceptionDemo = do
  putStrLn "\n=== IO 异常处理 ==="
  r1 <- safeDiv 10 2
  r2 <- safeDiv 10 0
  r3 <- safeDiv 100 7
  putStrLn $ "10 / 2  = " ++ show r1
  putStrLn $ "10 / 0  = " ++ show r2
  putStrLn $ "100 / 7 = " ++ show r3

-- ========== IO 与纯函数分离 ==========

-- 纯函数：业务逻辑
calculateTax :: Double -> Double -> Double
calculateTax rate amount = amount * rate

formatReceipt :: String -> Double -> Double -> String
formatReceipt item price tax =
  item ++ ": ¥" ++ show price ++ " (税: ¥" ++ show tax ++ ")"

-- IO 函数：只负责展示，不含业务逻辑
printReceipt :: [(String, Double)] -> Double -> IO ()
printReceipt items taxRate = do
  putStrLn "\n=== 发票 (纯函数 + IO 分离) ==="
  let receipts = map (\(name, price) ->
        let tax = calculateTax taxRate price
        in formatReceipt name price tax
        ) items
  mapM_ putStrLn receipts
  let total = sum (map snd items)
  let totalTax = calculateTax taxRate total
  putStrLn $ "---"
  putStrLn $ "合计: ¥" ++ show total ++ " (总税: ¥" ++ show totalTax ++ ")"

-- ========== 主函数 ==========

main :: IO ()
main = do
  hSetBuffering stdout LineBuffering

  putStrLn "=== IO Monad 基础 ==="
  greet "Haskell 学习者"
  doubleAndPrint 21

  putStrLn "\n=== IO 组合 (do 表示法) ==="
  checkout ["苹果", "牛奶", "面包", "咖啡"]
            [15.5, 8.0, 6.5, 32.0]

  counterDemo
  sequenceDemo
  exceptionDemo

  printReceipt
    [("笔记本电脑", 6999.0), ("鼠标", 199.0), ("键盘", 399.0)]
    0.13  -- 13% 税率

  putStrLn "\n=== IO 是 Monad：可以组合 ==="
  let actions = map (\n -> putStr (show n ++ " ")) [1..10 :: Int]
  putStr "sequence_ 打印1..10: "
  sequence_ actions
  putStrLn ""
  putStrLn "完成！注意：IO a 只是对动作的'描述'，main 才是执行入口"
