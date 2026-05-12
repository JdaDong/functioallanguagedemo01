(* dune exec ./main.exe
   demo 33: 自动微分 ── 神经网络的"链式法则机器"
   - 前向模式（dual number）：v = (value, deriv) ── 适合 R^1 → R^N
   - 反向模式（reverse mode / backprop）：建计算图 → 反向传梯度 ── 适合 R^N → R
   - PyTorch / TensorFlow / JAX 的 .backward() 就是反向模式
   - 验证函数：f(x) = x² + sin(x)，f'(x) = 2x + cos(x)
     在 x=1：f(1) = 1 + sin(1) ≈ 1.8415
              f'(1) = 2 + cos(1) ≈ 2.5403
*)

(* ============================================================ *)
(* 1. 前向模式：dual number = (value, derivative)                *)
(* ============================================================ *)

module Forward = struct
  type t = { v : float; d : float }

  let var x = { v = x; d = 1.0 }   (* 对该变量求导：d=1 *)

  let ( +! ) a b = { v = a.v +. b.v; d = a.d +. b.d }
  let ( *! ) a b = {
    v = a.v *. b.v;
    d = a.d *. b.v +. a.v *. b.d;     (* 乘法链式法则 *)
  }
  let sin a = { v = Stdlib.sin a.v;  d = Stdlib.cos a.v *. a.d }
end

(* ============================================================ *)
(* 2. 反向模式：建计算图，反向传 gradient                         *)
(* ============================================================ *)

module Reverse = struct
  (* 节点 = 当前值 + 累积梯度 + "上游链" 即如何把自己的梯度往父节点回灌 *)
  type node = {
    mutable value : float;
    mutable grad  : float;
    backprop      : unit -> unit;   (* 调用一次就把 grad 推回上游 *)
  }

  let leaf v = {
    value = v;
    grad = 0.0;
    backprop = (fun () -> ());      (* 叶子无上游 *)
  }

  let add a b =
    let out = {
      value = a.value +. b.value;
      grad = 0.0;
      backprop = (fun () ->
        (* 由本节点已知的 grad 推 *)
        a.grad <- a.grad +. 1.0;
        b.grad <- b.grad +. 1.0);
    } in
    (* 注意：这里我们简化为"全图 backward 一次性按拓扑序跑"——下面 backward 实现会处理 *)
    out, [a; b]

  (* 上面的写法只对 a/b 是叶子有用；为了支持任意深度的图，我们改成一个"tape"：
     每次创建节点把它和 closure (this -> parent gradients) 推入 tape。 *)

  type tape_entry = { out : node; back : unit -> unit }
  let tape : tape_entry list ref = ref []

  let push_tape entry = tape := entry :: !tape

  let make_op value back =
    let out = { value; grad = 0.0; backprop = (fun () -> ()) } in
    push_tape { out; back = (fun () -> back out) };
    out

  (* float 运算别名（普通函数）—— 接着我们要 shadow 掉 +. / *. *)
  let fadd = Stdlib.( +. )
  let fmul = Stdlib.( *. )

  (* 重新定义运算（对外 +. / *. 接收 node） *)
  let ( +. ) a b =
    make_op (fadd a.value b.value)
      (fun out ->
        a.grad <- fadd a.grad out.grad;
        b.grad <- fadd b.grad out.grad)

  let ( *. ) a b =
    make_op (fmul a.value b.value)
      (fun out ->
        a.grad <- fadd a.grad (fmul out.grad b.value);
        b.grad <- fadd b.grad (fmul out.grad a.value))

  let sin a =
    make_op (Stdlib.sin a.value)
      (fun out -> a.grad <- fadd a.grad (fmul out.grad (Stdlib.cos a.value)))

  (* 把上面那个"双 arity add"丢掉，避免误导 *)
  let _ = add

  (* backward：root 的梯度设 1，按 tape 逆序传播 *)
  let backward root =
    root.grad <- 1.0;
    List.iter (fun e -> e.back ()) !tape;
    tape := []   (* 一次 backward 后清空 tape *)
end

(* ============================================================ *)
(* 3. 测试：f(x) = x² + sin(x)                                  *)
(* ============================================================ *)

let () =
  Printf.printf "── 解析解（参考）──\n";
  let x0 = 1.0 in
  Printf.printf "  f(1)  = 1 + sin(1) = %.6f\n"  (1.0 +. Stdlib.sin x0);
  Printf.printf "  f'(1) = 2 + cos(1) = %.6f\n"  (2.0 +. Stdlib.cos x0);

  Printf.printf "\n── 前向模式（dual number）──\n";
  let open Forward in
  let x = var 1.0 in
  let f = (x *! x) +! (sin x) in
  Printf.printf "  f.v = %.6f\n" f.v;
  Printf.printf "  f.d = %.6f   (即 f'(1))\n" f.d;

  Printf.printf "\n── 反向模式（reverse mode）──\n";
  let open Reverse in
  let x = leaf 1.0 in
  let f = (x *. x) +. sin x in
  Printf.printf "  forward: f = %.6f\n" f.value;
  backward f;
  Printf.printf "  backward → x.grad = %.6f   (即 f'(1))\n" x.grad
