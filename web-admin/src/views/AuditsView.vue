<script setup lang="ts">
/**
 * 安全干预与操作审计只读列表。不改档位，也不补登记表单。
 */
import { listOperationAudits, listSafetyInterventions } from '@/shared/api/audit-api'
import { hasAdminToken } from '@/shared/auth/operator'
import type { OperationAuditSummary, SafetyInterventionSummary } from '@/shared/types/api.types'
import { onMounted, ref, watch } from 'vue'

type Tab = 'safety' | 'operations'

const tab = ref<Tab>('safety')
const safetyRows = ref<SafetyInterventionSummary[]>([])
const operationRows = ref<OperationAuditSummary[]>([])
const loading = ref(false)
const error = ref('')

/** 按页签刷新。 */
async function refresh() {
  if (!hasAdminToken()) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    if (tab.value === 'safety') {
      safetyRows.value = await listSafetyInterventions()
    } else {
      operationRows.value = await listOperationAudits()
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

watch(tab, () => {
  void refresh()
})

onMounted(() => {
  void refresh()
})
</script>

<template>
  <div class="space-y-4 max-w-5xl">
    <div class="flex items-center justify-between gap-3">
      <h2 class="text-lg font-semibold">审计</h2>
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
        type="button"
        class="rounded-md px-3 py-1.5 text-sm"
        :class="tab === 'safety'
          ? 'bg-blue-50 text-[var(--color-brand-primary)] font-medium'
          : 'text-[var(--color-text-muted)] hover:bg-slate-50'"
        @click="tab = 'safety'"
      >
        安全干预
      </button>
      <button
        type="button"
        class="rounded-md px-3 py-1.5 text-sm"
        :class="tab === 'operations'
          ? 'bg-blue-50 text-[var(--color-brand-primary)] font-medium'
          : 'text-[var(--color-text-muted)] hover:bg-slate-50'"
        @click="tab = 'operations'"
      >
        操作审计
      </button>
    </div>

    <p v-if="error" class="text-sm text-[var(--color-danger)]">{{ error }}</p>
    <p v-if="loading" class="text-sm text-[var(--color-text-muted)]">加载中…</p>

    <div v-if="tab === 'safety'" class="overflow-x-auto bg-white border border-slate-200 rounded-lg">
      <p class="px-3 py-2 text-xs text-[var(--color-text-muted)] border-b border-slate-100">
        只读最近干预，不含用户原文，也不改风险档位。
      </p>
      <table class="w-full text-sm text-left">
        <thead class="bg-slate-50 text-[var(--color-text-muted)]">
          <tr>
            <th class="px-3 py-2 font-medium">租户</th>
            <th class="px-3 py-2 font-medium">用户</th>
            <th class="px-3 py-2 font-medium">会话</th>
            <th class="px-3 py-2 font-medium">档位</th>
            <th class="px-3 py-2 font-medium">来源</th>
            <th class="px-3 py-2 font-medium">摘要</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && safetyRows.length === 0">
            <td colspan="6" class="px-3 py-6 text-center text-[var(--color-text-muted)]">暂无记录</td>
          </tr>
          <tr
            v-for="(row, index) in safetyRows"
            :key="`${row.sessionId}:${row.source}:${index}`"
            class="border-t border-slate-100"
          >
            <td class="px-3 py-2 font-mono text-xs">{{ row.tenantId || '—' }}</td>
            <td class="px-3 py-2 font-mono text-xs">{{ row.userId }}</td>
            <td class="px-3 py-2 font-mono text-xs">{{ row.sessionId }}</td>
            <td class="px-3 py-2">{{ row.riskLevel }}</td>
            <td class="px-3 py-2">{{ row.source }}</td>
            <td class="px-3 py-2">{{ row.summary }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-else class="overflow-x-auto bg-white border border-slate-200 rounded-lg">
      <p class="px-3 py-2 text-xs text-[var(--color-text-muted)] border-b border-slate-100">
        后台写入成功后自动落库；本页只读，不含前后快照。
      </p>
      <table class="w-full text-sm text-left">
        <thead class="bg-slate-50 text-[var(--color-text-muted)]">
          <tr>
            <th class="px-3 py-2 font-medium">操作人</th>
            <th class="px-3 py-2 font-medium">租户</th>
            <th class="px-3 py-2 font-medium">操作</th>
            <th class="px-3 py-2 font-medium">对象类型</th>
            <th class="px-3 py-2 font-medium">对象 ID</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && operationRows.length === 0">
            <td colspan="5" class="px-3 py-6 text-center text-[var(--color-text-muted)]">暂无记录</td>
          </tr>
          <tr
            v-for="(row, index) in operationRows"
            :key="`${row.operatorId}:${row.objectId}:${index}`"
            class="border-t border-slate-100"
          >
            <td class="px-3 py-2 font-mono text-xs">{{ row.operatorId }}</td>
            <td class="px-3 py-2 font-mono text-xs">{{ row.tenantId || '—' }}</td>
            <td class="px-3 py-2">{{ row.operation }}</td>
            <td class="px-3 py-2">{{ row.objectType }}</td>
            <td class="px-3 py-2 font-mono text-xs">{{ row.objectId }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
