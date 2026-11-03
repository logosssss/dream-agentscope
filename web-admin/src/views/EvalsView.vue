<script setup lang="ts">
/**
 * 评测：数据集列表/登记；追加用例；登记执行并打分。不调用模型。
 */
import {
  addEvalCase,
  disableEvalDataset,
  listEvalDatasets,
  registerEvalDataset,
  restoreEvalDataset,
  scoreEvalRun,
  startEvalRun,
} from '@/shared/api/eval-api'
import { hasAdminToken } from '@/shared/auth/operator'
import type {
  EvalCaseSummary,
  EvalDatasetSummary,
  EvalRunSummary,
} from '@/shared/types/api.types'
import { onMounted, ref, watch } from 'vue'

type Tab = 'datasets' | 'cases' | 'runs'

const tab = ref<Tab>('datasets')
const datasets = ref<EvalDatasetSummary[]>([])
/** 本页会话内追加过的用例，服务端无列表接口。 */
const recentCases = ref<EvalCaseSummary[]>([])
/** 本页会话内登记/打分过的执行，服务端无列表接口。 */
const recentRuns = ref<EvalRunSummary[]>([])
const loading = ref(false)
const error = ref('')
const submitting = ref(false)
const busyId = ref('')

const datasetId = ref('')
const name = ref('')
const description = ref('')

const caseDatasetId = ref('')
const caseNo = ref('1')
const inputText = ref('')
const expectedText = ref('')

const runDatasetId = ref('')
const scoreRunId = ref('')
const passedCases = ref('0')

/** 刷新数据集列表；用例/执行页签不请求列表。 */
async function refresh() {
  if (!hasAdminToken() || tab.value !== 'datasets') {
    return
  }
  loading.value = true
  error.value = ''
  try {
    datasets.value = await listEvalDatasets()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

/** 登记数据集。 */
async function onRegisterDataset() {
  const id = datasetId.value.trim()
  const n = name.value.trim()
  if (!id || !n || submitting.value) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    await registerEvalDataset({
      datasetId: id,
      name: n,
      description: description.value.trim(),
    })
    datasetId.value = ''
    name.value = ''
    description.value = ''
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '登记失败'
  } finally {
    submitting.value = false
  }
}

/** 停用仍启用的数据集。 */
async function onDisableDataset(row: EvalDatasetSummary) {
  if (busyId.value) {
    return
  }
  if (!window.confirm(`停用数据集 ${row.datasetId}？停用后不再开跑，用例仍保留。`)) {
    return
  }
  busyId.value = row.datasetId
  error.value = ''
  try {
    await disableEvalDataset(row.datasetId)
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '停用失败'
  } finally {
    busyId.value = ''
  }
}

/** 恢复已停用的数据集。 */
async function onRestoreDataset(row: EvalDatasetSummary) {
  if (busyId.value) {
    return
  }
  busyId.value = row.datasetId
  error.value = ''
  try {
    await restoreEvalDataset(row.datasetId)
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '恢复失败'
  } finally {
    busyId.value = ''
  }
}

/** 追加用例到 1.0.0。 */
async function onAddCase() {
  const id = caseDatasetId.value.trim()
  const no = Number.parseInt(caseNo.value, 10)
  const input = inputText.value.trim()
  if (!id || !input || !Number.isFinite(no) || no < 1 || submitting.value) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    const row = await addEvalCase(id, {
      caseNo: no,
      inputText: input,
      expectedText: expectedText.value.trim(),
    })
    recentCases.value = [row, ...recentCases.value]
    caseNo.value = String(no + 1)
    inputText.value = ''
    expectedText.value = ''
  } catch (e) {
    error.value = e instanceof Error ? e.message : '追加失败'
  } finally {
    submitting.value = false
  }
}

/** 登记一次执行。 */
async function onStartRun() {
  const id = runDatasetId.value.trim()
  if (!id || submitting.value) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    const row = await startEvalRun(id)
    recentRuns.value = [row, ...recentRuns.value]
    scoreRunId.value = row.evalRunId
    passedCases.value = '0'
  } catch (e) {
    error.value = e instanceof Error ? e.message : '登记执行失败'
  } finally {
    submitting.value = false
  }
}

/** 给 RUNNING 执行打分。 */
async function onScoreRun() {
  const id = scoreRunId.value.trim()
  const passed = Number.parseInt(passedCases.value, 10)
  if (!id || !Number.isFinite(passed) || passed < 0 || submitting.value) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    const row = await scoreEvalRun(id, passed)
    recentRuns.value = [row, ...recentRuns.value.filter((r) => r.evalRunId !== row.evalRunId)]
  } catch (e) {
    error.value = e instanceof Error ? e.message : '打分失败'
  } finally {
    submitting.value = false
  }
}

watch(tab, () => {
  error.value = ''
  void refresh()
})

onMounted(() => {
  void refresh()
})
</script>

<template>
  <div class="space-y-4 max-w-5xl">
    <div class="flex items-center justify-between gap-3">
      <h2 class="text-lg font-semibold">评测</h2>
      <button
        v-if="tab === 'datasets'"
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
        :class="tab === 'datasets'
          ? 'bg-blue-50 text-[var(--color-brand-primary)] font-medium'
          : 'text-[var(--color-text-muted)] hover:bg-slate-50'"
        @click="tab = 'datasets'"
      >
        数据集
      </button>
      <button
        type="button"
        class="rounded-md px-3 py-1.5 text-sm"
        :class="tab === 'cases'
          ? 'bg-blue-50 text-[var(--color-brand-primary)] font-medium'
          : 'text-[var(--color-text-muted)] hover:bg-slate-50'"
        @click="tab = 'cases'"
      >
        用例
      </button>
      <button
        type="button"
        class="rounded-md px-3 py-1.5 text-sm"
        :class="tab === 'runs'
          ? 'bg-blue-50 text-[var(--color-brand-primary)] font-medium'
          : 'text-[var(--color-text-muted)] hover:bg-slate-50'"
        @click="tab = 'runs'"
      >
        执行
      </button>
    </div>

    <p v-if="error" class="text-sm text-[var(--color-danger)]">{{ error }}</p>
    <p v-if="loading" class="text-sm text-[var(--color-text-muted)]">加载中…</p>

    <template v-if="tab === 'datasets'">
      <p class="text-xs text-[var(--color-text-muted)]">
        版本固定 1.0.0。停用后不再开跑；不带回用例，也不调用模型。
      </p>
      <form
        class="flex flex-wrap gap-2 items-end bg-white border border-slate-200 rounded-lg p-3"
        @submit.prevent="onRegisterDataset"
      >
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">数据集 ID</span>
          <input v-model="datasetId" class="block w-40 rounded-md border border-slate-300 px-2 py-1.5" required />
        </label>
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">名称</span>
          <input v-model="name" class="block w-44 rounded-md border border-slate-300 px-2 py-1.5" required />
        </label>
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">说明</span>
          <input v-model="description" class="block w-56 rounded-md border border-slate-300 px-2 py-1.5" />
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
              <th class="px-3 py-2 font-medium">数据集 ID</th>
              <th class="px-3 py-2 font-medium">名称</th>
              <th class="px-3 py-2 font-medium">版本</th>
              <th class="px-3 py-2 font-medium">说明</th>
              <th class="px-3 py-2 font-medium">启用</th>
              <th class="px-3 py-2 font-medium">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!loading && datasets.length === 0">
              <td colspan="6" class="px-3 py-6 text-center text-[var(--color-text-muted)]">暂无数据集</td>
            </tr>
            <tr
              v-for="row in datasets"
              :key="`${row.datasetId}:${row.version}`"
              class="border-t border-slate-100"
            >
              <td class="px-3 py-2 font-mono text-xs">{{ row.datasetId }}</td>
              <td class="px-3 py-2">{{ row.name }}</td>
              <td class="px-3 py-2">{{ row.version }}</td>
              <td class="px-3 py-2 text-[var(--color-text-muted)]">{{ row.description || '—' }}</td>
              <td class="px-3 py-2">{{ row.enabled ? '是' : '否' }}</td>
              <td class="px-3 py-2 whitespace-nowrap">
                <button
                  v-if="row.enabled"
                  type="button"
                  class="text-sm text-[var(--color-danger)] disabled:opacity-50"
                  :disabled="busyId === row.datasetId"
                  @click="onDisableDataset(row)"
                >
                  停用
                </button>
                <button
                  v-else
                  type="button"
                  class="text-sm text-[var(--color-brand-primary)] disabled:opacity-50"
                  :disabled="busyId === row.datasetId"
                  @click="onRestoreDataset(row)"
                >
                  恢复
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <template v-else-if="tab === 'cases'">
      <p class="text-xs text-[var(--color-text-muted)]">
        追加到该数据集的 1.0.0。服务端无用例列表接口，下方仅本页会话结果。
      </p>
      <form
        class="flex flex-wrap gap-2 items-end bg-white border border-slate-200 rounded-lg p-3"
        @submit.prevent="onAddCase"
      >
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">数据集 ID</span>
          <input v-model="caseDatasetId" class="block w-40 rounded-md border border-slate-300 px-2 py-1.5" required />
        </label>
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">序号</span>
          <input
            v-model="caseNo"
            type="number"
            min="1"
            class="block w-20 rounded-md border border-slate-300 px-2 py-1.5"
            required
          />
        </label>
        <label class="text-sm space-y-1 grow min-w-[12rem]">
          <span class="text-[var(--color-text-muted)]">输入</span>
          <input v-model="inputText" class="block w-full rounded-md border border-slate-300 px-2 py-1.5" required />
        </label>
        <label class="text-sm space-y-1 grow min-w-[12rem]">
          <span class="text-[var(--color-text-muted)]">期望</span>
          <input v-model="expectedText" class="block w-full rounded-md border border-slate-300 px-2 py-1.5" />
        </label>
        <button
          type="submit"
          class="rounded-md bg-[var(--color-brand-primary)] px-3 py-1.5 text-sm text-white disabled:opacity-50"
          :disabled="submitting"
        >
          追加
        </button>
      </form>

      <div class="overflow-x-auto bg-white border border-slate-200 rounded-lg">
        <table class="w-full text-sm text-left">
          <thead class="bg-slate-50 text-[var(--color-text-muted)]">
            <tr>
              <th class="px-3 py-2 font-medium">数据集</th>
              <th class="px-3 py-2 font-medium">序号</th>
              <th class="px-3 py-2 font-medium">输入</th>
              <th class="px-3 py-2 font-medium">期望</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="recentCases.length === 0">
              <td colspan="4" class="px-3 py-6 text-center text-[var(--color-text-muted)]">本页暂无追加记录</td>
            </tr>
            <tr
              v-for="row in recentCases"
              :key="`${row.datasetId}:${row.caseNo}`"
              class="border-t border-slate-100"
            >
              <td class="px-3 py-2 font-mono text-xs">{{ row.datasetId }}</td>
              <td class="px-3 py-2">{{ row.caseNo }}</td>
              <td class="px-3 py-2 max-w-xs truncate">{{ row.inputText }}</td>
              <td class="px-3 py-2 max-w-xs truncate text-[var(--color-text-muted)]">
                {{ row.expectedText || '—' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <template v-else>
      <p class="text-xs text-[var(--color-text-muted)]">
        登记执行只记用例条数，状态停在 RUNNING；打分写入通过数后 SUCCEEDED。不调用模型。无执行列表接口，下方为本页会话结果。
      </p>
      <form
        class="flex flex-wrap gap-2 items-end bg-white border border-slate-200 rounded-lg p-3"
        @submit.prevent="onStartRun"
      >
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">数据集 ID</span>
          <input v-model="runDatasetId" class="block w-40 rounded-md border border-slate-300 px-2 py-1.5" required />
        </label>
        <button
          type="submit"
          class="rounded-md bg-[var(--color-brand-primary)] px-3 py-1.5 text-sm text-white disabled:opacity-50"
          :disabled="submitting"
        >
          登记执行
        </button>
      </form>

      <form
        class="flex flex-wrap gap-2 items-end bg-white border border-slate-200 rounded-lg p-3"
        @submit.prevent="onScoreRun"
      >
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">执行 ID</span>
          <input v-model="scoreRunId" class="block w-56 rounded-md border border-slate-300 px-2 py-1.5 font-mono text-xs" required />
        </label>
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">通过数</span>
          <input
            v-model="passedCases"
            type="number"
            min="0"
            class="block w-24 rounded-md border border-slate-300 px-2 py-1.5"
            required
          />
        </label>
        <button
          type="submit"
          class="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50 disabled:opacity-50"
          :disabled="submitting"
        >
          打分
        </button>
      </form>

      <div class="overflow-x-auto bg-white border border-slate-200 rounded-lg">
        <table class="w-full text-sm text-left">
          <thead class="bg-slate-50 text-[var(--color-text-muted)]">
            <tr>
              <th class="px-3 py-2 font-medium">执行 ID</th>
              <th class="px-3 py-2 font-medium">数据集</th>
              <th class="px-3 py-2 font-medium">用例数</th>
              <th class="px-3 py-2 font-medium">通过</th>
              <th class="px-3 py-2 font-medium">状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="recentRuns.length === 0">
              <td colspan="5" class="px-3 py-6 text-center text-[var(--color-text-muted)]">本页暂无执行记录</td>
            </tr>
            <tr v-for="row in recentRuns" :key="row.evalRunId" class="border-t border-slate-100">
              <td class="px-3 py-2 font-mono text-xs">{{ row.evalRunId }}</td>
              <td class="px-3 py-2 font-mono text-xs">{{ row.datasetId }}</td>
              <td class="px-3 py-2">{{ row.totalCases }}</td>
              <td class="px-3 py-2">{{ row.passedCases }}</td>
              <td class="px-3 py-2">{{ row.status }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>
