#!/usr/bin/env bash
# demo.sh — 假设 server 已在 35100 运行（用 `clojure -M:run-server` 启动）
# 调用 8 个核心端点，打印 status + 关键字段。

set -e
PORT="${1:-35100}"
BASE="http://localhost:${PORT}"

# 颜色（可选）
G='\033[0;32m'; B='\033[1;34m'; N='\033[0m'

step() { echo -e "\n${B}── $* ──${N}"; }
hit() {
  local method="$1"; local path="$2"; local data="$3"
  echo "  ${method} ${path}"
  if [ -n "$data" ]; then
    curl -sS -X "$method" "${BASE}${path}" \
      -H 'Content-Type: application/json' -d "$data" \
      -w "\n  ${G}status=%{http_code} time=%{time_total}s${N}\n"
  else
    curl -sS -X "$method" "${BASE}${path}" \
      -w "\n  ${G}status=%{http_code} time=%{time_total}s${N}\n"
  fi
}

step "1. /health"
hit GET /health

step "2. POST /users/register"
hit POST /users/register \
  '{"user-id":"DEMO-1","name":"Demo User","email":"d@x","password":"pw"}'

step "3. POST /users/login"
hit POST /users/login \
  '{"user-id":"DEMO-1","password":"pw"}'

step "4. POST /orders（创建订单 + NEW10 优惠券）"
hit POST /orders \
  '{"order-id":"DEMO-O1","user-id":"DEMO-1",
    "items":[{"sku":"SKU-A","qty":2,"unit-price":50.0},
             {"sku":"SKU-B","qty":1,"unit-price":120.0}],
    "coupon":"NEW10"}'

step "5. GET /orders/DEMO-O1"
hit GET /orders/DEMO-O1

step "6. POST /orders/DEMO-O1/pay"
hit POST /orders/DEMO-O1/pay '{"amount":210.0}'

step "7. POST /orders/DEMO-O1/ship"
hit POST /orders/DEMO-O1/ship '{"tracking-no":"SF-DEMO-9001"}'

step "8. POST /orders/DEMO-O1/deliver"
hit POST /orders/DEMO-O1/deliver

step "9. GET /analytics/sales-by-sku（CSV 已 ingest 48 单）"
hit GET /analytics/sales-by-sku

step "10. GET /analytics/top-users?n=3"
hit GET /analytics/top-users?n=3

step "11. GET /analytics/window/mom?year=2026&month=4"
hit "GET" "/analytics/window/mom?year=2026&month=4"

echo -e "\n${G}✅ demo.sh 全部 11 次调用完成${N}"
