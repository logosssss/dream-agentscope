<script setup lang="ts">
/**
 * 任务调度：列表 + 登记。不计算下次触发，也不消费。
 */
import { listTaskSchedules, registerTaskSchedule } from '@/shared/api/schedule-api'
import { hasAdminToken } from '@/shared/auth/operator'
import type { TaskScheduleSummary } from '@/shared/types/api.types'
import { onMounted, ref } from 'vue'

const TASK_TYPES = ['BACKFILL', 'REPORT', 'BATCH'] as const

type TimingMode = 'cron' | 'delay'

const rows = ref<TaskScheduleSummary[]>([])
const loading = ref(false)
const error = ref('')
const submitting = ref(false)

const scheduleId = ref('')
const taskType = ref<(typeof TASK_TYPES)[number]>('BACKFILL')
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

/** 登记一条；cron 与延迟二选一。 */
async function onRegister() {
  const id = scheduleId.value.trim()
  if (!id || submitting.value) {
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

onMounted(() => {
  void refresh()
})
</script>

<template>
  <div class="space-y-4 max-w-4xl">
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
      只登记目录，不触发执行。cron 与延迟秒数二选一；不校验 cron 语法。同一标识不可重复。
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
          class="block w-44 rounded-md border border-slate-300 px-2 py-1.5"
          required
        />
      </label>
      <label class="text-sm space-y-1">
        <span class="text-[var(--color-text-muted)]">任务类型</span>
        <select v-model="taskType" class="block w-36 rounded-md border border-slate-300 px-2 py-1.5">
          <option v-for="t in TASK_TYPES" :key="t" :value="t">{{ t }}</option>
        </select>
      </label>
      <label class="text-sm space-y-1">
        <span class="text-[var(--color-text-muted)]">触发方式</span>
        <select v-model="timingMode" class="block w-28 rounded-md border border-slate-300 px-2 py-1.5">
          <option value="cron">cron</option>
          <option value="delay">延迟</option>
        </select>
      </label>
      <label v-if="timingMode === 'cron'" class="text-sm space-y-1">
        <span class="text-[var(--color-text-muted)]">cron</span>
        <input
          v-model="cronExpr"
          class="block w-52 rounded-md border border-slate-300 px-2 py-1.5 font-mono text-xs"
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
          class="block w-28 rounded-md border border-slate-300 px-2 py-1.5"
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
            <th class="px-3 py-2 font-medium">cron</th>
            <th class="px-3 py-2 font-medium">延迟秒</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && rows.length === 0">
            <td colspan="4" class="px-3 py-6 text-center text-[var(--color-text-muted)]">暂无调度</td>
          </tr>
          <tr v-for="row in rows" :key="row.scheduleId" class="border-t border-slate-100">
            <td class="px-3 py-2 font-mono text-xs">{{ row.scheduleId }}</td>
            <td class="px-3 py-2">{{ row.taskType }}</td>
            <td class="px-3 py-2 font-mono text-xs">{{ row.cronExpr || '—' }}</td>
            <td class="px-3 py-2">{{ row.delaySeconds ?? '—' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
