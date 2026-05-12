%%%-------------------------------------------------------------------
%%% Erlang 函数式编程 Demo 9: gen_statem —— OTP 状态机的正确姿势
%%%
%%% 04 号 Demo 用 gen_server 做了"单一状态"的 Actor，
%%% 真实业务里"订单 / 网络握手 / 协议帧机"往往有多个状态、状态之间有严格迁移规则。
%%% 手撸 receive + case 很快就会乱，OTP 为此提供了 gen_statem。
%%%
%%% 它的核心直觉：
%%%   * 每个"状态"对应一个回调函数 (state_functions 模式)
%%%   * 每收到一个事件，状态函数自己决定是否迁移、返回新状态
%%%   * 非法事件直接交给 gen_statem 默认处理，崩溃 -> let it crash
%%%   * 内置 state_timeout / event_timeout，天然支持"等 N 秒还没付钱就取消"
%%%
%%% 本 Demo 构建一个订单 FSM：
%%%   created -(pay)-> paid -(ship)-> shipped -(deliver)-> delivered
%%%       ｜                                                    ^
%%%       +-(cancel 或 state_timeout)-> cancelled              |
%%%
%%% 对标：
%%%   Scala   Demo 09 OrderStateMachine (cats-effect Ref + State)
%%%   Haskell 状态 Monad + Phantom 类型状态机
%%%   Rust    typestate pattern
%%%
%%% 运行：
%%%   erl -compile 09_gen_statem_order_fsm.erl
%%%   erl -noshell -s 09_gen_statem_order_fsm main -s init stop
%%%-------------------------------------------------------------------
-module('09_gen_statem_order_fsm').
-behaviour(gen_statem).

%% 对外 API
-export([start_link/1, pay/1, ship/1, deliver/1, cancel/1, state/1, stop/1]).
%% gen_statem 回调
-export([callback_mode/0, init/1, terminate/3, code_change/4]).
%% 每个状态一个回调函数
-export([created/3, paid/3, shipped/3, delivered/3, cancelled/3]).
%% 演示入口
-export([main/0]).

-record(data, {order_id, amount, paid_at, shipped_at, delivered_at, cancelled_reason}).

%% ============ API ============

start_link(OrderId) ->
    gen_statem:start_link(?MODULE, #{order_id => OrderId, amount => 0}, []).

pay(Pid)     -> gen_statem:call(Pid, {pay, 100}).
ship(Pid)    -> gen_statem:call(Pid, ship).
deliver(Pid) -> gen_statem:call(Pid, deliver).
cancel(Pid)  -> gen_statem:call(Pid, {cancel, user}).
state(Pid)   -> gen_statem:call(Pid, which_state).
stop(Pid)    -> gen_statem:stop(Pid).

%% ============ 回调模式 ============
%% state_functions: 每个状态一个函数 (推荐)
%% handle_event_function: 所有事件都进同一个 handle_event/4

callback_mode() -> state_functions.

init(#{order_id := OrderId}) ->
    io:format("  [init]   order=~p created~n", [OrderId]),
    Data = #data{order_id = OrderId, amount = 0},
    %% 进入 created 状态, 设置 2 秒状态超时: 不付钱就自动取消
    {ok, created, Data, [{state_timeout, 2000, not_paid}]}.

%% ============ 状态: created ============
created({call, From}, {pay, Amount}, Data) ->
    io:format("  [created -> paid]  amount=~p~n", [Amount]),
    NewData = Data#data{amount = Amount, paid_at = erlang:system_time(millisecond)},
    {next_state, paid, NewData, [{reply, From, {ok, paid}}]};

created({call, From}, {cancel, Reason}, Data) ->
    io:format("  [created -> cancelled] reason=~p~n", [Reason]),
    {next_state, cancelled, Data#data{cancelled_reason = Reason},
     [{reply, From, {ok, cancelled}}]};

created({call, From}, which_state, _Data) ->
    {keep_state_and_data, [{reply, From, created}]};

created(state_timeout, not_paid, Data) ->
    io:format("  [created -> cancelled] 超时未付款~n"),
    {next_state, cancelled, Data#data{cancelled_reason = timeout}};

created({call, From}, Event, _Data) ->
    {keep_state_and_data, [{reply, From, {error, {illegal_in_created, Event}}}]}.

%% ============ 状态: paid ============
paid({call, From}, ship, Data) ->
    io:format("  [paid -> shipped]~n"),
    NewData = Data#data{shipped_at = erlang:system_time(millisecond)},
    {next_state, shipped, NewData, [{reply, From, {ok, shipped}}]};

paid({call, From}, which_state, _Data) ->
    {keep_state_and_data, [{reply, From, paid}]};

paid({call, From}, Event, _Data) ->
    {keep_state_and_data, [{reply, From, {error, {illegal_in_paid, Event}}}]}.

%% ============ 状态: shipped ============
shipped({call, From}, deliver, Data) ->
    io:format("  [shipped -> delivered]~n"),
    NewData = Data#data{delivered_at = erlang:system_time(millisecond)},
    {next_state, delivered, NewData, [{reply, From, {ok, delivered}}]};

shipped({call, From}, which_state, _Data) ->
    {keep_state_and_data, [{reply, From, shipped}]};

shipped({call, From}, Event, _Data) ->
    {keep_state_and_data, [{reply, From, {error, {illegal_in_shipped, Event}}}]}.

%% ============ 状态: delivered (终态) ============
delivered({call, From}, which_state, _Data) ->
    {keep_state_and_data, [{reply, From, delivered}]};

delivered({call, From}, Event, _Data) ->
    {keep_state_and_data, [{reply, From, {error, {terminal_delivered, Event}}}]}.

%% ============ 状态: cancelled (终态) ============
cancelled({call, From}, which_state, _Data) ->
    {keep_state_and_data, [{reply, From, cancelled}]};

cancelled({call, From}, Event, _Data) ->
    {keep_state_and_data, [{reply, From, {error, {terminal_cancelled, Event}}}]}.

%% ============ 其他必要回调 ============
terminate(_Reason, State, _Data) ->
    io:format("  [terminate] final state=~p~n", [State]),
    ok.

code_change(_Old, State, Data, _Extra) -> {ok, State, Data}.

%% ============ 演示 ============
main() ->
    io:format("=== gen_statem 订单状态机 ===~n"),

    %% 场景 1: 正常走完全流程
    io:format("~n-- 场景 1: 正常完成 --~n"),
    {ok, P1} = start_link("ord-001"),
    {ok, paid}      = pay(P1),
    {ok, shipped}   = ship(P1),
    {ok, delivered} = deliver(P1),
    delivered       = state(P1),
    stop(P1),

    %% 场景 2: 非法事件 —— created 状态下直接 ship 会被拒绝
    io:format("~n-- 场景 2: 非法事件 --~n"),
    {ok, P2} = start_link("ord-002"),
    R = ship(P2),
    io:format("  created 直接 ship = ~p~n", [R]),
    created = state(P2),
    stop(P2),

    %% 场景 3: 超时自动取消 (state_timeout 2s)
    io:format("~n-- 场景 3: 2 秒内不付款 -> 自动取消 --~n"),
    {ok, P3} = start_link("ord-003"),
    timer:sleep(2200),
    cancelled = state(P3),
    stop(P3),

    io:format("~n=== 重点 ===~n"),
    io:format("  * state_functions 模式 = 每个状态一个函数, 天然防止『在错误状态下执行动作』~n"),
    io:format("  * state_timeout 内置超时机制, 替代手写 after + 自发消息~n"),
    io:format("  * 对标: Scala cats-effect FSM / Akka FSM / Rust typestate~n"),
    ok.
