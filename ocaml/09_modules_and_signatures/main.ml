(* dune exec ./main.exe
   demo 09: module / signature / .mli 接口文件
   - module type 声明"模块该长什么样"
   - module M : SIG = struct ... end 让 M 满足 SIG
   - 同目录 main.mli 控制本文件对外暴露什么（信息隐藏）
*)

(* === 1. 内联模块 + signature：单文件就能演示封装 === *)

module type COUNTER = sig
  type t                       (* 抽象类型：外界看不到 t 是啥 *)
  val make : unit -> t
  val incr : t -> t
  val get  : t -> int
end

(* 实现：t 内部是 int，但 SIG 把它隐藏了 *)
module Counter : COUNTER = struct
  type t = int
  let make () = 0
  let incr n = n + 1
  let get n = n
end

(* === 2. 本文件自己也是个模块（Main），main.mli 控制它的对外面貌 === *)
(* 下面这个 stack 实现的内部是 list，但 main.mli 把它声明成了抽象 'a stack。
   所以如果别的模块 import Main，它看不到这个 list。 *)

type 'a stack = 'a list
let empty : 'a stack = []
let push x s = x :: s
let pop = function
  | []      -> None
  | x :: xs -> Some (x, xs)
let size = List.length

(* === 3. 试用 === *)

let () =
  (* Counter：t 抽象，必须走 make/incr/get *)
  let c = Counter.make () |> Counter.incr |> Counter.incr |> Counter.incr in
  Printf.printf "counter = %d\n" (Counter.get c);

  (* 本文件的 stack：在本文件内能看到 t = list，但外部模块只能用 push/pop *)
  let s = empty |> push "a" |> push "b" |> push "c" in
  Printf.printf "stack size = %d\n" (size s);
  match pop s with
  | Some (top, _) -> Printf.printf "top = %s\n" top
  | None          -> print_endline "empty"
