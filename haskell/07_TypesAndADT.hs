{-
  Haskell 函数式编程 Demo 7: 高级类型系统与 ADT

  Haskell 类型系统的核心能力：
  - 代数数据类型 (ADT)：Sum Type + Product Type
  - newtype：零成本包装，类型安全
  - GADT (广义代数数据类型)：在构造子层面精确控制类型
  - 类型族 (Type Families)：关联类型，类型级函数
  - 幻影类型 (Phantom Types)：在类型层面携带信息，不占运行时内存

  关键直觉：让编译器帮你检查业务规则，把错误拦在编译期
-}

{-# LANGUAGE GADTs #-}
{-# LANGUAGE DataKinds #-}
{-# LANGUAGE KindSignatures #-}

module Main where

import Data.Kind (Type)

-- ========== Sum Type + Product Type ==========

-- Sum Type (求和类型): OR 关系，值是多个分支之一
data Result a
  = Success a
  | Failure String
  | Pending
  deriving (Show)

-- 使 Result 成为 Functor
instance Functor Result where
  fmap f (Success a) = Success (f a)
  fmap _ (Failure e) = Failure e
  fmap _ Pending     = Pending

-- Product Type (乘积类型): AND 关系，值同时包含所有字段
data User = User
  { userId   :: Int
  , userName :: String
  , userAge  :: Int
  } deriving (Show, Eq)

-- 递归 ADT：表达式树
data Expr
  = Lit   Double
  | Var   String
  | Add   Expr Expr
  | Mul   Expr Expr
  | Div   Expr Expr
  | Neg   Expr
  | IfPos Expr Expr Expr  -- if x > 0 then e1 else e2
  deriving (Show)

type Env = [(String, Double)]

eval :: Env -> Expr -> Either String Double
eval _   (Lit n)           = Right n
eval env (Var x)           = case lookup x env of
                               Nothing -> Left ("未定义变量: " ++ x)
                               Just v  -> Right v
eval env (Add a b)         = (+)  <$> eval env a <*> eval env b
eval env (Mul a b)         = (*)  <$> eval env a <*> eval env b
eval env (Div _ b)         | Right 0 <- eval env b = Left "除零错误"
eval env (Div a b)         = (/)  <$> eval env a <*> eval env b
eval env (Neg a)           = negate <$> eval env a
eval env (IfPos cond t f)  = do
  c <- eval env cond
  if c > 0 then eval env t else eval env f

-- ========== newtype: 零成本类型安全包装 ==========

-- 用 newtype 区分语义相同但含义不同的类型
newtype UserId    = UserId    Int    deriving (Show, Eq, Ord)
newtype ProductId = ProductId Int    deriving (Show, Eq, Ord)
newtype Amount    = Amount    Double deriving (Show, Eq, Ord)
newtype Email     = Email     String deriving (Show, Eq)

-- 这样编译器会阻止你把 UserId 误传给需要 ProductId 的函数
lookupUser :: UserId -> String
lookupUser (UserId n) = "User#" ++ show n

lookupProduct :: ProductId -> String
lookupProduct (ProductId n) = "Product#" ++ show n

-- ========== 幻影类型: 在类型层面携带状态信息 ==========

-- 定义类型级"标签"（在运行时不存在）
data Unvalidated
data Validated

-- 用幻影类型标注数据是否已校验
data Tagged t a = Tagged { unTagged :: a } deriving (Show)

type RawEmail    = Tagged Unvalidated String
type ValidEmail  = Tagged Validated   String

-- 只有校验函数能产生 ValidEmail
validateEmail :: RawEmail -> Either String ValidEmail
validateEmail (Tagged raw)
  | '@' `elem` raw && '.' `elem` dropWhile (/= '@') raw
               = Right (Tagged raw)
  | otherwise  = Left $ "无效邮箱: " ++ raw

-- 发送邮件只接受 ValidEmail (编译期保证)
sendEmail :: ValidEmail -> String -> IO ()
sendEmail (Tagged addr) msg =
  putStrLn $ "发送邮件到 " ++ addr ++ ": " ++ msg

-- ========== GADT: 类型安全的表达式 ==========

-- 普通 ADT 无法区分 IntExpr 和 BoolExpr
-- GADT 可以在类型参数上做精确区分
data TypedExpr :: Type -> Type where
  TLitInt  :: Int                           -> TypedExpr Int
  TLitBool :: Bool                          -> TypedExpr Bool
  TAdd     :: TypedExpr Int -> TypedExpr Int -> TypedExpr Int
  TMul     :: TypedExpr Int -> TypedExpr Int -> TypedExpr Int
  TGt      :: TypedExpr Int -> TypedExpr Int -> TypedExpr Bool
  TIf      :: TypedExpr Bool -> TypedExpr a -> TypedExpr a -> TypedExpr a
  TAnd     :: TypedExpr Bool -> TypedExpr Bool -> TypedExpr Bool

-- 类型安全的求值：返回值的类型与表达式类型一致
evalTyped :: TypedExpr a -> a
evalTyped (TLitInt  n)     = n
evalTyped (TLitBool b)     = b
evalTyped (TAdd a b)       = evalTyped a + evalTyped b
evalTyped (TMul a b)       = evalTyped a * evalTyped b
evalTyped (TGt  a b)       = evalTyped a > evalTyped b
evalTyped (TIf  c t f)     = if evalTyped c then evalTyped t else evalTyped f
evalTyped (TAnd a b)       = evalTyped a && evalTyped b

-- ========== 递归类型 + Smart Constructor ==========

-- 非空列表（通过类型保证至少有一个元素）
data NonEmpty a = a :| [a] deriving (Show)

toNonEmpty :: [a] -> Maybe (NonEmpty a)
toNonEmpty []     = Nothing
toNonEmpty (x:xs) = Just (x :| xs)

headNE :: NonEmpty a -> a
headNE (x :| _) = x

foldNE :: (b -> a -> b) -> b -> NonEmpty a -> b
foldNE f acc (x :| xs) = foldl f (f acc x) xs

-- ========== 主函数 ==========

main :: IO ()
main = do
  -- ADT + 表达式树
  putStrLn "=== ADT: 表达式树求值 ==="
  let env = [("x", 3.0), ("y", 4.0)]
  let expr1 = Add (Mul (Var "x") (Var "x"))
                  (Mul (Var "y") (Var "y"))
  putStrLn $ "x^2 + y^2 = " ++ show (eval env expr1)

  let expr2 = Div (Lit 10) (Add (Var "x") (Lit (-3)))
  putStrLn $ "10 / (x-3) = " ++ show (eval env expr2)  -- 除零

  let expr3 = IfPos (Var "x") (Mul (Var "x") (Lit 2)) (Neg (Var "x"))
  putStrLn $ "if x>0 then 2x else -x = " ++ show (eval env expr3)

  let expr4 = Add (Var "z") (Lit 1)
  putStrLn $ "z+1 (z未定义) = " ++ show (eval env expr4)

  -- Functor on Result
  putStrLn "\n=== Result Functor ==="
  let r1 = Success (42 :: Int)
  let r2 = Failure "not found" :: Result Int
  putStrLn $ "fmap (*2) Success 42 = " ++ show (fmap (*2) r1)
  putStrLn $ "fmap (*2) Failure    = " ++ show (fmap (*2) r2)

  -- newtype 类型安全
  putStrLn "\n=== newtype 类型安全 ==="
  putStrLn $ lookupUser    (UserId    42)
  putStrLn $ lookupProduct (ProductId 99)
  -- lookupUser (ProductId 99)  -- 这行无法编译！类型错误

  -- 幻影类型
  putStrLn "\n=== 幻影类型: 校验标注 ==="
  let raw1 = Tagged "alice@example.com" :: RawEmail
  let raw2 = Tagged "not-an-email"      :: RawEmail
  case validateEmail raw1 of
    Right valid -> sendEmail valid "欢迎注册！"
    Left  err   -> putStrLn $ "校验失败: " ++ err
  case validateEmail raw2 of
    Right valid -> sendEmail valid "欢迎注册！"
    Left  err   -> putStrLn $ "校验失败: " ++ err

  -- GADT 类型安全表达式
  putStrLn "\n=== GADT: 类型安全求值 ==="
  let intExpr = TAdd (TMul (TLitInt 3) (TLitInt 4)) (TLitInt 5)
  putStrLn $ "3*4+5 = " ++ show (evalTyped intExpr :: Int)

  let boolExpr = TAnd (TGt (TLitInt 10) (TLitInt 3))
                      (TGt (TLitInt 5)  (TLitInt 1))
  putStrLn $ "10>3 && 5>1 = " ++ show (evalTyped boolExpr :: Bool)

  let ifExpr = TIf (TGt (TLitInt 7) (TLitInt 5))
                   (TLitInt 100)
                   (TLitInt 0)
  putStrLn $ "if 7>5 then 100 else 0 = " ++ show (evalTyped ifExpr :: Int)

  -- NonEmpty
  putStrLn "\n=== NonEmpty: 类型保证非空 ==="
  case toNonEmpty [3, 1, 4, 1, 5, 9 :: Int] of
    Nothing -> putStrLn "空列表"
    Just ne -> do
      putStrLn $ "头元素: " ++ show (headNE ne)
      putStrLn $ "求和: "   ++ show (foldNE (+) 0 ne)
  putStrLn $ "空列表转换: " ++ show (toNonEmpty ([] :: [Int]))
