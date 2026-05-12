#!/bin/bash
#
# Erlang 函数式编程 Demo 一键运行脚本（27 个 demo）
#
# 用法:
#   ./run.sh           显示帮助
#   ./run.sh N         运行 Demo N (1-27)
#   ./run.sh safe      运行所有"无副作用"的纯计算 demo
#   ./run.sh all       运行全部 27 个（含副作用，自行承担：占端口/写磁盘/编 C）
#

set -e
cd "$(dirname "$0")"

GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

separator() {
    echo ""
    echo -e "${CYAN}════════════════════════════════════════════════════════${NC}"
    echo ""
}

if ! command -v erlc &> /dev/null; then
    echo -e "${RED}❌ 未检测到 Erlang，请先安装:${NC}"
    echo "   brew install erlang"
    exit 1
fi

# demo N 的 (module, entry) 表
# 索引 = demo 编号；module 名带数字前缀的需要单引号
declare -a MODULES
declare -a ENTRIES
declare -a TITLES

MODULES[1]="'01_pattern_matching'";           ENTRIES[1]="main";             TITLES[1]="模式匹配与递归"
MODULES[2]="'02_higher_order'";               ENTRIES[2]="main";             TITLES[2]="高阶函数与列表推导"
MODULES[3]="'03_actor_model'";                ENTRIES[3]="main";             TITLES[3]="Actor 模型"
MODULES[4]="'04_gen_server_counter'";         ENTRIES[4]="main";             TITLES[4]="gen_server 计数器"
MODULES[5]="'05_supervisor_tree'";            ENTRIES[5]="main";             TITLES[5]="supervisor 监督树"
MODULES[6]="'06_ets_and_state'";              ENTRIES[6]="main";             TITLES[6]="ETS 共享状态"
MODULES[7]="'07_distributed_nodes'";          ENTRIES[7]="single_node_demo"; TITLES[7]="分布式节点（单节点演示）"
MODULES[8]="'08_property_testing_proper'";    ENTRIES[8]="main";             TITLES[8]="PropEr 属性测试"
MODULES[9]="'09_gen_statem_order_fsm'";       ENTRIES[9]="main";             TITLES[9]="gen_statem 订单 FSM"
MODULES[10]="'10_binary_pattern_matching'";    ENTRIES[10]="main";            TITLES[10]="二进制模式匹配"
MODULES[11]="'11_mnesia_transactional_store'"; ENTRIES[11]="main";            TITLES[11]="mnesia 事务表 ⚠️写磁盘"
MODULES[12]="'12_hot_code_upgrade'";           ENTRIES[12]="main";            TITLES[12]="热代码升级"
MODULES[13]="'13_gen_tcp_echo_server'";        ENTRIES[13]="main";            TITLES[13]="gen_tcp echo ⚠️占端口"
MODULES[14]="'14_selective_receive_mailbox'";  ENTRIES[14]="main";            TITLES[14]="选择性接收"
MODULES[15]="'15_link_monitor_trap_exit'";     ENTRIES[15]="main";            TITLES[15]="link/monitor/trap_exit"
MODULES[16]="'16_logger_and_formatter'";       ENTRIES[16]="main";            TITLES[16]="logger 与自定义 formatter"
MODULES[17]="'17_common_test_ct'";          ENTRIES[17]="run";             TITLES[17]="Common Test 测试框架"
MODULES[18]="'18_application_and_release'"; ENTRIES[18]="run";             TITLES[18]="application / release 打包"
MODULES[19]="'19_recon_observer_introspect'"; ENTRIES[19]="run";           TITLES[19]="recon / observer 诊断"
MODULES[20]="'20_nif_and_port'";            ENTRIES[20]="run";             TITLES[20]="NIF / Port ⚠️需要 C 编译器"
MODULES[21]="'21_dets_and_disc_log'";       ENTRIES[21]="run";             TITLES[21]="dets / disk_log ⚠️写磁盘"
MODULES[22]="'22_ssl_and_tls'";             ENTRIES[22]="run";             TITLES[22]="ssl / TLS ⚠️生成证书"
MODULES[23]="'23_bench_and_profile'";       ENTRIES[23]="run";             TITLES[23]="性能分析 (timer/fprof/eprof)"
MODULES[24]="'24_rebar3_project_skeleton'"; ENTRIES[24]="show";            TITLES[24]="rebar3 项目骨架"
MODULES[25]="'25_elixir_vs_erlang'";        ENTRIES[25]="run";             TITLES[25]="Elixir vs Erlang 对照"
MODULES[26]="'26_gen_event_pubsub'";        ENTRIES[26]="run";             TITLES[26]="gen_event 发布订阅"
MODULES[27]="'27_erl_trace_and_dbg'";       ENTRIES[27]="run";             TITLES[27]="erlang:trace / dbg"

# 无副作用 demo 列表（safe-all 默认跑这些）
SAFE_DEMOS=(1 2 3 4 5 6 7 8 9 10 12 14 15 16 17 18 19 23 24 25 26 27)

echo -e "${GREEN}🚀 Erlang 函数式编程 Demo (27 个)${NC}"
echo -e "${YELLOW}   Erlang 版本: $(erl -eval 'io:format("~s", [erlang:system_info(otp_release)]), halt().' -noshell 2>/dev/null)${NC}"
separator

echo -e "${YELLOW}📦 编译中...${NC}"
erlc *.erl
echo -e "${GREEN}✅ 编译成功${NC}"
separator

run_demo() {
    local n=$1
    local module=${MODULES[$n]}
    local entry=${ENTRIES[$n]}
    local title=${TITLES[$n]}
    if [ -z "$module" ]; then
        echo -e "${RED}❌ Demo $n 不存在（有效范围 1-27）${NC}"
        return 1
    fi
    printf -v file "%02d_*.erl" "$n"
    echo -e "${GREEN}📘 Demo $n: $title${NC}"
    echo -e "${YELLOW}   模块入口: $module:$entry/0${NC}"
    separator
    erl -noshell -eval "$module:$entry()." -s init stop
    separator
}

show_help() {
    echo "用法: $0 [N | safe | all]"
    echo ""
    echo "  N        运行 Demo N (1-27)"
    echo "  safe     运行所有无副作用 demo（${#SAFE_DEMOS[@]} 个）"
    echo "  all      运行全部 27 个（含 ⚠️ 标记的副作用 demo）"
    echo ""
    echo "可用 demo："
    for i in $(seq 1 27); do
        printf "  %2d  %s\n" "$i" "${TITLES[$i]}"
    done
}

case "${1:-help}" in
    help|-h|--help|"")
        show_help
        ;;
    safe)
        for i in "${SAFE_DEMOS[@]}"; do
            run_demo "$i"
        done
        ;;
    all)
        for i in $(seq 1 27); do
            run_demo "$i"
        done
        ;;
    *)
        if [[ "$1" =~ ^[0-9]+$ ]] && [ "$1" -ge 1 ] && [ "$1" -le 27 ]; then
            run_demo "$1"
        else
            echo -e "${RED}❌ 未知参数: $1${NC}"
            show_help
            exit 1
        fi
        ;;
esac

# 清理编译产物
rm -f *.beam

echo -e "${GREEN}🎉 运行完毕！${NC}"
