import { useCallback, useEffect, useState } from 'react'
import { Button, Card, Form, Select, Space, Table, Tabs, Tag, Typography, message } from 'antd'
import { DownloadOutlined, ReloadOutlined } from '@ant-design/icons'
import { api } from '../../services/api'
import type { CapitalFlowItem, TradeFlowItem } from '../../services/types'
import {
  ACTION_TYPE,
  ORDER_TYPE,
  OrderStatusTag,
  TypeTag,
  fmtAmount,
} from '../../components/common/dicts'

const { Text, Title } = Typography

/** 流水页：Tab1 交易流水（筛选+CSV导出）/ Tab2 资金流水（对账锚点） */
export function FlowsPage() {
  const [activeTab, setActiveTab] = useState('trade')

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Title level={4} style={{ margin: 0 }}>
        资金流水
      </Title>
      <Card>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[
            { key: 'trade', label: '交易流水', children: <TradeFlowTab /> },
            { key: 'capital', label: '资金流水', children: <CapitalFlowTab /> },
          ]}
        />
      </Card>
    </Space>
  )
}

/** 交易流水：productType/orderType/status 组合筛选 + CSV 导出 */
function TradeFlowTab() {
  const [flows, setFlows] = useState<TradeFlowItem[]>([])
  const [loading, setLoading] = useState(true)
  const [filters, setFilters] = useState<{ productType?: string; orderType?: string; status?: string }>({})

  const load = useCallback(async (f: typeof filters) => {
    setLoading(true)
    try {
      const params = new URLSearchParams()
      if (f.productType) params.set('productType', f.productType)
      if (f.orderType) params.set('orderType', f.orderType)
      if (f.status) params.set('status', f.status)
      const res = await api.get<{ total: number; flows: TradeFlowItem[] }>(
        `/api/customer/flows?${params.toString()}`,
      )
      setFlows(res.flows)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load(filters)
  }, [filters, load])

  const exportCsv = async () => {
    const params = new URLSearchParams()
    params.set('format', 'csv')
    if (filters.productType) params.set('productType', filters.productType)
    if (filters.orderType) params.set('orderType', filters.orderType)
    if (filters.status) params.set('status', filters.status)
    try {
      await api.download(`/api/customer/flows?${params.toString()}`, '交易流水.csv')
      message.success('导出成功')
    } catch {
      message.error('导出失败')
    }
  }

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Form layout="inline" onFinish={(v) => setFilters(v)}>
        <Form.Item name="productType" initialValue={undefined}>
          <Select
            allowClear
            placeholder="产品类型"
            style={{ width: 130 }}
            options={[
              { value: 'PROPRIETARY', label: '自营' },
              { value: 'CONSIGNMENT', label: '代销' },
            ]}
          />
        </Form.Item>
        <Form.Item name="orderType" initialValue={undefined}>
          <Select
            allowClear
            placeholder="订单类型"
            style={{ width: 130 }}
            options={[
              { value: 'PURCHASE', label: '申购' },
              { value: 'REDEEM', label: '赎回' },
            ]}
          />
        </Form.Item>
        <Form.Item name="status" initialValue={undefined}>
          <Select
            allowClear
            placeholder="状态"
            style={{ width: 150 }}
            options={[
              'PENDING',
              'TA_ACCEPTED',
              'CONFIRMED',
              'SETTLED',
              'REDEEMED',
              'PENDING_QUEUE',
              'CANCELLED',
              'FAILED',
            ].map((s) => ({ value: s, label: s }))}
          />
        </Form.Item>
        <Button type="primary" htmlType="submit">
          筛选
        </Button>
        <Button icon={<ReloadOutlined />} onClick={() => setFilters({})} style={{ marginLeft: 8 }}>
          重置
        </Button>
        <Button icon={<DownloadOutlined />} onClick={() => void exportCsv()} style={{ marginLeft: 8 }}>
          导出 CSV
        </Button>
      </Form>

      <Table
        rowKey="orderNo"
        size="small"
        loading={loading}
        dataSource={flows}
        pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 笔` }}
        columns={[
          { title: '订单号', dataIndex: 'orderNo', width: 190, render: (v) => <Text code>{v}</Text> },
          {
            title: '产品',
            dataIndex: 'productCode',
            width: 140,
            render: (v: string, r: TradeFlowItem) => (
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
            title: '费用（元）',
            dataIndex: 'fee',
            width: 100,
            align: 'right',
            render: (v: number) => (v ? fmtAmount(v) : '-'),
          },
          {
            title: '状态',
            dataIndex: 'status',
            width: 120,
            render: (v: string) => <OrderStatusTag status={v} />,
          },
          {
            title: '时间',
            dataIndex: 'createdAt',
            width: 170,
            render: (v: string) => v?.replace('T', ' ').slice(0, 19),
          },
        ]}
      />
    </Space>
  )
}

/** 资金流水：FREEZE/DEDUCT/UNFREEZE/RETURN + balanceAfter 对账锚点 */
function CapitalFlowTab() {
  const [flows, setFlows] = useState<CapitalFlowItem[]>([])
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const res = await api.get<{ total: number; flows: CapitalFlowItem[] }>('/api/customer/capital-flows')
      setFlows(res.flows)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const ACTION_TAG: Record<string, { color: string; label: string }> = {
    FREEZE: { color: 'warning', label: ACTION_TYPE.FREEZE },
    DEDUCT: { color: 'processing', label: ACTION_TYPE.DEDUCT },
    UNFREEZE: { color: 'default', label: ACTION_TYPE.UNFREEZE },
    RETURN: { color: 'success', label: ACTION_TYPE.RETURN },
  }

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Text type="secondary">
        资金流水记录每一笔资金动作（冻结/扣划/解冻/回款），「动作后余额」为对账锚点，可用于订单-流水-持仓三方核对。
      </Text>
      <Table
        rowKey="id"
        size="small"
        loading={loading}
        dataSource={flows}
        pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 笔` }}
        columns={[
          { title: '关联订单号', dataIndex: 'orderNo', width: 190, render: (v) => <Text code>{v}</Text> },
          {
            title: '动作',
            dataIndex: 'actionType',
            width: 100,
            render: (v: string) => {
              const conf = ACTION_TAG[v]
              return <Tag color={conf?.color ?? 'default'}>{conf?.label ?? v}</Tag>
            },
          },
          {
            title: '金额（元）',
            dataIndex: 'amount',
            width: 140,
            align: 'right',
            render: (v: number) => fmtAmount(v),
          },
          {
            title: '动作后余额（元）',
            dataIndex: 'balanceAfter',
            width: 160,
            align: 'right',
            render: (v: number) => <Text strong>{fmtAmount(v)}</Text>,
          },
          {
            title: '时间',
            dataIndex: 'createdAt',
            width: 170,
            render: (v: string) => v?.replace('T', ' ').slice(0, 19),
          },
        ]}
      />
    </Space>
  )
}
