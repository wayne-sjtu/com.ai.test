import { useEffect, useState } from 'react'
import { Avatar, Dropdown, Layout, Menu, message } from 'antd'
import {
  ApartmentOutlined,
  DashboardOutlined,
  FileDoneOutlined,
  HeartOutlined,
  LogoutOutlined,
  RiseOutlined,
} from '@ant-design/icons'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { api } from '../services/api'
import type { AdminMe } from '../services/types'

const { Sider, Header, Content } = Layout

/** 管理端布局：侧边导航（订单/净值发布/工单/健康矩阵） */
export function AdminLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const [me, setMe] = useState<AdminMe | null>(null)

  useEffect(() => {
    api.get<AdminMe>('/api/admin/me').then(setMe).catch(() => undefined)
  }, [])

  const menuItems = [
    { key: '/admin', icon: <DashboardOutlined />, label: '统一订单列表' },
    { key: '/admin/nav-publish', icon: <RiseOutlined />, label: '净值发布' },
    { key: '/admin/tickets', icon: <FileDoneOutlined />, label: '工单处理' },
    { key: '/admin/channels', icon: <ApartmentOutlined />, label: '渠道维护' },
    { key: '/admin/health', icon: <HeartOutlined />, label: '健康矩阵' },
  ]

  const selectedKey =
    menuItems.filter((m) => m.key !== '/admin').find((m) => location.pathname.startsWith(m.key))?.key ?? '/admin'

  const logout = async () => {
    await api.post('/api/admin/logout').catch(() => undefined)
    message.success('已退出管理端')
    navigate('/admin/login', { replace: true })
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider theme="dark" width={200}>
        <div style={{ color: '#fff', fontSize: 16, fontWeight: 600, padding: '18px 24px' }}>
          管理端控制台
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            display: 'flex',
            justifyContent: 'flex-end',
            alignItems: 'center',
            background: '#fff',
            borderBottom: '1px solid #eee',
          }}
        >
          <Dropdown
            menu={{
              items: [
                { key: 'role', label: `角色：${me?.role ?? '-'}`, disabled: true },
                { type: 'divider' },
                { key: 'logout', icon: <LogoutOutlined />, label: '退出登录' },
              ],
              onClick: ({ key }) => {
                if (key === 'logout') void logout()
              },
            }}
          >
            <span style={{ cursor: 'pointer' }}>
              <Avatar size="small" style={{ marginRight: 6, background: '#1677ff' }}>
                {me?.username?.[0]?.toUpperCase() ?? 'A'}
              </Avatar>
              {me?.displayName ?? '...'}
            </span>
          </Dropdown>
        </Header>
        <Content style={{ padding: 24, background: '#f5f5f5' }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
