(* dune exec ./main.exe                       ── 不带参数，看 help
   dune exec ./main.exe -- hello -name Alice
   dune exec ./main.exe -- add  -a 3 -b 4
   demo 28: Core.Command ── Jane Street CLI 框架
   - 比 stdlib Arg 更现代：自动 --help、子命令、组合
   - flag / anon / param 三件套构造参数
   - Command.group 嵌套子命令
   - 配套的 Command_unix.run 是 OCaml 工业 CLI 的标配
*)

open Core

(* === 1. 子命令：hello -name Alice === *)
let hello_cmd =
  Command.basic
    ~summary:"打印问候"
    [%map_open.Command
      let name = flag "-name" (required string) ~doc:"NAME 要问候谁"
      and loud = flag "-loud" no_arg ~doc:" 用全大写"
      in
      fun () ->
        let msg = sprintf "Hello, %s!" name in
        print_endline (if loud then String.uppercase msg else msg)
    ]

(* === 2. 子命令：add -a 3 -b 4 === *)
let add_cmd =
  Command.basic
    ~summary:"算加法"
    [%map_open.Command
      let a = flag "-a" (required int) ~doc:"INT 加数 1"
      and b = flag "-b" (required int) ~doc:"INT 加数 2"
      in
      fun () -> printf "%d + %d = %d\n" a b (a + b)
    ]

(* === 3. 用 Command.group 把它们组成顶层命令 === *)
let cmd =
  Command.group
    ~summary:"demo 28: Core.Command 示范"
    [ "hello", hello_cmd
    ; "add",   add_cmd
    ]

let () = Command_unix.run cmd
