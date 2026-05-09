-- 45_UTXOLedger.hs — Mini UTXO 区块链账本（Cardano/Plutus 范式的最小内核）
--
-- 行业背景：
--   * Cardano / IOHK 的 Plutus 智能合约平台、Bitcoin、Ergo 都采用 UTXO 模型
--   * 与"账户余额模型"（Ethereum）相对，UTXO 模型天然适合并行化、易于形式化验证
--   * Haskell 的代数数据类型 + 模式匹配让 UTXO 验证逻辑写起来非常直接
--
-- 本 demo 用纯 base 实现一个能跑的最小 UTXO 账本：
--   1. Tx：交易类型（输入引用之前的 UTXO，输出产生新 UTXO）
--   2. UTXOSet：未花费输出集合（用 Map 模拟）
--   3. validateTx / applyTx：交易合法性校验 + 状态转移
--   4. 双花检测：同一笔 UTXO 不能在两笔交易里同时被花费（demo 中用反例展示）
--   5. 手写 FNV-1a 32 位哈希：用于"区块哈希指针"（链式不可篡改）
--   6. Merkle Root：一个区块内所有交易的 Merkle 树根（防篡改任意一笔交易）
--
-- 运行：runghc 45_UTXOLedger.hs

module Main where

import Data.Bits (xor, shiftL, shiftR, (.&.), (.|.))
import Data.Char (ord, intToDigit)
import Data.List (foldl', sortBy)
import Data.Map.Strict (Map)
import qualified Data.Map.Strict as Map
import Data.Maybe (fromMaybe, isJust)
import Data.Word (Word32)

------------------------------------------------------------
-- 1. 基础类型
------------------------------------------------------------

-- 公钥（Address）：演示用 String，省去真实加密
type Address = String

-- 金额：用 Int 表示最小单位（lovelace / satoshi）
type Amount = Int

-- 交易 ID：交易内容哈希后的 16 进制字符串
type TxId = String

-- UTXO 引用：哪个交易的第几号输出
data TxOutRef = TxOutRef { refTxId :: TxId, refIx :: Int }
  deriving (Eq, Ord, Show)

-- 交易输出：发给哪个地址多少钱
data TxOut = TxOut { outAddr :: Address, outAmt :: Amount }
  deriving (Eq, Show)

-- 交易输入：引用一个 UTXO + 解锁脚本（demo 用 Address 等值匹配代替签名）
data TxIn = TxIn { inRef :: TxOutRef, inSigner :: Address }
  deriving (Eq, Show)

-- 交易：输入列表 + 输出列表（不含签名/脚本，简化）
data Tx = Tx
  { txIns  :: [TxIn]
  , txOuts :: [TxOut]
  } deriving (Eq, Show)

-- UTXO 集：未花费输出的全集，状态用 Map 表示
type UTXOSet = Map TxOutRef TxOut

------------------------------------------------------------
-- 2. 手写 FNV-1a 32 位哈希（不依赖外部包）
------------------------------------------------------------
-- FNV-1a 是一个非加密哈希，工业上用于哈希表/校验和；
-- 真实区块链用 SHA-256/Blake2b，这里只为演示"哈希指针"概念。

fnvOffset, fnvPrime :: Word32
fnvOffset = 2166136261
fnvPrime  = 16777619

fnv1a :: String -> Word32
fnv1a = foldl' step fnvOffset
  where step h c = (h `xor` fromIntegral (ord c)) * fnvPrime

-- 把 Word32 渲染为 8 位 16 进制串（区块哈希常用展示形式）
toHex :: Word32 -> String
toHex w = pad 8 (go w "")
  where
    go 0 acc = if null acc then "0" else acc
    go n acc =
      let d = fromIntegral (n .&. 0xF)
          c = intToDigit d
      in go (n `shiftR` 4) (c : acc)
    pad n s = replicate (n - length s) '0' ++ s

-- 给任意 Show 的东西算哈希（demo 简便做法：show 后哈希）
hashShow :: Show a => a -> String
hashShow = toHex . fnv1a . show

-- 计算交易 ID：对交易内容（输入+输出）做哈希
txHash :: Tx -> TxId
txHash = hashShow

------------------------------------------------------------
-- 3. UTXO 验证 + 状态转移
------------------------------------------------------------

data TxError
  = MissingInput TxOutRef       -- 引用的 UTXO 不存在或已被花掉
  | WrongSigner TxOutRef Address Address  -- 解锁地址与 UTXO 拥有者不符
  | UnbalancedTx Amount Amount  -- 输入总额 ≠ 输出总额（demo 不允许 fee）
  deriving (Show)

-- 验证一笔交易是否能在当前 UTXO 集上合法执行
validateTx :: UTXOSet -> Tx -> Either TxError ()
validateTx u tx = do
  -- (a) 每个输入引用的 UTXO 必须存在
  let lookups = [(inp, Map.lookup (inRef inp) u) | inp <- txIns tx]
  mapM_ (\(inp, m) -> case m of
            Nothing  -> Left (MissingInput (inRef inp))
            Just out -> if outAddr out == inSigner inp
                         then Right ()
                         else Left (WrongSigner (inRef inp) (outAddr out) (inSigner inp)))
        lookups
  -- (b) 输入总额 == 输出总额
  let inAmt  = sum [outAmt out | (_, Just out) <- lookups]
      outAmt' = sum (map outAmt (txOuts tx))
  if inAmt == outAmt'
    then Right ()
    else Left (UnbalancedTx inAmt outAmt')

-- 把一笔交易应用到 UTXO 集：花掉输入、产生输出
applyTx :: UTXOSet -> Tx -> Either TxError UTXOSet
applyTx u tx = do
  validateTx u tx
  let tid     = txHash tx
      removed = foldl' (flip Map.delete) u (map inRef (txIns tx))
      added   = Map.fromList
                  [(TxOutRef tid i, o) | (i, o) <- zip [0..] (txOuts tx)]
  Right (Map.union added removed)

-- 一次提交多笔交易（区块内顺序执行）
applyBlock :: UTXOSet -> [Tx] -> Either (Int, TxError) UTXOSet
applyBlock = go 0
  where
    go _ u []     = Right u
    go i u (t:ts) = case applyTx u t of
      Left e   -> Left (i, e)
      Right u' -> go (i + 1) u' ts

------------------------------------------------------------
-- 4. Merkle 根：把一个区块里所有交易摘成一个 32 位指纹
------------------------------------------------------------
-- 经典 Merkle 树构造：奇数时复制最后一个，两两 hash 拼接到只剩一个节点。

merkleRoot :: [Tx] -> String
merkleRoot []  = toHex 0
merkleRoot txs = build (map txHash txs)
  where
    build [h]  = h
    build hs   = build (pair (ensureEven hs))
    ensureEven xs
      | even (length xs) = xs
      | otherwise        = xs ++ [last xs]
    pair (a:b:rest) = combine a b : pair rest
    pair []         = []
    pair [x]        = [x]   -- 理论上 ensureEven 后不会走到这里
    combine a b     = toHex (fnv1a (a ++ b))

------------------------------------------------------------
-- 5. 区块 + 哈希链
------------------------------------------------------------

data Block = Block
  { blkHeight   :: Int
  , blkPrevHash :: String   -- 指向上一区块的哈希指针
  , blkTxs      :: [Tx]
  , blkMerkle   :: String   -- 本区块交易的 Merkle 根
  , blkHash     :: String   -- 本区块自身哈希
  } deriving (Show)

-- 创建并自封哈希
mkBlock :: Int -> String -> [Tx] -> Block
mkBlock h prev ts =
  let mr  = merkleRoot ts
      raw = show (h, prev, mr, ts)
      bh  = toHex (fnv1a raw)
  in Block h prev ts mr bh

-- 验证整条链：每个区块的 prevHash 必须等于上一个的 hash，且本区块的 Merkle 根
-- 与重算结果一致（任何一笔交易被改都会被发现）
verifyChain :: [Block] -> Either String ()
verifyChain []       = Right ()
verifyChain (b0:bs0) = do
  checkBlock b0
  go b0 bs0
  where
    checkBlock b =
      if merkleRoot (blkTxs b) == blkMerkle b
        then Right ()
        else Left ("merkle mismatch at height " ++ show (blkHeight b))
    go _ [] = Right ()
    go prev (b:rest) = do
      if blkPrevHash b == blkHash prev
        then Right ()
        else Left ("prev-hash mismatch at height " ++ show (blkHeight b))
      checkBlock b
      go b rest

------------------------------------------------------------
-- 6. Demo 场景
------------------------------------------------------------

-- 创世交易：凭空给 alice 100 个币。
-- 真实链里这是 coinbase；这里用一个空输入交易表示，并直接塞进 UTXO 集。
genesisTx :: Tx
genesisTx = Tx [] [TxOut "alice" 100]

-- 启动账本：放入创世输出
genesisLedger :: UTXOSet
genesisLedger =
  let tid = txHash genesisTx
  in Map.fromList [(TxOutRef tid 0, TxOut "alice" 100)]

-- 工具：打印 UTXO 集
showLedger :: UTXOSet -> String
showLedger u =
  let rows = sortBy (\(_,a) (_,b) -> compare (outAddr a) (outAddr b))
                    (Map.toList u)
  in unlines [ "  " ++ outAddr o ++ "  -" ++ show (outAmt o)
               ++ "  <-  " ++ take 8 (refTxId r) ++ "#" ++ show (refIx r)
             | (r,o) <- rows ]

main :: IO ()
main = do
  putStrLn "=== UTXO Ledger Demo ==="
  putStrLn "[创世] alice 凭空获得 100 币"
  let u0 = genesisLedger
  putStr (showLedger u0)

  -- 交易 1：alice 给 bob 30 + 自己找零 70
  let aliceUtxo = TxOutRef (txHash genesisTx) 0
      tx1 = Tx
        { txIns  = [TxIn aliceUtxo "alice"]
        , txOuts = [TxOut "bob" 30, TxOut "alice" 70]
        }
  putStrLn "\n[交易 1] alice -> bob 30, 找零 70"
  case applyTx u0 tx1 of
    Left e -> putStrLn ("  ✗ 失败：" ++ show e)
    Right u1 -> do
      putStr (showLedger u1)

      -- 交易 2：bob 把 30 给 carol
      let bobUtxo = TxOutRef (txHash tx1) 0
          tx2 = Tx [TxIn bobUtxo "bob"] [TxOut "carol" 30]
      putStrLn "\n[交易 2] bob -> carol 30"
      case applyTx u1 tx2 of
        Left e -> putStrLn ("  ✗ 失败：" ++ show e)
        Right u2 -> do
          putStr (showLedger u2)

          -- 反例 1：bob 想再花一次同一笔 UTXO（双花）
          let txBad = Tx [TxIn bobUtxo "bob"] [TxOut "evil" 30]
          putStrLn "\n[反例-双花] bob 试图重复花掉同一笔 UTXO"
          case applyTx u2 txBad of
            Left e  -> putStrLn ("  ✓ double-spend rejected: " ++ show e)
            Right _ -> putStrLn "  ✗ 不该通过！"

          -- 反例 2：alice 想花掉 carol 的钱（签名错配）
          let carolUtxo = TxOutRef (txHash tx2) 0
              txWrongSig = Tx [TxIn carolUtxo "alice"] [TxOut "alice" 30]
          putStrLn "\n[反例-签名错配] alice 想花 carol 的 UTXO"
          case applyTx u2 txWrongSig of
            Left e  -> putStrLn ("  ✓ rejected: " ++ show e)
            Right _ -> putStrLn "  ✗ 不该通过！"

          -- 反例 3：金额不平衡（凭空印钱）
          let aliceLeft = TxOutRef (txHash tx1) 1  -- 70
              txInflate = Tx [TxIn aliceLeft "alice"]
                             [TxOut "alice" 71]    -- 多 1
          putStrLn "\n[反例-不平衡] alice 凭空多印 1 个币"
          case applyTx u2 txInflate of
            Left e  -> putStrLn ("  ✓ rejected: " ++ show e)
            Right _ -> putStrLn "  ✗ 不该通过！"

          -- 把上面 2 笔合法交易打包成区块链：block 0 = 创世，block 1 = tx1+tx2
          let b0 = mkBlock 0 "00000000" [genesisTx]
              b1 = mkBlock 1 (blkHash b0) [tx1, tx2]
              chain = [b0, b1]
          putStrLn "\n[区块链] 把 2 个区块串起来"
          mapM_ (\b -> putStrLn $
                  "  height=" ++ show (blkHeight b)
                  ++ "  hash=" ++ blkHash b
                  ++ "  prev=" ++ blkPrevHash b
                  ++ "  merkle=" ++ blkMerkle b)
                chain
          putStrLn $ "  block 1 hash = " ++ blkHash b1
          putStrLn $ "  merkle root  = " ++ blkMerkle b1

          -- 校验链 + 篡改演示
          putStrLn "\n[篡改演示] 偷偷把 tx2 的金额改成 999，看 merkle root 是否变化"
          let tx2Forged   = tx2 { txOuts = [TxOut "carol" 999] }
              merkleOrig  = merkleRoot [tx1, tx2]
              merkleForge = merkleRoot [tx1, tx2Forged]
          putStrLn $ "  original merkle = " ++ merkleOrig
          putStrLn $ "  forged   merkle = " ++ merkleForge
          if merkleOrig /= merkleForge
            then putStrLn "  ✓ 篡改可被检测（merkle 根不一致）"
            else putStrLn "  ✗ 哈希碰撞？这不应该发生"

          case verifyChain chain of
            Right ()  -> putStrLn "\n[verifyChain] ✓ 链合法"
            Left err  -> putStrLn ("\n[verifyChain] ✗ " ++ err)

  putStrLn "\n=== Done ==="
