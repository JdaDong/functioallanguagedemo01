(* dune exec ./main.exe
   demo 11: functor 进阶——多参 + sharing constraint
   - 一个 functor 吃多个模块参数
   - "with type" 共享约束：让 functor 输出的类型和输入的类型对齐
*)

(* === 1. 一对模块：分别表达"键"和"值的格式化方式" === *)

module type KEY = sig
  type t
  val compare : t -> t -> int
end

module type SHOW = sig
  type t
  val show : t -> string
end

(* === 2. 双参 functor：键有序 + 值能 show，得到一个有序 KV 表 === *)
(* "with type key = K.t and type value = V.t" 是 sharing constraint：
   把输出模块里的抽象类型，钉死到输入模块的具体类型上。
   不写的话外面拿到的 key/value 是抽象的，没法和真实 int/string 互操作。 *)

module Make_table (K : KEY) (V : SHOW)
  : sig
      type key
      type value
      type t
      val empty : t
      val set   : key -> value -> t -> t
      val dump  : t -> string
    end with type key = K.t and type value = V.t
= struct
  type key   = K.t
  type value = V.t
  type t     = (key * value) list   (* 简单 assoc list 实现 *)

  let empty : t = []

  let rec set k v = function
    | []                                  -> [k, v]
    | (k', _) :: rest when K.compare k k' = 0 -> (k, v) :: rest    (* 覆盖 *)
    | (k', v') :: rest when K.compare k k' < 0 -> (k, v) :: (k', v') :: rest
    | kv :: rest                          -> kv :: set k v rest

  let dump t =
    t
    |> List.map (fun (_, v) -> V.show v)
    |> String.concat ", "
end

(* === 3. 实例化：int 键 + string 值 === *)

module IntKey = struct
  type t = int
  let compare = compare
end

module StringShow = struct
  type t = string
  let show s = "\"" ^ s ^ "\""
end

module T = Make_table (IntKey) (StringShow)

(* === 4. 试用 === *)

let () =
  let t =
    T.empty
    |> T.set 3 "three"
    |> T.set 1 "one"
    |> T.set 2 "two"
    |> T.set 1 "ONE"             (* 覆盖 key=1 *)
  in
  (* 因为有 sharing constraint，T.set 接受真·int 和真·string，无需类型转换 *)
  Printf.printf "table: { %s }\n" (T.dump t)
