import { useState } from 'react'
import {
  Alert,
  Avatar,
  Button,
  Card,
  Input,
  Space,
  Spin,
  Tag,
  Typography,
  message,
} from 'antd'
import { RobotOutlined, SendOutlined, UserOutlined } from '@ant-design/icons'
import { api } from '../../services/api'
import type { AiQaResponse } from '../../services/types'

const { Text, Title, Paragraph } = Typography

interface ChatItem {
  role: 'user' | 'ai'
  text: string
  meta?: AiQaResponse
}

const SUGGESTIONS = [
  '什么是理财产品净值？',
  '申购和赎回有什么区别？',
  '风险测评等级代表什么？',
  '巨额赎回是什么意思？',
]

/** AI 投教问答：LLM 优先 + FAQ 降级，回答带来源/风险声明/转人工 */
export function AiQaPage() {
  const [items, setItems] = useState<ChatItem[]>([
    {
      role: 'ai',
      text: '您好！我是您的 AI 投资助手，可以为您解答理财知识、产品要素、交易规则等问题。投资咨询不构成投资建议，仅供参考。',
    },
  ])
  const [question, setQuestion] = useState('')
  const [loading, setLoading] = useState(false)

  const ask = async (q: string) => {
    if (!q.trim()) return
    setItems((prev) => [...prev, { role: 'user', text: q }])
    setQuestion('')
    setLoading(true)
    try {
      const res = await api.post<AiQaResponse>('/api/customer/ai-qa', { question: q })
      setItems((prev) => [...prev, { role: 'ai', text: res.answer, meta: res }])
    } catch (e) {
      message.error(e instanceof Error ? e.message : '提问失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Title level={4} style={{ margin: 0 }}>
        AI 投教助手
      </Title>

      <Card style={{ maxWidth: 860, margin: '0 auto', width: '100%' }}>
        <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
          {items.map((item, i) => (
            <div
              key={i}
              style={{ display: 'flex', gap: 12, flexDirection: item.role === 'user' ? 'row-reverse' : 'row' }}
            >
              <Avatar
                icon={item.role === 'ai' ? <RobotOutlined /> : <UserOutlined />}
                style={{ background: item.role === 'ai' ? '#1677ff' : '#87d068', flexShrink: 0 }}
              />
              <div style={{ maxWidth: '78%' }}>
                <Card
                  size="small"
                  style={{
                    background: item.role === 'user' ? '#e6f4ff' : '#fafafa',
                  }}
                >
                  <Paragraph style={{ marginBottom: item.meta ? 8 : 0, whiteSpace: 'pre-wrap' }}>
                    {item.text}
                  </Paragraph>
                  {item.meta && (
                    <Space size={8} wrap>
                      {item.meta.source === 'LLM' && <Tag color="blue">AI 生成</Tag>}
                      {item.meta.source === 'FAQ' && (
                        <Tag color="green">FAQ：{item.meta.faqQuestion}</Tag>
                      )}
                      {item.meta.source === 'HUMAN' && <Tag color="orange">已转人工</Tag>}
                      {item.meta.source === 'NONE' && <Tag color="default">未命中知识库</Tag>}
                      {item.meta.needHuman && item.meta.source !== 'HUMAN' && (
                        <Tag color="orange">建议转人工</Tag>
                      )}
                      {item.meta.ticketNo && (
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          已创建工单 {item.meta.ticketNo}
                        </Text>
                      )}
                    </Space>
                  )}
                </Card>
                {item.meta && (
                  <Text
                    type="secondary"
                    style={{ fontSize: 12, display: 'block', marginTop: 4, marginLeft: 4 }}
                  >
                    {item.meta.riskDisclaimer}
                  </Text>
                )}
              </div>
            </div>
          ))}
          {loading && (
            <div style={{ display: 'flex', gap: 12 }}>
              <Avatar icon={<RobotOutlined />} style={{ background: '#1677ff' }} />
              <Spin size="small" style={{ marginTop: 8 }} />
            </div>
          )}
        </Space>
      </Card>

      <div style={{ maxWidth: 860, margin: '0 auto', width: '100%' }}>
        <Space orientation="vertical" size="small" style={{ width: '100%' }}>
          <Space size={8} wrap>
            {SUGGESTIONS.map((s) => (
              <Tag
                key={s}
                style={{ cursor: 'pointer' }}
                onClick={() => (loading ? undefined : void ask(s))}
              >
                {s}
              </Tag>
            ))}
          </Space>
          <Space.Compact style={{ width: '100%' }}>
            <Input
              size="large"
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              onPressEnter={() => void ask(question)}
              placeholder="请输入您的理财问题（投诉/账户异常等敏感问题将转人工处理）"
              maxLength={200}
            />
            <Button
              type="primary"
              size="large"
              icon={<SendOutlined />}
              loading={loading}
              onClick={() => void ask(question)}
            >
              提问
            </Button>
          </Space.Compact>
          <Alert
            type="warning"
            showIcon
            title="AI 回答仅供参考，不构成投资建议；涉及个人账户、投诉等敏感问题将自动转人工客服。"
          />
        </Space>
      </div>
    </Space>
  )
}
