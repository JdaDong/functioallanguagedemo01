(* dune exec ./main.exe
   demo 12: first-class modules——把模块当成"值"传来传去
   - 普通模块只能在编译期被 functor 用
   - first-class module = (module M : SIG)，把模块打包成一个值
   - 用 (val v : SIG) 解包，恢复成正常模块在表达式里使用
   - 典型用途：运行时根据条件挑不同实现
*)

module type SHOW = sig
  type t
  val name : string
  val sample : t
  val show : t -> string
end

module Int_show : SHOW = struct
  type t = int
  let name = "Int"
  let sample = 42
  let show = string_of_int
end

module Bool_show : SHOW = struct
  type t = bool
  let name = "Bool"
  let sample = true
  let show = string_of_bool
end

(* 函数接受一个 first-class module，运行时再解包用 *)
let describe (m : (module SHOW)) =
  let module M = (val m : SHOW) in
  Printf.printf "[%s] sample = %s\n" M.name (M.show M.sample)

let () =
  (* 用 (module ...) 把模块打包成普通的值 *)
  let modules : (module SHOW) list = [
    (module Int_show);
    (module Bool_show);
  ] in
  List.iter describe modules;

  (* 也可以根据运行时条件挑模块 *)
  let pick_at_runtime (use_int : bool) : (module SHOW) =
    if use_int then (module Int_show) else (module Bool_show)
  in
  describe (pick_at_runtime true);
  describe (pick_at_runtime false)
