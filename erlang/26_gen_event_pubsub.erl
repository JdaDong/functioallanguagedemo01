%%% Erlang 函数式编程 Demo 26: gen_event 事件总线
%%%
%%% OTP 的四大 behaviour:
%%%   gen_server (Demo 04)  —— 请求/应答状态机
%%%   gen_statem (Demo 09)  —— 显式有限状态机
%%%   supervisor (Demo 05)  —— 进程监督
%%%   gen_event  (本 Demo)  —— 一对多事件分发（pub/sub 雏形）
%%%
%%% gen_event 让你：
%%%   - 用 add_handler / delete_handler 动态增删订阅者
%%%   - 通过 notify 把一个事件广播给全部 handler
%%%   - 每个 handler 有自己的状态（和 gen_server 类似但在同一个 manager 里）
%%%
%%% 虽然现代工程更常用 GenStage / Broadway / 自己的 pubsub，但 gen_event
%%% 仍然是 OTP logger 的底层机制，值得理解。

-module('26_gen_event_pubsub').

-behaviour(gen_event).

%% gen_event 回调
-export([init/1, handle_event/2, handle_call/2, handle_info/2,
         terminate/2, code_change/3]).

%% 对外 API
-export([run/0]).

%% ============================================================
%% Handler: 统计事件数量 + 打印
%% ============================================================
init({Name, Verbose}) ->
    io:format("[handler ~p] init, verbose=~p~n", [Name, Verbose]),
    {ok, #{name => Name, verbose => Verbose, count => 0, last => undefined}}.

handle_event(Evt, #{name := N, verbose := V, count := C} = S) ->
    S1 = S#{count := C + 1, last := Evt},
    case V of
        true  -> io:format("[handler ~p] got event #~p: ~p~n", [N, C + 1, Evt]);
        false -> ok
    end,
    {ok, S1}.

handle_call(get_stats, #{name := N, count := C, last := L} = S) ->
    {ok, #{name => N, count => C, last => L}, S};
handle_call(_Msg, S) ->
    {ok, ignored, S}.

handle_info(_Info, S) -> {ok, S}.

terminate(Reason, #{name := N}) ->
    io:format("[handler ~p] terminate: ~p~n", [N, Reason]),
    ok.

code_change(_Old, S, _Extra) -> {ok, S}.

%% ============================================================
%% 运行器
%% ============================================================
run() ->
    io:format("=== Erlang Demo 26: gen_event 事件总线 ===~n"),

    %% 1) 启动一个 event manager（类似 pubsub topic）
    {ok, Manager} = gen_event:start_link({local, demo_bus}),

    %% 2) 动态挂两个 handler（订阅者）
    %%    注意 Handler Id 可以是 module 也可以是 {module, term}，
    %%    用 {?MODULE, Tag} 方便同一个模块挂多次
    ok = gen_event:add_handler(demo_bus, {?MODULE, audit},   {audit,   true}),
    ok = gen_event:add_handler(demo_bus, {?MODULE, counter}, {counter, false}),

    %% 3) 广播事件
    io:format("~n-- 广播 5 条事件 --~n"),
    [ok = gen_event:notify(demo_bus, {user_login, "u" ++ integer_to_list(I)})
     || I <- lists:seq(1, 5)],
    timer:sleep(50),

    %% 4) 查两个 handler 各自的状态（它们是互相独立的）
    S1 = gen_event:call(demo_bus, {?MODULE, audit},   get_stats),
    S2 = gen_event:call(demo_bus, {?MODULE, counter}, get_stats),
    io:format("~n-- 各 handler 状态 --~n"),
    io:format("  audit   => ~p~n", [S1]),
    io:format("  counter => ~p~n", [S2]),

    %% 5) 动态卸载一个 handler
    _ = gen_event:delete_handler(demo_bus, {?MODULE, counter}, normal),
    io:format("~n-- 卸载 counter 后再广播 2 条 --~n"),
    [ok = gen_event:notify(demo_bus, {order_paid, I}) || I <- [100, 101]],
    timer:sleep(50),
    S3 = gen_event:call(demo_bus, {?MODULE, audit}, get_stats),
    io:format("  audit 最终 => ~p~n", [S3]),

    %% 6) 关闭 manager
    unlink(Manager),
    ok = gen_event:stop(demo_bus),

    io:format("~n=== 重点理解 ===~n"),
    io:format("- gen_event 是 OTP 里最接近 pub/sub 的内建原语~n"),
    io:format("- 每个 handler 有独立状态, manager 负责把事件逐个派发~n"),
    io:format("- 不适合高吞吐/背压场景 => 改用 GenStage / Broadway / Phoenix.PubSub~n"),
    io:format("- Erlang 的 logger 在 OTP 21 之前就是基于 gen_event 做的多 backend~n"),
    ok.
