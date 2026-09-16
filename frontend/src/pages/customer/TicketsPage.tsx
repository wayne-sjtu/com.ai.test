import { useCallback, useEffect, useState } from 'react'
import {
  Alert,
  Button,
  Form,
  Input,
  Modal,
  Radio,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { api } from '../../services/api'
import type { ProductSummary, Ticket, TicketListResponse } from '../../services/types'

const { Text, Title, Paragraph } = Typography

const TICKET_STATUS: Record<string, { color: string; label: string }> = {
  OPEN: { color: 'default', label: '待受理' },
  PROCESSING: { color: 'processing', label: '处理中' },
  CLOSED: { color: 'success', label: '已关闭' },
}

/** 客服工单：提交（咨询/投诉）+ 列表（处理进度/答复/代销外部同步） */
export function TicketsPage() {
  const [tickets, setTickets] = useState<Ticket[]>([])
  const [products, setProducts] = useState<ProductSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm<{
    ticketType: 'CONSULT' | 'COMPLAINT'
    productCode?: string
    content: string
  }>()

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [t, p] = await Promise.all([
        api.get<TicketListResponse>('/api/customer/tickets'),
        api.get<{ products: ProductSummary[] }>('/api/customer/products'),
      ])
      setTickets(t.tickets)
      setProducts(p.products)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const submit = async (values: {
    ticketType: 'CONSULT' | 'COMPLAINT'
    productCode?: string
    content: string
  }) => {
    setSubmitting(true)
    try {
      await api.post('/api/customer/tickets', values)
      message.success('工单提交成功')
      setOpen(false)
      form.resetFields()
      void load()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '提交失败')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Title level={4} style={{ margin: 0 }}>
          客服工单
        </Title>
        <Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setOpen(true)}>
            提交工单
          </Button>
          <Button icon={<ReloadOutlined />} onClick={() => void load()}>
            刷新
          </Button>
        </Space>
      </Space>

      <Table
        rowKey="ticketNo"
        loading={loading}
        dataSource={tickets}
        pagination={{ pageSize: 10 }}
        columns={[
          { title: '工单号', dataIndex: 'ticketNo', width: 180, render: (v) => <Text code>{v}</Text> },
          {
            title: '类型',
            dataIndex: 'ticketType',
            width: 80,
            render: (v: string) => (v === 'CONSULT' ? <Tag>咨询</Tag> : <Tag color="volcano">投诉</Tag>),
          },
          {
            title: '产品',
            dataIndex: 'productCode',
            width: 130,
            render: (v: string | null, r: Ticket) =>
              v ? (
                <Space size={4}>
                  {v}
                  {r.productType === 'CONSIGNMENT' && <Tag color="purple">代销</Tag>}
                </Space>
              ) : (
                '-'
              ),
          },
          {
            title: '内容',
            dataIndex: 'content',
            ellipsis: true,
            render: (v: string) => <Text style={{ maxWidth: 260 }}>{v}</Text>,
          },
          {
            title: '状态',
            dataIndex: 'status',
            width: 100,
            render: (v: string) => {
              const conf = TICKET_STATUS[v]
              return <Tag color={conf?.color ?? 'default'}>{conf?.label ?? v}</Tag>
            },
          },
          {
            title: '外部同步',
            dataIndex: 'externalSyncStatus',
            width: 100,
            render: (v: string | null) =>
              v ? (
                <Tag color={v === 'SYNCED' ? 'success' : 'warning'}>
                  {v === 'SYNCED' ? '已同步' : '待同步'}
                </Tag>
              ) : (
                <Text type="secondary">-</Text>
              ),
          },
          {
            title: '答复',
            dataIndex: 'reply',
            width: 90,
            render: (v: string | null) =>
              v ? (
                <Button
                  size="small"
                  type="link"
                  onClick={() =>
                    Modal.info({
                      title: '处理答复',
                      content: (
                        <Paragraph style={{ whiteSpace: 'pre-wrap' }}>{v}</Paragraph>
                      ),
                    })
                  }
                >
                  查看
                </Button>
              ) : (
                <Text type="secondary">-</Text>
              ),
          },
          {
            title: '提交时间',
            dataIndex: 'createdAt',
            width: 170,
            render: (v: string) => v?.replace('T', ' ').slice(0, 19),
          },
        ]}
      />

      <Modal
        title="提交工单"
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={submitting}
        okText="提交"
        width={520}
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16, marginTop: 8 }}
          title="代销产品的投诉将同步至发行机构处理（EXTERNAL_SYNC），处理进度会推送到消息中心。"
        />
        <Form form={form} layout="vertical" onFinish={submit}>
          <Form.Item name="ticketType" label="工单类型" initialValue="CONSULT" rules={[{ required: true }]}>
            <Radio.Group
              options={[
                { value: 'CONSULT', label: '咨询' },
                { value: 'COMPLAINT', label: '投诉' },
              ]}
            />
          </Form.Item>
          <Form.Item
            noStyle
            shouldUpdate={(p, c) => p.ticketType !== c.ticketType}
          >
            {({ getFieldValue }) =>
              getFieldValue('ticketType') === 'COMPLAINT' ? (
                <Form.Item
                  name="productCode"
                  label="投诉产品"
                  rules={[{ required: true, message: '投诉需选择产品' }]}
                >
                  <Select
                    showSearch
                    optionFilterProp="label"
                    placeholder="选择产品"
                    options={products.map((p) => ({
                      value: p.productCode,
                      label: `${p.productName}（${p.productCode}）`,
                    }))}
                  />
                </Form.Item>
              ) : null
            }
          </Form.Item>
          <Form.Item
            name="content"
            label="内容"
            rules={[
              { required: true, message: '请描述您的问题' },
              { max: 500, message: '不超过 500 字' },
            ]}
          >
            <Input.TextArea rows={4} maxLength={500} showCount placeholder="请描述您遇到的问题或建议" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
