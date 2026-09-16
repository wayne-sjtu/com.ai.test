import { useEffect } from 'react'
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import { onUnauthorized } from './services/api'
import { AdminGuard, CustomerGuard } from './auth/guards'
import { AdminLayout } from './components/AdminLayout'
import { CustomerLayout } from './components/CustomerLayout'
import { CustomerLoginPage } from './pages/customer/LoginPage'
import { HomePage } from './pages/customer/HomePage'
import { ShelfPage } from './pages/customer/ShelfPage'
import { ProductDetailPage } from './pages/customer/ProductDetailPage'
import { PurchasePage } from './pages/customer/PurchasePage'
import { RedeemPage } from './pages/customer/RedeemPage'
import { OrdersPage } from './pages/customer/OrdersPage'
import { PositionsPage } from './pages/customer/PositionsPage'
import { FlowsPage } from './pages/customer/FlowsPage'
import { PlansPage } from './pages/customer/PlansPage'
import { MessagesPage } from './pages/customer/MessagesPage'
import { TicketsPage } from './pages/customer/TicketsPage'
import { AiQaPage } from './pages/customer/AiQaPage'
import { ProfilePage } from './pages/customer/ProfilePage'
import { AdminLoginPage } from './pages/admin/LoginPage'
import { AdminOrdersPage } from './pages/admin/OrdersPage'
import { NavPublishPage } from './pages/admin/NavPublishPage'
import { AdminTicketsPage } from './pages/admin/TicketsPage'
import { HealthPage } from './pages/admin/HealthPage'
import { ChannelsPage } from './pages/admin/ChannelsPage'

/** 401 统一跳转：按当前分区跳对应登录页，保留当前路径供登录后回跳 */
function UnauthorizedRedirector() {
  const location = useLocation()
  const navigate = useNavigate()
  useEffect(() => {
    onUnauthorized(() => {
      const login = location.pathname.startsWith('/admin') ? '/admin/login' : '/customer/login'
      if (location.pathname !== login) {
        navigate(login, {
          replace: true,
          state: { from: location.pathname + location.search },
        })
      }
    })
  }, [location, navigate])
  return null
}

function AppRoutes() {
  return (
    <Routes>
      {/* 根路径：默认 C 端 */}
      <Route path="/" element={<Navigate to="/customer" replace />} />

      {/* ============ C 端分区（独立守卫与布局） ============ */}
      <Route path="/customer/login" element={<CustomerLoginPage />} />
      <Route path="/customer" element={<CustomerGuard />}>
        <Route element={<CustomerLayout />}>
          <Route index element={<HomePage />} />
          <Route path="shelf" element={<ShelfPage />} />
          <Route path="shelf/:id" element={<ProductDetailPage />} />
          <Route path="purchase" element={<PurchasePage />} />
          <Route path="redeem" element={<RedeemPage />} />
          <Route path="orders" element={<OrdersPage />} />
          <Route path="positions" element={<PositionsPage />} />
          <Route path="flows" element={<FlowsPage />} />
          <Route path="plans" element={<PlansPage />} />
          <Route path="messages" element={<MessagesPage />} />
          <Route path="tickets" element={<TicketsPage />} />
          <Route path="ai-qa" element={<AiQaPage />} />
          <Route path="profile" element={<ProfilePage />} />
        </Route>
      </Route>

      {/* ============ 管理端分区（独立身份体系） ============ */}
      <Route path="/admin/login" element={<AdminLoginPage />} />
      <Route path="/admin" element={<AdminGuard />}>
        <Route element={<AdminLayout />}>
          <Route index element={<AdminOrdersPage />} />
          <Route path="nav-publish" element={<NavPublishPage />} />
          <Route path="tickets" element={<AdminTicketsPage />} />
          <Route path="health" element={<HealthPage />} />
          <Route path="channels" element={<ChannelsPage />} />
        </Route>
      </Route>

      {/* 兜底 */}
      <Route path="*" element={<Navigate to="/customer" replace />} />
    </Routes>
  )
}

function App() {
  return (
    <ConfigProvider locale={zhCN}>
      <BrowserRouter>
        <UnauthorizedRedirector />
        <AppRoutes />
      </BrowserRouter>
    </ConfigProvider>
  )
}

export default App
