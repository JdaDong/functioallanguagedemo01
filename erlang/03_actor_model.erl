%%%-------------------------------------------------------------------
%%% Erlang 函数式编程 Demo 3: 进程与消息传递 (Actor 模型)
%%%
%%% Erlang 最著名的特性——轻量级进程和消息传递。
%%% 这是函数式编程与并发编程的完美结合 (Actor Model)。
%%% 每个进程是独立的，不共享状态，只通过消息通信。
%%%-------------------------------------------------------------------
-module(actor_model).
-export([main/0, counter/1, calculator/0, ping_pong/0]).

main() ->
    io:format("=== Actor 模型: 计数器 ===~n"),
    
    %% 启动一个计数器进程
    Counter = spawn(fun() -> counter(0) end),
    
    %% 发送消息
    Counter ! increment,
    Counter ! increment,
    Counter ! increment,
    Counter ! decrement,
    Counter ! {get, self()},
    
    receive
        {count, Value} -> io:format("计数器值: ~p~n", [Value])
    after 1000 -> io:format("超时~n")
    end,
    
    Counter ! stop,
    
    io:format("~n=== Actor 模型: 计算器 ===~n"),
    calculator(),
    
    io:format("~n=== Ping-Pong ===~n"),
    ping_pong(),
    
    timer:sleep(500),
    ok.

%% ========== 计数器 Actor ==========
%% 进程通过递归保持状态（而非可变变量）
counter(Count) ->
    receive
        increment ->
            io:format("  计数器 +1, 当前: ~p~n", [Count + 1]),
            counter(Count + 1);   %% 递归调用，传入新状态
        decrement ->
            io:format("  计数器 -1, 当前: ~p~n", [Count - 1]),
            counter(Count - 1);
        {get, Pid} ->
            Pid ! {count, Count},
            counter(Count);
        stop ->
            io:format("  计数器停止~n"),
            ok                    %% 不再递归 = 进程结束
    end.

%% ========== 计算器 Actor ==========
calculator() ->
    Calc = spawn(fun() -> calc_loop() end),
    
    %% 发送计算请求并接收结果
    Calculations = [
        {add, 10, 3},
        {sub, 10, 3},
        {mul, 10, 3},
        {divide, 10, 3},
        {divide, 10, 0}
    ],
    
    lists:foreach(
        fun(Op) ->
            Calc ! {Op, self()},
            receive
                {result, R} -> io:format("  ~p = ~p~n", [Op, R]);
                {error, E}  -> io:format("  ~p => 错误: ~s~n", [Op, E])
            after 1000 -> io:format("  超时~n")
            end
        end,
        Calculations
    ),
    
    Calc ! stop.

calc_loop() ->
    receive
        {{add, A, B}, Pid} ->
            Pid ! {result, A + B},
            calc_loop();
        {{sub, A, B}, Pid} ->
            Pid ! {result, A - B},
            calc_loop();
        {{mul, A, B}, Pid} ->
            Pid ! {result, A * B},
            calc_loop();
        {{divide, _, 0}, Pid} ->
            Pid ! {error, "除零错误"},
            calc_loop();
        {{divide, A, B}, Pid} ->
            Pid ! {result, A / B},
            calc_loop();
        stop ->
            ok
    end.

%% ========== Ping-Pong: 两个进程互相发消息 ==========
ping_pong() ->
    Pong = spawn(fun() -> pong() end),
    spawn(fun() -> ping(3, Pong) end),
    timer:sleep(300).

ping(0, Pong) ->
    Pong ! stop,
    io:format("  Ping 结束~n");
ping(N, Pong) ->
    Pong ! {ping, self()},
    receive
        pong -> io:format("  Ping 收到 pong (~p)~n", [N])
    end,
    ping(N - 1, Pong).

pong() ->
    receive
        {ping, Pid} ->
            io:format("  Pong 收到 ping~n"),
            Pid ! pong,
            pong();
        stop ->
            io:format("  Pong 结束~n")
    end.
