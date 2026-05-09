# Haskell 好玩的 SDK 和开源项目盘点

> 本文档整理 Haskell 社区里**真正"好玩"或"工业上真在用"**的 25 个项目，对应本仓库 48 个 demo（[`HASKELL_FP_ROADMAP.md`](./HASKELL_FP_ROADMAP.md)）打通"学完之后玩什么"的衔接。

Haskell 社区虽然小众，但精品密度极高，很多项目是"别的语言根本没有"或"其他语言要花十倍代码才能做到"。下面按 6 类组织，每个项目都标了 **🌟 趣味点** 和 **🔧 上手难度**。

---

## 🎮 一、游戏 / 创意编程（最直接的"好玩"）

### 1. **Tidal Cycles** — 用 Haskell DSL 现场打音乐
- **GitHub**: `tidalcycles/Tidal`
- 🌟 **趣味点**：在酒吧/音乐节上现场敲 Haskell 代码做电子乐（algorave 文化的代表），全球有几千个 live coder 在用
- 一行代码就能玩：
  ```haskell
  d1 $ sound "bd sn bd cp" # speed "1 0.5 2 1"
  ```
- 🔧 难度：⭐ 极易，装好就能玩，不需要懂 Haskell

### 2. **Helm** — 函数式 2D 游戏引擎（Elm 风格）
- **GitHub**: `z0w0/helm`
- 🌟 用 FRP 写游戏，状态管理纯函数，无 mutable state
- 适合写"贪吃蛇""俄罗斯方块"这种小游戏当 demo
- 🔗 配合本仓库 [`47_MinimalFRP.hs`](./47_MinimalFRP.hs) 食用

### 3. **Apecs** — 高性能 ECS 游戏框架
- **GitHub**: `jonascarpay/apecs`
- 🌟 Haskell 写出和 Rust Bevy / Unity DOTS 一个量级性能的 ECS，类型安全的实体组件系统
- 配套有 Apecs-Physics（2D 物理引擎绑定）

### 4. **Monomer** — 跨平台桌面 GUI（类 Flutter 思路）
- **GitHub**: `fjvallarino/monomer`
- 🌟 纯 Haskell 写桌面 app，声明式 UI + reactive state，跑得动 macOS/Linux/Windows
- 比 GTK 绑定那些"老派"GUI 库现代得多

---

## 🌐 二、Web / 后端（生产级别能用）

### 5. **Servant** — 类型即 API 文档
- **GitHub**: `haskell-servant/servant`
- 🌟 **真·震撼**：API 路由是类型，编译期保证客户端/服务端类型一致；改一个端点，所有用到它的地方编译报错
- 一份 API 类型自动生成：服务端 + 客户端 + Swagger 文档 + JS/Python 客户端代码
  ```haskell
  type API = "users" :> Capture "id" Int :> Get '[JSON] User
        :<|> "users" :> ReqBody '[JSON] User :> Post '[JSON] User
  ```
- 🔧 难度：⭐⭐⭐ 需要懂 type-level programming
- 🔗 配合本仓库 [`29_ServantMinimalApi.hs`](./29_ServantMinimalApi.hs)、[`30_ServantJsonCrud.hs`](./30_ServantJsonCrud.hs)

### 6. **Yesod** — "Rails of Haskell"
- **GitHub**: `yesodweb/yesod`
- 🌟 模板引擎在编译期检查 HTML/CSS/JS/SQL，前端写错变量编译就挂
- Hamlet (HTML) / Lucius (CSS) / Julius (JS) 三件套

### 7. **IHP** — 现代化 Web 框架（号称比 Rails 还快）
- **GitHub**: `digitallyinduced/ihp`
- 🌟 装好就能跑、自带 UI 数据库管理工具、零样板的 CRUD
- 是这两年 Haskell 圈最积极推广的"普通人能用"的框架

### 8. **PostgREST** — 把数据库一秒变 REST API
- **GitHub**: `PostgREST/postgrest`
- 🌟 **不是 Haskell 库，是 Haskell 写的现成工具**：指给它一个 Postgres，立刻得到一套全功能 REST API（含 OpenAPI 文档、行级权限、JWT）
- GitHub 23k+ stars，业界真在用

---

## 🔬 三、编程语言 / 编译器（Haskell 主场）

### 9. **Pandoc** — 万能文档转换器
- **GitHub**: `jgm/pandoc`
- 🌟 几乎任何文档格式互转（Markdown/LaTeX/Word/EPUB/HTML/RST/...），全世界程序员每天都在用
- John MacFarlane 一个人写了大半，是 Haskell"杀手级应用"的代名词

### 10. **ShellCheck** — Bash 静态分析神器
- **GitHub**: `koalaman/shellcheck`
- 🌟 你的 `.sh` 脚本里有什么坑它都看得到，VSCode/vim 集成
- 36k+ stars，几乎所有写 bash 的人都在用，**没人意识到这是 Haskell 写的**

### 11. **Elm Compiler** — Elm 语言本身
- **GitHub**: `elm/compiler`
- 🌟 Elm（号称"前端永不报错"）的编译器是 Haskell 写的
- 看完 [`43_TinyLangCompiler.hs`](./43_TinyLangCompiler.hs) 后再读它，会发现很多套路一致

### 12. **Idris 2** — 依值类型语言的旗舰
- **GitHub**: `idris-lang/Idris2`
- 🌟 把 [`25_DependentTypesAndSingletons.hs`](./25_DependentTypesAndSingletons.hs) 的"依值类型直觉"做成完整语言；可以把"列表长度""矩阵维度"放进类型，编译期消除 bug

### 13. **Agda** — 定理证明助手
- **GitHub**: `agda/agda`
- 🌟 用类型系统当数学证明工具；写程序 = 证定理（Curry-Howard 同构的极致）

### 14. **GHC 自己** — Glasgow Haskell Compiler
- **GitHub**: `ghc/ghc`
- 🌟 一个用 Haskell 写的 Haskell 编译器，最 meta 的项目
- 想啃源码请准备 6 个月

---

## 🪙 四、区块链 / 金融（Haskell 闪光）

### 15. **Cardano / Plutus** — 用 Haskell 写智能合约
- **GitHub**: `IntersectMBO/plutus`
- 🌟 整个 Cardano 公链用 Haskell 写；智能合约语言 Plutus 是 Haskell 子集
- 看完 [`45_UTXOLedger.hs`](./45_UTXOLedger.hs) 你已经摸到它的内核了

### 16. **Hledger** — 纯函数式记账
- **GitHub**: `simonmichael/hledger`
- 🌟 命令行版 GnuCash，纯文本账本（类似 ledger-cli），自己的财务可以管起来
- 个人用很爽，是少有的"Haskell 写给普通人用"的工具

### 17. **dhall-lang** — 可编程的配置语言
- **GitHub**: `dhall-lang/dhall-haskell`
- 🌟 替代 YAML/JSON 的配置语言：有类型、有函数、可导入、不会卡死（保证终止）
- Spago（PureScript 包管理器）、好几个 Kubernetes 工具用它做配置

---

## 🤖 五、AI / 数据处理（小众但好玩）

### 18. **Hasktorch** — Haskell 版 PyTorch
- **GitHub**: `hasktorch/hasktorch`
- 🌟 LibTorch 的 Haskell 绑定，类型安全的张量（编译期检查矩阵维度匹配）
- 看完 [`44_AutoDiff.hs`](./44_AutoDiff.hs) 后想搞真·神经网络就用它

### 19. **Hakyll** — 静态网站生成器（Jekyll 的 Haskell 版）
- **GitHub**: `jaspervdj/hakyll`
- 🌟 用 Haskell 配置博客；很多 Haskell 大佬的个人站都用它
- 比 Jekyll/Hugo 灵活得多（你写代码不写配置）

### 20. **Lambdabot** — IRC 机器人
- **GitHub**: `lambdabot/lambdabot`
- 🌟 Haskell IRC/Discord 上的机器人，能在频道里**实时跑 Haskell 代码片段**、查 Hoogle、跑类型推导
- `@type map` → `@type map :: (a -> b) -> [a] -> [b]`

---

## 🛠 六、底层 / 运维 / DevOps

### 21. **Niv / Nix** — 函数式包管理 / 操作系统
- **GitHub**: `nmattia/niv`、`NixOS/nix`
- 🌟 Nix 不全是 Haskell 写的（核心是 C++），但 NixOps、niv 等周边工具是
- 配置即代码、可复现的环境，硬核运维必备

### 22. **xmonad** — 平铺式窗口管理器
- **GitHub**: `xmonad/xmonad`
- 🌟 Linux 上最有名的平铺 WM 之一，配置文件就是 Haskell 代码
- 用它的 Linux 用户写一份 `xmonad.hs` 当成名片

### 23. **Darcs** — 比 Git 还函数式的版本控制
- **GitHub**: `darcs/darcs`
- 🌟 patch 是一等公民、可以代数运算的 VCS，理论很优雅但生态没赢过 Git
- 有趣的"另一种历史路径"

### 24. **Wasp** — Web app DSL
- **GitHub**: `wasp-lang/wasp`
- 🌟 用 DSL 写"Web app 是什么"，编译器（Haskell 写的）生成全栈 React+Node 代码

### 25. **Koka** — 微软研究院的 effect-typed 语言
- **GitHub**: `koka-lang/koka`
- 🌟 Daan Leijen（Parsec 作者）做的；可以把"会不会抛异常""会不会做 IO"放进类型签名
- 学完 [`21_EffectSystemPatterns.hs`](./21_EffectSystemPatterns.hs) 后看它会很有共鸣

---

## 🎯 推荐学习路径（对照本仓库 48 个 demo）

| 已掌握 | 推荐玩什么 |
|---|---|
| 完成 1-22 号（基础 + 工程） | **Servant** 或 **Yesod** 写个真 Web 服务；**Pandoc** 读源码 |
| 完成 23-26 号（研究方向） | **Idris 2** / **Agda** 浅尝；**Koka** 看 effect system |
| 完成 42-44 号（行业应用） | **Plutus** 智能合约；**Hasktorch** 写神经网络 |
| 完成 45-48 号（空白补全） | **Tidal Cycles** 现场打碟；**Hledger** 管个人财务 |

---

## 💡 一句话总结

> **Haskell 的"好玩"不在于工具本身，而在于"原来这种写法真的能跑"的颠覆感。**

如果只挑 3 个最能体现 Haskell 独特魅力的：

1. **Tidal Cycles** — 用 Haskell 现场打碟，能直接和别的语言用户炫
2. **Servant** — "类型即 API"，看完会重新思考"接口契约"该怎么表达
3. **Pandoc / ShellCheck** — 你每天都在用 Haskell 写的东西却不知道

---

## 🔗 相关文档

- 学习路线：[`HASKELL_FP_ROADMAP.md`](./HASKELL_FP_ROADMAP.md)（48 个 demo 详解）
- 跨语言对照：[`../LANGUAGE_COMPARISON.md`](../LANGUAGE_COMPARISON.md)
- 商业场景画像：[`../language_comparison_2.md`](../language_comparison_2.md)
