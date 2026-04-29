%%% Erlang 函数式编程 Demo 18: OTP application & release
%%%
%%% 04 号 Demo 演示了 gen_server，
%%% 05 号 Demo 演示了 supervisor，
%%% 本 Demo 把 OTP 三件套最后一块补齐：**application behaviour**。
%%%
%%% 真实项目的目录结构（rebar3 new app demo_app 生成）：
%%%   demo_app/
%%%     src/
%%%       demo_app.app.src         %% 应用元数据
%%%       demo_app_app.erl         %% application callback
%%%       demo_app_sup.erl         %% 顶层 supervisor
%%%       demo_app_worker.erl      %% 业务 worker
%%%     rebar.config
%%%
%%% 本 Demo 把这些角色全部写进一个文件，直接 demo18_application:run/0
%%% 跑出 application 启动 -> worker 存活 -> 正常关闭的完整链路。

-module('18_application_and_release').

%% 说明: OTP 约定一个模块只该 implement 一个 behaviour；
%% 本 Demo 为了"单文件展示三件套"做了折衷 —— 只显式声明 supervisor，
%% gen_server 和 application 的回调也用同一份 init/1 按参数分流。

%% 顶层 supervisor
-behaviour(supervisor).

-export([start/2, stop/1,             %% application 回调
         init/1,                      %% supervisor + gen_server 共用回调
         sup_start_link/0,
         worker_start_link/0,
         handle_call/3, handle_cast/2,
         run/0, ping/0]).

%% ============================================================
%% application 回调（手动调用, 不走 behaviour 声明）
%% ============================================================
start(_Type, _Args) ->
    io:format("[app] application:start/2 — Type=normal~n"),
    sup_start_link().

stop(_State) ->
    io:format("[app] application:stop/1 — 清理 application env~n"),
    ok.

%% ============================================================
%% supervisor
%% ============================================================
sup_start_link() ->
    supervisor:start_link({local, ?MODULE}, ?MODULE, []).

%% 统一 init/1: supervisor 会传 [], gen_server 会传 worker
init([]) ->
    io:format("[sup] supervisor init — one_for_one, intensity=5, period=10~n"),
    SupFlags = #{strategy => one_for_one, intensity => 5, period => 10},
    Child = #{
        id       => demo_worker,
        start    => {?MODULE, worker_start_link, []},
        restart  => permanent,
        shutdown => 5000,
        type     => worker,
        modules  => [?MODULE]
    },
    {ok, {SupFlags, [Child]}};
init(worker) ->
    Greeting = application:get_env(demo_app, greeting, "hello"),
    io:format("[worker] init — greeting=~p (来自 application env)~n", [Greeting]),
    {ok, #{greeting => Greeting, pings => 0}}.

%% ============================================================
%% gen_server worker
%% ============================================================
worker_start_link() ->
    gen_server:start_link({local, demo_worker}, ?MODULE, worker, []).

handle_call(ping, _From, #{pings := N, greeting := G} = S) ->
    {reply, {pong, G, N + 1}, S#{pings := N + 1}};
handle_call(_Msg, _From, S) ->
    {reply, ignored, S}.

handle_cast(_Msg, S) -> {noreply, S}.

%% 外部 API
ping() -> gen_server:call(demo_worker, ping).

%% ============================================================
%% 运行器：模拟 rebar3 shell 里"application:ensure_all_started/1"
%% ============================================================
run() ->
    io:format("=== Erlang Demo 18: application & release ===~n"),

    %% 1) 写入 application env（真实项目放 sys.config / *.app.src 里）
    application:set_env(demo_app, greeting, "hello-from-env"),

    %% 2) 模拟 application:ensure_all_started(demo_app)
    %%    这里为了不依赖 .app 文件，直接手工调 start/2
    {ok, SupPid} = start(normal, []),
    io:format("[run] sup started pid=~p~n", [SupPid]),

    %% 3) 调 worker，看 application env 是否生效
    {pong, G1, N1} = ping(),
    {pong, G2, N2} = ping(),
    io:format("[run] ping#1 => greeting=~p, count=~p~n", [G1, N1]),
    io:format("[run] ping#2 => greeting=~p, count=~p~n", [G2, N2]),

    %% 4) 正常关闭：先停 sup，再调 application:stop
    ok = supervisor:terminate_child(SupPid, demo_worker) ,
    exit(SupPid, shutdown),
    stop([]),

    io:format("~n=== 重点理解 ===~n"),
    io:format("- OTP 三件套: application -> supervisor -> worker，是 Erlang 打包/发布的最小单位~n"),
    io:format("- *.app.src 声明元信息，sys.config 声明环境变量，rebar3 release 打成 tar 包~n"),
    io:format("- application:get_env/3 是和配置解耦的标准姿势，不要散落 hardcode~n"),
    io:format("- release 是生产发布形态，自带热升级能力（对应 Demo 12）~n"),
    ok.
