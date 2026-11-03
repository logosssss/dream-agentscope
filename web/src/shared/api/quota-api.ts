import { apiJson } from '@/shared/api/http'
import type { QuotaSnapshot } from '@/shared/types/api.types'

/**
 * 当前用户当日额度。只读，不扣次数，也不写流水。
 */

/** 请求、token、费用和同时运行数。Redis 不可用时由接口返回不可用。 */
export function getQuota(): Promise<QuotaSnapshot> {
  return apiJson<QuotaSnapshot>('/api/quota')
}
