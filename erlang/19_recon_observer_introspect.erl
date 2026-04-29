%%% Erlang 函数式编程 Demo 19: BEAM 运行时自省（recon 同款思路）
%%%
%%% 线上排障两大杀手锏：
%%%   1) 某个进程邮箱堆爆 -> 看 message_queue_len
%%%   2) 某个进程 reduction 飙升 -> 看 reductions
%%% BEAM 把这些运行时信息都暴露在 erlang:process_info / system_info / ets:info 里，
%%% 不需要停机、不需要外挂工具就能抓到。
%%%
%%% 生产上常用 recon 库（github.com/ferd/recon）封装了 top-N 视角，
%%% 本 Demo 用纯 erlang 模块复刻核心思路，跑 demo19_recon:run/0 即可。

-module('19_recon_observer_introspect').

-export([run/0, top_by/2, ets_summary/0, system_snapshot/0]).

%% ============================================================
%% 1) 造几类“有个性”的进程：忙碌 / 邮箱堆积 / 闲置
%% ============================================================
spawn_busy(Name) ->
    spawn(fun Loop() ->
                  register_if_free(Name),
                  %% 不停做无意义计算，拉高 reductions
                  lists:sum(lists:seq(1, 10_000)),
                  receive stop -> ok after 0 -> Loop() end
          end).

spawn_mailbox_hoarder(Name) ->
    Pid = spawn(fun Loop() ->
                        receive stop -> ok
                        after 5_000 -> Loop()  %% 5 秒才检查一次，邮箱会越堆越高
                        end
                end),
    register(Name, Pid),
    %% 往这个进程疯狂塞消息
    [Pid ! {noise, I} || I <- lists:seq(1, 500)],
    Pid.

spawn_idle(Name) ->
    Pid = spawn(fun() -> receive stop -> ok end end),
    register(Name, Pid),
    Pid.

register_if_free(Name) ->
    case whereis(Name) of
        undefined -> catch register(Name, self());
        _         -> ok
    end.

%% ============================================================
%% 2) 自省函数：对所有进程按某个 key 取 top-N
%% ============================================================
%% Keys 常见值：message_queue_len | reductions | memory | heap_size
top_by(Key, N) ->
    Entries =
        [begin
             case erlang:process_info(Pid, [Key, registered_name, current_function]) of
                 undefined -> skip;
                 Info ->
                     Val = proplists:get_value(Key, Info),
                     Name = proplists:get_value(registered_name, Info, []),
                     MFA  = proplists:get_value(current_function, Info),
                     {Val, Pid, Name, MFA}
             end
         end || Pid <- processes()],
    Valid = [E || E <- Entries, E =/= skip],
    Sorted = lists:reverse(lists:sort(Valid)),
    lists:sublist(Sorted, N).

%% ============================================================
%% 3) ETS 自省
%% ============================================================
ets_summary() ->
    Tables = ets:all(),
    [{T,
      ets:info(T, name),
      ets:info(T, size),
      ets:info(T, memory)}
     || T <- Tables].

%% ============================================================
%% 4) 系统级自省
%% ============================================================
system_snapshot() ->
    #{
        process_count    => erlang:system_info(process_count),
        process_limit    => erlang:system_info(process_limit),
        schedulers       => erlang:system_info(schedulers),
        scheduler_online => erlang:system_info(schedulers_online),
        otp_release      => erlang:system_info(otp_release),
        atom_count       => erlang:system_info(atom_count),
        port_count       => erlang:system_info(port_count),
        memory           => erlang:memory()
    }.

%% ============================================================
%% 运行器
%% ============================================================
run() ->
    io:format("=== Erlang Demo 19: 运行时自省 ===~n"),

    %% 准备“有特征”的进程
    Busy   = spawn_busy(demo_busy),
    Mailbox = spawn_mailbox_hoarder(demo_mailbox),
    Idle   = spawn_idle(demo_idle),
    timer:sleep(200),  %% 让 busy 多跑几轮

    %% 1) 邮箱 top-5
    io:format("~n-- top 5 by message_queue_len --~n"),
    lists:foreach(fun print_entry/1, top_by(message_queue_len, 5)),

    %% 2) reductions top-5
    io:format("~n-- top 5 by reductions --~n"),
    lists:foreach(fun print_entry/1, top_by(reductions, 5)),

    %% 3) memory top-5
    io:format("~n-- top 5 by memory --~n"),
    lists:foreach(fun print_entry/1, top_by(memory, 5)),

    %% 4) ETS
    io:format("~n-- ets summary --~n"),
    lists:foreach(
        fun({T, Name, Size, Mem}) ->
            io:format("  table=~p name=~p size=~p memory=~p words~n", [T, Name, Size, Mem])
        end,
        lists:sublist(ets_summary(), 5)),

    %% 5) 系统级
    io:format("~n-- system snapshot --~n"),
    maps:foreach(fun(K, V) -> io:format("  ~p = ~p~n", [K, V]) end,
                 system_snapshot()),

    %% 清场
    [catch P ! stop || P <- [Busy, Mailbox, Idle]],

    io:format("~n=== 重点理解 ===~n"),
    io:format("- erlang:process_info/2 是 BEAM 给外面看每个进程内部的唯一官方通道~n"),
    io:format("- message_queue_len 堆积 => 典型反压失败或慢消费者信号~n"),
    io:format("- reductions 是 BEAM 自己的“虚拟 CPU 时间片”, 排 CPU 热点用它~n"),
    io:format("- 生产上直接用 recon:proc_count/2、recon:info/1，语义和这里等价~n"),
    ok.

print_entry({Val, Pid, Name, MFA}) ->
    io:format("  ~12w  pid=~w  name=~p  at=~p~n", [Val, Pid, Name, MFA]).
