{-# LANGUAGE Arrows #-}
{-# LANGUAGE GADTs #-}

-- ============================================================
-- Demo 13: Arrow & Profunctor — 箭头与反函子
--
-- 核心概念：
--   1. Arrow: 比 Monad 更抽象的计算模型
--   2. Arrow 组合: (>>>), (***), (&&&)
--   3. Kleisli Arrow: Monad -> Arrow 的桥梁
--   4. Profunctor: 反变+协变的二元 functor
--   5. 实际应用：电路模拟、流处理、 optics 基础
-- ============================================================

module Main where

import Prelude hiding ((.), id)
import Control.Category    (Category(..))
import Control.Arrow       (Arrow(..), ArrowChoice(..), returnA, (>>>), (***), (&&&), (|||))
import Data.Bifunctor      (Bifunctor(..))
import Data.Char           (toUpper)

-- ============================================================
-- Part 1: Arrow 基础
-- ============================================================
-- 
-- Arrow a b 是一种"带额外结构的函数"
-- 比 Monad 更弱（每个 Monad 可以变成 Arrow，但反之不行）
--
-- 核心方法:
--   arr     :: (a -> b) -> a b           -- 提升纯函数
--   first   :: a b -> a (b, c)          -- 只处理第一个元素
--   second  :: a b -> a (c, b)          -- 只处理第二个元素  
--   (***)   :: a b -> a c -> a (b, c)    -- 并行组合
--   (&&&)   :: a b -> a c -> a (b, c)    -- 分发+合并(复制输入)
--   (|||)   :: a b -> a c -> a (Either b c) -- 选择分支

-- ============================================================
-- Part 2: 使用 proc/do-notation 的 Arrow 程序
-- ============================================================

-- 数据验证管道（简化版，避免 proc 类型推导复杂性）
validateUser :: Arrow a => a String (Either String (String, Int, String))
validateUser = arr $ \input ->
  if null input then
    Left "输入不能为空"
  else
    let parts = splitComma (filter (/= ' ') input)
    in case parts of
      [name', ageStr, email'] ->
        let age = readSafe ageStr
        in if age > 0 && age < 150
           then Right (name', age, email')
           else Left "年龄无效"
      _ -> Left "格式错误: 需要 name,age,email"

  where
    splitComma s = wordsBy ',' (filter (/= ' ') s)
    wordsBy _ [] = []
    wordsBy c s = 
      let (w, rest) = break (== c) s
      in w : case rest of
        (_:rs) -> wordsBy c rs
        []      -> []
    readSafe s = case reads s of
      [(n, "")] -> n
      _         -> (-1)

-- ============================================================
-- Part 3: 信号处理 Arrow（类电路图）
-- ============================================================

-- 一个简单的信号处理器
data SignalProcessor a b where
  SPId     :: SignalProcessor a a
  SPMap    :: (a -> b) -> SignalProcessor a b
  SPSeq    :: SignalProcessor b c -> SignalProcessor a b -> SignalProcessor a c
  SPPar    :: SignalProcessor a b -> SignalProcessor c d -> SignalProcessor (a,c) (b,d)
  SPFanOut :: SignalProcessor a b -> SignalProcessor a c -> SignalProcessor a (b,c)
  SPFilter :: (a -> Bool) -> SignalProcessor a a
  SPAccum  :: (a -> b -> b) -> b -> SignalProcessor a b

runSP :: SignalProcessor a b -> [a] -> [b]
runSP SPId             xs = xs
runSP (SPMap f)        xs = map f xs
runSP (SPSeq sp2 sp1)  xs = runSP sp2 (runSP sp1 xs)
runSP (SPPar sp1 sp2)  xys = 
  let (xs, ys) = unzip xys
  in zip (runSP sp1 xs) (runSP sp2 ys)
runSP (SPFanOut sp1 sp2) xs =
  zip (runSP sp1 xs) (runSP sp2 xs)
runSP (SPFilter pred)   xs = filter pred xs
runSP (SPAccum f initVal) xs = scanl (flip f) initVal xs

-- 构建一个信号处理流水线（简化版）
signalPipeline :: SignalProcessor Int Int
signalPipeline =
  SPSeq
    (SPFilter (\t -> t > -500 && t < 1000)) -- 过滤异常值(放大后范围)
    (SPMap (`div` 10))                     -- 取整

-- ============================================================
-- Part 4: Profunctor
-- ============================================================
-- 
-- Profunctor p a b 表示一种"从 a 到 b 的关系/过程"
-- 它是反变于第一个参数，协变于第二个参数
-- 
-- 类比：
--   Functor      f a       — 容器，只能 map 输出
--   Contravariant f a      — 容器，只能 contramap 输入
--   Profunctor    p a b     — 过程，可以 dimap 两端

class Profunctor p where
  dimap :: (c -> a) -> (b -> d) -> p a b -> p c d
  lmap  :: (c -> a) -> p a b -> p c b
  rmap  :: (b -> d) -> p a b -> p a d

  -- 默认实现
  lmap f = dimap f id
  rmap = dimap id

-- 函数本身就是最简单的 Profunctor
instance Profunctor (->) where
  dimap f g h = g . h . f
  lmap  f h   = h . f
  rmap    g h = g . h

-- ---- 常用 Profunctor 操作 ----

-- 向上适配：在输出端应用函数
up :: Profunctor p => (b -> c) -> p a b -> p a c
up = rmap

-- 向下适配：在输入端应用函数
down :: Profunctor p => (b -> a) -> p a c -> p b c
down = lmap

-- ============================================================
-- Part 5: Optics 与 Profunctor 的联系
-- ============================================================
-- 
-- 简化版 Profunctor Lens（用具体类型而非 forall）
-- 完整的 van Laarhoven 表示需要 RankNTypes:
--   type Lens s t a b = forall p. Profunctor p => p a b -> p s t

data PLens s a = PLens
  { plView :: s -> a
  , plSet  :: a -> s -> s
  , plOver :: (a -> a) -> s -> s
  }

-- 构造器
makePL :: (s -> a) -> (a -> s -> s) -> PLens s a
makePL getter setter = PLens
  { plView = getter
  , plSet  = setter
  , plOver = \f s -> setter (f (getter s)) s
  }

-- 示例：Person lens
data PersonP = PersonP { _nameP :: String, _ageP :: Int } deriving Show

nameLensP :: PLens PersonP String
nameLensP = makePL getter setter
  where getter (PersonP n _) = n
        setter n (PersonP _ a) = PersonP n a

ageLensP :: PLens PersonP Int
ageLensP = makePL getter setter
  where getter (PersonP _ a) = a
        setter a (PersonP n _) = PersonP n a

-- 操作
viewP :: PLens s a -> s -> a
viewP = plView

overP :: PLens s a -> (a -> a) -> s -> s
overP = plOver

setP :: PLens s a -> a -> s -> s
setP l v = overP l (const v)

-- ============================================================
-- Main
-- ============================================================

main :: IO ()
main = do
  putStrLn "=========================================="
  putStrLn "Demo 13: Arrow & Profunctor"
  putStrLn "==========================================\n"

  -- ---- Arrow: 用户验证 ----
  putStrLn "=== 1. Arrow: 数据验证管道 ==="
  let inputs =
        [ "Alice,30,alice@example.com"
        , ""
        , "Bob,-5,bob@test.com"
        , "bad format"
        , "Charlie,25,charlie@demo.org"
        ]
  mapM_ (\input -> case validateUser input of
    Left err -> putStrLn $ "  \"" ++ input ++ "\" -> X " ++ err
    Right (n, a, e) -> putStrLn $ "  \"" ++ input ++ "\" -> OK " ++ show (n,a)
    ) inputs
  putStrLn ""

  -- ---- 信号处理 ----
  putStrLn "=== 2. 信号处理流水线 ==="
  let temperatures = [235, 240, 999, 228, -999, 253, 247] :: [Int]
      results = runSP signalPipeline temperatures
  putStrLn $ "  输入(放大10x): " ++ show temperatures
  putStrLn $ "  过滤+取整后: " ++ show results
  putStrLn ""

  -- ---- Profunctor ----
  putStrLn "=== 3. Profunctor: dimap ==="
  let doubleThenShow = (*2) :: Int -> Int
      process = dimap read show doubleThenShow
  putStrLn $ "  dimap read show (*2): \"42\" -> " ++ process "42"
  putStrLn $ "  dimap read show (*2): \"42\" -> " ++ process "42" ++ " | " ++ process "-5"
  putStrLn $ "  dimap length (++suffix): \"hello\" -> " ++ show (dimap length (++suffix) id "hello")
  putStrLn $ "  view name: " ++ viewP nameLensP person
