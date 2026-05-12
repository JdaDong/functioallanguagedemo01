(* dune exec ./main.exe
   demo 20: GADT —— 泛化代数数据类型，写"类型安全的解释器"
   - 普通 variant 所有构造子返回同一个类型 t
   - GADT 允许每个构造子返回 t 的不同实例化（如 t int、t bool）
   - 好处：编译期就能拒绝 If (1+2, ..., ...) 这种"条件不是 bool"的错
   - 对标 Haskell 的 GADTs 扩展
*)

(* === 1. 用 GADT 描述带类型的表达式 ===
   注意每个构造子返回的不是 expr，而是 'a expr，'a 在每条规则里被钉死 *)
type _ expr =
  | Int  : int  -> int expr                                (* 字面整数 *)
  | Bool : bool -> bool expr                               (* 字面布尔 *)
  | Add  : int expr * int expr -> int expr                 (* 加法只接受 int *)
  | Eq   : int expr * int expr -> bool expr                (* 相等比较返回 bool *)
  | If   : bool expr * 'a expr * 'a expr -> 'a expr        (* 条件必须是 bool，两支同类型 *)

(* === 2. eval：返回类型由 expr 的类型参数决定 ===
   一个表达式如果是 int expr，eval 返回 int；bool expr 则返回 bool *)
let rec eval : type a. a expr -> a = function
  | Int n        -> n
  | Bool b       -> b
  | Add (a, b)   -> eval a + eval b
  | Eq  (a, b)   -> eval a = eval b
  | If (c, t, e) -> if eval c then eval t else eval e

(* === 3. 试用：合法的能编译，非法的会被类型检查器挡掉 === *)
let () =
  (* 1 + 2 = 3 *)
  let e1 = Add (Int 1, Int 2) in
  Printf.printf "1 + 2 = %d\n" (eval e1);

  (* if (3 = 4) then 10 else 20 *)
  let e2 = If (Eq (Int 3, Int 4), Int 10, Int 20) in
  Printf.printf "if 3=4 then 10 else 20 = %d\n" (eval e2);

  (* 嵌套：if (1+2 = 3) then (5+5) else (0) *)
  let e3 = If (Eq (Add (Int 1, Int 2), Int 3),
               Add (Int 5, Int 5),
               Int 0) in
  Printf.printf "if 1+2=3 then 5+5 else 0 = %d\n" (eval e3);

  (* bool expr：eval 推断返回 bool *)
  let e4 = If (Bool true, Bool false, Bool true) in
  Printf.printf "if true then false else true = %b\n" (eval e4);

  (* 关键：下面这行如果取消注释，编译期就报错——
     因为 If 要求第一个参数是 bool expr，而 Int 1 是 int expr。
     这就是 GADT 的杀手锏：非法语法在编译期消失。
        let bad = If (Int 1, Int 10, Int 20)
  *)
  print_endline "GADT 让「类型不对的表达式」根本写不出来"
