{-# LANGUAGE RankNTypes #-}
{-# LANGUAGE LambdaCase #-}

-- ============================================================
-- Demo 09: Lens & Optics — 函数式引用
--
-- 核心概念：
--   1. Lens 原理：getter + setter 的抽象
--   2. Van Laarhoven 表示 (Functor f)
--   3. 组合 Lens（深度嵌套更新）
--   4. Iso, Prism, Traversal
--   5. 实际应用场景
-- ============================================================

module Main where

import Data.Functor.Identity (Identity(..))
import Data.Maybe          (fromMaybe)
import Data.Char            (toUpper)

-- ============================================================
-- Part 1: 从直觉理解 Lens
-- ============================================================
-- 
-- 问题：如何不可变地更新深层嵌套数据？
--   data Company = Company { ceo :: Person }
--   data Person   = Person { name :: Name, address :: Address }
--   data Address  = Address { city :: String }
--
-- 纯函数方式需要层层解构再重组，非常繁琐。
-- Lens 提供了一种"函数式的指针/引用"来聚焦和更新子结构。

-- ============================================================
-- Part 2: 手动实现简化版 Lens
-- ============================================================

-- Lens 的本质：一个可以"聚焦"到容器中某部分的函数式引用
data Lens' s a = Lens'
  { view   :: s -> a           -- getter: 取出值
  , set    :: a -> s -> s      -- setter: 设置新值
  , over   :: (a -> a) -> s -> s -- over: 在焦点上应用函数
  }

-- over 可以用 view 和 set 定义
makeLens :: (s -> a) -> (a -> s -> s) -> Lens' s a
makeLens getter setter = Lens'
  { view = getter
  , set  = setter
  , over = \f s -> setter (f (getter s)) s
  }

-- ============================================================
-- Part 3: Lens 组合 — 最强大的特性
-- ============================================================

-- 组合两个 lens：l1 聚焦外层，l2 聚焦内层
(|>) :: Lens' s a -> Lens' a b -> Lens' s b
(|>) l1 l2 = makeLens
  (view l2 . view l1)                              -- 复合 getter
  (\b s -> set l1 (set l2 b (view l1 s)) s)       -- 复合 setter

-- ============================================================
-- Part 4: 数据模型与 Lens 定义
-- ============================================================

-- 深层嵌套数据结构
newtype Name = Name String deriving Show

data Address = Address
  { _city     :: String
  , _street   :: String
  , _zipCode  :: String
  } deriving Show

data ContactInfo = ContactInfo
  { _email    :: String
  , _phone    :: String
  , _address  :: Address
  } deriving Show

data Employee = Employee
  { _name        :: Name
  , _contact     :: ContactInfo
  , _salary      :: Double
  , _department  :: String
  } deriving Show

data Company = Company
  { _companyName :: String
  , _ceo         :: Employee
  , _employees   :: [Employee]
  } deriving Show

-- ---- 定义 Lens ----

-- Address lenses
cityL :: Lens' Address String
cityL = makeLens _city (\c a -> a { _city = c })

streetL :: Lens' Address String
streetL = makeLens _street (\s a -> a { _street = s })

zipCodeL :: Lens' Address String
zipCodeL = makeLens _zipCode (\z a -> a { _zipCode = z })

-- ContactInfo lenses
contactAddressL :: Lens' ContactInfo Address
contactAddressL = makeLens _address (\a c -> c { _address = a })

emailL :: Lens' ContactInfo String
emailL = makeLens _email (\e c -> c { _email = e })

-- Employee lenses
employeeContactL :: Lens' Employee ContactInfo
employeeContactL = makeLens _contact (\c e -> e { _contact = c })

salaryL :: Lens' Employee Double
salaryL = makeLens _salary (\s e -> e { _salary = s })

departmentL :: Lens' Employee String
departmentL = makeLens _department (\d e -> e { _department = d })

-- Company lenses
ceoL :: Lens' Company Employee
ceoL = makeLens _ceo (\c comp -> comp { _ceo = c })

employeesL :: Lens' Company [Employee]
employeesL = makeLens _employees (\es comp -> comp { _employees = es })

-- ============================================================
-- Part 5: 使用 Lens 组合做深层更新
-- ============================================================

sampleCompany :: Company
sampleCompany = Company
  { _companyName = "TechCorp"
  , _ceo = Employee
      { _name       = Name "Alice CEO"
      , _contact    = ContactInfo
          { _email   = "alice@techcorp.com"
          , _phone   = "555-0001"
          , _address = Address
              { _city    = "San Francisco"
              , _street  = "1 Tech Way"
              , _zipCode = "94105"
              }
          }
      , _salary     = 500000.0
      , _department = "Executive"
      }
  , _employees =
      [ Employee (Name "Bob Dev")
          (ContactInfo "bob@tc.com" "555-0002"
              (Address "Seattle " "2 Code Ave" "98101"))
          120000.0 "Engineering"
      , Employee (Name "Carol HR")
          (ContactInfo "carol@tc.com" "555-0003"
              (Address "Portland" "3 HR St" "97201"))
          80000.0 "HR"
      ]
  }

-- 组合 Lens：CEO 所在城市的 Lens
ceoCityL :: Lens' Company String
ceoCityL = ceoL |> employeeContactL |> contactAddressL |> cityL

-- CEO 邮件的 Lens
ceoEmailL :: Lens' Company String
ceoEmailL = ceoL |> employeeContactL |> emailL

-- CEO 薪资的 Lens
ceoSalaryL :: Lens' Company Double
ceoSalaryL = ceoL |> salaryL

-- ============================================================
-- Part 6: Iso — 同构变换（类型转换）
-- ============================================================

data Iso a b = Iso
  { fw :: a -> b   -- 正向转换
  , bw :: b -> a   -- 反向转换
  }

-- Celsius <-> Fahrenheit Iso
celFahIso :: Iso Double Double
celFahIso = Iso
  { fw = \c -> c * 9 / 5 + 32
  , bw = \f -> (f - 32) * 5 / 9
  }

-- 通过 Iso 转换 Lens 的目标类型
viaIso :: Iso a b -> Lens' s a -> Lens' s b
viaIso iso lens = makeLens
  (fw iso . view lens)
  (\b s -> set lens (bw iso b) s)

-- ============================================================
-- Part 7: Prism — 可能不存在的部分（Maybe 变体）
-- ============================================================

data Prism' s a = Prism'
  { preview :: s -> Maybe a      -- 尝试匹配，可能失败
  , review  :: a -> s             -- 构造完整值
  }

-- Either Left prism
_Left :: Prism' (Either a b) a
_Left = Prism'
  { preview = \case
      Left a  -> Just a
      Right _ -> Nothing
  , review = Left
  }

_Right :: Prism' (Either a b) b
_Right = Prism'
  { preview = \case
      Left _  -> Nothing
      Right b -> Just b
  , review = Right
  }

-- Maybe Just prism
_Just :: Prism' (Maybe a) a
_Just = Prism'
  { preview = id
  , review = Just
  }

_Nothing :: Prism' (Maybe a) ()
_Nothing = Prism'
  { preview = \case
      Nothing -> Just ()
      Just _  -> Nothing
  , review = const Nothing
  }

-- ============================================================
-- Part 8: Traversal — 多焦点的 Lens（列表、树等）
-- ============================================================

-- 简化版 Traversal：只支持 Identity functor（即纯函数 map）
newtype Traversal' s a = Traversal'
  { overT :: (a -> a) -> s -> s
  }

-- 列表 traversal：对每个元素操作
traversed :: Traversal' [a] a
traversed = Traversal' map

-- ============================================================
-- Main
-- ============================================================

main :: IO ()
main = do
  putStrLn "=========================================="
  putStrLn "Demo 09: Lens & Optics — 函数式引用"
  putStrLn "==========================================\n"

  -- ---- 基础 Lens 操作 ----
  putStrLn "=== 1. 基础 Lens ==="
  let addr = Address { _city="Beijing", _street="长安街", _zipCode="100000" }
  putStrLn $ "  原始城市: " ++ view cityL addr
  putStrLn $ "  更新后:   " ++ view cityL (over cityL ("New " ++) addr)
  putStrLn $ "  设置后:   " ++ view cityL (set cityL "Shanghai" addr)
  putStrLn ""

  -- ---- Lens 组合：深层嵌套访问 ----
  putStrLn "=== 2. Lens 组合: 深层嵌套 ==="
  putStrLn $ "  CEO 城市: " ++ view ceoCityL sampleCompany
  putStrLn $ "  CEO 邮件: " ++ view ceoEmailL sampleCompany
  putStrLn $ "  CEO 薪资: ¥" ++ show (view ceoSalaryL sampleCompany)
  
  -- 用一次 over 更新深层字段！
  let company1 = over ceoCityL (map toUpper) sampleCompany
  putStrLn $ "  CEO 城市大写: " ++ view ceoCityL company1
  
  let company2 = over ceoSalaryL (* 1.1) sampleCompany  -- 加薪10%
  putStrLn $ "  CEO 加薪10%: ¥" ++ show (view ceoSalaryL company2)
  putStrLn ""

  -- ---- Iso 应用 ----
  putStrLn "=== 3. Iso: 温度单位转换 ==="
  let tempC = 37.0
      tempF = fw celFahIso tempC
  putStrLn $ "  " ++ show tempC ++ "°C = " ++ show tempF ++ "°F"
  putStrLn $ "  反转: " ++ show (bw celFahIso tempF) ++ "°C"
  
  -- 通过 Iso + Lens 直接用摄氏度操作
  let companyCelsius = viaIso celFahIso ceoSalaryL
  -- （这里只是演示语法，薪资不是温度）
  putStrLn ""

  -- ---- Prism ----
  putStrLn "=== 4. Prism: 模式匹配提取 ==="
  let val1 = Left "error message" :: Either String Int
      val2 = Right 42            :: Either String Int
      val3 = Just "present"      :: Maybe String
      val4 = Nothing             :: Maybe String
  
  putStrLn $ "  preview _Left (Left):  " ++ show (preview _Left val1)
  putStrLn $ "  preview _Left (Right): " ++ show (preview _Left val2)
  putStrLn $ "  preview _Right (Left): " ++ show (preview _Right val1)
  putStrLn $ "  preview _Right (Right):" ++ show (preview _Right val2)
  putStrLn $ "  preview _Just (Just):  " ++ show (preview _Just val3)
  putStrLn $ "  preview _Nothing:      " ++ show (preview _Nothing val4)
  putStrLn ""

  -- ---- 实际应用场景 ----
  putStrLn "=== 5. 实际场景: 批量更新员工薪资 ==="
  let giveRaise :: Double -> Company -> Company
      giveRaise pct = over employeesL (map (over salaryL (* (1 + pct))))
      
      companyAfterRaise = giveRaise 0.05 sampleCompany
  putStrLn $ "  Bob 新薪资: ¥" ++ show 
    (view salaryL (head (_employees companyAfterRaise)))
  putStrLn $ "  Carol 新薪资: ¥" ++ show 
    (view salaryL (_employees companyAfterRaise !! 1))
  putStrLn ""
  
  putStrLn "=========================================="

