/**
 * 后端 DTO 类型定义：逐字段对齐 backend model/ 下 Java record。
 * 命名保持与后端 JSON 序列化一致（camelCase）。
 */

// ============ 认证与账户 ============

/** GET /api/customer/me、POST /api/customer/login */
export interface CustomerMe {
  customerNo: string
  name: string
  mobile: string
  realNameStatus: string
  kycStatus: string
  channelCode: string
}

/** GET /api/customer/account */
export interface AccountResponse {
  accountNo: string | null
  status: string
  signedAt: string | null
  permissions: Array<{ type: string; status: string }>
}

/** 管理端登录响应（GET /api/admin/me、POST /api/admin/login） */
export interface AdminMe {
  username: string
  displayName: string
  role: string
}

// ============ 产品货架 ============

/** GET /api/customer/products 列表项 */
export interface ProductSummary {
  id: number
  productCode: string
  productName: string
  productType: 'PROPRIETARY' | 'CONSIGNMENT'
  issuerName: string
  riskLevel: string
  category: string
  termDays: number
  minPurchaseAmount: number
  expectedReturn: string
  latestNav: number | null
  latestNavDate: string | null
  status: string
  /** 代销产品风险提示文案 */
  consignmentRiskHint: string | null
}

export interface ProductListResponse {
  total: number
  products: ProductSummary[]
}

/** GET /api/customer/products/{id} */
export interface ProductDetail extends ProductSummary {
  purchaseFeeRate: number
  redemptionFeeRate: number
  remainingQuota: number
  tradeStartTime: string
  tradeEndTime: string
  navSource: { source: string; syncedAt: string | null; note: string } | null
  navTrend: Array<{ date: string; nav: number }>
  announcements: Array<{ date: string; title: string }>
}

// ============ 订单 ============

/** POST /api/customer/orders/purchase、redeem；GET /api/customer/orders */
export interface OrderResponse {
  orderNo: string
  productCode: string
  productType: string
  orderType: string
  amount: number
  shares: number
  fee: number
  status: string
  confirmRisk: boolean
  taSerialNo: string
  createdAt: string
  updatedAt: string
}

/** 管理端订单行（GET /api/admin/orders，含客户号与幂等键） */
export interface AdminOrderRow {
  orderNo: string
  clientRequestId: string | null
  customerNo: string
  productCode: string
  productType: string
  orderType: string
  amount: number
  shares: number
  fee: number
  status: string
  taSerialNo: string | null
  createdAt: string
  updatedAt: string
}

/** GET /api/admin/orders/{orderNo}/events */
export interface OrderEvent {
  id: number
  orderNo: string
  eventType: string
  fromStatus: string | null
  toStatus: string
  taTag: string | null
  taSerialNo: string | null
  detail: string
  createdAt: string
}

// ============ 持仓与流水 ============

/** GET /api/customer/positions */
export interface PositionViewResponse {
  positions: Array<{
    productCode: string
    productName: string
    productType: string
    riskLevel: string
    category: string
    shares: number
    frozenShares: number
    availableShares: number
    latestNav: number
    navSource: string
    navSyncedAt: string | null
    costAmount: number
    marketValue: number
    floatingPnl: number
    returnRatePercent: number
  }>
  inFlights: Array<{
    orderNo: string
    productCode: string
    productName: string
    productType: string
    orderType: string
    status: string
    amount: number
    shares: number
  }>
  summary: {
    totalMarketValue: number
    totalCost: number
    totalFloatingPnl: number
    proprietaryMarketValue: number
    consignmentMarketValue: number
  }
}

/** 交易流水（GET /api/customer/flows） */
export interface TradeFlowItem {
  orderNo: string
  productCode: string
  productType: string
  orderType: string
  amount: number
  shares: number
  fee: number
  status: string
  createdAt: string
}

/** 资金流水（GET /api/customer/capital-flows） */
export interface CapitalFlowItem {
  id: number
  orderNo: string
  actionType: string
  amount: number
  balanceAfter: number
  createdAt: string
}

// ============ 投资计划与分红 ============

/** POST/GET /api/customer/plans */
export interface InvestPlan {
  planNo: string
  productCode: string
  planType: 'RESERVE' | 'REGULAR_INVEST'
  amount: number
  triggerDate: string
  periodType: string | null
  nextTriggerDate: string | null
  status: string
  createdAt: string
}

/** 分红方式设置 */
export interface DividendSetting {
  productCode: string
  dividendType: 'CASH' | 'REINVEST'
  updatedAt: string
}

export interface DividendSettingResult {
  productCode: string
  productType: string
  dividendType: string
  taSerialNo: string
  taMessage: string
}

// ============ 消息中心 ============

/** GET /api/customer/messages */
export interface MessageItem {
  id: number
  msgType: 'NAV' | 'DEAL' | 'EXPIRY' | 'ANNOUNCEMENT' | 'TICKET_PROGRESS'
  title: string
  content: string
  readFlag: boolean
  createdAt: string
}

export interface MessageListResponse {
  total: number
  unreadCount: number
  messages: MessageItem[]
}

// ============ 工单 ============

export interface Ticket {
  ticketNo: string
  customerNo: string
  ticketType: 'CONSULT' | 'COMPLAINT'
  productCode: string | null
  productType: string | null
  content: string
  status: 'OPEN' | 'PROCESSING' | 'CLOSED'
  externalSyncStatus: string | null
  handler: string | null
  reply: string | null
  createdAt: string
  updatedAt: string
}

export interface TicketListResponse {
  total: number
  tickets: Ticket[]
}

// ============ AI 投教 ============

/** POST /api/customer/ai-qa */
export interface AiQaResponse {
  answer: string
  source: 'LLM' | 'FAQ' | 'HUMAN' | 'NONE'
  riskDisclaimer: string
  needHuman: boolean
  ticketNo: string | null
  faqQuestion: string | null
}

// ============ 适当性与风测 ============

/** GET /api/customer/suitability?productCode= */
export interface SuitabilityResult {
  action: 'PASS' | 'CONFIRM' | 'BLOCK'
  passed: boolean
  needConfirm: boolean
  message: string | null
}

// ============ 管理端健康 ============

/** GET /api/admin/health/matrix */
export interface HealthMatrix {
  overall: 'UP' | 'DOWN'
  checkedAt: string
  components: Array<{
    name: string
    label: string
    status: 'UP' | 'DOWN'
    detail: string
    /** 外部 TA 特有：当前故障注入模式 */
    faultMode?: string
  }>
}

// ============ 管理端渠道维护（spec 功能 2） ============

/** GET /api/admin/channels 列表项 */
export interface ChannelItem {
  channelCode: string
  channelName: string
  channelType: string
  coreFlag: boolean
  status: 'ACTIVE' | 'SUSPENDED'
  createdAt: string
  customerCount: number
}

export interface ChannelListResponse {
  channels: ChannelItem[]
}

/** POST /api/admin/nav/publish 响应 */
export interface NavPublishResult {
  productCode: string
  navDate: string
  nav: number
  source: string
  publishedAt: string
}
