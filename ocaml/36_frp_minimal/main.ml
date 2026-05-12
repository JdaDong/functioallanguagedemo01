(* dune exec ./main.exe
   demo 36: 极简 FRP（Functional Reactive Programming）── 信号 + 自动传播
   - signal = "随时间变化的值"（Conal Elliott 1997）
   - 三件套: source（输入）/ map / map2（派生）/ subscribe（监听）
   - 核心实现思路：每个 signal 维护一份订阅者列表，set 时通知下游
   - demo 27 Incremental 是"按需重算"模型；这里是"推式 push" FRP 模型
   两种心智都见识一下，工业上 ReactiveX/RxJS 走 push、Incremental 走 pull
   注意：本 demo 改 qty 会触发 2 次 watch（subtotal→tax 一次、subtotal→total 一次），
         这是 push FRP 的"glitch"现象。Incremental（demo 27）的 stabilize 模型可避免。
*)

(* === 一个 signal 内部就是 mutable 的 cell + 订阅者函数列表 === *)
type 'a signal = {
  mutable value     : 'a;
  mutable listeners : ('a -> unit) list;
}

let make v = { value = v; listeners = [] }

let get s = s.value

let subscribe s f =
  s.listeners <- f :: s.listeners

(* set 触发 push：写入新值 + 通知订阅者 *)
let set s v =
  s.value <- v;
  List.iter (fun f -> f v) s.listeners

(* === 派生：map ── 输入变就推到输出 === *)
let map (s : 'a signal) ~(f : 'a -> 'b) : 'b signal =
  let out = make (f s.value) in
  subscribe s (fun new_v -> set out (f new_v));
  out

(* === 派生：map2 ── 两路输入合并 === *)
let map2 (s1 : 'a signal) (s2 : 'b signal) ~(f : 'a -> 'b -> 'c) : 'c signal =
  let out = make (f s1.value s2.value) in
  subscribe s1 (fun v1 -> set out (f v1 s2.value));
  subscribe s2 (fun v2 -> set out (f s1.value v2));
  out

(* === 演示场景：购物车 ============================ *)
(*  price (单价) ─┐
                  ├── subtotal = price * qty ─┐
   qty (数量)    ─┘                            ├── total = subtotal + tax
                  tax_rate ────────────────────┘ *)

let () =
  (* 输入信号 *)
  let price    = make 100.0 in
  let qty      = make 1 in
  let tax_rate = make 0.13 in

  (* 派生信号 *)
  let subtotal = map2 price qty ~f:(fun p q -> p *. float_of_int q) in
  let tax      = map2 subtotal tax_rate ~f:( *. ) in
  let total    = map2 subtotal tax ~f:(+.) in
  (* 单输入派生：把 total 格式化成字符串（演示 map 单参版本） *)
  let total_str = map total ~f:(Printf.sprintf "¥%.2f") in

  (* 监听总价 ── 像 React/Vue 的"effect"或"watch" *)
  subscribe total (fun t ->
    Printf.printf "  [watch] total → %.2f\n" t);
  subscribe total_str (fun s ->
    Printf.printf "  [watch] total_str → %s\n" s);

  Printf.printf "── 初始状态 ──\n";
  Printf.printf "  price=%.2f qty=%d tax_rate=%.2f → subtotal=%.2f tax=%.2f total=%.2f\n"
    (get price) (get qty) (get tax_rate)
    (get subtotal) (get tax) (get total);

  Printf.printf "\n── 改 qty: 1 → 3 ──\n";
  set qty 3;
  Printf.printf "  当前 total=%.2f\n" (get total);

  Printf.printf "\n── 改 price: 100 → 50 ──\n";
  set price 50.0;
  Printf.printf "  当前 total=%.2f\n" (get total);

  Printf.printf "\n── 改 tax_rate: 0.13 → 0.05 ──\n";
  set tax_rate 0.05;
  Printf.printf "  当前 total=%.2f\n" (get total)
