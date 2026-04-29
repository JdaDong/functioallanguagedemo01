%%% Erlang 函数式编程 Demo 17: Common Test 集成测试骨架
%%%
%%% 08 号 Demo 讲了 PropEr 属性测试，单元和性质测完了，
%%% 还差一层：“把若干进程/模块串起来，从外部接口打一遍”——
%%% 这就是 OTP 官方集成测试框架 Common Test (CT) 的定位。
%%%
%%% 真实项目里：
%%%   rebar3 ct            %% 跑所有 suite
%%%   rebar3 ct --suite …  %% 跑单个 suite
%%%
%%% 本 Demo 用纯 erl 模式演示 CT 回调骨架，不依赖 rebar3，
%%% 直接 demo17_common_test_ct:run/0 就能看到手工驱动的结果。
-module('17_common_test_ct').

-export([run/0]).

%% CT 回调（真实 suite 里需要 export 这些）
-export([all/0, groups/0,
         init_per_suite/1, end_per_suite/1,
         init_per_group/2, end_per_group/2,
         init_per_testcase/2, end_per_testcase/2,
         tc_add/1, tc_concurrent_add/1, tc_timeout_guard/1]).

%% ============================================================
%% 被测服务：一个极简的计数器 gen_server（这里手写最简等价物）
%% ============================================================
-record(counter, {value = 0 :: integer()}).

start_counter() ->
    spawn(fun() -> counter_loop(#counter{}) end).

counter_loop(State) ->
    receive
        {add, N, From}  ->
            NewState = State#counter{value = State#counter.value + N},
            From ! {ok, NewState#counter.value},
            counter_loop(NewState);
        {get, From}     ->
            From ! {ok, State#counter.value},
            counter_loop(State);
        stop            -> ok
    end.

call(Pid, Msg) ->
    Pid ! erlang:append_element(Msg, self()),
    receive {ok, V} -> V after 1000 -> timeout end.

%% ============================================================
%% CT 回调
%% ============================================================
all() -> [{group, basic}, {group, concurrent}].

groups() ->
    [
        {basic,      [sequence], [tc_add, tc_timeout_guard]},
        {concurrent, [parallel], [tc_concurrent_add]}
    ].

init_per_suite(Config) ->
    io:format("[CT] init_per_suite — 拉起测试用的基础环境~n"),
    [{suite_started_at, erlang:system_time(millisecond)} | Config].

end_per_suite(_Config) ->
    io:format("[CT] end_per_suite — 清理环境~n"),
    ok.

init_per_group(Group, Config) ->
    io:format("[CT] init_per_group(~p)~n", [Group]),
    Config.

end_per_group(Group, _Config) ->
    io:format("[CT] end_per_group(~p)~n", [Group]),
    ok.

init_per_testcase(TC, Config) ->
    io:format("[CT]   -> init_per_testcase(~p)~n", [TC]),
    Pid = start_counter(),
    [{counter, Pid} | Config].

end_per_testcase(TC, Config) ->
    Pid = proplists:get_value(counter, Config),
    Pid ! stop,
    io:format("[CT]   <- end_per_testcase(~p)~n", [TC]),
    ok.

%% ============================================================
%% 测试用例
%% ============================================================
tc_add(Config) ->
    Pid = proplists:get_value(counter, Config),
    1 = call(Pid, {add, 1}),
    3 = call(Pid, {add, 2}),
    3 = call(Pid, {get}),
    ok.

tc_concurrent_add(Config) ->
    Pid = proplists:get_value(counter, Config),
    Parent = self(),
    N = 100,
    [spawn(fun() ->
                   _ = call(Pid, {add, 1}),
                   Parent ! done
           end) || _ <- lists:seq(1, N)],
    [receive done -> ok end || _ <- lists:seq(1, N)],
    N = call(Pid, {get}),
    ok.

tc_timeout_guard(Config) ->
    Pid = proplists:get_value(counter, Config),
    %% 演示在 CT 里怎么显式断言 timeout
    Pid ! {bogus_message, self()},
    Got = receive _ -> matched after 200 -> timeout end,
    timeout = Got,
    ok.

%% ============================================================
%% 手工驱动器（模拟 rebar3 ct 的流程）
%% ============================================================
run() ->
    io:format("=== Erlang Demo 17: Common Test 集成测试 ===~n"),
    Config0 = init_per_suite([]),
    lists:foreach(fun(Group) -> run_group(Group, Config0) end, all()),
    end_per_suite(Config0),
    io:format("~n=== 重点理解 ===~n"),
    io:format("- CT 是 OTP 官方自带的集成测试框架，和 PropEr 形成互补~n"),
    io:format("- init_per_* / end_per_* 是典型 setup/teardown 钩子，保证用例间互不污染~n"),
    io:format("- groups 能声明 sequence/parallel，天然利用 BEAM 的并发能力~n"),
    io:format("- 真实项目里用 rebar3 ct 跑，CI 里会生成 HTML 报告~n"),
    ok.

run_group({group, Name}, Config0) ->
    {Name, Props, Cases} = lists:keyfind(Name, 1, groups()),
    io:format("~n-- group ~p ~p --~n", [Name, Props]),
    Config1 = init_per_group(Name, Config0),
    lists:foreach(fun(TC) -> run_case(TC, Config1) end, Cases),
    end_per_group(Name, Config1).

run_case(TC, Config0) ->
    Config1 = init_per_testcase(TC, Config0),
    Result =
        try
            ok = ?MODULE:TC(Config1),
            pass
        catch
            Class:Reason:Stack ->
                {fail, Class, Reason, Stack}
        end,
    end_per_testcase(TC, Config1),
    io:format("[CT]   ~p => ~p~n", [TC, Result]),
    ok.
