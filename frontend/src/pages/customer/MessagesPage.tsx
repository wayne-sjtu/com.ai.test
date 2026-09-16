import { useCallback, useEffect, useState } from 'react'
import { Badge, Button, Card, Listy, Segmented, Space, Tag, Typography, message } from 'antd'
import { CheckOutlined, ReloadOutlined } from '@ant-design/icons'
import { api } from '../../services/api'
import type { MessageItem, MessageListResponse } from '../../services/types'

const { Text, Title, Paragraph } = Typography

const MSG_TYPE: Record<string, { color: string; label: string }> = {
  NAV: { color: 'blue', label: '净值更新' },
  DEAL: { color: 'green', label: '成交通知' },
  EXPIRY: { color: 'orange', label: '到期提醒' },
  ANNOUNCEMENT: { color: 'purple', label: '公告' },
  TICKET_PROGRESS: { color: 'cyan', label: '工单进度' },
}

/** 消息中心：类型筛选 + 未读过滤 + 单条/全部已读 */
export function MessagesPage() {
  const [messages, setMessages] = useState<MessageItem[]>([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState<{ msgType?: string; unread?: string }>({})

  const load = useCallback(async (f: typeof filter) => {
    setLoading(true)
    try {
      const params = new URLSearchParams()
      if (f.msgType) params.set('msgType', f.msgType)
      if (f.unread) params.set('unread', 'true')
      const res = await api.get<MessageListResponse>(`/api/customer/messages?${params.toString()}`)
      setMessages(res.messages)
      setUnreadCount(res.unreadCount)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load(filter)
  }, [filter, load])

  const markRead = async (id: number) => {
    try {
      await api.post(`/api/customer/messages/${id}/read`)
      void load(filter)
    } catch {
      message.error('操作失败')
    }
  }

  const markAllRead = async () => {
    try {
      const res = await api.post<{ markedRead: number }>('/api/customer/messages/read-all')
      message.success(`已将 ${res.markedRead} 条消息标记为已读`)
      void load(filter)
    } catch {
      message.error('操作失败')
    }
  }

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Title level={4} style={{ margin: 0 }}>
          消息中心
          {unreadCount > 0 && (
            <Badge count={unreadCount} style={{ marginLeft: 12 }} title={`${unreadCount} 条未读`} />
          )}
        </Title>
        <Space>
          <Button icon={<CheckOutlined />} onClick={() => void markAllRead()} disabled={unreadCount === 0}>
            全部已读
          </Button>
          <Button icon={<ReloadOutlined />} onClick={() => void load(filter)}>
            刷新
          </Button>
        </Space>
      </Space>

      <Segmented
        options={[
          { value: 'all', label: '全部' },
          { value: 'unread', label: `未读（${unreadCount}）` },
          ...Object.entries(MSG_TYPE).map(([value, conf]) => ({
            value: `type:${value}`,
            label: conf.label,
          })),
        ]}
        value={filter.unread ? 'unread' : filter.msgType ? `type:${filter.msgType}` : 'all'}
        onChange={(v) => {
          const s = v as string
          if (s === 'all') setFilter({})
          else if (s === 'unread') setFilter({ unread: 'true' })
          else setFilter({ msgType: s.slice(5) })
        }}
      />

      <Card loading={loading}>
        {messages.length === 0 ? (
          <Text type="secondary">暂无消息</Text>
        ) : (
          <Listy
            items={messages}
            rowKey={(m) => m.id}
            itemRender={(m) => {
              const conf = MSG_TYPE[m.msgType]
              return (
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'flex-start',
                    justifyContent: 'space-between',
                    gap: 12,
                    padding: '12px 0',
                    borderBottom: '1px solid #f0f0f0',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12, minWidth: 0 }}>
                    <Badge dot={!m.readFlag} offset={[-4, 4]}>
                      <Tag color={conf?.color ?? 'default'}>{conf?.label ?? m.msgType}</Tag>
                    </Badge>
                    <div style={{ minWidth: 0 }}>
                      <Text strong={!m.readFlag}>
                        {m.title}
                        <Text type="secondary" style={{ fontWeight: 'normal', marginLeft: 12, fontSize: 12 }}>
                          {m.createdAt.replace('T', ' ').slice(0, 19)}
                        </Text>
                      </Text>
                      <Paragraph style={{ marginBottom: 0 }}>{m.content}</Paragraph>
                    </div>
                  </div>
                  {!m.readFlag && (
                    <Button size="small" type="link" onClick={() => void markRead(m.id)}>
                      标为已读
                    </Button>
                  )}
                </div>
              )
            }}
          />
        )}
      </Card>
    </Space>
  )
}
