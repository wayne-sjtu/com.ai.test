import { useEffect, useState } from 'react'
import { Alert, Spin } from 'antd'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { ApiRequestError, api } from '../services/api'

/**
 * C 端路由守卫：挂载时校验 Session（GET /api/customer/me），
 * 未登录携带当前路径跳转登录页（登录后回跳）。
 */
export function CustomerGuard() {
  return <Guard mePath="/api/customer/me" loginPath="/customer/login" />
}

/** 管理端路由守卫：独立身份体系（GET /api/admin/me） */
export function AdminGuard() {
  return <Guard mePath="/api/admin/me" loginPath="/admin/login" />
}

function Guard({ mePath, loginPath }: { mePath: string; loginPath: string }) {
  const location = useLocation()
  const [state, setState] = useState<'checking' | 'ok' | 'denied' | 'error'>('checking')
  const [error, setError] = useState('')

  useEffect(() => {
    api
      .get(mePath)
      .then(() => setState('ok'))
      .catch((e) => {
        // 仅 401（未登录/会话失效）跳转登录页；网络/服务器错误不误跳
        if (e instanceof ApiRequestError && e.unauthorized) {
          setState('denied')
        } else {
          setError(e instanceof Error ? e.message : '网络错误')
          setState('error')
        }
      })
  }, [mePath])

  if (state === 'denied') {
    return <Navigate to={loginPath} replace state={{ from: location.pathname + location.search }} />
  }
  if (state === 'checking') {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: '120px 0' }}>
        <Spin size="large" />
      </div>
    )
  }
  if (state === 'error') {
    return (
      <Alert
        type="error"
        showIcon
        title="会话校验失败"
        description={`${error}，请稍后刷新重试。`}
        style={{ maxWidth: 480, margin: '120px auto' }}
      />
    )
  }
  return <Outlet />
}
