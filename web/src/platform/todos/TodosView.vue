<script setup lang="ts">
/**
 * 平台级待办页：列出还开着的待办，登记确认或任务，点开看创建时间。
 * 确认和任务可以完成或取消。审批待办仍回搭子处理。
 */
import { ApiBusinessError } from '@/shared/api/http'
import { cancelTodo, completeTodo, createTodo, getTodo, listTodos } from '@/shared/api/todo-api'
import { clearSession, loadSession } from '@/shared/auth/session'
import type { UserTodoDetail, UserTodoRow } from '@/shared/types/api.types'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const session = loadSession()

const rows = ref<UserTodoRow[]>([])
const loading = ref(false)
const error = ref('')
const expandedId = ref<number | null>(null)
const detail = ref<UserTodoDetail | null>(null)
const detailError = ref('')
const detailLoading = ref(false)
const busyId = ref<number | null>(null)
const cancelId = ref<number | null>(null)
const todoType = ref<'CONFIRM' | 'TASK'>('TASK')
const refId = ref('')
const title = ref('')
const creating = ref(false)
let detailSeq = 0

const identity = computed(() => session?.displayName || '已登录')

/** 登录失效时清会话并回登录。 */
async function bounceIfAuth(message: string) {
  if (message.includes('重新登录')) {
    clearSession()
    await router.replace('/login')
  }
}

/** 把不可用说成中文。其它文案原样留下。 */
function readable(message: string): string {
  if (message.includes('unavailable') || message.includes('503')) {
    return '待办暂不可用'
  }
  if (message.includes('approval todo')) {
    return '审批待办请在搭子里处理'
  }
  if (message.includes('not open')) {
    return '这条待办已经关上'
  }
  if (message.includes('already exists')) {
    return '同样的待办已经有了'
  }
  return message
}

/** 刷新还开着的待办。展开的行若不在新列表里就收起。 */
async function refresh() {
  loading.value = true
  error.value = ''
  try {
    rows.value = await listTodos()
    if (expandedId.value != null && !rows.value.some((row) => row.id === expandedId.value)) {
      collapseDetail()
    }
  } catch (e) {
    const message = e instanceof Error ? e.message : '加载失败'
    error.value = readable(message)
    await bounceIfAuth(message)
  } finally {
    loading.value = false
  }
}

/**
 * 登记一条确认或任务。不接受审批类型。
 * 关联标识必填，不检查它是否真有运行或任务。标题可空。
 */
async function onCreate() {
  const nextRef = refId.value.trim()
  const nextTitle = title.value.trim()
  if (!nextRef || creating.value) {
    if (!nextRef) {
      error.value = '请填写关联标识'
    }
    return
  }
  if (nextRef.length > 128) {
    error.value = '关联标识不能超过 128 字'
    return
  }
  if (nextTitle.length > 256) {
    error.value = '标题不能超过 256 字'
    return
  }
  creating.value = true
  error.value = ''
  try {
    const saved = await createTodo(todoType.value, nextRef, nextTitle)
    refId.value = ''
    title.value = ''
    rows.value = [saved, ...rows.value.filter((row) => row.id !== saved.id)]
  } catch (e) {
    const message = e instanceof Error ? e.message : '登记失败'
    error.value = readable(message)
    await bounceIfAuth(message)
  } finally {
    creating.value = false
  }
}

/** 收起详情。迟到的响应不能再写回。 */
function collapseDetail() {
  detailSeq += 1
  expandedId.value = null
  detail.value = null
  detailError.value = ''
  detailLoading.value = false
  cancelId.value = null
}

/** 空串不占一行。 */
function filled(value: string | null | undefined): string {
  return (value ?? '').trim()
}

/** 短标识。 */
function shortId(id: string | null | undefined): string {
  const text = (id ?? '').trim()
  if (!text) {
    return ''
  }
  return text.length > 8 ? `${text.slice(0, 8)}…` : text
}

/** 待办类型的短标签。 */
function typeLabel(todoType: string | null | undefined): string {
  if (todoType === 'APPROVAL') {
    return '审批'
  }
  if (todoType === 'CONFIRM') {
    return '确认'
  }
  if (todoType === 'TASK') {
    return '任务'
  }
  return (todoType ?? '').trim()
}

/** 状态的短标签。列表里通常是进行中。 */
function statusLabel(status: string | null | undefined): string {
  if (!status || status === 'OPEN') {
    return '进行中'
  }
  if (status === 'DONE') {
    return '已完成'
  }
  if (status === 'CANCELLED') {
    return '已取消'
  }
  return status
}

/** 确认和任务才能在这页关掉。审批回搭子。 */
function canClose(todoType: string | null | undefined): boolean {
  return todoType === 'CONFIRM' || todoType === 'TASK'
}

/**
 * 点一行展开创建时间，再点收起。同时只展开一行。
 * 找不到就刷新列表。不改状态。
 */
async function toggleDetail(row: UserTodoRow) {
  if (expandedId.value === row.id) {
    collapseDetail()
    return
  }
  const seq = ++detailSeq
  expandedId.value = row.id
  detail.value = null
  detailError.value = ''
  detailLoading.value = true
  cancelId.value = null
  try {
    const loaded = await getTodo(row.id)
    if (seq !== detailSeq) {
      return
    }
    detail.value = loaded
  } catch (e) {
    if (seq !== detailSeq) {
      return
    }
    const message = e instanceof Error ? e.message : '待办详情加载失败'
    if (message.includes('重新登录')) {
      clearSession()
      await router.replace('/login')
      return
    }
    if (e instanceof ApiBusinessError && e.code === 'NOT_FOUND') {
      collapseDetail()
      await refresh()
      return
    }
    detailError.value = readable(message)
  } finally {
    if (seq === detailSeq) {
      detailLoading.value = false
    }
  }
}

/** 完成确认或任务后刷新列表，关上的行会消失。 */
async function onComplete(row: UserTodoRow) {
  if (busyId.value != null || !canClose(row.todoType)) {
    return
  }
  busyId.value = row.id
  error.value = ''
  try {
    await completeTodo(row.id)
    if (expandedId.value === row.id) {
      collapseDetail()
    }
    await refresh()
  } catch (e) {
    const message = e instanceof Error ? e.message : '完成失败'
    if (message.includes('重新登录')) {
      clearSession()
      await router.replace('/login')
      return
    }
    if (e instanceof ApiBusinessError && e.code === 'NOT_FOUND') {
      collapseDetail()
      await refresh()
      return
    }
    error.value = readable(message)
    if (message.includes('not open')) {
      await refresh()
    }
  } finally {
    busyId.value = null
  }
}

/** 先在这一行确认，再取消。 */
function askCancel(row: UserTodoRow) {
  if (busyId.value != null || !canClose(row.todoType)) {
    return
  }
  cancelId.value = row.id
}

/** 取消确认层，不请求。 */
function dismissCancel() {
  cancelId.value = null
}

/** 取消确认或任务后刷新列表。 */
async function onCancel(row: UserTodoRow) {
  if (busyId.value != null || !canClose(row.todoType)) {
    return
  }
  busyId.value = row.id
  error.value = ''
  try {
    await cancelTodo(row.id)
    cancelId.value = null
    if (expandedId.value === row.id) {
      collapseDetail()
    }
    await refresh()
  } catch (e) {
    const message = e instanceof Error ? e.message : '取消失败'
    if (message.includes('重新登录')) {
      clearSession()
      await router.replace('/login')
      return
    }
    if (e instanceof ApiBusinessError && e.code === 'NOT_FOUND') {
      collapseDetail()
      await refresh()
      return
    }
    error.value = readable(message)
    if (message.includes('not open')) {
      await refresh()
    }
  } finally {
    busyId.value = null
  }
}

onMounted(() => {
  void refresh()
})
</script>

<template>
  <div class="h-full flex flex-col bg-[var(--color-surface-bg)]">
    <header
      class="shrink-0 border-b border-[var(--color-border)] bg-[var(--color-surface-card)] px-4 py-3 flex flex-wrap items-center gap-3"
    >
      <div class="min-w-0">
        <h1 class="text-lg font-semibold text-[var(--color-text-main)]">待办</h1>
        <p class="text-xs text-[var(--color-text-muted)] truncate">{{ identity }}</p>
      </div>
      <form class="ml-auto flex flex-wrap items-center gap-2" @submit.prevent="onCreate">
        <select
          v-model="todoType"
          class="rounded-lg border border-[var(--color-border-strong)] bg-[var(--color-surface-card)] px-2 py-1.5 text-sm text-[var(--color-text-main)]"
          :disabled="creating"
        >
          <option value="TASK">任务</option>
          <option value="CONFIRM">确认</option>
        </select>
        <input
          v-model="refId"
          maxlength="128"
          class="w-36 rounded-lg border border-[var(--color-border-strong)] bg-[var(--color-surface-card)] px-3 py-1.5 text-sm text-[var(--color-text-main)] outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-brand-primary)]"
          placeholder="关联标识"
          :disabled="creating"
        />
        <input
          v-model="title"
          maxlength="256"
          class="w-40 rounded-lg border border-[var(--color-border-strong)] bg-[var(--color-surface-card)] px-3 py-1.5 text-sm text-[var(--color-text-main)] outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-brand-primary)]"
          placeholder="标题，可空"
          :disabled="creating"
        />
        <button
          type="submit"
          class="rounded-lg bg-[var(--color-brand-primary)] px-3 py-1.5 text-sm text-white disabled:opacity-50"
          :disabled="creating || loading"
        >
          {{ creating ? '登记中…' : '登记' }}
        </button>
        <button
          type="button"
          class="rounded-lg border border-[var(--color-border-strong)] px-3 py-1.5 text-sm hover:bg-[var(--color-surface-muted)] disabled:opacity-50"
          :disabled="loading"
          @click="refresh"
        >
          刷新
        </button>
      </form>
    </header>

    <div class="flex-1 overflow-auto p-4 space-y-3">
      <p class="text-xs text-[var(--color-text-muted)]">
        只显示还开着的待办。可以登记确认或任务，关联标识不检查是否存在。审批请回搭子处理。
      </p>
      <p v-if="error" class="text-sm text-[var(--color-danger)]">{{ error }}</p>
      <p v-if="loading" class="text-sm text-[var(--color-text-muted)]">加载中…</p>

      <div class="overflow-x-auto bg-[var(--color-surface-card)] border border-[var(--color-border)] rounded-lg">
        <table class="w-full text-sm text-left">
          <thead class="bg-[var(--color-surface-muted)] text-[var(--color-text-muted)]">
            <tr>
              <th class="px-3 py-2 font-medium">标题</th>
              <th class="px-3 py-2 font-medium">类型</th>
              <th class="px-3 py-2 font-medium">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!loading && rows.length === 0">
              <td colspan="3" class="px-3 py-6 text-center text-[var(--color-text-muted)]">没有开着的待办</td>
            </tr>
            <template v-for="row in rows" :key="row.id">
              <tr
                class="border-t border-[var(--color-border)] cursor-pointer"
                @click="toggleDetail(row)"
              >
                <td class="px-3 py-2">
                  <span class="block truncate max-w-xs">{{ filled(row.title) || '无标题' }}</span>
                  <span v-if="shortId(row.refId)" class="block font-mono text-xs text-[var(--color-text-muted)] truncate">
                    {{ shortId(row.refId) }}
                  </span>
                </td>
                <td class="px-3 py-2">{{ typeLabel(row.todoType) }}</td>
                <td class="px-3 py-2 whitespace-nowrap" @click.stop>
                  <template v-if="canClose(row.todoType)">
                    <template v-if="cancelId === row.id">
                      <span class="text-xs text-[var(--color-text-muted)]">取消这条？</span>
                      <button
                        type="button"
                        class="ml-2 text-sm text-[var(--color-danger)] disabled:opacity-50"
                        :disabled="busyId === row.id"
                        @click="onCancel(row)"
                      >
                        {{ busyId === row.id ? '取消中…' : '确认' }}
                      </button>
                      <button
                        type="button"
                        class="ml-2 text-sm text-[var(--color-text-muted)]"
                        :disabled="busyId === row.id"
                        @click="dismissCancel"
                      >
                        留下
                      </button>
                    </template>
                    <template v-else>
                      <button
                        type="button"
                        class="text-sm text-[var(--color-brand-primary)] disabled:opacity-50"
                        :disabled="busyId === row.id"
                        @click="onComplete(row)"
                      >
                        {{ busyId === row.id ? '处理中…' : '完成' }}
                      </button>
                      <button
                        type="button"
                        class="ml-2 text-sm text-[var(--color-danger)] disabled:opacity-50"
                        :disabled="busyId != null"
                        @click="askCancel(row)"
                      >
                        取消
                      </button>
                    </template>
                  </template>
                  <span v-else class="text-xs text-[var(--color-text-muted)]">去搭子</span>
                </td>
              </tr>
              <tr v-if="expandedId === row.id" class="border-t border-[var(--color-border)]">
                <td colspan="3" class="px-3 py-2 space-y-0.5 bg-[var(--color-surface-muted)]">
                  <p v-if="detailLoading" class="text-xs text-[var(--color-text-muted)]">加载中…</p>
                  <template v-else-if="detail">
                    <p class="text-xs text-[var(--color-text-muted)]">{{ statusLabel(detail.status) }}</p>
                    <p v-if="filled(detail.createdAt)" class="text-xs text-[var(--color-text-muted)]">
                      创建 {{ detail.createdAt }}
                    </p>
                    <p v-else class="text-xs text-[var(--color-text-muted)]">没有创建时间</p>
                  </template>
                  <p v-if="detailError" class="text-xs text-[var(--color-danger)]">{{ detailError }}</p>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
