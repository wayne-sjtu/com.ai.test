#!/usr/bin/env bash
# 渠道登录拦截链路验证（spec 功能 1/2：渠道数据驱动 + 登录校验）
# 前置：后端已以 local Profile 启动（8080）
# 用法：./scripts/verify-channel-login.sh

set -u
BASE="http://localhost:8080"
PASS=0; FAIL=0

say()  { printf '\n\033[1;36m==> %s\033[0m\n' "$1"; }
ok()   { printf '  \033[1;32m✔ %s\033[0m\n' "$1"; PASS=$((PASS+1)); }
bad()  { printf '  \033[1;31m✘ %s\033[0m\n' "$1"; FAIL=$((FAIL+1)); }

# 断言：响应体包含期望 code
expect_code() { # $1=描述 $2=期望code $3=响应体
  if echo "$3" | grep -q "\"code\":\"$2\""; then ok "$1 → $2"; else bad "$1（期望 $2，实际：$(echo "$3" | head -c 120)）"; fi
}
expect_ok() { # $1=描述 $2=响应体
  if echo "$2" | grep -q '"customerNo"'; then ok "$1 → 登录成功（$(echo "$2" | grep -o '"name":"[^"]*"')）"; PASS=$((PASS)); else bad "$1（响应：$(echo "$2" | head -c 120)）"; fi
}

CUSTOMER_LOGIN() { # $1=渠道码
  curl -s -X POST "$BASE/api/customer/login" \
    -H 'Content-Type: application/json' \
    -d "{\"mobile\":\"13800000004\",\"password\":\"Passw0rd!\",\"channelCode\":\"$1\"}"
}
ADMIN_PUT_CHANNEL() { # $1=渠道码 $2=状态
  curl -s -b /tmp/adm_ck.txt -X PUT "$BASE/api/admin/channels/$1" \
    -H 'Content-Type: application/json' -d "{\"status\":\"$2\"}"
}

say "分支 1：不存在渠道码 → CHANNEL_INVALID"
expect_code "用未登记渠道登录" "CHANNEL_INVALID" "$(CUSTOMER_LOGIN 'UNREGISTERED_X')"

say "分支 2：已停用渠道（MINI_PROGRAM，种子自带 SUSPENDED）→ CHANNEL_SUSPENDED"
expect_code "用停用渠道登录" "CHANNEL_SUSPENDED" "$(CUSTOMER_LOGIN 'MINI_PROGRAM')"

say "分支 3：启用中的核心渠道（PC_WEB）→ 登录成功"
expect_ok "陈四经 PC_WEB 登录" "$(CUSTOMER_LOGIN 'PC_WEB')"

say "分支 4：管理端动态停用 H5 → 登录即时拦截 → 恢复后再放行"
# 管理端登录
curl -s -c /tmp/adm_ck.txt -X POST "$BASE/api/admin/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin_op","password":"Admin123!"}' > /dev/null
# 4a. 停用前 H5 可登录
expect_ok "停用前经 H5 登录" "$(CUSTOMER_LOGIN 'H5')"
# 4b. 停用
ADMIN_PUT_CHANNEL 'H5' 'SUSPENDED' > /dev/null
expect_code "停用后经 H5 登录" "CHANNEL_SUSPENDED" "$(CUSTOMER_LOGIN 'H5')"
# 4c. 恢复
ADMIN_PUT_CHANNEL 'H5' 'ACTIVE' > /dev/null
expect_ok "恢复后经 H5 登录" "$(CUSTOMER_LOGIN 'H5')"

say "分支 5：空渠道码 → 参数校验拦截"
resp=$(curl -s -X POST "$BASE/api/customer/login" \
  -H 'Content-Type: application/json' \
  -d '{"mobile":"13800000004","password":"Passw0rd!","channelCode":""}')
if echo "$resp" | grep -qE '"code"|"reasons"'; then ok "空渠道码被校验拦截（$(echo "$resp" | head -c 100)）"; else bad "空渠道码未被拦截"; fi

printf '\n\033[1m结果：%d 通过，%d 失败\033[0m\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ] && echo "渠道登录拦截链路 ✔ 全部通过"
exit "$FAIL"
