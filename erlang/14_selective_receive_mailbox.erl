%%%-------------------------------------------------------------------
%%% Erlang 函数式编程 Demo 14: 选择性 receive 与进程邮箱语义
%%%
%%% receive 看似简单, 但它其实是 Erlang 并发正确性的核心细节:
%%%
%%%   * 邮箱是"进程私有的 FIFO 队列"
%%%   * receive 做"选择性匹配": 按模式在邮箱里按顺序找第一个能匹配的, 不匹配就跳过
%%%   * 不匹配的消息会"留在邮箱"里 —— 可能堆积成性能杀手
%%%   * after N 提供超时语义
%%%   * after 0 非常有用: 用来"抽干邮箱" (flush) 或"非阻塞收"
%%%
%%% 本 Demo 演示 4 件事:
%%%   1. 按 tag 选择性接收, 无关消息被暂时留下
%%%   2. 用 after 0 把邮箱抽干
%%%   3. 用 after N 做超时
%%%   4. 用 make_ref() 做『请求-响应的唯一关联』, 避免抓错别的回复
%%%
%%% 运行：
%%%   erl -compile 14_selective_receive_mailbox.erl
%%%   erl -noshell -s 14_selective_receive_mailbox main -s init stop
%%%-------------------------------------------------------------------
-module('14_selective_receive_mailbox').
-export([main/0]).

%% ============================================================
%% 1. 选择性接收: 按模式从邮箱里"挑"消息
%% ============================================================

demo_selective() ->
    Self = self(),
    %% 给自己连发 4 条消息, 顺序是 a b c b
    Self ! {a, 1},
    Self ! {b, 2},
    Self ! {c, 3},
    Self ! {b, 4},

    %% 第一个 receive 只要 {b, _}, 会跳过 a 去拿第一个 b=2
    {b, B1} = receive {b, Vb1} -> {b, Vb1} end,

    %% 邮箱现在剩: a=1, c=3, b=4
    %% 第二个 receive 只要 {c, _}, 跳过 a 去拿 c=3
    {c, C}  = receive {c, Vc}  -> {c, Vc}  end,

    %% 邮箱现在剩: a=1, b=4
    {a, A}  = receive {a, Va}  -> {a, Va}  end,
    {b, B2} = receive {b, Vb2} -> {b, Vb2} end,

    {B1, C, A, B2}.

%% ============================================================
%% 2. after 0: 抽干邮箱 / 非阻塞收
%% ============================================================

flush() ->
    receive _ -> flush()
    after 0 -> ok
    end.

demo_flush() ->
    self() ! x, self() ! y, self() ! z,
    %% 期待至少有 3 条
    Before = erlang:process_info(self(), message_queue_len),
    flush(),
    After  = erlang:process_info(self(), message_queue_len),
    {Before, After}.

%% ============================================================
%% 3. after N: 超时
%% ============================================================

demo_timeout() ->
    T0 = erlang:monotonic_time(millisecond),
    R = receive
            {never_arrives, X} -> {got, X}
        after 200 -> timeout
        end,
    Dt = erlang:monotonic_time(millisecond) - T0,
    {R, Dt}.

%% ============================================================
%% 4. 请求-响应: 必须用 make_ref() 作为关联, 防止串线
%% ============================================================

echo_server() ->
    receive
        {Ref, From, Msg} ->
            From ! {Ref, {echo, Msg}},
            echo_server();
        stop -> ok
    end.

rpc_safe(ServerPid, Msg) ->
    Ref = make_ref(),
    ServerPid ! {Ref, self(), Msg},
    receive
        {Ref, Reply} -> Reply        %% 关键: 模式里写死 Ref
    after 500 -> timeout
    end.

demo_rpc() ->
    %% 启动 server
    S = spawn(fun echo_server/0),

    %% 故意先往自己邮箱塞一条"干扰"消息 (比如模拟别人误发给你)
    self() ! {some_other_ref, garbage},

    R1 = rpc_safe(S, <<"hello">>),
    R2 = rpc_safe(S, <<"world">>),
    S ! stop,
    {R1, R2}.

%% ============================================================
%% main
%% ============================================================

main() ->
    io:format("=== 选择性接收 & 邮箱语义 ===~n"),

    io:format("~n-- 1. 选择性 receive (跨过 a/c 去抓 b) --~n"),
    io:format("  结果 = ~p~n", [demo_selective()]),

    io:format("~n-- 2. after 0 抽干邮箱 --~n"),
    {Before, After} = demo_flush(),
    io:format("  flush 前队列长度=~p, flush 后=~p~n", [Before, After]),

    io:format("~n-- 3. after N 超时 --~n"),
    {R, Dt} = demo_timeout(),
    io:format("  结果=~p, 实际等了=~p ms~n", [R, Dt]),

    io:format("~n-- 4. rpc 用 make_ref() 防串线 --~n"),
    {A, B} = demo_rpc(),
    io:format("  ~p / ~p~n", [A, B]),

    io:format("~n=== 重点 ===~n"),
    io:format("  * 邮箱是 FIFO, 但 receive 是 『按模式扫描』, 能跨过不匹配的消息~n"),
    io:format("  * 不匹配的消息会留在邮箱 —— 邮箱积压是 Erlang 生产故障的常见源头~n"),
    io:format("  * after 0 做 flush; after N 做 timeout; after infinity 永远阻塞~n"),
    io:format("  * 请求-响应一定要 make_ref(), 否则会错抓别人的回复, 这是新手高频 bug~n"),
    ok.
