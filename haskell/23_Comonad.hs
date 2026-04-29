-- ============================================================
-- Demo 23: Comonad —— Functor 的镜像 / 上下文感知计算
--
-- Monad  用于 "把副作用放到值上"     (a -> m b) 的串联
-- Comonad 用于 "从上下文里取出一个值"（w a -> b） 的聚合
-- 两者是类型级别的对偶：
--
--   Monad:    return  :: a -> m a           join    :: m (m a) -> m a
--   Comonad:  extract :: w a -> a           duplicate :: w a -> w (w a)
--
-- 本 Demo 用 3 个最经典的 Comonad 直觉：
--   a) NonEmpty 流:   extract 取当前值, extend 做"扫描"聚合
--   b) Zipper:        一维 cellular automaton 的上下文 (左邻居/当前/右邻居)
--   c) Store:         builder 模式的 FP 表达 (focus + 观察函数)
--
-- 不依赖 comonad / lens 库，全部手写。
-- 运行: runhaskell 23_Comonad.hs
-- ============================================================

module Main where

-- ============================================================
-- 0. 典型 Comonad 定义
-- ============================================================

class Functor w => Comonad w where
  extract   :: w a -> a
  duplicate :: w a -> w (w a)
  extend    :: (w a -> b) -> w a -> w b
  extend f  = fmap f . duplicate

-- ============================================================
-- 1. NonEmpty 流 Comonad —— 扫描聚合
-- ============================================================

data NE a = a :| [a] deriving Show
infixr 5 :|

instance Functor NE where
  fmap f (x :| xs) = f x :| map f xs

instance Comonad NE where
  extract   (x :| _)      = x
  duplicate ne@(_ :| xs)  = ne :| tails_ xs
    where tails_ []       = []
          tails_ (y:ys)   = (y :| ys) : tails_ ys

-- 典型用法：extend 用"当前位置能看到的整个后缀"做聚合
-- 等价于 scanr —— 从右向左的累积
runningMax :: NE Int -> NE Int
runningMax = extend (\(x :| xs) -> maximum (x : xs))

runningAvg :: NE Double -> NE Double
runningAvg = extend (\(x :| xs) -> let ys = x : xs in sum ys / fromIntegral (length ys))

-- ============================================================
-- 2. 一维 Zipper Comonad —— cellular automaton
-- ============================================================

-- 左半无穷流（反向）, focus, 右半无穷流
data Z a = Z [a] a [a]

instance Show a => Show (Z a) where
  show (Z ls x rs) = show (reverse (take 5 ls)) ++ " > " ++ show x ++ " < " ++ show (take 5 rs)

instance Functor Z where
  fmap f (Z ls x rs) = Z (map f ls) (f x) (map f rs)

left, right :: Z a -> Z a
left  (Z (l:ls) x rs) = Z ls l (x:rs)
left  z               = z
right (Z ls x (r:rs)) = Z (x:ls) r rs
right z               = z

instance Comonad Z where
  extract   (Z _ x _) = x
  duplicate z         = Z (iterate1 left z) z (iterate1 right z)
    where iterate1 f = drop 1 . iterate f   -- 不含自身

-- 生命游戏 1D：规则 110
rule110 :: Z Bool -> Bool
rule110 z =
  let l   = extract (left z)
      c   = extract z
      r   = extract (right z)
  in case (l, c, r) of
       (True,  True,  True ) -> False
       (True,  True,  False) -> True
       (True,  False, True ) -> True
       (True,  False, False) -> False
       (False, True,  True ) -> True
       (False, True,  False) -> True
       (False, False, True ) -> True
       (False, False, False) -> False

-- 演示用：take n 个右侧 + focus + take n 个左侧，打印成一行
renderZ :: Int -> Z Bool -> String
renderZ n (Z ls x rs) =
    map cell (reverse (take n ls)) ++ [cell x] ++ map cell (take n rs)
  where cell b = if b then '#' else '.'

stepZ :: Z Bool -> Z Bool
stepZ = extend rule110

-- ============================================================
-- 3. Store Comonad —— focus + 观察函数
--    Store s a = s 类似 "指针", (s -> a) 是"看向什么位置得到什么值"
-- ============================================================

data Store s a = Store (s -> a) s

instance Functor (Store s) where
  fmap f (Store g s) = Store (f . g) s

instance Comonad (Store s) where
  extract   (Store g s)   = g s
  duplicate (Store g s)   = Store (Store g) s

-- 把 focus 移动到 s'
seek :: s -> Store s a -> Store s a
seek s' (Store g _) = Store g s'

-- 以当前 focus 为中心做"邻居平均"
neighborAvg :: Store Int Double -> Double
neighborAvg (Store g s) = (g (s - 1) + g s + g (s + 1)) / 3

-- ============================================================
-- main
-- ============================================================

main :: IO ()
main = do
  putStrLn "=========================================="
  putStrLn "Demo 23: Comonad —— Functor 的镜像"
  putStrLn "==========================================\n"

  putStrLn "=== 1. NonEmpty Comonad: extend = scan ==="
  let xs = 3 :| [1, 4, 1, 5, 9, 2, 6]
  putStrLn $ "  原始:       " ++ show xs
  putStrLn $ "  后缀 max:   " ++ show (runningMax xs)
  let ys = 1.0 :| [2, 3, 4, 5]
  putStrLn $ "  后缀 avg:   " ++ show (runningAvg ys)

  putStrLn "\n=== 2. Zipper Comonad: 一维元胞自动机 (rule 110) ==="
  let initial :: Z Bool
      initial = Z (repeat False) True (repeat False)
  mapM_ (putStrLn . ("  " ++) . renderZ 20) (take 10 (iterate stepZ initial))

  putStrLn "\n=== 3. Store Comonad: focus + 观察函数 ==="
  let st = Store (\i -> fromIntegral (i * i)) 5 :: Store Int Double
  putStrLn $ "  extract(focus=5) = " ++ show (extract st)            -- 25
  putStrLn $ "  neighborAvg(5)   = " ++ show (neighborAvg st)         -- (16+25+36)/3 = 25.666..
  let st7 = seek 7 st
  putStrLn $ "  extract(focus=7) = " ++ show (extract st7)            -- 49
  putStrLn $ "  neighborAvg(7)   = " ++ show (neighborAvg st7)         -- (36+49+64)/3

  putStrLn "\n=== 4. 直觉小结 ==="
  putStrLn "  Monad   put-into-context:   (a -> m b) bind"
  putStrLn "  Comonad read-from-context:  (w a -> b) extend"
  putStrLn ""
  putStrLn "  NonEmpty / Zipper / Store 三种常见 Comonad 的典型用途:"
  putStrLn "    * NonEmpty : scanr / runningAvg / 时序聚合"
  putStrLn "    * Zipper   : 元胞自动机 / 电子表格 / 光标编辑"
  putStrLn "    * Store    : 图形 builder / 界面 state + 观察函数 (Halogen / Elm-like)"
  putStrLn "=========================================="
