import { useEffect, useState } from 'react'
import { Button, Card, Col, Empty, Row, Space, Spin, Statistic, Typography } from 'antd'
import { ShopOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { api } from '../../services/api'
import type { PositionViewResponse } from '../../services/types'
import { TypeTag, fmtAmount } from '../../components/common/dicts'

const { Text, Title } = Typography

/** 首页：资产总览（复用持仓 summary）+ 快捷入口 */
export function HomePage() {
  const navigate = useNavigate()
  const [view, setView] = useState<PositionViewResponse | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    api
      .get<PositionViewResponse>('/api/customer/positions')
      .then(setView)
      .catch(() => setView(null))
      .finally(() => setLoading(false))
  }, [])

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 80 }}>
        <Spin size="large" />
      </div>
    )
  }

  const summary = view?.summary

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Title level={4} style={{ margin: 0 }}>
        资产总览
      </Title>

      {summary ? (
        <>
          <Row gutter={[16, 16]}>
            <Col xs={12} md={6}>
              <Card>
                <Statistic title="总市值（元）" value={summary.totalMarketValue} precision={2} />
              </Card>
            </Col>
            <Col xs={12} md={6}>
              <Card>
                <Statistic
                  title="总浮动盈亏（元）"
                  value={summary.totalFloatingPnl}
                  precision={2}
                  prefix={summary.totalFloatingPnl >= 0 ? '+' : ''}
                  styles={{ content: { color: summary.totalFloatingPnl >= 0 ? '#3f8600' : '#cf1322' } }}
                />
              </Card>
            </Col>
            <Col xs={12} md={6}>
              <Card>
                <Statistic title="自营市值（元）" value={summary.proprietaryMarketValue} precision={2} />
              </Card>
            </Col>
            <Col xs={12} md={6}>
              <Card>
                <Statistic title="代销市值（元）" value={summary.consignmentMarketValue} precision={2} />
              </Card>
            </Col>
          </Row>

          <Row gutter={[16, 16]}>
            <Col xs={24} md={14}>
              <Card
                title="持仓产品"
                size="small"
                extra={
                  <Button type="link" size="small" onClick={() => navigate('/customer/positions')}>
                    查看全部
                  </Button>
                }
              >
                {view.positions.length === 0 ? (
                  <Empty description="暂无持仓，去货架看看吧" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                ) : (
                  view.positions.slice(0, 5).map((p) => (
                    <div
                      key={p.productCode}
                      style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        padding: '8px 0',
                        borderBottom: '1px solid #f0f0f0',
                      }}
                    >
                      <Space>
                        <Text strong>{p.productName}</Text>
                        <TypeTag type={p.productType} />
                      </Space>
                      <Space size={24}>
                        <Text>{fmtAmount(p.marketValue)} 元</Text>
                        <Text type={p.floatingPnl >= 0 ? 'success' : 'danger'}>
                          {p.floatingPnl >= 0 ? '+' : ''}
                          {fmtAmount(p.floatingPnl)}
                        </Text>
                      </Space>
                    </div>
                  ))
                )}
              </Card>
            </Col>
            <Col xs={24} md={10}>
              <Card title="快捷入口" size="small">
                <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
                  <Button block size="large" icon={<ShopOutlined />} onClick={() => navigate('/customer/shelf')}>
                    浏览产品货架
                  </Button>
                  <Button block size="large" onClick={() => navigate('/customer/orders')}>
                    我的订单
                  </Button>
                  <Button block size="large" onClick={() => navigate('/customer/plans')}>
                    预约定投
                  </Button>
                  <Button block size="large" onClick={() => navigate('/customer/ai-qa')}>
                    AI 投教问答
                  </Button>
                </Space>
              </Card>
            </Col>
          </Row>

          {view.inFlights.length > 0 && (
            <Card title={`在途资产（${view.inFlights.length} 笔）`} size="small">
              {view.inFlights.map((f) => (
                <div
                  key={f.orderNo}
                  style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0' }}
                >
                  <Space>
                    <Text>{f.productName}</Text>
                    <TypeTag type={f.productType} />
                  </Space>
                  <Text type="secondary">{fmtAmount(f.amount)} 元 · {f.status}</Text>
                </div>
              ))}
            </Card>
          )}
        </>
      ) : (
        <Empty description="暂无资产数据" />
      )}
    </Space>
  )
}
