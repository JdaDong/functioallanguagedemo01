(* dune exec ./main.exe
   demo 07: ML 的可变一面
   - ref：单格可变盒子（counter := !counter + 1）
   - mutable field：record 内的可变字段
   - Array：定长可变数组，O(1) 随机访问
   OCaml 不强制纯，可变是一等公民——但要节制。
*)

(* 1. ref 是可变盒子；! 取值；:= 赋值 *)
let counter = ref 0
let bump () = counter := !counter + 1

(* 2. record 内 mutable field *)
type stats = { mutable hits : int; mutable misses : int }
let s = { hits = 0; misses = 0 }
let record_hit ()  = s.hits   <- s.hits + 1
let record_miss () = s.misses <- s.misses + 1

(* 3. Array：定长，O(1) 取/写 *)
let demo_array () =
  let a = Array.make 5 0 in
  for i = 0 to Array.length a - 1 do
    a.(i) <- i * i
  done;
  Array.iter (Printf.printf "%d ") a;
  print_newline ()

let () =
  bump (); bump (); bump ();
  Printf.printf "counter = %d\n" !counter;

  record_hit (); record_hit (); record_miss ();
  Printf.printf "hits=%d misses=%d\n" s.hits s.misses;

  demo_array ()
