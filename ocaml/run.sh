#!/bin/bash
#
# OCaml 函数式编程 Demo 一键运行脚本
# 用法:
#   ./run.sh              运行"零依赖组"(01~22, 仅需 stdlib + dune)
#   ./run.sh 1            只运行 Demo 01
#   ./run.sh deps         运行依赖组(23~37, 需 opam install core async ppx_jane 等)
#   ./run.sh ocaml5       运行 OCaml 5 组(16~19、40, 需 OCaml >= 5.0)
#   ./run.sh all          运行全部
#   ./run.sh list         仅列出所有可用 demo
#
# 说明:
#   - 全部 demo 形如 <编号>_<名>/main.ml + dune, 由根目录 dune-project 统一管理
#   - 零依赖组: 01~22 (除 16~19 需 OCaml 5)
#   - 依赖组:   23~30 用 Jane Street Core/Async/ppx_jane;
#               31~37 多为单文件演示 (Or_error/HM 等), 也用 stdlib
#   - OCaml 5 组: 16/17 effect handlers, 18 Domain, 19 Atomic, 40 多核光追
#   - Demo 29 是 expect_test, 跑法是 `dune runtest`
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
if ! command -v dune &> /dev/null; then
    echo -e "${RED}❌ 未检测到 dune, 请先安装 OCaml 工具链:${NC}"
    echo "   brew install opam               # macOS"
    echo "   opam init -y && opam switch create 5.1.1"
    echo "   eval \$(opam env)"
    echo "   opam install -y dune"
    exit 1
fi

OCAML_VERSION=$(ocaml -version 2>&1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1)
OCAML_MAJOR=$(echo "$OCAML_VERSION" | cut -d. -f1)

echo -e "${GREEN}🐫 OCaml 函数式编程 Demo${NC}"
echo -e "${YELLOW}   OCaml: $OCAML_VERSION   |   dune: $(dune --version)${NC}"
separator

# ---- demo 清单 ------------------------------------------------------------
# 零依赖组 (01~15、20~22): stdlib + dune
FREE_DEMOS=(
  "01_basics_and_adt|基础语法 + 代数数据类型"
  "02_pattern_matching|模式匹配 (含 OR-pattern / guard)"
  "03_higher_order_and_currying|高阶函数 + 柯里化 + 函数组合"
  "04_variants_and_records|变体 + 记录 + 可变字段"
  "05_exceptions_vs_result|异常 vs ('a, 'e) result"
  "06_tail_recursion|尾递归 + [@tail_mod_cons]"
  "07_mutable_refs_and_arrays|ref / mutable / Array"
  "08_io_and_channels|IO 与通道 (in_channel/out_channel)"
  "09_modules_and_signatures|模块系统 + .mli 签名"
  "10_functors_basic|Functor 入门 (Set 风格)"
  "11_functors_advanced|Functor 进阶 (多参 + 共享约束)"
  "12_first_class_modules|一等模块 (first-class modules)"
  "13_abstract_types|抽象类型 + 信息隐藏"
  "14_include_and_extension|include + 模块扩展"
  "15_polymorphic_variants|多态变体 [\`Foo | \`Bar]"
  "20_gadt_interpreter|GADT 类型安全解释器"
  "21_polymorphism_and_variance|参数多态 + 协变/逆变"
  "22_typeclass_via_modules|用 module type + functor 模拟 type class"
)

# OCaml 5 组: 16/17/18/19 + 40
OCAML5_DEMOS=(
  "16_effects_handlers|代数效应 + handler (OCaml 5)"
  "17_effects_as_generators|用 effect 实现 generator"
  "18_domains_parallel|Domain 多核并行"
  "19_atomic_and_lockfree|Atomic 原子 + 无锁数据结构"
  "40_raytracer_multicore|Domain 并行光线追踪"
)

# 依赖组 (23~37): 需 opam install
DEPS_DEMOS=(
  "23_core_basics|Jane Street Core 入门"
  "24_async_basics|Async 异步 (Lwt 风格的 JS 版)"
  "25_async_rpc|Async RPC (server/client)"
  "26_bin_prot_serialization|bin_prot 二进制序列化"
  "27_incremental_compute|incremental 增量计算"
  "28_command_line|Command_unix CLI 框架"
  "29_expect_tests|ppx_expect 快照测试 (dune runtest)"
  "30_ppx_deriving|ppx_jane 派生宏 (show/eq/sexp)"
  "31_mini_lang_interpreter|迷你语言解释器"
  "32_option_pricing_dsl|期权定价 DSL (Tagless Final)"
  "33_ad_autodiff|自动微分 (forward/reverse mode)"
  "34_etl_pipeline|CSV→JSON ETL 流水线"
  "35_utxo_ledger|UTXO 账本 (Cardano 风格)"
  "36_frp_minimal|极简 FRP (Behavior + Event)"
  "37_hindley_milner_inference|Hindley-Milner 类型推导"
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
    echo -e "${GREEN}📘 ${dir} — ${title}${NC}"
    separator
    if [[ "$dir" == "29_expect_tests" ]]; then
        # expect_test 用 runtest, 不是 exec
        dune runtest "$dir" --force || true
    else
        dune exec "$dir/main.exe"
    fi
    separator
}

run_group() {
    local -n group="$1"
    for entry in "${group[@]}"; do
        run_one "$entry"
    done
}

find_by_number() {
    local n="$1"
    local prefix
    prefix=$(printf "%02d_" "$n")
    for entry in "${FREE_DEMOS[@]}" "${OCAML5_DEMOS[@]}" "${DEPS_DEMOS[@]}"; do
        local dir="${entry%%|*}"
        if [[ "$dir" == ${prefix}* ]]; then
            echo "$entry"
            return 0
        fi
    done
    return 1
}

list_all() {
    echo -e "${CYAN}── 零依赖组 (FREE) ─────────────────────────────${NC}"
    for entry in "${FREE_DEMOS[@]}"; do
        printf "  %-40s %s\n" "${entry%%|*}" "${entry##*|}"
    done
    echo -e "${CYAN}── OCaml 5 组 (需 ocaml >= 5.0) ────────────────${NC}"
    for entry in "${OCAML5_DEMOS[@]}"; do
        printf "  %-40s %s\n" "${entry%%|*}" "${entry##*|}"
    done
    echo -e "${CYAN}── 依赖组 (需 opam install) ────────────────────${NC}"
    for entry in "${DEPS_DEMOS[@]}"; do
        printf "  %-40s %s\n" "${entry%%|*}" "${entry##*|}"
    done
}

# ---- 主分发 ---------------------------------------------------------------
case "${1:-free}" in
    list)
        list_all
        ;;
    free|"")
        echo -e "${YELLOW}运行零依赖组 (01~22, 含 OCaml 5 demos 跳过)${NC}"
        echo -e "${YELLOW}  其它分组: ./run.sh ocaml5 / deps / all${NC}"
        separator
        run_group FREE_DEMOS
        ;;
    ocaml5)
        if [[ "$OCAML_MAJOR" -lt 5 ]]; then
            echo -e "${RED}❌ 当前 OCaml $OCAML_VERSION < 5.0, 无法运行 OCaml 5 demo${NC}"
            echo "   opam switch create 5.1.1 && eval \$(opam env)"
            exit 1
        fi
        run_group OCAML5_DEMOS
        ;;
    deps)
        echo -e "${YELLOW}运行依赖组; 首次需:${NC}"
        echo "   opam install -y core core_unix async ppx_jane bin_prot incremental"
        separator
        run_group DEPS_DEMOS
        ;;
    all)
        run_group FREE_DEMOS
        if [[ "$OCAML_MAJOR" -ge 5 ]]; then
            run_group OCAML5_DEMOS
        else
            echo -e "${YELLOW}⏭  跳过 OCaml 5 组 (当前版本 $OCAML_VERSION < 5.0)${NC}"
        fi
        run_group DEPS_DEMOS
        ;;
    [0-9]|[0-9][0-9])
        found=$(find_by_number "$1") || {
            echo -e "${RED}❌ 没找到编号为 $1 的 Demo${NC}"
            echo "   用 ./run.sh list 看所有可用 demo"
            exit 1; }
        run_one "$found"
        ;;
    *)
        echo -e "${RED}❌ 未知参数: $1${NC}"
        echo "用法: $0 [free|ocaml5|deps|all|list|<编号>]"
        exit 1
        ;;
esac

echo -e "${GREEN}🎉 运行完毕!${NC}"
