%%%-------------------------------------------------------------------
%%% Erlang 函数式编程 Demo 10: 二进制模式匹配 —— BEAM 的杀手特性
%%%
%%% 其他大多数语言解析二进制协议都要 shift / mask 一堆位操作，
%%% Erlang 可以把位宽、字段、类型直接写在模式里，一行搞定：
%%%
%%%     <<Version:4, IHL:4, TOS:8, TotalLen:16, ...>> = Packet.
%%%
%%% 这就是为什么电信行业这么爱用 Erlang —— 协议栈写起来像写数学公式。
%%%
%%% 本 Demo 依次演示：
%%%   1. 构造二进制 —— <<...>> 语法与位宽
%%%   2. 解析 TCP header 前 20 字节
%%%   3. 解析 UTF-8 码点（变长字段）
%%%   4. 解析一个自定义的 TLV (type-length-value) 流
%%%   5. 位标志 (flags) 的取位 & 组合
%%%
%%% 运行：
%%%   erl -compile 10_binary_pattern_matching.erl
%%%   erl -noshell -s binary_pattern_matching main -s init stop
%%%-------------------------------------------------------------------
-module(binary_pattern_matching).
-export([main/0]).

%% ============================================================
%% 1. 构造 —— <<...>> 是二进制字面量, 每段默认 8 位
%% ============================================================

build_demo() ->
    %% 纯字节
    A = <<1, 2, 3, 4>>,
    %% 指定位宽
    B = <<255:16, 1:8, 2:4, 3:4>>,        %% 4 字节: FF 00 01 23
    %% 字符串也就是二进制
    C = <<"hello">>,
    %% 组合
    D = <<A/binary, C/binary, 0:8>>,      %% 把前两段拼起来，末尾补一个 0
    {A, B, C, D}.

%% ============================================================
%% 2. 解析 TCP header 前 20 字节
%% ============================================================
%% 真实 TCP header 结构:
%%   src_port:16 | dst_port:16 | seq:32 | ack:32
%%   | data_off:4 | reserved:3 | flags:9 | window:16
%%   | checksum:16 | urg_ptr:16 | ...options

parse_tcp_header(Packet)
    when byte_size(Packet) >= 20 ->
    <<SrcPort:16, DstPort:16,
      Seq:32,
      Ack:32,
      DataOff:4, _Reserved:3, Flags:9,
      Window:16,
      Checksum:16, UrgPtr:16,
      Rest/binary>> = Packet,
    #{src_port => SrcPort, dst_port => DstPort,
      seq => Seq, ack => Ack,
      data_offset => DataOff,
      flags => decode_tcp_flags(Flags),
      window => Window,
      checksum => Checksum, urg_ptr => UrgPtr,
      payload_size => byte_size(Rest)}.

%% TCP flag 位（仅演示常用 3 个）
decode_tcp_flags(Flags) ->
    #{syn => (Flags band 2#000000010) =/= 0,
      ack => (Flags band 2#000010000) =/= 0,
      fin => (Flags band 2#000000001) =/= 0}.

%% ============================================================
%% 3. 解析 UTF-8 —— 变长字段的典型用法
%% ============================================================
%% Erlang 有内建的 utf8 类型：<<Cp/utf8, Rest/binary>>
%% 循环模式：头部匹配 1 个码点, 剩下递归

utf8_codepoints(<<>>) -> [];
utf8_codepoints(<<Cp/utf8, Rest/binary>>) ->
    [Cp | utf8_codepoints(Rest)].

%% ============================================================
%% 4. TLV —— 自定义流协议
%%    | Type:8 | Len:16 | Value:Len 字节 | ...next
%% ============================================================

parse_tlv(<<>>) -> [];
parse_tlv(<<Type:8, Len:16, Value:Len/binary, Rest/binary>>) ->
    [#{type => Type, len => Len, value => Value} | parse_tlv(Rest)].

build_tlv(List) ->
    iolist_to_binary([ encode_tlv(T, V) || {T, V} <- List ]).

encode_tlv(Type, Value) when is_binary(Value) ->
    Len = byte_size(Value),
    <<Type:8, Len:16, Value/binary>>.

%% ============================================================
%% 5. 位标志读写
%% ============================================================

bitmap_demo() ->
    %% 把 4 个 bool 塞进一个字节
    Flags = <<1:1, 0:1, 1:1, 1:1, 0:4>>,
    <<B1:1, B2:1, B3:1, B4:1, _:4>> = Flags,
    {B1, B2, B3, B4}.

%% ============================================================
%% main
%% ============================================================

main() ->
    io:format("=== Erlang 二进制模式匹配 ===~n"),

    %% (1) 构造
    {A, B, C, D} = build_demo(),
    io:format("~n-- 构造 --~n"),
    io:format("  A = ~p  (size=~p)~n", [A, byte_size(A)]),
    io:format("  B = ~p~n", [B]),
    io:format("  C = ~p  (其实是 ~s)~n", [C, C]),
    io:format("  D = ~p~n", [D]),

    %% (2) TCP header
    Fake = <<16#1F90:16, 16#0050:16,
             12345:32,
             67890:32,
             5:4, 0:3, 2#000010010:9,      %% SYN+ACK
             4096:16,
             0:16, 0:16,
             "payload-bytes">>,
    Hdr = parse_tcp_header(Fake),
    io:format("~n-- TCP header 解析 --~n  ~p~n", [Hdr]),

    %% (3) UTF-8
    io:format("~n-- UTF-8 码点 --~n"),
    Cps = utf8_codepoints(<<"中文abc"/utf8>>),
    io:format("  codepoints = ~p~n", [Cps]),

    %% (4) TLV
    Tlv = build_tlv([{1, <<"name">>}, {2, <<"jiang">>}, {3, <<"v1.0">>}]),
    io:format("~n-- TLV 流 --~n  raw    = ~p~n", [Tlv]),
    io:format("  parsed = ~p~n", [parse_tlv(Tlv)]),

    %% (5) bitmap
    io:format("~n-- bitmap --~n  bits = ~p~n", [bitmap_demo()]),

    io:format("~n=== 重点 ===~n"),
    io:format("  * Erlang 把『协议字段结构』变成了『模式』, 解析器 = 一行模式匹配~n"),
    io:format("  * 位宽、字节序 (/big /little)、类型 (/utf8 /float) 都在模式里~n"),
    io:format("  * 适配场景: TCP/UDP/TLS/MQTT/AMQP/自定义二进制帧机~n"),
    io:format("  * 对照: Rust nom / Haskell binary / Scala scodec~n"),
    ok.
