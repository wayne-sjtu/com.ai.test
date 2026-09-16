import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Card, Col, Empty, Form, Input, Row, Select, Space, Spin, Typography } from 'antd'
import { SearchOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { api } from '../../services/api'
import type { ProductListResponse, ProductSummary } from '../../services/types'
import { RiskBadge, TypeTag, fmtAmount, fmtNav } from '../../components/common/dicts'

const { Text, Title } = Typography

/** 产品货架：统一列表（自营+代销），类型/风险筛选 + 关键词搜索；代销产品风险提示 */
export function ShelfPage() {
  const navigate = useNavigate()
  const [products, setProducts] = useState<ProductSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [filters, setFilters] = useState<{ productType?: string; riskLevel?: string; keyword?: string }>({})

  const load = useCallback(async (f: typeof filters) => {
    setLoading(true)
    try {
      const params = new URLSearchParams()
      if (f.productType) params.set('productType', f.productType)
      if (f.riskLevel) params.set('riskLevel', f.riskLevel)
      if (f.keyword) params.set('keyword', f.keyword)
      const res = await api.get<ProductListResponse>(`/api/customer/products?${params.toString()}`)
      setProducts(res.products)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load(filters)
  }, [filters, load])

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Title level={4} style={{ margin: 0 }}>
        产品货架
      </Title>

      <Form layout="inline" onFinish={(v) => setFilters(v)}>
        <Form.Item name="productType" initialValue={undefined}>
          <Select
            allowClear
            placeholder="产品类型"
            style={{ width: 140 }}
            options={[
              { value: 'PROPRIETARY', label: '自营' },
              { value: 'CONSIGNMENT', label: '代销' },
            ]}
          />
        </Form.Item>
        <Form.Item name="riskLevel" initialValue={undefined}>
          <Select
            allowClear
            placeholder="风险等级"
            style={{ width: 140 }}
            options={['R1', 'R2', 'R3', 'R4', 'R5'].map((r) => ({ value: r, label: r }))}
          />
        </Form.Item>
        <Form.Item name="keyword" initialValue={undefined}>
          <Input placeholder="产品名称 / 代码 / 发行方" prefix={<SearchOutlined />} style={{ width: 220 }} allowClear />
        </Form.Item>
        <Button type="primary" htmlType="submit">
          筛选
        </Button>
      </Form>

      {loading ? (
        <div style={{ textAlign: 'center', padding: 80 }}>
          <Spin size="large" />
        </div>
      ) : products.length === 0 ? (
        <Empty description="暂无符合条件的产品" />
      ) : (
        <Row gutter={[16, 16]}>
          {products.map((p) => (
            <Col key={p.productCode} xs={24} md={12} xl={8}>
              <Card
                hoverable
                onClick={() => navigate(`/customer/shelf/${p.id}`)}
                title={
                  <Space>
                    <span>{p.productName}</span>
                    <TypeTag type={p.productType} />
                    <RiskBadge level={p.riskLevel} />
                  </Space>
                }
                extra={<Text type="secondary" code>{p.productCode}</Text>}
              >
                <Space orientation="vertical" size="small" style={{ width: '100%' }}>
                  <div>
                    <Text type="secondary">发行方：</Text>
                    <Text>{p.issuerName}</Text>
                    <Text type="secondary" style={{ marginLeft: 16 }}>
                      期限：
                    </Text>
                    <Text>{p.termDays} 天</Text>
                  </div>
                  <div>
                    <Text type="secondary">最新净值：</Text>
                    <Text strong>{fmtNav(p.latestNav)}</Text>
                    {p.latestNavDate && (
                      <Text type="secondary" style={{ marginLeft: 8 }}>
                        （{p.latestNavDate}）
                      </Text>
                    )}
                    <Text type="secondary" style={{ marginLeft: 16 }}>
                      业绩比较基准：
                    </Text>
                    <Text>{p.expectedReturn ?? '-'}</Text>
                  </div>
                  <div>
                    <Text type="secondary">起购金额：</Text>
                    <Text strong style={{ color: '#cf1322' }}>
                      {fmtAmount(p.minPurchaseAmount)} 元
                    </Text>
                    <Text type="secondary" style={{ marginLeft: 16 }}>
                      状态：
                    </Text>
                    <Text>{p.status === 'OPEN' ? '开放中' : p.status}</Text>
                  </div>
                  {p.consignmentRiskHint && (
                    <Alert type="warning" showIcon title={p.consignmentRiskHint} />
                  )}
                </Space>
              </Card>
            </Col>
          ))}
        </Row>
      )}
    </Space>
  )
}
