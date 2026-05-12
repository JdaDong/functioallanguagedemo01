(* dune exec ./main.exe
   demo 14: include —— 模块"继承/合成"的方式
   - include M 把 M 的所有内容直接拷贝到当前模块里
   - 等价于 OOP 的 mixin / 组合，但是在模块层面
   - 常见用法：基础模块 + 扩展模块，工业代码大量用
*)

(* === 1. 基础模块：只提供最少的能力 === *)
module Base = struct
  type t = { x : int; y : int }

  let make x y = { x; y }
  let to_string p = Printf.sprintf "(%d, %d)" p.x p.y
end

(* === 2. 扩展模块：include Base 把它的全部 API 拿过来，再加新功能 === *)
module Point = struct
  include Base                              (* type t / make / to_string 全部并入 *)

  let zero = make 0 0

  let add a b = make (a.x + b.x) (a.y + b.y)

  let scale k p = make (k * p.x) (k * p.y)
end

(* === 3. include 也能用来"重写并扩展"已有模块 === *)
(* 这里模拟"给标准库 List 加一个 sum" *)
module My_list = struct
  include List                              (* List.map / List.filter ... 全有了 *)

  let sum = List.fold_left ( + ) 0
end

(* === 4. 试用 === *)
let () =
  let p1 = Point.make 1 2 in
  let p2 = Point.make 3 4 in
  Printf.printf "p1   = %s\n" (Point.to_string p1);          (* 来自 Base *)
  Printf.printf "p1+p2= %s\n" (Point.to_string (Point.add p1 p2));  (* Point 自己加的 *)
  Printf.printf "2*p2 = %s\n" (Point.to_string (Point.scale 2 p2));
  Printf.printf "zero = %s\n" (Point.to_string Point.zero);

  (* My_list 既有 List 的全部 API，也有自己加的 sum *)
  let xs = [1; 2; 3; 4; 5] in
  Printf.printf "len  = %d\n" (My_list.length xs);            (* 来自 List *)
  Printf.printf "sum  = %d\n" (My_list.sum xs)                (* My_list 加的 *)
