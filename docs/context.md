# 项目上下文（按需加载）

> Agent 接手任务前了解项目背景时读取。修改重大事项后请同步更新本文件。

## 项目是什么

**理财产品销售平台** —— "AI 黑客松"PK 赛参赛作品（48 小时赛制，2026-09-12 至 09-14）。银行零售财富业务平台，统一承载自营理财与代销产品销售：

- 命题 PDF：`/Users/chenwei/Downloads/AI黑客松大赛命题_理财产品销售平台.pdf`
- 后端：`backend/` — Spring Boot 3.3.5，端口 8080
- 前端：`frontend/` — React 19 + Vite 8，C 端与管理端同工程双路由分区

**当前范围**：主线一（客户与渠道服务）全部 17 个功能 + 端到端核心交易主链路（客户准入→浏览→适当性校验→申购→资金处理→TA 确认→持仓更新→赎回→回款）。主线二/三/四仅实现主线一强依赖的部分。

**关键决策**：见 `docs/decisions/`（ADR-0003 两套身份体系、0004 Session 认证、0005 双 TA Mock 容器、0006 简化账务模型）。领域术语见根目录 `CONTEXT.md`。

## 当前状态

- [x] 后端/前端骨架搭建完成
- [x] Agent 工程化目录（AGENTS.md / skills / docs / templates）就绪
- [x] 主线一需求拷问与领域建模（四轮决策全部完成，见 ADR-0003~0007）
- [x] 主线一 spec 已发布：`docs/specs/mainline-1-spec.md`（ready-for-agent）
- [x] 主线一技术设计文档已产出：`docs/design/technical-design.md`（12 章节 + 本地启动验证记录）
- [x] Phase 1 local Profile 已落地：H2（MODE=MySQL）数据源 + TaClient 接口抽象 + 进程内双 TA Mock（本行同步确认 / 外部异步回调+四种故障注入），8 项集成测试全通过（代码：`backend/.../taclient/`）
- [x] 数据层已落地：21 个 JPA 实体（`backend/.../repository/entity/`）+ schema.sql（21 表 H2/MySQL 兼容 DDL）+ data.sql 种子数据（6 客户覆盖适当性三分支/过期/未签约、8 产品、240 条净值、5 存量持仓、2 在途订单、资金账户/流水、2 管理端账号），SeedDataTest 9 项断言全通过
- [x] 认证域已落地：双身份 Session 认证（`CustomerAuthController` / `AdminAuthController`：login/logout/me × 2）+ `AuthInterceptor` 双身份守卫（未登录 401 / 越权 403）+ 渠道校验（停用渠道拒绝登录）+ 统一错误 `{code, message}`，12 项集成测试通过（演示账号：客户 `1380000000x`/`Passw0rd!`，管理端 `admin_op`/`admin_cs`/`Admin123!`）
- [x] 账户与准入域已落地：`/api/customer/account`（签约状态/签约/解约三重校验+原因列表）、`/api/customer/assessment`（append-only 测评，C1-C5 评分规则在 `domain/RiskLevelRule`，有效期 1 年+过期标志）、`/api/customer/documents/{type}/sign`（四类文件签收幂等）、`/api/customer/real-name/verify`（Mock 实名），9 项集成测试通过（`AccountAdmissionIntegrationTest`，@Transactional 回滚不污染种子）
- [x] 产品货架域已落地：`GET /api/customer/products`（统一货架，筛选参数 productType/riskLevel/category/issuer/keyword/maxTermDays/maxMinAmount，列表项含最新净值）+ `GET /api/customer/products/{id}`（要素/费率/剩余额度/交易时段/30 日净值走势/公告；代销强制带风险提示与 EXTERNAL 来源+同步时间标注），9 项集成测试通过（`ProductShelfIntegrationTest`）
- [x] 适当性引擎已落地（独立 service，设计文档第 7 章）：`SuitabilityService.check()` 校验链 = 测评存在性 → 有效期（过期强制重测）→ 产品开放状态 → C×R 数据驱动映射（`suitability_rule` 表 25 行种子，PASS/CONFIRM/BLOCK）；CONFIRM 档 `confirmRisk=true` 二次确认后放行；每次校验写 `suitability_log` 留痕（含 confirmed 标志与 orderNo 关联）；结果值对象 `domain/SuitabilityResult`（passed/needConfirm/message），11 项测试通过（`SuitabilityServiceTest`）
- [x] 适当性预检端点 + 演示页已落地：`GET /api/customer/suitability?productCode=`（引擎真实调用并留痕）；前端 `pages/SuitabilityDemo.tsx`（登录选账号 → 产品列表 → 三分支校验演示），前端 lint/build 全绿
- [x] 交易主链路·申购已落地：`POST /api/customer/orders/purchase` 九步校验链（签约/权限/产品状态/时段/起购/适当性/双录/额度行锁/资金冻结）→ 订单状态机（`domain/OrderStateMachine`，只进不退+事件轨迹）→ TA 路由（自营本行 TA 同步 SETTLED；代销外部 TA 受理 + `ExternalTaConfirmService` 异步回调幂等结算）→ 资金冻结/扣划（`CapitalService` 幂等流水）+ 持仓入账；失败单留痕（FAILED + 轨迹，400 返回 orderNo）；`clientRequestId` 幂等；`POST /api/customer/dual-records/mock-upload`（双录 Mock）；`GET /api/customer/orders`；13 项集成测试通过（`OrderPurchaseIntegrationTest`）
- [x] 交易主链路·赎回与撤单已落地：`POST /api/customer/orders/redeem`（校验链：签约/权限/产品状态/时段/持仓可用份额 → 冻结份额 → TA 路由）；回款 = 份额 × 最新净值 − 赎回费（真实净值数据）；巨额赎回判定（单日累计赎回份额 > 产品总份额 × `redeem_threshold_rate` 默认 10%）→ 整单 PENDING_QUEUE，`PendingQueueConfirmJob` @Scheduled 延期确认（`trade.pending-queue-delay-seconds` 默认 10s 模拟 T+1）；`POST /api/customer/orders/{orderNo}/cancel`（可撤时段 = 产品交易时段 + TA 确认前，申购单解冻资金/释放额度，赎回单解冻份额，与 TA 回调状态机守卫互斥）；持仓新增 `frozen_shares` 字段；外部 TA 回调按订单类型分派结算；10 项集成测试通过（`OrderRedeemCancelIntegrationTest`）；端到端冒烟实测：赎回 5000 份即时 REDEEMED（回款按真实净值）、巨额 20000 份 PENDING_QUEUE → 调度器 10s 后自动 REDEEMED、种子在途单撤单成功
- [x] 管理端订单域已落地（spec 用户故事 32/33/34）：`GET /api/admin/orders`（统一订单列表，productType/orderType/status/customerNo/productCode 任意组合筛选，Specification 动态查询，含客户号与 TA 路由流水）；`GET /api/admin/orders/{orderNo}/events`（事件轨迹，TA 标识 + 报文流水号）；`POST /api/admin/nav/publish`（自营净值发布：仅自营可发布/代销禁止、每产品每估值日唯一 append-only、发布后向持有人推送 NAV 站内消息）；`GET /api/admin/health/matrix`（组件矩阵 DB/INTA/EXTA，外部 TA 附带故障模式）+ `/actuator/health`（actuator 依赖已加）；`TaClient.ping()` 契约扩展；9 项集成测试通过（`AdminOrderIntegrationTest`）
- [x] C 端持仓与流水已落地（spec 用户故事 35/36/37）：`GET /api/customer/positions`（统一持仓视图 = 存量持仓【自营/代销分区展示，统一估值口径：份额×最新净值】+ 在途资产 + 分区小计汇总【总市值/总成本/总浮动盈亏/自营市值/代销市值】；代销净值带 EXTERNAL 来源 + navSyncedAt 同步时间标注，自营 INTERNAL；估值纯函数在 `domain/ValuationCalculator`）；`GET /api/customer/flows`（交易流水，productType/orderType/status 任意组合筛选，`format=csv` 导出 UTF-8 BOM 兼容 Excel）；`GET /api/customer/capital-flows`（资金流水，含 balanceAfter 对账锚点）；8 项集成测试通过（`PositionFlowIntegrationTest`）
- [x] C 端消息中心已落地（spec 用户故事 28）：`GET /api/customer/messages`（站内信列表，msgType 筛选【NAV/DEAL/EXPIRY/ANNOUNCEMENT/TICKET_PROGRESS】+ unread 只看未读 + unreadCount 未读数；消息来源已接入：净值发布推送/成交受理通知/到期提醒/公告种子）；`POST /api/customer/messages/{id}/read`（单条已读，非本人消息统一 404 不泄露存在性）；`POST /api/customer/messages/read-all`（全部已读返回标记条数）；6 项集成测试通过（`MessageCenterIntegrationTest`）
- [x] 工单域已落地（spec 用户故事 29/31）：`POST /api/customer/tickets`（提交咨询/投诉，jakarta 校验；代销产品投诉自动标记 `externalSyncStatus=PENDING` 同步发行机构，自营/咨询内部流转）；`GET /api/customer/tickets`（本人工单列表含处理状态/答复/产品类型标注）；`GET /api/admin/tickets`（status/ticketType 组合筛选）；`PUT /api/admin/tickets/{ticketNo}/handle`（ACCEPT 受理→PROCESSING / CLOSE 关闭→CLOSED 答复必填；代销投诉关闭置 SYNCED 完成发行机构闭环；handler 取管理端 Session；受理与关闭均推送客户 TICKET_PROGRESS 站内消息，与消息中心联动）；TICKET_NOT_FOUND 纳入 404 映射；13 项集成测试通过（`TicketIntegrationTest`）
- [x] AI 投教问答已落地（spec 用户故事 30，功能 17）：`POST /api/customer/ai-qa`（LLM 优先 + FAQ 关键词降级）；`llm/LlmClient` 接口抽象 + `InProcessLlmClient` 进程内 Mock（投教主题模板回答 + `setAvailable` 故障开关，降级路径同等可演示；`ai.llm.provider` 条件装配，Phase 2 可换真实 LLM API 零改业务代码）；回答链：敏感词检测（投诉/赔偿/被骗等）→ AI 不作答直接转人工 + 自动创建 CONSULT 工单（`[AI 投教转人工]` 前缀保留问答记录，与工单域联动）→ LLM 主题命中（source=LLM）→ FAQ 关键词匹配降级（source=FAQ，命中最多关键词优先）→ 均未命中建议转人工（source=NONE）；所有回答强制附带 riskDisclaimer 风险声明与 needHuman 转人工标记；6 项集成测试通过（`AiQaIntegrationTest`）
- [x] 预约定投 + 分红方式已落地（spec 用户故事 22/23，功能 12 分级收官）：`POST/GET/DELETE /api/customer/plans`（RESERVE 预约申购 triggerDate 必填 / REGULAR_INVEST 定投 periodType DAY/WEEK/MONTH 必填；触发日不得早于今日；取消仅 ACTIVE，他人计划 404）；`InvestPlanTriggerJob` @Scheduled 5s 扫描到期计划 → 复用 `OrderService.purchase` 全校验链自动下单（clientRequestId=计划号+触发日幂等，Job 重扫不重复下单）；预约触发成功 FINISHED / 失败（适当性/资金等）EXPIRED 作废（订单 FAILED 留痕）；定投触发后推进 nextTriggerDate（DAY+1/WEEK+7/MONTH+1月），失败下期再试；`PUT /api/customer/dividend-setting`（CASH/REINVEST，**走 TA 报文真实实现**：`TaClient.setDividendType(DividendCommand)` 契约扩展，自营本行 TA 同步 CONFIRMED、代销外部 TA 受理 ACCEPTED + 故障注入兼容，受理成功后落库可覆盖更新）+ `GET /api/customer/dividend-settings`；PLAN_NOT_FOUND 纳入 404 映射；11 项集成测试通过（`InvestPlanDividendIntegrationTest`）
- [x] 前端骨架已落地（AntD 5 + react-router-dom 7，骨架先行+逐页填充）：`/customer` 与 `/admin` 双路由分区独立守卫（挂载 GET /me 校验 Session，401 携带来源路径跳转对应登录页）与布局（C 端顶部导航+未读消息 30s 轮询徽标 / 管理端侧边导航）；`services/api.ts` 扩展 put/delete/download(blob) + onUnauthorized 统一 401 跳转；`services/types.ts` 逐字段对齐后端 DTO；19 页骨架就绪（双登录页真实可用：13800000001/Passw0rd!、admin/admin123!），其余页面为标注接口的占位；`SuitabilityDemo` 演示页已删除；lint/tsc/build 全绿
- [x] 前端第一批 C 端交易链页面已填充：货架（筛选：类型/风险/关键词 + 卡片列表 + 代销风险提示 Alert）、产品详情（要素 Descriptions + 零依赖 SVG 净值走势 + 公告 + 申购/赎回入口）、申购（挂载即预检账户签约+适当性，拦截链错误码分流引导：ACCOUNT_NOT_SIGNED/PERMISSION_DENIED→签约、SUITABILITY_BLOCKED→重测、SUITABILITY_CONFIRM_REQUIRED→风险不匹配二次确认弹窗重提交 confirmRisk=true、DUAL_RECORD_REQUIRED→双录；clientRequestId 幂等，失败换键重试）、赎回（持仓可赎份额上限校验 + PENDING_QUEUE 巨额赎回提示）、我的订单（状态机标签 + 在途可撤 Popconfirm 撤单 + ORDER_NOT_CANCELLABLE 提示）；公共组件 dicts.tsx（RiskBadge/TypeTag/OrderStatusTag/金额净值格式化）；后端小增强：货架 keyword 同时匹配产品代码（`ProductRepository.search`）；lint/tsc/build 全绿，后端全量测试通过
- [x] 前端第二批 C 端查询类页面已填充（C 端 14 页全部完成）：首页（资产总览汇总卡片 + 持仓Top5 + 在途 + 快捷入口）、持仓（4 汇总Statistic + 全部/自营/代销/在途四Tab，代销净值"外部"标注 + 赎回入口）、流水（交易流水Tab：productType/orderType/status筛选 + CSV blob导出；资金流水Tab：FREEZE/DEDUCT/UNFREEZE/RETURN色标 + balanceAfter对账锚点）、投资计划（预约定投CRUD Modal：类型联动周期必填；分红方式Tab：产品下拉 + CASH/REINVEST 走TA报文回执展示）、消息中心（Segmented类型筛选 + 未读徽标 + 单条/全部已读）、工单（提交Modal：投诉联动产品必选；列表含状态/外部同步/答复查看）、AI投教（聊天式问答 + 建议问题Tag + 来源标签【LLM/FAQ/HUMAN/NONE】+ 风险声明 + 工单号回显）、账户中心（基本信息/Mock实名/财富账户签约销户权限展示/风险测评5题问卷弹窗）；read-al返回 markedRead 对齐；lint/tsc/build 全绿
- [x] 前端管理端 4 页已填充（前端 19 页全部完成）：统一订单列表（productType/orderType/status/customerNo/productCode 五条件筛选 + 事件轨迹抽屉：Timeline 状态迁移 + TA 标识/报文流水号）、净值发布（自营产品下拉 + 估值日不可选未来 + 发布成功 Result 展示，附当前自营净值表）、工单处理（status/ticketType 筛选 + 受理/关闭 Modal：CLOSE 联动答复必填，代销投诉 SYNCED 提示）、健康矩阵（DB/本行 TA/外部 TA 三卡片 Badge + 外部 TA 故障模式 Tag 说明 + Actuator 链接）；配套后端小增强：新增 `GET /api/admin/products`（管理端独立产品端点，双身份隔离下不能复用 /api/customer/**）、`TicketResponse` 补 customerNo 字段；修正 AdminMe 为 displayName、管理端种子账号提示 admin_op/admin_cs（密码 Admin123!）；lint 0 错误 + tsc/build 全绿 + 后端全量测试通过；冒烟验证：管理端登录/订单筛选/健康矩阵链路通，/api/admin/products 需重启后端后生效
- [x] 交付方式确认：**不做 Docker-Compose，全部本地 Mock 启动**（用户决策 2026-09-16）：后端 `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local`（local Profile = H2 内存库 + 种子数据 + 进程内本行/外部双 TA Mock + 进程内 LLM Mock），前端 `cd frontend && npm run dev`（Vite 代理 /api → 8080）；重启后 /api/admin/products 与工单 customerNo 已生效；冒烟通过：管理端登录(admin_op/Admin123!)/产品端点/工单 customerNo、C 端登录/持仓(演示账号 13800000004 陈四：2 持仓 + 1 在途 + 总市值 80929)/AI 投教(LLM 命中)；演示账号速查——客户 13800000001~000005 密码均为 Passw0rd!（陈四有持仓+在途、陈五有持仓含冻结份额），管理端 admin_op/admin_cs 密码均为 Admin123!
- [x] 交付文档已就位：`docs/manual/demo-guide.md`（17 项功能清单 × 演示路径、启动方式、演示账号、5 分钟端到端演示脚本、API 端点速查、测试证据、ADR 索引）；全部端点路径已逐一与控制器注解核对无误
- [x] 代码审查与修复已完成（2026-09-16，Critical 4 项 + Major 7 项全部修复，verify.sh 全绿）：**并发安全**——资金账户 freeze/deduct/unfreeze/returnCash 悲观行锁 + 事务（防丢失更新）；赎回冻结份额悲观锁内复核可用份额（防超卖 TOCTOU）；**幂等语义**——历史 FAILED 单幂等命中返回失败（要求换 clientRequestId），并发唯一约束冲突/乐观锁冲突全局兜底 409 CONCURRENT_CONFLICT；前端申购二次确认换新幂等键、赎回页稳定幂等键 + 提交中禁用；**正确性**——申购校验链资金账户检查前移（防额度泄漏）、赎回确认净值缺失硬失败（NAV_NOT_FOUND 不再回退 1）、签约时幂等补建资金账户；**批量调度**——triggerDuePlans 移除外层事务（单计划失败不 rollback-only 污染整批）；**安全**——登录后重建会话（防 Session 固定）；**性能**——持仓视图批量查询消 N+1、工单筛选条件下推、风险测评 findTop1、净值发布按产品查持有人；**前端**——api.ts 非 JSON/网络错误安全解析 + 401 先行触发 + Blob 下载 Firefox 兼容、守卫仅 401 跳登录（网络错误不误跳）+ 重命名 `auth/guards.tsx`、401 统一跳转保留回跳路径。遗留演进项：MessageController/FlowController 业务下沉 service、额度占用/释放口径统一（usedQuota 语义需业务确认）、单号生成随机空间扩大

## 已确认的实现策略（拷问结论速查）

- 运行时策略（分阶段）：Phase 1 local Profile 本地 Mock 优先（H2 MySQL 方言 + 进程内 TaClient Mock Bean，`spring-boot:run` 零外部依赖）；Phase 2 compose Profile 四容器交付，业务代码零改动（详见设计文档 1.2 节）
- 数据库：MySQL 8（ADR-0007，交付态），本地开发用 H2 Mock
- 认证：Session（内存存储）；客户/管理端两套登录端点
- 估值：实时计算（份额 × 最新净值），自营/代销同口径，仅来源标注不同
- 功能 12 分级：撤单 + 分红方式全真实；预约申购/定投为计划表 + @Scheduled 到期触发
- AI 投教（功能 17）：LLM 优先 + FAQ 降级；答案必须带来源提示 + 风险声明 + 人工转接
- 申购校验最小集：产品总额度（占用/释放）+ 交易时段 + 起购金额 + 固定费率
- 订单状态机：CREATED → SUBMITTED → TA_ACCEPTED → CONFIRMED → SETTLED/REDEEMED；异常终态 CANCELLED/FAILED/EXPIRED；预留 REVERSED；每次迁移写 order_event
- 最小双录：代销 + R4/R5 触发双录判定，未完成拦截交易；双录本体 Mock 为标志文件上传
- 巨额赎回：产品级阈值（默认 10%），超限部分延期确认（PENDING_QUEUE，T+1 顺延）
- 健康检查：Actuator /actuator/health（compose 用）+ 自定义组件矩阵端点（DB/本行 TA/外部 TA）
- 交付文档结构：docs/ 下 requirements / design / manual / test-report / process / decisions / reference

## 重要约束

- **赛题红线**：任一身份/账户/测评/签署节点失败均终止交易；自营与代销在展示、订单、份额、资金链路上必须可识别、可追溯
- **技术栈**：后端 Java/Python 均可（已定 Java），前端不限（已定 React）；数据库/中间件自选但须在文档声明
- **外部依赖全 Mock**：外部 TA、核心账务、支付、监管报送等均需 Mock，Mock 需保证业务逻辑完整可跑通主链路
- **交付要求**：Docker-Compose 一键部署 + 健康检查接口 + 种子数据；文档结构化（MD/JSON）置于 docs/
- **评分结构**：作品分 40 + AI 工程能力 30（过程证据：Git 历史/会话轨迹/规约/评测脚本）+ 团队现场 30

## 关键路径速查

| 内容 | 位置 |
|------|------|
| 后端入口 | `backend/src/main/java/com/cib/ai/test/CibAiTestApplication.java` |
| 现有接口 | `backend/src/main/java/com/cib/ai/test/controller/HelloController.java` |
| 后端配置 | `backend/src/main/resources/application.yml` |
| 前端入口 | `frontend/src/main.tsx` / `frontend/src/App.tsx` |
| Vite 配置 | `frontend/vite.config.ts` |
| 领域词汇表 | `CONTEXT.md` |
| 架构决策 | `docs/decisions/` |
