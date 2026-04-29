%%% Erlang 函数式编程 Demo 24: rebar3 项目骨架（工程脚手架速查）
%%%
%%% 前面 17~23 都是单文件 Demo，真实项目绝对不会这么组织，
%%% 而是使用 rebar3（Erlang 社区事实标准的构建工具）。
%%% 本 Demo 不执行命令，而是把"一个真实 OTP app 的目录+文件应该长啥样"
%%% 集中在一份可读参考里，调用 demo24_rebar3:show/0 会把模板打印出来。
%%%
%%% 生成命令（不要当代码执行，放到 README 或 CI 里）:
%%%   rebar3 new app name=myapp
%%%   rebar3 new release name=myapp_release
%%%
%%% 典型命令:
%%%   rebar3 compile       编译
%%%   rebar3 shell         起 REPL 并加载本项目
%%%   rebar3 eunit         跑单元测试
%%%   rebar3 ct            跑 Common Test (对应 Demo 17)
%%%   rebar3 proper        跑 PropEr (对应 Demo 08)
%%%   rebar3 dialyzer      静态类型检查
%%%   rebar3 release       打 release（对应 Demo 18）
%%%   rebar3 relup          打热升级包（对应 Demo 12）

-module('24_rebar3_project_skeleton').

-export([show/0, tree/0, sample_rebar_config/0, sample_app_src/0,
         sample_sys_config/0, sample_vm_args/0]).

tree() ->
    [
     "myapp/",
     "├── rebar.config                 -- 构建配置",
     "├── rebar.lock                   -- 依赖锁定（自动生成）",
     "├── config/",
     "│   ├── sys.config               -- application env",
     "│   └── vm.args                  -- BEAM 启动参数",
     "├── src/",
     "│   ├── myapp.app.src            -- application 元数据",
     "│   ├── myapp_app.erl            -- application callback (对应 Demo 18)",
     "│   ├── myapp_sup.erl            -- 顶层 supervisor (对应 Demo 05)",
     "│   └── myapp_worker.erl        -- 业务 gen_server (对应 Demo 04)",
     "├── include/",
     "│   └── myapp.hrl                -- 共享头文件 / record 定义",
     "├── priv/                         -- 打包进 release 的静态资源（NIF .so、静态文件）",
     "├── test/",
     "│   ├── myapp_SUITE.erl          -- Common Test suite (对应 Demo 17)",
     "│   ├── myapp_tests.erl          -- EUnit 测试",
     "│   └── prop_myapp.erl           -- PropEr 属性测试 (对应 Demo 08)",
     "└── _build/                       -- 构建产物（git 忽略）"
    ].

sample_rebar_config() ->
    "%% rebar.config\n"
    "{erl_opts, [debug_info, warnings_as_errors]}.\n\n"
    "{deps, [\n"
    "    {lager,  \"3.9.2\"},\n"
    "    {cowboy, \"2.12.0\"},\n"
    "    {jsx,    \"3.1.0\"}\n"
    "]}.\n\n"
    "{relx, [\n"
    "    {release, {myapp, \"0.1.0\"}, [myapp, sasl]},\n"
    "    {sys_config, \"./config/sys.config\"},\n"
    "    {vm_args,    \"./config/vm.args\"},\n"
    "    {include_erts, true},\n"
    "    {extended_start_script, true}\n"
    "]}.\n\n"
    "{profiles, [\n"
    "    {prod, [{relx, [{dev_mode, false}, {include_erts, true}]}]},\n"
    "    {test, [{deps, [{proper, \"1.4.0\"}, {meck, \"0.9.2\"}]}]}\n"
    "]}.\n\n"
    "{dialyzer, [\n"
    "    {warnings, [unknown]},\n"
    "    {plt_extra_apps, [mnesia, ssl, crypto]}\n"
    "]}.\n".

sample_app_src() ->
    "%% src/myapp.app.src\n"
    "{application, myapp,\n"
    " [{description, \"A sample OTP app\"},\n"
    "  {vsn,         \"0.1.0\"},\n"
    "  {registered,  [myapp_sup]},\n"
    "  {mod,         {myapp_app, []}},\n"
    "  {applications, [kernel, stdlib, sasl, crypto]},\n"
    "  {env,         [{greeting, \"hello from env\"}]},\n"
    "  {modules,     []},\n"
    "  {licenses,    [\"Apache-2.0\"]},\n"
    "  {links,       []}\n"
    " ]}.\n".

sample_sys_config() ->
    "%% config/sys.config —— 生产发布时由 release 打进去\n"
    "[\n"
    "  {myapp, [\n"
    "      {greeting,  \"hello from sys.config\"},\n"
    "      {http_port, 8080}\n"
    "  ]},\n"
    "  {kernel, [\n"
    "      {logger_level, info}\n"
    "  ]}\n"
    "].\n".

sample_vm_args() ->
    "## config/vm.args —— BEAM 启动参数\n"
    "-name myapp@127.0.0.1\n"
    "-setcookie super-secret-cookie\n"
    "+K true           # kernel poll\n"
    "+A 16             # async thread pool\n"
    "+sbwt none        # 调度器忙等策略\n"
    "-heart            # 启动 heart 子进程 监控 BEAM 自身\n".

show() ->
    io:format("=== Erlang Demo 24: rebar3 项目骨架 ===~n~n"),

    io:format("-- 标准目录树 --~n"),
    [io:format("~s~n", [L]) || L <- tree()],

    io:format("~n-- rebar.config --~n~s~n", [sample_rebar_config()]),
    io:format("-- src/myapp.app.src --~n~s~n", [sample_app_src()]),
    io:format("-- config/sys.config --~n~s~n", [sample_sys_config()]),
    io:format("-- config/vm.args --~n~s~n", [sample_vm_args()]),

    io:format("~n=== 重点理解 ===~n"),
    io:format("- rebar3 是 Erlang 事实标准工具, 等同 Rust 的 cargo / Scala 的 sbt~n"),
    io:format("- src/*.app.src 和 release 阶段生成的 *.app 是两个文件~n"),
    io:format("- sys.config 是运行时配置, vm.args 是 BEAM 启动参数~n"),
    io:format("- _build/default/rel/myapp/bin/myapp 就是最终可执行脚本~n"),
    ok.
