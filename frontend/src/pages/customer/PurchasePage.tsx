import { useEffect, useState } from 'react'
import { Alert, Button, Card, Form, Input, InputNumber, Modal, Space, Spin, Typography, message } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { ApiRequestError, api } from '../../services/api'
import type { AccountResponse, ProductDetail, SuitabilityResult } from '../../services/types'
import { RiskBadge, TypeTag, fmtAmount } from '../../components/common/dicts'

const { Text } = Typography

/** 生成幂等键：每次进入表单生成一次，重复点击提交不重复下单 */
const newRequestId = () =>
  `WEB-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`

/** 申购页：适当性预检 → 提交 → 拦截链错误码分流引导 */
export function PurchasePage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const productCode = params.get('code') ?? ''

  const [detail, setDetail] = useState<ProductDetail | null>(null)
  const [account, setAccount] = useState<AccountResponse | null>(null)
  const [suitability, setSuitability] = useState<SuitabilityResult | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [clientRequestId, setClientRequestId] = useState(newRequestId())
  const [form] = Form.useForm<{ amount: number }>()

  useEffect(() => {
    if (!productCode) return
    setLoading(true)
    // 详情接口按 id 查询：先从货架列表定位产品
    api
      .get<{ products: Array<ProductDetail & { id: number }> }>(
        `/api/customer/products?keyword=${encodeURIComponent(productCode)}`,
      )
      .then(async (list) => {
        const hit = list.products.find((p) => p.productCode === productCode)
        if (!hit) {
          message.error('产品不存在')
          navigate('/customer/shelf')
          return
        }
        const d = await api.get<ProductDetail>(`/api/customer/products/${hit.id}`)
        setDetail(d)
        // 账户签约状态 + 适当性预检
        const [acc, suit] = await Promise.all([
          api.get<AccountResponse>('/api/customer/account'),
          api.get<SuitabilityResult>(`/api/customer/suitability?productCode=${productCode}`),
        ])
        setAccount(acc)
        setSuitability(suit)
      })
      .catch((e) => message.error(e instanceof Error ? e.message : '加载失败'))
      .finally(() => setLoading(false))
  }, [productCode, navigate])

  /** 提交申购：拦截链错误码分流（签约引导/测评引导/二次确认重提交） */
  const submit = async (confirmRisk: boolean) => {
    const amount = form.getFieldValue('amount') as number
    setSubmitting(true)
    try {
      await api.post('/api/customer/orders/purchase', {
        productCode,
        amount,
        confirmRisk,
        clientRequestId,
      })
      message.success('申购提交成功')
      navigate('/customer/orders')
    } catch (err) {
      const e = err as ApiRequestError
      switch (e.code) {
        case 'ACCOUNT_NOT_SIGNED':
        case 'PERMISSION_DENIED':
          Modal.confirm({
            title: '需要先开通财富账户',
            content: e.message,
            okText: '前往签约',
            onOk: () => navigate('/customer/profile'),
          })
          break
        case 'SUITABILITY_BLOCKED':
          Modal.error({
            title: '适当性校验未通过',
            content: (
              <div>
                <p>{e.message}</p>
                <p>请重新完成风险测评后再试。</p>
              </div>
            ),
            okText: '前往风险测评',
            onOk: () => navigate('/customer/profile'),
          })
          break
        case 'SUITABILITY_CONFIRM_REQUIRED':
          Modal.confirm({
            title: '风险不匹配确认',
            content: e.message,
            okText: '我已知晓风险，继续购买',
            cancelText: '取消',
            onOk: () => {
              // 二次确认是新业务动作：换新幂等键（旧键已绑定历史 FAILED 单）
              setClientRequestId(newRequestId())
              void submit(true)
            },
          })
          break
        case 'DUAL_RECORD_REQUIRED':
          Modal.info({
            title: '需要先完成双录',
            content: e.message,
            okText: '前往双录',
            onOk: () => navigate('/customer/profile'),
          })
          break
        default:
          message.error(e.message ?? '申购失败')
          // 其他失败（时段/额度/余额等）：换幂等键允许修改后重试
          setClientRequestId(newRequestId())
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 80 }}>
        <Spin size="large" />
      </div>
    )
  }
  if (!detail || !productCode) return null

  const unsigned = account?.status !== 'ACTIVE'
  const needConfirmHint = suitability?.action === 'CONFIRM'

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Space>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(-1)}>
          返回
        </Button>
      </Space>

      <Card
        title={
          <Space>
            <span style={{ fontSize: 18 }}>申购 · {detail.productName}</span>
            <TypeTag type={detail.productType} />
            <RiskBadge level={detail.riskLevel} />
          </Space>
        }
        style={{ maxWidth: 720 }}
      >
        {detail.consignmentRiskHint && (
          <Alert type="warning" showIcon title={detail.consignmentRiskHint} style={{ marginBottom: 16 }} />
        )}
        {unsigned && (
          <Alert
            type="error"
            showIcon
            title="尚未开通财富账户"
            description="请先前往账户中心完成开户签约，再进行申购。"
            action={
              <Button danger size="small" onClick={() => navigate('/customer/profile')}>
                去签约
              </Button>
            }
            style={{ marginBottom: 16 }}
          />
        )}
        {needConfirmHint && (
          <Alert
            type="warning"
            showIcon
            title="适当性提示：该产品风险等级高于您的测评等级"
            description={suitability.message}
            style={{ marginBottom: 16 }}
          />
        )}

        <Form form={form} layout="vertical" onFinish={() => void submit(false)}>
          <Form.Item label="产品代码">
            <Input value={productCode} disabled />
          </Form.Item>
          <Form.Item label="起购金额（元）">
            <Text>{fmtAmount(detail.minPurchaseAmount)}</Text>
          </Form.Item>
          <Form.Item
            name="amount"
            label="申购金额（元）"
            rules={[
              { required: true, message: '请输入申购金额' },
              {
                validator: (_, v: number) =>
                  v >= detail.minPurchaseAmount
                    ? Promise.resolve()
                    : Promise.reject(new Error(`不能低于起购金额 ${fmtAmount(detail.minPurchaseAmount)} 元`)),
              },
            ]}
          >
            <InputNumber
              style={{ width: '100%' }}
              min={detail.minPurchaseAmount}
              step={1000}
              precision={2}
              placeholder={`≥ ${fmtAmount(detail.minPurchaseAmount)} 元`}
            />
          </Form.Item>
          <Text type="secondary">
            申购费率 {(detail.purchaseFeeRate * 100).toFixed(2)}% · 交易时段 {detail.tradeStartTime} ~{' '}
            {detail.tradeEndTime} · 理财非存款，产品有风险，投资须谨慎
          </Text>
          <Button
            type="primary"
            htmlType="submit"
            size="large"
            block
            loading={submitting}
            disabled={unsigned}
            style={{ marginTop: 16 }}
          >
            提交申购
          </Button>
        </Form>
      </Card>
    </Space>
  )
}
