{-# LANGUAGE TemplateHaskell #-}

-- ============================================================
-- Demo 40: Template Haskell 入门（批次 4）
--
-- TH 让你在**编译期**生成代码：把 AST 当成值来操纵。
--
--   * Q Monad     —— TH 的底座，持有 name/type 等编译期信息
--   * Exp / Dec / Pat —— 被操纵的 AST 节点
--   * Quote  [| expr |]    —— 把源码拎进 AST
--   * Splice $( ... )      —— 把 AST 拼回源码（需要跨模块）
--
-- 本 Demo 聚焦 **单文件 + runghc 能直接跑** 的部分：
--   Part A. 用 quote 把表达式/模式拎成 AST，并打印
--   Part B. 在 Q Monad 里用 runQ + pprint 组装并观察 AST
--   Part C. Lift：把值型数据自动提升成 AST（常用于嵌入 DSL）
--
-- 注意：真正的 splice $( ... ) 必须放在"另一个模块"里才能展开，
--       这是 GHC 的 stage restriction。这里的 Demo 只做 AST 观察，
--       工程里的 persistent / aeson-th / lens-th 是同样套路的大号版。
--
-- 依赖：template-haskell（GHC boot 自带）
-- 运行：runghc 40_TemplateHaskellIntro.hs
-- ============================================================

module Main where

import Language.Haskell.TH
import Language.Haskell.TH.Ppr (pprint)
import Language.Haskell.TH.Syntax (Lift (..))

-- ============================================================
-- Part A: Quote —— 把源码变成 AST
-- ============================================================

-- [| ... |] 在类型上是 Q Exp，直接把表达式节点捞出来
e1 :: Q Exp
e1 = [| 1 + 2 * 3 |]

e2 :: Q Exp
e2 = [| \x -> if x > 0 then x else -x |]

-- [d| ... |]  quote 一组声明
decs :: Q [Dec]
decs = [d|
    myInc :: Int -> Int
    myInc x = x + 1
  |]

-- [p| ... |] quote 一个模式
pat1 :: Q Pat
pat1 = [p| (x, Just y) |]

-- [t| ... |] quote 一个类型
ty1 :: Q Type
ty1 = [t| Int -> Maybe String |]

-- ============================================================
-- Part B: 手工拼 AST —— 不用 quote，直接调构造子
-- ============================================================
-- 目标：生成表达式   \x -> x * x + 1

powerExpr :: Q Exp
powerExpr = do
  x <- newName "x"
  let body = InfixE (Just (InfixE (Just (VarE x)) (VarE '(*)) (Just (VarE x))))
                    (VarE '(+))
                    (Just (LitE (IntegerL 1)))
  pure (LamE [VarP x] body)

-- 生成 n 次方：  \x -> x * x * ... * x   (n 个 x 相乘)
powerN :: Int -> Q Exp
powerN n = do
  x <- newName "x"
  let xs = replicate (max 1 n) (VarE x)
      body = foldl1 (\a b -> InfixE (Just a) (VarE '(*)) (Just b)) xs
  pure (LamE [VarP x] body)

-- ============================================================
-- Part C: Lift —— 把普通值转成 AST 字面量
-- ============================================================
-- 常用于把"编译期就知道的配置/常量"嵌进 DSL。

configLifted :: Q Exp
configLifted = lift ([("host", "prod.example.com"), ("port", "8080")]
                        :: [(String, String)])

-- ============================================================
-- Part D: 打印 AST
-- ============================================================
-- runQ :: Quasi m => Q a -> m a
-- 在 IO 里跑 Q 计算，然后用 pprint 打成 Haskell 源码形式。

showExp :: String -> Q Exp -> IO ()
showExp label q = do
  e <- runQ q
  putStrLn $ "--- " ++ label
  putStrLn   "  源码形式: "
  putStrLn $ "    " ++ pprint e
  putStrLn   "  AST:"
  putStrLn $ "    " ++ show e
  putStrLn ""

showDecs :: String -> Q [Dec] -> IO ()
showDecs label q = do
  ds <- runQ q
  putStrLn $ "--- " ++ label
  putStrLn   "  源码形式: "
  mapM_ (\d -> putStrLn $ "    " ++ pprint d) ds
  putStrLn ""

main :: IO ()
main = do
  putStrLn "Demo 40: Template Haskell 入门"
  putStrLn "============================================================"
  putStrLn ""

  putStrLn "Part A. Quote 把源码拎成 AST"
  putStrLn "------------------------------------------------------------"
  showExp "[| 1 + 2 * 3 |]"                  e1
  showExp "[| \\x -> if x > 0 then x else -x |]" e2
  showDecs "[d| myInc x = x + 1 |]"           decs

  putStrLn "Part B. 手工拼 AST"
  putStrLn "------------------------------------------------------------"
  showExp "手工: \\x -> x * x + 1"           powerExpr
  showExp "powerN 4: \\x -> x*x*x*x"         (powerN 4)

  putStrLn "Part C. Lift：把值转成 AST"
  putStrLn "------------------------------------------------------------"
  showExp "lift [(\"host\",...),(\"port\",...)]" configLifted

  putStrLn "============================================================"
  putStrLn "真实工程里的用法（都是这套加强版）："
  putStrLn "  * persistent   —— 表 schema 用 TH 生成 CRUD 代码"
  putStrLn "  * aeson-th     —— 用 TH 生成 ToJSON / FromJSON 实例"
  putStrLn "  * lens-th      —— makeLenses 一行生成 Lens"
  putStrLn "  * servant      —— 类型级路由 + TH 生成 client / swagger"
