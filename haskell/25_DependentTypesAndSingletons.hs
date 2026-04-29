{-# LANGUAGE DataKinds      #-}
{-# LANGUAGE GADTs          #-}
{-# LANGUAGE KindSignatures #-}
{-# LANGUAGE TypeFamilies   #-}
{-# LANGUAGE TypeOperators  #-}
{-# LANGUAGE StandaloneDeriving #-}

-- ============================================================
-- Demo 25: 依值类型直觉 —— 把"长度"搬进类型
--
-- 真正的 Dependent Types 需要 Idris / Agda / Coq；
-- 但 Haskell 通过 DataKinds + GADTs + TypeFamilies + singletons
-- 已经可以模拟大部分"编译期长度校验"的效果。
--
-- 本 Demo 不依赖 singletons / vec 库，手写最小版本：
--   a) Peano 自然数提升到 Kind 级别 (类型层的数)
--   b) 长度索引向量  Vec (n :: Nat) a
--   c) 类型级加法     n + m  (TypeFamily)
--   d) 编译期保证   : head、append、zipWith 的长度正确
--
-- 阅读建议: 把"类型签名"当主角, 运行时的值只是"见证者"。
-- 运行:    runhaskell 25_DependentTypesAndSingletons.hs
-- ============================================================

module Main where

-- ============================================================
-- 1. 类型级自然数 + 单例 (singleton)
-- ============================================================

-- 普通值级 Nat
data Nat = Z | S Nat

-- 启用 DataKinds 后, `'Z / 'S n` 就是 *类型* 层的 Nat
-- 下面的 Vec 里 n :: Nat (其实是被提升的 kind)

-- Singleton：把"类型级 Nat"拉回"值级"，这样能在运行时对类型做 case 分析
data SNat (n :: Nat) where
  SZ :: SNat 'Z
  SS :: SNat n -> SNat ('S n)

-- 把 SNat 转成 Int，方便打印
toInt :: SNat n -> Int
toInt SZ      = 0
toInt (SS n)  = 1 + toInt n

-- ============================================================
-- 2. 类型级加法
-- ============================================================

type family (n :: Nat) + (m :: Nat) :: Nat where
  'Z    + m = m
  ('S n) + m = 'S (n + m)

-- ============================================================
-- 3. 长度索引向量 Vec (n :: Nat) a
-- ============================================================

data Vec (n :: Nat) a where
  VNil  :: Vec 'Z a
  VCons :: a -> Vec n a -> Vec ('S n) a

-- 这里 `(n :: Nat)` 出现在 VCons 的结果类型里，
-- 所以编译器知道"VCons 出来的 Vec 比原 Vec 多 1"。

-- ------- 安全的 head：空向量在编译期就不给调用 -------
vhead :: Vec ('S n) a -> a
vhead (VCons x _) = x

vtail :: Vec ('S n) a -> Vec n a
vtail (VCons _ xs) = xs

-- ------- append: 长度在类型里直接加起来 -------
vappend :: Vec n a -> Vec m a -> Vec (n + m) a
vappend VNil         ys = ys
vappend (VCons x xs) ys = VCons x (vappend xs ys)

-- ------- zipWith: 长度必须相等才允许 -------
vzipWith :: (a -> b -> c) -> Vec n a -> Vec n b -> Vec n c
vzipWith _ VNil         VNil         = VNil
vzipWith f (VCons x xs) (VCons y ys) = VCons (f x y) (vzipWith f xs ys)
-- 注意: 没有其它分支。编译器凭 GADT 知道两边要么都是 VNil、要么都是 VCons
-- 所以 "长度不匹配" 在编译期就不存在

-- ------- replicate: 根据 singleton 决定长度 -------
vreplicate :: SNat n -> a -> Vec n a
vreplicate SZ     _ = VNil
vreplicate (SS n) x = VCons x (vreplicate n x)

-- ------- toList: 方便打印 -------
vtoList :: Vec n a -> [a]
vtoList VNil          = []
vtoList (VCons x xs)  = x : vtoList xs

deriving instance Show a => Show (Vec n a)

-- ============================================================
-- 4. 让长度在编译期对齐
-- ============================================================

-- 一个类型层的 3: `'S ('S ('S 'Z))`
type N0 = 'Z
type N1 = 'S N0
type N2 = 'S N1
type N3 = 'S N2
type N4 = 'S N3

-- 运行期等价的单例
sn3 :: SNat N3
sn3 = SS (SS (SS SZ))

-- ============================================================
-- main
-- ============================================================

main :: IO ()
main = do
  putStrLn "=========================================="
  putStrLn "Demo 25: 依值类型直觉 —— 把长度搬进类型"
  putStrLn "==========================================\n"

  putStrLn "=== 1. SNat: 把类型层 Nat 拉回运行期 ==="
  putStrLn $ "  toInt sn3 = " ++ show (toInt sn3)  -- 3

  putStrLn "\n=== 2. Vec: 长度在类型里 ==="
  let v3 :: Vec N3 Int
      v3 = VCons 10 (VCons 20 (VCons 30 VNil))
  let v2 :: Vec N2 Int
      v2 = VCons 100 (VCons 200 VNil)
  putStrLn $ "  v3 = " ++ show (vtoList v3)
  putStrLn $ "  v2 = " ++ show (vtoList v2)

  putStrLn "\n=== 3. 安全 head ==="
  putStrLn $ "  vhead v3 = " ++ show (vhead v3)
  -- vhead VNil         -- 编译失败: 无法构造 Vec ('S n) a
  putStrLn "  vhead VNil 是编译错误 —— 空向量根本传不进去"

  putStrLn "\n=== 4. append: 类型加法直接算 ==="
  let v5 = vappend v3 v2
  putStrLn $ "  vappend v3 v2 :: Vec (N3+N2) = Vec N5"
  putStrLn $ "  => " ++ show (vtoList v5)

  putStrLn "\n=== 5. zipWith: 长度不等 = 编译错 ==="
  let z = vzipWith (+) v3 (vreplicate sn3 1 :: Vec N3 Int)
  putStrLn $ "  vzipWith (+) v3 (vreplicate sn3 1) = " ++ show (vtoList z)
  putStrLn "  vzipWith (+) v3 v2  -- 编译失败: N3 /= N2"

  putStrLn "\n=== 6. 直觉小结 ==="
  putStrLn "  * DataKinds    把普通 ADT 提升到类型层"
  putStrLn "  * GADTs        允许返回类型依赖构造子 (向量长度可推导)"
  putStrLn "  * TypeFamilies 在类型层做函数 (类型级 +)"
  putStrLn "  * Singletons   在类型层与值层之间搭桥"
  putStrLn ""
  putStrLn "  生产项目推荐直接用 singletons / vec / finite-typelits 库；"
  putStrLn "  想体验真正的 dependent types ⇒ Idris 2 / Agda / Lean"
  putStrLn "=========================================="
