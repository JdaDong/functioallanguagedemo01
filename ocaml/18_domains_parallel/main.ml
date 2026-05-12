(* dune exec ./main.exe
   demo 18: Domain —— OCaml 5 的多核并行
   - OCaml 4 时代有 GIL，多线程不能真并行
   - OCaml 5 引入 Domain：每个 Domain 是一个真正的 OS 线程，能跑在不同 CPU 核上
   - Domain.spawn f → 启动；Domain.join d → 等结果回来
   - 适合 CPU 密集型任务（IO 密集型用 effects/Eio 更合适）
*)

(* 一个故意慢一点的纯计算：算 [lo..hi] 区间内的平方和 *)
let sum_of_squares lo hi =
  let s = ref 0 in
  for i = lo to hi do
    s := !s + i * i
  done;
  !s

let () =
  let n = 10_000_000 in
  let mid = n / 2 in

  (* === 单核 baseline === *)
  let t0 = Unix.gettimeofday () in
  let r_seq = sum_of_squares 1 n in
  let t1 = Unix.gettimeofday () in
  Printf.printf "单核 : sum=%d  耗时=%.3fs\n" r_seq (t1 -. t0);

  (* === 双 Domain 并行：把任务切成两半，分给两个核 === *)
  let t2 = Unix.gettimeofday () in
  let d1 = Domain.spawn (fun () -> sum_of_squares 1 mid) in
  let d2 = Domain.spawn (fun () -> sum_of_squares (mid + 1) n) in
  let r_par = Domain.join d1 + Domain.join d2 in
  let t3 = Unix.gettimeofday () in
  Printf.printf "双核 : sum=%d  耗时=%.3fs\n" r_par (t3 -. t2);

  (* 正确性检查 *)
  if r_seq = r_par
  then print_endline "✓ 两种算法结果一致"
  else print_endline "✗ 结果不一致！"
