(* dune exec ./main.exe
   demo 17: 用 effects 实现 generator —— 复刻 Python 的 yield
   - producer 看上去就是个普通递归函数，里面 perform (Yield x)
   - handler 截到 Yield，把"未跑完的部分"打包成 thunk，等消费者再唤醒
   - 这正是 Python/JS generator 的本质：可恢复的协程
*)

open Effect
open Effect.Deep

type _ Effect.t += Yield : int -> unit Effect.t

(* === 1. producer：长得像普通过程式代码，但能被中途暂停 === *)
let produce () =
  for i = 1 to 5 do
    perform (Yield i)
  done

(* === 2. 用 effect handler 把 producer 包装成"按需出值"的 generator === *)
(* next : unit -> int option
   每次调用要么返回 Some n（拿到下一个值），要么 None（producer 跑完了）。 *)
let make_gen producer =
  (* 用一个 ref 存"接着跑下去要做什么" *)
  let resume = ref (fun () ->
    try_with producer ()
      { effc = fun (type a) (eff : a Effect.t) ->
          match eff with
          | Yield _ -> assert false   (* 占位，下面真正运行时会被覆盖 *)
          | _       -> None
      };
    None) in

  (* 第一次启动 *)
  let next_box : int option ref = ref None in
  resume := (fun () ->
    next_box := None;
    try_with producer ()
      { effc = fun (type a) (eff : a Effect.t) ->
          match eff with
          | Yield v ->
              Some (fun (k : (a, _) continuation) ->
                next_box := Some v;
                (* 把"恢复点"塞回 resume，下一次调用 next 时再 continue *)
                resume := (fun () ->
                  next_box := None;
                  continue k ();
                  !next_box);
                ())
          | _ -> None
      };
    !next_box
  );

  fun () -> !resume ()

(* === 3. 试用：像 Python next(g) 那样按需取值 === *)
let () =
  let next = make_gen produce in
  let rec drain () =
    match next () with
    | Some v -> Printf.printf "got %d\n" v; drain ()
    | None   -> print_endline "generator 结束"
  in
  drain ()
