#!/bin/bash
#
# Elixir 函数式编程 Demo 一键运行脚本
# 用法:
#   ./run.sh              运行"零依赖"组 (01~08)
#   ./run.sh 1            只运行 Demo 01
#   ./run.sh all          运行全部 (含需要 Mix.install 拉依赖的 09~15, 首次较慢)
#   ./run.sh deps         只跑需要依赖的 Demo (09~15)
#
# 说明:
#   - 01~08 零依赖, 直接 `elixir xx.exs` 即可
#   - 09~15 用 Mix.install 嵌入式拉 hex 包, 首次运行会下载 (~1-3 分钟), 之后有缓存
#   - Demo 10 (Phoenix) 会在 4001 端口短暂启一个 HTTP 服务并自测
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

if ! command -v elixir &> /dev/null; then
    echo -e "${RED}❌ 未检测到 Elixir, 请先安装:${NC}"
    echo "   brew install elixir           # macOS"
    echo "   或使用 asdf: asdf install elixir 1.17.2-otp-27"
    exit 1
fi

echo -e "${GREEN}🚀 Elixir 函数式编程 Demo${NC}"
echo -e "${YELLOW}   Elixir: $(elixir --version | tail -1)${NC}"
separator

FREE_DEMOS=(
  "01_basics_pipeline.exs|基础语法 + 管道 |> + 模式匹配"
  "02_struct_protocol_behaviour.exs|struct / protocol / behaviour 多态三件套"
  "03_with_result_flow.exs|with 语句 × Result 流程控制"
  "04_macros_intro.exs|宏系统入门 quote/unquote/defmacro"
  "05_macros_dsl_router.exs|宏进阶: 自制路由 DSL"
  "06_genserver_agent_task.exs|GenServer / Agent / Task"
  "07_supervisor_registry.exs|Supervisor / DynamicSupervisor / Registry"
  "08_task_supervisor_async_stream.exs|Task.Supervisor × async_stream"
)

DEPS_DEMOS=(
  "09_ecto_repo_changeset_multi.exs|Ecto: Repo/Schema/Changeset/Multi"
  "10_phoenix_router_plug.exs|Phoenix 风格 Plug 路由 (http 4001)"
  "11_liveview_mental_model.exs|LiveView 心智模型 (本地版)"
  "12_flow_genstage_broadway.exs|Flow / GenStage / Broadway 流式管道"
  "13_telemetry_opentelemetry.exs|Telemetry + OpenTelemetry"
  "14_exunit_doctest_mox_streamdata.exs|ExUnit + doctest + Mox + StreamData"
  "15_mix_umbrella_releases.exs|mix / umbrella / releases 骨架"
)

run_one() {
    local entry="$1"
    local file="${entry%%|*}"
    local title="${entry##*|}"
    echo -e "${GREEN}📘 ${file} — ${title}${NC}"
    separator
    elixir "${file}"
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
    local prefix=$(printf "%02d_" "$n")
    for f in ${prefix}*.exs; do
        if [[ -f "$f" ]]; then
            # 拼出"文件|标题"形式
            local title=""
            for entry in "${FREE_DEMOS[@]}" "${DEPS_DEMOS[@]}"; do
                if [[ "${entry%%|*}" == "$f" ]]; then title="${entry##*|}"; fi
            done
            echo "$f|$title"
            return 0
        fi
    done
    return 1
}

case "${1:-free}" in
    free|"")
        echo -e "${YELLOW}运行零依赖组 (01~08); 需要依赖的用 ./run.sh all 或 ./run.sh deps${NC}"
        separator
        run_group FREE_DEMOS
        ;;
    deps)
        echo -e "${YELLOW}运行依赖组 (09~15); 首次会拉 hex 包${NC}"
        separator
        run_group DEPS_DEMOS
        ;;
    all)
        run_group FREE_DEMOS
        run_group DEPS_DEMOS
        ;;
    [0-9]|[0-9][0-9])
        found=$(find_by_number "$1") || {
            echo -e "${RED}❌ 没找到编号为 $1 的 Demo${NC}"; exit 1; }
        run_one "$found"
        ;;
    *)
        echo -e "${RED}❌ 未知参数: $1${NC}"
        echo "用法: $0 [free|deps|all|1..15]"
        exit 1
        ;;
esac

echo -e "${GREEN}🎉 运行完毕!${NC}"
