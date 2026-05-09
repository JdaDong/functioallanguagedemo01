{-# LANGUAGE DataKinds      #-}
{-# LANGUAGE KindSignatures #-}

-- ============================================================
-- Demo 36: 幻影类型 (Phantom Types) 做编译期状态机
--
-- 目标：把"只能从 Draft 发布成 Published，Published 才能 Archive，
-- Archived 不能再编辑"这种业务规则从 *运行时检查* 提升到 *类型检查*。
--
-- 核心技巧：
--   * DataKinds 把数据构造器 Draft/Published/Archived "提升"为类型
--   * 用一个类型参数 s :: Status 标记 Post 处于哪种状态
--   * 状态迁移函数签名只接受合法的起点状态，起点错就编译失败
--
-- 对比其它语言：
--   * Elixir / Python / Go 都只能运行时查状态字段，忘了检查 = bug
--   * Scala + 类型成员 or sealed trait + 精确类型也能做，语法更重
--   * Rust 的 typestate 同源技术，和这里思路一致
--
-- 运行时：
--   本文件不会在运行时报错 —— 它主要表达的是"能编译 = 合法"。
--   文件末尾有一段用 {- ... -} 注释起来的反例，放出来就无法编译，
--   这正是类型系统帮我们做的工作。
--
-- 跑法：
--   runghc 36_PhantomTypesStateMachine.hs
-- ============================================================

module Main where

-- ============================================================
-- Part 1: 状态与 Post 类型
-- ============================================================

-- DataKinds 打开后，这三个构造器在"类型层面"也可用：
--   'Draft :: Status, 'Published :: Status, 'Archived :: Status
data Status = Draft | Published | Archived

-- s 是"幻影"的：它不在字段里出现，只在类型签名里。
-- 但编译器会严格区分 Post 'Draft / Post 'Published / Post 'Archived。
data Post (s :: Status) = Post
  { postTitle :: String
  , postBody  :: String
  }

-- ============================================================
-- Part 2: 合法转换（只有这些签名才存在）
-- ============================================================

-- 构造只能得到 Draft：Published / Archived 不能凭空创建
newDraft :: String -> String -> Post 'Draft
newDraft t b = Post { postTitle = t, postBody = b }

-- Draft 可以编辑
editDraft :: Post 'Draft -> String -> Post 'Draft
editDraft p newBody = p { postBody = newBody }

-- Draft --publish--> Published
publish :: Post 'Draft -> Post 'Published
publish (Post t b) = Post t b

-- Published --archive--> Archived
archive :: Post 'Published -> Post 'Archived
archive (Post t b) = Post t b

-- Published 可以被重新编辑吗？业务上说不可以，于是我们**不提供**
-- editPublished 这个函数。调用方根本写不出来。

-- 所有状态都能读，但渲染分状态（用类型类也行，这里保持简单）
summary :: String -> Post s -> String
summary stateTag (Post t _) = "[" <> stateTag <> "] " <> t

-- ============================================================
-- Part 3: 使用
-- ============================================================

main :: IO ()
main = do
  putStrLn "=========================================="
  putStrLn "Demo 36: Phantom Types State Machine"
  putStrLn "=========================================="

  let d0 = newDraft "Hello Haskell" "lorem ipsum"
      d1 = editDraft d0 "lorem ipsum dolor"
      p  = publish   d1
      a  = archive   p

  putStrLn $ summary "Draft    " d0
  putStrLn $ summary "Draft'   " d1
  putStrLn $ summary "Published" p
  putStrLn $ summary "Archived " a

  putStrLn ""
  putStrLn "编译期已保证："
  putStrLn "  * publish  只接 Post 'Draft"
  putStrLn "  * archive  只接 Post 'Published"
  putStrLn "  * 不存在 editPublished/unarchive —— 根本写不出非法调用"

  putStrLn "=========================================="

{- ============================================================
   Part 4: 反例（故意留着，取消注释即编译失败）
   ============================================================

-- 反例 1：想跳过 publish 直接 archive 一个 Draft
--   archive d0
-- 报错：Couldn't match type 'Draft with 'Published

-- 反例 2：想把 Archived 重新 publish
--   publish a
-- 报错：Couldn't match type 'Archived with 'Draft

-- 反例 3：编辑一个已发布的 Post
--   editDraft p "try to edit published"
-- 报错：Couldn't match type 'Published with 'Draft

这三个"bug"是别的语言典型的运行时错误。在 Haskell 里它们
根本进不了可执行文件 —— 这就是类型即业务规则。
============================================================ -}
