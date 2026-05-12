#!/usr/bin/env bash
# demo_full.sh — 一键启停：后台启 server → 等就绪 → 跑 demo.sh → 杀 server

set -e
PORT="${1:-35100}"
LOG="/tmp/ecom_server_${PORT}.log"
HERE="$(cd "$(dirname "$0")" && pwd)"

cleanup() {
  if [ -n "$SERVER_PID" ] && kill -0 "$SERVER_PID" 2>/dev/null; then
    echo -e "\n  → stopping server (pid=$SERVER_PID)"
    kill "$SERVER_PID" 2>/dev/null || true
    wait "$SERVER_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

echo "  → starting server on port ${PORT} (log: ${LOG})"
( cd "$HERE" && clojure -M:run-server "$PORT" ) > "$LOG" 2>&1 &
SERVER_PID=$!

# 等就绪：最多 60 秒，每秒 ping 一次 /health
echo -n "  → waiting for /health"
for i in $(seq 1 60); do
  if curl -sS -o /dev/null -w "%{http_code}" "http://localhost:${PORT}/health" 2>/dev/null | grep -q 200; then
    echo "  ✅ ready (${i}s)"
    break
  fi
  echo -n "."
  sleep 1
  if [ "$i" = "60" ]; then
    echo -e "\n  ✗ server failed to start within 60s"
    echo "  --- last 30 lines of $LOG ---"
    tail -30 "$LOG"
    exit 1
  fi
done

bash "$HERE/demo.sh" "$PORT"
