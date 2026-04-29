%%% Erlang 函数式编程 Demo 21: DETS 与 disk_log（轻量持久化）
%%%
%%% 11 号 Demo 讲了 Mnesia（支持事务、分布式的 DBMS），但有时你只是想：
%%%   “我只有一台机器，写点 KV / append-only 的日志，别整那么重。”
%%% 这时候就用 OTP 内建的两个模块：
%%%   - DETS      == 磁盘版 ETS，接口几乎一样，但文件级持久
%%%   - disk_log  == 官方的 append-only 日志（类似 journald / kafka segment）

-module('21_dets_and_disc_log').

-export([run/0, demo_dets/0, demo_disk_log/0]).

%% ============================================================
%% 1) DETS：磁盘版 ETS
%% ============================================================
demo_dets() ->
    io:format("~n-- DETS: 磁盘 KV --~n"),
    File = "/tmp/demo21_dets.tab",
    file:delete(File),

    {ok, Ref} = dets:open_file(users, [{file, File}, {type, set}]),
    ok = dets:insert(Ref, {1, "alice"}),
    ok = dets:insert(Ref, {2, "bob"}),
    ok = dets:insert(Ref, {3, "carol"}),

    [{1, "alice"}] = dets:lookup(Ref, 1),
    io:format("  lookup(1) = ~p~n", [dets:lookup(Ref, 1)]),
    io:format("  size      = ~p~n", [dets:info(Ref, size)]),

    %% 关闭 + 重开：数据仍然在
    ok = dets:close(Ref),
    {ok, Ref2} = dets:open_file(users, [{file, File}, {type, set}]),
    io:format("  reopen lookup(2) = ~p  (持久化验证)~n", [dets:lookup(Ref2, 2)]),

    %% 遍历
    All = dets:foldl(fun(Obj, Acc) -> [Obj | Acc] end, [], Ref2),
    io:format("  all = ~p~n", [lists:sort(All)]),

    ok = dets:close(Ref2),
    io:format("  DETS 适合单机、中小规模、想要 O(1) 查询的场景~n"),
    ok.

%% ============================================================
%% 2) disk_log：append-only 日志
%% ============================================================
demo_disk_log() ->
    io:format("~n-- disk_log: append-only 日志 --~n"),
    File = "/tmp/demo21_disk_log",

    %% 清理旧文件
    file:delete(File),
    file:delete(File ++ ".1"),
    file:delete(File ++ ".2"),
    file:delete(File ++ ".siz"),
    file:delete(File ++ ".idx"),

    {ok, Log} = disk_log:open([
        {name,   demo_log},
        {file,   File},
        {type,   wrap},              %% wrap = ring buffer 式
        {size,   {512, 3}},          %% 每个文件 512 字节，最多 3 个
        {format, internal}           %% 写 Erlang term
    ]),

    %% 连续写 20 条，会自动 wrap
    [ok = disk_log:log(Log, {event, I, erlang:system_time(millisecond)})
     || I <- lists:seq(1, 20)],
    ok = disk_log:sync(Log),

    %% 从头读
    {Cont, First} = disk_log:chunk(Log, start),
    io:format("  first chunk size = ~p~n", [length(First)]),
    io:format("  first 3 items    = ~p~n", [lists:sublist(First, 3)]),
    _ = drain(Log, Cont),

    ok = disk_log:close(Log),
    io:format("  disk_log 适合: audit / append-only 事件流 / 故障现场快照~n"),
    ok.

drain(Log, Cont) ->
    case disk_log:chunk(Log, Cont) of
        eof -> ok;
        {Next, Items} ->
            io:format("  chunk items = ~p~n", [length(Items)]),
            drain(Log, Next)
    end.

%% ============================================================
%% 运行器
%% ============================================================
run() ->
    io:format("=== Erlang Demo 21: DETS & disk_log ===~n"),
    demo_dets(),
    demo_disk_log(),
    io:format("~n=== 选型速查 ===~n"),
    io:format("  内存 KV、进程共享 .......... ETS (Demo 06)~n"),
    io:format("  磁盘 KV、单机、小规模 ....... DETS (本 Demo)~n"),
    io:format("  追加日志、滚动文件 .......... disk_log (本 Demo)~n"),
    io:format("  事务、二级索引、集群 ........ Mnesia (Demo 11)~n"),
    io:format("  重负载关系型 ................ 外部 Postgres + epgsql~n"),
    ok.
