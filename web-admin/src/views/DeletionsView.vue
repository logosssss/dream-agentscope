<script setup lang="ts">
/**
 * 删除请求决定：按 requestId 批准/拒绝。不执行删除；管理端无列表接口。
 */
import { decideDeletionRequest } from '@/shared/api/deletion-api'
import type { DeletionRequestSummary } from '@/shared/types/api.types'
import { ref } from 'vue'

const requestId = ref('')
const recent = ref<DeletionRequestSummary[]>([])
const error = ref('')
const submitting = ref(false)

/** 把最新决定顶到本页会话列表。 */
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
  if (!window.confirm(`${label}删除请求 ${id}？批准后仍不会真正删数据。`)) {
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
</script>

<template>
  <div class="space-y-4 max-w-4xl">
    <div class="flex items-center justify-between gap-3">
      <h2 class="text-lg font-semibold">删除请求</h2>
    </div>

    <p class="text-xs text-[var(--color-text-muted)]">
      管理端只有决定接口，无待处理列表。请求由用户进程登记；批准或拒绝后仍不执行删除。下方为本页会话结果。
    </p>

    <p v-if="error" class="text-sm text-[var(--color-danger)]">{{ error }}</p>

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
          </tr>
        </thead>
        <tbody>
          <tr v-if="recent.length === 0">
            <td colspan="6" class="px-3 py-6 text-center text-[var(--color-text-muted)]">本页暂无决定记录</td>
          </tr>
          <tr v-for="row in recent" :key="row.requestId" class="border-t border-slate-100">
            <td class="px-3 py-2 font-mono text-xs">{{ row.requestId }}</td>
            <td class="px-3 py-2 font-mono text-xs">{{ row.userId }}</td>
            <td class="px-3 py-2 font-mono text-xs">{{ row.tenantId || '—' }}</td>
            <td class="px-3 py-2">{{ row.dataType }}</td>
            <td class="px-3 py-2">{{ row.status }}</td>
            <td class="px-3 py-2 text-[var(--color-text-muted)] max-w-xs truncate">
              {{ row.reason || '—' }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
