{-# LANGUAGE LambdaCase #-}
-- ============================================================
-- Demo 10: Parser Combinators — 解析器组合子
--
-- 核心概念：
--   1. 解析器的代数本质：String -> [(a, String)]
--   2. 基本组合子：char, string, satisfy, many, choice
--   3. Monad 实例：支持 do-notation
--   4. Applicative 实例：liftA2 组合
--   5. 实际应用：JSON 解析器、计算器、CSV
-- ============================================================

module Main where

import Control.Applicative (Alternative(..), (<|>), many, some)
import Data.Char           (isDigit, isLetter, isAlphaNum, isSpace)

-- ============================================================
-- Part 1: Parser 类型定义
-- ============================================================
-- 
-- 一个 Parser a 是一个函数：接收输入字符串
-- 返回解析结果列表（每个结果是 (值, 剩余字符串)）
-- 列表允许回溯（多个可能的解析路径）

newtype Parser a = Parser
  { runParser :: String -> [(a, String)] }

-- ============================================================
-- Part 2: Functor / Applicative / Monad / Alternative
-- ============================================================

instance Functor Parser where
  fmap f (Parser p) = Parser $ \input ->
    [ (f x, rest) | (x, rest) <- p input ]

instance Applicative Parser where
  pure x = Parser $ \input -> [(x, input)]
  (Parser pf) <*> (Parser px) = Parser $ \input ->
    [ (f x, rest2) | (f, rest1) <- pf input
                    , (x, rest2) <- px rest1 ]

instance Monad Parser where
  (Parser px) >>= f = Parser $ \input ->
    [ (y, rest2) | (x, rest1) <- px input
                  , (y, rest2) <- runParser (f x) rest1 ]

instance Alternative Parser where
  empty = Parser $ const []
  (Parser p1) <|> (Parser p2) = Parser $ \input ->
    case p1 input of
      []     -> p2 input       -- p1 失败，尝试 p2
      result -> result          -- p1 成功，直接返回

-- ============================================================
-- Part 3: 基本组合子
-- ============================================================

-- 解析并返回单个字符
satisfy :: (Char -> Bool) -> Parser Char
satisfy pred = Parser $ \case
    (c:cs) | pred c -> [(c, cs)]
    _              -> []

-- 解析特定字符
char :: Char -> Parser Char
char c = satisfy (== c)

-- 解析特定字符串
string :: String -> Parser String
string ""     = pure ""
string (c:cs) = (:) <$> char c <*> string cs

-- 解析一个或多个满足条件的字符
someSatisfy :: (Char -> Bool) -> Parser String
someSatisfy = some . satisfy

-- 解析零个或多个
manySatisfy :: (Char -> Bool) -> Parser String
manySatisfy = many . satisfy

-- 跳过空白
spaces :: Parser String
spaces = manySatisfy isSpace

token :: Parser a -> Parser a
token p = spaces *> p <* spaces

-- ============================================================
-- Part 4: 派生组合子
-- ============================================================

digit :: Parser Char
digit = satisfy isDigit

letter :: Parser Char
letter = satisfy isLetter

alphaNum :: Parser Char
alphaNum = satisfy isAlphaNum

-- 解析整数（支持正负号）
intParser :: Parser Int
intParser = token $ do
  sign <- (char '-' *> pure negate) <|> (char '+' *> pure id) <|> pure id
  digits <- some digit
  pure $ sign (read digits)

-- 解析浮点数
floatParser :: Parser Double
floatParser = token $ do
  sign <- (char '-' *> pure negate) <|> (char '+' *> pure id) <|> pure id
  intPart <- some digit
  fracPart <- (char '.' *> some digit) <|> pure "0"
  pure $ sign (read (intPart ++ "." ++ fracPart))

-- 用括号包裹
parenthesized :: Parser a -> Parser a
parenthesized p = char '(' *> p <* char ')'

-- 用逗号分隔
commaSep :: Parser a -> Parser [a]
commaSep p = (p `sepBy` char ',')
  where
    sepBy p' sep = (:) <$> p' <*> many (sep *> p') <|> pure []

-- ============================================================
-- Part 5: 应用 — 算术表达式求值器
-- ============================================================

data Expr
  = EConst Int
  | EAdd   Expr Expr
  | EMul   Expr Expr
  | ENeg   Expr
  deriving Show

-- 表达式语法（优先级）:
--   expr   ::= term (('+' | '-') term)*
--   term   ::= factor (('*' | '/') factor)*
--   factor ::= int | '(' expr ')' | '-' factor

expr :: Parser Expr
expr = do
  t <- term
  more t
  where
    more acc = (do
      op <- char '+' <|> char '-'
      next <- term
      more (if op == '+' then EAdd acc next else EAdd acc (ENeg next)))
      <|> pure acc

term :: Parser Expr
term = do
  f <- factor
  more f
  where
    more acc = (do
      char '*'
      next <- factor
      more (EMul acc next))
      <|> pure acc

factor :: Parser Expr
factor = token $
  (EConst <$> intParser)
  <|> parenthesized expr
  <|> (char '-' *> (ENeg <$> factor))

-- 求值
evalExpr :: Expr -> Int
evalExpr (EConst n)    = n
evalExpr (EAdd e1 e2)   = evalExpr e1 + evalExpr e2
evalExpr (EMul e1 e2)   = evalExpr e1 * evalExpr e2
evalExpr (ENeg e)       = -(evalExpr e)

parseAndEval :: String -> Maybe Int
parseAndEval input =
  case runParser (expr <* spaces) input of
    [(result, "")] -> Just (evalExpr result)
    _              -> Nothing

-- ============================================================
-- Part 6: 应用 — JSON 解析器（简化版）
-- ============================================================

data JValue
  = JNull
  | JBool Bool
  | JNumber Double
  | JString String
  | JArray [JValue]
  | JObject [(String, JValue)]
  deriving Show

jsonNull :: Parser JValue
jsonNull = JNull <$ string "null"

jsonBool :: Parser JValue
jsonBool = (JBool True <$ string "true")
      <|> (JBool False <$ string "false")

jsonNumber :: Parser JValue
jsonNumber = JNumber <$> floatParser

jstringInner :: Parser String
jstringInner = many (satisfy (/= '"'))

jsonString :: Parser JValue
jsonString = JString <$> (char '"' *> jstringInner <* char '"')

-- 需要处理左递归的 mutual recursion，用 lazy 包装
jsonValue :: Parser JValue
jsonValue = jsonNull
       <|> jsonBool
       <|> jsonNumber
       <|> jsonString
       <|> jsonArray
       <|> jsonObject

jsonArray :: Parser JValue
jsonArray = JArray <$> (char '[' *> commaSep jsonValue <* char ']')

jsonObject :: Parser JValue
jsonObject = JObject <$>
  (char '{' *> commaSep jsonPair <* char '}')
  where
    jsonPair = do
      key <- jsonString
      char ':'
      val <- jsonValue
      case key of
        JString k -> pure (k, val)
        _         -> error "object keys must be strings"

parseJSON :: String -> Maybe JValue
parseJSON input =
  case runParser (spaces *> jsonValue <* spaces) input of
    [(result, [])] -> Just result
    _              -> Nothing

-- ============================================================
-- Part 7: 应用 — CSV/Key-Value 解析器
-- ============================================================

type KV = (String, String)

kvLine :: Parser KV
kvLine = do
  key <- manySatisfy (/= '=')
  char '='
  val <- manySatisfy (/= '\n')
  pure (key, val)

kvFile :: Parser [(String, String)]
kvFile = many (kvLine <* (char '\n' <|> pure '\n'))

parseKV :: String -> [(String, String)]
parseKV content =
  case runParser kvFile content of
    [(result, _)] -> result
    _             -> []

-- ============================================================
-- Main
-- ============================================================

main :: IO ()
main = do
  putStrLn "=========================================="
  putStrLn "Demo 10: Parser Combinators — 解析器组合子"
  putStrLn "==========================================\n"

  -- ---- 基础组合子 ----
  putStrLn "=== 1. 基础组合子 ==="
  let r1 = runParser (char 'h') "hello"
  putStrLn $ "  parse 'h' from \"hello\": " ++ show r1
  
  let r2 = runParser (string "func") "functional"
  putStrLn $ "  parse \"func\" from \"functional\": " ++ show r2
  
  let r3 = runParser (some letter) "123abc"
  putStrLn $ "  some letter from \"123abc\": " ++ show r3
  putStrLn ""

  -- ---- 算术表达式求值 ----
  putStrLn "=== 2. 算术表达式求值 ==="
  let expressions =
        [ "2 + 3 * 4"            -- 14
        , "(2 + 3) * 4"          -- 20  
        , "-5 + 10"              -- 5
        , "1 + 2 + 3 + 4"        -- 10
        , "2 * 3 + 4 * 5"        -- 26
        ]
  
  mapM_ (\expr -> case parseAndEval expr of
    Just result -> putStrLn $ "  " ++ expr ++ " = " ++ show result
    Nothing     -> putStrLn $ "  " ++ expr ++ " → 解析失败!"
    ) expressions
  putStrLn ""

  -- ---- JSON 解析 ----
  putStrLn "=== 3. JSON 解析 ==="
  let jsonSamples =
        [ "null"
        , "true"
        , "42"
        , "3.14"
        , "\"hello\""
        , "[1,2,3]"
        , "{\"name\":\"Alice\",\"age\":30}"
        ]
  
  mapM_ (\js -> case parseJSON js of
    Just value -> putStrLn $ "  " ++ js ++ "\n    => " ++ show value
    Nothing    -> putStrLn $ "  " ++ js ++ "\n    => 解析失败"
    ) jsonSamples
  putStrLn ""

  -- ---- Key-Value 解析 ----
  putStrLn "=== 4. 配置文件解析 (Key=Value) ==="
  let configContent = unlines
        [ "host=localhost"
        , "port=8080"
        , "debug=true"
        , "timeout=30"
        ]
      config = parseKV configContent
  putStrLn $ "  解析结果:"
  mapM_ (\(k,v) -> putStrLn $ "    " ++ k ++ " = " ++ v) config
  putStrLn ""
  
  putStrLn "=========================================="
