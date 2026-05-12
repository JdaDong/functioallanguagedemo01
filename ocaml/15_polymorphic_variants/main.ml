(* dune exec ./main.exe
   demo 15: polymorphic variants（多态变体）
   - 写法：`Tag 或 `Tag of value，开头一个反引号
   - 不需要事先 type 声明，可以现用现写
   - 类型 [> ...] 表示"至少包含这些 tag"，是开放的
   - 类型 [< ...] 表示"至多包含这些 tag"，是闭合的
   - 工业用途：跨模块共享 tag、不强制把所有 case 集中到一个 type 声明
*)

(* === 1. 不需要 type 声明，直接用 === *)
let stoplight_color = `Red             (* 推断类型: [> `Red ] *)

(* === 2. 模式匹配：类型自动收紧到匹配里写出的 tag === *)
let to_hex = function
  | `Red    -> "#FF0000"
  | `Green  -> "#00FF00"
  | `Blue   -> "#0000FF"
  | `RGB (r, g, b) -> Printf.sprintf "#%02X%02X%02X" r g b

(* === 3. 开放性：函数 A 接受的 tag 集合可以是函数 B 的子集 === *)

(* 只处理基础三色，不认识 RGB 也不认识别的 *)
let basic_name = function
  | `Red   -> "红"
  | `Green -> "绿"
  | `Blue  -> "蓝"

(* 调用 basic_name 时，传入的值类型只需要"包含 Red/Green/Blue 中的一个"
   不需要事先在某个 type 里把 Red Green Blue 一起声明。 *)

(* === 4. 用 [> ...] 标注让函数明确"接受任何包含这几个 tag 的 variant" === *)
let describe (c : [> `Red | `Green | `Blue ]) =
  match c with
  | `Red   -> "warning"
  | `Green -> "ok"
  | `Blue  -> "info"
  | _      -> "unknown"

(* === 5. 试用 === *)
let () =
  Printf.printf "stoplight = %s\n" (to_hex stoplight_color);
  Printf.printf "rgb       = %s\n" (to_hex (`RGB (10, 200, 30)));
  Printf.printf "basic Red = %s\n" (basic_name `Red);
  Printf.printf "describe Green = %s\n" (describe `Green);
  Printf.printf "describe Yellow = %s\n" (describe `Yellow);  (* 命中通配 _ *)

  (* 一个 list 里混着不同 tag 也没问题——类型自动是 [> `A | `B | `C ] *)
  let xs = [`A; `B 42; `C "hi"] in
  List.iter (function
    | `A   -> print_endline "got A"
    | `B n -> Printf.printf "got B %d\n" n
    | `C s -> Printf.printf "got C %s\n" s
  ) xs
