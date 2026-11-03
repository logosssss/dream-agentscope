import { apiJson } from '@/shared/api/http'
import type { ModelConfigSummary, ModelPriceEntry } from '@/shared/types/api.types'

/**
 * 模型配置与单价目录。配置不解析参数；单价只新增不改旧行。
 */

/** 最近模型配置。 */
export function listModelConfigs(limit = 50): Promise<ModelConfigSummary[]> {
  const q = new URLSearchParams({ limit: String(limit) })
  return apiJson<ModelConfigSummary[]>(`/api/admin/model-configs?${q}`)
}

/** 登记配置，版本由服务端固定 1.0.0。空白租户范围按全局。 */
export function registerModelConfig(body: {
  configId: string
  model: string
  provider: string
  tenantScope: string
}): Promise<ModelConfigSummary> {
  return apiJson<ModelConfigSummary>('/api/admin/model-configs', {
    method: 'POST',
    body: JSON.stringify({
      configId: body.configId,
      model: body.model,
      provider: body.provider,
      tenantScope: body.tenantScope || null,
    }),
  })
}

/** 最近模型单价。 */
export function listModelPrices(limit = 50): Promise<ModelPriceEntry[]> {
  const q = new URLSearchParams({ limit: String(limit) })
  return apiJson<ModelPriceEntry[]>(`/api/admin/model-prices?${q}`)
}

/**
 * datetime-local 常是 yyyy-MM-ddTHH:mm，补秒再给 LocalDateTime。
 * 空白仍交 null，让服务端用现在或表示仍有效。
 */
function toLocalDateTime(value: string): string | null {
  const trimmed = value.trim()
  if (!trimmed) {
    return null
  }
  return trimmed.length === 16 ? `${trimmed}:00` : trimmed
}

/** 新增一行单价。空生效开始用服务端 UTC 现在；空结束表示仍有效。 */
export function addModelPrice(body: {
  model: string
  currency: string
  inputPer1k: string
  outputPer1k: string
  effectiveFrom: string
  effectiveTo: string
}): Promise<ModelPriceEntry> {
  return apiJson<ModelPriceEntry>('/api/admin/model-prices', {
    method: 'POST',
    body: JSON.stringify({
      model: body.model,
      currency: body.currency || null,
      inputPer1k: body.inputPer1k,
      outputPer1k: body.outputPer1k,
      effectiveFrom: toLocalDateTime(body.effectiveFrom),
      effectiveTo: toLocalDateTime(body.effectiveTo),
    }),
  })
}
