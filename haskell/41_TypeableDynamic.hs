{-# LANGUAGE ScopedTypeVariables #-}

-- ============================================================
-- Demo 41: Typeable & Dynamic（批次 4）
--
-- Haskell 默认是静态类型的，但有些场景天然要求"把任意类型
-- 塞进同一个容器，到边界再把它取出来"：
--
--   * 插件系统 / 消息总线
--   * 反序列化 / 跨进程通信
--   * 嵌入式脚本 / 调试工具
--
-- 工具箱：
--   Typeable  —— 运行时类型指纹 (TypeRep)
--   Dynamic   —— 带指纹的"万能盒子"：toDyn / fromDynamic / dynApply
--   cast      —— 安全下行：from a to b if they have the same TypeRep
--
-- 本 Demo 覆盖：
--   1) 用 TypeRep 比较类型
--   2) Dynamic 装异构值、按类型解包
--   3) cast 做类型安全下行
--   4) 简单的类型分发注册表（插件/事件总线雏形）
--
-- 依赖：base（Data.Typeable / Data.Dynamic 都是 base 自带）
-- 运行：runghc 41_TypeableDynamic.hs
-- ============================================================

module Main where

import Data.Typeable  (Typeable, TypeRep, typeOf, cast)
import Data.Dynamic   (Dynamic, toDyn, fromDynamic, dynTypeRep, dynApply)
import Data.Maybe     (mapMaybe)

-- ============================================================
-- Part 1: TypeRep —— 类型的"运行时指纹"
-- ============================================================

showType :: Typeable a => a -> String
showType x = "类型指纹: " ++ show (typeOf x)

-- 同类型指纹相等
sameType :: (Typeable a, Typeable b) => a -> b -> Bool
sameType a b = typeOf a == typeOf b

-- ============================================================
-- Part 2: Dynamic —— 异构容器
-- ============================================================

dyns :: [Dynamic]
dyns =
  [ toDyn (42        :: Int)
  , toDyn (3.14      :: Double)
  , toDyn ("hello"   :: String)
  , toDyn (True      :: Bool)
  , toDyn ([1,2,3]   :: [Int])
  ]

-- 按类型从 Dynamic 解包，不匹配返回 Nothing
showDyn :: Dynamic -> String
showDyn d
  | Just (n :: Int)    <- fromDynamic d = "Int("    ++ show n ++ ")"
  | Just (x :: Double) <- fromDynamic d = "Double(" ++ show x ++ ")"
  | Just (s :: String) <- fromDynamic d = "String(" ++ show s ++ ")"
  | Just (b :: Bool)   <- fromDynamic d = "Bool("   ++ show b ++ ")"
  | Just (xs :: [Int]) <- fromDynamic d = "[Int](" ++ show xs ++ ")"
  | otherwise                           = "Unknown(" ++ show (dynTypeRep d) ++ ")"

-- 只把能解成 Int 的值挑出来
onlyInts :: [Dynamic] -> [Int]
onlyInts = mapMaybe fromDynamic

-- 只把能解成 String 的值挑出来
onlyStrings :: [Dynamic] -> [String]
onlyStrings = mapMaybe fromDynamic

-- ============================================================
-- Part 3: cast —— 单个值的安全下行
-- ============================================================
-- cast :: (Typeable a, Typeable b) => a -> Maybe b
-- 若 a 和 b 的 TypeRep 相同，就返回 Just (强转后的值)，否则 Nothing

-- 一个"如果是 Int 就 +1"的通用函数
incIfInt :: Typeable a => a -> a
incIfInt x = case cast x :: Maybe Int of
  Just n  -> case cast (n + 1) of
               Just back -> back
               Nothing   -> x
  Nothing -> x

-- ============================================================
-- Part 4: 简易"事件总线" —— 按类型分发 handler
-- ============================================================
-- Handler a = a -> IO ()，不同类型事件各自有 handler，
-- 总线内部用 Dynamic 保存，派发时 fromDynamic 匹配。

-- 两类业务事件
data UserLogin    = UserLogin    { userName :: String }  deriving (Show)
data OrderCreated = OrderCreated { orderId  :: Int    }  deriving (Show)

-- 注：现代 GHC（8.10+）对所有类型自动派生 Typeable 实例，无需手写。

-- 一个 handler 的通用签名（藏在 Dynamic 里）
--   原始类型： forall a. Typeable a => a -> IO ()
--   存进 Dynamic：用 "接收 Dynamic 的 handler" 包一层
type AnyHandler = Dynamic -> IO ()

-- 把类型化 handler 包成 AnyHandler
wrap :: Typeable a => (a -> IO ()) -> AnyHandler
wrap h dyn = case fromDynamic dyn of
  Just x  -> h x
  Nothing -> pure ()    -- 类型不匹配，直接忽略

-- 事件总线：按顺序把事件发给每个 handler，匹配类型的会响应
dispatch :: [AnyHandler] -> Dynamic -> IO ()
dispatch handlers evt = mapM_ ($ evt) handlers

-- ============================================================
-- Part 5: dynApply —— 动态函数应用（少用但有）
-- ============================================================
-- 把 Dynamic 函数应用到 Dynamic 参数，类型匹配就成功

addDyn :: Dynamic
addDyn = toDyn ((+) :: Int -> Int -> Int)

tryAdd :: Int -> Int -> Maybe Dynamic
tryAdd a b =
  addDyn `dynApply` toDyn a >>= (`dynApply` toDyn b)

-- ============================================================
main :: IO ()
main = do
  putStrLn "Demo 41: Typeable & Dynamic"
  putStrLn "============================================================"
  putStrLn ""

  putStrLn "Part 1: TypeRep"
  putStrLn $ "  " ++ showType (42 :: Int)
  putStrLn $ "  " ++ showType "hello"
  putStrLn $ "  sameType 1 2             = " ++ show (sameType (1::Int) (2::Int))
  putStrLn $ "  sameType 1 \"x\"           = " ++ show (sameType (1::Int) ("x"::String))
  putStrLn ""

  putStrLn "Part 2: Dynamic 异构容器"
  mapM_ (\d -> putStrLn $ "  " ++ showDyn d) dyns
  putStrLn $ "  onlyInts    = " ++ show (onlyInts    dyns)
  putStrLn $ "  onlyStrings = " ++ show (onlyStrings dyns)
  putStrLn ""

  putStrLn "Part 3: cast 安全下行"
  putStrLn $ "  incIfInt (10 :: Int)     = " ++ show (incIfInt (10 :: Int))
  putStrLn $ "  incIfInt (\"hi\" :: String) = " ++ show (incIfInt ("hi" :: String))
  putStrLn ""

  putStrLn "Part 4: 事件总线"
  let bus =
        [ wrap (\(UserLogin n)    -> putStrLn $ "    [login]    welcome "  ++ n)
        , wrap (\(OrderCreated i) -> putStrLn $ "    [order]    created #" ++ show i)
        ]
  dispatch bus (toDyn (UserLogin    "alice"))
  dispatch bus (toDyn (OrderCreated 777))
  dispatch bus (toDyn (42 :: Int))        -- 没人订阅 Int，静默丢弃
  putStrLn ""

  putStrLn "Part 5: dynApply 动态函数应用"
  case tryAdd 3 4 of
    Just d  -> putStrLn $ "  addDyn 3 4 = " ++ showDyn d
    Nothing -> putStrLn "  addDyn 3 4 = <type mismatch>"

  putStrLn ""
  putStrLn "============================================================"
  putStrLn "典型场景：插件系统 / 事件总线 / 跨进程消息 / 调试反射"
  putStrLn "不是让 Haskell 变动态语言，而是在\"类型收纳点\"有一个安全出口。"
