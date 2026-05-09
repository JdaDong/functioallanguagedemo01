{- cabal:
build-depends:
  , base           ^>= 4.18 || ^>= 4.19 || ^>= 4.20 || ^>= 4.21
  , warp
  , wai
  , wai-extra
  , http-types
  , bytestring
  , text
  , time
-}

{-# LANGUAGE OverloadedStrings #-}

-- ============================================================
-- Demo 31: Warp / WAI Middleware
--
-- 29/30 号用 servant 做"类型驱动"的 Web，本 Demo 回到更底层：
-- 直接用 WAI + Warp 写一个 Application，然后演示
--   **Middleware = Application -> Application**
-- 这个核心抽象。
--
-- 本 Demo 组合 4 个中间件：
--   1. logStdoutDev    —— wai-extra 提供的请求日志
--   2. addServerHeader —— 自写，给所有响应加 Server 头
--   3. corsPermissive  —— 自写，最简单的开放式 CORS
--   4. timingHeader    —— 自写，响应加 X-Response-Time-Ms
--
-- 核心洞察：中间件只是"在 Application 外面再包一层的纯函数"，
-- 组合就是函数组合 (.)，顺序就是调用顺序。
--
-- 跑法：
--   cabal run 31_WarpMiddleware.hs
--   curl -i http://localhost:8082/
--   curl -i -H 'Origin: http://example.com' http://localhost:8082/
--
-- 对应 Scala: 29_Http4sRoutes.scala / 48_Http4sAuthMiddleware.scala
-- ============================================================

module Main where

import qualified Data.ByteString.Char8 as BS
import Data.Time.Clock      (diffUTCTime, getCurrentTime)
import Network.HTTP.Types   (status200)
import Network.Wai          (Application, Middleware, Response, mapResponseHeaders, responseLBS)
import Network.Wai.Handler.Warp  (run)
import Network.Wai.Middleware.RequestLogger (logStdoutDev)

-- ============================================================
-- Part 1: 基础 Application
-- ============================================================

baseApp :: Application
baseApp _req respond = respond $
  responseLBS status200
    [("Content-Type", "text/plain; charset=utf-8")]
    "hello from bare WAI\n"

-- ============================================================
-- Part 2: 自写中间件 —— addServerHeader
-- ============================================================
--
-- Middleware 的类型就是 Application -> Application，
-- 这里我们"透传到下游 app，拿到 Response，往 header 里塞一行"。

addServerHeader :: Middleware
addServerHeader innerApp req respond =
  innerApp req $ \resp ->
    respond (mapResponseHeaders (("Server", "demo-31") :) resp)

-- ============================================================
-- Part 3: 自写中间件 —— corsPermissive
-- ============================================================
--
-- 最简单粗暴的 CORS：所有来源、所有方法都放行。
-- 生产环境请收紧 Access-Control-Allow-Origin。

corsPermissive :: Middleware
corsPermissive innerApp req respond =
  innerApp req $ \resp ->
    respond $ mapResponseHeaders
      ( \hs ->
          ("Access-Control-Allow-Origin",  "*") :
          ("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS") :
          ("Access-Control-Allow-Headers", "Content-Type, Authorization") :
          hs
      )
      resp

-- ============================================================
-- Part 4: 自写中间件 —— timingHeader
-- ============================================================
--
-- 包下游 app，测量处理耗时，把毫秒写回响应头。
-- 关键点：下游 app 是 CPS 风格，所以我们得在它的 respond 回调里打点。

timingHeader :: Middleware
timingHeader innerApp req respond = do
  t0 <- getCurrentTime
  innerApp req $ \resp -> do
    t1 <- getCurrentTime
    let ms = round ((realToFrac (diffUTCTime t1 t0) :: Double) * 1000) :: Int
        hdr = ("X-Response-Time-Ms", BS.pack (show ms))
    respond (mapResponseHeaders (hdr :) resp)

-- ============================================================
-- Part 5: 组合
-- ============================================================
--
-- Middleware 就是 Application -> Application，组合它们用普通函数组合 (.) 即可。
--   finalApp = mw1 . mw2 . mw3 $ baseApp
-- 调用时的执行顺序是：mw1(mw2(mw3(baseApp)))
-- 也就是说：最外层的 mw1 先拦到请求、最后加工响应。

finalApp :: Application
finalApp =
    logStdoutDev     -- 最外层：每次请求都打日志
  . addServerHeader  -- 加 Server 头
  . corsPermissive   -- 加 CORS 头
  . timingHeader     -- 最内层（最先包裹 baseApp）：打耗时
  $ baseApp

-- ============================================================
-- Main
-- ============================================================

main :: IO ()
main = do
  putStrLn "=========================================="
  putStrLn "Demo 31: Warp + WAI Middleware"
  putStrLn "listening on http://localhost:8082"
  putStrLn ""
  putStrLn "试着 curl -i 看响应头："
  putStrLn "  Server:              demo-31        (addServerHeader)"
  putStrLn "  Access-Control-*:    *              (corsPermissive)"
  putStrLn "  X-Response-Time-Ms:  <小整数>       (timingHeader)"
  putStrLn "stdout 应能看到 logStdoutDev 的请求日志行"
  putStrLn "=========================================="
  run 8082 finalApp
