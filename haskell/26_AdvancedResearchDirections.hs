-- ============================================================
-- Demo 26: 收官 —— FP 研究方向地图 + 前沿工具箱
--
-- 前 25 个 Demo 走完了"工程师日常能用上的 FP 全景"；
-- 这一站不写新实现，而是把 Haskell / FP 研究里仍然活跃的
-- 几条"再往上走"的方向画成地图，方便读者挑自己感兴趣的深入。
--
-- 本 Demo 不依赖任何第三方库，只打印索引。运行:
--   runhaskell 26_AdvancedResearchDirections.hs
-- ============================================================

module Main where

import Data.List (intercalate)

-- 一个小工具：带缩进地打印 "主题 + 要点列表"
section :: String -> [String] -> IO ()
section title bullets = do
  putStrLn $ "--- " ++ title
  mapM_ (\b -> putStrLn $ "    * " ++ b) bullets
  putStrLn ""

bar :: IO ()
bar = putStrLn (replicate 60 '=')

main :: IO ()
main = do
  bar
  putStrLn "Demo 26: FP 研究方向地图 + 前沿工具箱 (Haskell 系列收官)"
  bar
  putStrLn ""

  section "A. 类型系统进阶"
    [ "Liquid Haskell —— 用 refinement type 直接在类型里写规格"
    , "Linear Haskell —— 线性类型, 资源恰好用一次 (对标 Rust 所有权)"
    , "Dependent Haskell —— 未来方向, 目前可用 singletons / DataKinds 近似"
    , "Row polymorphism / extensible records (Idris 2, PureScript)"
    ]

  section "B. Effect Systems 流派"
    [ "MTL (transformers)          —— 经典, Demo 08 已演示"
    , "Tagless Final               —— 对标 Scala cats-effect, Demo 21"
    , "Free / Freer / Eff          —— 把程序当 AST, Demo 11"
    , "Polysemy / fused-effects    —— 零成本 effect handler, 工程主流"
    , "Effectful / cleff           —— 2023 后新一代, 性能对齐 IO"
    , "Koka / Eff / OCaml 5        —— 真·algebraic effects 原生语言"
    ]

  section "C. 并发 & 异步"
    [ "STM + async (Haskell 内置)  —— Demo 06, 19"
    , "unliftio / resourcet        —— 在 MTL 下安全处理资源与异常"
    , "streaming / pipes / conduit —— Demo 16 是这一家的小型版"
    , "Dunai / FRP (Yampa, reflex) —— 响应式 / 游戏 / UI"
    ]

  section "D. 并行计算"
    [ "par/pseq / Strategies       —— 声明式并行"
    , "accelerate / repa / massiv  —— 数组并行 + GPU"
    , "Cloud Haskell               —— Erlang 式分布式 actor in Haskell"
    ]

  section "E. 证明 & 形式化方法"
    [ "QuickCheck / Hedgehog       —— Demo 12"
    , "Liquid Haskell              —— 在类型层写不变量并自动证明"
    , "Coq / Agda / Lean / Idris   —— 真·证明辅助器, 可机验"
    , "Property-based state machine, smallcheck, inspection-testing"
    ]

  section "F. 类型层技术 & 元编程"
    [ "GHC.Generics + deriving via —— Demo 20"
    , "Template Haskell            —— 编译期代码生成 (persistent, aeson 都用)"
    , "Type-level programming      —— Demo 15, 25"
    , "row-types, generic-lens     —— 让记录真正通用"
    ]

  section "G. 抽象代数与范畴论"
    [ "Functor / Applicative / Monad (01-08)"
    , "Traversable / Foldable (14)"
    , "Arrow / Profunctor (13)"
    , "Comonad (23)"
    , "Kan extensions, Yoneda, Ends / Coends —— 下一层抽象"
    , "Recursion schemes (24) —— 把递归写法本身抽象掉"
    ]

  section "H. 跨语言对标速查"
    [ fmtRow [ "概念",          "Haskell",                "Scala",                   "Rust"             ]
    , fmtRow [ "纯函数",        "纯 + IO",                "def / val",               "fn, 不可变默认"   ]
    , fmtRow [ "Functor",       "fmap",                   "map",                     "map (Iterator)"   ]
    , fmtRow [ "Monad",         ">>=",                    "flatMap / for",           "? (对 Result)"    ]
    , fmtRow [ "Effect System", "mtl / polysemy",         "cats-effect",             "tokio + own-build"]
    , fmtRow [ "不可变数据",    "默认",                   "val / immutable.Map",     "&T / 所有权"      ]
    , fmtRow [ "共享可变",      "IORef / STM",            "Ref / AtomicCell",        "Arc<Mutex>"       ]
    , fmtRow [ "并发",          "forkIO / async",         "IO.start / Fiber",        "tokio::spawn"     ]
    , fmtRow [ "流处理",        "conduit / streaming",    "fs2",                     "tokio-stream/futures"]
    , fmtRow [ "Actor",         "Cloud Haskell",          "Akka",                    "Actix / ractor"   ]
    , fmtRow [ "属性测试",      "QuickCheck / Hedgehog",  "ScalaCheck",              "proptest / quickcheck"]
    , fmtRow [ "Typestate",     "Phantom / GADT",         "sealed trait + FSM",      "PhantomData"      ]
    ]

  bar
  putStrLn "Haskell 系列 26 号 Demo 结束. 恭喜你走完整个 FP 知识圈!"
  bar

-- -- 辅助：把 N 列拼成一行，每列固定宽度
fmtRow :: [String] -> String
fmtRow = intercalate " | " . map (pad 20)
  where
    pad n s = s ++ replicate (max 0 (n - length s)) ' '
