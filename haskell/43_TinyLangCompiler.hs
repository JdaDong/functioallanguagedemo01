-- 43_TinyLangCompiler.hs
-- ============================================================================
-- 行业应用场景：小语言 / DSL 编译器（Haskell 最闪亮的领域）
-- ----------------------------------------------------------------------------
-- 背景：GHC、Pandoc、Elm 编译器、shellcheck、Hasura 都是 Haskell 写的。
-- 原因：ADT + 模式匹配天然适配 AST 操作；类型系统让中间表示不会走样。
--
-- 本 demo 把一门"最小但完整"的函数式语言一次性串起来：
--     parser  →  typechecker (HM 简化版) →  evaluator
-- 每一步都是纯函数，不依赖任何外部库。
--
-- 语言特性：Int / Bool / let / lambda / 函数调用 / if / +, ==
--
-- 运行： runghc 43_TinyLangCompiler.hs
-- ============================================================================

module Main where

import Control.Exception (evaluate, try, SomeException)
import Data.Char (isAlpha, isDigit, isSpace)

-- ===========================================================================
-- 1. AST
-- ===========================================================================

data Expr
    = EInt  Int
    | EBool Bool
    | EVar  String
    | ELam  String Expr           -- \x -> body
    | EApp  Expr Expr
    | ELet  String Expr Expr      -- let x = e1 in e2
    | EIf   Expr Expr Expr
    | EAdd  Expr Expr
    | EEq   Expr Expr
    deriving Show

-- ===========================================================================
-- 2. Parser —— 手写递归下降，不用任何库（复习 10 号 demo 的思路）
-- ===========================================================================

-- 极简 token
data Tok = TInt Int | TIdent String | TSym String
    deriving (Show, Eq)

keywords :: [String]
keywords = ["let","in","if","then","else","true","false"]

tokenize :: String -> [Tok]
tokenize [] = []
tokenize (c:cs)
    | isSpace c = tokenize cs
    | isDigit c = let (n, rest) = span isDigit (c:cs)
                  in TInt (read n) : tokenize rest
    | isAlpha c = let (w, rest) = span (\x -> isAlpha x || isDigit x) (c:cs)
                  in TIdent w : tokenize rest
    | c == '=' && take 1 cs == "=" = TSym "==" : tokenize (drop 1 cs)
    | c == '-' && take 1 cs == ">" = TSym "->" : tokenize (drop 1 cs)
    | c `elem` "=+()\\-" = TSym [c] : tokenize cs
    | otherwise = error ("lex error near: " ++ [c])

-- Parser 用最朴素的 [Tok] -> (ast, 剩余 Tok) 签名
type P a = [Tok] -> (a, [Tok])

parseExpr :: P Expr
parseExpr ts = case ts of
    TIdent "let" : TIdent x : TSym "=" : rest1 ->
        let (e1, rest2) = parseExpr rest1
        in case rest2 of
            TIdent "in" : rest3 ->
                let (e2, rest4) = parseExpr rest3
                in (ELet x e1 e2, rest4)
            _ -> error "expected 'in'"
    TIdent "if" : rest1 ->
        let (c, rest2) = parseExpr rest1
        in case rest2 of
            TIdent "then" : rest3 ->
                let (t, rest4) = parseExpr rest3
                in case rest4 of
                    TIdent "else" : rest5 ->
                        let (e, rest6) = parseExpr rest5
                        in (EIf c t e, rest6)
                    _ -> error "expected 'else'"
            _ -> error "expected 'then'"
    TSym "\\" : TIdent x : TSym "->" : rest1 ->
        let (body, rest2) = parseExpr rest1
        in (ELam x body, rest2)
    _ -> parseAddEq ts

-- +, == 左结合，优先级最低
parseAddEq :: P Expr
parseAddEq ts =
    let (lhs, rest) = parseApp ts
    in case rest of
        TSym "+"  : rest1 -> let (rhs, rest2) = parseAddEq rest1
                             in (EAdd lhs rhs, rest2)
        TSym "==" : rest1 -> let (rhs, rest2) = parseAddEq rest1
                             in (EEq  lhs rhs, rest2)
        _ -> (lhs, rest)

-- 函数调用左结合： f x y = (f x) y
parseApp :: P Expr
parseApp ts =
    let (h, rest) = parseAtom ts
    in go h rest
  where
    go acc rest = case rest of
        (TInt _ : _)     -> let (a, r) = parseAtom rest in go (EApp acc a) r
        (TIdent w : _)
            | w `notElem` ["in","then","else"] ->
                let (a, r) = parseAtom rest in go (EApp acc a) r
        (TSym "(" : _)   -> let (a, r) = parseAtom rest in go (EApp acc a) r
        _                -> (acc, rest)

parseAtom :: P Expr
parseAtom ts = case ts of
    TInt n : rest            -> (EInt n, rest)
    TIdent "true"  : rest    -> (EBool True, rest)
    TIdent "false" : rest    -> (EBool False, rest)
    TIdent x : rest          -> (EVar x, rest)
    TSym "(" : rest1         ->
        let (e, rest2) = parseExpr rest1
        in case rest2 of
            TSym ")" : rest3 -> (e, rest3)
            _ -> error "expected ')'"
    _ -> error ("unexpected: " ++ show ts)

parse :: String -> Expr
parse s = case parseExpr (tokenize s) of
    (e, []) -> e
    (_, xs) -> error ("trailing tokens: " ++ show xs)

-- ===========================================================================
-- 3. Type checker —— Hindley-Milner 的简化版（无 let 泛化，但够用）
-- ===========================================================================

data Ty = TInt' | TBool' | TVar Int | TFun Ty Ty
    deriving Eq

instance Show Ty where
    show TInt'      = "Int"
    show TBool'     = "Bool"
    show (TVar i)   = "t" ++ show i
    show (TFun a b) = "(" ++ show a ++ " -> " ++ show b ++ ")"

type Subst = [(Int, Ty)]

apply :: Subst -> Ty -> Ty
apply s t@(TVar i)   = maybe t (apply s) (lookup i s)
apply s (TFun a b)   = TFun (apply s a) (apply s b)
apply _ t            = t

applyEnv :: Subst -> [(String,Ty)] -> [(String,Ty)]
applyEnv s = map (\(x,t) -> (x, apply s t))

-- occurs check：防无限类型（如 t0 = t0 -> t0）
occurs :: Int -> Ty -> Bool
occurs i (TVar j)   = i == j
occurs i (TFun a b) = occurs i a || occurs i b
occurs _ _          = False

unify :: Ty -> Ty -> Subst
unify a b | a == b = []
unify (TVar i) t
    | occurs i t = error ("infinite type: t" ++ show i ++ " = " ++ show t)
    | otherwise  = [(i, t)]
unify t (TVar i) = unify (TVar i) t
unify (TFun a1 b1) (TFun a2 b2) =
    let s1 = unify a1 a2
        s2 = unify (apply s1 b1) (apply s1 b2)
    in s2 ++ s1
unify a b = error ("type mismatch: " ++ show a ++ " vs " ++ show b)

-- 返回 (类型, 积累 subst, 下一个可用 fresh id)
infer :: [(String,Ty)] -> Int -> Expr -> (Ty, Subst, Int)
infer _ n (EInt _)  = (TInt',  [], n)
infer _ n (EBool _) = (TBool', [], n)
infer env n (EVar x) = case lookup x env of
    Just t  -> (t, [], n)
    Nothing -> error ("unbound variable: " ++ x)
infer env n (ELam x body) =
    let tv = TVar n
        (tb, s, n') = infer ((x, tv) : env) (n + 1) body
    in (TFun (apply s tv) tb, s, n')
infer env n (EApp f a) =
    let (tf, s1, n1) = infer env n f
        (ta, s2, n2) = infer (applyEnv s1 env) n1 a
        tr = TVar n2
        s3 = unify (apply s2 tf) (TFun ta tr)
    in (apply s3 tr, s3 ++ s2 ++ s1, n2 + 1)
infer env n (ELet x e1 e2) =
    let (t1, s1, n1) = infer env n e1
        (t2, s2, n2) = infer ((x, t1) : applyEnv s1 env) n1 e2
    in (t2, s2 ++ s1, n2)
infer env n (EIf c t e) =
    let (tc, s1, n1) = infer env n c
        s1'          = unify tc TBool' ++ s1
        (tt, s2, n2) = infer (applyEnv s1' env) n1 t
        (te, s3, n3) = infer (applyEnv (s2 ++ s1') env) n2 e
        sfinal       = unify (apply s3 tt) te ++ s3 ++ s2 ++ s1'
    in (apply sfinal tt, sfinal, n3)
infer env n (EAdd a b) =
    let (ta, s1, n1) = infer env n a
        s1'          = unify ta TInt' ++ s1
        (tb, s2, n2) = infer (applyEnv s1' env) n1 b
        s2'          = unify tb TInt' ++ s2 ++ s1'
    in (TInt', s2', n2)
infer env n (EEq a b) =
    let (ta, s1, n1) = infer env n a
        (tb, s2, n2) = infer (applyEnv s1 env) n1 b
        s3           = unify (apply s2 ta) tb
    in (TBool', s3 ++ s2 ++ s1, n2)

typeOf :: Expr -> Ty
typeOf e = let (t, s, _) = infer [] 0 e
          -- 强制 subst 被完整求值，触发其中可能潜藏的 unify 错误
          -- （Haskell 惰性下，未被 apply 到的 subst 条目里的 error 不会抛出）
          in length s `seq` apply s t

-- ===========================================================================
-- 4. Evaluator
-- ===========================================================================

data Val = VInt Int | VBool Bool | VClosure String Expr [(String,Val)]

instance Show Val where
    show (VInt n)       = show n
    show (VBool b)      = if b then "true" else "false"
    show (VClosure {})  = "<function>"

eval :: [(String,Val)] -> Expr -> Val
eval _   (EInt n)      = VInt n
eval _   (EBool b)     = VBool b
eval env (EVar x)      = case lookup x env of
    Just v  -> v
    Nothing -> error ("unbound at runtime: " ++ x)
eval env (ELam x b)    = VClosure x b env
eval env (EApp f a)    = case eval env f of
    VClosure x b cEnv -> eval ((x, eval env a) : cEnv) b
    _                 -> error "not a function"
eval env (ELet x e1 e2) = eval ((x, eval env e1) : env) e2
eval env (EIf c t e)   = case eval env c of
    VBool True  -> eval env t
    VBool False -> eval env e
    _           -> error "if on non-bool"
eval env (EAdd a b) = case (eval env a, eval env b) of
    (VInt x, VInt y) -> VInt (x + y)
    _                -> error "+ on non-int"
eval env (EEq a b) = case (eval env a, eval env b) of
    (VInt x,  VInt y ) -> VBool (x == y)
    (VBool x, VBool y) -> VBool (x == y)
    _                  -> error "== on mismatched types"

-- ===========================================================================
-- 5. Demo
-- ===========================================================================

runOk :: String -> IO ()
runOk src = do
    let e = parse src
        t = typeOf e
        v = eval [] e
    putStrLn $ "  源码:  " ++ src
    putStrLn $ "  类型:  " ++ show t
    putStrLn $ "  求值:  " ++ show v
    putStrLn ""

runTypeErr :: String -> IO ()
runTypeErr src = do
    putStrLn $ "  源码:  " ++ src
    let e = parse src
    result <- try (evaluate (typeOf e)) :: IO (Either SomeException Ty)
    case result of
        Right t -> putStrLn $ "  类型检查意外通过: " ++ show t
        Left ex -> putStrLn $ "  类型检查报错 ✓: " ++ show ex
    putStrLn ""

main :: IO ()
main = do
    putStrLn "=== TinyLang：parser → type checker → evaluator ==="
    putStrLn ""

    putStrLn "-- [1] 恒等函数应用 --"
    runOk "let id = \\x -> x in id 42"

    putStrLn "-- [2] 嵌套 let + 加法 --"
    runOk "let x = 10 in let y = 32 in x + y"

    putStrLn "-- [3] 高阶函数：twice --"
    runOk "let twice = \\f -> \\x -> f (f x) in let inc = \\n -> n + 1 in twice inc 10"

    putStrLn "-- [4] if / == --"
    runOk "let x = 5 in if x == 5 then 100 else 0"

    putStrLn "-- [5] 类型不匹配：1 + true，应在类型检查阶段报错 --"
    runTypeErr "let bad = 1 + true in bad"

-- ---------------------------------------------------------------------------
-- 关键看点
-- ---------------------------------------------------------------------------
-- 1. ADT 完美表达 AST：新加语言结构 = 加一个构造子 + 每个阶段加一个分支
-- 2. 类型推导 = unify + substitution：Haskell 的模式匹配让这段算法异常紧凑
-- 3. Closure 就是个带环境的 ADT：不需要任何"对象/类"的概念
-- 4. parser / typechecker / eval 完全解耦，可以各自测试、各自优化
-- 这就是 GHC / Elm / Pandoc 的基础范式：ADT + 模式匹配 + 解释器分层。
