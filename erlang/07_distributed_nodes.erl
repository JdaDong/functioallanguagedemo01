%%%-------------------------------------------------------------------
%%% Erlang 函数式编程 Demo 7: 分布式节点 —— Actor 跨机的真正威力
%%%
%%% Erlang 的 spawn / send / receive 从一开始就设计成"位置透明"的：
%%% 同一台机器上的进程、另一台机器上的进程，对代码来说接口完全一样。
%%% 只要两个节点建立了 net_kernel 连接，消息就能直接透传。
%%%
%%% 本 Demo 演示同一台机器上启动两个节点互相通信：
%%%   * 节点 A 起一个 "echo server"
%%%   * 节点 B 通过 {ProcName, NodeA} 向它发消息
%%%
%%% 运行方式 A（单 shell，演示核心 API，不真的起多节点）：
%%%   erl -sname local -noshell -s distributed_nodes single_node_demo -s init stop
%%%
%%% 运行方式 B（真跨节点，开两个终端）：
%%%   终端 1:  erl -sname a -setcookie demo
%%%           (a@host)1> distributed_nodes:echo_server().
%%%   终端 2:  erl -sname b -setcookie demo
%%%           (b@host)1> distributed_nodes:ping(a@'<your-host>').
%%%
%%%-------------------------------------------------------------------
-module(distributed_nodes).
-export([single_node_demo/0, echo_server/0, echo_loop/0, ping/1]).

%% ====================== 单节点演示 ======================
%% 在同一个 shell 里演示所有 API，不需要真的起 -sname

single_node_demo() ->
    io:format("=== 1. node() / nodes() / is_alive() ===~n"),
    io:format("  node()     = ~p~n", [node()]),
    io:format("  nodes()    = ~p~n", [nodes()]),
    io:format("  is_alive() = ~p~n", [is_alive()]),

    io:format("~n=== 2. 本地注册 + 用 {Name, Node} 定位 ===~n"),
    Pid = spawn(fun echo_loop/0),
    register(echo_local, Pid),
    %% 这里 Node = node() 就是当前节点，实际跨机时替换成远程节点 atom
    {echo_local, node()} ! {hi, self()},
    receive
        {echoed, M} -> io:format("  远程风格调用本地 server，得到: ~p~n", [M])
    after 500 -> io:format("  timeout~n") end,
    echo_local ! stop,

    io:format("~n=== 3. rpc:call —— 同步远程调用 ===~n"),
    %% rpc:call 在单机上照样可用，传 node() 即对着自己发
    Sum = rpc:call(node(), lists, sum, [[1, 2, 3, 4, 5]]),
    io:format("  rpc:call(node(), lists, sum, [[1..5]]) = ~p~n", [Sum]),

    io:format("~n=== 4. 概念对照 ===~n"),
    io:format("  spawn(Node, Fun)          ~n"),
    io:format("    === Akka remote actor  |  Scala cats-effect 没有直接对应，需要配 fs2-grpc~n"),
    io:format("  {Name, Node} ! Msg        ~n"),
    io:format("    === Akka actorSelection |  Rust/Tokio 需 tonic/grpc 自行搭~n"),
    io:format("  rpc:call(Node, M, F, A)   ~n"),
    io:format("    === 等价一次跨网同步 RPC，幂等性/超时得自己考虑~n"),
    ok.

%% ====================== Echo Server ======================
%% 真跨节点时执行: distributed_nodes:echo_server() 会注册一个全局名

echo_server() ->
    Pid = spawn(fun echo_loop/0),
    register(echo_local, Pid),
    io:format("  echo_server 已在节点 ~p 上注册为 echo_local~n", [node()]),
    Pid.

echo_loop() ->
    receive
        {hi, From}  -> From ! {echoed, {hello_from, node()}}, echo_loop();
        {bye, From} -> From ! {echoed, bye}, ok;
        stop        -> ok;
        Other       -> io:format("  echo got: ~p~n", [Other]), echo_loop()
    end.

%% ====================== Ping 客户端（真跨节点用）======================

ping(TargetNode) ->
    %% 先试着连上目标节点
    case net_adm:ping(TargetNode) of
        pong ->
            io:format("  连接 ~p 成功~n", [TargetNode]),
            {echo_local, TargetNode} ! {hi, self()},
            receive
                {echoed, M} -> io:format("  收到对方回复: ~p~n", [M])
            after 1000 -> io:format("  timeout~n") end;
        pang ->
            io:format("  连不上 ~p —— 检查 cookie / hostname~n", [TargetNode])
    end.
