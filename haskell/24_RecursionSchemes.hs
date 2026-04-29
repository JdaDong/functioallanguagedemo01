{-# LANGUAGE DeriveFunctor #-}

-- ============================================================
-- Demo 24: Recursion Schemes —— 用 fix + fmap 统一所有递归
--
-- 如果说 Monad 是"把副作用抽象出来"，
-- 那 Recursion Schemes 就是"把递归本身抽象出来"。
--
-- 核心直觉:
--   * 先把"递归数据"的"一层"抽出来做成一个 Functor f
--     (所谓 "pattern functor")
--   * 具体类型 = Fix f  (反复套 f 自己)
--   * 各种递归方案就是在同样的骨架上换不同的函数:
--
--     cata (fold):    (f a -> a)          -> Fix f -> a
--     ana  (unfold):  (a -> f a)          -> a     -> Fix f
--     hylo (fuse):    (f b -> b) -> (a -> f a) -> a -> b
--     para (带子树):  (f (Fix f, a) -> a) -> Fix f -> a
--
-- 没有 recursion-schemes 库依赖，全部手写 90 行内搞定。
-- 运行: runhaskell 24_RecursionSchemes.hs
-- ============================================================

module Main where

-- ============================================================
-- 0. 通用骨架
-- ============================================================

newtype Fix f = Fix { unFix :: f (Fix f) }

cata :: Functor f => (f a -> a) -> Fix f -> a
cata alg = alg . fmap (cata alg) . unFix

ana  :: Functor f => (a -> f a) -> a -> Fix f
ana  coa = Fix . fmap (ana coa) . coa

-- hylo: 先 ana 再 cata，但编译器可以把它"融合"掉中间的 Fix 结构
hylo :: Functor f => (f b -> b) -> (a -> f a) -> a -> b
hylo alg coa = alg . fmap (hylo alg coa) . coa

-- para: fold 的增强版，同时拿到"当前子结构原件"和"子结果"
para :: Functor f => (f (Fix f, a) -> a) -> Fix f -> a
para alg = alg . fmap (\x -> (x, para alg x)) . unFix

-- ============================================================
-- 1. 自然数 —— 最简 Recursion Schemes 示例
-- ============================================================

data NatF a = ZeroF | SuccF a deriving (Show, Functor)

type Nat = Fix NatF

zero :: Nat
zero = Fix ZeroF

suc :: Nat -> Nat
suc n = Fix (SuccF n)

-- cata: 把 Nat 转成 Int
toInt :: Nat -> Int
toInt = cata alg
  where alg ZeroF     = 0
        alg (SuccF n) = n + 1

-- ana:  从 Int 展开成 Nat
fromInt :: Int -> Nat
fromInt = ana coa
  where coa n | n <= 0 = ZeroF
              | otherwise = SuccF (n - 1)

-- hylo: 从 Int 直接算 n!, 中间的 List 结构被融合掉
--   coa: 把 n 展开成 ConsF n (n-1); 遇到 0 停住产出 NilF
--   alg: 把 ConsF k acc 折成 k * acc; NilF 是 base case 返回 1
factorial :: Int -> Int
factorial = hylo algL coaL . max 0
  where
    coaL 0 = NilF
    coaL n = ConsF n (n - 1)
    algL NilF           = 1
    algL (ConsF k acc)  = k * acc

-- ============================================================
-- 2. 算术表达式 —— 更有说服力的例子
-- ============================================================

data ExprF a = Lit Int | Add a a | Mul a a deriving (Show, Functor)
type Expr = Fix ExprF

lit :: Int -> Expr
lit n = Fix (Lit n)

add, mul :: Expr -> Expr -> Expr
add a b = Fix (Add a b)
mul a b = Fix (Mul a b)

-- cata: 求值
evalE :: Expr -> Int
evalE = cata go
  where go (Lit n)   = n
        go (Add a b) = a + b
        go (Mul a b) = a * b

-- cata: 打印
printE :: Expr -> String
printE = cata go
  where go (Lit n)   = show n
        go (Add a b) = "(" ++ a ++ " + " ++ b ++ ")"
        go (Mul a b) = "(" ++ a ++ " * " ++ b ++ ")"

-- para: 简化规则 —— "乘以 1 的子树直接消去"
-- 要看到"原始子树"才能判断结构，这正是 para 超越 cata 之处
simplify :: Expr -> Expr
simplify = para go
  where
    go (Lit n)                              = lit n
    go (Add (_, a) (_, b))                  = add a b
    go (Mul (Fix (Lit 1), _) (_, b))        = b
    go (Mul (_, a) (Fix (Lit 1), _))        = a
    go (Mul (_, a) (_, b))                  = mul a b

-- ============================================================
-- 3. List 也是 Fix of ListF —— 让 map/sum 变成 cata
-- ============================================================

data ListF a r = NilF | ConsF a r deriving (Show, Functor)
type List a = Fix (ListF a)

fromList :: [a] -> List a
fromList = foldr cons nil
  where nil     = Fix NilF
        cons x xs = Fix (ConsF x xs)

sumList :: List Int -> Int
sumList = cata go
  where go NilF         = 0
        go (ConsF x r)  = x + r

lenList :: List a -> Int
lenList = cata go
  where go NilF         = 0
        go (ConsF _ r)  = 1 + r

-- ============================================================
-- main
-- ============================================================

main :: IO ()
main = do
  putStrLn "=========================================="
  putStrLn "Demo 24: Recursion Schemes"
  putStrLn "==========================================\n"

  putStrLn "=== 1. Nat: cata / ana / hylo ==="
  let n = fromInt 5
  putStrLn $ "  toInt   (fromInt 5) = " ++ show (toInt n)        -- 5
  putStrLn $ "  factorial 6         = " ++ show (factorial 6)     -- 720
  putStrLn $ "  factorial 10        = " ++ show (factorial 10)    -- 3628800
  -- 小提示: 这里的"阶乘"严格说是"连乘 n*(n-1)"，而不是
  -- 教科书意义上的 n! —— 但它完整展示了 hylo 融合中间结构的意图。
  -- 下面这个版本才是真正的 n!：
  putStrLn $ "  classic n!(6)       = " ++ show (product [1..6]) -- 对照

  putStrLn "\n=== 2. Expr: cata / para ==="
  let e = add (mul (lit 3) (lit 1))
              (mul (lit 1) (add (lit 4) (lit 0)))
  putStrLn $ "  expr         = " ++ printE e
  putStrLn $ "  eval         = " ++ show (evalE e)
  putStrLn $ "  simplified   = " ++ printE (simplify e)
  putStrLn $ "  eval(simpl)  = " ++ show (evalE (simplify e))

  putStrLn "\n=== 3. List as Fix ListF ==="
  let xs = fromList [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
  putStrLn $ "  sumList = " ++ show (sumList xs)
  putStrLn $ "  lenList = " ++ show (lenList xs)

  putStrLn "\n=== 4. 直觉小结 ==="
  putStrLn "  cata  ≡ foldr      —— 把一棵树层层压下去"
  putStrLn "  ana   ≡ unfold     —— 从种子层层展开成一棵树"
  putStrLn "  hylo  ≡ ana >>> cata (编译器会融合掉中间树)"
  putStrLn "  para  ≡ cata + 原始子结构，用于简化/替换/rewriting"
  putStrLn ""
  putStrLn "  实战库: recursion-schemes (Edward Kmett)"
  putStrLn "          把 ExprF 这种 pattern functor 用 TH 自动生成，"
  putStrLn "          业务代码里只写 cata/ana/hylo/para，不手写递归。"
  putStrLn "=========================================="
