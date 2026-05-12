
# Erlang Demo 路线图

本目录共 27 个 demo，覆盖 Erlang/OTP 从语法基础到生产级运维的完整能力面。
全部 demo 已完成 ✅。本文档为追溯式路线图：分主题分层呈现，便于按需查阅。

> 配套阅读：[ERLANG_ECOSYSTEM.md](./ERLANG_ECOSYSTEM.md)（生态全景）
> 一键运行：`./run.sh [N]`（不带参数列出帮助；`./run.sh all` 跑全部纯计算 demo）

## 第一阶段：函数式基础（01-02）

| # | 主题 | 文件 | 入口 | 状态 |
|---|------|------|------|------|
| 01 | 模式匹配 / 递归 / 守卫 | [01_pattern_matching.erl](./01_pattern_matching.erl) | `'01_pattern_matching':main/0` | ✅ |
| 02 | 高阶函数 / 列表推导 / 函数组合 | [02_higher_order.erl](./02_higher_order.erl) | `'02_higher_order':main/0` | ✅ |

## 第二阶段：并发与 Actor 模型（03）

| # | 主题 | 文件 | 入口 | 状态 |
|---|------|------|------|------|
| 03 | 进程 / 消息 / spawn / receive | [03_actor_model.erl](./03_actor_model.erl) | `'03_actor_model':main/0` | ✅ |

## 第三阶段：OTP 行为与监督树（04-05）

| # | 主题 | 文件 | 入口 | 状态 |
|---|------|------|------|------|
| 04 | gen_server 计数器 | [04_gen_server_counter.erl](./04_gen_server_counter.erl) | `'04_gen_server_counter':main/0` | ✅ |
| 05 | supervisor 监督树 / 重启策略 | [05_supervisor_tree.erl](./05_supervisor_tree.erl) | `'05_supervisor_tree':main/0` | ✅ |

## 第四阶段：状态存储与分布式（06-07）

| # | 主题 | 文件 | 入口 | 状态 |
|---|------|------|------|------|
| 06 | ETS 共享状态 | [06_ets_and_state.erl](./06_ets_and_state.erl) | `'06_ets_and_state':main/0` | ✅ |
| 07 | 分布式节点 / RPC（单节点演示） | [07_distributed_nodes.erl](./07_distributed_nodes.erl) | `'07_distributed_nodes':single_node_demo/0` | ✅ |

## 第五阶段：测试与协议层（08-10）

| # | 主题 | 文件 | 入口 | 状态 |
|---|------|------|------|------|
| 08 | PropEr 属性测试 | [08_property_testing_proper.erl](./08_property_testing_proper.erl) | `'08_property_testing_proper':main/0` | ✅ |
| 09 | gen_statem 订单 FSM | [09_gen_statem_order_fsm.erl](./09_gen_statem_order_fsm.erl) | `'09_gen_statem_order_fsm':main/0` | ✅ |
| 10 | 二进制模式匹配 / 协议解析 | [10_binary_pattern_matching.erl](./10_binary_pattern_matching.erl) | `'10_binary_pattern_matching':main/0` | ✅ |

## 第六阶段：持久化与热升级（11-12）

| # | 主题 | 文件 | 入口 | 状态 |
|---|------|------|------|------|
| 11 | mnesia 分布式事务表 | [11_mnesia_transactional_store.erl](./11_mnesia_transactional_store.erl) | `'11_mnesia_transactional_store':main/0` | ✅ |
| 12 | 热代码升级 | [12_hot_code_upgrade.erl](./12_hot_code_upgrade.erl) | `'12_hot_code_upgrade':main/0` | ✅ |

## 第七阶段：网络与生命周期（13-15）

| # | 主题 | 文件 | 入口 | 状态 |
|---|------|------|------|------|
| 13 | gen_tcp echo 服务器 | [13_gen_tcp_echo_server.erl](./13_gen_tcp_echo_server.erl) | `'13_gen_tcp_echo_server':main/0` | ✅ |
| 14 | 选择性接收 / mailbox 优先级 | [14_selective_receive_mailbox.erl](./14_selective_receive_mailbox.erl) | `'14_selective_receive_mailbox':main/0` | ✅ |
| 15 | link / monitor / trap_exit | [15_link_monitor_trap_exit.erl](./15_link_monitor_trap_exit.erl) | `'15_link_monitor_trap_exit':main/0` | ✅ |

## 第八阶段：可观测性与发布（16-19）

| # | 主题 | 文件 | 入口 | 状态 |
|---|------|------|------|------|
| 16 | logger 与自定义 formatter | [16_logger_and_formatter.erl](./16_logger_and_formatter.erl) | `'16_logger_and_formatter':main/0` | ✅ |
| 17 | Common Test 测试框架 | [17_common_test_ct.erl](./17_common_test_ct.erl) | `'17_common_test_ct':run/0` | ✅ |
| 18 | application / release 打包 | [18_application_and_release.erl](./18_application_and_release.erl) | `'18_application_and_release':run/0` | ✅ |
| 19 | recon / observer 在线诊断 | [19_recon_observer_introspect.erl](./19_recon_observer_introspect.erl) | `'19_recon_observer_introspect':run/0` | ✅ |

## 第九阶段：系统集成（20-22）

| # | 主题 | 文件 | 入口 | 状态 |
|---|------|------|------|------|
| 20 | NIF / Port / port_command | [20_nif_and_port.erl](./20_nif_and_port.erl) | `'20_nif_and_port':run/0` | ✅ |
| 21 | dets / disk_log 持久化 | [21_dets_and_disc_log.erl](./21_dets_and_disc_log.erl) | `'21_dets_and_disc_log':run/0` | ✅ |
| 22 | ssl / TLS 自签证书握手 | [22_ssl_and_tls.erl](./22_ssl_and_tls.erl) | `'22_ssl_and_tls':run/0` | ✅ |

## 第十阶段：性能与工程化（23-25）

| # | 主题 | 文件 | 入口 | 状态 |
|---|------|------|------|------|
| 23 | timer / fprof / eprof 性能分析 | [23_bench_and_profile.erl](./23_bench_and_profile.erl) | `'23_bench_and_profile':run/0` | ✅ |
| 24 | rebar3 项目骨架蓝图 | [24_rebar3_project_skeleton.erl](./24_rebar3_project_skeleton.erl) | `'24_rebar3_project_skeleton':show/0` | ✅ |
| 25 | Elixir vs Erlang 对照 | [25_elixir_vs_erlang.erl](./25_elixir_vs_erlang.erl) | `'25_elixir_vs_erlang':run/0` | ✅ |

## 第十一阶段：事件总线与 Trace（26-27）

| # | 主题 | 文件 | 入口 | 状态 |
|---|------|------|------|------|
| 26 | gen_event 发布订阅总线 | [26_gen_event_pubsub.erl](./26_gen_event_pubsub.erl) | `'26_gen_event_pubsub':run/0` | ✅ |
| 27 | erlang:trace / dbg 在线追踪 | [27_erl_trace_and_dbg.erl](./27_erl_trace_and_dbg.erl) | `'27_erl_trace_and_dbg':run/0` | ✅ |

---

## 注意事项

1. **入口函数差异**：早期 demo（01-16）入口是 `main/0`；后期 demo（17-27，除 24 用 `show/0`）入口是 `run/0`；07 用 `single_node_demo/0`。
2. **module 名统一带数字前缀**（atom 形式），调用需加引号，如 `'01_pattern_matching':main()`、`'17_common_test_ct':run()`。
3. **副作用 demo**：11（创磁盘表）、13（占 TCP 端口）、20（需 C 编译器）、22（生成证书）会修改本地状态或环境，建议单独运行。
4. **可选依赖**：08 需要 `proper`，未安装时会跳过实际属性测试。

