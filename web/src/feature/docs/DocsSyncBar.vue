<script setup lang="ts">
/**
 * 文档同步条。只在 /docs 根页展示：读状态、手动触发、轮询到结束。
 * 不在挂载时自动同步；不把 runId 打到控制台。
 */
import { fetchDocsSyncStatus, startDocsSync } from '@/shared/api/docs-api'
import { ApiBusinessError } from '@/shared/api/http'
import type { DocsSyncStatus } from '@/shared/types/api.types'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const emit = defineEmits<{
  synced: []
}>()

const status = ref<DocsSyncStatus | null>(null)
const actionError = ref('')
const starting = ref(false)

/** 轮询句柄；卸载或结束后清掉，避免漏网定时器。 */
let pollTimer: ReturnType<typeof setTimeout> | null = null
let pollLeft = 0
let alive = true

const running = computed(() => status.value?.status === 'RUNNING' || starting.value)

/** 上次同步时间：优先结束时间，没有就用开始时间。 */
const syncTimeLabel = computed(() => {
  const row = status.value
  if (!row || row.status === 'NONE') {
    return '尚未同步'
  }
  const raw = row.finishedAt || row.startedAt
  if (!raw) {
    return '尚未同步'
  }
  const text = raw.replace('T', ' ')
  return text.length >= 16 ? text.slice(0, 16) : text
})

const countsLabel = computed(() => {
  const row = status.value
  if (!row || row.status === 'NONE') {
    return '扫描 0 · 新增 0 · 更新 0 · 删除 0'
  }
  return `扫描 ${row.scanned} · 新增 ${row.added} · 更新 ${row.updated} · 删除 ${row.removed}`
})

const badge = computed(() => {
  const code = (status.value?.status || 'NONE').toUpperCase()
  if (code === 'RUNNING') {
    return { text: '同步中…', className: 'text-[var(--color-brand-primary)]' }
  }
  if (code === 'SUCCESS') {
    return { text: 'SUCCESS', className: 'text-[var(--color-success,#16a34a)]' }
  }
  if (code === 'PARTIAL') {
    return { text: 'PARTIAL', className: 'text-[var(--color-warning)]' }
  }
  if (code === 'FAILED') {
    return { text: 'FAILED', className: 'text-[var(--color-danger)]' }
  }
  return { text: 'NONE', className: 'text-[var(--color-text-muted)]' }
})

const noteLine = computed(() => {
  if (actionError.value) {
    return actionError.value
  }
  const note = (status.value?.note || '').trim()
  if (!note) {
    return ''
  }
  return note.length > 80 ? `${note.slice(0, 80)}…` : note
})

/** 业务码翻成维护者能懂的话；其它保留原 message。 */
function readable(cause: unknown): string {
  if (cause instanceof ApiBusinessError && cause.code === 'NOT_FOUND') {
    return '只有维护者可以触发同步'
  }
  if (cause instanceof ApiBusinessError && cause.code === 'UNAVAILABLE') {
    return '文档索引暂不可用'
  }
  return cause instanceof Error ? cause.message : '同步失败'
}

function stopPoll() {
  if (pollTimer != null) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
  pollLeft = 0
}

/** 挂载后只读状态，不触发同步。 */
async function loadStatus() {
  try {
    status.value = await fetchDocsSyncStatus()
  } catch (cause) {
    actionError.value = readable(cause)
  }
}

/**
 * 轮询到非 RUNNING 或次数用尽。
 * SUCCESS / PARTIAL 通知外层重拉树；FAILED 只展示 note。
 */
function schedulePoll() {
  stopPoll()
  pollLeft = 60
  const tick = async () => {
    if (!alive || pollLeft <= 0) {
      starting.value = false
      return
    }
    pollLeft -= 1
    try {
      const next = await fetchDocsSyncStatus()
      if (!alive) {
        return
      }
      status.value = next
      if (next.status === 'RUNNING') {
        pollTimer = setTimeout(() => {
          void tick()
        }, 2000)
        return
      }
      starting.value = false
      if (next.status === 'SUCCESS' || next.status === 'PARTIAL') {
        emit('synced')
      }
    } catch (cause) {
      if (!alive) {
        return
      }
      starting.value = false
      actionError.value = readable(cause)
    }
  }
  void tick()
}

/** 点按钮：先 POST，再按 2 秒间隔读状态，最多 60 次。 */
async function onSync() {
  if (running.value) {
    return
  }
  actionError.value = ''
  starting.value = true
  stopPoll()
  try {
    await startDocsSync()
    if (!alive) {
      return
    }
    status.value = {
      id: status.value?.id ?? 0,
      startedAt: status.value?.startedAt ?? null,
      finishedAt: null,
      scanned: status.value?.scanned ?? 0,
      added: status.value?.added ?? 0,
      updated: status.value?.updated ?? 0,
      removed: status.value?.removed ?? 0,
      status: 'RUNNING',
      note: null,
    }
    schedulePoll()
  } catch (cause) {
    starting.value = false
    actionError.value = readable(cause)
  }
}

onMounted(() => {
  void loadStatus()
})

onBeforeUnmount(() => {
  alive = false
  stopPoll()
})
</script>

<template>
  <div class="flex flex-col gap-1 text-xs text-[var(--color-text-muted)]">
    <div class="flex items-center gap-2 flex-wrap">
      <span class="truncate">{{ syncTimeLabel }}</span>
      <span aria-hidden="true">·</span>
      <span class="truncate">{{ countsLabel }}</span>
      <span :class="['shrink-0 font-medium', badge.className]">{{ badge.text }}</span>
      <button
        type="button"
        class="ml-auto shrink-0 rounded-md border border-[var(--color-border)] px-2 py-1 text-xs text-[var(--color-text-main)] disabled:opacity-50"
        :disabled="running"
        @click="onSync"
      >
        {{ running ? '同步中…' : '重新同步' }}
      </button>
    </div>
    <p v-if="noteLine" class="truncate text-[var(--color-danger)]" :title="noteLine">
      {{ noteLine }}
    </p>
  </div>
</template>
