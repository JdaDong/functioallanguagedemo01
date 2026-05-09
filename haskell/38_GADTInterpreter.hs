{-# LANGUAGE DataKinds       #-}
{-# LANGUAGE GADTs           #-}
{-# LANGUAGE KindSignatures  #-}
{-# LANGUAGE TypeOperators   #-}
{-# LANGUAGE PolyKinds       #-}
{-# LANGUAGE RankNTypes      #-}
{-# LANGUAGE NoStarIsType    #-}

-- ============================================================
-- Demo 38: GADT 类型安全解释器（批次 4）
--
-- 目标：用 15 行左右写完一个"编译期保证类型健全"的 lambda 解释器。
--
--   * 上下文 Ctx     = 类型列表（DataKinds 提升）
--   * 变量  Var ctx a = 带类型的 de Bruijn 索引
--   * 表达式 Term ctx a = 由环境 ctx 和结果类型 a 索引
--   * 运行时环境 Env ctx = HList 形态，每层塞一个对应类型的值
--
--   eval 的签名本身就是"类型健全定理"：
--     eval :: Env ctx -> Term ctx a -> a
--   —— 只要编译通过，就不可能出现类型错误。
--
-- 运行：runghc 38_GADTInterpreter.hs
-- ============================================================

module Main where

import Data.Kind (Type)

-- ============================================================
-- 1. 类型层的上下文：就是一个"类型列表"
-- ============================================================

data Ctx where
  Empty :: Ctx
  Snoc  :: Ctx -> Type -> Ctx

-- 中缀别名，让类型签名更好读
type (::>) = 'Snoc
infixl 5 ::>

-- ============================================================
-- 2. 变量 = 带类型的 de Bruijn 索引
-- ============================================================
-- Here         指向最新加入环境的那一层
-- There v      跳过最外层，继续到 v 指向的位置
-- 每个 Var 都携带"取出来是什么类型"的证据

data Var :: Ctx -> Type -> Type where
  Here  :: Var (ctx ::> a) a
  There :: Var ctx a -> Var (ctx ::> b) a

-- ============================================================
-- 3. 表达式：同时被环境和结果类型索引
-- ============================================================

data Term :: Ctx -> Type -> Type where
  LitI :: Int  -> Term ctx Int
  LitB :: Bool -> Term ctx Bool
  VarE :: Var ctx a -> Term ctx a
  Lam  :: Term (ctx ::> a) b -> Term ctx (a -> b)
  App  :: Term ctx (a -> b) -> Term ctx a -> Term ctx b
  Add  :: Term ctx Int -> Term ctx Int -> Term ctx Int
  If   :: Term ctx Bool -> Term ctx a -> Term ctx a -> Term ctx a
  EqI  :: Term ctx Int -> Term ctx Int -> Term ctx Bool

-- ============================================================
-- 4. 运行时环境：HList，形状和 Ctx 对齐
-- ============================================================

data Env :: Ctx -> Type where
  ENil  :: Env 'Empty
  ECons :: a -> Env ctx -> Env (ctx ::> a)

-- 按索引取值：类型保证一定取得出、且类型正确
lookupV :: Var ctx a -> Env ctx -> a
lookupV Here      (ECons x _)  = x
lookupV (There v) (ECons _ xs) = lookupV v xs

-- ============================================================
-- 5. 求值 —— 类型签名就是"类型健全"的定理陈述
-- ============================================================

eval :: Env ctx -> Term ctx a -> a
eval _   (LitI n)     = n
eval _   (LitB b)     = b
eval env (VarE v)     = lookupV v env
eval env (Lam body)   = \x -> eval (ECons x env) body
eval env (App f x)    = eval env f (eval env x)
eval env (Add a b)    = eval env a + eval env b
eval env (If c t e)   = if eval env c then eval env t else eval env e
eval env (EqI a b)    = eval env a == eval env b

-- ============================================================
-- 6. 示例程序
-- ============================================================

-- \x. x + 1   :  Int -> Int
prog1 :: Term 'Empty (Int -> Int)
prog1 = Lam (Add (VarE Here) (LitI 1))

-- \x. \y. if x == y then x else x + y   :  Int -> Int -> Int
prog2 :: Term 'Empty (Int -> Int -> Int)
prog2 =
  Lam $ Lam $
    If (EqI (VarE (There Here)) (VarE Here))
       (VarE (There Here))
       (Add (VarE (There Here)) (VarE Here))

-- 应用：prog1 41
demo1 :: Int
demo1 = eval ENil (App prog1 (LitI 41))

-- 应用：prog2 3 5
demo2 :: Int
demo2 = eval ENil (App (App prog2 (LitI 3)) (LitI 5))

-- 应用：prog2 7 7
demo3 :: Int
demo3 = eval ENil (App (App prog2 (LitI 7)) (LitI 7))

-- ============================================================
-- 7. "编译期拒绝类型错误"的演示
-- ============================================================
-- 下面这些行若取消注释，GHC 会直接报错 —— 这就是我们想要的：
--
--   badAdd :: Term 'Empty Int
--   badAdd = Add (LitI 1) (LitB True)          -- Int 和 Bool 不能相加
--
--   badApp :: Term 'Empty Int
--   badApp = App (LitI 1) (LitI 2)             -- Int 不是函数
--
--   badVar :: Term 'Empty Int
--   badVar = VarE Here                         -- 空环境没有变量可取

main :: IO ()
main = do
  putStrLn "Demo 38: GADT 类型安全解释器"
  putStrLn "-----------------------------------"
  putStrLn $ "prog1 41        = " ++ show demo1   -- 42
  putStrLn $ "prog2 3 5       = " ++ show demo2   -- 8
  putStrLn $ "prog2 7 7       = " ++ show demo3   -- 7
  putStrLn ""
  putStrLn "eval :: Env ctx -> Term ctx a -> a"
  putStrLn "  —— 这个签名就是类型健全定理本身"
