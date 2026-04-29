%%%-------------------------------------------------------------------
%%% Erlang 函数式编程 Demo 16: OTP logger —— 生产日志框架
%%%
%%% OTP 21+ 内置 logger 模块, 替代老 error_logger。它具备现代日志库该有的一切:
%%%   * 分级: debug / info / notice / warning / error / critical / alert / emergency
%%%   * 结构化: 日志数据可以是 map, 不是纯字符串
%%%   * 多 handler: 同一条日志可以同时去 console / 文件 / syslog / ELK
%%%   * 过滤器: 按模块 / level / 正则过滤
%%%   * 格式化器: 内置 logger_formatter 模板可控, 可外挂自定义 formatter 模块
%%%   * 运行时调级: 线上无须重启, 开 debug 半小时再调回来
%%%
%%% 本 Demo:
%%%   1. 默认 handler 输出到 stdout
%%%   2. 调整全局 level
%%%   3. 结构化日志 (第二个参数传 map 元数据)
%%%   4. 重配 logger_formatter 的 template, 让日志带时间/级别/模块
%%%   5. 按模块级别单独调
%%%
%%% 运行：
%%%   erl -compile 16_logger_and_formatter.erl
%%%   erl -noshell -s logger_and_formatter main -s init stop
%%%-------------------------------------------------------------------
-module(logger_and_formatter).
-export([main/0]).

%% ============================================================
%% main
%% ============================================================

main() ->
    io:format("=== OTP logger 演示 ===~n"),

    %% ---------- 1. 默认 handler ----------
    io:format("~n-- 1. 默认级别 (notice) 之上才会显示 --~n"),
    logger:debug("这条 debug 默认不显示"),
    logger:info("这条 info 默认也不显示 (primary level = notice)"),
    logger:notice("这条 notice 会显示"),
    logger:warning("这条 warning 会显示"),
    logger:error("这条 error 会显示, ~p", [oops]),

    timer:sleep(50),

    %% ---------- 2. 调整全局 level ----------
    io:format("~n-- 2. 把全局 level 调到 debug --~n"),
    ok = logger:set_primary_config(level, debug),
    logger:debug("现在 debug 也会显示"),

    timer:sleep(50),

    %% ---------- 3. 结构化日志 ----------
    io:format("~n-- 3. 结构化日志: 第二参数传 report (map) --~n"),
    logger:info(#{event => order_paid,
                  order_id => "ord-101",
                  amount   => 99.9,
                  user     => "alice"}),

    timer:sleep(50),

    %% ---------- 4. 改 logger_formatter 的 template ----------
    %% template 里可以穿插原子 (取自 meta) 和字符串, 这就是大部分场景要的『改格式』
    io:format("~n-- 4. 用 logger_formatter 定制模板 [LEVEL TIME PID] MSG --~n"),
    ok = logger:update_handler_config(
        default, formatter,
        {logger_formatter,
         #{template => [ "[", level, " ", time, " ", pid, "] ", msg, "\n" ],
           single_line => true}}),
    logger:notice("shipment scheduled"),
    logger:info(#{event => user_signup, uid => 42, plan => "pro"}),
    logger:error("db connection ~s", ["timeout"]),

    timer:sleep(50),

    %% 恢复默认 template, 便于后续观察
    ok = logger:update_handler_config(default, formatter,
                                      {logger_formatter, #{}}),

    %% ---------- 5. 按模块级别 ----------
    io:format("~n-- 5. 让 ?MODULE 模块只接受 warning 以上 --~n"),
    ok = logger:set_module_level(?MODULE, warning),
    logger:info("这条 info 会被模块级别过滤掉, 不显示"),
    logger:warning("这条 warning 会显示"),
    ok = logger:unset_module_level(?MODULE),

    timer:sleep(50),

    io:format("~n=== 重点 ===~n"),
    io:format("  * logger 已内置, 无需 lager / sasl; 直接 logger:info/1,2 即可~n"),
    io:format("  * 结构化日志 (传 map) 是现代运维要求, ELK / Datadog 能直接索引字段~n"),
    io:format("  * 用 logger_formatter 的 template 就能把日志格式调到你想要的样子~n"),
    io:format("  * 需要 JSON? 把 formatter 换成独立模块 (生产项目通常用 flatlog / logger_std_h + json)~n"),
    io:format("  * 运行时 set_primary_config / set_module_level 是线上调试利器~n"),
    io:format("  * 对照: Scala cats-effect log4cats / Rust tracing / Haskell katip~n"),
    ok.
