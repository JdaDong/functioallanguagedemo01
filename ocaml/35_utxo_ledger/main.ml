(* dune exec ./main.exe
   demo 35: UTXO 账本 ── 比特币风格的不可变状态机
   - UTXO = "Unspent Transaction Output" 未花费输出
   - 账户余额 = 所有属于该地址的 UTXO 之和
   - 每笔交易 = 选若干 UTXO 当输入 + 创建新 UTXO 当输出
   - 校验：输入和 ≥ 输出和；输入必须未被花过；签名（本 demo 简化为 owner 校验）
   - 教学：函数式不可变 + 双花拒绝
*)

(* === 数据结构 === *)
type address = string

type utxo = {
  txid    : string;       (* 来自哪笔交易 *)
  index   : int;          (* 该交易第几个输出 *)
  owner   : address;
  amount  : int;
}

type tx_input = {
  ref_txid  : string;
  ref_index : int;
  spender   : address;    (* 简化：用地址自身代签名 *)
}

type tx_output = {
  to_addr : address;
  amount  : int;
}

type tx = {
  txid    : string;
  inputs  : tx_input list;
  outputs : tx_output list;
}

(* === 账本：UTXO 集 === *)
module UtxoSet = Map.Make (struct
  type t = string * int   (* (txid, index) *)
  let compare = compare
end)

type ledger = utxo UtxoSet.t

let empty : ledger = UtxoSet.empty

(* === 创世：直接发币（无输入）=== *)
let coinbase ~txid ~outputs (l : ledger) : ledger =
  List.fold_left (fun acc (i, o) ->
    UtxoSet.add (txid, i)
      { txid; index = i; owner = o.to_addr; amount = o.amount } acc)
    l (List.mapi (fun i o -> (i, o)) outputs)

(* === 应用一笔交易 === *)
let apply_tx (l : ledger) (t : tx) : (ledger, string) result =
  (* 1. 收集输入引用 + 校验它们都还在 UTXO 集里、且 owner 对得上 *)
  let consumed = ref [] in
  let collect_inputs () =
    List.fold_left (fun acc i ->
      match acc with
      | Error _ -> acc
      | Ok sum ->
          (match UtxoSet.find_opt (i.ref_txid, i.ref_index) l with
           | None -> Error (Printf.sprintf "input not found or already spent: %s#%d"
                              i.ref_txid i.ref_index)
           | Some u when u.owner <> i.spender ->
               Error (Printf.sprintf "owner mismatch: utxo owner=%s, spender=%s"
                        u.owner i.spender)
           | Some u ->
               consumed := (i.ref_txid, i.ref_index) :: !consumed;
               Ok (sum + u.amount)))
      (Ok 0) t.inputs
  in
  match collect_inputs () with
  | Error e -> Error e
  | Ok in_sum ->
      let out_sum = List.fold_left (fun s o -> s + o.amount) 0 t.outputs in
      if out_sum > in_sum then
        Error (Printf.sprintf "outputs > inputs: %d > %d" out_sum in_sum)
      else
        (* 2. 把消耗掉的 UTXO 移除 *)
        let l = List.fold_left (fun l k -> UtxoSet.remove k l) l !consumed in
        (* 3. 加入新 UTXO *)
        let l = List.fold_left (fun l (i, o) ->
          UtxoSet.add (t.txid, i)
            { txid = t.txid; index = i;
              owner = o.to_addr; amount = o.amount } l)
          l (List.mapi (fun i o -> (i, o)) t.outputs)
        in
        Ok l

let balance (l : ledger) (a : address) : int =
  UtxoSet.fold (fun _ u acc -> if u.owner = a then acc + u.amount else acc) l 0

let print_balances l names =
  List.iter (fun n -> Printf.printf "  %s: %d\n" n (balance l n)) names

(* === 演示 === *)
let () =
  Printf.printf "── 创世：给 Alice 100 ──\n";
  let l0 = coinbase
      ~txid:"genesis"
      ~outputs:[ { to_addr = "Alice"; amount = 100 } ]
      empty in
  print_balances l0 ["Alice"; "Bob"; "Carol"];

  Printf.printf "\n── 交易 1: Alice 转 Bob 30，找零 70 给自己 ──\n";
  let tx1 = {
    txid = "tx1";
    inputs = [ { ref_txid = "genesis"; ref_index = 0; spender = "Alice" } ];
    outputs = [
      { to_addr = "Bob"; amount = 30 };
      { to_addr = "Alice"; amount = 70 };
    ];
  } in
  let l1 = match apply_tx l0 tx1 with
    | Ok l -> Printf.printf "  ✓ 成功\n"; l
    | Error e -> Printf.printf "  ✗ %s\n" e; l0
  in
  print_balances l1 ["Alice"; "Bob"; "Carol"];

  Printf.printf "\n── 交易 2（双花尝试）：Alice 再次花掉 genesis#0 ──\n";
  let tx2_double_spend = {
    txid = "tx2";
    inputs = [ { ref_txid = "genesis"; ref_index = 0; spender = "Alice" } ];
    outputs = [ { to_addr = "Carol"; amount = 100 } ];
  } in
  (match apply_tx l1 tx2_double_spend with
   | Ok _ -> Printf.printf "  !! 不应该成功\n"
   | Error e -> Printf.printf "  ✓ 正确拒绝: %s\n" e);

  Printf.printf "\n── 交易 3: Bob 转 Carol 25 ──\n";
  let tx3 = {
    txid = "tx3";
    inputs = [ { ref_txid = "tx1"; ref_index = 0; spender = "Bob" } ];
    outputs = [
      { to_addr = "Carol"; amount = 25 };
      { to_addr = "Bob"; amount = 5 };
    ];
  } in
  let l2 = match apply_tx l1 tx3 with
    | Ok l -> Printf.printf "  ✓ 成功\n"; l
    | Error e -> Printf.printf "  ✗ %s\n" e; l1
  in
  print_balances l2 ["Alice"; "Bob"; "Carol"];

  Printf.printf "\n── 交易 4（伪造 owner）：Carol 想花 Alice 的找零 ──\n";
  let tx4_forged = {
    txid = "tx4";
    inputs = [ { ref_txid = "tx1"; ref_index = 1; spender = "Carol" } ];
    outputs = [ { to_addr = "Carol"; amount = 70 } ];
  } in
  match apply_tx l2 tx4_forged with
  | Ok _    -> Printf.printf "  !! 不应该成功\n"
  | Error e -> Printf.printf "  ✓ 正确拒绝: %s\n" e
