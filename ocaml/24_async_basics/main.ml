(* dune exec ./main.exe
   demo 24: Async ── Jane Street 的并发框架
   - 'a Deferred.t = "未来某时刻产出 'a"，等价 JS Promise/Future
   - >>= / >>| / let%bind 让你像写同步代码那样写异步
   - Ivar = 一次性可填的"信号量"，await + signal 的标准用法
   - Async 跑在自己的 scheduler 上，main 末尾要 Scheduler.go 或 ()_exn never_returns
*)

open Core
open Async

(* === 1. 最简单的 Deferred：返回值"延迟" === *)
let delay_value v ~ms =
  let%bind () = after (Time_float.Span.of_ms (float_of_int ms)) in
  return v

(* === 2. 串行 (>>=) vs 并行 (Deferred.both) === *)
let demo_serial () =
  let%bind a = delay_value 10 ~ms:50 in
  let%bind b = delay_value 20 ~ms:50 in
  printf "serial: a+b = %d\n" (a + b);
  return ()

let demo_parallel () =
  let%bind (a, b) =
    Deferred.both
      (delay_value 100 ~ms:50)
      (delay_value 200 ~ms:50)
  in
  printf "parallel: a+b = %d\n" (a + b);
  return ()

(* === 3. Ivar：一次性写入的"未来变量" === *)
let demo_ivar () =
  let iv = Ivar.create () in
  (* 50ms 后填 Ivar *)
  upon (after (Time_float.Span.of_ms 30.))
    (fun () -> Ivar.fill_exn iv "hello from upon");
  (* 等它被填上 *)
  let%bind v = Ivar.read iv in
  printf "ivar got: %s\n" v;
  return ()

let main () =
  let t0 = Time_float.now () in
  let%bind () = demo_serial () in
  let t1 = Time_float.now () in
  printf "  (serial 耗时 ~%dms)\n"
    (Float.to_int (Time_float.Span.to_ms (Time_float.diff t1 t0)));

  let t2 = Time_float.now () in
  let%bind () = demo_parallel () in
  let t3 = Time_float.now () in
  printf "  (parallel 耗时 ~%dms)\n"
    (Float.to_int (Time_float.Span.to_ms (Time_float.diff t3 t2)));

  let%bind () = demo_ivar () in
  shutdown 0;
  return ()

let () =
  don't_wait_for (main ());
  never_returns (Scheduler.go ())
