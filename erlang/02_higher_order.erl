%%%-------------------------------------------------------------------
%%% Erlang 函数式编程 Demo 2: 高阶函数与列表推导
%%%
%%% Erlang 中的 fun (匿名函数) 和 lists 模块提供了强大的函数式操作。
%%% 列表推导 (List Comprehension) 是 Erlang 最优雅的特性之一。
%%%-------------------------------------------------------------------
-module('02_higher_order').
-export([main/0, my_map/2, my_filter/2, my_foldl/3, compose/2]).

main() ->
    Numbers = lists:seq(1, 10),
    
    io:format("=== 高阶函数 ===~n"),
    io:format("原始列表: ~p~n", [Numbers]),
    
    %% map: 对每个元素应用函数
    Doubled = lists:map(fun(X) -> X * 2 end, Numbers),
    io:format("每个翻倍: ~p~n", [Doubled]),
    
    Squared = lists:map(fun(X) -> X * X end, Numbers),
    io:format("每个平方: ~p~n", [Squared]),
    
    %% filter: 过滤元素
    Evens = lists:filter(fun(X) -> X rem 2 == 0 end, Numbers),
    io:format("偶数: ~p~n", [Evens]),
    
    %% foldl: 左折叠
    Sum = lists:foldl(fun(X, Acc) -> X + Acc end, 0, Numbers),
    io:format("求和: ~p~n", [Sum]),
    
    %% 链式操作: 找出偶数 -> 平方 -> 求和
    Result = lists:foldl(
        fun(X, Acc) -> X + Acc end,
        0,
        lists:map(
            fun(X) -> X * X end,
            lists:filter(fun(X) -> X rem 2 == 0 end, Numbers)
        )
    ),
    io:format("偶数平方和: ~p~n", [Result]),
    
    io:format("~n=== 列表推导 (List Comprehension) ===~n"),
    
    %% 基本列表推导
    Squares = [X * X || X <- lists:seq(1, 5)],
    io:format("平方数: ~p~n", [Squares]),
    
    %% 带条件过滤
    EvenSquares = [X * X || X <- lists:seq(1, 10), X rem 2 == 0],
    io:format("偶数的平方: ~p~n", [EvenSquares]),
    
    %% 笛卡尔积
    Pairs = [{X, Y} || X <- [1, 2, 3], Y <- [a, b]],
    io:format("笛卡尔积: ~p~n", [Pairs]),
    
    %% 勾股数 (Pythagorean Triples)
    Pyth = [{A, B, C} || C <- lists:seq(1, 20),
                          B <- lists:seq(1, C),
                          A <- lists:seq(1, B),
                          A*A + B*B == C*C],
    io:format("勾股数 (C<=20): ~p~n", [Pyth]),
    
    io:format("~n=== 自定义高阶函数 ===~n"),
    
    %% 自定义 map
    MyDoubled = my_map(fun(X) -> X * 2 end, [1, 2, 3, 4, 5]),
    io:format("my_map 翻倍: ~p~n", [MyDoubled]),
    
    %% 自定义 filter
    MyEvens = my_filter(fun(X) -> X rem 2 == 0 end, lists:seq(1, 10)),
    io:format("my_filter 偶数: ~p~n", [MyEvens]),
    
    %% 函数组合
    Double = fun(X) -> X * 2 end,
    AddOne = fun(X) -> X + 1 end,
    DoubleThenAdd = compose(AddOne, Double),
    io:format("compose(+1, *2)(5) = ~p~n", [DoubleThenAdd(5)]),
    
    ok.

%% 自定义 map - 递归实现
my_map(_F, []) -> [];
my_map(F, [H | T]) -> [F(H) | my_map(F, T)].

%% 自定义 filter - 递归实现
my_filter(_F, []) -> [];
my_filter(F, [H | T]) ->
    case F(H) of
        true  -> [H | my_filter(F, T)];
        false -> my_filter(F, T)
    end.

%% 自定义 foldl - 递归实现
my_foldl(_F, Acc, []) -> Acc;
my_foldl(F, Acc, [H | T]) -> my_foldl(F, F(H, Acc), T).

%% 函数组合
compose(F, G) -> fun(X) -> F(G(X)) end.
