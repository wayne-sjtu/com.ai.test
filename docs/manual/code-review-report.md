# 代码审查报告 · 主线一交付前全面审查

> 审查日期：2026-09-16
> 审查范围：`backend/` 全部 service / controller / domain / repository / entity 层，`frontend/` 关键页面与请求封装
> 审查方式：代码审查子 agent 实地阅读核实（交易主链路、资金账务、适当性、认证 Session、前端请求层）
> 修复状态：**Critical 4 项 + Major 7 项全部修复，Minor 9 项中高价值项已顺手修复**，`./scripts/verify.sh` 全绿（后端编译 + 全量测试 + 前端 lint/build）

---

## 一、总体评价

整体架构清晰、分层意图明确：

- `domain` 层（`OrderStateMachine` / `ValuationCalculator` / `RiskLevelRule`）保持纯函数独立性，不依赖 Spring
- 核心交易链路围绕「校验链 → 状态机 → 留痕」设计，状态机约束完整且迁移处均有事件轨迹
- 双身份 Session 隔离（`/api/customer/**` vs `/api/admin/**`）实现干净

主要问题集中在三类：**资金与份额的并发一致性**（check-then-act 无锁）、**幂等键的正确性**（前端生成时机 + 后端失败单语义）、**查询性能**（N+1 / 全表扫描）。

---

## 二、Critical（必须立即修复，已全部修复）

### C-1 资金账户余额并发更新无锁，存在丢失更新

- **位置**：`CapitalService`（freeze / deduct / unfreeze / returnCash）、`CapitalAccount`、`CapitalAccountRepository`
- **问题**：四个资金动作均为「读余额 → 改余额 → save」的 read-modify-write，但既无 `@Version` 乐观锁也无悲观行锁。`capital_flow` 的 `uk_cf_order_action` 唯一约束只能防**同一订单同一动作**重复入账，无法防**同一客户两个不同订单并发冻结**时后写覆盖先写（可用余额只扣了第二笔的量），导致资金超卖 / 账实不符。
- **修复**：
  1. `CapitalAccountRepository` 新增 `findByCustomerNoForUpdate`（`@Lock(PESSIMISTIC_WRITE)`）
  2. `requireAccount` 改用行锁读取
  3. 四个动作补 `@Transactional`（锁必须在事务内才生效）
- **文件**：`repository/CapitalAccountRepository.java`、`service/CapitalService.java`

### C-2 clientRequestId 幂等 check-then-act，并发重复提交返回 500

- **位置**：`OrderService.purchase` / `redeem`（先 `findByClientRequestId` 判重再创建订单）
- **问题**：`client_request_id` 有唯一约束。并发下两个相同幂等键的请求都通过判重检查，第二个在 `save` 时违反唯一约束抛 `DataIntegrityViolationException`，全局异常处理器未处理 → 500，而非幂等返回原订单。
- **修复**：全局异常处理器新增并发冲突兜底——`OptimisticLockException` / `ConcurrencyFailureException` / `DataIntegrityViolationException` 统一映射 **409 `{code:"CONCURRENT_CONFLICT"}`**，客户端用原幂等键重试即可获得原订单结果。
- **文件**：`controller/ApiExceptionHandler.java`

### C-3 前端二次确认复用幂等键 + 后端失败单包装成功，流程失效并误报成功

- **位置**：`PurchasePage.tsx`、`OrderService.purchase` 幂等分支
- **问题**：第一次提交因适当性 `CONFIRM` 档失败 → 后端创建 `FAILED` 订单并占用该 `clientRequestId`。用户点「我已知晓风险，继续购买」复用同一幂等键重提，后端幂等命中返回历史 FAILED 单，且被 `PurchaseOutcome.success` 包装 → Controller 返回 200 → 前端提示「申购提交成功」并跳转，**实际未成交**。
- **修复**：
  1. 后端：新增 `idempotentOutcome`——幂等命中 FAILED 单返回失败（`ORDER_FAILED`，提示更换 `clientRequestId`），成功/在途单返回原订单
  2. 前端：`SUITABILITY_CONFIRM_REQUIRED` 分支二次确认前 `setClientRequestId(newRequestId())` 换新键
- **文件**：`service/OrderService.java`、`pages/customer/PurchasePage.tsx`

### C-4 赎回页幂等键每次提交重新生成，防重复提交失效

- **位置**：`RedeemPage.tsx` `submit` 内生成 `clientRequestId`
- **问题**：快速重复点击、或首次请求实际成功但响应丢失后的重试，均产生不同幂等键 → 两笔真实赎回下单（重复冻结份额），违背幂等设计初衷。
- **修复**：`useState` 进入页面生成一次稳定幂等键；仅请求失败后换键（允许修改后重试）；提交按钮 `disabled={submitting || available <= 0}`。
- **文件**：`pages/customer/RedeemPage.tsx`

---

## 三、Major（近期修复，已全部修复）

### M-1 赎回份额冻结 TOCTOU，可并发超卖

- **位置**：`OrderService.validateRedeemChain`（校验可用份额）→ `freezeShares`（再读再写），两步非原子
- **修复**：`PositionRepository` 新增 `findByCustomerNoAndProductCodeForUpdate`（悲观行锁）；`freezeShares` 锁内复核可用份额不足即抛 `INSUFFICIENT_SHARES`，校验与更新原子化。
- **文件**：`repository/PositionRepository.java`、`service/OrderService.java`

### M-2 申购校验链顺序导致额度泄漏

- **位置**：`OrderService.validateChain`（原资金账户校验在额度占用之后）
- **问题**：额度占用（行锁 + `usedQuota` 增加）后若资金账户不存在抛异常回滚，事务回滚本可释放，但与新签约无资金账户场景叠加时用户永远无法交易且报错语义混乱。
- **修复**：资金账户存在性校验（纯查询）前移到额度占用之前；`catch` 分支补 `releaseQuota`；签约时幂等补建资金账户（余额 0）——已存在则跳过（种子数据客户已预置，幂等避免唯一约束冲突）。
- **文件**：`service/OrderService.java`、`service/WealthAccountService.java`

### M-3 赎回确认净值缺失静默回退 1.0

- **位置**：`OrderService.confirmRedeemAndSettle`、`PositionViewService`（`nav != null ? nav : ONE`）
- **问题**：净值缺失时回退默认值 1 会产生错误估值 = 资金差错。
- **修复**：确认结算链路净值缺失硬失败（`NAV_NOT_FOUND`，由回调重试/人工介入）；持仓视图保留兜底仅用于零持仓展示场景。
- **文件**：`service/OrderService.java`、`service/PositionViewService.java`

### M-4 InvestPlanService 批量调度 rollback-only 污染

- **位置**：`InvestPlanService.triggerDuePlans` 整体 `@Transactional` + 循环 catch
- **问题**：单计划数据库层冲突被 catch 后，JPA 事务可能已标记 rollback-only，后续 save 与最终提交抛 `UnexpectedRollbackException`，**一个失败污染整批**。
- **修复**：移除外层 `@Transactional`，每个计划经 `orderService.purchase` 独立事务提交，失败捕获不阻断整批。
- **文件**：`service/InvestPlanService.java`

### M-5 Session 固定防护缺失

- **位置**：`CustomerAuthController` / `AdminAuthController` login
- **问题**：登录成功只在当前 Session 上 setAttribute，未轮换 JSESSIONID，存在 Session Fixation 风险。
- **修复**：登录成功后 `session.invalidate()` 废弃旧会话 → `getSession(true)` 重建（新 JSESSIONID）→ 再写入身份标记。
- **文件**：`controller/CustomerAuthController.java`、`controller/AdminAuthController.java`

### M-6 前端请求层对非 JSON 响应与网络错误无保护

- **位置**：`services/api.ts`
- **问题**：`JSON.parse` 无 try/catch（网关 HTML 错误页抛 SyntaxError）；解析发生在 401 判断之前，401 场景可能不触发跳转；断网抛原生 TypeError。
- **修复**：fetch 网络异常包装为 `ApiRequestError(0, 'NETWORK_ERROR')`；401 判断前置（不依赖响应体可解析）；`JSON.parse` 安全解析（非 JSON body 置 null 不崩溃）。
- **文件**：`services/api.ts`

### M-7 全表扫描 / N+1 查询

- **位置与修复**：

| 位置 | 问题 | 修复 |
|------|------|------|
| `PositionViewService.view` | 循环内逐条查 Product/ProductNav（N+1） | `findByProductCodeIn` + `findLatestByProductCodeIn` 批量查询，内存 Map 组装，往返从 2N+1 降为 3 |
| `TicketService.adminSearch` | `findAllByOrderByCreatedAtDesc` 全表加载后内存过滤 | repository 新增组合条件 JPQL `search(status, ticketType)`，条件下推数据库 |
| `RiskAssessmentRepository.findLatest` | append-only 全历史加载取第一条 | 派生查询 `findTopByCustomerNoOrderByCreatedAtDesc`（数据库层 Top 1） |
| `NavPublishService.publish` | `positionRepository.findAll()` 全表扫描过滤产品 | `findByProductCode` 按产品直查 |

- **文件**：`PositionViewService`、`TicketService`/`TicketRepository`、`RiskAssessmentRepository`、`NavPublishService`/`PositionRepository`、`ProductRepository`、`ProductNavRepository`

### M-8 守卫与 401 处理逻辑缺陷（前端）

- **问题**：
  1. 守卫任意异常都跳登录（网络/500 误跳）
  2. 全局 401 handler 用 `window.location.assign` 硬刷新丢失回跳路径
  3. `AdminGuard` 定义在 `CustomerGuard.tsx` 文件名不匹配
- **修复**：
  1. 守卫仅 401 跳登录，网络/服务器错误展示错误态（不误跳）
  2. 401 统一跳转改用 router `navigate` + `state.from` 保留回跳路径
  3. 重命名为 `auth/guards.tsx`（`CustomerGuard` + `AdminGuard` 统一出口）
- **文件**：`auth/guards.tsx`（原 `CustomerGuard.tsx` 删除）、`App.tsx`

### M-9 Blob 下载过早 revoke，Firefox 兼容问题

- **位置**：`api.ts` download
- **修复**：`<a>` 挂载 DOM → click → remove，`setTimeout(..., 0)` 延迟 `revokeObjectURL`。
- **文件**：`services/api.ts`

---

## 四、修复过程中的回归与处理

| 回归 | 根因 | 处理 |
|------|------|------|
| Spring 上下文启动失败（Ambiguous mapping） | `ApiExceptionHandler` 存在旧会话遗留的重复并发冲突处理器，与新增的冲突 | 合并为单一 `handleConcurrency` 处理器 |
| 签约集成测试失败（资金账户唯一约束） | 种子数据已为演示客户预置资金账户，签约时无条件补建触发唯一约束 | 改为幂等补建：`findByCustomerNo` 为空才创建 |

修复后 `./scripts/verify.sh` 全量验证通过：后端编译 + 全量集成测试（100 个测试，0 失败 0 错误）+ 前端 Oxlint + tsc + build。

---

## 五、Minor / 遗留演进项（未修复，不影响单机演示正确性）

| # | 项 | 位置 | 说明 |
|---|-----|------|------|
| 1 | controller 下沉业务逻辑 | `MessageController` / `FlowController` | 直接注入 repository 做查询/已读更新/CSV 组装，绕过 service 层；建议新增 `MessageService` / `FlowService` |
| 2 | 额度占用/释放口径不一致 | `OrderService` | 申购占用按 `amount`、赎回释放按 `gross`（份额×净值），口径差异 + 净值波动会积累漂移；**需业务确认 `usedQuota` 语义后统一** |
| 3 | 单号生成随机空间窄 | `OrderService` / `TicketService` / `WealthAccountService` | 时间戳 + 3 位随机（账号仅 2 位），高并发碰撞靠唯一约束兜底 409；建议换 UUID/分布式 ID |
| 4 | 外部 TA 回调去重仅进程内 | `ExternalTaConfirmService` | `processedSerialNos` 进程内 Set，多实例不共享；且「先标记后处理」在处理异常时丢重试；建议持久化去重或处理后标记 |
| 5 | 受理/确认流水号覆盖 | `InProcessExternalTaClient` | 异步回调生成新 serialNo 覆盖订单 `taSerialNo`，事件轨迹出现两个流水号，追溯语义模糊 |
| 6 | CSV 导出未转义 | `FlowController.csv()` | 未处理逗号/引号/换行及 `=` 公式注入；当前字段受控风险低 |
| 7 | 前端请求竞态无取消保护 | 守卫 / `PurchasePage` | useEffect 无 AbortController/mounted 标志，StrictMode 重复请求、快速切换产品旧响应可能覆盖新状态 |
| 8 | 会话内存存储无超时配置 | `application.yml` | 未配 `server.servlet.session.timeout`；多实例部署需 Redis/JWT |
| 9 | 管理端故障注入切换 | 外部 TA Mock | 缺少 HTTP 切换端点（当前仅配置文件） |

---

## 六、审查结论

- **Critical 4 项、Major 7 项全部修复**，修复聚焦正确性、并发安全、幂等语义与查询性能，无大规模分层重构
- 全量回归验证通过，主线一交付物（34 后端端点 + 19 前端页面 + 100 项集成测试）可交付
- Minor 9 项作为后续演进任务记录于 `docs/context.md`，其中**额度口径（#2）需业务方确认语义后处理**

---

## 七、修复验证 SOP

三层验证由浅入深：自动化回归 → 修复项定向手工验证 → 端到端主链路走查。

### 7.1 第 1 层：自动化全量回归

```bash
./scripts/verify.sh
```

- 后端编译 + 全量集成测试 + 前端 lint/tsc/build
- 覆盖：幂等语义、校验链顺序、净值缺失硬失败、签约幂等补建资金账户、批量调度拆事务
- 作用：回归保护，证明修复未破坏既有功能

### 7.2 第 2 层：修复项定向手工验证

启动环境：

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # 终端1（8080）
cd frontend && npm run dev                                              # 终端2（5173）
```

逐项验证：

| 验证项 | 操作步骤 | 预期结果（修复后） |
|--------|----------|----------|
| C-1 资金并发 | 用陈四账号并发发两笔申购（或观察后端日志无负余额） | 冻结/扣划串行化，无丢失更新 |
| C-2 幂等并发 409 | 两条 curl 用**同一 clientRequestId** 并发 POST `/api/customer/orders/purchase` | 一条 200 返回原订单，另一条 409 `CONCURRENT_CONFLICT`（而非 500） |
| C-3 二次确认 | 陈一（低风险等级）申购 R4 产品 → 弹「风险不匹配」→ 点「继续购买」 | 第二次请求真正下单成功，订单状态非 FAILED |
| C-4 赎回连点 | 持仓页发起赎回，快速连点提交按钮 | 只产生一笔赎回订单（按钮禁用 + 稳定幂等键） |
| M-1 份额冻结 | 陈五（10000 冻结份额）发起赎回，输入超过可用份额 | 明确提示「可用份额不足」 |
| M-5 会话重建 | 登录前记下 Cookie 中的 JSESSIONID → 登录后再查看 | JSESSIONID 已更换（防 Session 固定） |
| M-6/M-8 前端容错 | 停掉后端 → 刷新 C 端页面 | 显示「网络错误」提示，**不跳转登录页** |
| 401 回跳 | 未登录直接访问 `/customer/orders` | 跳登录页，登录后回到原页面 |
| M-7 批量查询 | 打开 `/customer/positions` | 后端日志 SQL 条数为常数（3 条批量查询，不随持仓数增长） |

C-2 关键 curl 示例（同一幂等键并发提交）：

```bash
# 获取 Session（渠道码为 PC_WEB）
curl -c cookies.txt -X POST http://localhost:8080/api/customer/login \
  -H 'Content-Type: application/json' \
  -d '{"mobile":"13800000001","password":"Passw0rd!","channelCode":"PC_WEB"}'

# 同一幂等键并发提交两次
curl -b cookies.txt -X POST http://localhost:8080/api/customer/orders/purchase \
  -H 'Content-Type: application/json' \
  -d '{"productCode":"P-PR-01","amount":500,"clientRequestId":"TEST-DUP-001","confirmRisk":false}' &
curl -b cookies.txt -X POST http://localhost:8080/api/customer/orders/purchase \
  -H 'Content-Type: application/json' \
  -d '{"productCode":"P-PR-01","amount":500,"clientRequestId":"TEST-DUP-001","confirmRisk":false}' &
wait
```

### 7.3 第 3 层：端到端主链路走查

按 `docs/manual/demo-guide.md` 第五章的 10 步演示脚本执行（陈四为主角：登录 → 货架 → 申购 → 持仓 → 赎回 → 撤单 → 流水导出 → 管理端工单/净值/健康矩阵），确认修复后业务闭环无回归。

