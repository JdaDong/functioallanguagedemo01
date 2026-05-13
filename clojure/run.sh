#!/bin/bash
#
# Clojure 函数式编程 Demo 一键运行脚本
# 用法:
#   ./run.sh              运行"零依赖组"(01~18 单文件, 仅需 clj)
#   ./run.sh 1            只运行 Demo 01
#   ./run.sh deps         运行依赖组(子目录形式, 各自带 deps.edn 拉 maven 包)
#   ./run.sh all          运行全部 (51 号大项目除外, 见 51_ecommerce_analytics/demo.sh)
#   ./run.sh list         仅列出所有可用 demo
#
# 说明:
#   - 单文件 demo:   `clojure -M XX_*.clj`
#   - 子目录 demo:   `cd XX_*/ && clojure -M demoXX.clj`  (依赖各目录 deps.edn)
#   - 51 号是完整电商分析项目, 用 `cd 51_ecommerce_analytics && ./demo.sh`
#   - 子目录组首次会拉 maven 包到 ~/.m2 (~分钟级), 之后秒启
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
if ! command -v clojure &> /dev/null; then
    echo -e "${RED}❌ 未检测到 clojure CLI, 请先安装:${NC}"
    echo "   brew install clojure/tools/clojure   # macOS"
    echo "   或参考 https://clojure.org/guides/install_clojure"
    exit 1
fi

if ! command -v java &> /dev/null; then
    echo -e "${RED}❌ 未检测到 java, Clojure 运行时需要 JDK >= 11${NC}"
    echo "   brew install openjdk@21              # macOS"
    exit 1
fi

echo -e "${GREEN}🍃 Clojure 函数式编程 Demo${NC}"
echo -e "${YELLOW}   Java: $(java -version 2>&1 | head -1)${NC}"
echo -e "${YELLOW}   Clojure CLI: $(clojure --version 2>&1 | head -1)${NC}"
separator

# ---- demo 清单 ------------------------------------------------------------
# 零依赖组 (01~18, 21、25、26、35~38、40、44、47~50): 单文件, 仅 clojure.core
FREE_DEMOS=(
  "01_basics_and_collections.clj|基础语法 + 集合"
  "02_immutable_data_structures.clj|persistent 数据结构 (vector/map/set)"
  "03_higher_order_and_transducers.clj|高阶函数 + transducers"
  "04_destructuring.clj|解构 (vector/map/seq)"
  "05_recur_and_loop.clj|recur / loop 显式尾递归"
  "06_lazy_seq_and_infinite.clj|lazy-seq + 无穷序列"
  "07_multimethods.clj|multimethod 开放分派"
  "08_protocols_and_records.clj|defprotocol / defrecord"
  "09_macros_intro.clj|宏入门 quote/unquote/syntax-quote"
  "10_macros_anaphoric.clj|捕获式宏 (anaphoric)"
  "11_macros_dsl.clj|宏写 DSL"
  "12_macros_state_machine.clj|宏写状态机"
  "13_reader_macros.clj|reader 宏 #() #_ #'"
  "14_macro_hygiene.clj|宏卫生 + gensym"
  "15_atoms_and_state.clj|atom + swap! + watcher"
  "16_refs_and_stm.clj|ref + dosync STM 转账守恒"
  "17_agents_async.clj|agent 异步状态"
  "18_futures_and_delay.clj|future / delay / promise"
)

# 中间组: 单文件但用了一些标准库 / 仍然 0 maven 依赖
MID_DEMOS=(
  "21_reducers_parallel.clj|reducers 并行 fork-join"
  "22_spec_basic.clj|clojure.spec.alpha 入门"
  "25_data_oriented_programming.clj|数据驱动编程 (Rich Hickey 风格)"
  "26_edn_format.clj|edn 数据格式"
  "35_reagent_mental_model.clj|Reagent 心智模型 (本地版)"
  "36_re_frame_event_loop.clj|re-frame 事件循环"
  "37_option_pricing_dsl.clj|期权定价 DSL"
  "38_utxo_ledger.clj|UTXO 账本 (Cardano 风格)"
  "40_nubank_style_event_sourcing.clj|Nubank 风格事件溯源"
  "44_transducers_advanced.clj|transducers 进阶"
  "47_macros_deep.clj|宏深度: with-meta / &form"
  "48_metadata_protocols.clj|元数据 + 协议组合"
  "49_reducers_fold.clj|reducers fold + r/foldcat"
  "50_java_interop_advanced.clj|Java interop 进阶"
)

# 依赖组: 子目录形式, 各自带 deps.edn
DEPS_DEMOS=(
  "19_core_async_channels|core.async chan/go/<! >!"
  "20_core_async_pipeline|core.async pipeline 保序"
  "23_spec_generators|spec + test.check 生成器"
  "24_malli_schema|metosin/malli 数据校验"
  "27_transit_format|Transit 二进制 edn"
  "28_schema_evolution|schema 演进 (向前/向后兼容)"
  "29_ring_handler|Ring handler 入门"
  "30_compojure_router|Compojure 路由"
  "31_reitit_data_router|Reitit 数据驱动路由"
  "32_datomic_mini|Datomic 入门 (in-memory)"
  "33_datalog_query|Datalog 查询"
  "34_metabase_style_pipeline|Metabase 风格分析管道"
  "39_csv_to_json_etl|CSV→JSON ETL"
  "41_core_async_pipeline_async|pipeline-async 保序异步"
  "42_core_async_pubsub_mix|core.async pub/sub + mix"
  "43_core_async_error_dlq|core.async 错误 DLQ"
  "45_spec_advanced|spec 进阶"
  "46_malli_advanced|malli 进阶"
)

# ---- 运行函数 -------------------------------------------------------------
run_single_file() {
    local entry="$1"
    local file="${entry%%|*}"
    local title="${entry##*|}"
    if [[ ! -f "$file" ]]; then
        echo -e "${RED}❌ 文件不存在: $file${NC}"
        return 1
    fi
    echo -e "${GREEN}📘 ${file} — ${title}${NC}"
    separator
    clojure -M "$file"
    separator
}

run_dir_demo() {
    local entry="$1"
    local dir="${entry%%|*}"
    local title="${entry##*|}"
    if [[ ! -d "$dir" ]]; then
        echo -e "${RED}❌ 目录不存在: $dir${NC}"
        return 1
    fi
    # 推断 demo 文件名: demoXX.clj
    local num="${dir%%_*}"
    local demo_file="demo${num}.clj"
    echo -e "${GREEN}📘 ${dir}/${demo_file} — ${title}${NC}"
    separator
    (cd "$dir" && clojure -M "$demo_file")
    separator
}

run_group_single() {
    local -n group="$1"
    for entry in "${group[@]}"; do
        run_single_file "$entry"
    done
}

run_group_dir() {
    local -n group="$1"
    for entry in "${group[@]}"; do
        run_dir_demo "$entry"
    done
}

find_by_number() {
    local n="$1"
    local prefix
    prefix=$(printf "%02d_" "$n")
    # 单文件查找
    for entry in "${FREE_DEMOS[@]}" "${MID_DEMOS[@]}"; do
        local f="${entry%%|*}"
        if [[ "$f" == ${prefix}* ]]; then
            echo "single|$entry"
            return 0
        fi
    done
    # 子目录查找
    for entry in "${DEPS_DEMOS[@]}"; do
        local d="${entry%%|*}"
        if [[ "$d" == ${prefix}* ]]; then
            echo "dir|$entry"
            return 0
        fi
    done
    return 1
}

list_all() {
    echo -e "${CYAN}── 零依赖组 (FREE, 单文件 clojure -M) ──────────${NC}"
    for entry in "${FREE_DEMOS[@]}"; do
        printf "  %-44s %s\n" "${entry%%|*}" "${entry##*|}"
    done
    echo -e "${CYAN}── 中间组 (MID, 0 maven 依赖, 单文件) ──────────${NC}"
    for entry in "${MID_DEMOS[@]}"; do
        printf "  %-44s %s\n" "${entry%%|*}" "${entry##*|}"
    done
    echo -e "${CYAN}── 依赖组 (DEPS, 子目录 + deps.edn) ────────────${NC}"
    for entry in "${DEPS_DEMOS[@]}"; do
        printf "  %-44s %s\n" "${entry%%|*}" "${entry##*|}"
    done
    echo -e "${CYAN}── 完整项目 ────────────────────────────────────${NC}"
    echo "  51_ecommerce_analytics/    电商实时分析 (见 51_ecommerce_analytics/demo.sh)"
}

# ---- 主分发 ---------------------------------------------------------------
case "${1:-free}" in
    list)
        list_all
        ;;
    free|"")
        echo -e "${YELLOW}运行零依赖组 (01~18); 其它: ./run.sh mid / deps / all${NC}"
        separator
        run_group_single FREE_DEMOS
        ;;
    mid)
        echo -e "${YELLOW}运行中间组 (单文件、0 maven 依赖)${NC}"
        separator
        run_group_single MID_DEMOS
        ;;
    deps)
        echo -e "${YELLOW}运行依赖组; 首次拉 maven 包 (~分钟级)${NC}"
        separator
        run_group_dir DEPS_DEMOS
        ;;
    all)
        run_group_single FREE_DEMOS
        run_group_single MID_DEMOS
        run_group_dir DEPS_DEMOS
        echo -e "${YELLOW}⏭  跳过 51 号完整项目 — 请用 cd 51_ecommerce_analytics && ./demo.sh${NC}"
        ;;
    [0-9]|[0-9][0-9])
        result=$(find_by_number "$1") || {
            echo -e "${RED}❌ 没找到编号为 $1 的 Demo${NC}"
            echo "   用 ./run.sh list 看所有可用 demo"
            exit 1; }
        kind="${result%%|*}"
        rest="${result#*|}"
        if [[ "$kind" == "single" ]]; then
            run_single_file "$rest"
        else
            run_dir_demo "$rest"
        fi
        ;;
    *)
        echo -e "${RED}❌ 未知参数: $1${NC}"
        echo "用法: $0 [free|mid|deps|all|list|<编号>]"
        exit 1
        ;;
esac

echo -e "${GREEN}🎉 运行完毕!${NC}"
