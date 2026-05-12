(* dune exec ./main.exe
   demo 10: functor 基础——参数化模块
   - functor = "吃模块、吐模块"的函数
   - 经典案例：Make_set(Ord) -> Set，按任意排序规则做集合
   - stdlib 的 Map.Make / Set.Make 就是这个套路
*)

(* === 1. 输入参数的接口：要求"能比较两个值" === *)
module type ORDERED = sig
  type t
  val compare : t -> t -> int
end

(* === 2. functor：吃一个 ORDERED，吐一个排序集合 === *)
module Make_set (Ord : ORDERED) = struct
  type elt = Ord.t
  type t = elt list                 (* 内部用有序无重 list 实现 *)

  let empty : t = []

  let rec add x = function
    | []                          -> [x]
    | y :: ys when Ord.compare x y = 0 -> y :: ys      (* 已存在 *)
    | y :: ys when Ord.compare x y < 0 -> x :: y :: ys (* 插入点 *)
    | y :: ys                          -> y :: add x ys

  let mem x s =
    List.exists (fun y -> Ord.compare x y = 0) s

  let to_list s = s
end

(* === 3. 实例化：拿不同的 Ord 喂给 functor === *)

module IntSet = Make_set (struct
  type t = int
  let compare = compare           (* stdlib 多态比较 *)
end)

module StrDescSet = Make_set (struct
  type t = string
  let compare a b = compare b a   (* 反过来排：降序 *)
end)

(* === 4. 试用 === *)

let () =
  let s1 = IntSet.(empty |> add 3 |> add 1 |> add 4 |> add 1 |> add 5) in
  Printf.printf "IntSet 升序: [%s]\n"
    (IntSet.to_list s1 |> List.map string_of_int |> String.concat "; ");
  Printf.printf "包含 4? %b\n" (IntSet.mem 4 s1);

  let s2 = StrDescSet.(empty |> add "apple" |> add "cherry" |> add "banana") in
  Printf.printf "StrDescSet 降序: [%s]\n"
    (StrDescSet.to_list s2 |> String.concat "; ")
