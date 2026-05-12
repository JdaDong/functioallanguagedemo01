(* dune exec ./main.exe
   demo 22: 用模块系统模拟 Haskell 的 typeclass
   - Haskell: class Show a where show :: a -> String
   - OCaml : module type SHOW = sig type t val show : t -> string end
   - Haskell 的 instance Show Int / instance Show Bool
     ↔ OCaml 的 module Int_show : SHOW / module Bool_show : SHOW
   - functor 充当 Haskell 的"约束"：
     module Pretty (S : SHOW) = struct ... S.show ... end
*)

(* === 1. "typeclass" Show === *)
module type SHOW = sig
  type t
  val show : t -> string
end

(* === 2. 几个 instance === *)
module Show_int : SHOW with type t = int = struct
  type t = int
  let show = string_of_int
end

module Show_bool : SHOW with type t = bool = struct
  type t = bool
  let show = string_of_bool
end

module Show_string : SHOW with type t = string = struct
  type t = string
  let show s = "\"" ^ s ^ "\""
end

(* === 3. functor 模拟"带 typeclass 约束的泛型函数"
   Haskell: showList :: Show a => [a] -> String
   OCaml:   module Show_list (S : SHOW) : SHOW with type t = S.t list *)
module Show_list (S : SHOW) : SHOW with type t = S.t list = struct
  type t = S.t list
  let show xs =
    "[" ^ (xs |> List.map S.show |> String.concat "; ") ^ "]"
end

(* === 4. 第二个 typeclass：Eq === *)
module type EQ = sig
  type t
  val eq : t -> t -> bool
end

module Eq_int : EQ with type t = int = struct
  type t = int
  let eq = ( = )
end

(* === 5. 多 typeclass 约束：要求同时是 SHOW 和 EQ ===
   Haskell: dedup :: (Show a, Eq a) => [a] -> String *)
module Dedup_show (S : SHOW) (E : EQ with type t = S.t) = struct
  let dedup_show xs =
    let rec uniq = function
      | [] -> []
      | x :: rest ->
          x :: uniq (List.filter (fun y -> not (E.eq x y)) rest)
    in
    let module L = Show_list (S) in
    L.show (uniq xs)
end

(* === 6. 试用 === *)
let () =
  (* 直接用 instance *)
  Printf.printf "Show_int 42       = %s\n" (Show_int.show 42);
  Printf.printf "Show_bool true    = %s\n" (Show_bool.show true);
  Printf.printf "Show_string hello = %s\n" (Show_string.show "hello");

  (* 用 functor 派生出 list 的 Show *)
  let module SLI = Show_list (Show_int) in
  Printf.printf "Show [1;2;3]      = %s\n" (SLI.show [1; 2; 3]);

  let module SLS = Show_list (Show_string) in
  Printf.printf "Show [\"a\";\"b\"]    = %s\n" (SLS.show ["a"; "b"]);

  (* 嵌套：list of list *)
  let module SLLI = Show_list (Show_list (Show_int)) in
  Printf.printf "Show [[1;2];[3]]  = %s\n" (SLLI.show [[1;2]; [3]]);

  (* 双约束（Show + Eq）：去重再 show *)
  let module D = Dedup_show (Show_int) (Eq_int) in
  Printf.printf "dedup [1;2;1;3;2] = %s\n" (D.dedup_show [1; 2; 1; 3; 2])
