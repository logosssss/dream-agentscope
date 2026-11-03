<script setup lang="ts">
/**
 * 平台级用量页：列出本人的 token 记录，点开看输入输出和记账时间。
 * 不展示提示词，也不改金额。
 */
import { ApiBusinessError } from '@/shared/api/http'
import { getUsage, listUsage } from '@/shared/api/usage-api'
import { clearSession, loadSession } from '@/shared/auth/session'
import type { TokenUsageDetail, TokenUsageRow } from '@/shared/types/api.types'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const session = loadSession()

const rows = ref<TokenUsageRow[]>([])
const loading = ref(false)
const error = ref('')
const expandedId = ref<number | null>(null)
const detail = ref<TokenUsageDetail | null>(null)
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
    return '用量暂不可用'
  }
  return message
}

/** 刷新本人的用量。展开的行若不在新列表里就收起。 */
async function refresh() {
  loading.value = true
  error.value = ''
  try {
    rows.value = await listUsage()
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

/** token 数。没有时写成 0。 */
function tokens(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) {
    return '0'
  }
  return String(value)
}

/** 金额配上币种。没有金额时写成 0。 */
function money(amount: number | string | null | undefined, currency: string | null | undefined): string {
  const value = amount == null || amount === '' ? '0' : String(amount)
  const unit = filled(currency)
  return unit ? `${value} ${unit}` : value
}

/**
 * 点一行展开记账时间，再点收起。同时只展开一行。
 * 找不到就刷新列表。不改金额。
 */
async function toggleDetail(row: TokenUsageRow) {
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
    const loaded = await getUsage(row.id)
    if (seq !== detailSeq) {
      return
    }
    detail.value = loaded
  } catch (e) {
    if (seq !== detailSeq) {
      return
    }
    const message = e instanceof Error ? e.message : '用量详情加载失败'
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
        <h1 class="text-lg font-semibold text-[var(--color-text-main)]">用量</h1>
        <p class="text-xs text-[var(--color-text-muted)] truncate">{{ identity }}</p>
      </div>
      <button
        type="button"
        class="ml-auto rounded-lg border border-[var(--color-border-strong)] px-3 py-1.5 text-sm hover:bg-[var(--color-surface-muted)] disabled:opacity-50"
        :disabled="loading"
        @click="refresh"
      >
        刷新
      </button>
    </header>

    <div class="flex-1 overflow-auto p-4 space-y-3">
      <p class="text-xs text-[var(--color-text-muted)]">
        只显示已经记下的 token。点一行看输入、输出和记账时间。不展示提示词。
      </p>
      <p v-if="error" class="text-sm text-[var(--color-danger)]">{{ error }}</p>
      <p v-if="loading" class="text-sm text-[var(--color-text-muted)]">加载中…</p>

      <div class="overflow-x-auto bg-[var(--color-surface-card)] border border-[var(--color-border)] rounded-lg">
        <table class="w-full text-sm text-left">
          <thead class="bg-[var(--color-surface-muted)] text-[var(--color-text-muted)]">
            <tr>
              <th class="px-3 py-2 font-medium">模型</th>
              <th class="px-3 py-2 font-medium">合计</th>
              <th class="px-3 py-2 font-medium">金额</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!loading && rows.length === 0">
              <td colspan="3" class="px-3 py-6 text-center text-[var(--color-text-muted)]">还没有用量</td>
            </tr>
            <template v-for="row in rows" :key="row.id">
              <tr
                class="border-t border-[var(--color-border)] cursor-pointer"
                @click="toggleDetail(row)"
              >
                <td class="px-3 py-2">
                  <span class="block truncate max-w-xs">{{ filled(row.model) || '未记模型' }}</span>
                  <span v-if="shortId(row.runId)" class="block font-mono text-xs text-[var(--color-text-muted)] truncate">
                    运行 {{ shortId(row.runId) }}
                  </span>
                </td>
                <td class="px-3 py-2">{{ tokens(row.totalTokens) }}</td>
                <td class="px-3 py-2">{{ money(row.costAmount, row.currency) }}</td>
              </tr>
              <tr v-if="expandedId === row.id" class="border-t border-[var(--color-border)]">
                <td colspan="3" class="px-3 py-2 space-y-0.5 bg-[var(--color-surface-muted)]">
                  <p v-if="detailLoading" class="text-xs text-[var(--color-text-muted)]">加载中…</p>
                  <template v-else-if="detail">
                    <p class="text-xs text-[var(--color-text-muted)]">
                      输入 {{ tokens(detail.inputTokens) }} · 输出 {{ tokens(detail.outputTokens) }}
                    </p>
                    <p v-if="filled(detail.createdAt)" class="text-xs text-[var(--color-text-muted)]">
                      记账 {{ detail.createdAt }}
                    </p>
                    <p v-else class="text-xs text-[var(--color-text-muted)]">没有记账时间</p>
                    <p v-if="shortId(detail.sessionId)" class="text-xs text-[var(--color-text-muted)] truncate">
                      会话 {{ shortId(detail.sessionId) }}
                    </p>
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
