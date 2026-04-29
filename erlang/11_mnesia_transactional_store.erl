%%%-------------------------------------------------------------------
%%% Erlang 函数式编程 Demo 11: Mnesia —— BEAM 原生分布式事务数据库
%%%
%%% 06 号 Demo 用 ETS 说明了"进程外共享状态"，
%%% 但 ETS 没有事务、没有跨节点、重启就丢。
%%% Mnesia 解决了这三点，而且是 OTP 内置：
%%%   * 表可选存内存 / 磁盘 / 内存+磁盘
%%%   * 事务 (ACID): transaction/1，失败自动回滚
%%%   * 天然分布式：多节点复制同一张表
%%%   * 查询: QLC (Query List Comprehension) 或二级索引
%%%
%%% 本 Demo 聚焦单节点 + 内存表，把"事务 / 索引 / QLC"三条线展示清楚：
%%%   1. 建表 (ram_copies)
%%%   2. 单行 CRUD
%%%   3. transaction: 转账，中途失败自动回滚
%%%   4. 二级索引 & QLC 查询
%%%
%%% 运行：
%%%   erl -compile 11_mnesia_transactional_store.erl
%%%   erl -noshell -s mnesia_transactional_store main -s init stop
%%%-------------------------------------------------------------------
-module(mnesia_transactional_store).

%% 包含 QLC 查询宏
-include_lib("stdlib/include/qlc.hrl").

-export([main/0]).

%% 账户表的记录定义
-record(account, {id, owner, balance}).
%% 交易流水
-record(tx_log,  {id, from, to, amount, ts}).

%% ============================================================
%% 1. 初始化 schema 和表（内存版, 单节点）
%% ============================================================

setup() ->
    %% 启动 mnesia (单节点, ram_copies 不需要预先 create_schema 到磁盘)
    ok = mnesia:start(),

    %% 如果表已存在就删掉，保证每次跑都是干净的
    lists:foreach(fun(T) -> catch mnesia:delete_table(T) end, [account, tx_log]),

    {atomic, ok} = mnesia:create_table(account,
        [{attributes, record_info(fields, account)},
         {ram_copies, [node()]},
         {index, [owner]}]),                       %% owner 作为二级索引

    {atomic, ok} = mnesia:create_table(tx_log,
        [{attributes, record_info(fields, tx_log)},
         {ram_copies, [node()]},
         {type, ordered_set}]),
    ok.

%% ============================================================
%% 2. CRUD —— 都要包在 transaction 里
%% ============================================================

open_account(Id, Owner, Balance) ->
    F = fun() -> mnesia:write(#account{id = Id, owner = Owner, balance = Balance}) end,
    {atomic, ok} = mnesia:transaction(F),
    ok.

get_account(Id) ->
    F = fun() -> mnesia:read({account, Id}) end,
    {atomic, R} = mnesia:transaction(F),
    R.

%% ============================================================
%% 3. 事务 —— 转账，中途失败要回滚
%% ============================================================

transfer(From, To, Amount) ->
    F = fun() ->
        [SrcAcc] = mnesia:read({account, From}),
        [DstAcc] = mnesia:read({account, To}),
        case SrcAcc#account.balance >= Amount of
            false ->
                %% abort 会让整个事务回滚，连前面 read 都当作没发生
                mnesia:abort({insufficient_funds, From, SrcAcc#account.balance, Amount});
            true ->
                mnesia:write(SrcAcc#account{balance = SrcAcc#account.balance - Amount}),
                mnesia:write(DstAcc#account{balance = DstAcc#account.balance + Amount}),
                mnesia:write(#tx_log{id = erlang:unique_integer([monotonic, positive]),
                                     from = From, to = To, amount = Amount,
                                     ts = erlang:system_time(millisecond)}),
                ok
        end
    end,
    mnesia:transaction(F).

%% ============================================================
%% 4. 查询：二级索引 & QLC
%% ============================================================

find_by_owner(Owner) ->
    F = fun() -> mnesia:index_read(account, Owner, #account.owner) end,
    {atomic, R} = mnesia:transaction(F),
    R.

%% QLC —— 像列表推导一样写数据库查询, 比 select 可读性强
high_balance_owners(Threshold) ->
    F = fun() ->
        Q = qlc:q([ {A#account.owner, A#account.balance}
                   || A <- mnesia:table(account),
                      A#account.balance >= Threshold ]),
        qlc:e(Q)
    end,
    {atomic, R} = mnesia:transaction(F),
    R.

list_tx_logs() ->
    F = fun() ->
        Q = qlc:q([ L || L <- mnesia:table(tx_log) ]),
        qlc:e(Q)
    end,
    {atomic, R} = mnesia:transaction(F),
    R.

%% ============================================================
%% main
%% ============================================================

main() ->
    io:format("=== Mnesia 事务与分布式存储 ===~n"),
    setup(),

    %% 开三个账户
    open_account(1, "alice", 100),
    open_account(2, "bob",   50),
    open_account(3, "carol", 200),

    io:format("~n-- 初始余额 --~n"),
    lists:foreach(fun(Id) -> io:format("  ~p~n", [get_account(Id)]) end, [1,2,3]),

    %% 正常转账
    io:format("~n-- 转账 alice -> bob 30 --~n"),
    R1 = transfer(1, 2, 30),
    io:format("  结果: ~p~n", [R1]),
    lists:foreach(fun(Id) -> io:format("  ~p~n", [get_account(Id)]) end, [1,2,3]),

    %% 余额不够, 事务 abort, 账上不应有任何变化
    io:format("~n-- 转账 alice -> carol 999 (应该失败回滚) --~n"),
    R2 = transfer(1, 3, 999),
    io:format("  结果: ~p~n", [R2]),
    lists:foreach(fun(Id) -> io:format("  ~p~n", [get_account(Id)]) end, [1,2,3]),

    %% 二级索引 & QLC
    io:format("~n-- 索引查找 owner=bob --~n  ~p~n", [find_by_owner("bob")]),
    io:format("~n-- 余额 >= 80 的账户 --~n  ~p~n", [high_balance_owners(80)]),

    io:format("~n-- 交易流水 --~n"),
    lists:foreach(fun(L) -> io:format("  ~p~n", [L]) end, list_tx_logs()),

    mnesia:stop(),
    io:format("~n=== 重点 ===~n"),
    io:format("  * 所有读写必须包在 mnesia:transaction/1 里才有 ACID~n"),
    io:format("  * mnesia:abort/1 会让整段事务原子回滚, 天然做业务一致性~n"),
    io:format("  * ram_copies / disc_copies / disc_only_copies 三种表类型覆盖缓存到持久化~n"),
    io:format("  * 多节点只需在 create_table 里写 {ram_copies, [node1, node2, ...]}, 自动同步~n"),
    io:format("  * 对照: Scala 99 号 Doobie 事务 Inbox / Haskell STM / Rust sqlx tx~n"),
    ok.
