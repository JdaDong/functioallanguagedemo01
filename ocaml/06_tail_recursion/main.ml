(* dune exec ./main.exe
   demo 06: 尾递归
   - 普通递归：栈深度 = 输入规模，大输入会爆栈
   - 尾递归：累加器 + 末尾位置调用，OCaml 编译器把它变成循环
   - [@tail_mod_cons]：构造 cons 时也能享受尾调用优化（OCaml 4.14+）
*)

(* 1. 普通递归求和：n 大会栈溢出 *)
let rec sum_naive n =
  if n = 0 then 0 else n + sum_naive (n - 1)

(* 2. 尾递归求和：把"待加的部分"放进累加器 acc *)
let sum_tail n =
  let rec loop acc n =
    if n = 0 then acc
    else loop (acc + n) (n - 1)   (* 末尾位置：编译器消栈帧 *)
  in
  loop 0 n

(* 3. [@tail_mod_cons]：构造 list 时也尾调用
   普通的 List.map 在长 list 上也会栈溢出，
   带这个属性的版本不会。 *)
let[@tail_mod_cons] rec map f = function
  | []      -> []
  | x :: xs -> f x :: map f xs

let () =
  Printf.printf "sum_naive 1000 = %d\n" (sum_naive 1000);
  Printf.printf "sum_tail 1_000_000 = %d\n" (sum_tail 1_000_000);
  let xs = map (fun x -> x * 2) [1; 2; 3; 4; 5] in
  List.iter (Printf.printf "%d ") xs;
  print_newline ()
