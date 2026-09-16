import { useCallback, useEffect, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Modal,
  Popconfirm,
  Radio,
  Row,
  Space,
  Spin,
  Tag,
  Typography,
  message,
} from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api } from '../../services/api'
import type { AccountResponse, CustomerMe } from '../../services/types'

const { Text, Title } = Typography

interface AssessmentInfo {
  riskLevel: string
  score: number
  validFrom: string
  validTo: string
  expired: boolean
}

/** 5 题快速测评问卷（答案 A-D，如 "A,B,C,D,A"；A=保守 B=稳健 C=平衡 D=进取） */
const QUESTIONS = [
  '您的年龄范围是？',
  '您的投资经验年限？',
  '您可接受的最大年度亏损幅度？',
  '您的投资目标偏好？',
  '家庭收入中可用于投资的比例？',
]
const OPTIONS: Record<string, string> = { A: 'A', B: 'B', C: 'C', D: 'D' }

/** 账户中心：账户签约 / 实名 / 风险测评（含 5 题问卷）/ 销户 */
export function ProfilePage() {
  const [me, setMe] = useState<CustomerMe | null>(null)
  const [account, setAccount] = useState<AccountResponse | null>(null)
  const [assessment, setAssessment] = useState<AssessmentInfo | null>(null)
  const [loading, setLoading] = useState(true)
  const [quizOpen, setQuizOpen] = useState(false)
  const [answers, setAnswers] = useState<(keyof typeof OPTIONS)[]>([])

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [m, acc] = await Promise.all([
        api.get<CustomerMe>('/api/customer/me'),
        api.get<AccountResponse>('/api/customer/account'),
      ])
      setMe(m)
      setAccount(acc)
      // 测评可能 404（从未测评）
      const a = await api.get<AssessmentInfo>('/api/customer/assessment').catch(() => null)
      setAssessment(a)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const sign = async () => {
    try {
      const res = await api.post<AccountResponse>('/api/customer/account/sign')
      message.success('财富账户签约成功（自营 + 代销交易权限已开通）')
      setAccount(res)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '签约失败')
    }
  }

  const terminate = async () => {
    try {
      const res = await api.post<AccountResponse>('/api/customer/account/terminate')
      message.success('已解约')
      setAccount(res)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '解约失败')
    }
  }

  const verifyRealName = async () => {
    try {
      const res = await api.post<CustomerMe>('/api/customer/real-name/verify')
      message.success('实名认证通过（Mock 人脸+活体）')
      setMe(res)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '认证失败')
    }
  }

  const submitQuiz = async () => {
    if (answers.length < QUESTIONS.length || answers.some((a) => !a)) {
      message.warning('请完成全部 5 题作答')
      return
    }
    try {
      const res = await api.post<AssessmentInfo>('/api/customer/assessment', {
        answers: answers.join(','),
      })
      message.success(`测评完成：${res.riskLevel}（有效期至 ${res.validTo.slice(0, 10)}）`)
      setAssessment(res)
      setQuizOpen(false)
      setAnswers([])
    } catch (e) {
      message.error(e instanceof Error ? e.message : '提交失败')
    }
  }

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 80 }}>
        <Spin size="large" />
      </div>
    )
  }

  const active = account?.status === 'ACTIVE'

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Title level={4} style={{ margin: 0 }}>
          账户中心
        </Title>
        <Button icon={<ReloadOutlined />} onClick={() => void load()}>
          刷新
        </Button>
      </Space>

      <Row gutter={[16, 16]}>
        {/* 基本信息 + 实名 */}
        <Col xs={24} lg={12}>
          <Card title="基本信息">
            <Descriptions column={1} size="small">
              <Descriptions.Item label="客户号">
                <Text code>{me?.customerNo}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="姓名">{me?.name}</Descriptions.Item>
              <Descriptions.Item label="手机号">{me?.mobile}</Descriptions.Item>
              <Descriptions.Item label="实名状态">
                {me?.realNameStatus === 'VERIFIED' ? (
                  <Tag color="success">已实名</Tag>
                ) : (
                  <Space>
                    <Tag color="warning">未实名</Tag>
                    <Button size="small" onClick={() => void verifyRealName()}>
                      Mock 人脸认证
                    </Button>
                  </Space>
                )}
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        {/* 财富账户签约 */}
        <Col xs={24} lg={12}>
          <Card
            title="财富账户"
            extra={
              active ? (
                <Popconfirm
                  title="确认解约财富账户？"
                  description="解约后将无法进行申赎交易，在途订单仍会正常完成。"
                  onConfirm={() => void terminate()}
                >
                  <Button size="small" danger>
                    销户
                  </Button>
                </Popconfirm>
              ) : (
                <Button size="small" type="primary" onClick={() => void sign()}>
                  签约开户
                </Button>
              )
            }
          >
            {active ? (
              <Descriptions column={1} size="small">
                <Descriptions.Item label="账户状态">
                  <Tag color="success">已签约</Tag>
                </Descriptions.Item>
                <Descriptions.Item label="账户号">
                  <Text code>{account?.accountNo}</Text>
                </Descriptions.Item>
                <Descriptions.Item label="签约时间">
                  {account?.signedAt?.replace('T', ' ').slice(0, 19)}
                </Descriptions.Item>
                <Descriptions.Item label="交易权限">
                  <Space wrap>
                    {(account?.permissions ?? []).map((p) => (
                      <Tag key={p.type} color={p.status === 'ACTIVE' ? 'blue' : 'default'}>
                        {p.type === 'PROPRIETARY' ? '自营交易' : '代销交易'}
                      </Tag>
                    ))}
                  </Space>
                </Descriptions.Item>
              </Descriptions>
            ) : (
              <Alert
                type="warning"
                showIcon
                title="尚未开通财富账户"
                description="开通后将同时获得自营与代销产品交易权限，申购前需完成签约。"
              />
            )}
          </Card>
        </Col>

        {/* 风险测评 */}
        <Col xs={24}>
          <Card
            title="风险测评"
            extra={
              <Button type="primary" size="small" onClick={() => setQuizOpen(true)}>
                {assessment ? '重新测评' : '开始测评'}
              </Button>
            }
          >
            {assessment ? (
              <Descriptions column={{ xs: 1, md: 2 }} size="small">
                <Descriptions.Item label="当前等级">
                  <Tag
                    color={
                      { R1: 'green', R2: 'cyan', R3: 'blue', R4: 'orange', R5: 'red' }[assessment.riskLevel] ??
                      'default'
                    }
                  >
                    {assessment.riskLevel}
                  </Tag>
                  {assessment.expired && <Tag color="error">已过期</Tag>}
                </Descriptions.Item>
                <Descriptions.Item label="得分">{assessment.score}</Descriptions.Item>
                <Descriptions.Item label="有效期">
                  {assessment.validFrom.slice(0, 10)} ~ {assessment.validTo.slice(0, 10)}
                </Descriptions.Item>
                <Descriptions.Item label="状态">
                  {assessment.expired ? (
                    <Text type="danger">已过期，交易前需重新测评</Text>
                  ) : (
                    <Text type="success">有效期内</Text>
                  )}
                </Descriptions.Item>
              </Descriptions>
            ) : (
              <Alert
                type="info"
                showIcon
                title="尚未完成风险测评"
                description="风险测评结果是购买理财产品的适当性校验依据（有效期 1 年）。"
              />
            )}
          </Card>
        </Col>
      </Row>

      {/* 测评问卷弹窗 */}
      <Modal
        title="风险测评问卷"
        open={quizOpen}
        onCancel={() => setQuizOpen(false)}
        onOk={() => void submitQuiz()}
        okText="提交测评"
        width={560}
      >
        <Alert
          type="info"
          showIcon
          style={{ margin: '12px 0' }}
          title="请如实作答，测评结果将作为适当性匹配依据（A 偏保守 → D 偏进取）。"
        />
        {QUESTIONS.map((q, i) => (
          <div key={i} style={{ marginBottom: 16 }}>
            <Text strong>
              {i + 1}. {q}
            </Text>
            <Radio.Group
              style={{ display: 'block', marginTop: 8 }}
              value={answers[i]}
              onChange={(e) => {
                const next = [...answers]
                next[i] = e.target.value
                setAnswers(next)
              }}
            >
              <Radio.Button value="A">A</Radio.Button>
              <Radio.Button value="B">B</Radio.Button>
              <Radio.Button value="C">C</Radio.Button>
              <Radio.Button value="D">D</Radio.Button>
            </Radio.Group>
          </div>
        ))}
      </Modal>
    </Space>
  )
}
