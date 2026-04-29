{-
  Haskell 函数式编程 Demo 2: 类型类与 Functor/Monad

  类型类 (Type Class) 是 Haskell 的多态机制，类似于接口但更强大。
  Functor、Applicative、Monad 是 Haskell 中最重要的抽象概念，
  它们描述了"在上下文中进行计算"的通用模式。
-}

module Main where

-- ========== 自定义类型类 ==========

-- 定义一个"可以转为描述字符串"的类型类
class Describable a where
  describe :: a -> String

-- 形状的代数数据类型
data Shape = Circle Double
           | Rectangle Double Double
           | Triangle Double Double Double
           deriving (Show)

-- 为 Shape 实现 Describable
instance Describable Shape where
  describe (Circle r) = "圆形, 半径=" ++ show r ++ ", 面积=" ++ show (pi * r * r)
  describe (Rectangle w h) = "矩形, 宽=" ++ show w ++ " 高=" ++ show h ++
                              ", 面积=" ++ show (w * h)
  describe (Triangle a b c) = "三角形, 三边=" ++ show a ++ "," ++
                               show b ++ "," ++ show c

-- 为常见类型实现
instance Describable Int where
  describe n = "整数 " ++ show n ++ " (" ++ (if even n then "偶数" else "奇数") ++ ")"

instance Describable Bool where
  describe True  = "真 ✓"
  describe False = "假 ✗"

-- ========== Maybe Monad: 优雅处理错误 ==========

-- 安全的除法
safeDivide :: Double -> Double -> Maybe Double
safeDivide _ 0 = Nothing
safeDivide a b = Just (a / b)

-- 安全的平方根 (只接受非负数)
safeSqrt :: Double -> Maybe Double
safeSqrt x
  | x < 0    = Nothing
  | otherwise = Just (sqrt x)

-- 安全的对数
safeLog :: Double -> Maybe Double
safeLog x
  | x <= 0   = Nothing
  | otherwise = Just (log x)

-- 使用 do 表示法链式调用 (Monad 的魔力!)
-- 计算: sqrt(100/x) 的 log
complexCalc :: Double -> Maybe Double
complexCalc x = do
  divided  <- safeDivide 100 x     -- 100/x, 如果 x=0 则失败
  rooted   <- safeSqrt divided      -- sqrt(100/x), 如果为负则失败
  safeLog rooted                    -- log(sqrt(100/x)), 如果为0则失败

-- ========== Either: 带错误信息的计算 ==========

data ValidationError = TooShort | TooLong | InvalidChar Char
  deriving (Show)

validateUsername :: String -> Either ValidationError String
validateUsername name
  | length name < 3  = Left TooShort
  | length name > 20 = Left TooLong
  | not (all isValidChar name) = Left (InvalidChar (head (filter (not . isValidChar) name)))
  | otherwise = Right name
  where isValidChar c = c `elem` (['a'..'z'] ++ ['A'..'Z'] ++ ['0'..'9'] ++ "_")

-- ========== 自定义 Functor: 二叉树 ==========

data Tree a = Leaf | Node a (Tree a) (Tree a)
  deriving (Show)

-- 让 Tree 成为 Functor (可以 fmap)
instance Functor Tree where
  fmap _ Leaf         = Leaf
  fmap f (Node x l r) = Node (f x) (fmap f l) (fmap f r)

-- 构建示例树
sampleTree :: Tree Int
sampleTree = Node 1
               (Node 2 (Node 4 Leaf Leaf) (Node 5 Leaf Leaf))
               (Node 3 (Node 6 Leaf Leaf) Leaf)

-- 树的折叠
foldTree :: (a -> b -> b) -> b -> Tree a -> b
foldTree _ acc Leaf         = acc
foldTree f acc (Node x l r) = foldTree f (f x (foldTree f acc r)) l

-- 树转列表 (中序遍历)
treeToList :: Tree a -> [a]
treeToList = foldTree (:) []

-- ========== 主函数 ==========

main :: IO ()
main = do
  putStrLn "=== 类型类 ==="
  putStrLn $ describe (Circle 5.0)
  putStrLn $ describe (Rectangle 3.0 4.0)
  putStrLn $ describe (Triangle 3.0 4.0 5.0)
  putStrLn $ describe (42 :: Int)
  putStrLn $ describe True

  putStrLn "\n=== Maybe Monad ==="
  putStrLn $ "complexCalc(25) = " ++ show (complexCalc 25)
  putStrLn $ "complexCalc(0)  = " ++ show (complexCalc 0)
  putStrLn $ "complexCalc(-4) = " ++ show (complexCalc (-4))

  putStrLn "\n=== Either 验证 ==="
  putStrLn $ "validate 'ab':      " ++ show (validateUsername "ab")
  putStrLn $ "validate 'alice':   " ++ show (validateUsername "alice")
  putStrLn $ "validate 'a@b':     " ++ show (validateUsername "a@b")

  putStrLn "\n=== Functor: 二叉树 ==="
  putStrLn $ "原始树:     " ++ show sampleTree
  putStrLn $ "fmap (*2):  " ++ show (fmap (*2) sampleTree)
  putStrLn $ "fmap show:  " ++ show (fmap show sampleTree)
  putStrLn $ "树转列表:   " ++ show (treeToList sampleTree)
  putStrLn $ "树元素求和: " ++ show (foldTree (+) 0 sampleTree)
