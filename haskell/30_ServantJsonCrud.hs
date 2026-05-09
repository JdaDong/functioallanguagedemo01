{- cabal:
build-depends:
  , base              ^>= 4.18 || ^>= 4.19 || ^>= 4.20 || ^>= 4.21
  , servant
  , servant-server
  , warp
  , aeson
  , text
  , stm
  , containers
-}

{-# LANGUAGE DataKinds         #-}
{-# LANGUAGE TypeOperators     #-}
{-# LANGUAGE DeriveGeneric     #-}
{-# LANGUAGE OverloadedStrings #-}

-- ============================================================
-- Demo 30: Servant JSON CRUD
--
-- 29 号只做了 GET；本 Demo 把一个 User 资源的 4 个 CRUD 端点补齐：
--
--   GET    /users          列全部
--   POST   /users          新增（body 为 CreateUser JSON）
--   GET    /users/:id      查单个
--   DELETE /users/:id      删除
--
-- 关键点：
--   * ReqBody '[JSON] CreateUser    —— 类型级别声明请求体
--   * FromJSON/ToJSON (Generic)     —— 编解码一行完成
--   * TVar + STM                    —— 最小可用的线程安全内存仓储
--
-- 跑法：
--   cabal run 30_ServantJsonCrud.hs
--   curl -XPOST -H 'Content-Type: application/json' \
--        -d '{"name":"Alice"}' http://localhost:8081/users
--   curl http://localhost:8081/users
--   curl http://localhost:8081/users/1
--   curl -XDELETE http://localhost:8081/users/1
--
-- 对应 Scala: 32_Http4sJsonApi.scala
-- ============================================================

module Main where

import Control.Concurrent.STM
import Data.Aeson             (FromJSON, ToJSON)
import qualified Data.Map.Strict as M
import Data.Text              (Text)
import GHC.Generics           (Generic)
import Network.Wai.Handler.Warp (run)
import Servant

-- ============================================================
-- Part 1: 数据模型
-- ============================================================

data User = User
  { userId   :: Int
  , userName :: Text
  } deriving (Show, Generic)

instance ToJSON   User
instance FromJSON User

-- 创建请求的 payload：不含 id（由服务端分配）
newtype CreateUser = CreateUser { name :: Text }
  deriving (Show, Generic)

instance ToJSON   CreateUser
instance FromJSON CreateUser

-- ============================================================
-- Part 2: 存储（STM 内存仓储）
-- ============================================================
--
-- 为什么用 STM：
--   * atomically 保证"分配 id + 插入"是原子的
--   * 比 MVar 可组合（见 06_ConcurrencySTM.hs 的 retry/orElse）
--   * 比 IORef 并发安全

data Store = Store
  { storeUsers  :: TVar (M.Map Int User)
  , storeNextId :: TVar Int
  }

newStore :: IO Store
newStore = Store <$> newTVarIO M.empty <*> newTVarIO 1

addUser :: Store -> CreateUser -> STM User
addUser s cu = do
  nid <- readTVar (storeNextId s)
  let u = User { userId = nid, userName = name cu }
  modifyTVar' (storeUsers s) (M.insert nid u)
  writeTVar   (storeNextId s) (nid + 1)
  pure u

listUsers :: Store -> STM [User]
listUsers s = M.elems <$> readTVar (storeUsers s)

findUser :: Store -> Int -> STM (Maybe User)
findUser s i = M.lookup i <$> readTVar (storeUsers s)

deleteUser :: Store -> Int -> STM Bool
deleteUser s i = do
  m <- readTVar (storeUsers s)
  case M.lookup i m of
    Nothing -> pure False
    Just _  -> do
      writeTVar (storeUsers s) (M.delete i m)
      pure True

-- ============================================================
-- Part 3: API 类型
-- ============================================================
--
-- 同一个资源的 4 个操作写在一个类型里，编译器会确保
-- handlers 的签名、顺序、数量都和这里一一对应。

type UserAPI =
       "users" :> Get '[JSON] [User]
  :<|> "users" :> ReqBody '[JSON] CreateUser :> PostCreated '[JSON] User
  :<|> "users" :> Capture "id" Int :> Get '[JSON] User
  :<|> "users" :> Capture "id" Int :> DeleteNoContent

-- ============================================================
-- Part 4: Handlers
-- ============================================================

listH :: Store -> Handler [User]
listH s = liftIO $ atomically (listUsers s)

createH :: Store -> CreateUser -> Handler User
createH s cu = liftIO $ atomically (addUser s cu)

getH :: Store -> Int -> Handler User
getH s i = do
  mu <- liftIO $ atomically (findUser s i)
  case mu of
    Just u  -> pure u
    Nothing -> throwError err404 { errBody = "user not found" }

deleteH :: Store -> Int -> Handler NoContent
deleteH s i = do
  ok <- liftIO $ atomically (deleteUser s i)
  if ok
    then pure NoContent
    else throwError err404 { errBody = "user not found" }

-- Server 需要闭包到 Store，用 server s 构造
server :: Store -> Server UserAPI
server s = listH s :<|> createH s :<|> getH s :<|> deleteH s

-- ============================================================
-- Part 5: 启动
-- ============================================================

apiProxy :: Proxy UserAPI
apiProxy = Proxy

app :: Store -> Application
app s = serve apiProxy (server s)

main :: IO ()
main = do
  store <- newStore
  putStrLn "=========================================="
  putStrLn "Demo 30: Servant JSON CRUD"
  putStrLn "listening on http://localhost:8081"
  putStrLn "  GET    /users"
  putStrLn "  POST   /users     {\"name\":\"Alice\"}"
  putStrLn "  GET    /users/:id"
  putStrLn "  DELETE /users/:id"
  putStrLn "=========================================="
  run 8081 (app store)
