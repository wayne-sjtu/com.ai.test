/** 接口调用错误：携带后端 {code, message} 结构 */
export class ApiRequestError extends Error {
  readonly code?: string
  readonly reasons?: string[]
  readonly status: number

  constructor(status: number, code?: string, message?: string, reasons?: string[]) {
    super(message ?? `请求失败（HTTP ${status}）`)
    this.status = status
    this.code = code
    this.reasons = reasons
  }

  get unauthorized(): boolean {
    return this.status === 401
  }
}

/** 401 统一处理：由 App 挂载时注册（按分区跳转对应登录页，避免 api 层依赖路由） */
let unauthorizedHandler: (() => void) | null = null

export function onUnauthorized(handler: () => void): void {
  unauthorizedHandler = handler
}

/** 后端接口调用封装：统一 Session Cookie、JSON 解析与错误结构 */
async function request<T>(path: string, options?: RequestInit): Promise<T> {
  let res: Response
  try {
    res = await fetch(path, {
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      ...options,
    })
  } catch {
    // 网络层错误（断网/代理不可达）：统一包装，避免页面拿到原生 TypeError
    throw new ApiRequestError(0, 'NETWORK_ERROR', '网络错误，请检查网络连接')
  }
  // 401 先行触发统一跳转（不依赖响应体可解析）
  if (res.status === 401) {
    unauthorizedHandler?.()
  }
  const text = await res.text()
  let body: unknown = null
  if (text) {
    try {
      body = JSON.parse(text)
    } catch {
      body = null // 非 JSON 响应体（网关错误页等）不崩溃
    }
  }
  if (!res.ok) {
    const err = body as { code?: string; message?: string; reasons?: string[] } | null
    throw new ApiRequestError(
      res.status,
      err?.code,
      err?.message ?? (text || undefined),
      err?.reasons,
    )
  }
  return body as T
}

/** 文件下载（CSV 导出等）：带 Session Cookie，返回 Blob */
async function download(path: string, filename: string): Promise<void> {
  let res: Response
  try {
    res = await fetch(path, { credentials: 'same-origin' })
  } catch {
    throw new ApiRequestError(0, 'NETWORK_ERROR', '网络错误，请检查网络连接')
  }
  if (!res.ok) {
    throw new ApiRequestError(res.status)
  }
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  // 挂载到 DOM 后点击再移除（Firefox 兼容），延迟 revoke 避免过早回收
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 0)
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body ? JSON.stringify(body) : undefined }),
  put: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'PUT', body: body ? JSON.stringify(body) : undefined }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
  download,
}
