(* dune exec ./main.exe
   demo 37: Hindley-Milner 类型推导（Algorithm W 简化版）
   - 这是 OCaml/Haskell/SML 类型系统的核心，1969 / 1978
   - 核心两步：
     1) 收集约束 + 合一（unify）：解出类型变量
     2) let 多态：函数定义处 generalize（∀），使用处 instantiate（取新副本）
   - occurs check 防止 α = α → β 这类无穷类型
   - 失败用例： λx. x x  ── 自我应用，类型 α = α → β 无解

   语法（同 demo 31，但加上 Bool 和原始函数）：
   e ::= n | b | x | λx.e | e e | let x = e in e | if e then e else e
*)

(* === 1. AST === *)
type expr =
  | EInt of int
  | EBool of bool
  | EVar of string
  | ELam of string * expr
  | EApp of expr * expr
  | ELet of string * expr * expr
  | EIf  of expr * expr * expr

(* === 2. 类型 === *)
type ty =
  | TInt
  | TBool
  | TVar of int            (* 类型变量编号 *)
  | TArr of ty * ty        (* a -> b *)

type scheme = Forall of int list * ty   (* ∀ vars. ty *)

(* === 3. 全局 fresh 变量计数器 === *)
let fresh_counter = ref 0
let fresh () = incr fresh_counter; TVar !fresh_counter

(* === 4. 替换：把变量映射到类型 === *)
module IntMap = Map.Make (Int)
type subst = ty IntMap.t

let rec apply (s : subst) (t : ty) : ty =
  match t with
  | TInt | TBool -> t
  | TVar n ->
      (match IntMap.find_opt n s with
       | Some t' -> apply s t'
       | None -> t)
  | TArr (a, b) -> TArr (apply s a, apply s b)

let apply_scheme s (Forall (vs, t)) =
  (* 不替换被 scheme 量化的变量 *)
  let s' = List.fold_left (fun m v -> IntMap.remove v m) s vs in
  Forall (vs, apply s' t)

let compose s1 s2 =
  (* (s1 ∘ s2) t = s1 (s2 t) *)
  let s2' = IntMap.map (apply s1) s2 in
  IntMap.union (fun _ a _ -> Some a) s1 s2'

(* === 5. occurs check === *)
let rec occurs n = function
  | TInt | TBool -> false
  | TVar m -> n = m
  | TArr (a, b) -> occurs n a || occurs n b

(* === 6. unify === *)
exception Unify_error of string

let rec unify (t1 : ty) (t2 : ty) : subst =
  match t1, t2 with
  | TInt, TInt | TBool, TBool -> IntMap.empty
  | TVar n, t | t, TVar n ->
      if t = TVar n then IntMap.empty
      else if occurs n t then
        raise (Unify_error
                 (Printf.sprintf "occurs check: α%d 出现在自身右边" n))
      else IntMap.singleton n t
  | TArr (a1, b1), TArr (a2, b2) ->
      let s1 = unify a1 a2 in
      let s2 = unify (apply s1 b1) (apply s1 b2) in
      compose s2 s1
  | _ ->
      raise (Unify_error
               (Printf.sprintf "无法合一: 两类型结构不同"))

(* === 7. 类型环境（变量 → scheme） === *)
type env = (string * scheme) list

let rec free_in_ty = function
  | TInt | TBool -> []
  | TVar n -> [n]
  | TArr (a, b) -> free_in_ty a @ free_in_ty b
let free_in_scheme (Forall (vs, t)) =
  List.filter (fun n -> not (List.mem n vs)) (free_in_ty t)
let free_in_env env =
  List.concat_map (fun (_, sc) -> free_in_scheme sc) env

let generalize (env : env) (t : ty) : scheme =
  let env_fv = free_in_env env in
  let vs = List.filter (fun n -> not (List.mem n env_fv)) (free_in_ty t) in
  let vs = List.sort_uniq compare vs in
  Forall (vs, t)

let instantiate (Forall (vs, t)) : ty =
  let s = List.fold_left (fun m v ->
    match fresh () with TVar n -> IntMap.add v (TVar n) m | _ -> assert false)
    IntMap.empty vs in
  apply s t

(* === 8. Algorithm W ── 推 (subst, ty) === *)
let rec infer (env : env) (e : expr) : subst * ty =
  match e with
  | EInt _ -> IntMap.empty, TInt
  | EBool _ -> IntMap.empty, TBool
  | EVar x ->
      (match List.assoc_opt x env with
       | Some sc -> IntMap.empty, instantiate sc
       | None -> raise (Unify_error ("unbound: " ^ x)))
  | ELam (x, body) ->
      let tv = fresh () in
      let env' = (x, Forall ([], tv)) :: env in
      let s1, t1 = infer env' body in
      s1, TArr (apply s1 tv, t1)
  | EApp (f, a) ->
      let s1, t1 = infer env f in
      let env' = List.map (fun (n, sc) -> n, apply_scheme s1 sc) env in
      let s2, t2 = infer env' a in
      let tv = fresh () in
      let s3 = unify (apply s2 t1) (TArr (t2, tv)) in
      compose s3 (compose s2 s1), apply s3 tv
  | ELet (x, e1, e2) ->
      let s1, t1 = infer env e1 in
      let env' = List.map (fun (n, sc) -> n, apply_scheme s1 sc) env in
      let sc = generalize env' t1 in
      let s2, t2 = infer ((x, sc) :: env') e2 in
      compose s2 s1, t2
  | EIf (c, t, e) ->
      let s1, tc = infer env c in
      let s2 = unify (apply s1 tc) TBool in
      let s12 = compose s2 s1 in
      let env' = List.map (fun (n, sc) -> n, apply_scheme s12 sc) env in
      let s3, tt = infer env' t in
      let s4, te = infer env' e in
      let s5 = unify (apply s4 tt) te in
      let s_all = compose s5 (compose s4 (compose s3 s12)) in
      s_all, apply s5 te

(* === 9. pretty print === *)
let rec pp_ty ?(s = IntMap.empty) t =
  match apply s t with
  | TInt -> "int"
  | TBool -> "bool"
  | TVar n -> Printf.sprintf "α%d" n
  | TArr (a, b) ->
      let l = match a with TArr _ -> Printf.sprintf "(%s)" (pp_ty ~s a)
                         | _ -> pp_ty ~s a in
      Printf.sprintf "%s -> %s" l (pp_ty ~s b)

let infer_top e =
  fresh_counter := 0;
  try
    let s, t = infer [] e in
    Printf.printf "  ✓ : %s\n" (pp_ty ~s t)
  with Unify_error msg ->
    Printf.printf "  ✗ %s\n" msg

(* === 10. 测试 === *)
let () =
  Printf.printf "── 1. λx. x   (恒等函数) ──\n";
  infer_top (ELam ("x", EVar "x"));    (* α -> α *)

  Printf.printf "── 2. λx. λy. x   (K 组合子) ──\n";
  infer_top (ELam ("x", ELam ("y", EVar "x")));   (* α -> β -> α *)

  Printf.printf "── 3. let id = λx. x in id 42   (let 多态) ──\n";
  infer_top (ELet ("id", ELam ("x", EVar "x"),
                   EApp (EVar "id", EInt 42)));    (* int *)

  Printf.printf "── 4. let id = λx. x in if id true then id 1 else id 2 (同一 id 用于两种类型) ──\n";
  infer_top (ELet ("id", ELam ("x", EVar "x"),
                   EIf (EApp (EVar "id", EBool true),
                        EApp (EVar "id", EInt 1),
                        EApp (EVar "id", EInt 2))));  (* int *)

  Printf.printf "── 5. λx. x x   (自我应用，应失败：occurs check) ──\n";
  infer_top (ELam ("x", EApp (EVar "x", EVar "x")));

  Printf.printf "── 6. if 1 then 2 else 3   (条件不是 bool，应失败) ──\n";
  infer_top (EIf (EInt 1, EInt 2, EInt 3))
