%%% Erlang 函数式编程 Demo 20: NIF vs Port（BEAM 调外部世界的两种姿势）
%%%
%%% BEAM 是虚拟机，内建模块不方便做的事（压缩、加密、GPU、C 库），
%%% 有两条路跳到原生代码：
%%%
%%%   方式          | 同进程 | 速度 | 危险度           | 典型用法
%%%   ------------- | ------ | ---- | ---------------- | ------------------
%%%   NIF           | 是     | 极快 | 崩 NIF 会砸 VM   | crypto、asn1、jiffy
%%%   Port          | 否     | 一般 | 独立进程，安全   | os:cmd、外部可执行
%%%   port_driver   | 是     | 快   | 需要写 driver    | 老式做法
%%%
%%% 本 Demo 不真写 C 代码（需要 GCC），而是：
%%%   - 用现成的 NIF（crypto 模块、erlang:term_to_binary）演示 NIF 的感觉
%%%   - 用 open_port/2 真正拉起一个 OS 外部进程来演示 Port 语义

-module('20_nif_and_port').

-export([run/0, demo_nif/0, demo_port/0, demo_port_line/1]).

%% ============================================================
%% 1) NIF 侧：直接用 OTP 自带的 NIF 模块感受“原生速度”
%% ============================================================
demo_nif() ->
    io:format("~n-- NIF: crypto:hash/2 (SHA-256) --~n"),

    %% crypto 模块绝大多数函数就是 NIF
    Data = <<"hello NIF world">>,
    Hash = crypto:hash(sha256, Data),
    io:format("  sha256(~p) = ~s~n", [Data, binary_to_hex(Hash)]),

    io:format("~n-- NIF: term_to_binary/1 + binary_to_term/1 --~n"),
    Term = #{msg => "hello", nums => [1,2,3], tuple => {a, b, c}},
    Bin  = erlang:term_to_binary(Term),
    Back = erlang:binary_to_term(Bin),
    io:format("  term         = ~p~n", [Term]),
    io:format("  bin (~p bytes) = ~w~n", [byte_size(Bin), Bin]),
    io:format("  roundtrip    = ~p~n", [Back]),
    Term = Back,

    io:format("~n-- NIF 的风险对照 --~n"),
    io:format("  - 同进程、同调度器，没上下文切换 => 快~n"),
    io:format("  - 但 NIF 里 segfault 会带着 BEAM 一起挂~n"),
    io:format("  - 长 NIF 会阻塞调度器 => 要用 dirty scheduler 或 enif_schedule_nif~n"),
    ok.

binary_to_hex(Bin) ->
    lists:flatten([io_lib:format("~2.16.0b", [B]) || <<B>> <= Bin]).

%% ============================================================
%% 2) Port 侧：拉一个外部可执行文件跑
%% ============================================================
demo_port() ->
    io:format("~n-- Port: open_port({spawn, \"echo ...\"}) --~n"),

    %% 用 shell echo 作为“外部程序”
    Port = open_port({spawn, "echo hello-from-external-process"},
                     [exit_status, stderr_to_stdout, {line, 1024}]),

    Result = collect_port(Port, []),
    io:format("  外部进程返回: ~p~n", [Result]),

    io:format("~n-- Port 的核心语义 --~n"),
    io:format("  - open_port 返回的不是 pid 而是 port, 但消息语义类似 mailbox~n"),
    io:format("  - 外部进程崩溃 == 收到 {Port, {exit_status, N}}, BEAM 毫发无伤~n"),
    io:format("  - 数据流通过 {Port, {data, Bin}} 消息送达, 天然异步~n"),
    ok.

collect_port(Port, Acc) ->
    receive
        {Port, {data, {eol, Line}}} ->
            collect_port(Port, [Line | Acc]);
        {Port, {data, {noeol, Line}}} ->
            collect_port(Port, [Line | Acc]);
        {Port, {exit_status, Status}} ->
            {ok, Status, lists:reverse(Acc)}
    after 2000 ->
        {timeout, lists:reverse(Acc)}
    end.

demo_port_line(Cmd) ->
    Port = open_port({spawn, Cmd},
                     [exit_status, stderr_to_stdout, {line, 1024}]),
    collect_port(Port, []).

%% ============================================================
%% 运行器
%% ============================================================
run() ->
    io:format("=== Erlang Demo 20: NIF vs Port ===~n"),
    demo_nif(),
    demo_port(),
    io:format("~n=== 决策树 ===~n"),
    io:format("  性能要求极高、C 库也稳定 => NIF (或 dirty NIF)~n"),
    io:format("  外部程序/脚本、不稳定的第三方可执行 => Port~n"),
    io:format("  需要零拷贝共享大块二进制 => NIF + 资源对象 (enif_alloc_resource)~n"),
    ok.
