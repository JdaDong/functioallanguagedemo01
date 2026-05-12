%%%-------------------------------------------------------------------
%%% Erlang 函数式编程 Demo 12: 热代码升级 —— BEAM 独有的不停服换代码
%%%
%%% "跑着的系统 9 年不停机, 中间升级了几十个版本" ——
%%% 这不是段子, 是爱立信 AXD301 真实数据。支撑这个能力的核心机制就是:
%%% **同一个模块可以同时存在两个版本 (current / old), 切换时老进程跑老代码,
%%%   新进程 / 再次进入 loop 的老进程用完全限定调用 ?MODULE:loop/1 走新代码。**
%%%
%%% 本 Demo 在一个文件里手动模拟"编译两份代码 -> 替换 -> 老进程无缝切换":
%%%   1. 启动一个 server 进程, 跑 v1 版 loop (只会 incr)
%%%   2. 我们用 erl_syntax 在运行时合成"v2 版模块"(会 incr 2)
%%%   3. compile:forms 得到新 beam, code:load_binary 装进系统
%%%   4. 给 server 发 upgrade, 它下一次 loop 用 ?MODULE:loop/1 就进了新版本
%%%   5. 观察: 同一个 Pid, 同一个状态, 行为已经换了
%%%
%%% 为避免在运行时拼 AST, 我们用更简单的做法:
%%% 用"策略函数"存在 state 里, upgrade 消息把策略替换掉 —— 这是"热升级的
%%% 最小直觉"; 真实 release 升级会走 appup/relup, 但机制本质相同:
%%% 让已经在跑的 loop 尊重下一个循环时拿到的『新代码 / 新策略』。
%%%
%%% 运行：
%%%   erl -compile 12_hot_code_upgrade.erl
%%%   erl -noshell -s 12_hot_code_upgrade main -s init stop
%%%-------------------------------------------------------------------
-module('12_hot_code_upgrade').
-export([main/0, start/0, incr/1, get/1, upgrade/2, stop/1, loop/1]).

-compile({no_auto_import, [get/1]}).

%% ============================================================
%% server: state = {Counter, BumpFun}
%%   - BumpFun/1 : 给老状态返回新状态, 替换它就等于『热换业务逻辑』
%% ============================================================

start() ->
    %% 初始策略 v1: 每次 +1
    V1 = fun(N) -> N + 1 end,
    Pid = spawn(?MODULE, loop, [{0, v1, V1}]),
    {ok, Pid}.

incr(Pid)     -> Pid ! {incr, self()}, recv().
get(Pid)      -> Pid ! {get,  self()}, recv().
upgrade(Pid, Tag) when Tag == v2 ->
    V2 = fun(N) -> N + 2 end,          %% v2: 每次 +2
    Pid ! {upgrade, Tag, V2, self()},
    recv();
upgrade(Pid, Tag) when Tag == v3 ->
    V3 = fun(N) -> N * 2 + 1 end,      %% v3: *2+1, 状态保留
    Pid ! {upgrade, Tag, V3, self()},
    recv().
stop(Pid)     -> Pid ! stop, ok.

recv() ->
    receive X -> X after 500 -> timeout end.

%% 关键: 递归时用 ?MODULE:loop/1 的【完全限定调用】,
%% 让 BEAM 每次进入循环前重新解析模块版本 —— 这就是真实 OTP 热升级的套路。
loop({N, Tag, BumpFun} = State) ->
    receive
        {incr, From} ->
            N2 = BumpFun(N),
            From ! {ok, Tag, N2},
            ?MODULE:loop({N2, Tag, BumpFun});

        {get, From} ->
            From ! {ok, Tag, N},
            ?MODULE:loop(State);

        {upgrade, NewTag, NewFun, From} ->
            io:format("  [upgrade] ~p -> ~p, 状态保留 N=~p~n", [Tag, NewTag, N]),
            From ! {upgraded, NewTag},
            ?MODULE:loop({N, NewTag, NewFun});

        stop -> ok
    end.

%% ============================================================
%% 演示
%% ============================================================

main() ->
    io:format("=== 热代码 / 热策略升级 ===~n"),

    {ok, P} = start(),

    io:format("~n-- v1: 每次 +1 --~n"),
    io:format("  ~p~n", [incr(P)]),   %% {ok,v1,1}
    io:format("  ~p~n", [incr(P)]),   %% {ok,v1,2}
    io:format("  ~p~n", [incr(P)]),   %% {ok,v1,3}

    io:format("~n-- 热升级到 v2: 每次 +2, 老状态保留 --~n"),
    {upgraded, v2} = upgrade(P, v2),
    io:format("  ~p~n", [incr(P)]),   %% {ok,v2,5}
    io:format("  ~p~n", [incr(P)]),   %% {ok,v2,7}
    io:format("  ~p~n", [get(P)]),    %% {ok,v2,7}

    io:format("~n-- 再热升级到 v3: N = N*2+1 --~n"),
    {upgraded, v3} = upgrade(P, v3),
    io:format("  ~p~n", [incr(P)]),   %% {ok,v3, 7*2+1 = 15}
    io:format("  ~p~n", [incr(P)]),   %% {ok,v3, 15*2+1 = 31}

    stop(P),

    io:format("~n=== 重点 ===~n"),
    io:format("  * 同一个 Pid 全程未重启, 内部状态一直保留 —— 这就是零停机升级~n"),
    io:format("  * 关键代码: ?MODULE:loop/1 完全限定调用, 让 BEAM 下轮循环重新 dispatch~n"),
    io:format("  * 真实 OTP: appup/relup + release_handler, 机制相同, 能换整张模块表~n"),
    io:format("  * 这是 BEAM 虚拟机独有的能力, JVM/CLR/Go 都做不到这么彻底的 in-place 升级~n"),
    ok.
