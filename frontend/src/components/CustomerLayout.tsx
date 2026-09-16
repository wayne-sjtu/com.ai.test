import { useEffect, useState } from 'react'
import { Avatar, Badge, Dropdown, Layout, Menu, message } from 'antd'
import {
  AimOutlined,
  BookOutlined,
  CustomerServiceOutlined,
  FileTextOutlined,
  FundOutlined,
  HomeOutlined,
  LineChartOutlined,
  LogoutOutlined,
  ScheduleOutlined,
  ShopOutlined,
  SwapOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { api } from '../services/api'
import type { CustomerMe, MessageListResponse } from '../services/types'

const { Header, Content, Footer } = Layout

/** C 端布局：顶部导航 + 未读消息徽标 + 用户菜单 */
export function CustomerLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const [me, setMe] = useState<CustomerMe | null>(null)
  const [unreadCount, setUnreadCount] = useState(0)

  useEffect(() => {
    api.get<CustomerMe>('/api/customer/me').then(setMe).catch(() => undefined)
    // 未读徽标：首载 + 30s 轮询
    const loadUnread = () => {
      api
        .get<MessageListResponse>('/api/customer/messages?unread=true')
        .then((r) => setUnreadCount(r.unreadCount))
        .catch(() => undefined)
    }
    loadUnread()
    const timer = window.setInterval(loadUnread, 30_000)
    return () => window.clearInterval(timer)
  }, [])

  const menuItems = [
    { key: '/customer', icon: <HomeOutlined />, label: '首页' },
    { key: '/customer/shelf', icon: <ShopOutlined />, label: '产品货架' },
    { key: '/customer/positions', icon: <FundOutlined />, label: '我的持仓' },
    { key: '/customer/flows', icon: <SwapOutlined />, label: '资金流水' },
    { key: '/customer/orders', icon: <FileTextOutlined />, label: '我的订单' },
    { key: '/customer/plans', icon: <ScheduleOutlined />, label: '投资计划' },
    { key: '/customer/messages', icon: <Badge count={unreadCount} size="small"><AimOutlined /></Badge>, label: '消息中心' },
    { key: '/customer/tickets', icon: <CustomerServiceOutlined />, label: '客服工单' },
    { key: '/customer/ai-qa', icon: <BookOutlined />, label: 'AI 投教' },
  ]

  const selectedKey =
    menuItems
      .filter((m) => m.key !== '/customer')
      .find((m) => location.pathname.startsWith(m.key))?.key ??
    (location.pathname === '/customer' ? '/customer' : '/customer')

  const logout = async () => {
    await api.post('/api/customer/logout').catch(() => undefined)
    message.success('已退出登录')
    navigate('/customer/login', { replace: true })
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center', gap: 24 }}>
        <div
          style={{ color: '#fff', fontSize: 18, fontWeight: 600, whiteSpace: 'nowrap', cursor: 'pointer' }}
          onClick={() => navigate('/customer')}
        >
          <LineChartOutlined style={{ marginRight: 8 }} />
          智惠理财
        </div>
        <Menu
          theme="dark"
          mode="horizontal"
          selectedKeys={[selectedKey]}
          items={menuItems}
          style={{ flex: 1, minWidth: 0 }}
          onClick={({ key }) => navigate(key)}
        />
        <Dropdown
          menu={{
            items: [
              { key: 'profile', icon: <UserOutlined />, label: '账户中心' },
              { type: 'divider' },
              { key: 'logout', icon: <LogoutOutlined />, label: '退出登录' },
            ],
            onClick: ({ key }) => {
              if (key === 'logout') void logout()
              if (key === 'profile') navigate('/customer/profile')
            },
          }}
        >
          <span style={{ color: '#fff', cursor: 'pointer', whiteSpace: 'nowrap' }}>
            <Avatar size="small" icon={<UserOutlined />} style={{ marginRight: 6 }} />
            {me?.name ?? '...'}
          </span>
        </Dropdown>
      </Header>
      <Content style={{ padding: '24px 48px', maxWidth: 1280, width: '100%', margin: '0 auto' }}>
        <Outlet />
      </Content>
      <Footer style={{ textAlign: 'center', color: '#999' }}>
        理财非存款 · 产品有风险 · 投资须谨慎（AI Hackathon 演示项目）
      </Footer>
    </Layout>
  )
}
