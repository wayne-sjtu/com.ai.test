# 主线一交付清单与演示手册

> 理财产品销售平台 · "AI 黑客松"参赛作品
> 范围：主线一（客户与渠道服务）全部 17 个功能 + 端到端核心交易主链路
> 更新时间：2026-09-16

---

## 一、启动方式（全本地 Mock，零外部依赖）

```bash
# 后端（local Profile = H2 内存库 + 种子数据 + 进程内双 TA Mock + LLM Mock）
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # 8080

# 前端（Vite 代理 /api → 8080）
cd frontend && npm run dev                                              # 5173
```

- 后端无任何外部依赖：数据库为 H2 内存库（MySQL 方言，启动自动加载 schema.sql + data.sql 种子数据）
- 本行 TA / 外部 TA 为进程内 Mock（外部 TA 支持四种故障注入：NORMAL / TIMEOUT / REJECT / DELAY_CONFIRM）
- AI 投教 LLM 为进程内 Mock（支持故障开关，可演示 FAQ 降级路径）
- 一键验证：`./scripts/verify.sh`（后端编译+测试 + 前端 lint/tsc/build）

### 演示账号

| 身份 | 账号 | 密码 | 演示亮点 |
|------|------|------|----------|
| 客户·陈一 | 13800000001 | Passw0rd! | 已签约已测评，无持仓（干净的下单演示起点） |
| 客户·陈四 | 13800000004 | Passw0rd! | **主力演示号**：2 持仓（自营+代销）+ 1 在途单 + 工单 + 未读消息 |
| 客户·陈五 | 13800000005 | Passw0rd! | 3 持仓含 10000 冻结份额（赎回在途），演示冻结/巨额赎回 |
| 管理端·运营 | admin_op | Admin123! | 订单 / 净值发布 / 健康矩阵 |
| 管理端·客服 | admin_cs | Admin123! | 工单处理 |

> 其余种子客户：13800000002（测评过期，演示强制重测拦截）、13800000003（未签约，演示准入拦截）。

### 种子数据概览

- 6 客户（覆盖适当性三分支 / 测评过期 / 未签约）
- 8 产品（自营 4 + 代销 4，风险等级 R1~R5）
- 240 条净值（30 日走势）
- 5 笔存量持仓、7 笔订单（含 2 笔在途）
- 资金账户与流水（含 balanceAfter 对账锚点）
- 2 个管理端账号

---

## 二、功能清单与演示路径（17 项功能）

### A. 准入与账户（功能 1-5）

| # | 功能 | 演示路径 | 演示要点 |
|---|------|----------|----------|
| 1 | 客户注册登录（Session） | `/customer/login` | 手机号+密码登录，双身份隔离（C 端 Session 访问管理端接口 403） |
| 2 | 实名认证 | `/customer/profile` → Mock 人脸认证 | VERIFIED 状态刷新 |
| 3 | 财富账户签约 | `/customer/profile` → 签约开户 | 自营+代销双交易权限；未签约申购被拦截并引导 |
| 4 | 风险测评 | `/customer/profile` → 开始测评 | 5 题问卷 → C1~C5 等级，有效期 1 年，append-only 快照 |
| 5 | 电子文件签收 | 后端 `POST /api/customer/documents/{type}/sign` | 四类文件幂等签收 |

### B. 产品货架（功能 6-9）

| # | 功能 | 演示路径 | 演示要点 |
|---|------|----------|----------|
| 6 | 统一货架 | `/customer/shelf` | 自营/代销同列，类型/风险/关键词筛选，代销强制风险提示 |
| 7 | 产品详情 | `/customer/shelf/:id` | 要素/费率/额度/时段 + 30 日净值走势（SVG）+ 公告 |
| 8 | 净值展示与来源标注 | 详情页 / 持仓页 | 代销净值标"外部TA同步"（EXTERNAL+时间），自营 INTERNAL |
| 9 | 组合筛选 | `/customer/shelf` 筛选表单 | 七条件动态查询，keyword 支持名称/发行方/代码 |

### C. 交易主链路（功能 10-13）★ 核心演示

| # | 功能 | 演示路径 | 演示要点 |
|---|------|----------|----------|
| 10 | 申购 | 详情页 → 立即申购 | **九步校验链**：签约/权限/时段/起购/适当性/双录/额度/资金；风险不匹配弹二次确认（confirmRisk 重提交） |
| 11 | 赎回 | 持仓页 → 赎回 | 回款 = 份额 × 最新净值 − 赎回费；冻结份额不可赎 |
| 12 | 撤单 / 巨额赎回 / 分红方式 | `/customer/orders` 撤单；赎回巨额触发；`/customer/plans` 分红 Tab | TA 确认前可撤（资金/份额解冻）；巨额赎回 PENDING_QUEUE → 10s 后自动延期确认；分红方式走 TA 报文（自营同步 CONFIRMED / 代销受理） |
| 13 | 适当性校验 | 申购链路内 | C×R 25 行规则表驱动，PASS / CONFIRM / BLOCK 三分支，每次校验留痕 |

### D. 持仓与查询（功能 14-16）

| # | 功能 | 演示路径 | 演示要点 |
|---|------|----------|----------|
| 14 | 统一持仓视图 | `/customer/positions` | 全部/自营/代销/在途四 Tab + 5 项汇总（总市值/浮动盈亏/分区小计），统一估值口径（份额×最新净值） |
| 15 | 交易/资金流水 | `/customer/flows` | 交易流水筛选 + **CSV 导出**（UTF-8 BOM 兼容 Excel）；资金流水含 balanceAfter 对账锚点 |
| 16 | 预约定投 | `/customer/plans` | 预约/定投（日/周/月）创建取消；@Scheduled 5s 扫描到期计划自动下单，复用全校验链，幂等不重复 |

### E. 服务与运营（功能 17 + 管理端）

| # | 功能 | 演示路径 | 演示要点 |
|---|------|----------|----------|
| 17 | AI 投教问答 | `/customer/ai-qa` | LLM 优先 + FAQ 降级 + 未命中转人工；敏感词自动建工单；答案强制带来源/风险声明/转人工标记 |
| — | 消息中心 | `/customer/messages` | 5 类消息筛选（NAV/DEAL/EXPIRY/ANNOUNCEMENT/TICKET_PROGRESS）+ 未读徽标 + 已读；净值发布/成交/工单进度自动推送 |
| — | 工单（C 端 + 管理端） | `/customer/tickets` + `/admin/tickets` | 代销投诉自动标记外同步 PENDING，关闭置 SYNCED 闭环，进度推消息 |
| — | 管理端统一订单列表 | `/admin` | 五条件筛选（类型/订单类型/状态/客户号/产品代码）+ **事件轨迹抽屉**（Timeline 状态迁移 + TA 标识/报文流水号） |
| — | 净值发布 | `/admin/nav-publish` | 仅自营可发布、每产品每估值日唯一（append-only）、发布即推送持有人 NAV 站内消息 |
| — | 健康矩阵 | `/admin/health` | DB / 本行 TA / 外部 TA 三组件状态 + 外部 TA 故障注入模式展示 + `/actuator/health` 标准端点 |

---

## 三、端到端主链路演示脚本（推荐 5 分钟版）

以 **陈四（13800000004）** 为主角：

1. **登录** `/customer/login`（13800000004 / Passw0rd!）
2. **浏览货架** `/customer/shelf` → 筛选自营 → 点开"现金宝1号"看详情与净值走势
3. **申购** 详情页 → 立即申购 → 输入 5 万 → 提交
   - 可穿插演示：换陈一买 R4 产品 → 弹风险不匹配二次确认；换陈二 → 测评过期拦截引导重测
4. **查订单** `/customer/orders` → 看状态机流转；对在途单演示**撤单**
5. **查持仓** `/customer/positions` → 市值/浮动盈亏变化，在途资产 Tab
6. **查流水** `/customer/flows` → 资金流水看 FREEZE/DEDUCT 与 balanceAfter；导出 CSV
7. **赎回** 持仓页 → 赎回部分份额 → 冻结份额变化；大额触发巨额赎回 PENDING_QUEUE → 10s 后自动确认
8. **切管理端** `/admin/login`（admin_op）→ 订单列表看全链路 → 点**事件轨迹**看 TA 报文流水
9. **净值发布** `/admin/nav-publish` → 发自营净值 → 切回 C 端消息中心看 NAV 推送
10. **AI 投教** `/customer/ai-qa` → 问"什么是理财产品净值"（LLM）→ 问冷门问题（FAQ）→ 问"我要投诉"（自动建工单）→ `/admin/tickets`（admin_cs）处理闭环

---

## 四、API 端点速查

### C 端（/api/customer）

| 域 | 端点 |
|----|------|
| 认证 | `POST /login` `POST /logout` `GET /me` |
| 账户准入 | `GET /account` `POST /account/sign` `POST /account/terminate` `POST /real-name/verify` `POST /documents/{type}/sign` |
| 风测 | `GET /assessment` `POST /assessment` `GET /suitability?productCode=` |
| 货架 | `GET /products` `GET /products/{id}` |
| 交易 | `POST /orders/purchase` `POST /orders/redeem` `POST /orders/{orderNo}/cancel` `GET /orders` `POST /dual-records/mock-upload` |
| 持仓流水 | `GET /positions` `GET /flows`（?format=csv） `GET /capital-flows` |
| 计划分红 | `POST/GET/DELETE /plans` `PUT /dividend-setting` `GET /dividend-settings` |
| 消息 | `GET /messages` `POST /messages/{id}/read` `POST /messages/read-all` |
| 工单 | `POST /tickets` `GET /tickets` |
| AI 投教 | `POST /ai-qa` |

### 管理端（/api/admin）

| 域 | 端点 |
|----|------|
| 认证 | `POST /login` `POST /logout` `GET /me` |
| 订单 | `GET /orders` `GET /orders/{orderNo}/events` |
| 净值 | `POST /nav/publish` |
| 产品 | `GET /products` |
| 工单 | `GET /tickets` `PUT /tickets/{ticketNo}/handle` |
| 健康 | `GET /health/matrix`（另有 `/actuator/health`） |

---

## 五、测试与质量证据

- **后端集成测试 95+ 项**，覆盖 12 个测试类：
  - 认证 / 账户准入 / 产品货架 / 适当性引擎 / 申购 / 赎回撤单 / 管理端订单 / 持仓流水 / 消息中心 / 工单 / AI 投教 / 预约定投分红
- 估值计算、适当性评分、订单状态机为 `domain/` 纯函数，不依赖 Spring 框架
- 一键验证脚本 `./scripts/verify.sh` 当前全绿（后端编译+测试 + 前端 lint/tsc/build）

## 六、架构决策记录（docs/decisions/）

| ADR | 决策 |
|-----|------|
| 0003 | 客户/管理端双身份体系 |
| 0004 | Session 认证（内存存储） |
| 0005 | 双 TA Mock 容器（本行同步 / 外部异步+故障注入） |
| 0006 | 简化账务模型（资金账户 + 幂等流水 + 持仓） |
| 0007 | MySQL 8 选型（本地开发用 H2） |

领域术语表见根目录 `CONTEXT.md`；技术设计见 `docs/design/technical-design.md`；需求规格见 `docs/specs/mainline-1-spec.md`。
