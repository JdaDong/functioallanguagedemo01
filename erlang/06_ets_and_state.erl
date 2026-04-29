%%%-------------------------------------------------------------------
%%% Erlang 函数式编程 Demo 6: ETS —— 纯 FP 在 BEAM 上的边界
%%%
%%% Erlang 进程里是纯粹的不可变：一切"状态"都靠递归 + 消息传递维护。
%%% 但有时候多进程需要共享一张"表"（配置、缓存、用户在线状态……），
%%% 此时用消息通信虽然正确但会让单个 Actor 成瓶颈。
%%%
%%% BEAM 提供的解决方案就是 ETS (Erlang Term Storage):
%%%   * 进程外的内存表，本质是 C 实现的哈希表/树
%%%   * 默认只有 owner 能写，其他进程可以随意读 (public 表则随意读写)
%%%   * 读操作 O(1) 且几乎零 GC 压力
%%%
%%% 这就是 FP 世界里"Shared Mutable State 的被规训版"：
%%%   不可变是默认，但有一块"明示的、受控的"可变区作为性能后门。
%%%
%%% 本 Demo：
%%%   a) 基础 CRUD
%%%   b) 并发读 (多个进程一起 lookup)
%%%   c) match / select 函数式查询
%%%-------------------------------------------------------------------
-module(ets_and_state).
-export([main/0]).

main() ->
    io:format("=== 1. ETS 基础 CRUD ===~n"),
    Tab = ets:new(users, [set, public, named_table, {keypos, 1}]),
    ets:insert(Tab, {1001, "Alice",   "VIP"}),
    ets:insert(Tab, {1002, "Bob",     "Normal"}),
    ets:insert(Tab, {1003, "Charlie", "VIP"}),
    ets:insert(Tab, {1004, "Diana",   "Normal"}),

    [{_, Name, Grade}] = ets:lookup(Tab, 1003),
    io:format("  lookup(1003) = ~p (~p)~n", [Name, Grade]),

    ets:insert(Tab, {1002, "Bob",     "VIP"}),   %% 覆盖更新
    [{_, _, NewGrade}] = ets:lookup(Tab, 1002),
    io:format("  updated(1002) grade = ~p~n", [NewGrade]),

    ets:delete(Tab, 1004),
    io:format("  after delete(1004), size = ~p~n", [ets:info(Tab, size)]),

    io:format("~n=== 2. 函数式查询: match / select ===~n"),
    %% 用 match 模式：第 3 位是 "VIP" 的所有记录
    VipMatch = ets:match(Tab, {'$1', '$2', "VIP"}),
    io:format("  VIP 用户 (match): ~p~n", [VipMatch]),

    %% select 更强大：支持 guard
    Guard = [{{'$1', '$2', '$3'},
              [{'=:=', '$3', "VIP"}],
              [{{'$1', '$2'}}]}],
    VipSel = ets:select(Tab, Guard),
    io:format("  VIP 用户 (select): ~p~n", [VipSel]),

    io:format("~n=== 3. 多进程并发读 ===~n"),
    Parent = self(),
    Ids = [1001, 1002, 1003],
    lists:foreach(
      fun(Id) ->
          spawn(fun() ->
              %% 注意：并发读 public 表不需要加锁
              case ets:lookup(Tab, Id) of
                  [{_, N, G}] -> Parent ! {done, Id, N, G};
                  []          -> Parent ! {done, Id, not_found, none}
              end
          end)
      end, Ids),
    collect(length(Ids)),

    ets:delete(Tab),
    io:format("~n=== 重点 ===~n"),
    io:format("  * ETS = 受控的共享可变状态；默认仍鼓励消息传递，ETS 只做性能后门~n"),
    io:format("  * match / select 让你用『纯模式』描述查询，和 Haskell list comprehension 直觉一致~n"),
    io:format("  * 对照: Scala cats-effect Ref / MapRef；Rust Arc<DashMap>；Haskell STM TVar~n"),
    ok.

collect(0) -> ok;
collect(N) ->
    receive
        {done, Id, Name, Grade} ->
            io:format("  reader got ~p -> ~p / ~p~n", [Id, Name, Grade]),
            collect(N - 1)
    after 1000 -> io:format("  timeout~n")
    end.
