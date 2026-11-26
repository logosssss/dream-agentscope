<script setup lang="ts">
/**
 * 安全干预、后台操作审计与执行事件审计。全部只读，不改档位，也不补登记表单。
 */
import { listAuditEvents, listOperationAudits, listSafetyInterventions } from '@/shared/api/audit-api'
import { hasAdminToken } from '@/shared/auth/operator'
import type {
  AuditEventRow,
  OperationAuditSummary,
  SafetyInterventionSummary,
} from '@/shared/types/api.types'
import { onMounted, ref, watch } from 'vue'

type Tab = 'safety' | 'operations' | 'events'

const tab = ref<Tab>('safety')
const safetyRows = ref<SafetyInterventionSummary[]>([])
const operationRows = ref<OperationAuditSummary[]>([])
const eventRows = ref<AuditEventRow[]>([])
const loading = ref(false)
const error = ref('')

/** 按运行号 / 会话 / 操作者过滤执行事件；空着就是最近 50 条。 */
const eventRunId = ref('')
const eventSessionId = ref('')
const eventUserId = ref('')

const TABS: { id: Tab; label: string }[] = [
  { id: 'safety', label: '安全干预' },
  { id: 'operations', label: '操作审计' },
  { id: 'events', label: '执行事件' },
]

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
    } else if (tab.value === 'operations') {
      operationRows.value = await listOperationAudits()
    } else {
      eventRows.value = await listAuditEvents({
        runId: eventRunId.value.trim(),
        sessionId: eventSessionId.value.trim(),
        userId: eventUserId.value.trim(),
      })
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

/** 清空三个过滤条件并重新取最近事件。 */
function clearFilters() {
  eventRunId.value = ''
  eventSessionId.value = ''
  eventUserId.value = ''
  void refresh()
}

/** 时间列只显示到秒，空值给破折号。 */
function time(raw: string): string {
  if (!raw) {
    return '—'
  }
  return raw.length >= 19 ? raw.slice(0, 19).replace('T', ' ') : raw
}

watch(tab, () => {
  void refresh()
})

onMounted(() => {
  void refresh()
})
</script>

<template>
  <div class="space-y-4 max-w-6xl">
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
        v-for="item in TABS"
        :key="item.id"
        type="button"
        class="rounded-md px-3 py-1.5 text-sm"
        :class="tab === item.id
          ? 'bg-blue-50 text-[var(--color-brand-primary)] font-medium'
          : 'text-[var(--color-text-muted)] hover:bg-slate-50'"
        @click="tab = item.id"
      >
        {{ item.label }}
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

    <div
      v-else-if="tab === 'operations'"
      class="overflow-x-auto bg-white border border-slate-200 rounded-lg"
    >
      <p class="px-3 py-2 text-xs text-[var(--color-text-muted)] border-b border-slate-100">
        后台写入成功后自动落库；本页只读，不含前后快照。
      </p>
      <table class="w-full text-sm text-left">
        <thead class="bg-slate-50 text-[var(--color-text-muted)]">
          <tr>
            <th class="px-3 py-2 font-medium">时间</th>
            <th class="px-3 py-2 font-medium">操作人</th>
            <th class="px-3 py-2 font-medium">租户</th>
            <th class="px-3 py-2 font-medium">操作</th>
            <th class="px-3 py-2 font-medium">对象类型</th>
            <th class="px-3 py-2 font-medium">对象 ID</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && operationRows.length === 0">
            <td colspan="6" class="px-3 py-6 text-center text-[var(--color-text-muted)]">暂无记录</td>
          </tr>
          <tr
            v-for="(row, index) in operationRows"
            :key="`${row.operatorId}:${row.objectId}:${index}`"
            class="border-t border-slate-100"
          >
            <td class="px-3 py-2 font-mono text-xs whitespace-nowrap">{{ time(row.createdAt) }}</td>
            <td class="px-3 py-2 font-mono text-xs">{{ row.operatorId }}</td>
            <td class="px-3 py-2 font-mono text-xs">{{ row.tenantId || '—' }}</td>
            <td class="px-3 py-2">{{ row.operation }}</td>
            <td class="px-3 py-2">{{ row.objectType }}</td>
            <td class="px-3 py-2 font-mono text-xs">{{ row.objectId }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-else class="space-y-3">
      <div class="flex flex-wrap items-end gap-2 bg-white border border-slate-200 rounded-lg p-3">
        <label class="text-sm">
          <span class="block text-xs text-[var(--color-text-muted)] mb-1">运行号（runId）</span>
          <input
            v-model="eventRunId"
            type="text"
            placeholder="例如 run-…"
            class="w-56 rounded-md border border-slate-300 px-2 py-1.5 font-mono text-xs"
            @keyup.enter="refresh"
          >
        </label>
        <label class="text-sm">
          <span class="block text-xs text-[var(--color-text-muted)] mb-1">会话</span>
          <input
            v-model="eventSessionId"
            type="text"
            class="w-48 rounded-md border border-slate-300 px-2 py-1.5 font-mono text-xs"
            @keyup.enter="refresh"
          >
        </label>
        <label class="text-sm">
          <span class="block text-xs text-[var(--color-text-muted)] mb-1">用户</span>
          <input
            v-model="eventUserId"
            type="text"
            class="w-48 rounded-md border border-slate-300 px-2 py-1.5 font-mono text-xs"
            @keyup.enter="refresh"
          >
        </label>
        <button
          type="button"
          class="rounded-md bg-[var(--color-brand-primary)] px-3 py-1.5 text-sm text-white disabled:opacity-50"
          :disabled="loading"
          @click="refresh"
        >
          查询
        </button>
        <button
          type="button"
          class="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50"
          :disabled="loading"
          @click="clearFilters"
        >
          清空
        </button>
      </div>

      <div class="overflow-x-auto bg-white border border-slate-200 rounded-lg">
        <p class="px-3 py-2 text-xs text-[var(--color-text-muted)] border-b border-slate-100">
          三个条件互相 AND，都空着就是最近 50 条。运行号是 P14 之后才落库的，更早的行这一列为空。
        </p>
        <table class="w-full text-sm text-left">
          <thead class="bg-slate-50 text-[var(--color-text-muted)]">
            <tr>
              <th class="px-3 py-2 font-medium">时间</th>
              <th class="px-3 py-2 font-medium">动作</th>
              <th class="px-3 py-2 font-medium">用户</th>
              <th class="px-3 py-2 font-medium">会话</th>
              <th class="px-3 py-2 font-medium">运行</th>
              <th class="px-3 py-2 font-medium">说明</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!loading && eventRows.length === 0">
              <td colspan="6" class="px-3 py-6 text-center text-[var(--color-text-muted)]">暂无记录</td>
            </tr>
            <tr
              v-for="(row, index) in eventRows"
              :key="`${row.eventId}:${index}`"
              class="border-t border-slate-100"
            >
              <td class="px-3 py-2 font-mono text-xs whitespace-nowrap">{{ time(row.createdAt) }}</td>
              <td class="px-3 py-2">{{ row.eventType }}</td>
              <td class="px-3 py-2 font-mono text-xs">{{ row.userId || '—' }}</td>
              <td class="px-3 py-2 font-mono text-xs">{{ row.sessionId || '—' }}</td>
              <td class="px-3 py-2 font-mono text-xs">{{ row.runId || '—' }}</td>
              <td class="px-3 py-2 text-xs">{{ row.payload }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
