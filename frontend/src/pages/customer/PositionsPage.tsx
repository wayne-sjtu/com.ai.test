import { useCallback, useEffect, useState } from 'react'
import type { ColumnsType } from 'antd/es/table'
import { Button, Card, Col, Empty, Row, Space, Spin, Statistic, Table, Tabs, Typography, message } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { api } from '../../services/api'
import type { PositionViewResponse } from '../../services/types'
import {
  ORDER_TYPE,
  OrderStatusTag,
  RiskBadge,
  TypeTag,
  fmtAmount,
  fmtNav,
} from '../../components/common/dicts'

const { Text, Title } = Typography

type Position = PositionViewResponse['positions'][number]
type InFlight = PositionViewResponse['inFlights'][number]

/** 持仓列定义（自营/代销分区复用） */
const positionColumns = (navigate: (path: string) => void): ColumnsType<Position> => [
  {
    title: '产品',
    dataIndex: 'productName',
    render: (v: string, r: Position) => (
      <Space size={4}>
        {v}
        <TypeTag type={r.productType} />
        <RiskBadge level={r.riskLevel} />
      </Space>
    ),
  },
  {
    title: '持有份额',
    dataIndex: 'shares',
    width: 120,
    align: 'right',
    render: (v: number) => fmtAmount(v),
  },
  {
    title: '可赎份额',
    dataIndex: 'availableShares',
    width: 120,
    align: 'right',
    render: (v: number) => <Text strong>{fmtAmount(v)}</Text>,
  },
  {
    title: '冻结份额',
    dataIndex: 'frozenShares',
    width: 110,
    align: 'right',
    render: (v: number) => (v > 0 ? <Text type="warning">{fmtAmount(v)}</Text> : '-'),
  },
  {
    title: '最新净值',
    dataIndex: 'latestNav',
    width: 130,
    align: 'right',
    render: (v: number, r: Position) => (
      <Space size={4}>
        {fmtNav(v)}
        {r.navSource === 'EXTERNAL' && <Text type="secondary" style={{ fontSize: 12 }}>外部</Text>}
      </Space>
    ),
  },
  {
    title: '持仓成本（元）',
    dataIndex: 'costAmount',
    width: 130,
    align: 'right',
    render: (v: number) => fmtAmount(v),
  },
  {
    title: '市值（元）',
    dataIndex: 'marketValue',
    width: 130,
    align: 'right',
    render: (v: number) => <Text strong>{fmtAmount(v)}</Text>,
  },
  {
    title: '浮动盈亏（元）',
    dataIndex: 'floatingPnl',
    width: 130,
    align: 'right',
    render: (v: number) => (
      <Text type={v >= 0 ? 'success' : 'danger'} strong>
        {v >= 0 ? '+' : ''}
        {fmtAmount(v)}
      </Text>
    ),
  },
  {
    title: '收益率',
    dataIndex: 'returnRatePercent',
    width: 90,
    align: 'right',
    render: (v: number) => (
      <Text type={v >= 0 ? 'success' : 'danger'}>
        {v >= 0 ? '+' : ''}
        {v.toFixed(2)}%
      </Text>
    ),
  },
  {
    title: '操作',
    key: 'action',
    width: 80,
    render: (_: unknown, r: Position) => (
      <Button
        size="small"
        disabled={r.availableShares <= 0}
        onClick={() => navigate(`/customer/redeem?code=${r.productCode}`)}
      >
        赎回
      </Button>
    ),
  },
]

/** 统一持仓视图：汇总卡片 + 自营/代销分区 + 在途资产 */
export function PositionsPage() {
  const navigate = useNavigate()
  const [view, setView] = useState<PositionViewResponse | null>(null)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setView(await api.get<PositionViewResponse>('/api/customer/positions'))
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  if (!view) {
    return loading ? (
      <div style={{ textAlign: 'center', padding: 80 }}>
        <Spin size="large" />
      </div>
    ) : (
      <Empty description="暂无持仓数据" />
    )
  }

  const { positions, inFlights, summary } = view
  const proprietary = positions.filter((p) => p.productType === 'PROPRIETARY')
  const consignment = positions.filter((p) => p.productType === 'CONSIGNMENT')

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Title level={4} style={{ margin: 0 }}>
          我的持仓
        </Title>
        <Button icon={<ReloadOutlined />} onClick={() => void load()}>
          刷新
        </Button>
      </Space>

      {/* 汇总卡片 */}
      <Row gutter={16}>
        <Col xs={12} md={6}>
          <Card size="small">
            <Statistic title="总市值（元）" value={summary.totalMarketValue} precision={2} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small">
            <Statistic
              title="总浮动盈亏（元）"
              value={summary.totalFloatingPnl}
              precision={2}
              styles={{ content: { color: summary.totalFloatingPnl >= 0 ? '#3f8600' : '#cf1322' } }}
              prefix={summary.totalFloatingPnl >= 0 ? '+' : ''}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small">
            <Statistic title="自营市值（元）" value={summary.proprietaryMarketValue} precision={2} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small">
            <Statistic title="代销市值（元）" value={summary.consignmentMarketValue} precision={2} />
          </Card>
        </Col>
      </Row>

      <Card>
        <Tabs
          items={[
            {
              key: 'all',
              label: `全部持仓（${positions.length}）`,
              children: (
                <Table
                  rowKey="productCode"
                  size="small"
                  loading={loading}
                  dataSource={positions}
                  columns={positionColumns(navigate)}
                  pagination={false}
                  locale={{ emptyText: '暂无持仓' }}
                  scroll={{ x: 1100 }}
                />
              ),
            },
            {
              key: 'proprietary',
              label: `自营（${proprietary.length}）`,
              children: (
                <Table
                  rowKey="productCode"
                  size="small"
                  dataSource={proprietary}
                  columns={positionColumns(navigate)}
                  pagination={false}
                  locale={{ emptyText: '暂无自营持仓' }}
                  scroll={{ x: 1100 }}
                />
              ),
            },
            {
              key: 'consignment',
              label: `代销（${consignment.length}）`,
              children: (
                <>
                  <Text type="secondary" style={{ display: 'block', marginBottom: 8 }}>
                    代销产品净值由外部 TA 同步（EXTERNAL 标注），数据以发行机构为准。
                  </Text>
                  <Table
                    rowKey="productCode"
                    size="small"
                    dataSource={consignment}
                    columns={positionColumns(navigate)}
                    pagination={false}
                    locale={{ emptyText: '暂无代销持仓' }}
                    scroll={{ x: 1100 }}
                  />
                </>
              ),
            },
            {
              key: 'inflight',
              label: `在途资产（${inFlights.length}）`,
              children: (
                <Table
                  rowKey="orderNo"
                  size="small"
                  dataSource={inFlights}
                  pagination={false}
                  locale={{ emptyText: '暂无在途资产' }}
                  columns={[
                    { title: '订单号', dataIndex: 'orderNo', render: (v) => <Text code>{v}</Text> },
                    {
                      title: '产品',
                      dataIndex: 'productName',
                      render: (v: string, r: InFlight) => (
                        <Space size={4}>
                          {v}
                          <TypeTag type={r.productType} />
                        </Space>
                      ),
                    },
                    {
                      title: '类型',
                      dataIndex: 'orderType',
                      width: 80,
                      render: (v: string) => ORDER_TYPE[v] ?? v,
                    },
                    {
                      title: '金额（元）',
                      dataIndex: 'amount',
                      width: 120,
                      align: 'right',
                      render: (v: number) => fmtAmount(v),
                    },
                    {
                      title: '份额',
                      dataIndex: 'shares',
                      width: 110,
                      align: 'right',
                      render: (v: number) => (v ? fmtAmount(v) : '-'),
                    },
                    {
                      title: '状态',
                      dataIndex: 'status',
                      width: 120,
                      render: (v: string) => <OrderStatusTag status={v} />,
                    },
                  ]}
                />
              ),
            },
          ]}
        />
      </Card>
    </Space>
  )
}
