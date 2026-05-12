(* dune exec ./main.exe
   demo 16: 代数效应（Algebraic Effects）—— OCaml 5 的旗舰特性
   - 类比 try/catch：抛出 → 处理。但区别是：处理完可以"回到原现场"继续
   - 用法：定义一个 effect → perform 它 → 在外层 try ... with effect 处理
   - 关键魔法：handler 拿到一个 continuation k，可以用 continue k v 把控制权送回
   - 对标：Koka 语言的 effects 系统、Haskell 的 free monad
*)

open Effect
open Effect.Deep

(* === 1. 声明一个 effect：表示"我需要外部给我一个 int" === *)
type _ Effect.t += Ask : int Effect.t

(* === 2. 业务函数：随手 perform Ask，就像同步函数那样写 === *)
let business () =
  let a = perform Ask in
  let b = perform Ask in
  Printf.printf "  business 收到: a=%d, b=%d\n" a b;
  a + b

(* === 3. handler 1：每次 Ask 都给固定 42 === *)
let run_with_const () =
  try_with business ()
    { effc = fun (type a) (eff : a Effect.t) ->
        match eff with
        | Ask -> Some (fun (k : (a, _) continuation) -> continue k 42)
        | _   -> None
    }

(* === 4. handler 2：维持一个外部计数器，每次 Ask 给递增的值 === *)
let run_with_counter () =
  let n = ref 0 in
  try_with business ()
    { effc = fun (type a) (eff : a Effect.t) ->
        match eff with
        | Ask ->
            Some (fun (k : (a, _) continuation) ->
              incr n;
              continue k !n)
        | _ -> None
    }

let () =
  Printf.printf "[handler=const42]\n";
  let r1 = run_with_const () in
  Printf.printf "  result = %d\n" r1;

  Printf.printf "[handler=counter]\n";
  let r2 = run_with_counter () in
  Printf.printf "  result = %d\n" r2
