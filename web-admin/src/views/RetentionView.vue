<script setup lang="ts">
/**
 * 数据保留策略：列表 + 登记 + 停用/恢复。不执行清理。
 */
import {
  disableRetentionPolicy,
  listRetentionPolicies,
  registerRetentionPolicy,
  restoreRetentionPolicy,
} from '@/shared/api/retention-api'
import { hasAdminToken } from '@/shared/auth/operator'
import type { RetentionPolicySummary } from '@/shared/types/api.types'
import { onMounted, ref } from 'vue'

const DATA_TYPES = ['SESSION', 'MESSAGE', 'EVENT', 'ATTACHMENT'] as const
const ACTIONS = ['ARCHIVE', 'DELETE', 'ANONYMIZE'] as const

const rows = ref<RetentionPolicySummary[]>([])
const loading = ref(false)
const error = ref('')
const submitting = ref(false)
const busyKey = ref('')

const dataType = ref<(typeof DATA_TYPES)[number]>('SESSION')
const scope = ref('')
const retainDays = ref('90')
const action = ref<(typeof ACTIONS)[number]>('ARCHIVE')

/** 行唯一键：类型 + 范围。 */
function rowKey(row: RetentionPolicySummary) {
  return `${row.dataType}:${row.scope}`
}

/** 刷新最近策略。 */
async function refresh() {
  if (!hasAdminToken()) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    rows.value = await listRetentionPolicies()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

/** 登记一条；天数从 1 起。 */
async function onRegister() {
  const days = Number.parseInt(retainDays.value, 10)
  if (!Number.isFinite(days) || days < 1 || submitting.value) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    await registerRetentionPolicy({
      dataType: dataType.value,
      scope: scope.value.trim(),
      retainDays: days,
      action: action.value,
    })
    scope.value = ''
    retainDays.value = '90'
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '登记失败'
  } finally {
    submitting.value = false
  }
}

/** 停用仍启用的策略。 */
async function onDisable(row: RetentionPolicySummary) {
  const key = rowKey(row)
  if (busyKey.value) {
    return
  }
  if (!window.confirm(`停用保留策略 ${row.dataType} / ${row.scope}？仍不执行清理。`)) {
    return
  }
  busyKey.value = key
  error.value = ''
  try {
    await disableRetentionPolicy(row.dataType, row.scope)
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '停用失败'
  } finally {
    busyKey.value = ''
  }
}

/** 恢复已停用的策略。 */
async function onRestore(row: RetentionPolicySummary) {
  const key = rowKey(row)
  if (busyKey.value) {
    return
  }
  busyKey.value = key
  error.value = ''
  try {
    await restoreRetentionPolicy(row.dataType, row.scope)
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '恢复失败'
  } finally {
    busyKey.value = ''
  }
}

onMounted(() => {
  void refresh()
})
</script>

<template>
  <div class="space-y-4 max-w-4xl">
    <div class="flex items-center justify-between gap-3">
      <h2 class="text-lg font-semibold">保留策略</h2>
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
      只登记目录，不跑清理。范围空表示全局 `*`；同一数据类型+范围不可重复。
    </p>

    <p v-if="error" class="text-sm text-[var(--color-danger)]">{{ error }}</p>
    <p v-if="loading" class="text-sm text-[var(--color-text-muted)]">加载中…</p>

    <form
      class="flex flex-wrap gap-2 items-end bg-white border border-slate-200 rounded-lg p-3"
      @submit.prevent="onRegister"
    >
      <label class="text-sm space-y-1">
        <span class="text-[var(--color-text-muted)]">数据类型</span>
        <select v-model="dataType" class="block w-36 rounded-md border border-slate-300 px-2 py-1.5">
          <option v-for="t in DATA_TYPES" :key="t" :value="t">{{ t }}</option>
        </select>
      </label>
      <label class="text-sm space-y-1">
        <span class="text-[var(--color-text-muted)]">范围</span>
        <input
          v-model="scope"
          class="block w-40 rounded-md border border-slate-300 px-2 py-1.5"
          placeholder="空=全局"
        />
      </label>
      <label class="text-sm space-y-1">
        <span class="text-[var(--color-text-muted)]">保留天数</span>
        <input
          v-model="retainDays"
          type="number"
          min="1"
          class="block w-24 rounded-md border border-slate-300 px-2 py-1.5"
          required
        />
      </label>
      <label class="text-sm space-y-1">
        <span class="text-[var(--color-text-muted)]">动作</span>
        <select v-model="action" class="block w-36 rounded-md border border-slate-300 px-2 py-1.5">
          <option v-for="a in ACTIONS" :key="a" :value="a">{{ a }}</option>
        </select>
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
            <th class="px-3 py-2 font-medium">数据类型</th>
            <th class="px-3 py-2 font-medium">范围</th>
            <th class="px-3 py-2 font-medium">天数</th>
            <th class="px-3 py-2 font-medium">动作</th>
            <th class="px-3 py-2 font-medium">启用</th>
            <th class="px-3 py-2 font-medium">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && rows.length === 0">
            <td colspan="6" class="px-3 py-6 text-center text-[var(--color-text-muted)]">暂无策略</td>
          </tr>
          <tr
            v-for="row in rows"
            :key="rowKey(row)"
            class="border-t border-slate-100"
          >
            <td class="px-3 py-2 font-mono text-xs">{{ row.dataType }}</td>
            <td class="px-3 py-2 font-mono text-xs">{{ row.scope }}</td>
            <td class="px-3 py-2">{{ row.retainDays }}</td>
            <td class="px-3 py-2">{{ row.action }}</td>
            <td class="px-3 py-2">{{ row.enabled ? '是' : '否' }}</td>
            <td class="px-3 py-2 whitespace-nowrap">
              <button
                v-if="row.enabled"
                type="button"
                class="text-sm text-[var(--color-danger)] disabled:opacity-50"
                :disabled="busyKey === rowKey(row)"
                @click="onDisable(row)"
              >
                停用
              </button>
              <button
                v-else
                type="button"
                class="text-sm text-[var(--color-brand-primary)] disabled:opacity-50"
                :disabled="busyKey === rowKey(row)"
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
