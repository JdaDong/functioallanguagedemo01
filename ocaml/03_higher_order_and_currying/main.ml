(* dune exec ./main.exe
   demo 03: 高阶函数 + 柯里化
   - |>  正向管道（值在左、函数在右）
   - @@  反向应用（省括号）
   - 偏应用（函数天然柯里化，少喂参数即得新函数）
*)

(* 所有函数默认柯里化：add : int -> int -> int *)
let add x y = x + y

(* 偏应用：给 add 喂一个参数得到新函数 *)
let inc = add 1

(* 高阶：接收函数作参数 *)
let twice f x = f (f x)

(* |> 管道：1 |> inc |> inc 比 inc (inc 1) 顺序更自然 *)
let pipeline_demo () =
  [1; 2; 3; 4; 5]
  |> List.map inc            (* [2;3;4;5;6] *)
  |> List.filter (fun x -> x mod 2 = 0)  (* [2;4;6] *)
  |> List.fold_left ( + ) 0  (* 12 *)

(* @@ 反向应用：f @@ x 等价于 f (x)，常用于省一对括号 *)
let at_demo () =
  print_int @@ 1 + 2;          (* 等价于 print_int (1 + 2) *)
  print_newline ()

let () =
  Printf.printf "inc 10 = %d\n" (inc 10);
  Printf.printf "twice inc 10 = %d\n" (twice inc 10);
  Printf.printf "pipeline = %d\n" (pipeline_demo ());
  at_demo (); print_newline ()
