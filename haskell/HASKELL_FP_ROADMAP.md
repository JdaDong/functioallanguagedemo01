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
| `14_FoldableTraversable.hs` | Foldable & Traversable | foldMap/Monoid 聚合、traverse/sequenceA、effect 编织 |
| `15_TypesAdvanced.hs` | GADTs / Phantom / TypeFamilies | 类型安全表达式、DataKinds 状态机、type family |
| `16_StreamingPipeline.hs` | Streaming Pipeline | 手写最小流、map/filter/take、conduit/pipes 直觉 |
| `17_LambdaCalculusAndFix.hs` | λ 演算 / fix / 折叠同构 | Church 编码、匿名递归、Y 组合子、cata/ana 起点 |
| `18_AlternativeAndMonadPlus.hs` | Alternative / MonadPlus | empty/`<\|>`、some/many、guard、回溯搜索 |
| `19_ExceptionsAndConcurrency.hs` | 异常 / 资源 / 并发 | try/catch、bracket、forkIO+MVar、最小 Deferred |
| `20_GenericsAndDerivingVia.hs` | Generics / DerivingVia | 四种 deriving 策略、GHC.Generics、Identity/Const/Compose |
| `21_EffectSystemPatterns.hs` | Effect System 手法 | MTL class 抽象能力 + 手写 Free Eff、生产/测试双实例 |
| `22_FPBestPracticesAndStateMachineTests.hs` | 工程收口 | 状态机属性测试、ReaderT Env IO 模式、扩展/测试金字塔 |
| `23_Comonad.hs` | Comonad | NonEmpty 扫描、Zipper（一维 CA）、Store 模式 |
| `24_RecursionSchemes.hs` | Recursion Schemes | pattern functor、cata/ana/hylo/para、把递归抽象掉 |
| `25_DependentTypesAndSingletons.hs` | 依值类型直觉 | 类型级 Nat、SNat 单例、长度索引 Vec、类型级加法 |
| `26_AdvancedResearchDirections.hs` | FP 研究方向地图 | Liquid/Linear/Dependent、Effect 流派、FRP、分布式、形式化 |
| `27_GADTsAndTypeFamilies.hs` | 类型族进阶（15 号补充） | data family、associated type、injectivity、定长向量、异构 payload 状态机 |
| `28_MonadTransformersVsMTL.hs` | Transformer vs MTL 风格 | 同一业务两种写法、lift 显式链 vs 类约束、选型建议 |
| `29_ServantMinimalApi.hs` | Servant 最小 REST API | 类型级路由 DSL、Capture、Handler、Warp 启动 |
| `30_ServantJsonCrud.hs` | Servant JSON CRUD | `ReqBody`、`PostCreated`、`DeleteNoContent`、STM 内存仓储 |
| `31_WarpMiddleware.hs` | WAI/Warp 中间件 | `Application -> Application`、日志/CORS/耗时/Server 头组合 |
| `32_PersistentSqliteCrud.hs` | Persistent + SQLite CRUD | TH schema、migration、`SqlPersistT`、insert/select/update/delete |
| `33_StmBankTransferRetry.hs` | STM 细粒度转账 | `retry` 阻塞等醒、`orElse` 事务组合、多 TVar 原子更新 |
| `34_AsyncConcurrentlyRace.hs` | async 并发组合子 | `concurrently`、`race`、`mapConcurrently`、`withAsync` 作用域化 |
| `35_IoRefVsMVarVsTVar.hs` | 三种可变引用对比 | IORef 的 RMW race、MVar 互斥、TVar 事务；选型速查 |
| `36_PhantomTypesStateMachine.hs` | 幻影类型状态机 | `DataKinds` 提升、`Post 'Draft`/`'Published`/`'Archived` 编译期保证 |
| `37_LazyEvaluationTricks.hs` | 惰性求值实战小套路 | 自引用 fibs、牛顿迭代流、foldr 短路、foldl vs foldl' |
| `38_GADTInterpreter.hs` | GADT 类型安全解释器（批次4） | 类型安全 AST、HList 环境、变量索引、lambda/app |
| `39_STMonadInPlace.hs` | ST Monad 局部可变（批次4） | runST 逃逸管控、STRef、原地快排/原地计数、对外纯 |
| `40_TemplateHaskellIntro.hs` | Template Haskell 入门（批次4） | 编译期代码生成、Q Monad、Exp/Dec 拼接、splice/quote |
| `41_TypeableDynamic.hs` | Typeable & Dynamic（批次4） | TypeRep、cast、Dynamic 容器、运行时类型分发 |

> 29~32 依赖外部包（servant / warp / persistent / aeson 等），使用 Cabal script header，需用 `cabal run` 运行，详见下文"运行方法"。

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

## 第十四阶段：专家 — Foldable & Traversable

### 目标
把"遍历结构"和"编织 effect"这两件事，抽象成同一个抽象的两张面孔。

### Demo：`14_FoldableTraversable.hs`

```haskell
-- Foldable：任何结构都能被聚合成一个值
-- 只要实现 foldr 或 foldMap 其中之一
sumViaFoldMap     = getSum     . foldMap Sum
productViaFoldMap = getProduct . foldMap Product
allPositive       = getAll     . foldMap (All . (> 0))

-- foldMap + Monoid 一遍遍历同时算多个聚合量
avg xs =
  let (Sum s, Sum n) = foldMap (\x -> (Sum x, Sum 1)) xs
  in  s / n

-- Traversable：结构化遍历 + effect 编织
--   [IO a]                -> IO [a]
--   [Maybe a]             -> Maybe [a]
--   Tree (Either e a)     -> Either e (Tree a)
-- 都只是 traverse / sequenceA 的特例。
users <- traverse fetchUser userIds     :: IO [User]
result  = traverse validate rawInputs    :: Either String [Valid]
```

### 关键理解

| 抽象 | 核心方法 | 直觉 |
|------|---------|------|
| `Foldable t` | `foldMap :: Monoid m => (a->m) -> t a -> m` | 用 Monoid 把结构压扁 |
| `Traversable t` | `traverse :: Applicative f => (a->f b) -> t a -> f (t b)` | 保持形状，把 effect 提到外层 |

**工程价值**：一次写的 `fetchUser :: Id -> IO User`，搭配 `traverse` 自动得到批量版本。Scala 的 `traverse` / `sequence` 正是同一抽象。

---

## 第十五阶段：专家 — GADTs / Phantom Type / Type Families

### 目标
用 Haskell 最强的类型武器把错误消灭在编译阶段。

### Demo：`15_TypesAdvanced.hs`

```haskell
-- GADT：每个构造器单独指定它产生的类型参数
data Expr a where
  IntE  :: Int  -> Expr Int
  BoolE :: Bool -> Expr Bool
  Add   :: Expr Int  -> Expr Int  -> Expr Int
  Eq    :: Eq a => Expr a -> Expr a -> Expr Bool
  If    :: Expr Bool -> Expr a -> Expr a -> Expr a

eval :: Expr a -> a              -- 返回类型与表达式类型一致，无需 Either tag
eval (IntE n)  = n
eval (Add x y) = eval x + eval y
eval (If c t e) = if eval c then eval t else eval e

-- Phantom Type：类型参数当"标签"用，零运行时开销
data Unvalidated; data Validated
newtype Tagged t a = Tagged a
validateEmail :: Tagged Unvalidated String -> Maybe (Tagged Validated String)

-- DataKinds 状态机：订单状态被提升到类型层
data OrderStatus = Placed | Paid | Shipped
data Order (s :: OrderStatus) = Order { ... }
pay     :: Order 'Placed  -> Order 'Paid      -- 编译期保证状态转移合法
shipIt  :: Order 'Paid    -> Order 'Shipped

-- Type Family：类型层面的函数
type family ElemT c where
  ElemT [a]      = a
  ElemT (Maybe a) = a
```

### 和 27 号的分工

15 号讲 **GADT 基础 + Phantom + DataKinds 状态机 + closed type family**；27 号讲 **data family / associated type / injectivity / 递归归纳型族 / 异构 payload**。

---

## 第十六阶段：专家 — Streaming Pipeline

### 目标
把 "源 / 变换 / 消费" 分离，严格控制 effect 发生时机 —— 建立 fs2 / conduit / pipes 的直觉。

### Demo：`16_StreamingPipeline.hs`

```haskell
-- 最小流模型：一层只做一件事（生产一个元素或结束）
data Step m a     = Done | Yield a (Stream m a)
newtype Stream m a = Stream { runStep :: m (Step m a) }

fromList   :: Monad m => [a] -> Stream m a
naturals   :: Monad m => Stream m Int          -- 无限自然数流
tickEvery  :: Int -> Stream IO Int             -- 每次被拉动时打一行日志

-- 组合子：都是"按需拉"
smap   :: Monad m => (a -> b)   -> Stream m a -> Stream m b
sfilter:: Monad m => (a -> Bool) -> Stream m a -> Stream m a
stake  :: Monad m => Int         -> Stream m a -> Stream m a

-- 消费者：只在这里真正触发 effect
sfold  :: Monad m => (b -> a -> b) -> b -> Stream m a -> m b
stoList:: Monad m => Stream m a -> m [a]
```

### 为什么需要它？
普通列表 + 惰性求值就能表达"按需计算"，但一旦涉及 IO（文件、网络），必须把 **"拉动下一个元素" = "触发一次 effect"** 这件事显式化。16 号用约 100 行写出同构于 conduit 的最小版本。

### 横向对比
Scala fs2 `Stream[F, A]`、Rust `futures::Stream`、Elixir `Stream + Flow` 三者的核心抽象都与此同型。

---

## 第十七阶段：专家 — Lambda 演算 / fix / 折叠同构

### 目标
追到 FP 的数学底座：所有语法糖、递归、数据类型都能追到 λ 演算。

### Demo：`17_LambdaCalculusAndFix.hs`

```haskell
-- Church 编码：没有数据类型，只有函数
cTrue, cFalse :: a -> a -> a
cTrue  t _ = t
cFalse _ f = f

-- 自然数 n = "把 f 连用 n 次"
cZero, cOne, cTwo :: (a -> a) -> a -> a
cZero  _ x = x
cOne   f x = f x
cTwo   f x = f (f x)
cSucc n f x = f (n f x)

-- 匿名递归：没有 let rec，用 fix
factorial :: Int -> Int
factorial = fix (\rec n -> if n == 0 then 1 else n * rec (n - 1))

-- 这正是 Y 组合子在 Haskell 里的直接对应
-- 等价定义：fix f = let x = f x in x

-- foldr 是 List 的唯一范畴论正统 eliminator
-- 因此 length / map / filter / reverse 都能重写成 foldr 的特例
mapViaFoldr    f = foldr (\x acc -> f x : acc) []
filterViaFoldr p = foldr (\x acc -> if p x then x : acc else acc) []
```

### 关键理解
- **Church 编码**告诉你：函数就够了，其他都是糖。
- **fix** 把递归变成"值"（不动点），为 24 号 Recursion Schemes 做铺垫。
- **foldr 同构**告诉你：任何对 List 的"结构递归"，本质都是 `foldr cons nil`。

---

## 第十八阶段：专家 — Alternative / MonadPlus

### 目标
在 Monad "链式计算" 之上加一维 **"失败 / 选择 / 非确定"** 抽象。

### Demo：`18_AlternativeAndMonadPlus.hs`

```haskell
-- 核心方法
--   empty  :: f a              失败
--   (<|>)  :: f a -> f a -> f a 先试左边，不行试右边
--   some   :: f a -> f [a]     一次或多次
--   many   :: f a -> f [a]     零次或多次

-- Maybe：拿到第一个 Just
lookupMulti key dicts = foldr (<|>) empty (map (lookup key) dicts)

-- List 的 MonadPlus = "非确定计算 + 回溯"
pythTriples n = do
  a <- [1..n]; b <- [a..n]; c <- [b..n]
  guard (a*a + b*b == c*c)       -- 不满足就 empty，分支剪掉
  pure (a, b, c)

-- N 皇后（简化版）：几行搞定
queens n = go n
  where
    go 0 = pure []
    go k = do
      qs <- go (k - 1)
      q  <- [1 .. n]
      guard (safe q qs)
      pure (q : qs)
```

### 这抽象在哪里出现？
- **Parser 的 `<|>`**（Demo 10）就是 Alternative 最经典的用武之地。
- **regex / 查询 DSL / 解谜器** 背后都是它。
- **Scala cats 的 `MonoidK` / `Alternative`** 同型。

---

## 第十九阶段：专家 — 异常 / 资源 / 并发

### 目标
建立真实工程的 effect 管理直觉：异常、资源安全、并发同步。

### Demo：`19_ExceptionsAndConcurrency.hs`

```haskell
-- 两类错误的分野
--   1) 业务逻辑失败 -> Either / ExceptT
--   2) IO 外部意外  -> Control.Exception
instance Exception BusinessError
safeLookup n = try (lookupUser n) :: IO (Either BusinessError String)

-- bracket = Scala cats-effect Resource
-- 保证 use 抛异常时 release 一定执行
withConn :: (Conn -> IO a) -> IO a
withConn = bracket (openConn 1) closeConn

-- forkIO + MVar 手写最小 Deferred（= async 的 Fiber 雏形）
async' :: IO a -> IO (MVar a)
async' io = do
  box <- newEmptyMVar
  _   <- forkIO (io >>= putMVar box)
  pure box

wait :: MVar a -> IO a
wait = takeMVar
```

### 核心对照表

| Haskell | Cats Effect | 用途 |
|---------|------------|------|
| `bracket` | `Resource` | 资源获取/释放 |
| `finally` | `guarantee` | 清理钩子 |
| `try / catch` | `attempt / handleErrorWith` | 异常转值 |
| `forkIO + MVar` | `IO.start + Deferred` | 轻量并发 |
| `MVar` 互斥 | `Semaphore(1)` | 锁 |

---

## 第二十阶段：专家 — Generics / DerivingVia

### 目标
用 **类型结构即编码** 的思路，把"手写样板代码"变成"一行 deriving"。

### Demo：`20_GenericsAndDerivingVia.hs`

```haskell
-- 四种 deriving 策略（读任何现代 Haskell 工程必备）
deriving stock    (Eq, Show, Generic)   -- 编译器内置
deriving newtype  (Show, Eq, Ord, Num)  -- 平移底层实例
deriving anyclass (MyClass)             -- 用 class 的默认方法
deriving via T (MyClass)                -- 以 T 的实例语义实现

-- GHC.Generics：类型的结构反射
class GCountFields f where gCount :: f p -> Int
instance GCountFields U1 where gCount _ = 0
instance GCountFields (K1 i c) where gCount _ = 1
instance (GCountFields a, GCountFields b) => GCountFields (a :*: b) where
  gCount (x :*: y) = gCount x + gCount y

-- 一次实现，所有派生 Generic 的类型自动具备
countFields :: (Generic a, GCountFields (Rep a)) => a -> Int

-- 三个"抽象适配器"
--   Identity f a = f a       （让普通 a 假装是个 f a）
--   Const c a    = c         （容器形状，但不装 a）
--   Compose f g a = f (g a)   （两层 functor 组合）
```

### 工程意义
`aeson` / `persistent` / `beam` / `servant` 的用法（"加一行 deriving 就能 encode/decode/存表/生成路由"）全都建立在 GHC.Generics + DerivingVia 之上。

---

## 第二十一阶段：专家 — Effect System 手法

### 目标
把 11 号（Free）+ 08 号（Transformer）融会贯通，理解 mtl / free / polysemy 三家共同的"核心套路"。

### Demo：`21_EffectSystemPatterns.hs`

```haskell
-- Part A：MTL 风格 —— 用 class 抽象"能力"
class Monad m => MonadLogger   m where
  logInfo  :: String -> m ()
class Monad m => MonadUserStore m where
  getUser  :: String -> m (Maybe String)
  putUser  :: String -> String -> m ()

-- 业务：只依赖能力，不关心实现
registerUser :: (MonadLogger m, MonadUserStore m) => String -> String -> m Bool

-- 实例 1：真实 IO 环境（IORef 假装 DB）
newtype ProdM a = ProdM { runProdM :: IORef (Map String String) -> IO a }
instance MonadLogger ProdM    where logInfo  = ProdM . const . putStrLn
instance MonadUserStore ProdM where getUser k = ...

-- 实例 2：测试 Mock（纯 State + Writer，无 IO）
newtype TestM a = TestM { runTestM :: [String] -> Map String String
                                     -> ([String], Map String String, a) }

-- Part B：手写最小 Free Eff —— "指令 + 解释器" 分离
data Instr next = Log String next | Get String (Maybe String -> next) | ...
```

### 三家对照

| 方案 | 本 Demo / 对应 | 特点 |
|------|-------------|------|
| **mtl** | 21 号 Part A + 08 号 | 主流，类型推导稳 |
| **free / freer** | 21 号 Part B + 11 号 | 多解释器、可审计 |
| **polysemy / effectful** | 本 Demo 未实现 | 零成本抽象，工程新宠 |

---

## 第二十二阶段：专家 — FP 工程化收口 + 状态机属性测试

### 目标
把 01–21 的所有抽象**钉死在真实工程直觉里**。

### Demo：`22_FPBestPracticesAndStateMachineTests.hs`

```haskell
-- Part A：基于状态机的属性测试
--   SUT（被测系统）= 可变计数器 IORef Int
--   模型          = 纯 Int
--   随机生成命令序列：[Inc, Dec, Reset, ...]
--   同时跑 SUT 和模型，比对最终状态
data Cmd = Inc | Dec | Reset deriving (Show, Eq)
stepModel :: Cmd -> Int -> Int
stepSUT   :: Cmd -> Counter -> IO ()

-- 随机生成 N 条序列，一有差异立即暴露
runStateMachineProperty :: Int -> IO ()

-- Part B：Haskell 工程最佳实践清单
-- 1) 项目结构：cabal + hpack + stack；Main/Lib/Test 三层
-- 2) 运行时：ReaderT Env IO + tagless class（Env 放 log / db / config）
-- 3) 性能：严格字段 !、Text/ByteString 替代 String、STM 粒度控制
-- 4) 测试金字塔：unit → property → state-machine → integration
```

### 这套组合拳在哪里能用？
银行系统、数据库引擎、分布式协议（Paxos/Raft 实现常这样测）、云服务 API —— 凡是"状态转移必须正确"的系统都适用。`hedgehog` / `quickcheck-state-machine` 是工业级的此类框架。

---

## 第二十三阶段：拓展研究 — Comonad

### Demo：`23_Comonad.hs`

Comonad 是 Monad 的**类型级对偶**。

```haskell
--   Monad:    return  :: a -> m a        join    :: m (m a) -> m a
--   Comonad:  extract :: w a -> a        duplicate :: w a -> w (w a)

class Functor w => Comonad w where
  extract   :: w a -> a
  duplicate :: w a -> w (w a)
  extend    :: (w a -> b) -> w a -> w b

-- 三大实例（都手写，无 comonad 库依赖）
--   a) NonEmpty 流：extend 做"扫描聚合"（= scanr）
--   b) Zipper    ：一维 cellular automaton (规则 110)
--   c) Store     ：builder 模式的 FP 表达（focus + 观察函数）
```

**直觉**：`w a` 就是"带上下文的一个 a"；`extend f` 就是"用上下文感知函数 f 重新计算每个位置"。图像滤波、卷积、Conway 生命游戏都是它的天然用例。

---

## 第二十四阶段：拓展研究 — Recursion Schemes

### Demo：`24_RecursionSchemes.hs`

Monad 把**副作用**抽象出来；Recursion Schemes 把**递归本身**抽象出来。

```haskell
-- 骨架（90 行内手写，无库依赖）
newtype Fix f = Fix { unFix :: f (Fix f) }
cata  :: Functor f => (f a -> a)          -> Fix f -> a   -- fold
ana   :: Functor f => (a -> f a)          -> a     -> Fix f -- unfold
hylo  :: Functor f => (f b -> b) -> (a -> f a) -> a -> b  -- fuse
para  :: Functor f => (f (Fix f, a) -> a) -> Fix f -> a   -- fold 带子树

-- 用在自然数、表达式树、列表上：同一骨架换不同代数
data NatF  a = ZeroF | SuccF a       deriving Functor
data ExprF a = Lit Int | Add a a     deriving Functor
data ListF x a = NilF | ConsF x a    deriving Functor
```

**价值**：一旦把递归从业务里抽掉，优化（fusion）、可视化、并行化、变种（带注释、带路径）都变得可组合。库对应 `recursion-schemes`。

---

## 第二十五阶段：拓展研究 — 依值类型直觉

### Demo：`25_DependentTypesAndSingletons.hs`

真正的依值类型需要 Idris / Agda / Coq；Haskell 用 **DataKinds + GADTs + TypeFamilies + 手写 Singleton** 模拟大部分编译期长度校验。

```haskell
-- 类型级自然数
data Nat = Z | S Nat          -- 启用 DataKinds 后 'Z / 'S n 也是 kind

-- 类型级加法
type family (n :: Nat) + (m :: Nat) :: Nat where
  'Z    + m = m
  ('S n) + m = 'S (n + m)

-- 长度索引向量
data Vec (n :: Nat) a where
  VNil  :: Vec 'Z a
  VCons :: a -> Vec n a -> Vec ('S n) a

-- 头函数：编译期就拒绝空向量
vhead :: Vec ('S n) a -> a

-- append 的长度被类型保证正确
vappend :: Vec n a -> Vec m a -> Vec (n + m) a
```

**思路切换**：把类型签名当主角，运行时的值只是"见证者"。`singletons` 库把这一套系统化。

---

## 第二十六阶段：拓展研究 — FP 研究方向地图

### Demo：`26_AdvancedResearchDirections.hs`

这一站不写新实现，而是把"再往上走"的几条活跃研究方向画成**地图**，方便挑自己感兴趣的深入。主要索引：

- **A. 类型系统进阶**：Liquid Haskell / Linear Haskell / Dependent Haskell / Row polymorphism
- **B. Effect Systems 流派**：mtl / Tagless Final / Free-Freer-Eff / polysemy / fused-effects / effectful / cleff / Koka
- **C. 并发 & 异步**：STM / unliftio / resourcet / streaming-pipes-conduit / Dunai / FRP (Yampa / reflex)
- **D. 并行计算**：par/pseq / Strategies / accelerate / repa / massiv / Cloud Haskell
- **E. 形式化 & 证明**：Agda / Coq / Lean / Isabelle / LiquidHaskell refinement
- **F. 前端 & UI**：GHCJS / Reflex-FRP / Elm / PureScript
- **G. 性能方向**：GHC core/STG、unboxed、inspection-testing、linear types 零拷贝

**定位**：这一章是"哪条路你要不要挑？"的路标，而不是教程。

---

## 附加章节 A：类型族进阶（15 号补充）

### Demo：`27_GADTsAndTypeFamilies.hs`

15 号 `15_TypesAdvanced.hs` 已覆盖 GADT 基础、Phantom Type、DataKinds 状态机、独立 type family；本 Demo 专门补那部分**没**讲的：

```haskell
-- data family：同名类型族，每个索引用完全不同的构造子存储
data family Array a
data instance Array Bool = ABool [Bool]
data instance Array Int  = AInt  [Int]

-- associated type family：type family 绑在类型类上
class Container c where
  type Elem c :: Type
  empty  :: c
  insert :: Elem c -> c -> c

-- injective：由结果可反推参数，帮助类型推导
type family UnElem c = r | r -> c where
  UnElem [a] = a

-- 归纳型族 + GADT：类型安全的定长向量 append
type family (n :: Nat) :+ (m :: Nat) :: Nat where
  'Z     :+ m = m
  ('S n) :+ m = 'S (n :+ m)

data Vec (n :: Nat) a where
  VNil  :: Vec 'Z a
  VCons :: a -> Vec n a -> Vec ('S n) a

vappend :: Vec n a -> Vec m a -> Vec (n :+ m) a  -- 长度编译期验证

-- data family 状态机：每个状态带各自 payload（15 号做不到）
data family Message (s :: MsgState)
data instance Message 'SDraft = Draft { draftText :: String }
data instance Message 'SSent  = Sent  { sentText :: String, sentTo :: String }
data instance Message 'SAcked = Acked { ackedTo :: String, ackId :: Int }
```

### 与 15 号的分工

| 主题 | 15 号 | 27 号 |
|------|-------|-------|
| GADT 基础 / Phantom / DataKinds | ✅ | — |
| 独立 type family、简单 closed family | ✅ | — |
| `data family` | — | ✅ |
| associated type family | — | ✅ |
| injective type family | — | ✅ |
| 递归归纳型族（类型层面自然数加法） | — | ✅ |
| 异构 payload 状态机 | — | ✅ |

---

## 附加章节 B：Monad Transformers vs MTL 风格

### Demo：`28_MonadTransformersVsMTL.hs`

8 号 `08_MonadTransformers.hs` 讲了 transformer 堆叠；本 Demo 用**同一个业务**（批量处理订单：Reader 读配置 + State 计数 + Except 报错）分别写**两种风格**，让差异一目了然：

```haskell
-- Part 1: 具体 transformer 栈
type AppT a = ExceptT BizErr (StateT Counter (ReaderT Config Identity)) a

processOrderT :: Order -> AppT Int
processOrderT o = do
  cfg <- lift . lift $ ask       -- 穿两层 lift
  when (amt > max) $ throwError ...
  lift $ modify (+1)             -- 穿一层 lift
  ...

-- Part 2: MTL 类约束（函数体几乎相同，但签名是"能力集合"）
processOrderM ::
  ( MonadReader Config m
  , MonadState Counter m
  , MonadError BizErr m
  ) => Order -> m Int
processOrderM o = do
  cfg <- ask                      -- 无 lift
  when (amt > max) $ throwError ...
  modify (+1)
  ...
```

### 两种风格的取舍

| 维度 | Transformer 具体栈 | MTL 类约束 |
|------|----|----|
| 函数签名 | 冗长、精确 | 只声明需要的能力 |
| `lift` | 需要，层数越深越多 | 不需要 |
| 换层序 / 换底座 | 改所有签名 | 业务函数不动 |
| 类型推导 | 稳健 | 层数多时可能吃力 |
| GHC 扩展 | 少 | 需要 `FlexibleContexts` 等 |

**经验法则**：库和跨层复用代码用 MTL 风格；应用入口（`runXxx`）写具体栈；大型项目常两者混用。

---

## 附加章节 C：Servant 最小 REST API

### Demo：`29_ServantMinimalApi.hs`

Servant 把 **"API 定义"变成一个类型**：路由 / 参数 / 方法 / 返回值全部在类型层。

```haskell
type API = "hello" :> Get '[JSON] String
      :<|> "users" :> Capture "uid" Int :> Get '[JSON] User
      :<|> "greet" :> Capture "name" String :> Get '[JSON] String

server :: Server API
server = helloH :<|> userH :<|> greetH       -- 顺序和类型必须严格一一对应

main = run 8080 (serve (Proxy :: Proxy API) server)
```

**价值**：编译期保证 handler 类型和路由签名一致；自动派生 client / 文档 / OpenAPI。靠 `cabal run` 跑（依赖 servant / warp）。

---

## 附加章节 D：Servant JSON CRUD

### Demo：`30_ServantJsonCrud.hs`

在 29 号基础上加 `ReqBody` / `PostCreated` / `DeleteNoContent` + STM 内存仓储，演示完整 User 资源 CRUD。

```haskell
type UserAPI =
       "users" :> Get         '[JSON] [User]
  :<|> "users" :> ReqBody '[JSON] NewUser :> PostCreated '[JSON] User
  :<|> "users" :> Capture "uid" Int :> Get '[JSON] User
  :<|> "users" :> Capture "uid" Int :> ReqBody '[JSON] User :> Put '[JSON] User
  :<|> "users" :> Capture "uid" Int :> DeleteNoContent

-- STM TVar 做内存存储 —— 天然线程安全
type Store = TVar (Map Int User)
```

---

## 附加章节 E：WAI / Warp 中间件

### Demo：`31_WarpMiddleware.hs`

中间件的签名只有一行：`type Middleware = Application -> Application`。

```haskell
logMw     :: Middleware     -- 请求/响应日志
corsMw    :: Middleware     -- CORS 头
timingMw  :: Middleware     -- 耗时统计
serverMw  :: Middleware     -- 自定义 Server 头

app :: Application
app = logMw . corsMw . timingMw . serverMw $ baseApp
```

**直觉**：用函数组合 `.` 堆叠，和 Express / Koa 的 `use(mw)` 本质一致，但这里没有可变数组，全是纯函数组合。

---

## 附加章节 F：Persistent + SQLite CRUD

### Demo：`32_PersistentSqliteCrud.hs`

TH schema 定义表，自动 migration + 类型安全查询。

```haskell
share [mkPersist sqlSettings, mkMigrate "migrateAll"] [persistLowerCase|
  Person
    name String
    age  Int
    deriving Show
|]

runSqlite ":memory:" $ do
  runMigration migrateAll
  pid <- insert (Person "Alice" 30)
  mp  <- get pid                        -- SelectFirst 类型安全
  update pid [PersonAge +=. 1]
  delete pid
```

**关键**：TH 自动生成 `PersonAge` / `PersonName` 等类型安全字段，写错列名编译不过。

---

## 附加章节 G：STM 细粒度转账 + retry

### Demo：`33_StmBankTransferRetry.hs`

06 号讲了 STM 基础；33 号聚焦 **`retry` 阻塞等醒** 和 **`orElse` 事务组合**。

```haskell
-- retry：条件不满足时原子地"挂起 + 重试"
transferWhenEnough :: TVar Int -> TVar Int -> Int -> STM ()
transferWhenEnough from to n = do
  bal <- readTVar from
  when (bal < n) retry           -- 自动等到余额够
  modifyTVar from (subtract n)
  modifyTVar to   (+ n)

-- orElse：两个事务任选一个成功
preferA `orElse` preferB
```

**直觉**：`retry` = 声明式"等到条件成立"，不用条件变量、不用轮询、天然无死锁。

---

## 附加章节 H：async 并发组合子

### Demo：`34_AsyncConcurrentlyRace.hs`

`async` 库把 `forkIO + MVar + exception` 封装成一套**高层并发原语**。

```haskell
concurrently    :: IO a -> IO b -> IO (a, b)     -- 两个并行全部完成
race            :: IO a -> IO b -> IO (Either a b) -- 谁先完成谁赢，另一方自动取消
mapConcurrently :: Traversable t => (a -> IO b) -> t a -> IO (t b)
withAsync       :: IO a -> (Async a -> IO b) -> IO b  -- 作用域化，逃出自动取消
```

**对比 19 号**：19 号用 `forkIO + MVar` 手写原语（学原理）；34 号用 `async` 库（学工程习惯）。

---

## 附加章节 I：IORef vs MVar vs TVar

### Demo：`35_IoRefVsMVarVsTVar.hs`

三种可变引用的选型速查。

| 类型 | 语义 | 代价 | 典型场景 |
|------|------|------|----------|
| `IORef` | **非**原子 RMW，单线程安全 | 最小 | 单线程状态、缓存 |
| `MVar`  | 互斥 + 信号量（空/满） | 锁切换 | 锁、channel、一次性信号 |
| `TVar`  | STM 事务，可组合 | 事务开销 | 多变量原子更新、声明式等待 |

```haskell
-- IORef 典型坑：RMW race
modifyIORef  ref (+1)     -- 多线程下丢更新！
atomicModifyIORef ref (\x -> (x+1, ()))  -- 显式原子版本才安全
```

---

## 附加章节 J：Phantom Types 状态机

### Demo：`36_PhantomTypesStateMachine.hs`

用 **DataKinds + Phantom** 把状态提升到类型层，让"状态转移合法"由编译器检查。

```haskell
data PostState = Draft | Published | Archived
data Post (s :: PostState) = Post { title :: String, body :: String }

-- 只有 Draft 能 publish，只有 Published 能 archive —— 写反了编译不过
publish :: Post 'Draft     -> Post 'Published
archive :: Post 'Published -> Post 'Archived
edit    :: Post 'Draft     -> String -> Post 'Draft
```

**对比 15/25 号**：15 号讲概念，25 号讲类型级算术，36 号展示**业务场景**（博客、订单、审批流最常见）。

---

## 附加章节 K：惰性求值实战小套路

### Demo：`37_LazyEvaluationTricks.hs`

惰性求值不只是"酷"，在真实代码里有几个立刻能用的套路。

```haskell
-- 1) 自引用无限流（经典 Fibonacci）
fibs :: [Integer]
fibs = 0 : 1 : zipWith (+) fibs (tail fibs)

-- 2) 牛顿迭代：生成收敛数列，想要多少精度取多少
sqrtStream x = iterate (\g -> (g + x/g)/2) 1
mySqrt x eps = head $ dropWhile (\(a,b) -> abs (a-b) > eps) $ zip xs (tail xs)
  where xs = sqrtStream x

-- 3) foldr 短路：any / or / and 都是 foldr 特化，遇到答案立即停
any p = foldr (\x acc -> p x || acc) False   -- True 出现就停

-- 4) foldl vs foldl' —— 累积器强求值避免 thunk 堆积
sum'  = foldl' (+) 0   -- 正确
-- sum = foldl (+) 0   -- 大列表会栈溢出
```

**工程关键**：**写累加器永远用 `foldl'`**；无限结构 + `take` / `head` 是 Haskell 的惯用手段。

---

## 附加章节 L：GADT 类型安全解释器（批次 4）

### Demo：`38_GADTInterpreter.hs`

在 15 号 GADT 基础上再进一步：**带变量环境的 lambda 演算解释器**，用 HList 做强类型环境，变量用 de Bruijn 索引表示。编译期保证"查变量时索引存在且类型正确"。

```haskell
-- 类型层的上下文
data Ctx = Empty | (::>) Ctx *     -- 环境 = 类型列表

-- 变量就是带类型的索引
data Var :: Ctx -> * -> * where
  Here  :: Var (ctx '::> a) a
  There :: Var ctx a -> Var (ctx '::> b) a

-- 表达式由类型环境 ctx 和结果类型 a 共同索引
data Term :: Ctx -> * -> * where
  Lit   :: Int -> Term ctx Int
  VarE  :: Var ctx a -> Term ctx a
  Lam   :: Term (ctx '::> a) b -> Term ctx (a -> b)
  App   :: Term ctx (a -> b) -> Term ctx a -> Term ctx b

-- HList 形态的运行时环境
data Env :: Ctx -> * where
  ENil  :: Env 'Empty
  ECons :: a -> Env ctx -> Env (ctx '::> a)

-- eval 的类型签名本身就是"类型健全定理"
eval :: Env ctx -> Term ctx a -> a
```

**意义**：它是"Haskell 为什么是研究语言设计首选"的最短证明 —— 15 行搞定类型安全 STLC。

---

## 附加章节 M：ST Monad 局部可变（批次 4）

### Demo：`39_STMonadInPlace.hs`

`ST` 是 Haskell 的"局部可变逃生舱"：**内部用原地更新写高性能算法，对外呈现为纯函数**。秘密武器是 `runST` 的 rank-2 类型签名，利用幻影参数 `s` 防止 `STRef` 逃逸。

```haskell
runST :: (forall s. ST s a) -> a    -- 关键！forall s. 禁止 STRef 逃出

-- 原地计数排序：O(n+k)，对外纯
countingSort :: Int -> [Int] -> [Int]
countingSort k xs = runST $ do
  arr <- newArray (0, k) 0           :: ST s (STArray s Int Int)
  forM_ xs $ \x -> do
    c <- readArray arr x
    writeArray arr x (c + 1)
  concat <$> forM [0 .. k] (\i -> do
    c <- readArray arr i
    pure (replicate c i))

-- 原地快排：可变数组 + 分区
quicksortST :: Ord a => [a] -> [a]
```

**对照**：Rust `Vec::sort` 靠 `&mut`；Haskell 靠 `ST` + 类型系统把"局部可变"和"全局纯"精准分层。

---

## 附加章节 N：Template Haskell 入门（批次 4）

### Demo：`40_TemplateHaskellIntro.hs`

Template Haskell 让你 **在编译期生成代码**。Q Monad 里操纵 AST（`Exp` / `Dec` / `Pat`），最后 `splice` 拼回源程序。

```haskell
{-# LANGUAGE TemplateHaskell #-}

-- 生成字段访问器：一条命令生成 N 个 getter
mkGetters :: Name -> Q [Dec]
mkGetters typeName = do
  TyConI (DataD _ _ _ _ [RecC _ fields] _) <- reify typeName
  forM fields $ \(nm, _, _) -> do
    let fnName = mkName ("get_" ++ nameBase nm)
    [d| $(varP fnName) = $(varE nm) |] >>= \(d:_) -> pure d

-- Quote / Splice：把 AST 当值操纵
power :: Int -> Q Exp
power 0 = [| \_ -> 1 |]
power n = [| \x -> x * $(power (n - 1)) x |]

-- 使用：$$(power 4) 在编译期展开为 \x -> x * x * x * x * 1
```

**用武之地**：`persistent` 的表定义、`aeson-th` 的 JSON 实例、`lens-th` 的 Lens 生成 —— 写一遍模板，用一百次。

---

## 附加章节 O：Typeable & Dynamic（批次 4）

### Demo：`41_TypeableDynamic.hs`

Haskell 默认是静态类型，但通过 `Typeable` / `Dynamic` 可以在运行时**安全**地做类型分发。

```haskell
import Data.Typeable
import Data.Dynamic

-- TypeRep：类型的"运行时指纹"
tRep :: Typeable a => a -> TypeRep
tRep = typeOf

-- cast：带类型指纹的安全下行
cast :: (Typeable a, Typeable b) => a -> Maybe b

-- Dynamic：把任意 Typeable 值装进同一容器
box1 = toDyn (42 :: Int)
box2 = toDyn ("hello" :: String)
bag  = [box1, box2] :: [Dynamic]

showAny :: Dynamic -> String
showAny d
  | Just (n :: Int)    <- fromDynamic d = "int:" ++ show n
  | Just (s :: String) <- fromDynamic d = "str:" ++ s
  | otherwise                           = "unknown"
```

**定位**：不是让你写动态语言，而是**在插件系统 / 消息总线 / serialize 边界**这种"跨类型收纳点"派上用场。对照 Java 的反射、Rust 的 `Any`。

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
runghc 27_GADTsAndTypeFamilies.hs
runghc 28_MonadTransformersVsMTL.hs  # 需要 mtl 库
runghc 33_StmBankTransferRetry.hs    # 需要 stm 库
runghc 34_AsyncConcurrentlyRace.hs   # 需要 async 库（GHC 分发）
runghc 35_IoRefVsMVarVsTVar.hs       # 需要 stm 库
runghc 36_PhantomTypesStateMachine.hs
runghc 37_LazyEvaluationTricks.hs
runghc 38_GADTInterpreter.hs
runghc 39_STMonadInPlace.hs   # 需要 array 库（GHC boot 分发）
runghc 40_TemplateHaskellIntro.hs
runghc 41_TypeableDynamic.hs
```

> **注意**: `05`、`06`、`08`、`28` 需要 `mtl` 库，`06`、`33`、`35` 需要 `stm` 库，`34` 需要 `async` 库，`39` 需要 `array` 库。这些随 GHC 标准安装。`40` 需要启用 `TemplateHaskell` 扩展并用 GHC 8.10+。

### 29~32：需要 Cabal 拉依赖

这四个 Demo 引入了非 GHC boot 库的第三方包（servant / warp / aeson / persistent / stm / containers 等），每个文件顶部都写了 Cabal script header，用新版 cabal（建议 3.12+，本仓库开发环境用的是 3.16.1）一条命令执行、自动拉依赖：

```bash
cabal run 29_ServantMinimalApi.hs      # :8080  GET /hello /users/:id /greet/:name
cabal run 30_ServantJsonCrud.hs        # :8081  User 资源的 CRUD
cabal run 31_WarpMiddleware.hs         # :8082  4 层中间件叠加
cabal run 32_PersistentSqliteCrud.hs   # 走完建表/插入/查/改/删 后退出
```

> 首次跑会拉 Hackage 包，可能耗时数十秒到10分钟；cabal 会把缓存放在 `~/.cabal/store/` 下，后续竬闪启动。

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

---

## 🏭 批次 5 — 行业应用场景实战（42-44）

前面 41 个 Demo 把 Haskell 的**语言能力**讲透了；本批次换一个视角：
**"真实工业界拿 Haskell 干什么？"** 每个文件都对应一个 Haskell 在业界**真的被使用**的领域，都是单文件、纯 `base`、`runghc` 直接跑。

### 42. `42_OptionPricingDSL.hs` — 金融衍生品定价 DSL
- **行业背景**：渣打银行 Mu、巴克莱 FPF、IOHK Cardano/Plutus 都是这套范式
- **核心套路**：`Contract` 用 **GADT** 表示成合约树，配多个解释器
  - 解释器 A：Black-Scholes **解析解**
  - 解释器 B：**蒙特卡洛** 数值解（手写 LCG 随机数，10 万路径）
- **亮点**：同一份 DSL 喂给两种定价模型做**交叉验证**（实跑偏差 <1%）
- **对应 roadmap 知识点**：11 号 Free Monad & DSL、15/27 号 GADT、38 号 GADT Interpreter

### 43. `43_TinyLangCompiler.hs` — 小语言全链路编译器
- **行业背景**：GHC、Pandoc、Elm、shellcheck、Hasura 都是 Haskell 写的编译器/静态分析器
- **核心套路**：一次性串起 **lexer → 手写递归下降 parser → Hindley-Milner 类型推导 → 闭包求值器**
- **支持特性**：`Int / Bool / let / \x -> body / if-then-else / + / ==`
- **实跑示例**：
  - `let twice = \f -> \x -> f (f x) in let inc = \n -> n + 1 in twice inc 10` → `12 : Int`
  - `let bad = 1 + true in bad` → 类型检查期报错 ✓
- **坑点提醒**：HM 推导里 `subst` 必须被 **force**，否则未被 `apply` 触达的错误条目在惰性下永远不会抛出（代码里用 `length s \`seq\`` 处理）
- **对应 roadmap 知识点**：10 号 Parser Combinators、17 号 λ 演算与 fix、38 号 GADT Interpreter

### 44. `44_AutoDiff.hs` — 前向模式自动微分
- **行业背景**：PyTorch / TensorFlow / JAX autograd 的底层思想，Haskell 只用 **20 行核心代码** 就能实现
- **核心套路**：定义 `D Double Double`（值, 导数）为"对偶数"，让它成为 `Num / Fractional / Floating` 实例，任意用 `+ * / sin exp` 写的普通数值代码**自动获得求导能力**，零运行时开销
- **实跑示例**：
  - `f(x) = x³ + 2x`，AD 结果 `f'(3) = 29` 与解析解完全一致
  - 梯度下降求 `(x-3)² + 1` 极小值，从 `x=-5` 迭代 15 步收敛到 `3.0`
- **对应 roadmap 知识点**：02 号 Type Class & Monad、37 号 Lazy Evaluation

---

> 批次 5 的设计原则和前 41 个 demo 一致：
> **单文件、纯 base、`runghc` 可跑、每段代码都能追溯到一个真实工业用途**。
> 如果你只能挑一个看，推荐 **44 号**——20 行核心代码展示 Haskell 类型类重载的威力；
> 如果你想"贯穿整份 roadmap"，看 **43 号**——它把 10/11/15/17/27/38 号的抽象全串成了一个能跑的小语言。

---

## 🧱 批次 6 — 空白领域补全（45-48）

批次 5 用 3 个 demo 把 Haskell 在金融/编译器/ML 三大经典场景做了缩影；本批次补齐另外 4 个**真实工业用途但前面 44 个 demo 都没正面覆盖**的方向：区块链账本、声明式并行、FRP、ETL 流水线。

### 45. `45_UTXOLedger.hs` — UTXO 区块链账本（Cardano/Plutus 范式）
- **行业背景**：Cardano / IOHK 的 Plutus、Bitcoin、Ergo 全部采用 UTXO 模型；与"账户余额"模型（Ethereum）相比，UTXO 易并行、易形式化验证
- **核心套路**：
  - `Tx` / `TxIn` / `TxOut` / `TxOutRef`：四个 ADT 描述完整交易语义
  - `validateTx`：检查"输入存在、签名匹配、收支平衡"三条不变量
  - `applyTx`：纯函数式状态转移（删除已花费 UTXO、加入新输出）
  - 手写 **FNV-1a 32 位哈希** + **Merkle 根** + **链式 prevHash 指针**：演示"哈希指针"为何能让账本不可篡改
- **实跑示例**：
  - 创世 → alice→bob 30 → bob→carol 30 → 三类反例（双花 / 错签名 / 不平衡）全部被拒
  - 篡改 tx2 的金额 → Merkle 根从 `4b436d14` 变为 `ac5b769b`，被 `verifyChain` 检测出
- **对应 roadmap 知识点**：07 号 ADT、22 号属性测试思维、42 号 DSL 范式

### 46. `46_ParallelStrategies.hs` — 声明式并行：par / pseq / Strategies
- **行业背景**：Haskell 是少有的"业务代码不变、只在末尾标几个 `par` 就能多核加速"的语言；金融定价、风险计算、科学模拟在 Haskell 端常用 Strategies 做并行
- **关键区分**：**并发 ≠ 并行**。06/19/33/34 号 demo 讲的是并发（多任务交错），本 demo 讲并行（单任务多核加速）
- **核心套路**（三种用法横向对比）：
  - **底层** `par a b` + `pseq a b`：手动放置火花点（spark）
  - **工业首选** `xs \`using\` parListChunk n rdeepseq`：业务/策略分离，避免 spark 数量爆炸
  - **显式分治** `runEval` + `rpar` + `rdeepseq`：写 map-reduce 时更自然
- **实跑示例**（4 核机器，cabal block 已开 `-threaded -with-rtsopts=-N`）：
  - `fib 38`：seq **0.408 s** → par(cutoff=20) **0.067 s**，**6.1× 加速**
  - 质数计数 [2..200000]：parList **0.082 s**（spark 太多）vs parListChunk 500 **0.008 s**（演示为什么必须 chunk）
  - map-reduce：seq 0.008 s → par/16 0.003 s，2.7× 加速
- **运行注意**：这是仓库**第一个**不能直接 `runghc` 的 demo——`Control.Parallel` 不在 `base` 里，必须 `cabal run`（首次会从 Hackage 自动拉 parallel/deepseq/time）
- **对应 roadmap 知识点**：37 号 Lazy Evaluation、19 号 Concurrency

### 47. `47_MinimalFRP.hs` — 古典 FRP 内核（Conal Elliott 1997 范式）
- **行业背景**：FRP 在游戏/动画/UI/机器人控制中应用广泛——Reflex（Web/GUI）、Yampa（机器人）、bacon.js、RxJS 都是这条路线的延展
- **核心套路**：
  - `Behavior a = Time -> a`（连续随时间变化的值）→ 天然就是 `Reader Time`，自动获得 `Functor / Applicative` 实例
  - `Event a = [(Time, a)]`（离散时间点上的值流）
  - 基础组合子：`fmap` / `filterE` / `mergeE` / **`accumE`**（带状态推进）
  - 互操作：**`stepper`**（事件流 + 初值 → Behavior）、**`snapshot`**（事件触发时拍 Behavior 一张照）
  - 高阶 FRP：**`switchB`**（Behavior 在事件触发时切换为另一条 Behavior）
- **实跑示例**：
  - 累积计数器：8 个按键事件流过 `accumE`，输出每事件后的 count（含 reset）
  - 速度→位置：`MoveLeft/Right/Stop` 事件流 → `stepper` 提升为 velocity Behavior → 数值积分得到位置轨迹
  - switchB：t<1 用 `sin t`，t≥1 切到 `cos t + 10`
- **对应 roadmap 知识点**：02 号 Functor/Applicative、05 号 Reader、23 号 Comonad（Behavior 也可看作 Stream comonad）

### 48. `48_CsvToJsonETL.hs` — 完整 CSV→JSON ETL 流水线
- **行业背景**：ETL 是数据工程最常见的工作模式；Haskell 的纯函数让 transform 阶段天然可组合、易测试。工业上用 cassava + aeson；本 demo 用纯 base 实现一份等价物
- **核心套路**（端到端）：
  - **手写 CSV parser**：支持引号包裹、`""` 转义、CRLF；解析失败时报告"row N col M"
  - **类型推导**：扫一遍数据，每列推出最窄类型（Int / Double / Bool / String），用 `joinTy` 求"最宽公共类型"
  - **transform 管道**：`filterAdults` → `addTag` → `groupByCity`，全部纯函数
  - **手写 JSON encoder**：完整字符串转义（含 `\u00XX`），含 pretty-print
- **实跑示例**：
  - 6 行 CSV（含逗号字段 `"Carol, the brave"`）→ 推出 `name::TyString, age::TyInt, city::TyString, active::TyBool`
  - 过滤 `age>=18` 后剩 4 行；`groupByCity` 输出 `Beijing→2, Shanghai→1, Shenzhen→1`
  - 最终输出合法 JSON 数组（active 字段是真 `true/false` 而非字符串）
  - 故意制造未闭合引号的破损 CSV → 报告 `parse error at row 3 col 1: unclosed quoted field`
- **对应 roadmap 知识点**：10 号 Parser Combinators、16 号 Streaming Pipeline、07 号 ADT

---

> 批次 6 的"批次 5 风格"延续：每份都对应一个**真实工业领域**，原则上单文件可跑。
> 唯一例外：**46 号必须用 `cabal run`**，因为 `parallel` 包不在 base 里。
> 如果只挑一个看，推荐 **45 号 UTXO**——300 行内呈现整个区块链不可篡改原理；
> 如果想看 Haskell **真正的杀手锏**，看 **46 号 par/Strategies**——业务代码几乎不变，只加一个 `using parListChunk`，就拿到 6 倍加速。

---

## 🔗 See also — 跨语言 FP 学习路线

Haskell 是 **类型驱动** 的 FP，但同一套抽象（Functor / Monad / Resource / Effect / Lens / STM / Free）在别的语言里也都能找到映射，推荐配合阅读：

| 语言 | 路线图 | 这些 Haskell 概念在那儿叫什么 |
|---|---|---|
| 🟣 Scala | [`../scala/SCALA_FP_ROADMAP.md`](../scala/SCALA_FP_ROADMAP.md) | `Functor/Monad` → `cats.Functor/Monad`；`ExceptT` → `EitherT`；`ResourceT` → `cats-effect Resource`；`STM` → `cats-stm`；`Free` → `cats.free.Free` |
| 💧 Elixir | [`../elixir/ELIXIR_FP_ROADMAP.md`](../elixir/ELIXIR_FP_ROADMAP.md) | `do`-notation → `with` 表达式；`IORef` → `Agent`；`forkIO` → `Task`；`MVar/STM` → 进程邮箱 + `GenServer` |
| 🦀 Rust | [`../rust/`](../rust/) | `Maybe/Either` → `Option/Result`；`class` typeclass → `trait`；`Monad.bind` → `?` 运算符；`async` Haskell → `async fn` + `tokio` |
| ⚡ Erlang | [`../erlang/`](../erlang/) | `forkIO` + `Chan` → `spawn` + `receive`；`STM` → `mnesia` 事务；pattern match 两家都一流；typeclass 则完全没有 |

横向速查表：[`../LANGUAGE_COMPARISON.md`](../LANGUAGE_COMPARISON.md) —— 把 Haskell 12 张抽象表一一映射到 Scala / Rust / Erlang / Elixir。

> **对照建议**：读完 `02_TypeClassAndMonad` → 去看 Scala `07_TypeClassStyle` 和 `14_FunctorApplicativeMonad`；
> 读完 `05_StateAndReader` → 对照 Scala `15_ReaderConfig` / `16_StateCalculator`；
> 读完 `06_ConcurrencySTM` → 对照 Rust 13 `Send/Sync/内部可变性` + Scala `36_CatsEffectDeferredRef`；
> 读完 `11_FreeMonadsAndDSL` → 对照 Scala `22_TaglessUserService` / `30_TaglessCatsEffect`；
> 读完 `21_EffectSystemPatterns` → 对照 Scala cats-effect 全系（26~101）+ Elixir 13 Telemetry。
