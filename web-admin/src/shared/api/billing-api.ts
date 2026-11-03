import { apiJson } from '@/shared/api/http'
import type { BillingSummary } from '@/shared/types/api.types'

/**
 * 账单草稿流转。无列表接口；不重算金额，也不改价格。
 */

/** datetime-local 常缺秒，补齐后再给 LocalDateTime。 */
function toLocalDateTime(value: string): string | null {
  const trimmed = value.trim()
  if (!trimmed) {
    return null
  }
  return trimmed.length === 16 ? `${trimmed}:00` : trimmed
}

type PeriodBody = {
  tenantId: string
  periodStart: string
  periodEnd: string
}

/** 共用租户+区间体。 */
function periodPayload(body: PeriodBody) {
  return {
    tenantId: body.tenantId,
    periodStart: toLocalDateTime(body.periodStart),
    periodEnd: toLocalDateTime(body.periodEnd),
  }
}

/** 生成 DRAFT；同一区间冲突由服务端返回。 */
export function createBillingDraft(body: PeriodBody): Promise<BillingSummary> {
  return apiJson<BillingSummary>('/api/admin/billing/drafts', {
    method: 'POST',
    body: JSON.stringify(periodPayload(body)),
  })
}

/** 草稿 → CONFIRMED。不重算金额。 */
export function confirmBilling(body: PeriodBody): Promise<BillingSummary> {
  return apiJson<BillingSummary>('/api/admin/billing/confirmations', {
    method: 'POST',
    body: JSON.stringify(periodPayload(body)),
  })
}

/** 已确认 → INVOICED。 */
export function invoiceBilling(body: PeriodBody): Promise<BillingSummary> {
  return apiJson<BillingSummary>('/api/admin/billing/invoices', {
    method: 'POST',
    body: JSON.stringify(periodPayload(body)),
  })
}

/** 已开票 → PAID。 */
export function payBilling(body: PeriodBody): Promise<BillingSummary> {
  return apiJson<BillingSummary>('/api/admin/billing/payments', {
    method: 'POST',
    body: JSON.stringify(periodPayload(body)),
  })
}
