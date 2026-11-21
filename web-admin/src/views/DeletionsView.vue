<script setup lang="ts">
/**
 * 删除请求：列出近期请求，按 requestId 批准/拒绝。
 * 批准不删数据；真删由用户进程异步执行，结果看「执行时间 / 执行结果」两列。
 */
import { decideDeletionRequest, listDeletionRequests } from '@/shared/api/deletion-api'
import type { DeletionRequestSummary } from '@/shared/types/api.types'
import { onMounted, ref } from 'vue'

const requestId = ref('')
const recent = ref<DeletionRequestSummary[]>([])
const error = ref('')
const submitting = ref(false)
const loading = ref(false)
const statusFilter = ref('ALL')

const STATUS_OPTIONS = ['ALL', 'PENDING', 'APPROVED', 'REJECTED', 'RUNNING', 'DONE', 'FAILED']

/** 状态的展示色：执行完绿、失败红、还在跑黄。 */
function statusClass(status: string): string {
  if (status === 'DONE') {
    return 'text-[var(--color-success,#15803d)]'
  }
  if (status === 'FAILED') {
    return 'text-[var(--color-danger)]'
  }
  if (status === 'RUNNING') {
    return 'text-[var(--color-warning,#b45309)]'
  }
  return ''
}

/** 拉一次列表。默认不过滤，最多 100 条。 */
async function refresh() {
  if (loading.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    recent.value = await listDeletionRequests(statusFilter.value)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

/** 把最新决定顶到列表头，避免等刷新。 */
function pushResult(row: DeletionRequestSummary) {
  recent.value = [row, ...recent.value.filter((r) => r.requestId !== row.requestId)]
}

/** 提交决定。批准也二次确认，避免误点。 */
async function onDecide(decision: 'APPROVED' | 'REJECTED') {
  const id = requestId.value.trim()
  if (!id || submitting.value) {
    return
  }
  const label = decision === 'APPROVED' ? '批准' : '拒绝'
  if (!window.confirm(`${label}删除请求 ${id}？批准后由用户进程异步真删，可在列表里看执行结果。`)) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    const row = await decideDeletionRequest(id, decision)
    pushResult(row)
    requestId.value = ''
  } catch (e) {
    error.value = e instanceof Error ? e.message : '决定失败'
  } finally {
    submitting.value = false
  }
}

onMounted(refresh)
</script>

<template>
  <div class="space-y-4 max-w-6xl">
    <div class="flex items-center justify-between gap-3">
      <h2 class="text-lg font-semibold">删除请求</h2>
      <button
        type="button"
        class="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50 disabled:opacity-50"
        :disabled="loading"
        @click="refresh"
      >
        刷新
      </button>
    </div>

    <p class="text-xs text-[var(--color-text-muted)]">
      批准只改状态（APPROVED），真删由用户进程的执行器异步完成，成功后状态变 DONE、失败变 FAILED；
      执行时间与结果见右侧两列。执行器默认关闭，见 <code>agentscope.lifecycle.enabled</code>。
    </p>

    <p v-if="error" class="text-sm text-[var(--color-danger)]">{{ error }}</p>

    <div class="flex flex-wrap items-end gap-2">
      <label class="text-sm space-y-1">
        <span class="text-[var(--color-text-muted)]">状态</span>
        <select
          v-model="statusFilter"
          class="block rounded-md border border-slate-300 px-2 py-1.5 text-sm"
          @change="refresh"
        >
          <option v-for="option in STATUS_OPTIONS" :key="option" :value="option">{{ option }}</option>
        </select>
      </label>
    </div>

    <form
      class="flex flex-wrap gap-2 items-end bg-white border border-slate-200 rounded-lg p-3"
      @submit.prevent
    >
      <label class="text-sm space-y-1 grow min-w-[16rem]">
        <span class="text-[var(--color-text-muted)]">请求 ID</span>
        <input
          v-model="requestId"
          class="block w-full rounded-md border border-slate-300 px-2 py-1.5 font-mono text-xs"
          required
        />
      </label>
      <button
        type="button"
        class="rounded-md bg-[var(--color-brand-primary)] px-3 py-1.5 text-sm text-white disabled:opacity-50"
        :disabled="submitting"
        @click="onDecide('APPROVED')"
      >
        批准
      </button>
      <button
        type="button"
        class="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50 disabled:opacity-50"
        :disabled="submitting"
        @click="onDecide('REJECTED')"
      >
        拒绝
      </button>
    </form>

    <div class="overflow-x-auto bg-white border border-slate-200 rounded-lg">
      <table class="w-full text-sm text-left">
        <thead class="bg-slate-50 text-[var(--color-text-muted)]">
          <tr>
            <th class="px-3 py-2 font-medium">请求 ID</th>
            <th class="px-3 py-2 font-medium">用户</th>
            <th class="px-3 py-2 font-medium">租户</th>
            <th class="px-3 py-2 font-medium">类型</th>
            <th class="px-3 py-2 font-medium">状态</th>
            <th class="px-3 py-2 font-medium">原因</th>
            <th class="px-3 py-2 font-medium">执行时间</th>
            <th class="px-3 py-2 font-medium">执行结果</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="recent.length === 0">
            <td colspan="8" class="px-3 py-6 text-center text-[var(--color-text-muted)]">
              {{ loading ? '加载中…' : '没有符合条件的请求' }}
            </td>
          </tr>
          <tr v-for="row in recent" :key="row.requestId" class="border-t border-slate-100">
            <td class="px-3 py-2 font-mono text-xs">{{ row.requestId }}</td>
            <td class="px-3 py-2 font-mono text-xs">{{ row.userId }}</td>
            <td class="px-3 py-2 font-mono text-xs">{{ row.tenantId || '—' }}</td>
            <td class="px-3 py-2">{{ row.dataType }}</td>
            <td class="px-3 py-2" :class="statusClass(row.status)">{{ row.status }}</td>
            <td class="px-3 py-2 text-[var(--color-text-muted)] max-w-xs truncate">
              {{ row.reason || '—' }}
            </td>
            <td class="px-3 py-2 font-mono text-xs">{{ row.executedAt || '—' }}</td>
            <td class="px-3 py-2 text-[var(--color-text-muted)] max-w-xs truncate" :title="row.executedNote">
              {{ row.executedNote || '—' }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
