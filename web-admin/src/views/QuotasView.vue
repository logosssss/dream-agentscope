<script setup lang="ts">
/**
 * 租户额度：请求 / token / 费用微单位 / 同时运行。列表、登记、改上限。
 */
import { listQuotas, registerQuota, updateQuota } from '@/shared/api/quota-api'
import { listTenants } from '@/shared/api/tenant-api'
import { hasAdminToken } from '@/shared/auth/operator'
import type { QuotaKind, TenantQuotaSummary, TenantSummary } from '@/shared/types/api.types'
import { computed, onMounted, reactive, ref, watch } from 'vue'

const TABS: { kind: QuotaKind; label: string; hint: string }[] = [
  { kind: 'requests', label: '每日请求', hint: 'REQUESTS / DAILY，0 表示不限额' },
  { kind: 'tokens', label: '每日 Token', hint: 'TOKENS / DAILY，0 表示不限额' },
  { kind: 'cost', label: '每日费用', hint: 'COST / DAILY，limit 为金额×1e6 微单位，0 不限额' },
  { kind: 'concurrent', label: '同时运行', hint: 'CONCURRENT_RUNS / CURRENT，0 表示不限额' },
]

const kind = ref<QuotaKind>('requests')
const rows = ref<TenantQuotaSummary[]>([])
const tenants = ref<TenantSummary[]>([])
const loading = ref(false)
const error = ref('')
const submitting = ref(false)
const tenantId = ref('')
const newLimit = ref('0')
/** 行内编辑中的上限草稿，键为 tenantId */
const drafts = reactive<Record<string, string>>({})

const activeTab = computed(() => TABS.find((t) => t.kind === kind.value)!)

/** 拉列表并灌草稿。 */
async function refresh() {
  if (!hasAdminToken()) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    rows.value = await listQuotas(kind.value)
    for (const row of rows.value) {
      drafts[row.tenantId] = String(row.limit)
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

/** 登记新额度。 */
async function onRegister() {
  const tid = tenantId.value.trim()
  const limit = Number(newLimit.value)
  if (!tid || Number.isNaN(limit) || limit < 0 || submitting.value) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    await registerQuota(kind.value, tid, Math.floor(limit))
    tenantId.value = ''
    newLimit.value = '0'
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '登记失败'
  } finally {
    submitting.value = false
  }
}

/** 保存一行上限。 */
async function onSave(row: TenantQuotaSummary) {
  const raw = drafts[row.tenantId]
  const limit = Number(raw)
  if (Number.isNaN(limit) || limit < 0 || submitting.value) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    await updateQuota(kind.value, row.tenantId, Math.floor(limit))
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '保存失败'
  } finally {
    submitting.value = false
  }
}

watch(kind, () => {
  void refresh()
})

onMounted(async () => {
  if (hasAdminToken()) {
    try {
      tenants.value = await listTenants(100)
    } catch {
      // 下拉失败不挡主列表
    }
  }
  await refresh()
})
</script>

<template>
  <div class="space-y-4 max-w-5xl">
    <div class="flex items-center justify-between gap-3">
      <h2 class="text-lg font-semibold">额度</h2>
      <button
        type="button"
        class="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50"
        :disabled="loading"
        @click="refresh"
      >
        刷新
      </button>
    </div>

    <div class="flex flex-wrap gap-1 border-b border-slate-200 pb-2">
      <button
        v-for="tab in TABS"
        :key="tab.kind"
        type="button"
        class="rounded-md px-3 py-1.5 text-sm"
        :class="kind === tab.kind
          ? 'bg-blue-50 text-[var(--color-brand-primary)] font-medium'
          : 'text-[var(--color-text-muted)] hover:bg-slate-50'"
        @click="kind = tab.kind"
      >
        {{ tab.label }}
      </button>
    </div>
    <p class="text-xs text-[var(--color-text-muted)]">{{ activeTab.hint }}</p>

    <form
      class="flex flex-wrap gap-2 items-end bg-white border border-slate-200 rounded-lg p-3"
      @submit.prevent="onRegister"
    >
      <label class="text-sm space-y-1">
        <span class="text-[var(--color-text-muted)]">租户</span>
        <select v-model="tenantId" class="block w-56 rounded-md border border-slate-300 px-2 py-1.5" required>
          <option value="">请选择</option>
          <option v-for="t in tenants" :key="t.tenantId" :value="t.tenantId">
            {{ t.tenantId }}（{{ t.name }}）
          </option>
        </select>
      </label>
      <label class="text-sm space-y-1">
        <span class="text-[var(--color-text-muted)]">上限</span>
        <input
          v-model="newLimit"
          type="number"
          min="0"
          step="1"
          class="block w-36 rounded-md border border-slate-300 px-2 py-1.5"
          required
        />
      </label>
      <button
        type="submit"
        class="rounded-md bg-[var(--color-brand-primary)] px-3 py-1.5 text-sm text-white disabled:opacity-50"
        :disabled="submitting"
      >
        登记
      </button>
    </form>

    <p v-if="error" class="text-sm text-[var(--color-danger)]">{{ error }}</p>
    <p v-if="loading" class="text-sm text-[var(--color-text-muted)]">加载中…</p>

    <div class="overflow-x-auto bg-white border border-slate-200 rounded-lg">
      <table class="w-full text-sm text-left">
        <thead class="bg-slate-50 text-[var(--color-text-muted)]">
          <tr>
            <th class="px-3 py-2 font-medium">租户</th>
            <th class="px-3 py-2 font-medium">类型</th>
            <th class="px-3 py-2 font-medium">周期</th>
            <th class="px-3 py-2 font-medium">上限</th>
            <th class="px-3 py-2 font-medium">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && rows.length === 0">
            <td colspan="5" class="px-3 py-6 text-center text-[var(--color-text-muted)]">暂无登记</td>
          </tr>
          <tr v-for="row in rows" :key="`${row.tenantId}:${row.quotaType}`" class="border-t border-slate-100">
            <td class="px-3 py-2 font-mono text-xs">{{ row.tenantId }}</td>
            <td class="px-3 py-2">{{ row.quotaType }}</td>
            <td class="px-3 py-2">{{ row.period }}</td>
            <td class="px-3 py-2">
              <input
                v-model="drafts[row.tenantId]"
                type="number"
                min="0"
                step="1"
                class="w-32 rounded-md border border-slate-300 px-2 py-1"
              />
            </td>
            <td class="px-3 py-2">
              <button
                type="button"
                class="rounded border border-slate-300 px-2 py-0.5 text-xs hover:bg-slate-50 disabled:opacity-50"
                :disabled="submitting"
                @click="onSave(row)"
              >
                保存
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
