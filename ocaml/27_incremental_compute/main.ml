(* dune exec ./main.exe
   demo 27: Incremental ── Jane Street 的"增量计算"库
   - 思路：把"计算图"显式建出来，输入变了只重算"受影响的那部分"
   - Excel 的核心心智：单元格依赖图，A1 改了只重算依赖 A1 的格子
   - 对标 React.useMemo / Adapton / FRP 的"行为"概念
   - 工业用途：实时仪表盘、订单簿、风险计算

   API 三件套：
     Var.create      ── 创建一个可变输入
     Var.watch       ── 把它接入计算图作为节点
     map / map2 / bind ── 在节点上做派生
     Observer        ── "我要看这个节点的当前值"
     stabilize       ── 推一波传播
*)

open Core
module Inc = Incremental.Make ()

let () =
  (* === 1. 输入：两个数 === *)
  let a_var = Inc.Var.create 1 in
  let b_var = Inc.Var.create 10 in

  (* === 2. 派生：c = a + b，d = c * 2 === *)
  let a = Inc.Var.watch a_var in
  let b = Inc.Var.watch b_var in
  let c = Inc.map2 a b ~f:(fun x y ->
    printf "  [recompute c]\n";
    x + y) in
  let d = Inc.map c ~f:(fun x ->
    printf "  [recompute d]\n";
    x * 2) in

  (* === 3. 观察：要拿到值就得有 observer === *)
  let obs_d = Inc.observe d in

  let show () =
    Inc.stabilize ();
    printf "a=%d b=%d → d=%d\n"
      (Inc.Var.value a_var)
      (Inc.Var.value b_var)
      (Inc.Observer.value_exn obs_d)
  in

  printf "── 初次稳定 ──\n";
  show ();

  printf "\n── 改 a：1 → 5（应只重算 c 和 d）──\n";
  Inc.Var.set a_var 5;
  show ();

  printf "\n── 不改任何输入再 stabilize（不应有任何 recompute）──\n";
  show ();

  printf "\n── 改 b：10 → 100 ──\n";
  Inc.Var.set b_var 100;
  show ()
