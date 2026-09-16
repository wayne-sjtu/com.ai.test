import { useEffect, useState } from 'react'
import { Alert, Button, Card, Descriptions, Form, InputNumber, Space, Spin, Typography, message } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { ApiRequestError, api } from '../../services/api'
import type { PositionViewResponse } from '../../services/types'
import { RiskBadge, TypeTag, fmtAmount, fmtNav } from '../../components/common/dicts'

const { Text } = Typography

/** 赎回页：从持仓取可赎份额上限，按份额赎回（回款 = 份额 × 最新净值 − 赎回费） */
export function RedeemPage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const productCode = params.get('code') ?? ''

  const [position, setPosition] = useState<PositionViewResponse['positions'][number] | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  // 稳定幂等键：进入页面生成一次，重复点击/响应丢失重试不重复下单
  const [clientRequestId, setClientRequestId] = useState(
    `WEB-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
  )
  const [form] = Form.useForm<{ shares: number }>()

  useEffect(() => {
    if (!productCode) return
    setLoading(true)
    api
      .get<PositionViewResponse>('/api/customer/positions')
      .then((view) => {
        const hit = view.positions.find((p) => p.productCode === productCode)
        if (!hit) {
          message.error('未找到该产品持仓')
          navigate('/customer/positions')
          return
        }
        setPosition(hit)
      })
      .catch((e) => message.error(e instanceof Error ? e.message : '加载失败'))
      .finally(() => setLoading(false))
  }, [productCode, navigate])

  const submit = async (values: { shares: number }) => {
    setSubmitting(true)
    try {
      const order = await api.post<{ status: string; orderNo: string }>('/api/customer/orders/redeem', {
        productCode,
        shares: values.shares,
        clientRequestId,
      })
      if (order.status === 'PENDING_QUEUE') {
        message.warning('已触发巨额赎回，订单进入延期确认队列（T+1 处理）')
      } else {
        message.success('赎回提交成功')
      }
      navigate('/customer/orders')
    } catch (err) {
      const e = err as ApiRequestError
      message.error(e.message ?? '赎回失败')
      // 失败后换幂等键，允许修改后重试
      setClientRequestId(`WEB-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`)
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
  if (!position || !productCode) return null

  const available = position.availableShares

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
            <span style={{ fontSize: 18 }}>赎回 · {position.productName}</span>
            <TypeTag type={position.productType} />
            <RiskBadge level={position.riskLevel} />
          </Space>
        }
        style={{ maxWidth: 720 }}
      >
        <Descriptions bordered column={1} size="small" style={{ marginBottom: 24 }}>
          <Descriptions.Item label="持有份额">{fmtAmount(position.shares)}</Descriptions.Item>
          <Descriptions.Item label="冻结份额">{fmtAmount(position.frozenShares)}</Descriptions.Item>
          <Descriptions.Item label="可赎份额">
            <Text strong style={{ color: '#cf1322' }}>
              {fmtAmount(available)}
            </Text>
          </Descriptions.Item>
          <Descriptions.Item label="最新净值">
            {fmtNav(position.latestNav)}
            {position.navSource === 'EXTERNAL' && (
              <Text type="secondary">（外部 TA 同步）</Text>
            )}
          </Descriptions.Item>
        </Descriptions>

        {available <= 0 && (
          <Alert
            type="warning"
            showIcon
            title="无可赎份额"
            description="您在该产品的份额均被冻结（如巨额赎回延期中），暂不能发起赎回。"
            style={{ marginBottom: 16 }}
          />
        )}

        <Form form={form} layout="vertical" onFinish={submit}>
          <Form.Item
            name="shares"
            label="赎回份额"
            rules={[
              { required: true, message: '请输入赎回份额' },
              {
                validator: (_, v: number) =>
                  v > 0 && v <= available
                    ? Promise.resolve()
                    : Promise.reject(new Error(`份额需大于 0 且不超过可赎份额 ${fmtAmount(available)}`)),
              },
            ]}
          >
            <InputNumber
              style={{ width: '100%' }}
              min={0.01}
              max={available}
              step={100}
              precision={2}
              placeholder={`≤ ${fmtAmount(available)} 份`}
            />
          </Form.Item>
          <Text type="secondary">
            回款金额 = 赎回份额 × 最新净值 − 赎回费；当日赎回份额超过产品总额度阈值时触发巨额赎回，整单延期确认。
          </Text>
          <Button
            type="primary"
            htmlType="submit"
            size="large"
            block
            loading={submitting}
            disabled={submitting || available <= 0}
            style={{ marginTop: 16 }}
          >
            提交赎回
          </Button>
        </Form>
      </Card>
    </Space>
  )
}
