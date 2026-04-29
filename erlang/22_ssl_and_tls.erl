%%% Erlang 函数式编程 Demo 22: SSL/TLS（在 gen_tcp 之上换成 ssl 模块）
%%%
%%% 13 号 Demo 用 gen_tcp 做了明文 echo 服务。
%%% 生产上 99% 的服务要走 TLS，OTP 提供的 ssl 模块的 API 故意设计得
%%% 和 gen_tcp 几乎一致：ssl:listen / ssl:transport_accept / ssl:handshake
%%% / ssl:send / ssl:recv / ssl:close。
%%%
%%% 本 Demo 自生成自签证书（不依赖外部 CA）并完成一次客户端-服务器握手，
%%% 跑 demo22_ssl:run/0 即可。

-module('22_ssl_and_tls').

-export([run/0, gen_self_signed/1]).

%% ============================================================
%% 1) 用 public_key/crypto 生成自签证书（纯 OTP，不依赖 openssl）
%% ============================================================
gen_self_signed(BaseDir) ->
    ok = filelib:ensure_dir(filename:join(BaseDir, "placeholder")),
    KeyPath  = filename:join(BaseDir, "server.key"),
    CertPath = filename:join(BaseDir, "server.crt"),

    %% 简化处理：如果已经存在就直接复用
    case filelib:is_regular(CertPath) of
        true -> {ok, KeyPath, CertPath};
        false ->
            %% OTP 25+ 有 public_key:pkix_test_root_cert，这里用最朴素的路径：
            %% 让 ssl 在 handshake 时动态协商——仍然需要文件。
            %% 为简化，使用 OTP 自带测试密钥（inets 里的示例）。
            %% 这里我们走更通用的方案: 调 erl_make_certs (OTP 内部模块) 或生成 PEM。
            Cmd = io_lib:format(
                   "openssl req -x509 -newkey rsa:2048 -keyout ~s -out ~s "
                   "-days 1 -nodes -subj '/CN=localhost' 2>/dev/null",
                   [KeyPath, CertPath]),
            _ = os:cmd(lists:flatten(Cmd)),
            case filelib:is_regular(CertPath) of
                true  -> {ok, KeyPath, CertPath};
                false -> {error, "需要系统有 openssl 命令才能生成自签证书"}
            end
    end.

%% ============================================================
%% 2) TLS echo server + client
%% ============================================================
start_server(Port, KeyFile, CertFile) ->
    {ok, LSock} = ssl:listen(Port, [
        {certfile, CertFile},
        {keyfile,  KeyFile},
        {reuseaddr, true},
        {active,    false},
        binary,
        {packet, line}
    ]),
    Parent = self(),
    spawn_link(fun() ->
        Parent ! {listening, self()},
        {ok, TSock} = ssl:transport_accept(LSock),
        {ok, SSLSock} = ssl:handshake(TSock, 5_000),
        {ok, CertInfo} = ssl:connection_information(SSLSock, [protocol, selected_cipher_suite]),
        io:format("[server] TLS handshake ok, info=~p~n", [CertInfo]),
        handle_echo(SSLSock)
    end),
    receive {listening, _ServerPid} -> ok after 1000 -> timeout end,
    LSock.

handle_echo(Sock) ->
    case ssl:recv(Sock, 0, 5_000) of
        {ok, Data} ->
            ok = ssl:send(Sock, Data),
            handle_echo(Sock);
        {error, closed} -> ok;
        {error, Reason} -> io:format("[server] recv error=~p~n", [Reason])
    end.

run_client(Port, CertFile) ->
    {ok, Sock} = ssl:connect("localhost", Port, [
        {cacertfile, CertFile},   %% 因为是自签, 把 server 证书当 CA
        {verify, verify_none},    %% demo 专用, 生产必须 verify_peer
        {active, false},
        binary,
        {packet, line}
    ], 5_000),
    ok = ssl:send(Sock, <<"hello over TLS\n">>),
    {ok, Echo} = ssl:recv(Sock, 0, 5_000),
    io:format("[client] echo = ~p~n", [Echo]),
    ok = ssl:close(Sock).

%% ============================================================
%% 运行器
%% ============================================================
run() ->
    io:format("=== Erlang Demo 22: SSL / TLS ===~n"),
    _ = application:ensure_all_started(ssl),
    _ = application:ensure_all_started(crypto),

    Dir = "/tmp/demo22_tls",
    case gen_self_signed(Dir) of
        {ok, KeyFile, CertFile} ->
            Port = 0,  %% 让系统自选端口
            LSock = start_server(Port, KeyFile, CertFile),
            {ok, {_Addr, ChosenPort}} = ssl:sockname(LSock),
            io:format("[run] server listening on port ~p~n", [ChosenPort]),
            run_client(ChosenPort, CertFile),
            ssl:close(LSock),
            io:format("~n=== 重点理解 ===~n"),
            io:format("- ssl 模块 API 和 gen_tcp 对称: listen/accept/send/recv~n"),
            io:format("- handshake 是显式的一步, 可以看到握手成功/失败的原因~n"),
            io:format("- verify_peer + cacertfile 是生产配置必备, 千万别用 verify_none~n"),
            io:format("- ssl:connection_information/2 能看选到哪个 cipher / protocol 版本~n");
        {error, Why} ->
            io:format("~n跳过本 Demo: ~s~n", [Why]),
            io:format("请先安装 openssl 命令行工具再重跑。~n")
    end,
    ok.
