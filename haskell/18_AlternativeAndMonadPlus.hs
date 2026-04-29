-- ============================================================
-- Demo 18: Alternative / MonadPlus —— 失败、选择与非确定计算
--
-- 为什么重要：
--   * Monad    回答："一个计算成功后怎么接下一步"
--   * Alternative / MonadPlus 回答："失败了怎么办？有多个候选怎么办？"
--
--   这正是 parser 的 `<|>`、回溯搜索、解谜器、查询语言
--   等等背后的共同抽象。
--
-- 核心方法（Alternative）:
--   empty  :: f a                 表示 "失败 / 没有结果"
--   (<|>)  :: f a -> f a -> f a    表示 "先试左边，不行再试右边"
--   some   :: f a -> f [a]         一次或多次（至少一个）
--   many   :: f a -> f [a]         零次或多次
--
-- 运行：runhaskell 18_AlternativeAndMonadPlus.hs
-- ============================================================

module Main where

import Control.Applicative (Alternative (..))
import Control.Monad       (MonadPlus, guard, mzero, mplus, msum)
import Data.List           (nub)

-- ============================================================
-- Part 1: Maybe 的 Alternative —— "拿到第一个 Just"
-- ============================================================
-- empty       = Nothing
-- Nothing <|> r = r
-- Just a  <|> _ = Just a

lookupMulti :: Eq k => k -> [[(k, v)]] -> Maybe v
lookupMulti key dicts =
  -- 依次查多个字典，取到第一个匹配
  foldr (<|>) empty (map (lookup key) dicts)

-- 也可以用 asum / msum（对 list of f a 进行 <|> 聚合）
firstJust :: [Maybe a] -> Maybe a
firstJust = msum

-- ============================================================
-- Part 2: List 的 MonadPlus —— "非确定计算"
-- ============================================================
-- 对 [] 来说：
--   empty = []
--   xs <|> ys = xs ++ ys
-- List Monad 的 "return a" = [a]，
-- "xs >>= k" = 把每个 x 展开成一条分支
-- 合起来就是经典的"列表推导 / 回溯搜索"。

-- 经典例子：用 MonadPlus + guard 解毕达哥拉斯三元组
pythTriples :: Int -> [(Int, Int, Int)]
pythTriples n = do
  a <- [1 .. n]
  b <- [a .. n]
  c <- [b .. n]
  guard (a * a + b * b == c * c)  -- 不满足时 guard 返回 empty，分支被剪掉
  pure (a, b, c)

-- N 皇后问题（简化版）：非常短但说明力十足
safe :: Int -> [Int] -> Bool
safe col placed =
  all (\(i, c) -> c /= col
                && abs (c - col) /= i + 1)   -- 对角线冲突
      (zip [0 ..] placed)

queens :: Int -> [[Int]]
queens n = go n
  where
    go 0 = pure []
    go k = do
      rest <- go (k - 1)
      col  <- [1 .. n]
      guard (safe col rest)
      pure (col : rest)

-- ============================================================
-- Part 3: 写一个最小的 Parser，看 Alternative 的真正威力
-- ============================================================
--
-- 这个 Parser 很小，但和 Demo 10 的用途一致，
-- 这里重点只想让你看到：<|>、some、many 全部是 Alternative 自带的。

newtype Parser a = Parser { runParser :: String -> Maybe (a, String) }

instance Functor Parser where
  fmap f (Parser p) = Parser $ \s -> do
    (a, s') <- p s
    pure (f a, s')

instance Applicative Parser where
  pure a = Parser $ \s -> Just (a, s)
  Parser pf <*> Parser pa = Parser $ \s -> do
    (f, s1) <- pf s
    (a, s2) <- pa s1
    pure (f a, s2)

instance Monad Parser where
  return = pure
  Parser p >>= k = Parser $ \s -> do
    (a, s') <- p s
    runParser (k a) s'

-- 这里是关键：Alternative 实例
instance Alternative Parser where
  empty = Parser $ const Nothing
  Parser p <|> Parser q = Parser $ \s ->
    case p s of
      Just r  -> Just r
      Nothing -> q s
  -- some / many 有默认定义：some p = (:) <$> p <*> many p ; many p = some p <|> pure []

charP :: (Char -> Bool) -> Parser Char
charP pred_ = Parser $ \s -> case s of
  (c:rest) | pred_ c -> Just (c, rest)
  _                 -> Nothing

digitP :: Parser Char
digitP = charP (`elem` "0123456789")

-- number = some digit   —— 这里的 some 完全来自 Alternative
numberP :: Parser Int
numberP = read <$> some digitP

-- 多个候选：int 或者字面量 "null"
valueP :: Parser (Either Int String)
valueP =
       (Left  <$> numberP)
   <|> (Right <$> traverse (charP . (==)) "null")

-- ============================================================
-- Part 4: guard 的本质 —— "条件过滤器"
-- ============================================================
-- guard :: Alternative f => Bool -> f ()
-- guard True  = pure ()
-- guard False = empty

-- 用 guard 写一个非常贴近"数学语言"的素数筛
primes :: Int -> [Int]
primes n = do
  p <- [2 .. n]
  guard (all (\d -> p `mod` d /= 0) [2 .. p - 1])
  pure p

-- ============================================================
-- Part 5: Alternative 的组合规律（直觉版）
-- ============================================================
-- 对大多数实例：
--   empty <|> x   ==  x
--   x <|> empty   ==  x
--   (x <|> y) <|> z == x <|> (y <|> z)
--
-- 也就是说 (<|>, empty) 组成一个 Monoid，
-- 这和 Semigroup/Monoid 是同一种 "加法"抽象，
-- 只不过这里的"元素"是"带上下文的计算"。

-- ============================================================
-- Main
-- ============================================================

main :: IO ()
main = do
  putStrLn "=========================================="
  putStrLn "Demo 18: Alternative / MonadPlus"
  putStrLn "==========================================\n"

  -- ---- Maybe + <|> ----
  putStrLn "=== 1. Maybe: 拿到第一个 Just ==="
  let d1 = [("a", 1), ("b", 2)]
      d2 = [("b", 20), ("c", 30)]
      d3 = [("x", 999)]
  putStrLn $ "  lookupMulti \"b\" = " ++ show (lookupMulti "b" [d1, d2, d3])
  putStrLn $ "  lookupMulti \"c\" = " ++ show (lookupMulti "c" [d1, d2, d3])
  putStrLn $ "  lookupMulti \"z\" = " ++ show (lookupMulti "z" [d1, d2, d3])
  putStrLn $ "  firstJust [Nothing, Nothing, Just 7, Just 9] = "
              ++ show (firstJust [Nothing, Nothing, Just (7 :: Int), Just 9])
  putStrLn ""

  -- ---- List + guard ----
  putStrLn "=== 2. List 作为非确定计算：pythTriples ==="
  putStrLn $ "  pythTriples 20 = " ++ show (pythTriples 20)
  putStrLn ""

  putStrLn "=== 3. 简易 N-Queens ==="
  let sols4 = queens 4
      sols6 = queens 6
  putStrLn $ "  4 皇后解数 = " ++ show (length sols4) ++ ", 首解: " ++ show (head sols4)
  putStrLn $ "  6 皇后解数 = " ++ show (length sols6) ++ ", 首解: " ++ show (head sols6)
  putStrLn ""

  -- ---- 最小 Parser + <|> / some / many ----
  putStrLn "=== 4. 最小 Parser：some / <|> 全部来自 Alternative ==="
  putStrLn $ "  numberP \"1234abc\"  = " ++ show (runParser numberP "1234abc")
  putStrLn $ "  valueP  \"1234abc\"  = " ++ show (runParser valueP  "1234abc")
  putStrLn $ "  valueP  \"nullabc\"  = " ++ show (runParser valueP  "nullabc")
  putStrLn $ "  valueP  \"????\"     = " ++ show (runParser valueP  "????")
  putStrLn ""

  -- ---- guard 的本质 ----
  putStrLn "=== 5. guard：非常贴近数学语言的素数筛 ==="
  putStrLn $ "  primes 30 = " ++ show (primes 30)
  putStrLn ""

  -- ---- 小结 ----
  putStrLn "=== 知识点小结 ==="
  putStrLn "  * Alternative (<|>, empty) 抽象的是 '失败 / 选择'"
  putStrLn "  * MonadPlus   = Monad + Alternative，用于 '带 effect 的非确定'"
  putStrLn "  * guard p     = if p then pure () else empty（"过滤器""
  putStrLn "  * 列表推导 [...] do 记法 = MonadPlus 的糖"
  putStrLn "  * Parser <|> / some / many 全部免费 —— 只要你实现了 Alternative"
  putStrLn "=========================================="
