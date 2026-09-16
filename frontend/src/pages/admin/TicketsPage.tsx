import { useCallback, useEffect, useState } from 'react'
import type { ColumnsType } from 'antd/es/table'
import {
  Alert,
  Button,
  Card,
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
import { ReloadOutlined } from '@ant-design/icons'
import { api } from '../../services/api'
import type { Ticket } from '../../services/types'

const { Text, Title, Paragraph } = Typography

const TICKET_STATUS: Record<string, { color: string; label: string }> = {
  OPEN: { color: 'default', label: '待受理' },
  PROCESSING: { color: 'processing', label: '处理中' },
  CLOSED: { color: 'success', label: '已关闭' },
}

/** 管理端工单处理：筛选 + 受理（ACCEPT）/ 关闭（CLOSE 答复必填，代销投诉 SYNCED 闭环） */
export function AdminTicketsPage() {
  const [tickets, setTickets] = useState<Ticket[]>([])
  const [loading, setLoading] = useState(true)
  const [filters, setFilters] = useState<{ status?: string; ticketType?: string }>({})

  // 处理弹窗
  const [handleTarget, setHandleTarget] = useState<Ticket | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm<{ action: 'ACCEPT' | 'CLOSE'; reply?: string }>()

  const load = useCallback(async (f: typeof filters) => {
    setLoading(true)
    try {
      const params = new URLSearchParams()
      if (f.status) params.set('status', f.status)
      if (f.ticketType) params.set('ticketType', f.ticketType)
      setTickets(await api.get<Ticket[]>(`/api/admin/tickets?${params.toString()}`))
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load(filters)
  }, [filters, load])

  const openHandle = (ticket: Ticket) => {
    setHandleTarget(ticket)
    form.resetFields()
    form.setFieldValue('action', 'ACCEPT')
  }

  const handle = async (values: { action: 'ACCEPT' | 'CLOSE'; reply?: string }) => {
    if (!handleTarget) return
    setSubmitting(true)
    try {
      const res = await api.put<Ticket>(`/api/admin/tickets/${handleTarget.ticketNo}/handle`, values)
      message.success(
        values.action === 'ACCEPT'
          ? '已受理，客户将收到进度消息'
          : `已关闭${res.externalSyncStatus === 'SYNCED' ? '，代销投诉已同步发行机构（SYNCED）' : ''}`,
      )
      setHandleTarget(null)
      void load(filters)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '处理失败')
    } finally {
      setSubmitting(false)
    }
  }

  const columns: ColumnsType<Ticket> = [
    { title: '工单号', dataIndex: 'ticketNo', width: 180, render: (v) => <Text code>{v}</Text> },
    {
      title: '类型',
      dataIndex: 'ticketType',
      width: 70,
      render: (v: string) => (v === 'CONSULT' ? <Tag>咨询</Tag> : <Tag color="volcano">投诉</Tag>),
    },
    { title: '客户号', dataIndex: 'customerNo', width: 110 },
    {
      title: '产品',
      dataIndex: 'productCode',
      width: 120,
      render: (v: string | null, r) =>
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
      render: (v: string) => <Text style={{ maxWidth: 220 }}>{v}</Text>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => {
        const conf = TICKET_STATUS[v]
        return <Tag color={conf?.color ?? 'default'}>{conf?.label ?? v}</Tag>
      },
    },
    {
      title: '外部同步',
      dataIndex: 'externalSyncStatus',
      width: 90,
      render: (v: string | null) =>
        v ? (
          <Tag color={v === 'SYNCED' ? 'success' : 'warning'}>{v === 'SYNCED' ? '已同步' : '待同步'}</Tag>
        ) : (
          <Text type="secondary">-</Text>
        ),
    },
    {
      title: '处理人',
      dataIndex: 'handler',
      width: 90,
      render: (v: string | null) => v ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 80,
      fixed: 'right',
      render: (_, r: Ticket) =>
        r.status !== 'CLOSED' ? (
          <Button size="small" type="link" onClick={() => openHandle(r)}>
            处理
          </Button>
        ) : (
          <Text type="secondary">-</Text>
        ),
    },
  ]

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Title level={4} style={{ margin: 0 }}>
          工单处理
        </Title>
        <Button icon={<ReloadOutlined />} onClick={() => void load(filters)}>
          刷新
        </Button>
      </Space>

      <Alert
        type="info"
        showIcon
        title="受理后工单进入处理中；关闭时答复必填。代销产品投诉关闭时自动置 SYNCED 完成发行机构同步闭环，客户全程收到 TICKET_PROGRESS 消息。"
      />

      <Card size="small">
        <Form layout="inline" onFinish={(v) => setFilters(v)}>
          <Form.Item name="status" initialValue={undefined}>
            <Select
              allowClear
              placeholder="状态"
              style={{ width: 120 }}
              options={Object.entries(TICKET_STATUS).map(([value, c]) => ({ value, label: c.label }))}
            />
          </Form.Item>
          <Form.Item name="ticketType" initialValue={undefined}>
            <Select
              allowClear
              placeholder="类型"
              style={{ width: 120 }}
              options={[
                { value: 'CONSULT', label: '咨询' },
                { value: 'COMPLAINT', label: '投诉' },
              ]}
            />
          </Form.Item>
          <Button type="primary" htmlType="submit">
            筛选
          </Button>
        </Form>
      </Card>

      <Card size="small">
        <Table
          rowKey="ticketNo"
          size="small"
          loading={loading}
          dataSource={tickets}
          columns={columns}
          scroll={{ x: 1100 }}
          pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 条` }}
        />
      </Card>

      <Modal
        title={
          <Space>
            <span>处理工单</span>
            {handleTarget && <Text code>{handleTarget.ticketNo}</Text>}
          </Space>
        }
        open={!!handleTarget}
        onCancel={() => setHandleTarget(null)}
        onOk={() => form.submit()}
        confirmLoading={submitting}
        okText="提交处理"
        width={520}
      >
        {handleTarget && (
          <>
            <Paragraph type="secondary" style={{ whiteSpace: 'pre-wrap', background: '#fafafa', padding: 12 }}>
              {handleTarget.content}
            </Paragraph>
            <Form form={form} layout="vertical" onFinish={handle}>
              <Form.Item name="action" label="处理动作" initialValue="ACCEPT" rules={[{ required: true }]}>
                <Radio.Group
                  options={[
                    { value: 'ACCEPT', label: '受理（进入处理中）' },
                    { value: 'CLOSE', label: '关闭（需答复客户）' },
                  ]}
                />
              </Form.Item>
              <Form.Item noStyle shouldUpdate={(p, c) => p.action !== c.action}>
                {({ getFieldValue }) =>
                  getFieldValue('action') === 'CLOSE' ? (
                    <Form.Item
                      name="reply"
                      label="答复内容"
                      rules={[
                        { required: true, message: '关闭工单必须填写答复' },
                        { max: 500, message: '不超过 500 字' },
                      ]}
                    >
                      <Input.TextArea rows={4} maxLength={500} showCount placeholder="填写给客户的处理结果答复" />
                    </Form.Item>
                  ) : null
                }
              </Form.Item>
            </Form>
          </>
        )}
      </Modal>
    </Space>
  )
}
