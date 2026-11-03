<script setup lang="ts">
/**
 * 账单草稿流转：生成 → 确认 → 开票 → 付款。无列表接口，下方为本页会话结果。
 */
import {
  confirmBilling,
  createBillingDraft,
  invoiceBilling,
  payBilling,
} from '@/shared/api/billing-api'
import type { BillingSummary } from '@/shared/types/api.types'
import { ref } from 'vue'

type Action = 'draft' | 'confirm' | 'invoice' | 'pay'

const recent = ref<BillingSummary[]>([])
const error = ref('')
const submitting = ref(false)

const tenantId = ref('')
const periodStart = ref('')
const periodEnd = ref('')

/** 把最新结果顶到本页会话列表。 */
function pushResult(row: BillingSummary) {
  const key = `${row.tenantId}|${row.periodStart}|${row.periodEnd}`
  recent.value = [row, ...recent.value.filter((r) => `${r.tenantId}|${r.periodStart}|${r.periodEnd}` !== key)]
}

/** 按动作提交同一租户区间。 */
async function onAction(action: Action) {
  const tid = tenantId.value.trim()
  if (!tid || !periodStart.value.trim() || !periodEnd.value.trim() || submitting.value) {
    return
  }
  submitting.value = true
  error.value = ''
  const body = {
    tenantId: tid,
    periodStart: periodStart.value,
    periodEnd: periodEnd.value,
  }
  try {
    let row: BillingSummary
    if (action === 'draft') {
      row = await createBillingDraft(body)
    } else if (action === 'confirm') {
      row = await confirmBilling(body)
    } else if (action === 'invoice') {
      row = await invoiceBilling(body)
    } else {
      row = await payBilling(body)
    }
    pushResult(row)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '操作失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="space-y-4 max-w-5xl">
    <div class="flex items-center justify-between gap-3">
      <h2 class="text-lg font-semibold">账单</h2>
    </div>

    <p class="text-xs text-[var(--color-text-muted)]">
      按租户与区间流转：DRAFT → CONFIRMED → INVOICED → PAID。不重算金额；区间终点不含该时刻，跨度不超过 366 天。服务端无账单列表，下方为本页会话结果。
    </p>

    <p v-if="error" class="text-sm text-[var(--color-danger)]">{{ error }}</p>

    <form class="space-y-3 bg-white border border-slate-200 rounded-lg p-3" @submit.prevent>
      <div class="flex flex-wrap gap-2 items-end">
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">租户 ID</span>
          <input
            v-model="tenantId"
            class="block w-44 rounded-md border border-slate-300 px-2 py-1.5"
            required
          />
        </label>
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">区间起点</span>
          <input
            v-model="periodStart"
            type="datetime-local"
            class="block rounded-md border border-slate-300 px-2 py-1.5"
            required
          />
        </label>
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">区间终点</span>
          <input
            v-model="periodEnd"
            type="datetime-local"
            class="block rounded-md border border-slate-300 px-2 py-1.5"
            required
          />
        </label>
      </div>
      <div class="flex flex-wrap gap-2">
        <button
          type="button"
          class="rounded-md bg-[var(--color-brand-primary)] px-3 py-1.5 text-sm text-white disabled:opacity-50"
          :disabled="submitting"
          @click="onAction('draft')"
        >
          生成草稿
        </button>
        <button
          type="button"
          class="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50 disabled:opacity-50"
          :disabled="submitting"
          @click="onAction('confirm')"
        >
          确认
        </button>
        <button
          type="button"
          class="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50 disabled:opacity-50"
          :disabled="submitting"
          @click="onAction('invoice')"
        >
          开票
        </button>
        <button
          type="button"
          class="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50 disabled:opacity-50"
          :disabled="submitting"
          @click="onAction('pay')"
        >
          付款
        </button>
      </div>
    </form>

    <div class="overflow-x-auto bg-white border border-slate-200 rounded-lg">
      <table class="w-full text-sm text-left">
        <thead class="bg-slate-50 text-[var(--color-text-muted)]">
          <tr>
            <th class="px-3 py-2 font-medium">租户</th>
            <th class="px-3 py-2 font-medium">区间</th>
            <th class="px-3 py-2 font-medium">Token</th>
            <th class="px-3 py-2 font-medium">金额</th>
            <th class="px-3 py-2 font-medium">币种</th>
            <th class="px-3 py-2 font-medium">状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="recent.length === 0">
            <td colspan="6" class="px-3 py-6 text-center text-[var(--color-text-muted)]">本页暂无账单记录</td>
          </tr>
          <tr
            v-for="row in recent"
            :key="`${row.tenantId}:${row.periodStart}:${row.periodEnd}:${row.status}`"
            class="border-t border-slate-100"
          >
            <td class="px-3 py-2 font-mono text-xs">{{ row.tenantId }}</td>
            <td class="px-3 py-2 text-xs text-[var(--color-text-muted)]">
              {{ row.periodStart }} ~ {{ row.periodEnd }}
            </td>
            <td class="px-3 py-2">{{ row.tokenTotal }}</td>
            <td class="px-3 py-2">{{ row.costAmount }}</td>
            <td class="px-3 py-2">{{ row.currency }}</td>
            <td class="px-3 py-2">{{ row.status }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
