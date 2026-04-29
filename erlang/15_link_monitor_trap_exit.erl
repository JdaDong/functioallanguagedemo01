%%%-------------------------------------------------------------------
%%% Erlang 函数式编程 Demo 15: link / monitor / trap_exit
%%%   —— Let-it-crash 到底是怎么"传播"的?
%%%
%%% 05 号 Demo 展示了 supervisor 重启子进程, 但其底层机制没有拆解:
%%% 三种"进程之间的关联"决定了故障怎么流动:
%%%
%%%   1. link(Pid)        —— 双向链接: 一方挂了, 另一方也收到 {'EXIT', Pid, Reason}
%%%                           默认是直接挂掉, 除非开了 trap_exit
%%%   2. monitor(process, Pid) —— 单向监控: Pid 挂了我收到
%%%                           {'DOWN', Ref, process, Pid, Reason}, 自己不受影响
%%%   3. process_flag(trap_exit, true) —— 让 link 不再致命,
%%%                           把 'EXIT' 当普通消息收, 自己决定怎么处理
%%%
%%% 搞清这三者, Let-it-crash 和 supervisor 就都懂了。
%%%
%%% 运行：
%%%   erl -compile 15_link_monitor_trap_exit.erl
%%%   erl -noshell -s link_monitor_trap_exit main -s init stop
%%%-------------------------------------------------------------------
-module(link_monitor_trap_exit).
-export([main/0]).

%% ============================================================
%% helpers
%% ============================================================

%% 一个会按指令结束的被观察进程
subject_loop() ->
    receive
        {die_normal, From} -> From ! bye, exit(normal);
        {die_bad, From}    -> From ! bye, exit({boom, intentional});
        ping               -> subject_loop()
    end.

spawn_subject() -> spawn(fun subject_loop/0).

%% ============================================================
%% 演示 1: link 传播 —— 不 trap_exit, 被连者挂我也挂
%% ============================================================

demo_link_propagation() ->
    Self = self(),

    %% 我们在一个"中间人"里 link, 这样中间人挂了不会影响 main
    Middle = spawn(fun() ->
        Victim = spawn_subject(),
        link(Victim),
        Victim ! {die_bad, self()},
        receive bye -> ok after 500 -> timeout end,
        %% 这里其实到不了, link 会让中间人自己也挂
        Self ! middle_survived
    end),

    %% 监控 Middle, 看它是不是真的被 link 传播挂了
    Ref = erlang:monitor(process, Middle),
    receive
        middle_survived ->
            "中间人居然活着 (不应该)";
        {'DOWN', Ref, process, Middle, Reason} ->
            {middle_crashed_with, Reason}
    after 500 -> timeout
    end.

%% ============================================================
%% 演示 2: trap_exit —— 让 link 不再致命, 把死亡变成普通消息
%% ============================================================

demo_trap_exit() ->
    %% 开启自己的 trap_exit
    process_flag(trap_exit, true),

    Victim = spawn_subject(),
    link(Victim),
    Victim ! {die_bad, self()},
    receive bye -> ok after 500 -> timeout end,

    %% link 死亡会变成 {'EXIT', Pid, Reason} 出现在邮箱
    R = receive {'EXIT', Victim, Reason} -> {received_exit, Reason}
        after 500 -> timeout
        end,

    process_flag(trap_exit, false),
    R.

%% ============================================================
%% 演示 3: monitor —— 单向、不致命、一次性
%% ============================================================

demo_monitor_normal() ->
    Victim = spawn_subject(),
    Ref = erlang:monitor(process, Victim),
    Victim ! {die_normal, self()},
    receive bye -> ok after 500 -> timeout end,
    receive
        {'DOWN', Ref, process, Victim, Reason} -> {down, Reason}
    after 500 -> timeout
    end.

demo_monitor_bad() ->
    Victim = spawn_subject(),
    Ref = erlang:monitor(process, Victim),
    Victim ! {die_bad, self()},
    receive bye -> ok after 500 -> timeout end,
    receive
        {'DOWN', Ref, process, Victim, Reason} -> {down, Reason}
    after 500 -> timeout
    end.

%% 监控已经死掉的进程: 立刻拿到 noproc
demo_monitor_already_dead() ->
    Victim = spawn_subject(),
    Victim ! {die_normal, self()},
    receive bye -> ok after 500 -> timeout end,
    timer:sleep(50),           %% 确保它真的死了
    Ref = erlang:monitor(process, Victim),
    receive
        {'DOWN', Ref, process, Victim, Reason} -> {down, Reason}
    after 500 -> timeout
    end.

%% ============================================================
%% main
%% ============================================================

main() ->
    io:format("=== link / monitor / trap_exit 三兄弟 ===~n"),

    io:format("~n-- 1. link 传播 (中间人应该被拽死) --~n"),
    io:format("  ~p~n", [demo_link_propagation()]),

    io:format("~n-- 2. trap_exit: 把死亡变成普通消息 --~n"),
    io:format("  ~p~n", [demo_trap_exit()]),

    io:format("~n-- 3. monitor: 单向, 不致命 --~n"),
    io:format("  normal exit: ~p~n", [demo_monitor_normal()]),
    io:format("  abnormal   : ~p~n", [demo_monitor_bad()]),
    io:format("  已死进程   : ~p~n", [demo_monitor_already_dead()]),

    io:format("~n=== 重点对照 ===~n"),
    io:format("           link                  monitor~n"),
    io:format("  方向     双向                  单向 (只有我收到)~n"),
    io:format("  致命性   默认致命              从不致命~n"),
    io:format("  解除     unlink / trap_exit    demonitor / 一次性~n"),
    io:format("  消息     {'EXIT', Pid, R}      {'DOWN', Ref, process, Pid, R}~n"),
    io:format("~n"),
    io:format("  * supervisor 内部其实用的就是 link + trap_exit: 子进程死 -> supervisor 收 EXIT -> 决定是否重启~n"),
    io:format("  * 应用开发者自己监听『另一个进程的死亡』时, 用 monitor 更干净: 不会把自己拖下水~n"),
    io:format("  * Let-it-crash 不是『到处崩』, 而是『崩得让正确的人感知到, 并由 supervisor 统一恢复』~n"),
    ok.
