%%%-------------------------------------------------------------------
%%% Erlang 函数式编程 Demo 4: gen_server 行为 —— OTP 入门
%%%
%%% 03 号 Demo 用 spawn + receive 手写了 Actor，能理解直觉；
%%% 但真实工程里几乎没有人直接那么写，因为要自己处理：
%%%   * 初始化、终止清理
%%%   * 同步 call vs 异步 cast
%%%   * 崩溃后怎么报告给监督者
%%%   * 热代码升级
%%%
%%% OTP 的 gen_server 行为把这些"共通套路"抽掉了，
%%% 你只需要填 5 个回调：init/1, handle_call/3, handle_cast/2,
%%% handle_info/2, terminate/2。本 Demo 用一个最小计数器服务演示。
%%%
%%% 运行：
%%%   erl -compile 04_gen_server_counter.erl
%%%   erl -noshell -s gen_server_counter main -s init stop
%%%-------------------------------------------------------------------
-module(gen_server_counter).
-behaviour(gen_server).

-compile({no_auto_import, [get/1]}).

%% 对外 API
-export([start_link/0, start_link/1, incr/1, decr/1, get/1, stop/1]).
%% gen_server 回调
-export([init/1, handle_call/3, handle_cast/2, handle_info/2, terminate/2, code_change/3]).
%% 演示入口
-export([main/0]).

%% ====================== API ======================

start_link()       -> start_link(0).
start_link(Init)   -> gen_server:start_link(?MODULE, Init, []).

incr(Pid)          -> gen_server:cast(Pid, incr).
decr(Pid)          -> gen_server:cast(Pid, decr).
get(Pid)           -> gen_server:call(Pid, get).
stop(Pid)          -> gen_server:stop(Pid).

%% ====================== 回调 ======================

init(Init) ->
    io:format("  [init]   counter started at ~p~n", [Init]),
    {ok, Init}.

%% 同步调用：调用方要拿到结果才返回
handle_call(get, _From, State) ->
    {reply, State, State};
handle_call(Other, _From, State) ->
    {reply, {error, {unknown_call, Other}}, State}.

%% 异步调用：fire-and-forget
handle_cast(incr, State) -> {noreply, State + 1};
handle_cast(decr, State) -> {noreply, State - 1};
handle_cast(_, State)    -> {noreply, State}.

%% 来自非 call/cast 的消息（比如 timer、monitor）
handle_info(Info, State) ->
    io:format("  [info]   got ~p~n", [Info]),
    {noreply, State}.

%% 终止回调：让我们清理资源
terminate(Reason, State) ->
    io:format("  [terminate] reason=~p final=~p~n", [Reason, State]),
    ok.

code_change(_Old, State, _Extra) -> {ok, State}.

%% ====================== 演示 ======================

main() ->
    io:format("=== gen_server counter ===~n"),
    {ok, Pid} = start_link(10),
    incr(Pid), incr(Pid), incr(Pid), decr(Pid),
    V1 = get(Pid),
    io:format("  incr x3 / decr x1 后: ~p~n", [V1]),

    %% 发个不存在的同步调用，看错误封装
    R = gen_server:call(Pid, nonsense),
    io:format("  未知调用: ~p~n", [R]),

    %% 主动发一条 info 消息，走 handle_info
    Pid ! {tick, erlang:system_time(millisecond)},
    timer:sleep(50),

    stop(Pid),
    io:format("~n=== 重点 ===~n"),
    io:format("  * init / call / cast / info / terminate 五个回调把 Actor 套路彻底标准化~n"),
    io:format("  * call 是同步、cast 是异步，这和 Scala cats-effect 的 Ref.get / update 对应~n"),
    io:format("  * 对标: Haskell STM MVar + forkIO  |  Scala cats-effect Ref/Deferred  |  Rust Arc<Mutex>~n"),
    ok.
