{-# LANGUAGE GADTs                #-}
{-# LANGUAGE DataKinds            #-}
{-# LANGUAGE KindSignatures       #-}
{-# LANGUAGE TypeFamilies         #-}
{-# LANGUAGE ScopedTypeVariables  #-}
{-# LANGUAGE StandaloneDeriving   #-}

-- ============================================================
-- Demo 15: GADTs / Phantom Type / Type Families
--          —— 用类型系统把错误直接消灭在"编译阶段"
--
-- 这是 Haskell 最有代表性的一面：
--   * 普通 ADT 只能描述"值有哪些形状"
--   * GADT 还能描述"这些形状各自带着什么类型"
--   * Phantom Type 让类型参数成为"标签"，零运行时开销
--   * Type Family 则让类型本身变成可计算的函数
--
-- 本 Demo 目标：一个"类型安全的小小计算器 + 状态机"。
-- 运行：ghc 15_TypesAdvanced.hs && ./15_TypesAdvanced
--   或：runhaskell 15_TypesAdvanced.hs
-- ============================================================

module Main where

-- ============================================================
-- Part 1: 为什么需要 GADT？—— 先看普通 ADT 的局限
-- ============================================================
--
-- 用普通 ADT 写一个表达式树：
--
--   data Expr = IntE Int | BoolE Bool | Add Expr Expr | If Expr Expr Expr
--
--   eval :: Expr -> ???
--
-- 问题在于：Add 的两个子节点可能是 IntE，也可能是 BoolE，
--           If 的分支两边可能类型不一致。
-- 结果：eval 必须返回一个"可能是 Int 也可能是 Bool"的和类型，
--      运行时还要到处做类型检查。
--
-- 而这种错误本质上是"在编译时就能发现的"。

-- ============================================================
-- Part 2: GADT 版的表达式 —— 类型安全表达式树
-- ============================================================
--
-- 用 GADT 可以给每个构造器"单独指定"它产生的类型。

data Expr a where
  IntE  :: Int  -> Expr Int
  BoolE :: Bool -> Expr Bool
  Add   :: Expr Int  -> Expr Int  -> Expr Int
  Eq    :: Eq a => Expr a -> Expr a -> Expr Bool   -- 需要 a 可比较
  If    :: Expr Bool -> Expr a -> Expr a -> Expr a

-- 现在 eval 的类型本身就非常干净：
--   输入 Expr a，输出 a。不需要 Either、不需要运行时 tag。
eval :: Expr a -> a
eval (IntE n)     = n
eval (BoolE b)    = b
eval (Add x y)    = eval x + eval y
eval (Eq  x y)    = eval x == eval y
eval (If c t e)   = if eval c then eval t else eval e

-- 下面这段如果取消注释，编译器会拒绝：
-- badExpr :: Expr Int
-- badExpr = Add (IntE 1) (BoolE True)  -- 类型错，编译期拒绝

-- ============================================================
-- Part 3: Phantom Type —— 用类型"打标签"，零运行时开销
-- ============================================================
--
-- 常见场景：把"还没校验的输入"和"已经校验过的输入"区分开，
--          禁止把未校验的字符串传给要求已校验输入的函数。

-- 定义两个"空类型"作为标签。它们只在类型层面出现，运行时根本不存在。
data Raw       -- 未校验
data Validated -- 已校验

-- 这是关键：Email 带有一个类型参数 tag，但构造器里完全不用它。
newtype Email tag = Email { getEmail :: String } deriving Show

-- 直接拿字符串包一层 = 还没有校验过，标签必须是 Raw。
mkRaw :: String -> Email Raw
mkRaw = Email

-- 真正做校验的函数：只有走完这里才能得到 Email Validated。
validateEmail :: Email Raw -> Maybe (Email Validated)
validateEmail (Email s)
  | '@' `elem` s && length s >= 5 = Just (Email s)
  | otherwise                     = Nothing

-- 只允许对"已校验"的 Email 发送邮件。
-- 签名里写死了 Email Validated，没校验过的东西根本传不进来。
sendEmail :: Email Validated -> IO ()
sendEmail (Email s) = putStrLn $ "[send] -> " ++ s

-- 如果下一行取消注释，编译器会拒绝：
-- leak :: IO ()
-- leak = sendEmail (mkRaw "not-valid")   -- 类型错

-- ============================================================
-- Part 4: DataKinds + GADT —— 编译期状态机
-- ============================================================
--
-- 让"订单状态"从运行时值上升到类型层面。
-- 然后用 GADT 约束：只有 Paid 的订单才能发货，只有 Shipped 的才能完成。

-- 用 DataKinds，把普通值提升成类型。
data OrderStatus = Created | Paid | Shipped | Completed | Cancelled

-- Order 的类型参数是一个 OrderStatus 类型，不是值。
data Order (s :: OrderStatus) where
  MkOrder :: { orderId :: Int, amount :: Double } -> Order 'Created

-- 只有 Created 的订单能 pay；pay 之后类型变成 Paid。
pay :: Order 'Created -> Order 'Paid
pay (MkOrder i a) = MkOrder i a

ship :: Order 'Paid -> Order 'Shipped
ship (MkOrder i a) = MkOrder i a

complete :: Order 'Shipped -> Order 'Completed
complete (MkOrder i a) = MkOrder i a

-- 只有还在 Created 或 Paid 的订单能被取消。
-- 这里用类型类约束实现简单的"多输入单输出"关系。
class Cancellable (s :: OrderStatus)
instance Cancellable 'Created
instance Cancellable 'Paid

cancel :: Cancellable s => Order s -> Order 'Cancelled
cancel (MkOrder i a) = MkOrder i a

-- 如果下面这行取消注释，编译器会拒绝（Shipped 不在 Cancellable 实例里）：
-- oops :: Order 'Cancelled
-- oops = cancel (ship (pay (MkOrder 1 99.0)))

-- 要让这类对象能 show，需要 StandaloneDeriving，因为 GADT 的 deriving 规则更严格。
deriving instance Show (Order s)

-- ============================================================
-- Part 5: Type Family —— "类型层面的函数"
-- ============================================================
--
-- 类型族让我们在类型层面写"函数"：根据输入类型，映射到输出类型。

-- 开放类型族：在不同实例里添加新的映射。
type family Result a
type instance Result Int    = Double    -- 整数运算结果是浮点
type instance Result String = [String]  -- 字符串运算结果是字符串列表
type instance Result Bool   = Bool

class Process a where
  process :: a -> Result a

instance Process Int where
  process n = fromIntegral n / 2

instance Process String where
  process s = words s

instance Process Bool where
  process = not

-- 闭合类型族：所有分支一次写完，类型检查更可预期。
type family Elem c where
  Elem String = Char
  Elem [a]    = a
  Elem (Maybe a) = a

-- ============================================================
-- Part 6: Proxy + Type-Level Naturals —— 把数字放到类型里
-- ============================================================
--
-- 用 Nat 让 "长度" 出现在类型里。
-- 这是 vector-sized / safe-tensor / HList 这些库的起点。

data Nat = Z | S Nat   -- 皮亚诺数（类型层面也能用）

-- 长度带类型参数的列表
data Vec (n :: Nat) a where
  VNil  :: Vec 'Z a
  VCons :: a -> Vec n a -> Vec ('S n) a

-- head 只接受长度 >= 1 的 Vec，空向量调用会直接被编译器拒绝
vhead :: Vec ('S n) a -> a
vhead (VCons x _) = x

-- 两个等长向量的逐元素相加，长度信息由类型保证不会出错
vzipAdd :: Num a => Vec n a -> Vec n a -> Vec n a
vzipAdd VNil         VNil         = VNil
vzipAdd (VCons x xs) (VCons y ys) = VCons (x + y) (vzipAdd xs ys)

-- 把 Vec 转成普通列表方便打印
vecToList :: Vec n a -> [a]
vecToList VNil         = []
vecToList (VCons x xs) = x : vecToList xs

-- ============================================================
-- Main
-- ============================================================

main :: IO ()
main = do
  putStrLn "=========================================="
  putStrLn "Demo 15: GADTs / Phantom / Type Families"
  putStrLn "==========================================\n"

  -- ---- GADT 表达式 ----
  putStrLn "=== 1. GADT 表达式树：eval 不需要返回 Either ==="
  let e1 = Add (IntE 2) (IntE 3)                           :: Expr Int
      e2 = If (Eq (IntE 5) (Add (IntE 2) (IntE 3)))
              (IntE 100) (IntE 0)                          :: Expr Int
  putStrLn $ "  eval (2 + 3)                       = " ++ show (eval e1)
  putStrLn $ "  eval (if 5 == 2+3 then 100 else 0) = " ++ show (eval e2)
  putStrLn ""

  -- ---- Phantom Type ----
  putStrLn "=== 2. Phantom Type：未校验的邮箱根本传不进 sendEmail ==="
  let ok   = mkRaw "alice@example.com"
      fail_ = mkRaw "not-an-email"
  case validateEmail ok of
    Just v  -> sendEmail v
    Nothing -> putStrLn "  校验失败"
  case validateEmail fail_ of
    Just v  -> sendEmail v
    Nothing -> putStrLn "  [reject] not-an-email"
  putStrLn ""

  -- ---- DataKinds 状态机 ----
  putStrLn "=== 3. DataKinds + GADT：类型级订单状态机 ==="
  let o0 = MkOrder 1 99.0       :: Order 'Created
      o1 = pay o0               :: Order 'Paid
      o2 = ship o1              :: Order 'Shipped
      o3 = complete o2          :: Order 'Completed
      oc = cancel (pay (MkOrder 2 50.0)) :: Order 'Cancelled
  putStrLn $ "  new order      : " ++ show o0
  putStrLn $ "  after pay      : " ++ show o1
  putStrLn $ "  after ship     : " ++ show o2
  putStrLn $ "  after complete : " ++ show o3
  putStrLn $ "  cancel paid    : " ++ show oc
  putStrLn "  (尝试 cancel 一个 shipped 的订单 -> 编译期拒绝)"
  putStrLn ""

  -- ---- Type Family ----
  putStrLn "=== 4. Type Family：'类型层面的函数' ==="
  putStrLn $ "  process (10 :: Int)               = " ++ show (process (10 :: Int))
  putStrLn $ "  process \"hello world from fp\"     = " ++ show (process "hello world from fp")
  putStrLn $ "  process (True :: Bool)            = " ++ show (process True)
  putStrLn ""

  -- ---- 长度带到类型里 ----
  putStrLn "=== 5. 类型级自然数：长度安全的 Vec ==="
  let v1 = VCons (1 :: Int) (VCons 2 (VCons 3 VNil))   -- 长度 = 3
      v2 = VCons (10 :: Int) (VCons 20 (VCons 30 VNil))-- 长度 = 3
      vs = vzipAdd v1 v2
  putStrLn $ "  v1            = " ++ show (vecToList v1)
  putStrLn $ "  v2            = " ++ show (vecToList v2)
  putStrLn $ "  v1 + v2       = " ++ show (vecToList vs)
  putStrLn $ "  vhead v1      = " ++ show (vhead v1)
  putStrLn "  (vhead VNil 或者加一个长度不同的 Vec -> 编译期拒绝)"
  putStrLn ""

  -- ---- 小结 ----
  putStrLn "=== 知识点小结 ==="
  putStrLn "  * GADT         : 让构造器各自带上更精细的返回类型"
  putStrLn "  * Phantom Type : 类型参数只做标签，零运行时开销"
  putStrLn "  * DataKinds    : 把 'Created/'Paid 这类值提升到类型层"
  putStrLn "  * TypeFamilies : 允许在类型层面定义函数"
  putStrLn "  核心哲学：能在类型层面表达的约束，就不要放到运行时"
  putStrLn "=========================================="
