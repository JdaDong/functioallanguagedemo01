%%%-------------------------------------------------------------------
%%% Erlang 函数式编程 Demo 8: 属性测试 —— 自制迷你 PropEr
%%%
%%% Haskell 有 QuickCheck，Scala 有 ScalaCheck，Erlang 有 PropEr / eqc。
%%% 它们的共同直觉：
%%%   * 不再写"一组固定输入 -> 期望输出"
%%%   * 而是声明"对任意输入应该成立的性质 (property)"
%%%   * 框架自动生成上百条随机输入来找反例
%%%   * 找到反例后自动"缩小 (shrinking)"到最小反例
%%%
%%% 本 Demo 不依赖 proper / eqc，手写一个最小 property runner 来
%%% 说明直觉；想用生产版，把 check/3 换成 proper:quickcheck/1 即可。
%%%
%%% 运行：
%%%   erl -compile 08_property_testing_proper.erl
%%%   erl -noshell -s property_testing_proper main -s init stop
%%%-------------------------------------------------------------------
-module(property_testing_proper).
-export([main/0]).

%% ============================================================
%% 1. 生成器 (generator) —— 随机构造测试输入
%% ============================================================

gen_int()        -> rand:uniform(200) - 100.
gen_nat()        -> rand:uniform(1000).
gen_list(0)      -> [];
gen_list(N)      -> [gen_int() | gen_list(N - 1)].
gen_any_list()   -> gen_list(rand:uniform(20)).
gen_small_pair() -> {rand:uniform(50), rand:uniform(50)}.

%% ============================================================
%% 2. property runner —— 跑 N 次，找到反例就 shrink
%% ============================================================

check(Name, Gen, Prop) ->
    check(Name, Gen, Prop, 100).

check(Name, Gen, Prop, N) ->
    case find_counterexample(Gen, Prop, N) of
        ok ->
            io:format("  [OK]   ~ts: ~p cases passed~n", [Name, N]);
        {bad, Input} ->
            Shrunk = shrink(Input, Prop),
            io:format("  [FAIL] ~ts: counterexample = ~p  (shrunk from ~p)~n",
                      [Name, Shrunk, Input])
    end.

find_counterexample(_, _, 0) -> ok;
find_counterexample(Gen, Prop, N) ->
    Input = Gen(),
    case safe_apply(Prop, Input) of
        true  -> find_counterexample(Gen, Prop, N - 1);
        false -> {bad, Input}
    end.

safe_apply(Prop, Input) ->
    try Prop(Input) of
        Bool when is_boolean(Bool) -> Bool;
        _ -> false
    catch _:_ -> false
    end.

%% ---------- 极简 shrinker ----------
%% 真正的 PropEr 会根据类型做递归结构化 shrink；
%% 这里给 list / int / tuple 三种类型各实现最朴素的"去掉一个元素 / 向 0 靠"

shrink(L, Prop) when is_list(L) -> shrink_list(L, Prop, L);
shrink(N, Prop) when is_integer(N) -> shrink_int(N, Prop, N);
shrink(T, Prop) when is_tuple(T) ->
    Lst = tuple_to_list(T),
    list_to_tuple(shrink_list(Lst, fun(X) -> Prop(list_to_tuple(X)) end, Lst));
shrink(X, _) -> X.

shrink_list([], _, Acc) -> Acc;
shrink_list(L, Prop, Acc) ->
    Candidates = [drop_at(I, L) || I <- lists:seq(1, length(L))],
    Failing    = [C || C <- Candidates, not safe_apply(Prop, C)],
    case Failing of
        []    -> Acc;
        [H|_] -> shrink_list(H, Prop, H)
    end.

shrink_int(0, _, Acc) -> Acc;
shrink_int(N, Prop, Acc) when N > 0 ->
    Cand = N div 2,
    case safe_apply(Prop, Cand) of
        false -> shrink_int(Cand, Prop, Cand);
        true  -> Acc
    end;
shrink_int(N, Prop, Acc) when N < 0 ->
    Cand = N div 2,
    case safe_apply(Prop, Cand) of
        false -> shrink_int(Cand, Prop, Cand);
        true  -> Acc
    end.

drop_at(I, L) ->
    {H, [_|T]} = lists:split(I - 1, L),
    H ++ T.

%% ============================================================
%% 3. 真·性质 —— 故意埋一个 bug 给框架找
%% ============================================================

%% 性质 1: reverse . reverse == id
prop_reverse_involution(L) ->
    lists:reverse(lists:reverse(L)) =:= L.

%% 性质 2: sort 后每对相邻元素有序
prop_sort_ordered(L) ->
    S = lists:sort(L),
    is_ordered(S).

is_ordered([]) -> true;
is_ordered([_]) -> true;
is_ordered([A, B | T]) when A =< B -> is_ordered([B | T]);
is_ordered(_) -> false.

%% 性质 3: sort 保持长度
prop_sort_length(L) ->
    length(lists:sort(L)) =:= length(L).

%% 性质 4（故意埋 bug）：自定义加法 buggy_add(A,B) 当 A+B >= 100 时会 -1
buggy_add(A, B) when A + B >= 100 -> A + B - 1;
buggy_add(A, B) -> A + B.

prop_commutative(_) ->
    %% 从全局生成器拿两个小正整数
    {A, B} = gen_small_pair(),
    buggy_add(A, B) =:= buggy_add(B, A).

prop_identity(_) ->
    %% 对 buggy_add 来说这条也不恒成立
    N = gen_nat(),
    buggy_add(N, 0) =:= N.

%% ============================================================
%% main
%% ============================================================

main() ->
    io:format("=== 随机属性测试 (mini PropEr) ===~n"),

    check("reverse . reverse == id",
          fun gen_any_list/0,
          fun prop_reverse_involution/1),

    check("sort 结果是有序的",
          fun gen_any_list/0,
          fun prop_sort_ordered/1),

    check("sort 保持长度",
          fun gen_any_list/0,
          fun prop_sort_length/1),

    check("buggy_add 交换律",
          fun() -> ignore end,
          fun prop_commutative/1,
          50),

    check("buggy_add 零元",
          fun() -> ignore end,
          fun prop_identity/1,
          50),

    io:format("~n=== 重点 ===~n"),
    io:format("  * 属性测试的精髓: 用『性质』描述正确性, 让机器去找反例~n"),
    io:format("  * shrinking 把反例缩到最小, 这才是比单元测试『爆炸性强』的地方~n"),
    io:format("  * 真用生产版: 把 check/3 换成 proper:quickcheck/1 或 triq:check/1~n"),
    io:format("  * 对照: Haskell QuickCheck (Demo 12)  |  ScalaCheck / scalatest prop-based~n"),
    ok.
