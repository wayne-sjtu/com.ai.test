import { Tag } from 'antd'

/** 风险等级 → 颜色（R1 低 ~ R5 高） */
const RISK_COLORS: Record<string, string> = {
  R1: 'green',
  R2: 'cyan',
  R3: 'blue',
  R4: 'orange',
  R5: 'red',
}

/** 订单状态 → 颜色 */
const ORDER_STATUS: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'default', label: '待处理' },
  TA_ACCEPTED: { color: 'processing', label: 'TA已受理' },
  CONFIRMED: { color: 'processing', label: '已确认' },
  SETTLED: { color: 'success', label: '已完成' },
  REDEEMED: { color: 'success', label: '赎回到账' },
  PENDING_QUEUE: { color: 'warning', label: '延期确认中' },
  CANCELLED: { color: 'default', label: '已撤销' },
  FAILED: { color: 'error', label: '失败' },
}

/** 订单类型 → 中文 */
export const ORDER_TYPE: Record<string, string> = {
  PURCHASE: '申购',
  REDEEM: '赎回',
}

/** 资金动作类型 → 中文 */
export const ACTION_TYPE: Record<string, string> = {
  FREEZE: '冻结',
  DEDUCT: '扣划',
  UNFREEZE: '解冻',
  RETURN: '回款',
}

/** 风险等级徽标 */
export function RiskBadge({ level }: { level: string }) {
  return <Tag color={RISK_COLORS[level] ?? 'default'}>{level}</Tag>
}

/** 产品类型徽标：自营 / 代销 */
export function TypeTag({ type }: { type: string }) {
  return type === 'PROPRIETARY' ? (
    <Tag color="geekblue">自营</Tag>
  ) : (
    <Tag color="purple">代销</Tag>
  )
}

/** 订单状态标签 */
export function OrderStatusTag({ status }: { status: string }) {
  const conf = ORDER_STATUS[status]
  return <Tag color={conf?.color ?? 'default'}>{conf?.label ?? status}</Tag>
}

/** 金额格式化（千分位） */
export function fmtAmount(value: number | null | undefined): string {
  if (value === null || value === undefined) return '-'
  return value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 净值格式化（4 位小数） */
export function fmtNav(value: number | null | undefined): string {
  if (value === null || value === undefined) return '-'
  return value.toFixed(4)
}
