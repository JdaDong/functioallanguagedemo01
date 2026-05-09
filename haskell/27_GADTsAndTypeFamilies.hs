{-# LANGUAGE GADTs                #-}
{-# LANGUAGE DataKinds            #-}
{-# LANGUAGE KindSignatures       #-}
{-# LANGUAGE TypeFamilies         #-}
{-# LANGUAGE TypeFamilyDependencies #-}
{-# LANGUAGE TypeOperators        #-}
{-# LANGUAGE FlexibleInstances    #-}
{-# LANGUAGE ScopedTypeVariables  #-}
{-# LANGUAGE StandaloneDeriving   #-}

-- ============================================================
-- Demo 27: GADT / Type Family —— 进阶篇
--
-- 15_TypesAdvanced.hs 已经讲过：
--   * GADT 表达式树、Phantom Type
--   * DataKinds 状态机（用类型类约束合法转换）
--   * 独立 type family 和一个简单 closed type family
--
-- 本 Demo 补它没讲的那一半：
--   Part 1: data family —— "数据层面的类型族"，每个索引类型
--           可以有完全不同的构造子和存储方式
--   Part 2: associated type family —— 绑在类型类上的 type family，
--           让 class 的不同 instance 带各自的"伙伴类型"
--   Part 3: injective type family —— 声明单射，让类型推导更强
--   Part 4: 递归归纳型族 —— 类型安全的定长向量 Vec 及其 append，
--           在类型层面做自然数加法
--   Part 5: data family 驱动的协议状态机 ——
--           不同状态携带不同 payload 类型（15 号用 phantom 做不到）
--
-- 运行：runghc 27_GADTsAndTypeFamilies.hs
-- ============================================================

module Main where

import Data.Kind (Type)

-- ============================================================
-- Part 1: data family
-- ============================================================
--
-- type family 只能计算出"类型"，但每个索引对应的构造子必须在别处定义。
-- data family 则允许"同一个名字、按索引给出完全不同的数据构造"。
--
-- 典型场景：为不同元素类型选择最紧凑的存储。

data family Array a
data instance Array Bool   = ABool   { runBool   :: [Bool] }
data instance Array Int    = AInt    { runInt    :: [Int] }
data instance Array (a, b) = APair   (Array a) (Array b)  -- 双数组，不打包

-- 每个 instance 都可以有自己完全不同的实现
arrLen :: Array Bool -> Int
arrLen (ABool xs) = length xs

arrSum :: Array Int -> Int
arrSum (AInt xs) = sum xs

-- Pair 版的"长度"是两个子数组长度相同才有意义
arrPairLen :: Array (Bool, Int) -> (Int, Int)
arrPairLen (APair a b) = (arrLen a, arrSum b)  -- 第二个其实是 sum，演示"不同语义"

-- ============================================================
-- Part 2: associated type family
-- ============================================================
--
-- 独立的 type family：
--     type family Result a
--     type instance Result Int = Double
-- associated 版本：type family 写进 class 里，每个 instance 顺带给出
--
-- 好处：强制"实现 class 就必须给出伙伴类型"，耦合更紧。

class Container c where
  type Elem c :: Type              -- 关联型族：c 对应的元素类型
  empty  :: c
  insert :: Elem c -> c -> c
  toList :: c -> [Elem c]

instance Container [a] where
  type Elem [a] = a
  empty        = []
  insert x xs  = x : xs
  toList       = id

-- 对 String 特化：虽然 String = [Char]，但我们可以给 String 一个
-- 不同的 insert 语义（前置加感叹号的花哨版），来演示 associated 的灵活
newtype Shouted = Shouted { unShouted :: String } deriving Show

instance Container Shouted where
  type Elem Shouted = Char
  empty             = Shouted ""
  insert c (Shouted s) = Shouted (c : '!' : s)
  toList (Shouted s)   = s

-- ============================================================
-- Part 3: injective type family
-- ============================================================
--
-- 默认的 type family 不是单射的：编译器不会从 F a ~ F b 推出 a ~ b。
-- 加 | r -> a 声明"由结果 r 可以唯一确定参数 a"，推导就能反向走。
--
-- 经典例子：容器与它的元素。

type family UnElem c = r | r -> c where
  UnElem [a]      = a
  UnElem (Maybe a) = a

-- 因为单射，编译器可以从 "UnElem c ~ Int" 反推 c 的可能形状
-- （此处只展示声明，实际使用常见于 effect/free 库的 dispatch）

demoUnElem :: UnElem [Int]
demoUnElem = 42  -- 类型等价于 Int

-- ============================================================
-- Part 4: 递归归纳型族 —— 定长向量
-- ============================================================
--
-- 在类型层面写"自然数加法"，并用它保证 append 的长度正确。

data Nat = Z | S Nat

-- 类型层面的加法
type family (n :: Nat) :+ (m :: Nat) :: Nat where
  'Z     :+ m = m
  ('S n) :+ m = 'S (n :+ m)

-- 定长向量：长度写进类型
data Vec (n :: Nat) a where
  VNil  :: Vec 'Z a
  VCons :: a -> Vec n a -> Vec ('S n) a

-- 类型安全的 append：结果长度 = n + m，编译器帮你查
vappend :: Vec n a -> Vec m a -> Vec (n :+ m) a
vappend VNil        ys = ys
vappend (VCons x xs) ys = VCons x (vappend xs ys)

vtoList :: Vec n a -> [a]
vtoList VNil         = []
vtoList (VCons x xs) = x : vtoList xs

-- head 只接受非空向量 —— 编译期拒绝 vhead VNil
vhead :: Vec ('S n) a -> a
vhead (VCons x _) = x

-- ============================================================
-- Part 5: data family 驱动的协议状态机
-- ============================================================
--
-- 15 号用 DataKinds + phantom 做过状态机，但所有状态的 Order 长得一样
-- （只是类型标签不同）。现实里不同状态常带不同字段：
--   Draft   只有草稿文本
--   Sent    带收件人和时间戳
--   Acked   带回执 id
-- 这正是 data family 的用武之地：同一个 Message 类型族，每个状态
-- 独立定义构造子与字段。

data MsgState = SDraft | SSent | SAcked

data family Message (s :: MsgState)

data instance Message 'SDraft = Draft { draftText :: String }
data instance Message 'SSent  = Sent  { sentText :: String, sentTo :: String }
data instance Message 'SAcked = Acked { ackedTo :: String, ackId :: Int }

-- 合法转换：只能沿 Draft -> Sent -> Acked 走
send :: String -> Message 'SDraft -> Message 'SSent
send to (Draft t) = Sent { sentText = t, sentTo = to }

ack :: Int -> Message 'SSent -> Message 'SAcked
ack aid (Sent _ to) = Acked { ackedTo = to, ackId = aid }

-- 想写 ack _ (Draft _) —— 编译期直接拒绝：类型对不上

-- ============================================================
-- Main
-- ============================================================

main :: IO ()
main = do
  putStrLn "=========================================="
  putStrLn "Demo 27: GADT / Type Family —— 进阶篇"
  putStrLn "==========================================\n"

  putStrLn "=== Part 1: data family ==="
  let ab = ABool [True, False, True]
      ai = AInt [1, 2, 3, 4]
      ap = APair ab ai
  putStrLn $ "  Array Bool 长度: " ++ show (arrLen ab)
  putStrLn $ "  Array Int  求和: " ++ show (arrSum ai)
  putStrLn $ "  Array (Bool,Int) pair: " ++ show (arrPairLen ap)
  putStrLn ""

  putStrLn "=== Part 2: associated type family ==="
  let xs = insert 3 (insert 2 (insert 1 (empty :: [Int])))
  putStrLn $ "  [Int] insert 1,2,3: " ++ show (toList xs)
  let sh = insert 'c' (insert 'b' (insert 'a' (empty :: Shouted)))
  putStrLn $ "  Shouted insert a,b,c: " ++ show sh
  putStrLn ""

  putStrLn "=== Part 3: injective type family ==="
  putStrLn $ "  UnElem [Int] demo 值: " ++ show demoUnElem
  putStrLn "  （单射声明让编译器能从结果反推参数，见源码注释）"
  putStrLn ""

  putStrLn "=== Part 4: 类型安全定长向量 ==="
  let v1 = VCons 1 (VCons 2 VNil)            :: Vec ('S ('S 'Z)) Int
      v2 = VCons 3 (VCons 4 (VCons 5 VNil))  :: Vec ('S ('S ('S 'Z))) Int
      v3 = vappend v1 v2                      -- 类型自动推导为 Vec 5 Int
  putStrLn $ "  v1 (len=2): " ++ show (vtoList v1)
  putStrLn $ "  v2 (len=3): " ++ show (vtoList v2)
  putStrLn $ "  vappend v1 v2 (len=5): " ++ show (vtoList v3)
  putStrLn $ "  vhead v1 = " ++ show (vhead v1)
  putStrLn "  注：vhead VNil 在编译期就被拒绝"
  putStrLn ""

  putStrLn "=== Part 5: data family 状态机 ==="
  let d = Draft "hello world"
      s = send "alice@example.com" d
      a = ack 42 s
  putStrLn $ "  Draft:  text=" ++ show (draftText d)
  putStrLn $ "  Sent:   to=" ++ show (sentTo s) ++ ", text=" ++ show (sentText s)
  putStrLn $ "  Acked:  to=" ++ show (ackedTo a) ++ ", ackId=" ++ show (ackId a)
  putStrLn "  注：ack 只接 'SSent，编译期拒绝 ack _ (Draft _)"
  putStrLn ""

  putStrLn "=========================================="
  putStrLn "对比 15 号："
  putStrLn "  15: GADT 基础 / Phantom / DataKinds / 简单 type family"
  putStrLn "  27: data family / associated / injectivity / 归纳型族 / 异构 payload 状态机"
  putStrLn "=========================================="
