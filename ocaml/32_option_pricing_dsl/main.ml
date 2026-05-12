(* dune exec ./main.exe
   demo 32: 期权定价 DSL ── Black-Scholes 闭式解 + functor 组合
   - 期权 = 一个数据结构 (option_kind + 参数)
   - 定价器 = 一个函数 option -> price
   - 用 module type 抽象定价模型；BS 是其中一种实现
   - 业务上还有蒙特卡洛、二叉树等模型，可以无缝替换
   - 参考基准：S=100,K=100,r=5%,σ=20%,T=1 → Call≈10.4506 Put≈5.5735
*)

(* === 1. 标准正态分布 CDF：Abramowitz-Stegun 近似 === *)
let erf x =
  let a1 =  0.254829592 in
  let a2 = -0.284496736 in
  let a3 =  1.421413741 in
  let a4 = -1.453152027 in
  let a5 =  1.061405429 in
  let p  =  0.3275911 in
  let sign = if x < 0.0 then -1.0 else 1.0 in
  let x = abs_float x in
  let t = 1.0 /. (1.0 +. p *. x) in
  let y = 1.0 -. (((((a5 *. t +. a4) *. t) +. a3) *. t +. a2) *. t +. a1) *. t *. exp (-. x *. x) in
  sign *. y

let cdf_normal x = 0.5 *. (1.0 +. erf (x /. sqrt 2.0))

(* === 2. 期权数据结构 === *)
type kind = Call | Put

type option_t = {
  kind  : kind;
  s     : float;   (* 标的现价 *)
  k     : float;   (* 行权价 *)
  r     : float;   (* 无风险利率 *)
  sigma : float;   (* 波动率 *)
  t     : float;   (* 到期年化 *)
}

(* === 3. 定价器接口（module type）=== *)
module type Pricer = sig
  val name  : string
  val price : option_t -> float
end

(* === 4. Black-Scholes 实现 === *)
module BlackScholes : Pricer = struct
  let name = "Black-Scholes"
  let price o =
    let { kind; s; k; r; sigma; t } = o in
    let d1 = (log (s /. k) +. (r +. 0.5 *. sigma *. sigma) *. t)
             /. (sigma *. sqrt t) in
    let d2 = d1 -. sigma *. sqrt t in
    match kind with
    | Call -> s *. cdf_normal d1 -. k *. exp (-. r *. t) *. cdf_normal d2
    | Put  -> k *. exp (-. r *. t) *. cdf_normal (-. d2)
              -. s *. cdf_normal (-. d1)
end

(* === 5. 蒙特卡洛实现（演示同接口可替换）=== *)
module MonteCarlo : Pricer = struct
  let name = "Monte-Carlo (10w paths)"

  (* Box-Muller 生成标准正态 *)
  let std_normal () =
    let u1 = Random.float 1.0 in
    let u2 = Random.float 1.0 in
    sqrt (-2.0 *. log u1) *. cos (2.0 *. Float.pi *. u2)

  let price o =
    let { kind; s; k; r; sigma; t } = o in
    let n = 100_000 in
    let payoff_sum = ref 0.0 in
    for _ = 1 to n do
      let z = std_normal () in
      let st = s *. exp ((r -. 0.5 *. sigma *. sigma) *. t
                        +. sigma *. sqrt t *. z) in
      let payoff = match kind with
        | Call -> max (st -. k) 0.0
        | Put  -> max (k -. st) 0.0
      in
      payoff_sum := !payoff_sum +. payoff
    done;
    exp (-. r *. t) *. (!payoff_sum /. float_of_int n)
end

(* === 6. functor：组装一个"打印报告"的高阶模块 === *)
module Make_reporter (P : Pricer) = struct
  let report o =
    let p = P.price o in
    let kind_s = match o.kind with Call -> "Call" | Put -> "Put" in
    Printf.printf "  [%s] %s(S=%.0f K=%.0f σ=%.0f%% T=%.0f) = %.4f\n"
      P.name kind_s o.s o.k (o.sigma *. 100.) o.t p
end

(* === 7. 跑两组报告 === *)
let () =
  let module R_BS = Make_reporter (BlackScholes) in
  let module R_MC = Make_reporter (MonteCarlo) in

  let opt_call = { kind=Call; s=100.; k=100.; r=0.05; sigma=0.20; t=1.0 } in
  let opt_put  = { opt_call with kind=Put } in

  print_endline "── 闭式解 ──";
  R_BS.report opt_call;     (* 期望 ~10.4506 *)
  R_BS.report opt_put;      (* 期望 ~5.5735  *)

  print_endline "── 蒙特卡洛（同接口替换）──";
  Random.self_init ();
  R_MC.report opt_call;
  R_MC.report opt_put;

  (* Put-Call 平价：C - P = S - K*e^(-rT) *)
  let c = BlackScholes.price opt_call in
  let p = BlackScholes.price opt_put in
  let lhs = c -. p in
  let rhs = opt_call.s -. opt_call.k *. exp (-. opt_call.r *. opt_call.t) in
  Printf.printf "── put-call 平价校验 ──\n";
  Printf.printf "  C - P     = %.6f\n" lhs;
  Printf.printf "  S - Ke^-rT = %.6f\n" rhs;
  Printf.printf "  误差       = %.2e\n" (abs_float (lhs -. rhs))
