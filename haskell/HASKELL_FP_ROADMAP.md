# Haskell FP 学习路线图

## 当前进度

本仓库 `haskell/` 目录下已有以下 Demo，系统覆盖了 Haskell FP 的核心概念：

| 文件 | 主题 | 核心概念 |
|------|------|---------|
| `01_PureAndLazy.hs` | 纯函数与惰性求值 | 纯函数、无限列表、自定义 map/filter/foldr、列表推导 |
| `02_TypeClassAndMonad.hs` | 类型类与 Monad | Functor、Maybe Monad、Either、自定义类型类、二叉树 |
| `03_CurryAndCompose.hs` | 柯里化与函数组合 | 柯里化、部分应用、`(.)` 组合、Point-Free 风格 |
| `04_IOAndSideEffects.hs` | IO Monad 与副作用 | IO 类型、do 表示法、IORef、mapM/sequence、异常处理 |
| `05_StateAndReader.hs` | State 与 Reader | State Monad、Reader Monad、ReaderT+State 组合 |
| `06_ConcurrencySTM.hs` | 并发与 STM | forkIO、MVar、TVar、STM 事务、TQueue、Chan |
| `07_TypesAndADT.hs` | 高级类型系统 | Sum/Product Type、newtype、幻影类型、GADT、NonEmpty |
| `08_MonadTransformers.hs` | Monad Transformers | MaybeT/ExceptT、StateT+WriterT 堆叠、自定义 Transformer |
| `09_LensAndOptics.hs` | Lens 与 Optics | 手动实现 Lens、组合 (|>)、Iso、Prism、Traversal |
| `10_ParserCombinators.hs` | 解析器组合子 | Parser 类型、Applicative/Monad 解析器、算术/JSON/KV 解析 |
| `11_FreeMonadsAndDSL.hs` | Free Monad & DSL | 自由 Monad 实现、Console/FS/KVStore DSL、多解释器模式 |
| `12_QuickCheck.hs` | 属性测试 | Generator 组合子、属性定义、反例查找、测试运行器 |
| `13_ArrowAndProfunctor.hs` | Arrow & Profunctor | proc 语法、信号处理、Profunctor dimap、Optics 联系 |

---

## 总体学习路线

| 阶段 | 目标 | 关键词 |
|------|------|--------|
| **入门** | 理解 Haskell 函数式风格 | 纯函数、惰性求值、列表操作 |
| **进阶** | 掌握类型抽象 | Functor、Monad、类型类、函数组合 |
| **中级** | 副作用管理 | IO Monad、State、Reader、依赖注入 |
| **高级** | 并发与类型系统 | STM、GADT、幻影类型、类型安全 |
| **专家** | Transformer/Optics/DSL | Monad Transformer 堆叠、Lens、Free Monad、属性测试、Arrow |

---

## 第一阶段：入门 — 纯函数与惰性求值

### 目标
理解 Haskell 作为纯函数式语言的核心特性：**所有函数都是纯的，求值默认是惰性的**。

### Demo：`01_PureAndLazy.hs`

```haskell
-- 纯函数：相同输入 → 相同输出，永远无副作用
celsiusToFahrenheit :: Double -> Double
celsiusToFahrenheit c = c * 9 / 5 + 32

-- 惰性：无限列表，只在 take 时才计算
fibs :: [Integer]
fibs = 0 : 1 : zipWith (+) fibs (tail fibs)

-- 列表推导
pythagorean = [(a,b,c) | c <- [1..25], b <- [1..c], a <- [1..b], a*a + b*b == c*c]
```

### 关键理解
- `IO` 是 Haskell 把副作用"隔离"到类型系统的方法
- `[0..]` 是真正的无限列表，Haskell 不会卡死，因为默认惰性
- 模式匹配 `f (x:xs)` 是处理递归结构的惯用方式

---

## 第二阶段：进阶 — 类型类与 Monad

### 目标
掌握 Haskell 最重要的三个抽象：Functor、Applicative、Monad。

### Demo：`02_TypeClassAndMonad.hs`

```haskell
-- 类型类：定义接口
class Describable a where
  describe :: a -> String

-- Maybe Monad：do 表示法链式处理，遇到 Nothing 自动短路
complexCalc :: Double -> Maybe Double
complexCalc x = do
  divided <- safeDivide 100 x
  rooted  <- safeSqrt divided
  safeLog rooted

-- 自定义 Functor：二叉树
instance Functor Tree where
  fmap _ Leaf         = Leaf
  fmap f (Node x l r) = Node (f x) (fmap f l) (fmap f r)
```

### 关键理解

| 抽象 | 核心方法 | 直觉 |
|------|---------|------|
| `Functor` | `fmap :: (a->b) -> f a -> f b` | 在容器/上下文内映射 |
| `Applicative` | `<*> :: f (a->b) -> f a -> f b` | 容器内的函数应用 |
| `Monad` | `>>= :: m a -> (a -> m b) -> m b` | 依赖上一步结果的链式计算 |

---

## 第三阶段：中级前置 — 柯里化与函数组合

### 目标
掌握 Haskell 最优雅的编程风格：Point-Free、函数组合管道。

### Demo：`03_CurryAndCompose.hs`

```haskell
-- 所有函数天然柯里化
add :: Int -> Int -> Int   -- 实为 Int -> (Int -> Int)
add5 = add 5               -- 部分应用

-- 函数组合 (.) : 从右到左
letterFrequency = sortBy (comparing (Down . snd))
               . map (\g -> (head g, length g))
               . group . sort . map toLower . filter isAlpha

-- Point-Free 风格
sumOfSquares :: [Int] -> Int
sumOfSquares = sum . map (^2)
```

---

## 第四阶段：中级 — IO Monad 与副作用管理

### 目标
理解 Haskell 如何用类型系统把副作用和纯函数彻底分离。

### Demo：`04_IOAndSideEffects.hs`

```haskell
-- IO a 是"描述一个会产生副作用并返回 a 的动作"
-- 不是立即执行，而是一个"配方"
greet :: String -> IO ()
greet name = putStrLn ("你好, " ++ name)

-- IORef: 局部可变状态
counter <- newIORef (0 :: Int)
modifyIORef counter (+1)
val <- readIORef counter

-- mapM: 把 IO 动作列表变成单个 IO 动作
results <- mapM processOne [1..5]

-- 纯函数负责业务逻辑，IO 只负责展示
calculateTax :: Double -> Double -> Double  -- 纯函数
printReceipt :: [(String, Double)] -> IO () -- 副作用
```

### Scala 对比

| Haskell | Scala (cats-effect) |
|---------|-------------------|
| `IO a` | `IO[A]` |
| `do { x <- io1; io2 x }` | `for { x <- io1; _ <- io2(x) } yield ()` |
| `IORef` | `Ref[IO, A]` |
| `mapM` | `xs.traverse(f)` |

---

## 第五阶段：高级 — State 与 Reader Monad

### 目标
用 Monad 封装"有状态计算"和"依赖环境的计算"，保持函数纯性。

### Demo：`05_StateAndReader.hs`

```haskell
-- State s a ≈ s -> (a, s)
push :: a -> State (Stack a) ()
push x = modify (x:)

pop :: State (Stack a) (Maybe a)
pop = do
  s <- get
  case s of
    []     -> return Nothing
    (x:xs) -> put xs >> return (Just x)

-- Reader r a ≈ r -> a
connectionString :: App String
connectionString = do
  host <- asks dbHost
  port <- asks dbPort
  return $ host ++ ":" ++ show port

-- local: 局部覆盖环境
withDebug :: App a -> App a
withDebug = local (\cfg -> cfg { debugMode = True })

-- ReaderT + State 组合
type Session a = ReaderT AppConfig (State SessionState) a
```

### Scala 对比

| Haskell | Scala (cats) |
|---------|-------------|
| `State s a` | `StateT[IO, S, A]` |
| `Reader r a` | `ReaderT[IO, R, A]` |
| `get / put / modify` | `StateT.get / set / modify` |
| `ask / asks / local` | `Kleisli` / `ReaderT` |

---

## 第六阶段：高级 — 并发与 STM

### 目标
掌握 Haskell 独树一帜的 STM（软件事务内存）并发模型。

### Demo：`06_ConcurrencySTM.hs`

```haskell
-- forkIO: 轻量级绿色线程
producer <- forkIO $ forM_ [1..5] $ putMVar box

-- STM: 原子事务块，像数据库事务一样
transfer :: Account -> Account -> Int -> STM (Either String ())
transfer from to amount = do
  bal <- readTVar (accBalance from)
  if bal < amount
    then return (Left "余额不足")
    else do
      modifyTVar (accBalance from) (subtract amount)
      modifyTVar (accBalance to) (+amount)
      return (Right ())

-- atomically 执行 STM 块（原子、可重试、无死锁）
result <- atomically $ transfer alice bob 300

-- retry: 声明式等待（STM 最强特性）
transferWhenReady from to amount = do
  bal <- readTVar (accBalance from)
  when (bal < amount) retry  -- 自动等到余额足够
```

### STM vs 传统并发

| 特性 | 传统锁 | STM |
|------|--------|-----|
| 死锁 | 可能 | 不可能 |
| 组合性 | 难 | 天然支持 |
| 等待条件 | 条件变量 | `retry` |
| 错误恢复 | 手动 | 自动回滚 |

### Scala 对比

| Haskell | Scala (cats-effect) |
|---------|-------------------|
| `forkIO` | `IO.start` / `Fiber` |
| `MVar` | `MVar[IO, A]` |
| `TVar` | `Ref[IO, A]` (原子) |
| `STM` | 无直接对应（cats-stm 库） |
| `Chan` | `Queue[IO, A]` |

---

## 第七阶段：高级 — 类型系统与 ADT

### 目标
用 Haskell 强大的类型系统在编译期捕获业务规则错误。

### Demo：`07_TypesAndADT.hs`

```haskell
-- newtype: 零成本包装，防止混淆
newtype UserId    = UserId    Int
newtype ProductId = ProductId Int
-- lookupUser (ProductId 99)  -- 编译错误！

-- 幻影类型: 类型层面的状态机
data Unvalidated; data Validated
data Tagged t a = Tagged a
type RawEmail   = Tagged Unvalidated String
type ValidEmail = Tagged Validated   String

validateEmail :: RawEmail  -> Either String ValidEmail
sendEmail     :: ValidEmail -> String -> IO ()  -- 只接受已校验的

-- GADT: 类型安全的表达式，类型参数精确匹配
data TypedExpr :: * -> * where
  TLitInt :: Int  -> TypedExpr Int
  TLitBool :: Bool -> TypedExpr Bool
  TAdd     :: TypedExpr Int -> TypedExpr Int -> TypedExpr Int
  TIf      :: TypedExpr Bool -> TypedExpr a -> TypedExpr a -> TypedExpr a

evalTyped :: TypedExpr a -> a  -- 返回类型与表达式类型一致
```

---

## 第八阶段：专家 — Monad Transformers 深度实践

### 目标
理解如何将多个 Monad 效果组合在一起，解决现实世界的"效果叠加"问题。

### Demo：`08_MonadTransformers.hs`

```haskell
-- MaybeT: 最常用的错误处理 Transformer
findUserEmail :: Int -> MaybeT IO String
findUserEmail uid = do
  user <- MaybeT $ pure (lookupUser uid)
  liftIO $ putStrLn $ "找到用户: " ++ userName user
  pure (userEmail user)

-- ExceptT: 带错误类型的异常处理
transferMoney :: Int -> Int -> Double -> ExceptT AppError IO String
transferMoney fromId toId amount = do
  fromUser <- MaybeT (pure $ lookupUser fromId))
    `catchE` (\_ -> throwE (UserNotFound fromId))
  when (amount > userBalance fromUser) $
    throwE (InsufficientFunds (userBalance fromUser) amount)
  pure $ concat [userName fromUser, " 转给 ", userName toId]

-- StateT + WriterT 堆叠：带日志的状态计算
type EvalM a = StateT [(String, Int)] (WriterT [String] IO) a

-- ReaderT + StateT: 配置注入 + 可变状态（经典 Web 应用模式）
type AppM a = ReaderT AppConfig (StateT AppState IO) a
```

### 核心概念

| Transformer | 解决的问题 | 核心方法 |
|-------------|-----------|---------|
| `MaybeT m` | 在 `m` 中增加可能失败的效果 | MaybeT、runMaybeT |
| `ExceptT e m` | 带错误类型的可恢复异常 | throwE、catchE |
| `StateT s m` | 在 `m` 中增加可变状态 | get、put、modify |
| `ReaderT r m` | 在 `m` 中增加只读环境 | ask、asks、local |
| `WriterT w m` | 在 `m` 中追加式日志 | tell、listen |

### lift 层级规则

```
IO           → 直接用 liftIO
StateT IO    → liftIO 提升到 IO 层
ReaderT (StateT IO) → lift 提升 StateT 层，liftIO 提升 IO 层
```

---

## 第九阶段：专家 — Lens & Optics

### 目标
用函数式引用优雅地操作深层嵌套的不可变数据结构。

### Demo：`09_LensAndOptics.hs`

```haskell
-- 手动实现 Lens
data Lens' s a = Lens'
  { view :: s -> a, set :: a -> s -> s, over :: (a->a)->s->s }

-- 组合：CEO 的城市的 Lens = ceoL |> employeeContactL |> contactAddressL |> cityL
ceoCityL :: Lens' Company String
ceoCityL = ceoL |> employeeContactL |> contactAddressL |> cityL

-- 一行代码更新深层字段！
let company2 = over ceoSalaryL (* 1.1) sampleCompany

-- Iso: 类型同构转换
celFahIso :: Iso Double Double  -- Celsius ↔ Fahrenheit
viaIso celFahIso lens            -- 通过 Iso 转换 Lens 目标类型

-- Prism: 可能不存在的部分
_Left   :: Prism' (Either a b) a
_Just   :: Prism' (Maybe a) a
```

### 四种 Optics 对比

| Optic | 能力 | 典型场景 |
|-------|------|---------|
| **Lens** | 1-to-1 聚焦 | 深层字段访问/更新 |
| **Iso** | 双向转换 | 单位转换 (Celsius↔Fahrenheit) |
| **Prism** | 0-or-1 聚焦 | 提取 Maybe/Either 中的值 |
| **Traversal** | 0-to-N 聚焦 | 列表/树中所有元素批量更新 |

---

## 第十阶段：专家 — Parser Combinators

### 目标
用纯函数式方法构建解析器，无需正则或外部工具。

### Demo：`10_ParserCombinators.hs`

```haskell
-- Parser 类型定义
newtype Parser a = Parser { runParser :: String -> [(a, String)] }
-- 返回列表支持回溯：(解析结果, 剩余字符串)

instance Functor Parser where fmap f ...
instance Applicative Parser where pure ... (<*>) ...
instance Monad Parser where (>>=) ...
instance Alternative Parser where empty ... (<|>) ...

-- 算术表达式求值器（优先级处理）
expr   ::= term (('+' | '-') term)*
term   ::= factor (('*' | '/') factor)*
factor ::= int | '(' expr ')' | '-' factor

-- JSON 解析器
jsonValue ::= jsonNull | jsonBool | jsonNumber | jsonString 
          | jsonArray | jsonObject
```

### 核心思想

- **Parser 是一个 Monad**：可以用 `do` 表示法顺序组合
- **Parser 是一个 Alternative**：可以用 `<|>` 做选择/回溯
- **Parser 组合即程序**：小的 parser 组合成复杂的语法分析器
- **Parsec 库**是 Haskell 最流行的解析器框架，本 demo 实现了简化版

---

## 第十一阶段：专家 — Free Monads & DSL

### 目标
将"做什么"(DSL)与"怎么做"(解释器)分离，实现可测试、可替换效果的程序。

### Demo：`11_FreeMonadsAndDSL.hs`

```haskell
-- Free Monad 定义
data Free' f a = Pure' a | Free' (f (Free' f a))

-- Console DSL（纯声明式的交互流程）
data ConsoleF next = PutStrLn String next | GetLine (String -> next) | ...
consoleProgram = do
  putStrLn' "请输入名字:"
  name <- getLine'
  putStrLn' $ "欢迎, " ++ name ++ "!"

-- 同一个程序，多种解释器：
runConsoleIO   :: Free' ConsoleF a -> IO a      -- 真实 IO 执行
runConsoleLog  :: Free' ConsoleF a -> [String]   -- 日志收集（测试用）
```

### Free Monad 核心价值

1. **描述 WHAT 不关心 HOW** — DSL 只声明意图
2. **多解释器模式** — 同一程序可以有多种执行方式
3. **天然可测试** — 用 Mock 解释器替代真实副作用
4. **审计追踪** — 解释器天然可以记录所有操作

---

## 第十二阶段：专家 — 属性测试 (QuickCheck 风格)

### 目标
从"手写测试用例"升级为"属性断言+随机生成"，让计算机帮你找 bug。

### Demo：`12_QuickCheck.hs`

```haskell
-- 传统测试: assert(reverse([1,2,3]) == [3,2,1])  -- 只测一种情况!

-- 属性测试: ∀ xs. reverse(reverse(xs)) == xs       -- 覆盖所有可能!
prop_reverseReverse = forAll genList genChar
  (\xs -> reverse (reverse xs) == xs)
  "reverse.reverse = id"

prop_sortedIsOrdered = forAll (genList genInt)
  (\xs -> isSorted (sort xs))
  "sort produces ordered list"

-- Generator 组合子
genIntRange (-100, 100)     -- 有界整数
genList genChar             -- 随机长度字符列表
genPair genA genB           -- 元组
genMaybe genA               -- 可能值
```

### QuickCheck vs 传统测试

| 维度 | 传统测试 | 属性测试 |
|------|---------|---------|
| 测试数量 | 手写 N 个 | 自动生成数百个 |
| 覆盖范围 | 你想到的情况 | 随机探索边界条件 |
| 失败信息 | 固定输入 | 自动 shrink 到最小反例 |
| 统计能力 | 无 | classify/collect 分类统计 |

---

## 第十三阶段：专家 — Arrow & Profunctor

### 目标
理解比 Monad 更抽象的计算模型，掌握函数式 optics 的数学基础。

### Demo：`13_ArrowAndProfunctor.hs`

```haskell
-- Arrow proc 语法（类似电路图连接）
validateUser = proc input -> do
  nonEmpty <- arr (not . null) -< input
  if nonEmpty then do
    parts <- arr splitComma -< input
    returnA -< Right (name', age, email')
  else
    returnA -< Left "不能为空"

-- Arrow 组合操作
(>>>)  :: Arrow a => a b c -> a c d -> a b d   -- 管道
(***)  :: Arrow a => a b c -> a b' c' -> a (b,b') (c,c')  -- 并行
(&&&)  :: Arrow a => a b c -> a b d -> a b (c,d)  -- 分叉(复制输入)
(|||)  :: ArrowChoice a => a b d -> a c d -> a (Either b c) d  -- 选择

-- Profunctor: 反变+协变的二元 functor
class Profunctor p where
  dimap :: (c -> a) -> (b -> d) -> p a b -> p c d

-- 函数就是最简单的 Profunctor
instance Profunctor (->) where
  dimap f g h = g . h . f
```

### Arrow vs Monad 选择指南

| 场景 | 选择 | 原因 |
|------|------|------|
| 动态分支/依赖下一步结果 | **Monad** | `>>=` 支持动态决策 |
| 静态数据流/电路模拟 | **Arrow** | 编译时确定拓扑结构 |
| 需要并行化分析 | **Arrow** | `(***)` 天然并行 |
| 需要静态优化/融合 | **Arrow** | 图重写优化 |
| 常规业务逻辑 | **Monad** | 更直观易读 |

---

## 运行方法

```bash
cd /path/to/haskell/

# GHC 已安装 (9.14.1)
runghc 01_PureAndLazy.hs
runghc 02_TypeClassAndMonad.hs
runghc 03_CurryAndCompose.hs
runghc 04_IOAndSideEffects.hs
runghc 05_StateAndReader.hs   # 需要 mtl 库
runghc 06_ConcurrencySTM.hs   # 需要 stm 库
runghc 07_TypesAndADT.hs
runghc 08_MonadTransformers.hs   # 需要 mtl 库
runghc 09_LensAndOptics.hs
runghc 10_ParserCombinators.hs
runghc 11_FreeMonadsAndDSL.hs
runghc 12_QuickCheck.hs
runghc 13_ArrowAndProfunctor.hs  # 需要 arrows 库（GHC 内置）
```

> **注意**: `05`、`06`、`08` 需要 `mtl` 库，`06` 还需要 `stm` 库。这些随 GHC 标准安装。

---

## Haskell vs Scala 核心概念速查

| 概念 | Haskell | Scala (Cats) |
|------|---------|-------------|
| 纯函数 | 默认 | 约定 |
| 惰性求值 | 默认 | `LazyList` / `by-name` |
| 类型类 | `class` / `instance` | `trait` + `given` |
| 柯里化 | 默认 | 手动或 `.curried` |
| 函数组合 | `(.)` | `andThen` / `compose` |
| Maybe | `Maybe` | `Option` |
| 错误处理 | `Either` | `Either` / `EitherT` |
| IO 封装 | `IO a` | `cats.effect.IO[A]` |
| 可变状态 | `IORef` / `TVar` | `Ref[IO, A]` |
| 状态 Monad | `State s a` | `StateT[IO, S, A]` |
| 环境 Monad | `Reader r a` | `ReaderT[IO, R, A]` |
| 并发 | `forkIO` | `IO.start` / `Fiber` |
| 事务内存 | STM / TVar | `cats-stm` |
| Functor | `Functor` typeclass | `Functor[F[_]]` |
| Monad | `Monad` typeclass | `Monad[F[_]]` |

---

## 推荐学习顺序

### 入门路线（01-07）
1. `01_PureAndLazy.hs` — 建立纯函数和惰性直觉
2. `03_CurryAndCompose.hs` — 学会函数组合风格
3. `02_TypeClassAndMonad.hs` — 理解类型类和 Monad
4. `04_IOAndSideEffects.hs` — 掌握副作用隔离
5. `05_StateAndReader.hs` — 深入 Monad Transformer
6. `07_TypesAndADT.hs` — 用类型驱动设计
7. `06_ConcurrencySTM.hs` — 掌握 Haskell 并发模型

### 专家路线（08-13）
8. `08_MonadTransformers.hs` — Transformer 堆叠实战
9. `10_ParserCombinators.hs` — 用纯函数构建解析器
11. `11_FreeMonadsAndDSL.hs` — DSL 与解释器分离模式
9. `09_LensAndOptics.hs` — 函数式深层更新
12. `12_QuickCheck.hs` — 属性驱动测试思维
13. `13_ArrowAndProfunctor.hs` — 最高阶抽象

### 进阶工程路线（14-22）
14. `14_FoldableTraversable.hs` — 遍历与累积
15. `15_TypesAdvanced.hs` — GADTs / DataKinds / TypeFamilies
16. `16_StreamingPipeline.hs` — 手写流式管道
17. `17_LambdaCalculusAndFix.hs` — λ 演算与 fix
18. `18_AlternativeAndMonadPlus.hs` — 选择/零元抽象
19. `19_ExceptionsAndConcurrency.hs` — 异常与并发
20. `20_GenericsAndDerivingVia.hs` — GHC.Generics + DerivingVia
21. `21_EffectSystemPatterns.hs` — Effect System 常见实现手法
22. `22_FPBestPracticesAndStateMachineTests.hs` — 工程最佳实践 + 状态机属性测试

### 研究方向路线（23-26，锦上添花）
23. `23_Comonad.hs` — Comonad 三大实例（NonEmpty / Zipper / Store）
24. `24_RecursionSchemes.hs` — 把递归本身抽象掉（cata / ana / hylo / para）
25. `25_DependentTypesAndSingletons.hs` — 把"长度"搬进类型的依值类型直觉
26. `26_AdvancedResearchDirections.hs` — FP 研究方向地图 + 跨语言对照表

---

> 前 22 个 Demo 已经完整覆盖了 **学习 + 工程落地** 的全部关键点；
> 23-26 是"再往上走"的几条研究方向路标，
> 跨语言对照另见仓库根目录 [`LANGUAGE_COMPARISON.md`](../LANGUAGE_COMPARISON.md)。
