(* dune exec ./main.exe
   demo 19: Atomic —— OCaml 5 的无锁原语
   - 多 Domain 同时改一个普通 ref，会丢更新（race condition）
   - Atomic.t 提供 fetch_and_add / compare_and_set 等硬件级原子操作
   - 用它能写 lock-free counter / stack / queue
*)

(* === 1. 演示：4 个 Domain 各加 100_000 次，期望最终 = 400_000 === *)
let n_domains = 4
let n_per_domain = 100_000

(* 用普通 ref：会丢更新 *)
let test_with_ref () =
  let r = ref 0 in
  let domains = Array.init n_domains (fun _ ->
    Domain.spawn (fun () ->
      for _ = 1 to n_per_domain do
        r := !r + 1            (* 不是原子的！读、加、写三步会被打断 *)
      done))
  in
  Array.iter Domain.join domains;
  !r

(* 用 Atomic：每次 +1 是原子的，结果一定准确 *)
let test_with_atomic () =
  let a = Atomic.make 0 in
  let domains = Array.init n_domains (fun _ ->
    Domain.spawn (fun () ->
      for _ = 1 to n_per_domain do
        Atomic.incr a
      done))
  in
  Array.iter Domain.join domains;
  Atomic.get a

(* === 2. CAS（compare-and-set）演示：lock-free 累加器 === *)
(* 哪怕没有 Atomic.incr，用 CAS 自己也能写一个 *)
let cas_increment a =
  let rec loop () =
    let old_v = Atomic.get a in
    if Atomic.compare_and_set a old_v (old_v + 1)
    then ()                    (* 成功，结束 *)
    else loop ()               (* 别人抢先改了，重读重试 *)
  in
  loop ()

let test_with_cas () =
  let a = Atomic.make 0 in
  let domains = Array.init n_domains (fun _ ->
    Domain.spawn (fun () ->
      for _ = 1 to n_per_domain do
        cas_increment a
      done))
  in
  Array.iter Domain.join domains;
  Atomic.get a

let () =
  let expected = n_domains * n_per_domain in
  Printf.printf "期望值: %d\n" expected;

  let r1 = test_with_ref () in
  Printf.printf "普通 ref     : %d  (丢失 %d)\n" r1 (expected - r1);

  let r2 = test_with_atomic () in
  Printf.printf "Atomic.incr  : %d  %s\n" r2
    (if r2 = expected then "✓" else "✗");

  let r3 = test_with_cas () in
  Printf.printf "手写 CAS 累加: %d  %s\n" r3
    (if r3 = expected then "✓" else "✗")
