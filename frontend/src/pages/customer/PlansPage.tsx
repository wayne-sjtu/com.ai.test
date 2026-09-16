import { useCallback, useEffect, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Radio,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { api } from '../../services/api'
import type { DividendSetting, DividendSettingResult, InvestPlan, ProductSummary } from '../../services/types'
import { fmtAmount } from '../../components/common/dicts'

const { Text, Title } = Typography

/** 计划页：Tab1 预约申购/定投计划；Tab2 分红方式设置 */
export function PlansPage() {
  const [activeTab, setActiveTab] = useState('plans')
  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Title level={4} style={{ margin: 0 }}>
        投资计划
      </Title>
      <Card>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[
            { key: 'plans', label: '预约定投', children: <PlansTab /> },
            { key: 'dividend', label: '分红方式', children: <DividendTab /> },
          ]}
        />
      </Card>
    </Space>
  )
}

const PLAN_STATUS: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'processing', label: '生效中' },
  FINISHED: { color: 'success', label: '已完成' },
  CANCELLED: { color: 'default', label: '已取消' },
  EXPIRED: { color: 'error', label: '已作废' },
}

/** 预约申购/定投计划列表 + 创建/取消 */
function PlansTab() {
  const [plans, setPlans] = useState<InvestPlan[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm<{
    productCode: string
    planType: 'RESERVE' | 'REGULAR_INVEST'
    amount: number
    triggerDate: string
    periodType?: string
  }>()

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const res = await api.get<{ total: number; plans: InvestPlan[] }>('/api/customer/plans')
      setPlans(res.plans)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const create = async (values: {
    productCode: string
    planType: string
    amount: number
    triggerDate: string
    periodType?: string
  }) => {
    setSubmitting(true)
    try {
      await api.post('/api/customer/plans', values)
      message.success('计划创建成功，到期将自动发起申购')
      setOpen(false)
      form.resetFields()
      void load()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '创建失败')
    } finally {
      setSubmitting(false)
    }
  }

  const cancel = async (planNo: string) => {
    try {
      await api.delete(`/api/customer/plans/${planNo}`)
      message.success('已取消计划')
      void load()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '取消失败')
    }
  }

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Text type="secondary">
        预约申购在指定日期自动下单；定投按周期自动扣款申购（DAY/WEEK/MONTH）。触发时仍会执行签约、适当性、资金等全套校验。
      </Text>
      <Space>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setOpen(true)}>
          新建计划
        </Button>
        <Button icon={<ReloadOutlined />} onClick={() => void load()}>
          刷新
        </Button>
      </Space>
      <Table
        rowKey="planNo"
        size="small"
        loading={loading}
        dataSource={plans}
        pagination={false}
        columns={[
          { title: '计划号', dataIndex: 'planNo', width: 180, render: (v) => <Text code>{v}</Text> },
          { title: '产品', dataIndex: 'productCode', width: 120 },
          {
            title: '类型',
            dataIndex: 'planType',
            width: 90,
            render: (v: string) => (v === 'RESERVE' ? <Tag>预约申购</Tag> : <Tag color="blue">定投</Tag>),
          },
          {
            title: '金额（元）',
            dataIndex: 'amount',
            width: 110,
            align: 'right',
            render: (v: number) => fmtAmount(v),
          },
          {
            title: '周期',
            dataIndex: 'periodType',
            width: 80,
            render: (v: string | null) =>
              v ? ({ DAY: '每日', WEEK: '每周', MONTH: '每月' }[v] ?? v) : '-',
          },
          {
            title: '下次触发',
            dataIndex: 'nextTriggerDate',
            width: 110,
            render: (v: string | null) => v ?? '-',
          },
          {
            title: '状态',
            dataIndex: 'status',
            width: 90,
            render: (v: string) => {
              const conf = PLAN_STATUS[v]
              return <Tag color={conf?.color ?? 'default'}>{conf?.label ?? v}</Tag>
            },
          },
          {
            title: '操作',
            key: 'action',
            width: 80,
            render: (_, r: InvestPlan) =>
              r.status === 'ACTIVE' ? (
                <Popconfirm title="确认取消该计划？" onConfirm={() => void cancel(r.planNo)}>
                  <Button size="small" danger>
                    取消
                  </Button>
                </Popconfirm>
              ) : (
                <Text type="secondary">-</Text>
              ),
          },
        ]}
      />

      <Modal
        title="新建投资计划"
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={submitting}
        okText="创建"
      >
        <Form form={form} layout="vertical" onFinish={create} style={{ marginTop: 16 }}>
          <Form.Item name="productCode" label="产品代码" rules={[{ required: true, message: '请输入产品代码' }]}>
            <Input placeholder="如 P-PR-01" />
          </Form.Item>
          <Form.Item name="planType" label="计划类型" initialValue="RESERVE" rules={[{ required: true }]}>
            <Radio.Group
              options={[
                { value: 'RESERVE', label: '预约申购（指定日期一次性买入）' },
                { value: 'REGULAR_INVEST', label: '定投（周期性自动买入）' },
              ]}
            />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(p, c) => p.planType !== c.planType}>
            {({ getFieldValue }) =>
              getFieldValue('planType') === 'REGULAR_INVEST' ? (
                <Form.Item name="periodType" label="定投周期" rules={[{ required: true, message: '定投需选择周期' }]}>
                  <Select
                    options={[
                      { value: 'DAY', label: '每日' },
                      { value: 'WEEK', label: '每周' },
                      { value: 'MONTH', label: '每月' },
                    ]}
                  />
                </Form.Item>
              ) : null
            }
          </Form.Item>
          <Form.Item
            name="amount"
            label="每期金额（元）"
            rules={[{ required: true, message: '请输入金额' }]}
          >
            <InputNumber style={{ width: '100%' }} min={1} step={1000} precision={2} />
          </Form.Item>
          <Form.Item
            name="triggerDate"
            label={form.getFieldValue('planType') === 'REGULAR_INVEST' ? '首期扣款日' : '预约申购日'}
            rules={[{ required: true, message: '请选择日期' }]}
          >
            <Input type="date" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}

/** 分红方式设置：走 TA 报文（CASH/REINVEST） */
function DividendTab() {
  const [settings, setSettings] = useState<DividendSetting[]>([])
  const [products, setProducts] = useState<ProductSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm<{ productCode: string; dividendType: 'CASH' | 'REINVEST' }>()

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [s, p] = await Promise.all([
        api.get<DividendSetting[]>('/api/customer/dividend-settings'),
        api.get<{ products: ProductSummary[] }>('/api/customer/products'),
      ])
      setSettings(s)
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

  const set = async (values: { productCode: string; dividendType: string }) => {
    setSubmitting(true)
    try {
      const res = await api.put<DividendSettingResult>('/api/customer/dividend-setting', values)
      Modal.success({
        title: '分红方式设置成功',
        content: `${values.productCode} → ${values.dividendType === 'CASH' ? '现金分红' : '红利再投资'}（${res.taMessage}，TA 流水号 ${res.taSerialNo}）`,
      })
      form.resetFields()
      void load()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '设置失败')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Alert
        type="info"
        showIcon
        title="分红方式经 TA 报文登记后生效：自营产品走本行 TA 同步确认，代销产品走外部 TA 受理。"
      />
      <Form form={form} layout="inline" onFinish={set}>
        <Form.Item name="productCode" rules={[{ required: true, message: '请选择产品' }]}>
          <Select
            showSearch
            optionFilterProp="label"
            placeholder="选择产品"
            style={{ width: 260 }}
            options={products.map((p) => ({
              value: p.productCode,
              label: `${p.productName}（${p.productCode}）`,
            }))}
          />
        </Form.Item>
        <Form.Item name="dividendType" initialValue="CASH" rules={[{ required: true }]}>
          <Radio.Group
            options={[
              { value: 'CASH', label: '现金分红' },
              { value: 'REINVEST', label: '红利再投资' },
            ]}
          />
        </Form.Item>
        <Button type="primary" htmlType="submit" loading={submitting}>
          提交设置
        </Button>
      </Form>

      <Table
        rowKey="productCode"
        size="small"
        loading={loading}
        dataSource={settings}
        pagination={false}
        locale={{ emptyText: '暂无分红方式设置' }}
        columns={[
          { title: '产品代码', dataIndex: 'productCode', width: 140 },
          {
            title: '分红方式',
            dataIndex: 'dividendType',
            width: 140,
            render: (v: string) => (v === 'CASH' ? '现金分红' : '红利再投资'),
          },
          {
            title: '更新时间',
            dataIndex: 'updatedAt',
            render: (v: string) => v?.replace('T', ' ').slice(0, 19),
          },
        ]}
      />
    </Space>
  )
}
