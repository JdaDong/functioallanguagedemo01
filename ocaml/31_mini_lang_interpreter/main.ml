(* dune exec ./main.exe
   demo 31: mini_lang ── 简化 lambda 演算解释器（无类型，只看求值）
   - 语法：变量 / 整数 / 加减乘 / lambda / 应用 / let / if=0
   - 求值：环境模型（Map<string, value>）+ 闭包
   - 教学卖点：闭包正确捕获 enclosing scope，递归靠 let rec
   类型推导留给 demo 37（HM 推导专题）
*)

(* === AST === *)
type expr =
  | Int    of int
  | Var    of string
  | Add    of expr * expr
  | Mul    of expr * expr
  | Lam    of string * expr            (* λx. e *)
  | App    of expr * expr              (* e1 e2 *)
  | Let    of string * expr * expr     (* let x = e1 in e2 *)
  | LetRec of string * string * expr * expr  (* let rec f x = e1 in e2 *)
  | IfZero of expr * expr * expr       (* if e=0 then e1 else e2 *)

(* === 运行时值 === *)
type value =
  | VInt of int
  | VClos of string * expr * env       (* 闭包：参数名 + 体 + 捕获的环境 *)

and env = (string * value ref) list    (* 用 ref 撑 let rec 的"自指" *)

(* === 求值 === *)
let rec eval env = function
  | Int n -> VInt n
  | Var x ->
      (try !(List.assoc x env)
       with Not_found -> failwith ("unbound variable: " ^ x))
  | Add (e1, e2) ->
      (match eval env e1, eval env e2 with
       | VInt a, VInt b -> VInt (a + b)
       | _ -> failwith "Add: 非整数")
  | Mul (e1, e2) ->
      (match eval env e1, eval env e2 with
       | VInt a, VInt b -> VInt (a * b)
       | _ -> failwith "Mul: 非整数")
  | Lam (x, body) -> VClos (x, body, env)
  | App (f, arg) ->
      (match eval env f with
       | VClos (x, body, clos_env) ->
           let v = eval env arg in
           eval ((x, ref v) :: clos_env) body
       | _ -> failwith "App: 非函数")
  | Let (x, e1, e2) ->
      let v = eval env e1 in
      eval ((x, ref v) :: env) e2
  | LetRec (f, x, body, rest) ->
      (* 标准 trick：先放占位，再回填，让闭包能自引用 *)
      let placeholder = ref (VInt 0) in
      let env' = (f, placeholder) :: env in
      placeholder := VClos (x, body, env');
      eval env' rest
  | IfZero (cond, e1, e2) ->
      (match eval env cond with
       | VInt 0 -> eval env e1
       | VInt _ -> eval env e2
       | _ -> failwith "IfZero: 非整数")

let string_of_value = function
  | VInt n -> string_of_int n
  | VClos _ -> "<closure>"

(* === 三个测试程序 === *)

(* 1. (\x. x + 1) 41 *)
let prog1 =
  App (Lam ("x", Add (Var "x", Int 1)), Int 41)

(* 2. let add = \x. \y. x + y in (add 3) 4    ── 测试柯里化 + 闭包 *)
let prog2 =
  Let ("add",
       Lam ("x", Lam ("y", Add (Var "x", Var "y"))),
       App (App (Var "add", Int 3), Int 4))

(* 3. let rec fact = \n. if n=0 then 1 else n * fact(n-1) in fact 5 *)
let prog3 =
  LetRec ("fact", "n",
          IfZero (Var "n",
                  Int 1,
                  Mul (Var "n",
                       App (Var "fact", Add (Var "n", Int (-1))))),
          App (Var "fact", Int 5))

let () =
  Printf.printf "── prog1: (λx. x+1) 41 ──\n";
  Printf.printf "  = %s   (期望 42)\n" (string_of_value (eval [] prog1));

  Printf.printf "── prog2: let add = λx.λy. x+y in (add 3) 4 ──\n";
  Printf.printf "  = %s   (期望 7)\n" (string_of_value (eval [] prog2));

  Printf.printf "── prog3: let rec fact ... in fact 5 ──\n";
  Printf.printf "  = %s   (期望 120)\n" (string_of_value (eval [] prog3))
