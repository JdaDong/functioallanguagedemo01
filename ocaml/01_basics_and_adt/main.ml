(* dune exec ./main.exe
   demo 01: let / match / type —— OCaml 三件套
   - let 绑定（含递归）
   - match 模式匹配
   - type 定义代数数据类型
*)

(* 1. let 绑定一个常量 *)
let greeting = "你好，OCaml"

(* 2. let rec 定义递归函数：Fibonacci *)
let rec fib n =
  match n with
  | 0 -> 0
  | 1 -> 1
  | _ -> fib (n - 1) + fib (n - 2)

(* 3. type 定义代数数据类型（ADT）—— 一棵二叉树 *)
type 'a tree =
  | Leaf
  | Node of 'a * 'a tree * 'a tree

(* 4. 用模式匹配遍历树，求节点数 *)
let rec size = function
  | Leaf -> 0
  | Node (_, l, r) -> 1 + size l + size r

let () =
  print_endline greeting;
  Printf.printf "fib(10) = %d\n" (fib 10);
  let t =
    Node (1,
      Node (2, Leaf, Leaf),
      Node (3, Node (4, Leaf, Leaf), Leaf))
  in
  Printf.printf "tree size = %d\n" (size t)
