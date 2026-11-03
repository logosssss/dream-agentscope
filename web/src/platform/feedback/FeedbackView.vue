<script setup lang="ts">
/**
 * 平台级反馈页：提交 1 到 5 的评分，列出本人的记录，点开看创建时间。
 * 不核对运行是否存在，也不展示对话正文。
 */
import { createFeedback, getFeedback, listFeedback } from '@/shared/api/feedback-api'
import { ApiBusinessError } from '@/shared/api/http'
import { clearSession, loadSession } from '@/shared/auth/session'
import type { FeedbackDetail, FeedbackSummary } from '@/shared/types/api.types'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const session = loadSession()

const rows = ref<FeedbackSummary[]>([])
const loading = ref(false)
const error = ref('')
const rating = ref(5)
const runId = ref('')
const comment = ref('')
const creating = ref(false)
const expandedId = ref<number | null>(null)
const detail = ref<FeedbackDetail | null>(null)
const detailError = ref('')
const detailLoading = ref(false)
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
    return '反馈暂不可用'
  }
  if (message.includes('invalid')) {
    return '评分必须是 1 到 5'
  }
  return message
}

/** 刷新本人的反馈。展开的行若不在新列表里就收起。 */
async function refresh() {
  loading.value = true
  error.value = ''
  try {
    rows.value = await listFeedback()
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
 * 提交一条评分。运行标识和说明可空，不检查运行是否存在。
 */
async function onCreate() {
  const nextRun = runId.value.trim()
  const nextComment = comment.value.trim()
  if (creating.value) {
    return
  }
  if (rating.value < 1 || rating.value > 5) {
    error.value = '评分必须是 1 到 5'
    return
  }
  if (nextRun.length > 128) {
    error.value = '运行标识不能超过 128 字'
    return
  }
  if (nextComment.length > 1000) {
    error.value = '说明不能超过 1000 字'
    return
  }
  creating.value = true
  error.value = ''
  try {
    await createFeedback(rating.value, nextRun, nextComment)
    runId.value = ''
    comment.value = ''
    rating.value = 5
    await refresh()
  } catch (e) {
    const message = e instanceof Error ? e.message : '提交失败'
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

/**
 * 点一行展开创建时间，再点收起。同时只展开一行。
 * 找不到就刷新列表。不读对话正文。
 */
async function toggleDetail(row: FeedbackSummary) {
  if (expandedId.value === row.id) {
    collapseDetail()
    return
  }
  const seq = ++detailSeq
  expandedId.value = row.id
  detail.value = null
  detailError.value = ''
  detailLoading.value = true
  try {
    const loaded = await getFeedback(row.id)
    if (seq !== detailSeq) {
      return
    }
    detail.value = loaded
  } catch (e) {
    if (seq !== detailSeq) {
      return
    }
    const message = e instanceof Error ? e.message : '反馈详情加载失败'
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
        <h1 class="text-lg font-semibold text-[var(--color-text-main)]">反馈</h1>
        <p class="text-xs text-[var(--color-text-muted)] truncate">{{ identity }}</p>
      </div>
      <form class="ml-auto flex flex-wrap items-center gap-2" @submit.prevent="onCreate">
        <select
          v-model.number="rating"
          class="rounded-lg border border-[var(--color-border-strong)] bg-[var(--color-surface-card)] px-2 py-1.5 text-sm text-[var(--color-text-main)]"
          :disabled="creating"
        >
          <option :value="5">5 分</option>
          <option :value="4">4 分</option>
          <option :value="3">3 分</option>
          <option :value="2">2 分</option>
          <option :value="1">1 分</option>
        </select>
        <input
          v-model="runId"
          maxlength="128"
          class="w-36 rounded-lg border border-[var(--color-border-strong)] bg-[var(--color-surface-card)] px-3 py-1.5 text-sm text-[var(--color-text-main)] outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-brand-primary)]"
          placeholder="运行标识，可空"
          :disabled="creating"
        />
        <input
          v-model="comment"
          maxlength="1000"
          class="w-48 rounded-lg border border-[var(--color-border-strong)] bg-[var(--color-surface-card)] px-3 py-1.5 text-sm text-[var(--color-text-main)] outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-brand-primary)]"
          placeholder="说明，可空"
          :disabled="creating"
        />
        <button
          type="submit"
          class="rounded-lg bg-[var(--color-brand-primary)] px-3 py-1.5 text-sm text-white disabled:opacity-50"
          :disabled="creating || loading"
        >
          {{ creating ? '提交中…' : '提交' }}
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
        评分是 1 到 5。运行标识可以空着，也不检查这次运行是否还在。不展示对话正文。
      </p>
      <p v-if="error" class="text-sm text-[var(--color-danger)]">{{ error }}</p>
      <p v-if="loading" class="text-sm text-[var(--color-text-muted)]">加载中…</p>

      <div class="overflow-x-auto bg-[var(--color-surface-card)] border border-[var(--color-border)] rounded-lg">
        <table class="w-full text-sm text-left">
          <thead class="bg-[var(--color-surface-muted)] text-[var(--color-text-muted)]">
            <tr>
              <th class="px-3 py-2 font-medium">评分</th>
              <th class="px-3 py-2 font-medium">说明</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!loading && rows.length === 0">
              <td colspan="2" class="px-3 py-6 text-center text-[var(--color-text-muted)]">还没有反馈</td>
            </tr>
            <template v-for="row in rows" :key="row.id">
              <tr
                class="border-t border-[var(--color-border)] cursor-pointer"
                @click="toggleDetail(row)"
              >
                <td class="px-3 py-2 whitespace-nowrap">{{ row.rating }} / 5</td>
                <td class="px-3 py-2">
                  <span class="block truncate max-w-md">{{ filled(row.comment) || '无说明' }}</span>
                  <span v-if="shortId(row.runId)" class="block font-mono text-xs text-[var(--color-text-muted)] truncate">
                    运行 {{ shortId(row.runId) }}
                  </span>
                </td>
              </tr>
              <tr v-if="expandedId === row.id" class="border-t border-[var(--color-border)]">
                <td colspan="2" class="px-3 py-2 space-y-0.5 bg-[var(--color-surface-muted)]">
                  <p v-if="detailLoading" class="text-xs text-[var(--color-text-muted)]">加载中…</p>
                  <template v-else-if="detail">
                    <p v-if="filled(detail.comment)" class="text-xs text-[var(--color-text-main)] break-words">
                      {{ detail.comment }}
                    </p>
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
