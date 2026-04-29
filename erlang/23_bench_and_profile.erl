%%% Erlang 函数式编程 Demo 23: benchmark & profile
%%%
%%% BEAM 的性能诊断工具链：
%%%   timer:tc/1,3    —— 粗粒度计时，和 tic/toc 一个级别
%%%   erlang:statistics —— 拿到 reductions / run_queue / io / gc 等运行时统计
%%%   eprof           —— 时间 profiler（per-function wall-clock %）
%%%   fprof           —— 更细粒度，还能给出 call graph 和 own/accumulated time
%%%   cprof           —— 函数调用计数器（轻量）
%%%   tprof (OTP 27+) —— 新一代 profile，统一接口
%%%
%%% 本 Demo 用前四个最常用的工具，对同一个"斐波那契 + 列表翻转"函数做诊断。

-module('23_bench_and_profile').

-export([run/0, fib_slow/1, fib_fast/1, reverse_naive/1, reverse_acc/1]).

%% ============================================================
%% 两对被测函数：慢 vs 快
%% ============================================================

%% 慢版: 指数时间
fib_slow(0) -> 0;
fib_slow(1) -> 1;
fib_slow(N) when N > 1 -> fib_slow(N - 1) + fib_slow(N - 2).

%% 快版: O(n) 迭代
fib_fast(N) when N >= 0 -> fib_fast(N, 0, 1).
fib_fast(0, A, _) -> A;
fib_fast(N, A, B) -> fib_fast(N - 1, B, A + B).

%% 慢版: 每次 ++ 都要重新遍历左边
reverse_naive([]) -> [];
reverse_naive([H | T]) -> reverse_naive(T) ++ [H].

%% 快版: 尾递归 + accumulator
reverse_acc(L) -> reverse_acc(L, []).
reverse_acc([], Acc) -> Acc;
reverse_acc([H | T], Acc) -> reverse_acc(T, [H | Acc]).

%% ============================================================
%% 1) timer:tc —— 最简单的耗时测量
%% ============================================================
bench_tc() ->
    io:format("~n-- timer:tc/3 粗粒度计时 --~n"),
    {T1, _} = timer:tc(?MODULE, fib_slow, [30]),
    {T2, _} = timer:tc(?MODULE, fib_fast, [30]),
    io:format("  fib_slow(30) = ~p us~n", [T1]),
    io:format("  fib_fast(30) = ~p us~n", [T2]),
    io:format("  快慢差距 ≈ ~p 倍~n", [T1 div max(T2, 1)]),

    L = lists:seq(1, 2_000),
    {T3, _} = timer:tc(?MODULE, reverse_naive, [L]),
    {T4, _} = timer:tc(?MODULE, reverse_acc, [L]),
    io:format("  reverse_naive(2k) = ~p us~n", [T3]),
    io:format("  reverse_acc  (2k) = ~p us~n", [T4]),
    ok.

%% ============================================================
%% 2) erlang:statistics —— 看 reductions / gc / run_queue
%% ============================================================
bench_statistics() ->
    io:format("~n-- erlang:statistics --~n"),
    %% reductions 是 BEAM 自己的 CPU 时间片
    erlang:statistics(reductions),   %% 重置
    _ = fib_slow(30),
    {_, Reds} = erlang:statistics(reductions),
    io:format("  fib_slow(30) 消耗 reductions = ~p~n", [Reds]),

    erlang:statistics(reductions),
    _ = fib_fast(30),
    {_, Reds2} = erlang:statistics(reductions),
    io:format("  fib_fast(30) 消耗 reductions = ~p~n", [Reds2]),

    io:format("  run_queue = ~p 个 ready 进程~n", [erlang:statistics(run_queue)]),
    {GCs, WordsReclaimed, _} = erlang:statistics(garbage_collection),
    io:format("  累计 GC 次数 = ~p, 回收字数 = ~p~n", [GCs, WordsReclaimed]),
    ok.

%% ============================================================
%% 3) eprof —— 按函数看 wall-clock 时间占比
%% ============================================================
bench_eprof() ->
    io:format("~n-- eprof (function-level wall time) --~n"),
    eprof:start(),
    eprof:profile(
        fun() ->
                _ = fib_slow(28),
                _ = reverse_naive(lists:seq(1, 1_000)),
                _ = reverse_acc(lists:seq(1, 1_000))
        end),
    eprof:analyze(total),
    eprof:stop(),
    io:format("  ↑ 上面几行是 eprof 自动输出的报表~n"),
    ok.

%% ============================================================
%% 4) fprof —— 带 call-graph 的 profiler
%% ============================================================
bench_fprof() ->
    io:format("~n-- fprof (call graph) --~n"),
    fprof:apply(?MODULE, fib_slow, [25]),
    fprof:profile(),
    TraceFile = "/tmp/demo23_fprof.analysis",
    fprof:analyse([{dest, TraceFile}, {cols, 100}]),
    io:format("  fprof 分析写入: ~s (头 10 行如下)~n", [TraceFile]),
    case file:read_file(TraceFile) of
        {ok, Bin} ->
            Head = lists:sublist(string:split(binary_to_list(Bin), "\n", all), 10),
            [io:format("    ~s~n", [L]) || L <- Head];
        _ -> ok
    end,
    ok.

%% ============================================================
%% 运行器
%% ============================================================
run() ->
    io:format("=== Erlang Demo 23: benchmark & profile ===~n"),
    bench_tc(),
    bench_statistics(),
    bench_eprof(),
    bench_fprof(),
    io:format("~n=== 工具选型 ===~n"),
    io:format("  定位'慢在哪个函数'        => eprof (轻, 一行命令)~n"),
    io:format("  需要调用图/累计时间        => fprof (重, 输出 analysis 文件)~n"),
    io:format("  看'谁被调用了多少次'        => cprof~n"),
    io:format("  看虚拟机/调度器状态         => erlang:statistics + msacc~n"),
    io:format("  OTP 27+ 推荐统一入口        => tprof~n"),
    ok.
