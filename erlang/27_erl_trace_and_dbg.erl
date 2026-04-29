%%% Erlang 函数式编程 Demo 27: erlang:trace 与 dbg（在线追踪）
%%%
%%% Erlang 最独特的生产力武器之一: 不改代码、不重启、就能给任意进程/函数打 tracer。
%%% 核心两个接口:
%%%   erlang:trace/3        —— 原始 API, 功能齐全, 但不好记
%%%   dbg 模块             —— 对 erlang:trace 的用户友好封装
%%%
%%% 本 Demo 展示三种最常用姿势:
%%%   1) 追某个进程的“收发消息”
%%%   2) 追某个函数的“每次调用”（入参 + 返回值）
%%%   3) 用 match spec 只在满足条件时才触发（生产上必须这么干, 不然压死）
%%%
%%% 生产安全提示: 这些 trace 都同进程开销, 用完记得 dbg:stop() 收回。

-module('27_erl_trace_and_dbg').

-export([run/0, target_add/2, target_loop/0]).

%% ============================================================
%% 被追踪的目标
%% ============================================================
target_add(X, Y) ->
    X + Y.

target_loop() ->
    receive
        {ping, From} -> From ! pong, target_loop();
        stop         -> ok;
        _            -> target_loop()
    end.

%% ============================================================
%% 1) 用 erlang:trace 原生接口追消息
%% ============================================================
demo_trace_messages() ->
    io:format("~n-- 1) 追单个进程的收发消息 --~n"),
    Target = spawn(fun ?MODULE:target_loop/0),

    %% 让自己成为 tracer，监听 send + receive 两类事件
    1 = erlang:trace(Target, true, [send, 'receive']),

    Target ! {ping, self()},
    receive pong -> ok after 500 -> ok end,
    Target ! hello,
    Target ! stop,
    timer:sleep(100),

    %% 把自己收到的 trace 消息全部抽出来
    drain_trace(10),
    ok.

drain_trace(0) -> ok;
drain_trace(N) ->
    receive
        {trace, Pid, Tag, Msg} ->
            io:format("  [trace] ~p ~p ~p~n", [Pid, Tag, Msg]),
            drain_trace(N - 1);
        {trace, Pid, Tag, Msg1, Msg2} ->
            io:format("  [trace] ~p ~p ~p ~p~n", [Pid, Tag, Msg1, Msg2]),
            drain_trace(N - 1)
    after 100 -> ok
    end.

%% ============================================================
%% 2) 用 dbg 追函数调用（友好封装）
%% ============================================================
demo_dbg_calls() ->
    io:format("~n-- 2) 用 dbg 追函数调用 --~n"),

    %% 保证 tracer 干净
    dbg:stop(),

    %% 创建一个“打印型” tracer
    dbg:tracer(process, {fun(Msg, _) ->
                             io:format("  [dbg] ~p~n", [Msg]),
                             ok
                         end, ok}),

    %% 选择要 trace 的进程范围
    dbg:p(all, c),   %% c = call

    %% 注册 target_add/2 进 trace 列表, 还要求打印返回值
    dbg:tpl(?MODULE, target_add, 2, [{'_', [], [{return_trace}]}]),

    _ = target_add(1, 2),
    _ = target_add(10, 20),

    timer:sleep(80),
    dbg:stop(),
    ok.

%% ============================================================
%% 3) 用 match spec 过滤——只在 X > 5 时才触发
%% ============================================================
demo_match_spec() ->
    io:format("~n-- 3) match spec 条件过滤 --~n"),

    dbg:stop(),
    dbg:tracer(process, {fun(Msg, _) ->
                             io:format("  [dbg*] ~p~n", [Msg]),
                             ok
                         end, ok}),
    dbg:p(all, c),

    %% 等价 SQL: WHERE X > 5
    %%   [{'$1','$2'}]             => 两个入参分别绑定到 $1 $2
    %%   [{'>', '$1', 5}]          => 只在 $1 > 5 时命中
    %%   [{return_trace}]          => 顺带打印返回值
    MS = [{ ['$1', '$2'],
            [{'>', '$1', 5}],
            [{return_trace}] }],
    dbg:tpl(?MODULE, target_add, 2, MS),

    _ = target_add(1, 2),    %% 不满足条件, 不打印
    _ = target_add(10, 1),   %% 满足 => 打印
    _ = target_add(3, 3),    %% 不满足
    _ = target_add(6, 0),    %% 满足

    timer:sleep(80),
    dbg:stop(),
    ok.

%% ============================================================
%% 运行器
%% ============================================================
run() ->
    io:format("=== Erlang Demo 27: erlang:trace / dbg ===~n"),
    demo_trace_messages(),
    demo_dbg_calls(),
    demo_match_spec(),

    io:format("~n=== 重点理解 ===~n"),
    io:format("- erlang:trace 是 BEAM 内建能力, 不需要改代码、不需要停机~n"),
    io:format("- dbg 模块是 erlang:trace 的友好壳, 真实排障首选 dbg:tpl + match spec~n"),
    io:format("- match spec 能按入参值过滤, 避免 tracer 把线上打爆~n"),
    io:format("- 用完一定 dbg:stop(), 否则会持续占开销~n"),
    io:format("- 真实环境常配合 recon_trace:calls/2 使用, 语义一致但自带速率限制~n"),
    ok.
