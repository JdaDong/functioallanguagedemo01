(* dune exec ./main.exe
   demo 05: exception vs result，何时用哪个
   - exception：少见的、调用方很难恢复的"灾难"，沿调用栈穿透
   - result：常见的、调用方应当显式处理的"业务失败"
   经验法则：能 result 就 result；只有真出意外才 exception
*)

(* 1. 用 exception 表示"参数本就不该这样"——调用方无力恢复 *)
exception Negative_input of int

let factorial n =
  if n < 0 then raise (Negative_input n);
  let rec loop acc n = if n = 0 then acc else loop (acc * n) (n - 1) in
  loop 1 n

(* 2. 用 result 表示"业务上完全可能失败"——调用方必须显式处理 *)
type div_error = Div_by_zero
let safe_div a b : (int, div_error) result =
  if b = 0 then Error Div_by_zero else Ok (a / b)

let () =
  (* exception 路线：try ... with 捕获 *)
  (try
     Printf.printf "5! = %d\n" (factorial 5);
     Printf.printf "(-1)! = %d\n" (factorial (-1))
   with Negative_input n ->
     Printf.printf "捕获到非法输入：%d\n" n);

  (* result 路线：模式匹配处理两种结局 *)
  (match safe_div 10 2 with
   | Ok q       -> Printf.printf "10/2 = %d\n" q
   | Error Div_by_zero -> print_endline "除零");
  (match safe_div 10 0 with
   | Ok q       -> Printf.printf "10/0 = %d\n" q
   | Error Div_by_zero -> print_endline "10/0 -> 除零（已显式处理）")
