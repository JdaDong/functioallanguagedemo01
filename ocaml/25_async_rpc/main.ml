(* dune exec ./main.exe
   demo 25: Async RPC ── Jane Street 内部 RPC 协议的缩影
   - Rpc.Rpc.t = 一个 RPC 调用的 schema：name + version + 请求/响应类型
   - 服务端用 Rpc.Rpc.implement 注册 handler，组装成 Implementations
   - 客户端用 Rpc.Rpc.dispatch 发请求
   - 两端通过 Transport 通信。本 demo 用同进程内 Pipe_transport 演示协议本身
   - 真生产环境只需把 Pipe_transport 换成 Tcp.Transport 就能跨机器
*)

open Core
open Async
open Async_rpc_kernel

(* === 1. 声明 RPC schema：三件套（name / version / 类型） === *)
let add_rpc =
  Rpc.Rpc.create
    ~name:"add"
    ~version:1
    ~bin_query:[%bin_type_class: int * int]
    ~bin_response:[%bin_type_class: int]
    ~include_in_error_count:Only_on_exn

(* === 2. 服务端：注册 handler ─ Implementations 是 RPC 路由表 === *)
let server_impls =
  Rpc.Implementations.create_exn
    ~implementations:[
      Rpc.Rpc.implement add_rpc (fun () (a, b) ->
        printf "  [server] got add(%d, %d)\n" a b;
        return (a + b))
    ]
    ~on_unknown_rpc:`Continue

(* === 3. 用 Pipe.t 双向连接客户端和服务端 ── 同进程模拟跨进程 === *)
let make_transport () =
  (* 一对 Pipe：c2s 客户端→服务端；s2c 服务端→客户端 *)
  let c2s_r, c2s_w = Pipe.create () in
  let s2c_r, s2c_w = Pipe.create () in
  let pipe_to_transport reader writer =
    Pipe_transport.create Pipe_transport.Kind.string reader writer
  in
  let client_t = pipe_to_transport s2c_r c2s_w in
  let server_t = pipe_to_transport c2s_r s2c_w in
  client_t, server_t

let main () =
  let client_t, server_t = make_transport () in

  (* 启动服务端 connection *)
  don't_wait_for (
    let%bind result =
      Rpc.Connection.create
        ~implementations:server_impls
        ~connection_state:(fun _ -> ())
        server_t
    in
    match result with
    | Error exn -> raise exn
    | Ok _conn -> Deferred.never ()
  );

  (* 客户端 connection + 调 RPC *)
  let%bind result =
    Rpc.Connection.create
      ~connection_state:(fun _ -> ())
      client_t
  in
  match result with
  | Error exn -> raise exn
  | Ok conn ->
      let%bind r1 = Rpc.Rpc.dispatch_exn add_rpc conn (3, 4) in
      printf "  [client] add(3,4)  = %d\n" r1;
      let%bind r2 = Rpc.Rpc.dispatch_exn add_rpc conn (100, 200) in
      printf "  [client] add(100,200) = %d\n" r2;
      shutdown 0;
      return ()

let () =
  don't_wait_for (main ());
  never_returns (Scheduler.go ())
