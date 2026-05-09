{- cabal:
build-depends:
  , base                 ^>= 4.18 || ^>= 4.19 || ^>= 4.20 || ^>= 4.21
  , persistent
  , persistent-sqlite
  , persistent-template
  , monad-logger
  , resourcet
  , transformers
  , text
-}

{-# LANGUAGE DataKinds                  #-}
{-# LANGUAGE DerivingStrategies         #-}
{-# LANGUAGE FlexibleContexts           #-}
{-# LANGUAGE FlexibleInstances          #-}
{-# LANGUAGE GADTs                      #-}
{-# LANGUAGE GeneralizedNewtypeDeriving #-}
{-# LANGUAGE MultiParamTypeClasses      #-}
{-# LANGUAGE OverloadedStrings          #-}
{-# LANGUAGE QuasiQuotes                #-}
{-# LANGUAGE StandaloneDeriving         #-}
{-# LANGUAGE TemplateHaskell            #-}
{-# LANGUAGE TypeFamilies               #-}
{-# LANGUAGE UndecidableInstances       #-}

-- ============================================================
-- Demo 32: Persistent + SQLite CRUD
--
-- 本 Demo 展示 Haskell 工业级 ORM —— `persistent` 的最小工作链路：
--   1. 用 persistentLowerCase quasi-quote 声明 Book 实体
--   2. TH 自动生成 EntityField、Key、FromJSON/ToJSON 等一堆样板
--   3. 用 runSqlite 跑内存 DB (":memory:") 演示完整 CRUD
--      insert / selectList / get / update / delete
--
-- 关键点：
--   * schema 即 Haskell 类型 —— 编译器帮你查字段拼写错误
--   * runMigration 自动建表
--   * SqlPersistT m 是 mtl 风格的 effect，随便和 IO/STM 组合
--
-- 跑法：
--   cabal run 32_PersistentSqliteCrud.hs
--
-- 对应 Scala: 78_DoobieTransactorResource.scala
-- 对应 Elixir: 09_ecto_repo_changeset_multi.exs
-- ============================================================

module Main where

import Control.Monad.IO.Class    (liftIO)
import Control.Monad.Logger      (NoLoggingT, runNoLoggingT)
import Data.Text                 (Text)
import qualified Data.Text as T
import Database.Persist
import Database.Persist.Sqlite
import Database.Persist.TH

-- ============================================================
-- Part 1: Schema 定义（TH + quasi-quote）
-- ============================================================
--
-- 这段 quasi-quote 会在编译期生成：
--   * data Book = Book { bookTitle :: Text, bookAuthor :: Text, bookYear :: Int }
--   * data BookId = ...（自增主键）
--   * instance PersistEntity Book
--   * EntityField Book Text 等等
-- 你可以把它想成 "yaml 声明 → 一坨样板类型代码"。

share [mkPersist sqlSettings, mkMigrate "migrateAll"] [persistLowerCase|
Book
    title  Text
    author Text
    year   Int
    deriving Show
|]

-- ============================================================
-- Part 2: 小小仓储 DSL
-- ============================================================
--
-- 所有查询/写入都在 SqlPersistT 里，用 mtl 习惯写法。
-- 我们故意把"事务/DB 连接"的边界放在 main 里（runSqlite）。

seedBooks :: SqlPersistT (NoLoggingT IO) ()
seedBooks = do
  _ <- insert Book { bookTitle = "SICP",                bookAuthor = "Abelson & Sussman", bookYear = 1985 }
  _ <- insert Book { bookTitle = "The Dragon Book",      bookAuthor = "Aho et al.",        bookYear = 2006 }
  _ <- insert Book { bookTitle = "Purely FP Haskell",    bookAuthor = "Chris Okasaki",     bookYear = 1998 }
  pure ()

listAllBooks :: SqlPersistT (NoLoggingT IO) [Entity Book]
listAllBooks = selectList [] [Asc BookYear]

findByAuthor :: Text -> SqlPersistT (NoLoggingT IO) [Entity Book]
findByAuthor a = selectList [BookAuthor ==. a] []

bumpYear :: Text -> SqlPersistT (NoLoggingT IO) ()
bumpYear t = updateWhere [BookTitle ==. t] [BookYear +=. 1]

deleteOld :: Int -> SqlPersistT (NoLoggingT IO) Int
deleteOld cutoff = do
  old <- selectList [BookYear <. cutoff] []
  mapM_ (delete . entityKey) old
  pure (length old)

-- ============================================================
-- Part 3: 脚本化 main
-- ============================================================

printBook :: Entity Book -> IO ()
printBook (Entity k b) =
  putStrLn $ "  #" <> show (fromSqlKey k)
          <> "  " <> show (bookYear b)
          <> "  " <> T.unpack (bookTitle b)
          <> " — " <> T.unpack (bookAuthor b)

main :: IO ()
main = runNoLoggingT . withSqliteConn ":memory:" . runSqlConn $ do
  liftIO $ putStrLn "=========================================="
  liftIO $ putStrLn "Demo 32: Persistent + SQLite CRUD"
  liftIO $ putStrLn "==========================================\n"

  -- 1. 建表
  runMigration migrateAll
  liftIO $ putStrLn "[migrate] tables created"

  -- 2. 插入示例数据
  seedBooks
  liftIO $ putStrLn "[seed]    3 books inserted\n"

  -- 3. 全表 + 条件查询
  all_ <- listAllBooks
  liftIO $ putStrLn "=== All books (by year asc) ==="
  liftIO $ mapM_ printBook all_

  okasaki <- findByAuthor "Chris Okasaki"
  liftIO $ putStrLn "\n=== Books by Chris Okasaki ==="
  liftIO $ mapM_ printBook okasaki

  -- 4. 更新
  bumpYear "SICP"
  liftIO $ putStrLn "\n[update] SICP.year += 1"

  -- 5. 删除早于某年的
  removed <- deleteOld 2000
  liftIO $ putStrLn $ "[delete] removed " <> show removed <> " book(s) before 2000"

  -- 6. 终态
  final <- listAllBooks
  liftIO $ putStrLn "\n=== Final state ==="
  liftIO $ mapM_ printBook final
  liftIO $ putStrLn "\n=========================================="
