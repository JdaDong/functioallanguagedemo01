#!/bin/bash
#
# Racket 函数式编程 Demo 一键运行脚本
# 用法:
#   ./run.sh           运行全部 15 个 demo
#   ./run.sh 5         只运行编号 05
#   ./run.sh list      列出全部 demo
#
# 说明:
#   - 大部分 demo 仅需 stdlib，秒级完成
#   - demo 12 (web-server) 会在 8765 端口短暂启 HTTP 服务并自测
#   - demo 14 的 rackcheck 部分如未安装会自动 skip
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

# ---- 环境检测 -------------------------------------------------------------
if ! command -v racket &> /dev/null; then
    echo -e "${RED}❌ 未检测到 racket, 请先安装:${NC}"
    echo "   brew install --cask racket          # macOS"
    echo "   或访问 https://download.racket-lang.org/"
    exit 1
fi

echo -e "${GREEN}🎩 Racket 函数式编程 Demo${NC}"
echo -e "${YELLOW}   Racket: $(racket --version)${NC}"
separator

# ---- demo 清单 ------------------------------------------------------------
DEMOS=(
  "01_basics_and_lists|基础语法 + cond + match + 列表三剑客"
  "02_higher_order|高阶函数 + 闭包 + compose + curry"
  "03_recursion_and_tail|真尾递归 + named let + for 家族"
  "04_structs_and_match|struct + match (ADT 风格)"
  "05_macros_intro|宏入门 syntax-rules"
  "06_macros_syntax_parse|⭐ syntax-parse 工业级宏"
  "07_contracts|⭐ 一等公民契约系统"
  "08_typed_racket|⭐ Typed Racket 渐进类型"
  "09_continuations|⭐ call/cc 第一类延续"
  "10_parser_combinators|手写 parser combinators"
  "11_racket_lang|⭐ #lang 语言工作台"
  "12_web_server|web-server (8765 端口)"
  "13_concurrency_threads|thread + channel + sync"
  "14_property_testing|手写 mini quickcheck + rackcheck"
  "15_macros_dsl|⭐ 综合实战：状态机 DSL"
)

# ---- 运行函数 -------------------------------------------------------------
run_one() {
    local entry="$1"
    local dir="${entry%%|*}"
    local title="${entry##*|}"
    if [[ ! -d "$dir" ]]; then
        echo -e "${RED}❌ 目录不存在: $dir${NC}"
        return 1
    fi
    echo -e "${GREEN}🎩 ${dir} — ${title}${NC}"
    separator
    racket "$dir/main.rkt"
    separator
}

list_all() {
    echo -e "${CYAN}── 全部 15 个 demo ──────────────────────────────${NC}"
    for entry in "${DEMOS[@]}"; do
        printf "  %-32s %s\n" "${entry%%|*}" "${entry##*|}"
    done
}

find_by_number() {
    local n="$1"
    local prefix
    prefix=$(printf "%02d_" "$n")
    for entry in "${DEMOS[@]}"; do
        local d="${entry%%|*}"
        if [[ "$d" == ${prefix}* ]]; then
            echo "$entry"
            return 0
        fi
    done
    return 1
}

# ---- 主分发 ---------------------------------------------------------------
case "${1:-all}" in
    list)
        list_all
        ;;
    all|"")
        for entry in "${DEMOS[@]}"; do
            run_one "$entry"
        done
        ;;
    [0-9]|[0-9][0-9])
        found=$(find_by_number "$1") || {
            echo -e "${RED}❌ 没找到编号为 $1 的 demo${NC}"
            echo "   用 ./run.sh list 看所有 demo"
            exit 1; }
        run_one "$found"
        ;;
    *)
        echo -e "${RED}❌ 未知参数: $1${NC}"
        echo "用法: $0 [all|list|<编号>]"
        exit 1
        ;;
esac

echo -e "${GREEN}🎉 运行完毕!${NC}"
