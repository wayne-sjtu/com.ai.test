import { useCallback, useEffect, useState } from 'react'
import type { ColumnsType } from 'antd/es/table'
import { Button, Card, Drawer, Form, Input, Select, Space, Table, Tag, Timeline, Typography, message } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api } from '../../services/api'
import type { AdminOrderRow, OrderEvent } from '../../services/types'
import { ORDER_TYPE, OrderStatusTag, TypeTag, fmtAmount } from '../../components/common/dicts'

const { Text, Title } = Typography

/** 管理端订单：五条件组合筛选 + 事件轨迹抽屉（TA 标识/报文流水号） */
export function AdminOrdersPage() {
  const [orders, setOrders] = useState<AdminOrderRow[]>([])
  const [loading, setLoading] = useState(true)
  const [filters, setFilters] = useState<{
    productType?: string
    orderType?: string
    status?: string
    customerNo?: string
    productCode?: string
  }>({})

  // 事件轨迹抽屉
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [currentOrder, setCurrentOrder] = useState<AdminOrderRow | null>(null)
  const [events, setEvents] = useState<OrderEvent[]>([])
  const [eventsLoading, setEventsLoading] = useState(false)

  const load = useCallback(async (f: typeof filters) => {
    setLoading(true)
    try {
      const params = new URLSearchParams()
      if (f.productType) params.set('productType', f.productType)
      if (f.orderType) params.set('orderType', f.orderType)
      if (f.status) params.set('status', f.status)
      if (f.customerNo) params.set('customerNo', f.customerNo)
      if (f.productCode) params.set('productCode', f.productCode)
      setOrders(await api.get<AdminOrderRow[]>(`/api/admin/orders?${params.toString()}`))
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load(filters)
  }, [filters, load])

  const openEvents = async (order: AdminOrderRow) => {
    setCurrentOrder(order)
    setDrawerOpen(true)
    setEventsLoading(true)
    try {
      setEvents(await api.get<OrderEvent[]>(`/api/admin/orders/${order.orderNo}/events`))
    } catch (e) {
      message.error(e instanceof Error ? e.message : '轨迹加载失败')
    } finally {
      setEventsLoading(false)
    }
  }

  const columns: ColumnsType<AdminOrderRow> = [
    { title: '订单号', dataIndex: 'orderNo', width: 190, fixed: 'left', render: (v) => <Text code>{v}</Text> },
    { title: '客户号', dataIndex: 'customerNo', width: 110 },
    {
      title: '产品',
      dataIndex: 'productCode',
      width: 130,
      render: (v: string, r) => (
        <Space size={4}>
          {v}
          <TypeTag type={r.productType} />
        </Space>
      ),
    },
    { title: '类型', dataIndex: 'orderType', width: 70, render: (v) => ORDER_TYPE[v] ?? v },
    {
      title: '金额（元）',
      dataIndex: 'amount',
      width: 110,
      align: 'right',
      render: (v: number) => fmtAmount(v),
    },
    {
      title: '份额',
      dataIndex: 'shares',
      width: 100,
      align: 'right',
      render: (v: number) => (v ? fmtAmount(v) : '-'),
    },
    {
      title: '费用（元）',
      dataIndex: 'fee',
      width: 90,
      align: 'right',
      render: (v: number) => (v ? fmtAmount(v) : '-'),
    },
    { title: '状态', dataIndex: 'status', width: 110, render: (v) => <OrderStatusTag status={v} /> },
    {
      title: 'TA 流水号',
      dataIndex: 'taSerialNo',
      width: 150,
      render: (v: string | null) => (v ? <Text code style={{ fontSize: 12 }}>{v}</Text> : '-'),
    },
    {
      title: '下单时间',
      dataIndex: 'createdAt',
      width: 160,
      render: (v: string) => v?.replace('T', ' ').slice(0, 19),
    },
    {
      title: '操作',
      key: 'action',
      width: 90,
      fixed: 'right',
      render: (_, r) => (
        <Button size="small" type="link" onClick={() => void openEvents(r)}>
          事件轨迹
        </Button>
      ),
    },
  ]

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Title level={4} style={{ margin: 0 }}>
          统一订单列表
        </Title>
        <Button icon={<ReloadOutlined />} onClick={() => void load(filters)}>
          刷新
        </Button>
      </Space>

      <Card size="small">
        <Form layout="inline" onFinish={(v) => setFilters(v)}>
          <Form.Item name="productType" initialValue={undefined}>
            <Select
              allowClear
              placeholder="产品类型"
              style={{ width: 120 }}
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
              style={{ width: 120 }}
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
          <Form.Item name="customerNo" initialValue={undefined}>
            <Input allowClear placeholder="客户号" style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="productCode" initialValue={undefined}>
            <Input allowClear placeholder="产品代码" style={{ width: 140 }} />
          </Form.Item>
          <Button type="primary" htmlType="submit">
            筛选
          </Button>
        </Form>
      </Card>

      <Card size="small">
        <Table
          rowKey="orderNo"
          size="small"
          loading={loading}
          dataSource={orders}
          columns={columns}
          scroll={{ x: 1300 }}
          pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 笔` }}
        />
      </Card>

      <Drawer
        title={
          <Space>
            <span>事件轨迹</span>
            {currentOrder && (
              <>
                <Text code>{currentOrder.orderNo}</Text>
                <OrderStatusTag status={currentOrder.status} />
              </>
            )}
          </Space>
        }
        width={520}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
      >
        {eventsLoading ? (
          <Table loading size="small" />
        ) : (
          <Timeline
            items={events.map((e) => ({
              color: e.taTag ? 'blue' : 'gray',
              children: (
                <div key={e.id}>
                  <Space size={8} wrap>
                    <Text strong>{e.eventType}</Text>
                    {e.fromStatus && (
                      <>
                        <Tag>{e.fromStatus}</Tag>
                        <span>→</span>
                      </>
                    )}
                    <Tag color="blue">{e.toStatus}</Tag>
                    {e.taTag && <Tag color="geekblue">{e.taTag}</Tag>}
                  </Space>
                  <div style={{ margin: '4px 0' }}>
                    <Text type="secondary">{e.detail}</Text>
                  </div>
                  <Space size={16}>
                    {e.taSerialNo && (
                      <Text type="secondary" code style={{ fontSize: 12 }}>
                        报文流水号 {e.taSerialNo}
                      </Text>
                    )}
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {e.createdAt?.replace('T', ' ').slice(0, 19)}
                    </Text>
                  </Space>
                </div>
              ),
            }))}
          />
        )}
      </Drawer>
    </Space>
  )
}
