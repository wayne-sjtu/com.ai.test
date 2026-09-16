import { useCallback, useEffect, useState } from 'react'
import { Button, Card, Modal, Popconfirm, Space, Table, Typography, message } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api } from '../../services/api'
import type { OrderResponse } from '../../services/types'
import { ORDER_TYPE, OrderStatusTag, TypeTag, fmtAmount } from '../../components/common/dicts'

const { Title, Text } = Typography

/** 在途状态（TA 确认前可撤） */
const CANCELLABLE = new Set(['PENDING', 'TA_ACCEPTED'])

/** 我的订单：列表 + 可撤时段内撤单 */
export function OrdersPage() {
  const [orders, setOrders] = useState<OrderResponse[]>([])
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setOrders(await api.get<OrderResponse[]>('/api/customer/orders'))
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const cancelOrder = async (orderNo: string) => {
    try {
      await api.post(`/api/customer/orders/${orderNo}/cancel`)
      message.success('撤单成功')
      void load()
    } catch (e) {
      const err = e as { code?: string; message?: string }
      if (err.code === 'ORDER_NOT_CANCELLABLE') {
        Modal.warning({
          title: '不可撤销',
          content: (err.message ?? '') + '（订单已过可撤时段或 TA 已确认）',
        })
      } else {
        message.error(err.message ?? '撤单失败')
      }
    }
  }

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Title level={4} style={{ margin: 0 }}>
          我的订单
        </Title>
        <Button icon={<ReloadOutlined />} onClick={() => void load()}>
          刷新
        </Button>
      </Space>

      <Card>
        <Table
          rowKey="orderNo"
          loading={loading}
          dataSource={orders}
          pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 笔` }}
          columns={[
            { title: '订单号', dataIndex: 'orderNo', width: 190, render: (v) => <Text code>{v}</Text> },
            {
              title: '产品',
              dataIndex: 'productCode',
              width: 110,
              render: (v, r) => (
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
              render: (v) => ORDER_TYPE[v] ?? v,
            },
            {
              title: '金额（元）',
              dataIndex: 'amount',
              width: 120,
              align: 'right',
              render: (v) => fmtAmount(v),
            },
            {
              title: '份额',
              dataIndex: 'shares',
              width: 110,
              align: 'right',
              render: (v) => (v ? fmtAmount(v) : '-'),
            },
            {
              title: '费用（元）',
              dataIndex: 'fee',
              width: 100,
              align: 'right',
              render: (v) => (v ? fmtAmount(v) : '-'),
            },
            {
              title: '状态',
              dataIndex: 'status',
              width: 120,
              render: (v) => <OrderStatusTag status={v} />,
            },
            {
              title: '下单时间',
              dataIndex: 'createdAt',
              width: 170,
              render: (v) => v?.replace('T', ' ').slice(0, 19),
            },
            {
              title: '操作',
              key: 'action',
              width: 90,
              render: (_, r) =>
                CANCELLABLE.has(r.status) ? (
                  <Popconfirm title="确认撤销该订单？" onConfirm={() => void cancelOrder(r.orderNo)}>
                    <Button size="small" danger>
                      撤单
                    </Button>
                  </Popconfirm>
                ) : (
                  <Text type="secondary">-</Text>
                ),
            },
          ]}
        />
      </Card>
    </Space>
  )
}
