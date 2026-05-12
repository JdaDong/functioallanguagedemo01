#!/bin/bash
# regression_run.sh — 跑全 50 个 demo，记录 PASS/FAIL 和耗时
# 输出：/tmp/regression_<label>.log + /tmp/regression_summary.tsv

ROOT="/Users/jiangdadong/CodeBuddy/functioallanguagedemo01"
SUMMARY="/tmp/regression_summary.tsv"
TIMEOUT=120                                  # 每个 demo 最多 2 分钟

cd "$ROOT" || exit 1
> "$SUMMARY"

# 散文件 (clj) demo 列表
SINGLE_FILES=(
  "01_basics_and_collections.clj"
  "02_immutable_data_structures.clj"
  "03_higher_order_and_transducers.clj"
  "04_destructuring.clj"
  "05_recur_and_loop.clj"
  "06_lazy_seq_and_infinite.clj"
  "07_multimethods.clj"
  "08_protocols_and_records.clj"
  "09_macros_intro.clj"
  "10_macros_anaphoric.clj"
  "11_macros_dsl.clj"
  "12_macros_state_machine.clj"
  "13_reader_macros.clj"
  "14_macro_hygiene.clj"
  "15_atoms_and_state.clj"
  "16_refs_and_stm.clj"
  "17_agents_async.clj"
  "18_futures_and_delay.clj"
  "21_reducers_parallel.clj"
  "22_spec_basic.clj"
  "25_data_oriented_programming.clj"
  "26_edn_format.clj"
  "35_reagent_mental_model.clj"
  "36_re_frame_event_loop.clj"
  "37_option_pricing_dsl.clj"
  "38_utxo_ledger.clj"
  "40_nubank_style_event_sourcing.clj"
  "44_transducers_advanced.clj"
  "47_macros_deep.clj"
  "48_metadata_protocols.clj"
  "49_reducers_fold.clj"
  "50_java_interop_advanced.clj"
)

# 项目档目录列表
PROJ_DIRS=(
  "19_core_async_channels"
  "20_core_async_pipeline"
  "23_spec_generators"
  "24_malli_schema"
  "27_transit_format"
  "28_schema_evolution"
  "29_ring_handler"
  "30_compojure_router"
  "31_reitit_data_router"
  "32_datomic_mini"
  "33_datalog_query"
  "34_metabase_style_pipeline"
  "39_csv_to_json_etl"
  "41_core_async_pipeline_async"
  "42_core_async_pubsub_mix"
  "43_core_async_error_dlq"
  "45_spec_advanced"
  "46_malli_advanced"
)

run_one() {
  local label="$1"
  local cmd="$2"
  local log="/tmp/regression_${label}.log"
  local t0
  local t1
  local elapsed
  local rc
  local status
  t0=$(date +%s)
  timeout "$TIMEOUT" bash -c "$cmd </dev/null" > "$log" 2>&1
  rc=$?
  t1=$(date +%s)
  elapsed=$((t1 - t0))
  status="PASS"
  if [ $rc -ne 0 ]; then status="FAIL($rc)"; fi
  printf "%s\t%s\t%ds\t%s\n" "$label" "$status" "$elapsed" "$log" | tee -a "$SUMMARY"
}

echo "=== 散文件 32 个 ==="
for f in "${SINGLE_FILES[@]}"; do
  num="${f%%_*}"
  run_one "demo${num}" "clojure -M clojure/$f"
done

echo ""
echo "=== 项目档 18 个 ==="
for d in "${PROJ_DIRS[@]}"; do
  num="${d%%_*}"
  run_one "demo${num}" "cd clojure/$d && clojure -M:run"
done

echo ""
echo "=== 汇总 ==="
total=$(wc -l < "$SUMMARY")
pass=$(grep -c $'\tPASS\t' "$SUMMARY")
fail=$((total - pass))
echo "total=$total pass=$pass fail=$fail"
