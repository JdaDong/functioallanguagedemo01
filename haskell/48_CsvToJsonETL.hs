-- 48_CsvToJsonETL.hs — 完整 CSV→JSON ETL 流水线（含类型推导 + transform 管道 + 错误定位）
--
-- 行业背景：
--   * ETL（Extract-Transform-Load）是数据工程最常见的工作模式
--   * Haskell 在数据管道里的优势：纯函数让 transform 阶段天然可组合、易测试
--   * 工业实践常用 cassava + aeson；本 demo 不依赖任何外部包，纯 base 实现一份
--
-- 本 demo 串起一条端到端流水线，所有阶段都是纯函数：
--   1. parseCSV：手写 CSV 解析器（支持引号、双引号转义、CRLF）
--   2. inferTypes：扫一遍数据，推导每列的最窄类型（Int / Double / Bool / String）
--   3. transform：filter + map + group 三连，演示纯函数管道
--   4. encodeJSON：手写 JSON encoder（含字符串转义）
--   5. errorLocator：解析失败时报告"第 N 行第 M 列"
--
-- 运行：runghc 48_CsvToJsonETL.hs

{-# LANGUAGE LambdaCase #-}

module Main where

import Data.Char  (isDigit, toLower)
import Data.List  (foldl', groupBy, intercalate, sortBy)
import Data.Map.Strict (Map)
import qualified Data.Map.Strict as Map

------------------------------------------------------------
-- 1. CSV Parser（带行号/列号定位）
------------------------------------------------------------

-- ParseError 携带 0-base 行号 + 列号 + 原因
data ParseError = ParseError
  { errRow  :: Int
  , errCol  :: Int
  , errMsg  :: String
  } deriving (Eq)

instance Show ParseError where
  show (ParseError r c m) =
    "parse error at row " ++ show r ++ " col " ++ show c ++ ": " ++ m

-- 解析整个 CSV：返回每行字段数组
parseCSV :: String -> Either ParseError [[String]]
parseCSV s = goRows 0 s
  where
    goRows :: Int -> String -> Either ParseError [[String]]
    goRows _ "" = Right []
    goRows r input = do
      (row, rest) <- parseRow r 0 input
      case rest of
        ""      -> Right [row]
        _       -> do
          rows <- goRows (r + 1) rest
          Right (row : rows)

    -- 解析单行：返回字段列表 + 剩余字符串
    parseRow :: Int -> Int -> String -> Either ParseError ([String], String)
    parseRow r c input = do
      (field, rest) <- parseField r c input
      case rest of
        '\r':'\n':rest' -> Right ([field], rest')
        '\n':rest'      -> Right ([field], rest')
        ','  :rest'     -> do
          (more, rest'') <- parseRow r (c + 1) rest'
          Right (field : more, rest'')
        ""              -> Right ([field], "")
        (ch:_)          -> Left (ParseError r c
                            ("unexpected char " ++ show ch))

    -- 解析单字段：支持引号包裹（含 "" 转义）
    parseField :: Int -> Int -> String -> Either ParseError (String, String)
    parseField r c ('"':rest) = parseQuoted r c rest ""
    parseField _ _ rest       = Right (parseUnquoted rest "")

    parseUnquoted :: String -> String -> (String, String)
    parseUnquoted "" acc        = (reverse acc, "")
    parseUnquoted s'@(c:cs) acc
      | c == ',' || c == '\n' || c == '\r' = (reverse acc, s')
      | otherwise = parseUnquoted cs (c:acc)

    parseQuoted :: Int -> Int -> String -> String -> Either ParseError (String, String)
    parseQuoted r c "" _              = Left (ParseError r c "unclosed quoted field")
    parseQuoted r c ('"':'"':rest) acc = parseQuoted r c rest ('"':acc)
    parseQuoted _ _ ('"':rest) acc     = Right (reverse acc, rest)
    parseQuoted r c (ch:cs) acc        = parseQuoted r c cs (ch:acc)

------------------------------------------------------------
-- 2. 类型推导
------------------------------------------------------------

data CellTy = TyInt | TyDouble | TyBool | TyString
  deriving (Eq, Ord, Show)

-- 单元格"最窄类型"：能解析成 Int 就 Int，否则 Double，否则 Bool，否则 String
inferCell :: String -> CellTy
inferCell s
  | parseInt s    = TyInt
  | parseDouble s = TyDouble
  | parseBool s   = TyBool
  | otherwise     = TyString
  where
    parseInt cs = case cs of
      ('-':rest) -> not (null rest) && all isDigit rest
      _          -> not (null cs) && all isDigit cs
    parseDouble cs = case reads cs :: [(Double, String)] of
      [(_, "")] -> True
      _         -> False
    parseBool cs = case map toLower cs of
      "true"  -> True; "false" -> True
      _       -> False

-- 列类型：把列里所有 cell 的类型 join 成"最宽公共类型"
joinTy :: CellTy -> CellTy -> CellTy
joinTy a b
  | a == b                               = a
  | TyInt `elem` [a,b] && TyDouble `elem` [a,b] = TyDouble
  | otherwise                            = TyString

-- 给 [[String]] 推每一列的类型（第 0 行是 header，跳过）
inferColumnTypes :: [[String]] -> [CellTy]
inferColumnTypes []             = []
inferColumnTypes (_:body)
  | null body = []
  | otherwise =
      let nCol    = length (head body)
          colVals = [[row !! j | row <- body, j < length row] | j <- [0..nCol-1]]
          colTy c = case map inferCell c of
            []     -> TyString
            (t:ts) -> foldl' joinTy t ts
      in map colTy colVals

------------------------------------------------------------
-- 3. JSON 数据模型 + 编码器
------------------------------------------------------------

data JSON
  = JNull
  | JBool Bool
  | JNum  Double
  | JStr  String
  | JArr  [JSON]
  | JObj  [(String, JSON)]
  deriving (Eq, Show)

encodeJSON :: JSON -> String
encodeJSON = enc
  where
    enc JNull        = "null"
    enc (JBool b)    = if b then "true" else "false"
    enc (JNum x)
      | x == fromIntegral (truncate x :: Integer) = show (truncate x :: Integer)
      | otherwise                                  = show x
    enc (JStr s)     = '"' : concatMap esc s ++ "\""
    enc (JArr xs)    = "[" ++ intercalate "," (map enc xs) ++ "]"
    enc (JObj kvs)   = "{" ++ intercalate "," (map kv kvs) ++ "}"
    kv (k, v)        = '"' : concatMap esc k ++ "\":" ++ enc v
    esc '\\' = "\\\\"
    esc '"'  = "\\\""
    esc '\n' = "\\n"
    esc '\r' = "\\r"
    esc '\t' = "\\t"
    esc c
      | fromEnum c < 0x20 = "\\u" ++ pad4 (showHex (fromEnum c))
      | otherwise         = [c]
    pad4 s = replicate (4 - length s) '0' ++ s
    showHex 0 = "0"
    showHex n = go n ""
      where
        go 0 acc = acc
        go k acc =
          let d = k `mod` 16
              c = if d < 10 then toEnum (fromEnum '0' + d)
                            else toEnum (fromEnum 'a' + d - 10)
          in go (k `div` 16) (c:acc)

-- pretty print（按一级缩进，足够 demo 用）
prettyJSON :: JSON -> String
prettyJSON = go 0
  where
    indent n = replicate (n*2) ' '
    go _ JNull       = "null"
    go _ (JBool b)   = if b then "true" else "false"
    go _ (JNum x)
      | x == fromIntegral (truncate x :: Integer) = show (truncate x :: Integer)
      | otherwise                                  = show x
    go _ (JStr s)    = encodeJSON (JStr s)
    go _ (JArr [])   = "[]"
    go n (JArr xs)   = "[\n"
                       ++ intercalate ",\n" [indent (n+1) ++ go (n+1) x | x <- xs]
                       ++ "\n" ++ indent n ++ "]"
    go _ (JObj [])   = "{}"
    go n (JObj kvs)  = "{\n"
                       ++ intercalate ",\n"
                            [ indent (n+1) ++ encodeJSON (JStr k) ++ ": "
                              ++ go (n+1) v
                            | (k, v) <- kvs ]
                       ++ "\n" ++ indent n ++ "}"

------------------------------------------------------------
-- 4. CSV row + 推导类型 -> JSON
------------------------------------------------------------

cellToJSON :: CellTy -> String -> JSON
cellToJSON TyString s   = JStr s
cellToJSON TyInt s      = case reads s :: [(Integer, String)] of
  [(n, "")] -> JNum (fromIntegral n)
  _         -> JStr s
cellToJSON TyDouble s   = case reads s :: [(Double, String)] of
  [(x, "")] -> JNum x
  _         -> JStr s
cellToJSON TyBool s     = case map toLower s of
  "true"  -> JBool True
  "false" -> JBool False
  _       -> JStr s

rowsToJSON :: [String] -> [CellTy] -> [[String]] -> JSON
rowsToJSON headers tys body =
  JArr [ JObj [ (h, cellToJSON ty c) | (h, ty, c) <- zip3 headers tys row ]
       | row <- body
       , length row == length headers ]

------------------------------------------------------------
-- 5. transform 管道：filter + map + groupBy
------------------------------------------------------------
-- 这一节展示"业务变换"完全用纯函数表达：
--   * 过滤：只保留某列大于阈值的行
--   * 映射：给每行加一个派生列
--   * 分组：按某列归并并求和

-- 通用 row 视图：列名 -> 原始字符串
type Row = Map String String

mkRow :: [String] -> [String] -> Row
mkRow hs cs = Map.fromList (zip hs cs)

unRow :: [String] -> Row -> [String]
unRow hs r = [ Map.findWithDefault "" h r | h <- hs ]

-- (a) 只保留 age >= 18 的行
filterAdults :: [Row] -> [Row]
filterAdults = filter $ \r ->
  case Map.lookup "age" r >>= readMaybeInt of
    Just n  -> n >= 18
    Nothing -> False

-- (b) 给每行加一列 "tag" = grown_up
addTag :: [Row] -> [Row]
addTag = map (Map.insert "tag" "grown_up")

-- (c) 按 city 分组，统计人数
groupByCity :: [Row] -> [(String, Int)]
groupByCity rs =
  let ks = [Map.findWithDefault "?" "city" r | r <- rs]
      sorted = sortBy compare ks
      grp    = groupBy (==) sorted
  in [(head g, length g) | g <- grp]

readMaybeInt :: String -> Maybe Int
readMaybeInt s = case reads s :: [(Int, String)] of
  [(n, "")] -> Just n
  _         -> Nothing

------------------------------------------------------------
-- 6. Demo 数据
------------------------------------------------------------

sampleCSV :: String
sampleCSV = unlines
  [ "name,age,city,active"
  , "Alice,30,Beijing,true"
  , "Bob,17,Shanghai,false"
  , "\"Carol, the brave\",25,Beijing,true"
  , "Dave,42,Shanghai,true"
  , "Eve,15,Shenzhen,true"
  , "Frank,55,Shenzhen,false"
  ]

-- 故意写错的 CSV：第 4 行有未闭合引号
brokenCSV :: String
brokenCSV = unlines
  [ "name,age"
  , "Alice,30"
  , "Bob,17"
  , "Carol,\"25"
  , "Dave,42"
  ]

------------------------------------------------------------
-- 7. main：完整流水线
------------------------------------------------------------

main :: IO ()
main = do
  putStrLn "=== CSV -> JSON ETL Pipeline ==="

  -- ---- (1) Parse ----
  putStrLn "\n[1] parse CSV"
  rows <- case parseCSV sampleCSV of
    Left e   -> do { putStrLn ("✗ " ++ show e); return [] }
    Right rs -> do
      putStrLn $ "  parsed " ++ show (length rs) ++ " rows ("
                 ++ show (length (head rs)) ++ " cols)"
      return rs

  -- ---- (2) infer types ----
  putStrLn "\n[2] inferred types per column"
  let headers = head rows
      body    = tail rows
      tys     = inferColumnTypes rows
  mapM_ (\(h,t) -> putStrLn $ "    " ++ h ++ " :: " ++ show t)
        (zip headers tys)

  -- ---- (3) transform 管道 ----
  putStrLn "\n[3] transform: filter age>=18, addTag, groupByCity"
  let rs0  = map (mkRow headers) body
      rs1  = filterAdults rs0
      rs2  = addTag rs1
      grp  = groupByCity rs1
  putStrLn $ "  after filter (age>=18): " ++ show (length rs1) ++ " rows"
  putStrLn $ "  after addTag           : " ++ show (length rs2) ++ " rows"
  putStrLn   "  group by city          :"
  mapM_ (\(c,n) -> putStrLn $ "    " ++ c ++ " -> " ++ show n) grp

  -- ---- (4) encode JSON：原始数据 + transform 结果 ----
  putStrLn "\n[4] encoded JSON (transformed body)"
  let headers' = headers ++ ["tag"]
      tys'     = tys     ++ [TyString]
      body'    = [unRow headers' r | r <- rs2]
      json     = rowsToJSON headers' tys' body'
  putStrLn (prettyJSON json)

  -- ---- (5) 故意制造解析错误，演示行/列定位 ----
  putStrLn "\n[5] error locator demo"
  case parseCSV brokenCSV of
    Right _  -> putStrLn "  (unexpectedly parsed broken CSV?)"
    Left  e  -> putStrLn ("  ✓ " ++ show e)

  putStrLn "\n=== Done ==="
