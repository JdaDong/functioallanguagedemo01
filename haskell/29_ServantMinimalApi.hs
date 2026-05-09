{- cabal:
build-depends:
  , base              ^>= 4.18 || ^>= 4.19 || ^>= 4.20 || ^>= 4.21
  , servant
  , servant-server
  , warp
  , aeson
  , text
-}

{-# LANGUAGE DataKinds         #-}
{-# LANGUAGE TypeOperators     #-}
{-# LANGUAGE DeriveGeneric     #-}
{-# LANGUAGE OverloadedStrings #-}

-- ============================================================
-- Demo 29: Servant 最小 REST API
--
-- 本 Demo 展示 Haskell 最有特色的 Web 框架 servant：
-- 用 *类型* 声明路由，用 server 实现业务，编译器帮你保证
-- "路由与 handler 签名完全对得上"。
--
-- 设计思路：
--   1. 先把 API 用类型签名写出来          (type API = ...)
--   2. 为每条路由写一个纯 handler          (helloH, userH, ...)
--   3. 用 server :: Server API 把它们组合起来
--   4. Warp 跑起来就是一个 HTTP 服务
--
-- 跑法（会自动拉依赖）：
--   cabal run 29_ServantMinimalApi.hs
--   curl http://localhost:8080/hello
--   curl http://localhost:8080/users/42
--   curl http://localhost:8080/greet/Alice
--
-- 对应 Scala: 21_Http4sMiniService.scala
-- ============================================================

module Main where

import Data.Aeson         (ToJSON)
import Data.Text          (Text)
import qualified Data.Text as T
import GHC.Generics       (Generic)
import Network.Wai.Handler.Warp (run)
import Servant

-- ============================================================
-- Part 1: 数据模型
-- ============================================================

data User = User
  { userId   :: Int
  , userName :: Text
  } deriving (Show, Generic)

instance ToJSON User

-- ============================================================
-- Part 2: API 类型
-- ============================================================
--
-- 读法：
--   "hello"                          字面量路径段
--   :> Get '[PlainText] Text         返回类型 + 响应内容协商
--   "users" :> Capture "id" Int      从 URL 捕获 Int
--   :<|>                              路由组合子（类型级别的 or）
--
-- 整条 API 的类型 *就是* 它的文档。

type API =
       "hello" :> Get '[PlainText] Text                                -- GET /hello
  :<|> "users" :> Capture "id" Int :> Get '[JSON] User                  -- GET /users/:id
  :<|> "greet" :> Capture "name" Text :> Get '[PlainText] Text          -- GET /greet/:name

-- ============================================================
-- Part 3: Handlers
-- ============================================================
--
-- 每个 handler 的类型必须和 API 类型里对应那一段匹配。
-- 如果 API 类型说"返回 User"，而 handler 返回 Text，编译就失败。
-- 这就是 servant 最大的卖点：文档即类型，类型即检查。

helloH :: Handler Text
helloH = pure "hello from servant"

userH :: Int -> Handler User
userH uid
  | uid <= 0  = throwError err404 { errBody = "user not found" }
  | otherwise = pure User { userId = uid, userName = "user-" <> T.pack (show uid) }

greetH :: Text -> Handler Text
greetH name = pure $ "Hello, " <> name <> "!"

-- 路由组合，顺序要和 API 类型完全一致
server :: Server API
server = helloH :<|> userH :<|> greetH

-- ============================================================
-- Part 4: 启动
-- ============================================================

apiProxy :: Proxy API
apiProxy = Proxy

app :: Application
app = serve apiProxy server

main :: IO ()
main = do
  putStrLn "=========================================="
  putStrLn "Demo 29: Servant Minimal API"
  putStrLn "listening on http://localhost:8080"
  putStrLn "  GET /hello"
  putStrLn "  GET /users/:id   (e.g. /users/42)"
  putStrLn "  GET /greet/:name (e.g. /greet/Alice)"
  putStrLn "Ctrl-C to stop"
  putStrLn "=========================================="
  run 8080 app
