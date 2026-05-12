(* dune exec ./main.exe
   demo 30: ppx_jane ── PPX 预处理器扩展（OCaml 的"派生宏"）
   - 在类型后加 [@@deriving sexp, equal, compare, hash, ...]，自动生成对应函数
   - 类比 Haskell 的 deriving Show / Rust 的 #[derive(Debug, Clone)]
   - PPX 是编译期 AST 改写，性能零成本
   - ppx_jane 是 Jane Street 全套的"meta package"
*)

open Core

(* === 1. 一次派生四个方法 === *)
type color =
  | Red
  | Green
  | Blue
  | RGB of int * int * int
[@@deriving sexp, equal, compare, hash]

(* === 2. 嵌套 record 同样自动派生 === *)
type point = { x : int; y : int }
[@@deriving sexp, equal, compare, hash]

type shape =
  | Circle of point * int
  | Rect of point * point
[@@deriving sexp, equal, compare, hash]

let () =
  (* sexp ── 自动序列化 *)
  printf "── sexp ──\n";
  printf "  Red          = %s\n" (Sexp.to_string (sexp_of_color Red));
  printf "  RGB(1,2,3)   = %s\n" (Sexp.to_string (sexp_of_color (RGB (1,2,3))));
  printf "  Circle((0,0),5) = %s\n"
    (Sexp.to_string (sexp_of_shape (Circle ({x=0;y=0}, 5))));

  (* equal ── 结构相等 *)
  printf "\n── equal ──\n";
  printf "  Red = Red?  %b\n" (equal_color Red Red);
  printf "  RGB(1,2,3) = RGB(1,2,3)? %b\n"
    (equal_color (RGB (1,2,3)) (RGB (1,2,3)));
  printf "  RGB(1,2,3) = Red?         %b\n"
    (equal_color (RGB (1,2,3)) Red);

  (* compare ── 派生的字典序 *)
  printf "\n── compare ──\n";
  let xs = [Blue; Red; RGB (1,2,3); Green] in
  let sorted = List.sort xs ~compare:compare_color in
  List.iter sorted ~f:(fun c ->
    printf "  %s\n" (Sexp.to_string (sexp_of_color c)));

  (* hash ── 派生的 hash，可直接做 Hashtbl key === *)
  printf "\n── hash ──\n";
  printf "  hash Red          = %d\n" (hash_color Red);
  printf "  hash RGB(1,2,3)   = %d\n" (hash_color (RGB (1,2,3)));

  (* === 3. inline syntax [%equal: t] ── 不用记派生函数名 === *)
  printf "\n── inline syntax ──\n";
  printf "  [%%equal: color] Red Red = %b\n"
    ([%equal: color] Red Red);
  printf "  [%%compare: point] {1,2} {3,4} = %d\n"
    ([%compare: point] {x=1;y=2} {x=3;y=4})
