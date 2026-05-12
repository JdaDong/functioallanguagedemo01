%%%-------------------------------------------------------------------
%%% Erlang 函数式编程 Demo 13: gen_tcp Echo 服务器 —— "每连接一个进程"
%%%
%%% 这是 BEAM 并发模型的招牌场景:
%%%   * 一个 acceptor 进程 : 不断 accept
%%%   * 每个 TCP 连接      : spawn 一个独立进程单独处理
%%%   * 连接崩了只影响自己, acceptor 继续接新连接
%%%
%%% 这正是 cowboy / ranch 这些生产 HTTP 服务器的内核, 本 Demo 用最小 gen_tcp 再现:
%%%   1. listen 在随机端口
%%%   2. spawn acceptor
%%%   3. 每来一条连接, spawn handler
%%%   4. 起两个客户端并发发数据, 看每个连接都拿到自己独立的回显
%%%
%%% 运行：
%%%   erl -compile 13_gen_tcp_echo_server.erl
%%%   erl -noshell -s 13_gen_tcp_echo_server main -s init stop
%%%-------------------------------------------------------------------
-module('13_gen_tcp_echo_server').
-export([main/0, start_server/0, acceptor_loop/1, handler_loop/1, client/3]).

%% ============================================================
%% Server
%% ============================================================

start_server() ->
    %% 让系统随便挑一个端口
    Opts = [binary, {packet, line}, {active, false}, {reuseaddr, true}],
    {ok, LSock} = gen_tcp:listen(0, Opts),
    {ok, Port} = inet:port(LSock),
    AcceptorPid = spawn_link(?MODULE, acceptor_loop, [LSock]),
    {ok, #{port => Port, lsock => LSock, acceptor => AcceptorPid}}.

acceptor_loop(LSock) ->
    case gen_tcp:accept(LSock, 3000) of
        {ok, Sock} ->
            Pid = spawn(?MODULE, handler_loop, [Sock]),
            %% 把 socket 的所有权交给 handler 进程
            gen_tcp:controlling_process(Sock, Pid),
            acceptor_loop(LSock);
        {error, timeout} ->
            %% 没新连接就结束, 便于 demo 退出
            gen_tcp:close(LSock),
            ok;
        {error, closed} ->
            ok
    end.

handler_loop(Sock) ->
    case gen_tcp:recv(Sock, 0, 2000) of
        {ok, Line} ->
            %% 回显: "ECHO: <line>"
            ok = gen_tcp:send(Sock, <<"ECHO: ", Line/binary>>),
            handler_loop(Sock);
        {error, closed} ->
            ok;
        {error, timeout} ->
            gen_tcp:close(Sock),
            ok
    end.

%% ============================================================
%% Client (用 gen_tcp 跑一个小客户端, 方便 demo 一个文件完成闭环)
%% ============================================================

client(Port, Name, Lines) ->
    {ok, Sock} = gen_tcp:connect({127,0,0,1}, Port,
                                 [binary, {packet, line}, {active, false}]),
    lists:foreach(fun(L) ->
        ok = gen_tcp:send(Sock, <<L/binary, "\n">>),
        {ok, Reply} = gen_tcp:recv(Sock, 0, 1000),
        io:format("  [~s] send=~p  recv=~p~n", [Name, L, Reply])
    end, Lines),
    gen_tcp:close(Sock),
    ok.

%% ============================================================
%% 演示
%% ============================================================

main() ->
    io:format("=== gen_tcp Echo 服务器 —— 每连接一个进程 ===~n"),

    %% 启动 server
    {ok, #{port := Port}} = start_server(),
    io:format("~n  server 监听 127.0.0.1:~p~n", [Port]),
    timer:sleep(50),

    %% 两个客户端并发发数据
    Self = self(),
    spawn(fun() ->
        client(Port, "client-A", [<<"hello">>, <<"world">>]),
        Self ! a_done
    end),
    spawn(fun() ->
        client(Port, "client-B", [<<"foo">>, <<"bar">>, <<"baz">>]),
        Self ! b_done
    end),

    %% 收两边的完成信号
    receive a_done -> ok after 3000 -> timeout end,
    receive b_done -> ok after 3000 -> timeout end,

    timer:sleep(100),
    io:format("~n=== 重点 ===~n"),
    io:format("  * listen 一次, accept 循环, 每连接 spawn 一个 handler —— BEAM 并发招牌模式~n"),
    io:format("  * controlling_process/2 把 socket 所有权交给 handler, 之后 handler 崩了不牵连 acceptor~n"),
    io:format("  * 生产环境基本都是 ranch / cowboy, 它们正是把这套模板做成 supervisor + 限流~n"),
    io:format("  * 对照: Rust tokio::spawn + TcpListener / Go goroutine + net.Listen / Scala cats-effect + fs2.io~n"),
    ok.
