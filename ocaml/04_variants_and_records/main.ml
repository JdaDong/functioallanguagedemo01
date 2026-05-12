(* dune exec ./main.exe
   demo 04: variant + record
   - 标签型 variant（带名字的构造子）
   - record 字段更新语法 { r with ... }
   - 可变字段 mutable
*)

(* variant：表达"几种之一" *)
type color = Red | Green | Blue | RGB of int * int * int

(* record：表达"全都有"，字段名即文档 *)
type person = {
  name : string;
  age : int;
  mutable score : int;   (* 可变字段：少数允许就地修改的口子 *)
}

let color_to_hex = function
  | Red          -> "#FF0000"
  | Green        -> "#00FF00"
  | Blue         -> "#0000FF"
  | RGB (r,g,b)  -> Printf.sprintf "#%02X%02X%02X" r g b

let () =
  let p = { name = "张三"; age = 30; score = 60 } in
  Printf.printf "%s 当前 %d 分\n" p.name p.score;

  (* { r with ... } 不是修改，是产生新 record（age+1） *)
  let p2 = { p with age = p.age + 1 } in
  Printf.printf "明年 %s %d 岁\n" p2.name p2.age;

  (* mutable 字段才能就地改 *)
  p.score <- 100;
  Printf.printf "%s 涨到 %d 分\n" p.name p.score;

  print_endline (color_to_hex Red);
  print_endline (color_to_hex Green);
  print_endline (color_to_hex Blue);
  print_endline (color_to_hex (RGB (10, 200, 30)))
