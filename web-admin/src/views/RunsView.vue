<script setup lang="ts">
/**
 * 运行记录：先按请求号/用户/会话/状态找到那一轮，再展开看步骤与审计事件。
 *
 * 报障路径是「用户给出请求号 → 这里查到运行 → 用 runId 看审计事件」，
 * 所以详情面板同时拉 agent_run 的步骤和 agent_event_audit 的行。
 */
import { getRun, listRuns } from '@/shared/api/runs-api'
import { listAuditEvents } from '@/shared/api/audit-api'
import type { AuditEventRow, RunDetail, RunRow } from '@/shared/types/api.types'
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const requestId = ref('')
const tenantId = ref('')
const userId = ref('')
const sessionId = ref('')
const status = ref('')
const limit = ref(50)
const runs = ref<RunRow[]>([])
const loading = ref(false)
const error = ref('')

const selected = ref<RunDetail | null>(null)
const selectedEvents = ref<AuditEventRow[]>([])
const detailLoading = ref(false)
/** 详情与审计是两次请求，用序号防迟到的那次覆盖新的。 */
let detailSeq = 0

const STATUS_OPTIONS = ['', 'SUCCEEDED', 'FAILED', 'CANCELLED']

/** 状态的展示色：成功绿、失败红、取消灰。 */
function statusClass(value: string): string {
  if (value === 'SUCCEEDED') {
    return 'text-[var(--color-success,#15803d)]'
  }
  if (value === 'FAILED') {
    return 'text-[var(--color-danger)]'
  }
  return 'text-[var(--color-text-muted)]'
}

/** 时间只到秒，并把 ISO 的 T 换成空格。 */
function time(raw: string): string {
  if (!raw) {
    return ''
  }
  const cut = raw.replace('T', ' ')
  return cut.length > 19 ? cut.slice(0, 19) : cut
}

/** 耗时给毫秒；没有就空着，不显示 0ms 误导人。 */
function duration(ms: number | null): string {
  return ms === null || ms === undefined ? '' : `${ms} ms`
}

/** 拉一次列表。 */
async function refresh() {
  if (loading.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    runs.value = await listRuns({
      requestId: requestId.value,
      tenantId: tenantId.value,
      userId: userId.value,
      sessionId: sessionId.value,
      status: status.value,
      limit: limit.value,
    })
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

/** 打开某一轮：并行取步骤与审计事件，任一失败只提示不打断。 */
async function open(run: RunRow) {
  const seq = ++detailSeq
  detailLoading.value = true
  error.value = ''
  try {
    const [detail, events] = await Promise.all([
      getRun(run.runId),
      listAuditEvents({ runId: run.runId, limit: 50 }).catch(() => [] as AuditEventRow[]),
    ])
    if (seq !== detailSeq) {
      return
    }
    selected.value = detail
    selectedEvents.value = events
  } catch (e) {
    if (seq === detailSeq) {
      error.value = e instanceof Error ? e.message : '详情加载失败'
    }
  } finally {
    if (seq === detailSeq) {
      detailLoading.value = false
    }
  }
}

/** 收起详情面板，连同审计事件一起清掉，避免下次打开闪旧数据。 */
function closeDetail() {
  selected.value = null
  selectedEvents.value = []
}

/** 清空筛选并按当前输入重查。 */
async function clearFilters() {
  requestId.value = ''
  tenantId.value = ''
  userId.value = ''
  sessionId.value = ''
  status.value = ''
  await refresh()
}

onMounted(() => {
  const tenant = route.query.tenantId
  const user = route.query.userId
  if (typeof tenant === 'string') {
    tenantId.value = tenant
  }
  if (typeof user === 'string') {
    userId.value = user
  }
  void refresh()
})
</script>

<template>
  <section class="space-y-4">
    <header class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h2 class="text-lg font-semibold text-[var(--color-text-main)]">运行记录</h2>
        <p class="mt-1 text-sm text-[var(--color-text-muted)]">
          只读。先按请求号或会话找到那一轮，再展开看步骤与审计事件。
        </p>
      </div>
      <form class="flex flex-wrap items-end gap-2 text-sm" @submit.prevent="refresh">
        <label class="flex flex-col gap-1">
          <span class="text-[var(--color-text-muted)]">请求号</span>
          <input
            v-model="requestId"
            class="w-48 rounded-md border border-slate-300 px-2 py-1 font-mono text-xs outline-none focus-visible:ring-2 focus-visible:ring-blue-400"
            placeholder="X-Request-Id，可空"
          />
        </label>
        <label class="flex flex-col gap-1">
          <span class="text-[var(--color-text-muted)]">租户</span>
          <input
            v-model="tenantId"
            class="w-40 rounded-md border border-slate-300 px-2 py-1 outline-none focus-visible:ring-2 focus-visible:ring-blue-400"
            placeholder="可空"
          />
        </label>
        <label class="flex flex-col gap-1">
          <span class="text-[var(--color-text-muted)]">用户</span>
          <input
            v-model="userId"
            class="w-40 rounded-md border border-slate-300 px-2 py-1 outline-none focus-visible:ring-2 focus-visible:ring-blue-400"
            placeholder="可空"
          />
        </label>
        <label class="flex flex-col gap-1">
          <span class="text-[var(--color-text-muted)]">会话</span>
          <input
            v-model="sessionId"
            class="w-40 rounded-md border border-slate-300 px-2 py-1 outline-none focus-visible:ring-2 focus-visible:ring-blue-400"
            placeholder="可空"
          />
        </label>
        <label class="flex flex-col gap-1">
          <span class="text-[var(--color-text-muted)]">状态</span>
          <select
            v-model="status"
            class="w-32 rounded-md border border-slate-300 px-2 py-1 outline-none focus-visible:ring-2 focus-visible:ring-blue-400"
          >
            <option v-for="option in STATUS_OPTIONS" :key="option" :value="option">
              {{ option || '全部' }}
            </option>
          </select>
        </label>
        <label class="flex flex-col gap-1">
          <span class="text-[var(--color-text-muted)]">条数</span>
          <input
            v-model.number="limit"
            type="number"
            min="1"
            max="100"
            class="w-20 rounded-md border border-slate-300 px-2 py-1 outline-none focus-visible:ring-2 focus-visible:ring-blue-400"
          />
        </label>
        <button
          type="submit"
          class="rounded-md bg-[var(--color-brand-primary)] px-3 py-1.5 text-white disabled:opacity-50"
          :disabled="loading"
        >
          {{ loading ? '查询中…' : '查询' }}
        </button>
        <button
          type="button"
          class="rounded-md border border-slate-300 px-3 py-1.5"
          :disabled="loading"
          @click="clearFilters"
        >
          清空
        </button>
      </form>
    </header>

    <p v-if="error" class="rounded-md bg-red-50 px-3 py-2 text-sm text-[var(--color-danger)]">{{ error }}</p>

    <div class="overflow-x-auto rounded-md border border-slate-200 bg-white">
      <table class="min-w-full text-sm">
        <thead class="bg-slate-50 text-left text-[var(--color-text-muted)]">
          <tr>
            <th class="px-3 py-2 font-medium">时间</th>
            <th class="px-3 py-2 font-medium">状态</th>
            <th class="px-3 py-2 font-medium">运行号</th>
            <th class="px-3 py-2 font-medium">请求号</th>
            <th class="px-3 py-2 font-medium">用户</th>
            <th class="px-3 py-2 font-medium">来源</th>
            <th class="px-3 py-2 font-medium">会话</th>
            <th class="px-3 py-2 font-medium">耗时</th>
            <th class="px-3 py-2 font-medium">输入</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!runs.length">
            <td class="px-3 py-6 text-center text-[var(--color-text-muted)]" colspan="9">没有符合条件的运行。</td>
          </tr>
          <tr
            v-for="row in runs"
            :key="row.runId"
            class="cursor-pointer border-t border-slate-100 hover:bg-slate-50"
            @click="open(row)"
          >
            <td class="px-3 py-2 whitespace-nowrap">{{ time(row.startedAt) }}</td>
            <td class="px-3 py-2" :class="statusClass(row.status)">{{ row.status }}</td>
            <td class="px-3 py-2 font-mono text-xs">{{ row.runId }}</td>
            <td class="px-3 py-2 font-mono text-xs">{{ row.requestId || '—' }}</td>
            <td class="px-3 py-2">{{ row.userId }}</td>
            <td class="px-3 py-2 font-mono text-xs">{{ row.clientIp || '—' }}</td>
            <td class="px-3 py-2 font-mono text-xs">{{ row.sessionId }}</td>
            <td class="px-3 py-2 whitespace-nowrap">{{ duration(row.durationMs) }}</td>
            <td class="max-w-[16rem] truncate px-3 py-2" :title="row.inputSummary">{{ row.inputSummary }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <section v-if="selected" class="space-y-3 rounded-md border border-slate-200 bg-white p-4">
      <header class="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h3 class="font-semibold text-[var(--color-text-main)]">运行 {{ selected.runId }}</h3>
          <p class="mt-1 text-sm text-[var(--color-text-muted)]">
            {{ selected.triggerType }} · {{ selected.model }} · {{ time(selected.startedAt) }} →
            {{ time(selected.finishedAt) }}
          </p>
        </div>
        <button
          type="button"
          class="rounded-md border border-slate-300 px-2 py-1 text-sm"
          @click="closeDetail"
        >
          收起
        </button>
      </header>

      <dl class="grid grid-cols-2 gap-x-6 gap-y-2 text-sm">
        <div>
          <dt class="text-[var(--color-text-muted)]">状态</dt>
          <dd :class="statusClass(selected.status)">{{ selected.status }}</dd>
        </div>
        <div>
          <dt class="text-[var(--color-text-muted)]">错误码</dt>
          <dd>{{ selected.errorCode || '—' }}</dd>
        </div>
        <div>
          <dt class="text-[var(--color-text-muted)]">请求号</dt>
          <dd class="font-mono text-xs">{{ selected.requestId || '—' }}</dd>
        </div>
        <div>
          <dt class="text-[var(--color-text-muted)]">来源</dt>
          <dd class="font-mono text-xs">{{ selected.clientIp || '—' }}</dd>
        </div>
        <div>
          <dt class="text-[var(--color-text-muted)]">耗时</dt>
          <dd>{{ duration(selected.durationMs) || '—' }}</dd>
        </div>
        <div class="col-span-2">
          <dt class="text-[var(--color-text-muted)]">输入</dt>
          <dd class="whitespace-pre-wrap break-words">{{ selected.inputSummary || '—' }}</dd>
        </div>
        <div class="col-span-2">
          <dt class="text-[var(--color-text-muted)]">输出</dt>
          <dd class="whitespace-pre-wrap break-words">{{ selected.outputSummary || '—' }}</dd>
        </div>
      </dl>

      <div>
        <h4 class="text-sm font-medium text-[var(--color-text-main)]">用量</h4>
        <p v-if="!selected.usage?.recorded" class="mt-1 text-sm text-[var(--color-text-muted)]">
          这一轮没有用量记录。
        </p>
        <p v-else class="mt-1 text-sm">
          输入 {{ selected.usage.inputTokens }} · 输出 {{ selected.usage.outputTokens }} · 合计
          {{ selected.usage.totalTokens }}
          <span class="ml-3">金额 {{ selected.usage.costAmount }} {{ selected.usage.currency }}</span>
        </p>
      </div>

      <div>
        <h4 class="text-sm font-medium text-[var(--color-text-main)]">步骤</h4>
        <table class="mt-2 min-w-full text-sm">
          <thead class="bg-slate-50 text-left text-[var(--color-text-muted)]">
            <tr>
              <th class="px-3 py-1.5 font-medium">#</th>
              <th class="px-3 py-1.5 font-medium">类型</th>
              <th class="px-3 py-1.5 font-medium">工具</th>
              <th class="px-3 py-1.5 font-medium">结果</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!selected.steps.length">
              <td class="px-3 py-3 text-center text-[var(--color-text-muted)]" colspan="4">这一轮没有记录步骤。</td>
            </tr>
            <tr v-for="step in selected.steps" :key="`${step.stepNo}-${step.toolId}`" class="border-t border-slate-100">
              <td class="px-3 py-1.5">{{ step.stepNo ?? '' }}</td>
              <td class="px-3 py-1.5">{{ step.stepType }}</td>
              <td class="px-3 py-1.5 font-mono text-xs">{{ step.toolId || '—' }}</td>
              <td class="px-3 py-1.5" :class="statusClass(step.status)">{{ step.status }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div>
        <h4 class="text-sm font-medium text-[var(--color-text-main)]">
          审计事件<span class="ml-2 font-normal text-[var(--color-text-muted)]">（{{ selectedEvents.length }}）</span>
        </h4>
        <p class="mt-1 text-xs text-[var(--color-text-muted)]">
          只有 P14 之后落的行才带运行号；更早的行要去「审计 → 执行事件」按会话查。
        </p>
        <table class="mt-2 min-w-full text-sm">
          <thead class="bg-slate-50 text-left text-[var(--color-text-muted)]">
            <tr>
              <th class="px-3 py-1.5 font-medium">时间</th>
              <th class="px-3 py-1.5 font-medium">动作</th>
              <th class="px-3 py-1.5 font-medium">说明</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!selectedEvents.length">
              <td class="px-3 py-3 text-center text-[var(--color-text-muted)]" colspan="3">没有带这个运行号的事件。</td>
            </tr>
            <tr v-for="event in selectedEvents" :key="event.eventId" class="border-t border-slate-100">
              <td class="px-3 py-1.5 whitespace-nowrap">{{ time(event.createdAt) }}</td>
              <td class="px-3 py-1.5 font-mono text-xs">{{ event.eventType }}</td>
              <td class="max-w-[28rem] break-words px-3 py-1.5 text-xs">{{ event.payload }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <p v-if="detailLoading" class="text-sm text-[var(--color-text-muted)]">详情加载中…</p>
    </section>
  </section>
</template>
