%%%-------------------------------------------------------------------
%%% Erlang 函数式编程 Demo 5: Supervisor —— Let it crash
%%%
%%% Erlang 最有名的一句话就是 "Let it crash"。
%%% 本 Demo 展示它是怎么落地的：
%%%   * 业务进程写得尽量简单，不写防御代码
%%%   * 出异常就直接 exit
%%%   * 由 supervisor 按预设策略把它重启回来
%%%
%%% 三种常见重启策略：
%%%   one_for_one       —— 挂谁重启谁 (默认)
%%%   one_for_all       —— 一个挂了，全家重启
%%%   rest_for_one      —— 按启动顺序，挂了的和它后面的全部重启
%%%
%%% 运行：
%%%   erl -compile 05_supervisor_tree.erl
%%%   erl -noshell -s supervisor_tree main -s init stop
%%%-------------------------------------------------------------------
-module(supervisor_tree).
-behaviour(supervisor).

-export([main/0, start_link/0, init/1]).
%% 业务 worker
-export([worker_start/1, worker_loop/1]).

%% ====================== Supervisor ======================

start_link() -> supervisor:start_link({local, ?MODULE}, ?MODULE, []).

init([]) ->
    SupFlags = #{strategy => one_for_one, intensity => 5, period => 10},
    Children = [
        #{id => counter_a,
          start => {?MODULE, worker_start, [a]},
          restart => permanent, shutdown => 1000, type => worker},
        #{id => counter_b,
          start => {?MODULE, worker_start, [b]},
          restart => permanent, shutdown => 1000, type => worker}
    ],
    {ok, {SupFlags, Children}}.

%% ====================== Worker ======================

worker_start(Name) ->
    Pid = spawn_link(fun() -> worker_loop({Name, 0}) end),
    register(worker_name(Name), Pid),
    {ok, Pid}.

worker_name(a) -> counter_a;
worker_name(b) -> counter_b.

worker_loop({Name, N}) ->
    receive
        {bump, From} ->
            From ! {value, N + 1},
            worker_loop({Name, N + 1});

        crash ->
            io:format("  [~p] 主动崩溃 (Let it crash!)~n", [Name]),
            exit(intentional_crash);

        {divide, A, B, From} ->
            %% 故意不做防御：B=0 就直接 badarith 崩掉
            From ! {result, A div B},
            worker_loop({Name, N});

        stop -> ok
    end.

%% ====================== 演示 ======================

main() ->
    io:format("=== Supervisor: Let it crash ===~n"),
    {ok, SupPid} = start_link(),

    %% 1. 正常工作
    ok = bump_and_show(counter_a),
    ok = bump_and_show(counter_b),

    %% 2. 让 a 崩溃 (运行时错误)
    io:format("~n-- 故意让 counter_a 崩掉 --~n"),
    whereis(counter_a) ! crash,
    timer:sleep(100),              %% 给 supervisor 时间重启
    io:format("  counter_a 重启后 whereis=~p~n", [whereis(counter_a)]),
    ok = bump_and_show(counter_a), %% 新的 a 状态重置为 0

    %% 3. 让 b 因为未防御的除零崩掉
    io:format("~n-- counter_b 除 0 将触发 badarith --~n"),
    whereis(counter_b) ! {divide, 10, 0, self()},
    timer:sleep(100),
    io:format("  counter_b 重启后 whereis=~p~n", [whereis(counter_b)]),
    ok = bump_and_show(counter_b),

    %% 4. 查看 supervisor 当前子进程
    Children = supervisor:which_children(SupPid),
    io:format("~n  当前子进程: ~p~n", [Children]),

    supervisor:terminate_child(SupPid, counter_a),
    supervisor:terminate_child(SupPid, counter_b),
    exit(SupPid, shutdown),
    io:format("~n=== 重点 ===~n"),
    io:format("  * worker 只写业务，没有任何 try/catch —— 这就是 Let it crash~n"),
    io:format("  * supervisor 用声明式 child spec 描述重启策略，崩了自动恢复~n"),
    io:format("  * 重启后状态归零，这也是为什么重要状态要放进数据库 / mnesia 而不是内存~n"),
    io:format("  * 对照: Scala cats-effect Supervisor / Akka SupervisorStrategy~n"),
    ok.

bump_and_show(Name) ->
    whereis(Name) ! {bump, self()},
    receive {value, V} -> io:format("  ~p bump -> ~p~n", [Name, V]) after 500 -> io:format("timeout~n") end,
    ok.
