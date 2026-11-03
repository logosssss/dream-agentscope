<script setup lang="ts">
/**
 * 待审批：列表与批准/拒绝。不触发工具执行。
 */
import { decideApproval, listPendingApprovals } from '@/shared/api/approval-api'
import { hasAdminToken, loadOperatorId } from '@/shared/auth/operator'
import type { ApprovalRow } from '@/shared/types/api.types'
import { onMounted, ref } from 'vue'

const rows = ref<ApprovalRow[]>([])
const loading = ref(false)
const error = ref('')
const busyId = ref('')
const reason = ref('')

/** 刷新待审批。 */
async function refresh() {
  if (!hasAdminToken()) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    rows.value = await listPendingApprovals()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

/** 批准：无需二次确认。 */
async function onApprove(row: ApprovalRow) {
  await decide(row, 'APPROVED')
}

/** 拒绝：需确认。 */
async function onReject(row: ApprovalRow) {
  if (!window.confirm(`确认拒绝审批 ${row.approvalId}（工具 ${row.toolName}）？`)) {
    return
  }
  await decide(row, 'REJECTED')
}

/** 提交决定后刷新列表。 */
async function decide(row: ApprovalRow, decision: 'APPROVED' | 'REJECTED') {
  if (busyId.value) {
    return
  }
  busyId.value = row.approvalId
  error.value = ''
  try {
    await decideApproval(row.approvalId, decision, loadOperatorId(), reason.value.trim())
    reason.value = ''
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '决定失败'
  } finally {
    busyId.value = ''
  }
}

onMounted(() => {
  void refresh()
})
</script>

<template>
  <div class="space-y-4 max-w-5xl">
    <div class="flex items-center justify-between gap-3">
      <h2 class="text-lg font-semibold">待审批</h2>
      <button
        type="button"
        class="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50"
        :disabled="loading"
        @click="refresh"
      >
        刷新
      </button>
    </div>

    <label class="flex flex-wrap items-center gap-2 text-sm bg-white border border-slate-200 rounded-lg p-3">
      <span class="text-[var(--color-text-muted)]">决定意见（可空，下次提交带上）</span>
      <input
        v-model="reason"
        class="flex-1 min-w-48 rounded-md border border-slate-300 px-2 py-1.5"
        placeholder="可选"
      />
    </label>

    <p v-if="error" class="text-sm text-[var(--color-danger)]">{{ error }}</p>
    <p v-if="loading" class="text-sm text-[var(--color-text-muted)]">加载中…</p>

    <div class="overflow-x-auto bg-white border border-slate-200 rounded-lg">
      <table class="w-full text-sm text-left">
        <thead class="bg-slate-50 text-[var(--color-text-muted)]">
          <tr>
            <th class="px-3 py-2 font-medium">审批单</th>
            <th class="px-3 py-2 font-medium">用户</th>
            <th class="px-3 py-2 font-medium">会话</th>
            <th class="px-3 py-2 font-medium">工具</th>
            <th class="px-3 py-2 font-medium">申请时间</th>
            <th class="px-3 py-2 font-medium">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && rows.length === 0">
            <td colspan="6" class="px-3 py-6 text-center text-[var(--color-text-muted)]">暂无待审批</td>
          </tr>
          <tr v-for="row in rows" :key="row.approvalId" class="border-t border-slate-100">
            <td class="px-3 py-2 font-mono text-xs">{{ row.approvalId }}</td>
            <td class="px-3 py-2 font-mono text-xs">{{ row.userId }}</td>
            <td class="px-3 py-2 font-mono text-xs">{{ row.sessionId }}</td>
            <td class="px-3 py-2">{{ row.toolName }}</td>
            <td class="px-3 py-2 text-xs text-[var(--color-text-muted)]">{{ row.requestedAt || '—' }}</td>
            <td class="px-3 py-2">
              <div class="flex flex-wrap gap-1.5">
                <button
                  type="button"
                  class="rounded border border-emerald-300 px-2 py-0.5 text-xs text-emerald-800 hover:bg-emerald-50 disabled:opacity-50"
                  :disabled="!!busyId"
                  @click="onApprove(row)"
                >
                  批准
                </button>
                <button
                  type="button"
                  class="rounded border border-red-300 px-2 py-0.5 text-xs text-red-700 hover:bg-red-50 disabled:opacity-50"
                  :disabled="!!busyId"
                  @click="onReject(row)"
                >
                  拒绝
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
