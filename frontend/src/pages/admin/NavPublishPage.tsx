import { useCallback, useEffect, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Col,
  DatePicker,
  Form,
  InputNumber,
  Result,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import { api } from '../../services/api'
import type { NavPublishResult, ProductSummary } from '../../services/types'
import { fmtNav } from '../../components/common/dicts'

const { Text, Title } = Typography

/** 净值发布：仅自营产品；每产品每估值日唯一（append-only）；发布后推送持有人 NAV 站内消息 */
export function NavPublishPage() {
  const [products, setProducts] = useState<ProductSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<NavPublishResult | null>(null)
  const [form] = Form.useForm<{ productCode: string; navDate: Dayjs; nav: number }>()

  const load = useCallback(async () => {
    setLoading(true)
    try {
      // 仅自营产品可发布净值（管理端独立端点，双身份隔离）
      const res = await api.get<{ products: ProductSummary[] }>(
        '/api/admin/products?productType=PROPRIETARY',
      )
      setProducts(res.products)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const publish = async (values: { productCode: string; navDate: Dayjs; nav: number }) => {
    setSubmitting(true)
    try {
      const res = await api.post<NavPublishResult>('/api/admin/nav/publish', {
        productCode: values.productCode,
        navDate: values.navDate.format('YYYY-MM-DD'),
        nav: values.nav,
      })
      setResult(res)
      message.success('净值发布成功，已向持有人推送站内消息')
      form.resetFields()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '发布失败')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Title level={4} style={{ margin: 0 }}>
          自营净值发布
        </Title>
        <Button icon={<ReloadOutlined />} onClick={() => void load()}>
          刷新产品
        </Button>
      </Space>

      <Alert
        type="info"
        showIcon
        title="仅自营产品可由管理端发布净值（代销净值由外部 TA 同步）；同一产品同一估值日只能发布一次；发布成功后自动向持有人推送 NAV 站内消息。"
      />

      <Row gutter={16}>
        <Col xs={24} lg={12}>
          <Card title="发布净值" loading={loading}>
            <Form
              form={form}
              layout="vertical"
              onFinish={publish}
              initialValues={{ navDate: dayjs() }}
            >
              <Form.Item
                name="productCode"
                label="自营产品"
                rules={[{ required: true, message: '请选择产品' }]}
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder="选择自营产品"
                  options={products.map((p) => ({
                    value: p.productCode,
                    label: `${p.productName}（${p.productCode}，最新净值 ${fmtNav(p.latestNav)}）`,
                  }))}
                />
              </Form.Item>
              <Form.Item
                name="navDate"
                label="估值日"
                rules={[{ required: true, message: '请选择估值日' }]}
              >
                <DatePicker style={{ width: '100%' }} allowClear={false} disabledDate={(d) => d.isAfter(dayjs())} />
              </Form.Item>
              <Form.Item
                name="nav"
                label="单位净值（元）"
                rules={[
                  { required: true, message: '请输入净值' },
                  {
                    validator: (_, v: number) =>
                      v > 0 ? Promise.resolve() : Promise.reject(new Error('净值必须大于 0')),
                  },
                ]}
              >
                <InputNumber style={{ width: '100%' }} min={0.0001} step={0.001} precision={4} placeholder="如 1.0523" />
              </Form.Item>
              <Button type="primary" htmlType="submit" loading={submitting} block size="large">
                发布
              </Button>
            </Form>
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          {result ? (
            <Card title="最近发布结果">
              <Result
                status="success"
                title={`${result.productCode} · ${result.navDate}`}
                subTitle={`单位净值 ${fmtNav(result.nav)} · 来源 ${result.source}`}
                extra={
                  <Text type="secondary">
                    发布时间 {result.publishedAt?.replace('T', ' ').slice(0, 19)}，持有人 NAV 消息已推送
                  </Text>
                }
              />
            </Card>
          ) : (
            <Card title="当前自营产品净值" loading={loading} size="small">
              <Table
                rowKey="productCode"
                size="small"
                dataSource={products}
                pagination={false}
                columns={[
                  { title: '产品代码', dataIndex: 'productCode', width: 120 },
                  { title: '产品名称', dataIndex: 'productName', ellipsis: true },
                  {
                    title: '最新净值',
                    dataIndex: 'latestNav',
                    width: 90,
                    align: 'right',
                    render: (v: number | null) => fmtNav(v),
                  },
                  {
                    title: '估值日',
                    dataIndex: 'latestNavDate',
                    width: 110,
                    render: (v: string | null) => v ?? '-',
                  },
                ]}
              />
            </Card>
          )}
        </Col>
      </Row>

      <Tag color="geekblue">INTERNAL</Tag>
      <Text type="secondary">本页发布的净值统一标记为 INTERNAL 来源，C 端估值与详情走势图实时生效。</Text>
    </Space>
  )
}
