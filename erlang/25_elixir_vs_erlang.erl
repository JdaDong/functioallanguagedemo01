%%% Erlang 函数式编程 Demo 25: Elixir ↔ Erlang 互操作
%%%
%%% Elixir 也跑在 BEAM 上，和 Erlang 100% 互通：
%%%   - Elixir 编译后也是 .beam 文件
%%%   - Elixir 的 GenServer / Supervisor 就是 Erlang 的 gen_server / supervisor
%%%   - Elixir 模块名 `MyApp.Worker` 在 Erlang 看就是原子 'Elixir.MyApp.Worker'
%%%
%%% 本 Demo 用并排对照的方式展示同一个 gen_server 在两种语言里的写法。
%%% 不真正调用 Elixir 编译器，只把 Elixir 源码作为字符串打印出来对照。

-module('25_elixir_vs_erlang').
-compile({no_auto_import, [get/0]}).

-export([run/0, erlang_version/0, elixir_version_snippet/0]).

%% ============================================================
%% 1) Erlang 版的 gen_server（最简计数器）
%% ============================================================
-behaviour(gen_server).
-export([start_link/0, inc/1, get/0,
         init/1, handle_call/3, handle_cast/2]).

start_link() -> gen_server:start_link({local, counter_erl}, ?MODULE, 0, []).
inc(N)       -> gen_server:cast(counter_erl, {inc, N}).
get()        -> gen_server:call(counter_erl, get).

init(N) -> {ok, N}.
handle_call(get, _From, N) -> {reply, N, N}.
handle_cast({inc, K}, N)   -> {noreply, N + K}.

erlang_version() -> ok.

%% ============================================================
%% 2) Elixir 版的同等 GenServer（字符串展示，供对照阅读）
%% ============================================================
elixir_version_snippet() ->
    "defmodule Counter do\n"
    "  use GenServer\n"
    "\n"
    "  # Client API\n"
    "  def start_link(_),  do: GenServer.start_link(__MODULE__, 0, name: __MODULE__)\n"
    "  def inc(n),         do: GenServer.cast(__MODULE__, {:inc, n})\n"
    "  def get,            do: GenServer.call(__MODULE__, :get)\n"
    "\n"
    "  # Callbacks\n"
    "  @impl true\n"
    "  def init(n),                      do: {:ok, n}\n"
    "  @impl true\n"
    "  def handle_call(:get, _from, n),  do: {:reply, n, n}\n"
    "  @impl true\n"
    "  def handle_cast({:inc, k}, n),    do: {:noreply, n + k}\n"
    "end\n".

%% ============================================================
%% 3) Erlang 从 Elixir 调用（原子名的约定）
%% ============================================================
calling_elixir_from_erlang() ->
    "%% 假设 Elixir 里有模块 MyApp.Math.Utils, 函数 add/2\n"
    "%% Erlang 这样调:\n"
    "%%     'Elixir.MyApp.Math.Utils':add(1, 2).\n"
    "%% 也就是说 Elixir 模块在 Erlang 里的原子是 'Elixir.<原模块名>'。\n".

%% ============================================================
%% 4) Elixir 从 Erlang 调用
%% ============================================================
calling_erlang_from_elixir() ->
    "# Elixir 调 Erlang 模块, 模块名用小写原子即可:\n"
    "#     :lists.sum([1, 2, 3])\n"
    "#     :crypto.hash(:sha256, \"data\")\n"
    "#     :timer.tc(Module, :fun, args)\n".

%% ============================================================
%% 运行器
%% ============================================================
run() ->
    io:setopts([{encoding, unicode}]),
    io:format("=== Erlang Demo 25: Elixir ↔ Erlang 互操作 ===~n"),

    %% 跑一下 Erlang 版
    {ok, _} = start_link(),
    inc(3), inc(4),
    timer:sleep(50),
    N = get(),
    io:format("~n-- Erlang 版计数器 --~n"),
    io:format("  inc(3); inc(4); get() = ~p~n", [N]),

    io:format("~n-- 同等 Elixir 源码 --~n"),
    io:format("~ts~n", [elixir_version_snippet()]),

    io:format("-- Erlang → Elixir --~n~ts~n", [calling_elixir_from_erlang()]),
    io:format("-- Elixir → Erlang --~n~ts~n", [calling_erlang_from_elixir()]),

    io:format("~n=== 重点理解 ===~n"),
    io:format("- BEAM 家族: Erlang / Elixir / Gleam / LFE 全部编成 .beam, 互通~n"),
    io:format("- Elixir 模块 Foo.Bar 在 Erlang 眼里就是原子 'Elixir.Foo.Bar'~n"),
    io:format("- GenServer/Supervisor/Registry 都是 Elixir 对 OTP 的语法糖~n"),
    io:format("- 同一个 release 里可以混着 Erlang/Elixir 模块, 没有任何 FFI 成本~n"),
    ok.
