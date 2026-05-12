%%%-------------------------------------------------------------------
%%% Erlang 函数式编程 Demo 1: 模式匹配与递归
%%%
%%% Erlang 的模式匹配是语言的核心——函数参数、变量绑定、消息传递都依赖模式匹配。
%%% 递归是 Erlang 中替代循环的主要方式（Erlang 没有 for/while 循环）。
%%%-------------------------------------------------------------------
-module('01_pattern_matching').
-export([main/0, factorial/1, fibonacci/1, list_sum/1,
         quicksort/1, describe_temp/1, zip/2]).

main() ->
    io:setopts([{encoding, unicode}]),
    io:format("=== 基础模式匹配 ===~n"),
    
    %% 变量绑定就是模式匹配
    {Name, Age} = {"Alice", 30},
    io:format("姓名: ~s, 年龄: ~p~n", [Name, Age]),
    
    %% 列表模式匹配
    [Head | Tail] = [1, 2, 3, 4, 5],
    io:format("头部: ~p, 尾部: ~p~n", [Head, Tail]),
    
    [A, B | Rest] = [10, 20, 30, 40],
    io:format("前两个: ~p, ~p, 其余: ~p~n", [A, B, Rest]),
    
    io:format("~n=== 阶乘 (递归) ===~n"),
    lists:foreach(
        fun(N) -> io:format("~p! = ~p~n", [N, factorial(N)]) end,
        [0, 1, 5, 10]
    ),
    
    io:format("~n=== 斐波那契数列 ===~n"),
    Fibs = [fibonacci(N) || N <- lists:seq(0, 10)],
    io:format("fib(0..10) = ~p~n", [Fibs]),
    
    io:format("~n=== 列表求和 (尾递归) ===~n"),
    io:format("sum([1..10]) = ~p~n", [list_sum(lists:seq(1, 10))]),
    
    io:format("~n=== 快速排序 ===~n"),
    Unsorted = [5, 3, 8, 1, 9, 2, 7, 4, 6],
    io:format("排序前: ~p~n", [Unsorted]),
    io:format("排序后: ~p~n", [quicksort(Unsorted)]),
    
    io:format("~n=== 守卫表达式 (Guards) ===~n"),
    lists:foreach(
        fun(T) -> io:format("~p°C -> ~ts~n", [T, describe_temp(T)]) end,
        [-10, 0, 15, 25, 35, 42]
    ),
    
    io:format("~n=== 自定义 zip ===~n"),
    io:format("zip: ~p~n", [zip([a, b, c], [1, 2, 3])]),
    
    ok.

%% 阶乘 - 经典递归 + 模式匹配
factorial(0) -> 1;
factorial(N) when N > 0 -> N * factorial(N - 1).

%% 斐波那契数列
fibonacci(0) -> 0;
fibonacci(1) -> 1;
fibonacci(N) when N > 1 -> fibonacci(N - 1) + fibonacci(N - 2).

%% 列表求和 - 尾递归优化
list_sum(List) -> list_sum(List, 0).
list_sum([], Acc) -> Acc;
list_sum([H | T], Acc) -> list_sum(T, Acc + H).

%% 快速排序 - Erlang 经典实现，极其简洁
quicksort([]) -> [];
quicksort([Pivot | Rest]) ->
    Left  = [X || X <- Rest, X =< Pivot],
    Right = [X || X <- Rest, X > Pivot],
    quicksort(Left) ++ [Pivot] ++ quicksort(Right).

%% 守卫表达式: 类似 switch 但更强大
describe_temp(T) when T < 0   -> "严寒";
describe_temp(T) when T < 10  -> "寒冷";
describe_temp(T) when T < 20  -> "凉爽";
describe_temp(T) when T < 30  -> "温暖";
describe_temp(T) when T < 40  -> "炎热";
describe_temp(_)               -> "极端高温".

%% 自定义 zip 函数
zip([], _) -> [];
zip(_, []) -> [];
zip([H1 | T1], [H2 | T2]) -> [{H1, H2} | zip(T1, T2)].
