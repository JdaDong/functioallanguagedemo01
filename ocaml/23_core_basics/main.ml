(* dune exec ./main.exe
   demo 23: Jane Street Core 库——stdlib 的工业版
   - Map / Set / List 都比 stdlib 的 API 设计更现代
   - Option 有更多组合子（value_exn / value_map / Option.bind）
   - String/Int/Float 等模块自带 comparable / hashable / sexp
   - Sexp 序列化 + [@@deriving sexp] 是日常工作流
   注意：core 会 shadow 大量 stdlib 函数（如 [List.map] 改成 [List.map ~f:...]）
*)

open Core

(* === 1. Map：用 Int 作 key 立刻得到完整 API === *)
let demo_map () =
  let m =
    Map.empty (module Int)
    |> Map.set ~key:1 ~data:"one"
    |> Map.set ~key:2 ~data:"two"
    |> Map.set ~key:3 ~data:"three"
  in
  printf "map size = %d\n" (Map.length m);
  printf "find 2   = %s\n" (Map.find_exn m 2);
  Map.iteri m ~f:(fun ~key ~data -> printf "  [%d] %s\n" key data)

(* === 2. List：参数全部用 labeled，可读性远超 stdlib === *)
let demo_list () =
  let xs = [3; 1; 4; 1; 5; 9; 2; 6] in
  let sorted = List.sort xs ~compare:Int.compare in
  let dedup  = List.dedup_and_sort xs ~compare:Int.compare in
  let sum    = List.sum (module Int) xs ~f:Fn.id in
  printf "sorted = %s\n"
    (List.to_string sorted ~f:Int.to_string);
  printf "dedup  = %s\n"
    (List.to_string dedup ~f:Int.to_string);
  printf "sum    = %d\n" sum

(* === 3. Option.value_map / Option.bind 工业级链式 === *)
let demo_option () =
  let safe_div a b = if b = 0 then None else Some (a / b) in
  let r =
    safe_div 100 5
    |> Option.bind ~f:(fun x -> safe_div x 2)
    |> Option.value_map ~default:(-1) ~f:Fn.id
  in
  printf "100/5/2 = %d\n" r;
  let r2 = safe_div 100 0 |> Option.value ~default:(-999) in
  printf "100/0   = %d (default)\n" r2

(* === 4. [@@deriving sexp] —— Core 风格的"自动序列化"，PPX 帮你生成 of_sexp / sexp_of *)
type point = { x : int; y : int } [@@deriving sexp]

let demo_sexp () =
  let p = { x = 3; y = 4 } in
  let s = sexp_of_point p in
  printf "sexp_of_point = %s\n" (Sexp.to_string s);
  let p2 = point_of_sexp (Sexp.of_string "((x 10) (y 20))") in
  printf "round-trip    = (%d,%d)\n" p2.x p2.y

let () =
  printf "── Map ──\n";  demo_map ();
  printf "── List ──\n"; demo_list ();
  printf "── Option ──\n"; demo_option ();
  printf "── [@@deriving sexp] ──\n"; demo_sexp ()
