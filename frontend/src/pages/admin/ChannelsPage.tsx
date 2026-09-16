import { useCallback, useEffect, useState } from 'react'
import { Badge, Button, Card, Popconfirm, Space, Switch, Table, Tag, Typography, message } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api } from '../../services/api'
import type { ChannelItem, ChannelListResponse } from '../../services/types'

const { Text, Title } = Typography

const CHANNEL_TYPE: Record<string, string> = {
  PC_WEB: 'PC 网页',
  APP: '手机 App',
  H5: 'H5 微站',
  MINI_PROGRAM: '小程序',
}

/** 渠道维护（spec 功能 2）：渠道类型、核心/非核心、状态登记维护；停用后该渠道登录即被拒绝 */
export function ChannelsPage() {
  const [channels, setChannels] = useState<ChannelItem[]>([])
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const res = await api.get<ChannelListResponse>('/api/admin/channels')
      setChannels(res.channels)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const update = async (channelCode: string, body: { status?: string; coreFlag?: boolean }, tip: string) => {
    try {
      await api.put(`/api/admin/channels/${channelCode}`, body)
      message.success(tip)
      void load()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '操作失败')
    }
  }

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Title level={4} style={{ margin: 0 }}>
          渠道维护
        </Title>
        <Button icon={<ReloadOutlined />} onClick={() => void load()}>
          刷新
        </Button>
      </Space>

      <Card>
        <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
          渠道数据驱动差异化：停用（SUSPENDED）后该渠道客户登录即被拒绝；核心渠道为真实实现，非核心渠道仅 Mock 登记。
        </Text>
        <Table<ChannelItem>
          rowKey="channelCode"
          loading={loading}
          dataSource={channels}
          pagination={false}
          columns={[
            {
              title: '渠道码',
              dataIndex: 'channelCode',
              width: 140,
              render: (v: string) => <Text code>{v}</Text>,
            },
            { title: '渠道名称', dataIndex: 'channelName', width: 140 },
            {
              title: '类型',
              dataIndex: 'channelType',
              width: 110,
              render: (v: string) => CHANNEL_TYPE[v] ?? v,
            },
            {
              title: '核心渠道',
              dataIndex: 'coreFlag',
              width: 110,
              render: (v: boolean) =>
                v ? <Tag color="geekblue">核心</Tag> : <Tag>非核心</Tag>,
            },
            {
              title: '客户数',
              dataIndex: 'customerCount',
              width: 90,
              align: 'right',
            },
            {
              title: '状态',
              dataIndex: 'status',
              width: 100,
              render: (v: string) =>
                v === 'ACTIVE' ? (
                  <Badge status="success" text="启用" />
                ) : (
                  <Badge status="error" text="停用" />
                ),
            },
            {
              title: '操作',
              key: 'actions',
              render: (_, c) => (
                <Space>
                  <Popconfirm
                    title={c.status === 'ACTIVE' ? '停用后该渠道客户将无法登录，确认？' : '确认启用该渠道？'}
                    onConfirm={() =>
                      void update(
                        c.channelCode,
                        { status: c.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE' },
                        c.status === 'ACTIVE' ? '已停用渠道' : '已启用渠道',
                      )
                    }
                  >
                    <Button size="small" danger={c.status === 'ACTIVE'}>
                      {c.status === 'ACTIVE' ? '停用' : '启用'}
                    </Button>
                  </Popconfirm>
                  <Switch
                    size="small"
                    checked={c.coreFlag}
                    checkedChildren="核心"
                    unCheckedChildren="非核心"
                    onChange={(v) =>
                      void update(c.channelCode, { coreFlag: v }, v ? '已标记为核心渠道' : '已取消核心标记')
                    }
                  />
                </Space>
              ),
            },
          ]}
        />
      </Card>
    </Space>
  )
}
