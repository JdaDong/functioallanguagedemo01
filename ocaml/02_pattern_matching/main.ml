(* dune exec ./main.exe
   demo 02: pattern matching 的三种进阶用法
   - 深度嵌套模式（解构嵌套结构）
   - when 守卫（在模式后追加布尔条件）
   - as 绑定（在模式中给子结构起名）
*)

type point = { x : int; y : int }
type shape =
  | Circle of point * int       (* 圆心 + 半径 *)
  | Rect of point * point        (* 左下 + 右上 *)
  | Triangle of point * point * point

(* 1. 深度嵌套：直接解到字段 *)
let origin_circle = function
  | Circle ({ x = 0; y = 0 }, r) -> Printf.sprintf "原点圆，半径=%d" r
  | Circle (_, r)                -> Printf.sprintf "非原点圆，半径=%d" r
  | _                            -> "非圆"

(* 2. when 守卫：相同模式靠条件区分 *)
let classify_rect = function
  | Rect ({ x = x1; y = y1 }, { x = x2; y = y2 })
    when x2 - x1 = y2 - y1 -> "正方形"
  | Rect _                 -> "长方形"
  | _                      -> "非矩形"

(* 3. as 绑定：保留整体的同时拿到部件 *)
let describe = function
  | Circle (_, r) as c when r > 100 ->
      let name = match c with Circle _ -> "巨圆" | _ -> "?" in
      Printf.sprintf "%s（半径 %d）" name r
  | _ -> "其它形状"

let () =
  print_endline (origin_circle (Circle ({ x = 0; y = 0 }, 5)));
  print_endline (origin_circle (Circle ({ x = 1; y = 2 }, 5)));
  print_endline (classify_rect (Rect ({x=0;y=0}, {x=10;y=10})));
  print_endline (classify_rect (Rect ({x=0;y=0}, {x=10;y=5})));
  print_endline (describe (Circle ({x=0;y=0}, 200)));
  print_endline (describe (Triangle ({x=0;y=0}, {x=1;y=0}, {x=0;y=1})))
