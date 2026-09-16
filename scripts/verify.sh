#!/usr/bin/env bash
# 一键验证脚本：后端编译/测试 + 前端 lint/build
# 用法：./scripts/verify.sh [--skip-tests]
# Agent 修改代码后应运行本脚本自检。

set -euo pipefail
cd "$(dirname "$0")/.."

SKIP_TESTS="${1:-}"
FAILED=()

step() { printf "\n\033[1;34m==> %s\033[0m\n" "$1"; }
ok()   { printf "\033[1;32m✔ %s\033[0m\n" "$1"; }
fail() { printf "\033[1;31m✘ %s\033[0m\n" "$1"; FAILED+=("$1"); }

# ---------- 后端 ----------
step "后端编译"
if (cd backend && ./mvnw -q clean compile); then ok "后端编译"; else fail "后端编译"; fi

if [ "$SKIP_TESTS" != "--skip-tests" ]; then
  step "后端测试"
  if (cd backend && ./mvnw -q test); then ok "后端测试"; else fail "后端测试"; fi
fi

# ---------- 前端 ----------
step "前端 Lint"
if (cd frontend && npm run lint); then ok "前端 Lint"; else fail "前端 Lint"; fi

step "前端 Build"
if (cd frontend && npm run build); then ok "前端 Build"; else fail "前端 Build"; fi

# ---------- 汇总 ----------
printf "\n"
if [ ${#FAILED[@]} -eq 0 ]; then
  ok "全部验证通过"
  exit 0
else
  fail "未通过: ${FAILED[*]}"
  exit 1
fi
