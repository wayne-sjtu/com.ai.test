import { useState } from 'react'
import { Button, Card, Form, Input, message } from 'antd'
import { LockOutlined, MobileOutlined } from '@ant-design/icons'
import { useLocation, useNavigate } from 'react-router-dom'
import { api } from '../../services/api'
import type { CustomerMe } from '../../services/types'

/** C 端登录：mobile + password，Session Cookie 认证（channelCode 固定 PC_WEB） */
export function CustomerLoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const [loading, setLoading] = useState(false)

  const onFinish = async (values: { mobile: string; password: string }) => {
    setLoading(true)
    try {
      await api.post<CustomerMe>('/api/customer/login', {
        ...values,
        channelCode: 'PC_WEB',
      })
      message.success('登录成功')
      const from = (location.state as { from?: string } | null)?.from
      navigate(from ?? '/customer', { replace: true })
    } catch (err) {
      message.error(err instanceof Error ? err.message : '登录失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        background: 'linear-gradient(135deg, #1677ff22, #f5f5f5)',
      }}
    >
      <Card style={{ width: 380 }} title="智惠理财 · 客户登录">
        <Form onFinish={onFinish} size="large">
          <Form.Item name="mobile" rules={[{ required: true, message: '请输入手机号' }]}>
            <Input prefix={<MobileOutlined />} placeholder="手机号（种子：13800000001）" />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="密码（种子：Passw0rd!）" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={loading}>
            登录
          </Button>
        </Form>
      </Card>
    </div>
  )
}
