#!/bin/bash
#
# Erlang 函数式编程 Demo 一键运行脚本
# 用法: ./run.sh [demo编号]
#   ./run.sh        运行全部 Demo
#   ./run.sh 1      只运行 Demo 1 (模式匹配与递归)
#   ./run.sh 2      只运行 Demo 2 (高阶函数与列表推导)
#   ./run.sh 3      只运行 Demo 3 (Actor 模型)
#

set -e

# 切换到脚本所在目录
cd "$(dirname "$0")"

# 颜色定义
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # 无颜色

# 分隔线
separator() {
    echo ""
    echo -e "${CYAN}════════════════════════════════════════════════════════${NC}"
    echo ""
}

# 检查 Erlang 是否安装
if ! command -v erlc &> /dev/null; then
    echo -e "${RED}❌ 未检测到 Erlang，请先安装:${NC}"
    echo "   brew install erlang"
    exit 1
fi

echo -e "${GREEN}🚀 Erlang 函数式编程 Demo${NC}"
echo -e "${YELLOW}   Erlang 版本: $(erl -eval 'io:format("~s", [erlang:system_info(otp_release)]), halt().' -noshell 2>/dev/null)${NC}"
separator

# 编译所有 .erl 文件
echo -e "${YELLOW}📦 编译中...${NC}"
erlc *.erl
echo -e "${GREEN}✅ 编译成功${NC}"
separator

run_demo1() {
    echo -e "${GREEN}📘 Demo 1: 模式匹配与递归${NC}"
    echo -e "${YELLOW}   文件: 01_pattern_matching.erl${NC}"
    separator
    erl -noshell -s pattern_matching main -s init stop
    separator
}

run_demo2() {
    echo -e "${GREEN}📗 Demo 2: 高阶函数与列表推导${NC}"
    echo -e "${YELLOW}   文件: 02_higher_order.erl${NC}"
    separator
    erl -noshell -s higher_order main -s init stop
    separator
}

run_demo3() {
    echo -e "${GREEN}📙 Demo 3: 进程与消息传递 (Actor 模型)${NC}"
    echo -e "${YELLOW}   文件: 03_actor_model.erl${NC}"
    separator
    erl -noshell -s actor_model main -s init stop
    separator
}

# 根据参数选择运行
case "${1:-all}" in
    1)
        run_demo1
        ;;
    2)
        run_demo2
        ;;
    3)
        run_demo3
        ;;
    all)
        run_demo1
        run_demo2
        run_demo3
        ;;
    *)
        echo -e "${RED}❌ 未知参数: $1${NC}"
        echo "用法: $0 [1|2|3]"
        echo "  无参数  运行全部 Demo"
        echo "  1      Demo 1: 模式匹配与递归"
        echo "  2      Demo 2: 高阶函数与列表推导"
        echo "  3      Demo 3: Actor 模型"
        exit 1
        ;;
esac

# 清理编译产物
rm -f *.beam

echo -e "${GREEN}🎉 运行完毕！${NC}"
