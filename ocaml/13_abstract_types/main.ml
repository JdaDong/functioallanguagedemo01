(* dune exec ./main.exe
   demo 13: 抽象类型——用模块边界保护数据不变量
   - signature 只暴露 type t（不暴露实现）
   - 外界拿不到内部表示，只能走我们提供的构造/查询函数
   - 这就让我们在构造函数里强加不变量（比如：邮箱必须包含 @）
*)

(* === 1. 抽象类型 + 不变量 === *)
module Email : sig
  type t                                     (* 抽象：外界不知道是 string *)
  val make : string -> t option              (* 唯一入口，做合法性校验 *)
  val to_string : t -> string
end = struct
  type t = string

  let make s =
    if String.contains s '@' && String.length s >= 3 then Some s
    else None

  let to_string s = s
end

(* === 2. 试用：合法的进得来，非法的被挡在外面 === *)
let () =
  (match Email.make "alice@example.com" with
   | Some e -> Printf.printf "ok: %s\n" (Email.to_string e)
   | None   -> print_endline "rejected");

  (match Email.make "no-at-sign" with
   | Some e -> Printf.printf "ok: %s\n" (Email.to_string e)
   | None   -> print_endline "rejected: no-at-sign");

  (* 关键：下面这行如果取消注释会编译错误。
     因为 Email.t 是抽象的，外界没办法把任意 string 当成 Email.t 用。
     这就是抽象类型的"防御":
        let bad : Email.t = "totally fake" in
        print_endline (Email.to_string bad)
  *)
  print_endline "abstract type 把不变量锁在模块里——编译期就拒绝伪造"
