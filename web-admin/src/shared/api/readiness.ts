/**
 * 管理进程自己的就绪。不带管理令牌，也不查用户进程。
 * 200 和 503 都有正文：status 加上 mysql / redis / milvus。
 */

export interface ReadinessCheck {
  name: string
  state: string
}

export interface AdminReadiness {
  /** UP 或 DOWN。请求失败时由调用方自己处理。 */
  status: string
  checks: ReadinessCheck[]
}

/** 拉本进程就绪。503 仍解析正文，用来标出失败项。 */
export async function loadAdminReadiness(): Promise<AdminReadiness> {
  const response = await fetch('/health/ready')
  const raw = await response.text()
  if (!raw) {
    throw new Error(`就绪检查没有正文（HTTP ${response.status}）`)
  }
  let body: Record<string, unknown>
  try {
    body = JSON.parse(raw) as Record<string, unknown>
  } catch {
    throw new Error(`就绪检查不是 JSON（HTTP ${response.status}）`)
  }
  return parseReadiness(body, response.ok)
}

/** 从平铺的 JSON 里拆出总状态和各项。status 以外的键都是检查项。 */
export function parseReadiness(body: Record<string, unknown>, ok: boolean): AdminReadiness {
  const status = typeof body.status === 'string' && body.status ? body.status : ok ? 'UP' : 'DOWN'
  const checks: ReadinessCheck[] = []
  for (const [name, value] of Object.entries(body)) {
    if (name === 'status' || typeof value !== 'string') {
      continue
    }
    checks.push({ name, state: value })
  }
  return { status, checks }
}
