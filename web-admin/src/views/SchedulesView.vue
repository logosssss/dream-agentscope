<script setup lang="ts">
/**
 * 任务调度：列表 + 登记 + 停用/恢复。
 *
 * <p>登记时服务端算好「下次触发」；真正触发在用户进程（{@code agentscope.schedule.enabled}）。
 * 所以「最近状态」可能是空的——空表示还从未触发过。
 */
import {
  disableTaskSchedule,
  listTaskSchedules,
  registerTaskSchedule,
  restoreTaskSchedule,
} from '@/shared/api/schedule-api'
import { hasAdminToken } from '@/shared/auth/operator'
import type { TaskScheduleSummary } from '@/shared/types/api.types'
import { onMounted, ref } from 'vue'

const TASK_TYPES = ['BACKFILL', 'REPORT', 'BATCH'] as const

type TimingMode = 'cron' | 'delay'

const rows = ref<TaskScheduleSummary[]>([])
const loading = ref(false)
const error = ref('')
const submitting = ref(false)
const busyId = ref('')

const scheduleId = ref('')
const taskType = ref<(typeof TASK_TYPES)[number]>('BACKFILL')
const tenantId = ref('demo')
const userId = ref('')
const agentId = ref('companion-v1')
const timingMode = ref<TimingMode>('cron')
const cronExpr = ref('')
const delaySeconds = ref('60')

/** 刷新最近调度。 */
async function refresh() {
  if (!hasAdminToken()) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    rows.value = await listTaskSchedules()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

/** 登记一条；cron 与延迟二选一。归属用户必填——任务要落到某个用户名下。 */
async function onRegister() {
  const id = scheduleId.value.trim()
  const user = userId.value.trim()
  if (!id || submitting.value) {
    return
  }
  if (!user) {
    error.value = '请填写归属用户：任务按这个用户发布'
    return
  }
  let delay: number | null = null
  let cron = ''
  if (timingMode.value === 'cron') {
    cron = cronExpr.value.trim()
    if (!cron) {
      error.value = '请填写 cron 表达式'
      return
    }
  } else {
    const parsed = Number.parseInt(delaySeconds.value, 10)
    if (!Number.isFinite(parsed) || parsed < 1) {
      error.value = '延迟秒数须从 1 起'
      return
    }
    delay = parsed
  }
  submitting.value = true
  error.value = ''
  try {
    await registerTaskSchedule({
      scheduleId: id,
      taskType: taskType.value,
      tenantId: tenantId.value,
      userId: user,
      agentId: agentId.value,
      cronExpr: cron,
      delaySeconds: delay,
    })
    scheduleId.value = ''
    cronExpr.value = ''
    delaySeconds.value = '60'
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '登记失败'
  } finally {
    submitting.value = false
  }
}

/** 停用仍启用的调度。 */
async function onDisable(row: TaskScheduleSummary) {
  if (busyId.value) {
    return
  }
  if (!window.confirm(`停用调度 ${row.scheduleId}？停用后不再触发。`)) {
    return
  }
  busyId.value = row.scheduleId
  error.value = ''
  try {
    await disableTaskSchedule(row.scheduleId)
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '停用失败'
  } finally {
    busyId.value = ''
  }
}

/** 恢复已停用的调度；下次触发按现在重算，不补触发。 */
async function onRestore(row: TaskScheduleSummary) {
  if (busyId.value) {
    return
  }
  busyId.value = row.scheduleId
  error.value = ''
  try {
    await restoreTaskSchedule(row.scheduleId)
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '恢复失败'
  } finally {
    busyId.value = ''
  }
}

/** 触发结果只给两种颜色：成功绿、失败红；空串不染色。 */
function statusClass(status: string): string {
  if (status === 'SUCCEEDED') {
    return 'text-[var(--color-success)]'
  }
  if (status === 'FAILED') {
    return 'text-[var(--color-danger)]'
  }
  return 'text-[var(--color-text-muted)]'
}

onMounted(() => {
  void refresh()
})
</script>

<template>
  <div class="space-y-4 max-w-6xl">
    <div class="flex items-center justify-between gap-3">
      <h2 class="text-lg font-semibold">任务调度</h2>
      <button
        type="button"
        class="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50"
        :disabled="loading"
        @click="refresh"
      >
        刷新
      </button>
    </div>

    <p class="text-xs text-[var(--color-text-muted)]">
      登记时算好下次触发；用户进程的触发面打开后才会真的发布任务。「最近状态」为空表示从未触发。
      cron 与延迟秒数二选一，cron 支持五段（分 时 日 月 周）与六段（秒 分 时 日 月 周）。
    </p>

    <p v-if="error" class="text-sm text-[var(--color-danger)]">{{ error }}</p>
    <p v-if="loading" class="text-sm text-[var(--color-text-muted)]">加载中…</p>

    <form
      class="flex flex-wrap gap-2 items-end bg-white border border-slate-200 rounded-lg p-3"
      @submit.prevent="onRegister"
    >
      <label class="text-sm space-y-1">
        <span class="text-[var(--color-text-muted)]">调度 ID</span>
        <input
          v-model="scheduleId"
          class="block w-40 rounded-md border border-slate-300 px-2 py-1.5"
          required
        />
      </label>
      <label class="text-sm space-y-1">
        <span class="text-[var(--color-text-muted)]">任务类型</span>
        <select v-model="taskType" class="block w-32 rounded-md border border-slate-300 px-2 py-1.5">
          <option v-for="t in TASK_TYPES" :key="t" :value="t">{{ t }}</option>
        </select>
      </label>
      <label class="text-sm space-y-1">
        <span class="text-[var(--color-text-muted)]">租户</span>
        <input v-model="tenantId" class="block w-28 rounded-md border border-slate-300 px-2 py-1.5" />
      </label>
      <label class="text-sm space-y-1">
        <span class="text-[var(--color-text-muted)]">归属用户</span>
        <input
          v-model="userId"
          class="block w-36 rounded-md border border-slate-300 px-2 py-1.5"
          placeholder="任务按此用户发布"
          required
        />
      </label>
      <label class="text-sm space-y-1">
        <span class="text-[var(--color-text-muted)]">Agent</span>
        <input v-model="agentId" class="block w-36 rounded-md border border-slate-300 px-2 py-1.5" />
      </label>
      <label class="text-sm space-y-1">
        <span class="text-[var(--color-text-muted)]">触发方式</span>
        <select v-model="timingMode" class="block w-24 rounded-md border border-slate-300 px-2 py-1.5">
          <option value="cron">cron</option>
          <option value="delay">延迟</option>
        </select>
      </label>
      <label v-if="timingMode === 'cron'" class="text-sm space-y-1">
        <span class="text-[var(--color-text-muted)]">cron</span>
        <input
          v-model="cronExpr"
          class="block w-48 rounded-md border border-slate-300 px-2 py-1.5 font-mono text-xs"
          placeholder="0 0 * * *"
          required
        />
      </label>
      <label v-else class="text-sm space-y-1">
        <span class="text-[var(--color-text-muted)]">延迟秒数</span>
        <input
          v-model="delaySeconds"
          type="number"
          min="1"
          class="block w-24 rounded-md border border-slate-300 px-2 py-1.5"
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

    <div class="overflow-x-auto bg-white border border-slate-200 rounded-lg">
      <table class="w-full text-sm text-left">
        <thead class="bg-slate-50 text-[var(--color-text-muted)]">
          <tr>
            <th class="px-3 py-2 font-medium">调度 ID</th>
            <th class="px-3 py-2 font-medium">类型</th>
            <th class="px-3 py-2 font-medium">归属</th>
            <th class="px-3 py-2 font-medium">cron</th>
            <th class="px-3 py-2 font-medium">延迟秒</th>
            <th class="px-3 py-2 font-medium">启用</th>
            <th class="px-3 py-2 font-medium">下次触发</th>
            <th class="px-3 py-2 font-medium">上次触发</th>
            <th class="px-3 py-2 font-medium">最近状态</th>
            <th class="px-3 py-2 font-medium">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && rows.length === 0">
            <td colspan="10" class="px-3 py-6 text-center text-[var(--color-text-muted)]">暂无调度</td>
          </tr>
          <tr v-for="row in rows" :key="row.scheduleId" class="border-t border-slate-100">
            <td class="px-3 py-2 font-mono text-xs">{{ row.scheduleId }}</td>
            <td class="px-3 py-2">{{ row.taskType }}</td>
            <td class="px-3 py-2 text-xs">
              <div>{{ row.tenantId || '—' }}</div>
              <div class="text-[var(--color-text-muted)]">{{ row.userId || '—' }}</div>
              <div class="text-[var(--color-text-muted)]">{{ row.agentId || '—' }}</div>
            </td>
            <td class="px-3 py-2 font-mono text-xs">{{ row.cronExpr || '—' }}</td>
            <td class="px-3 py-2">{{ row.delaySeconds ?? '—' }}</td>
            <td class="px-3 py-2">{{ row.enabled ? '是' : '否' }}</td>
            <td class="px-3 py-2 text-xs">{{ row.nextFireAt || '—' }}</td>
            <td class="px-3 py-2 text-xs">{{ row.lastFireAt || '—' }}</td>
            <td class="px-3 py-2 text-xs" :class="statusClass(row.lastStatus)">
              {{ row.lastStatus || '未触发' }}
            </td>
            <td class="px-3 py-2 whitespace-nowrap">
              <button
                v-if="row.enabled"
                type="button"
                class="text-sm text-[var(--color-danger)] disabled:opacity-50"
                :disabled="busyId === row.scheduleId"
                @click="onDisable(row)"
              >
                停用
              </button>
              <button
                v-else
                type="button"
                class="text-sm text-[var(--color-brand-primary)] disabled:opacity-50"
                :disabled="busyId === row.scheduleId"
                @click="onRestore(row)"
              >
                恢复
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
