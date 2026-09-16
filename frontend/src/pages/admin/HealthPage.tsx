import { useCallback, useEffect, useState } from 'react'
import { Alert, Badge, Button, Card, Col, Row, Space, Spin, Tag, Typography, message } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api } from '../../services/api'
import type { HealthMatrix } from '../../services/types'

const { Text, Title } = Typography

/** 故障模式说明（外部 TA 差错矩阵演示） */
const FAULT_MODES: Record<string, { color: string; label: string; desc: string }> = {
  NORMAL: { color: 'green', label: '正常', desc: '外部 TA 正常受理与确认' },
  TIMEOUT: { color: 'red', label: '超时', desc: '模拟外部 TA 超时：订单受理失败' },
  REJECT: { color: 'volcano', label: '拒绝', desc: '模拟外部 TA 拒绝：订单受理被拒' },
  DELAY_CONFIRM: { color: 'orange', label: '延迟确认', desc: '模拟外部 TA 延迟确认：订单长时间停留 TA_ACCEPTED' },
}

/** 健康矩阵：DB / 本行 TA（INTA）/ 外部 TA（EXTA）+ 外部 TA 故障注入模式展示 */
export function HealthPage() {
  const [matrix, setMatrix] = useState<HealthMatrix | null>(null)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setMatrix(await api.get<HealthMatrix>('/api/admin/health/matrix'))
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Title level={4} style={{ margin: 0 }}>
          组件健康矩阵
        </Title>
        <Space>
          <Button
            onClick={() => void window.open('/actuator/health', '_blank')}
          >
            Actuator /health
          </Button>
          <Button icon={<ReloadOutlined />} onClick={() => void load()}>
            刷新
          </Button>
        </Space>
      </Space>

      {loading && !matrix ? (
        <div style={{ textAlign: 'center', padding: 80 }}>
          <Spin size="large" />
        </div>
      ) : matrix ? (
        <>
          <Alert
            type={matrix.overall === 'UP' ? 'success' : 'error'}
            showIcon
            title={
              matrix.overall === 'UP'
                ? '全部组件运行正常'
                : '存在异常组件，请检查下方矩阵详情'
            }
            description={`最近检查：${matrix.checkedAt?.replace('T', ' ').slice(0, 19)}`}
          />

          <Row gutter={[16, 16]}>
            {matrix.components.map((c) => {
              const isExternalTa = c.name === 'EXTERNAL_TA'
              const fault = c.faultMode ? FAULT_MODES[c.faultMode] : undefined
              return (
                <Col key={c.name} xs={24} md={8}>
                  <Card
                    title={c.label}
                    extra={
                      <Badge
                        status={c.status === 'UP' ? 'success' : 'error'}
                        text={c.status === 'UP' ? 'UP' : 'DOWN'}
                      />
                    }
                  >
                    <Space orientation="vertical" size="small" style={{ width: '100%' }}>
                      <Text type="secondary">组件标识：{c.name}</Text>
                      <Text>检查详情：{c.detail}</Text>
                      {isExternalTa && fault && (
                        <>
                          <div style={{ marginTop: 4 }}>
                            <Tag color={fault.color}>故障模式：{fault.label}（{c.faultMode}）</Tag>
                          </div>
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            {fault.desc}
                          </Text>
                        </>
                      )}
                    </Space>
                  </Card>
                </Col>
              )
            })}
          </Row>

          <Card size="small" title="说明">
            <Text type="secondary">
              本页为自定义组件状态矩阵（spec 用户故事 34）；Docker-Compose 部署时容器健康检查使用
              <Text code> /actuator/health </Text>
              标准端点。外部 TA 故障注入开关用于演示差错处理矩阵：可通过
              <Text code> ExternalTaClient.setFaultMode </Text>
              切换 TIMEOUT / REJECT / DELAY_CONFIRM 场景，观察订单差错处理表现。
            </Text>
          </Card>
        </>
      ) : null}
    </Space>
  )
}
