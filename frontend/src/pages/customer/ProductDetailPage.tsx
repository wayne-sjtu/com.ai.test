import { useEffect, useMemo, useState } from 'react'
import { Alert, Button, Card, Col, Descriptions, Listy, Row, Space, Spin, Tag, Typography, message } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../../services/api'
import type { ProductDetail } from '../../services/types'
import { RiskBadge, TypeTag, fmtAmount, fmtNav } from '../../components/common/dicts'

const { Text, Title } = Typography

/** 净值走势 SVG 折线（零依赖，演示足够） */
function NavTrend({ points }: { points: Array<{ date: string; nav: number }> }) {
  const { path, min, max } = useMemo(() => {
    if (points.length < 2) return { path: '', min: 0, max: 0 }
    const navs = points.map((p) => p.nav)
    const lo = Math.min(...navs)
    const hi = Math.max(...navs)
    const span = hi - lo || 1
    const w = 700
    const h = 180
    const d = points
      .map((p, i) => {
        const x = (i / (points.length - 1)) * w
        const y = h - ((p.nav - lo) / span) * (h - 20) - 10
        return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`
      })
      .join(' ')
    return { path: d, min: lo, max: hi }
  }, [points])

  if (!path) return <Text type="secondary">净值数据不足</Text>
  return (
    <div>
      <svg viewBox="0 0 700 180" style={{ width: '100%', height: 180 }}>
        <path d={path} fill="none" stroke="#1677ff" strokeWidth={2} />
      </svg>
      <Text type="secondary">
        区间：{fmtNav(min)} ~ {fmtNav(max)}（{points.length} 个估值日）
      </Text>
    </div>
  )
}

/** 产品详情：要素 + 净值走势 + 公告 + 申购/赎回入口 */
export function ProductDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [detail, setDetail] = useState<ProductDetail | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!id) return
    setLoading(true)
    api
      .get<ProductDetail>(`/api/customer/products/${id}`)
      .then(setDetail)
      .catch((e) => message.error(e instanceof Error ? e.message : '加载失败'))
      .finally(() => setLoading(false))
  }, [id])

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 80 }}>
        <Spin size="large" />
      </div>
    )
  }
  if (!detail) return null

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Space>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/customer/shelf')}>
          返回货架
        </Button>
      </Space>

      <Card
        title={
          <Space>
            <span style={{ fontSize: 18 }}>{detail.productName}</span>
            <TypeTag type={detail.productType} />
            <RiskBadge level={detail.riskLevel} />
            <Tag>{detail.status === 'OPEN' ? '开放中' : detail.status}</Tag>
          </Space>
        }
        extra={
          <Space>
            <Button
              type="primary"
              size="large"
              disabled={detail.status !== 'OPEN'}
              onClick={() => navigate(`/customer/purchase?code=${detail.productCode}`)}
            >
              立即申购
            </Button>
            <Button
              size="large"
              onClick={() => navigate(`/customer/redeem?code=${detail.productCode}`)}
            >
              赎回
            </Button>
          </Space>
        }
      >
        {detail.consignmentRiskHint && (
          <Alert type="warning" showIcon title={detail.consignmentRiskHint} style={{ marginBottom: 16 }} />
        )}
        <Descriptions bordered column={{ xs: 1, md: 2 }}>
          <Descriptions.Item label="产品代码">{detail.productCode}</Descriptions.Item>
          <Descriptions.Item label="发行方">{detail.issuerName}</Descriptions.Item>
          <Descriptions.Item label="产品类型">
            <TypeTag type={detail.productType} />
          </Descriptions.Item>
          <Descriptions.Item label="风险等级">
            <RiskBadge level={detail.riskLevel} />
          </Descriptions.Item>
          <Descriptions.Item label="投资期限">{detail.termDays} 天</Descriptions.Item>
          <Descriptions.Item label="起购金额">{fmtAmount(detail.minPurchaseAmount)} 元</Descriptions.Item>
          <Descriptions.Item label="申购费率">{(detail.purchaseFeeRate * 100).toFixed(2)}%</Descriptions.Item>
          <Descriptions.Item label="赎回费率">{(detail.redemptionFeeRate * 100).toFixed(2)}%</Descriptions.Item>
          <Descriptions.Item label="业绩比较基准">{detail.expectedReturn ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="剩余额度">{fmtAmount(detail.remainingQuota)} 元</Descriptions.Item>
          <Descriptions.Item label="交易时段">
            {detail.tradeStartTime} ~ {detail.tradeEndTime}
          </Descriptions.Item>
          <Descriptions.Item label="最新净值">
            {fmtNav(detail.latestNav)}
            {detail.navSource && (
              <Tag
                color={detail.navSource.source === 'EXTERNAL' ? 'purple' : 'geekblue'}
                style={{ marginLeft: 8 }}
              >
                {detail.navSource.source === 'EXTERNAL' ? '外部TA同步' : '本行发布'}
              </Tag>
            )}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Row gutter={16}>
        <Col xs={24} lg={14}>
          <Card title="净值走势" size="small">
            <NavTrend points={detail.navTrend ?? []} />
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Card title="产品公告" size="small">
            {(detail.announcements ?? []).length === 0 ? (
              <Text type="secondary">暂无公告</Text>
            ) : (
              <Listy
                items={detail.announcements ?? []}
                rowKey={(a) => `${a.date}-${a.title}`}
                itemRender={(a) => (
                  <div style={{ padding: '6px 0', borderBottom: '1px solid #f0f0f0' }}>
                    <Text type="secondary" style={{ marginRight: 12 }}>
                      {a.date}
                    </Text>
                    {a.title}
                  </div>
                )}
              />
            )}
          </Card>
        </Col>
      </Row>

      <Title level={5} type="secondary" style={{ fontWeight: 'normal' }}>
        理财非存款 · 产品有风险 · 投资须谨慎
      </Title>
    </Space>
  )
}
