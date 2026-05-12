(* dune runtest 29_expect_tests
   demo 29: ppx_expect ── 快照测试
   - 用 [%expect {| ... |}] 直接把"期望输出"内联进测试代码
   - 跑测试时如果实际输出和期望不一致，dune 会显示 diff
   - 接受新输出：dune promote（或 dune runtest --auto-promote）
   - 比传统 assertEquals 写得更"自然语言"，工业 OCaml 主力测试范式
*)

open Core

(* === 测试 1：纯函数 === *)
let%expect_test "List.sort 升序" =
  let sorted = List.sort [3; 1; 4; 1; 5; 9; 2; 6] ~compare:Int.compare in
  print_s [%sexp (sorted : int list)];
  [%expect {| (1 1 2 3 4 5 6 9) |}]

(* === 测试 2：副作用 / 多行输出 === *)
let%expect_test "fizzbuzz 1..5" =
  for i = 1 to 5 do
    let s =
      match i mod 3, i mod 5 with
      | 0, 0 -> "FizzBuzz"
      | 0, _ -> "Fizz"
      | _, 0 -> "Buzz"
      | _    -> Int.to_string i
    in
    print_endline s
  done;
  [%expect {|
    1
    2
    Fizz
    4
    Buzz
    |}]

(* === 测试 3：异常 === *)
let%expect_test "除以 0 抛 Division_by_zero" =
  (try
     let _ = 10 / 0 in ()
   with e -> printf "caught: %s\n" (Exn.to_string e));
  [%expect {| caught: (Division_by_zero) |}]
