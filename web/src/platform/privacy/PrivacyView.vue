<script setup lang="ts">
/**
 * 账号与数据页：提交数据删除请求，并盯住每条的进度。
 *
 * 这里只登记与查看。批准发生在管理端，真删由用户进程的执行器异步完成，
 * 所以页面不承诺「提交后立刻消失」，只展示后端回写的状态。
 */
import {
  getDeletionRequest,
  listDeletionRequests,
  submitDeletionRequest,
} from '@/shared/api/deletion-api'
import { ApiBusinessError } from '@/shared/api/http'
import { clearSession, loadSession } from '@/shared/auth/session'
import type { DeletionRequestDetail, DeletionRequestSummary } from '@/shared/types/api.types'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  dataTypeLabel,
  describeProgress,
  statusLabel,
  statusTone,
} from './deletionStatus'

const router = useRouter()
const session = loadSession()

const rows = ref<DeletionRequestSummary[]>([])
const loading = ref(false)
const error = ref('')
const dataType = ref('ALL')
const reason = ref('')
const creating = ref(false)
const expandedId = ref<string | null>(null)
const detail = ref<DeletionRequestDetail | null>(null)
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
    return '删除服务暂不可用'
  }
  if (message.includes('invalid')) {
    return '请求内容不合法，请检查类型与原因长度'
  }
  return message
}

/** 刷新本人的请求。展开的行若不在新列表里就收起。 */
async function refresh() {
  loading.value = true
  error.value = ''
  try {
    rows.value = await listDeletionRequests()
    if (expandedId.value != null && !rows.value.some((row) => row.requestId === expandedId.value)) {
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

/** 提交一条请求。原因可空，最长 1024 字。 */
async function onCreate() {
  const nextReason = reason.value.trim()
  if (creating.value) {
    return
  }
  if (nextReason.length > 1024) {
    error.value = '原因不能超过 1024 字'
    return
  }
  creating.value = true
  error.value = ''
  try {
    await submitDeletionRequest(dataType.value, nextReason)
    reason.value = ''
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

/** 点一行展开详情，再点收起。同时只展开一行。 */
async function toggleDetail(row: DeletionRequestSummary) {
  if (expandedId.value === row.requestId) {
    collapseDetail()
    return
  }
  const seq = ++detailSeq
  expandedId.value = row.requestId
  detail.value = null
  detailError.value = ''
  detailLoading.value = true
  try {
    const loaded = await getDeletionRequest(row.requestId)
    if (seq !== detailSeq) {
      return
    }
    detail.value = loaded
  } catch (e) {
    if (seq !== detailSeq) {
      return
    }
    const message = e instanceof Error ? e.message : '详情加载失败'
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
        <h1 class="text-lg font-semibold text-[var(--color-text-main)]">账号与数据</h1>
        <p class="text-xs text-[var(--color-text-muted)] truncate">{{ identity }}</p>
      </div>
      <form class="ml-auto flex flex-wrap items-center gap-2" @submit.prevent="onCreate">
        <select
          v-model="dataType"
          class="rounded-lg border border-[var(--color-border-strong)] bg-[var(--color-surface-card)] px-2 py-1.5 text-sm text-[var(--color-text-main)]"
          :disabled="creating"
        >
          <option value="ALL">全部个人数据</option>
          <option value="SESSION">会话与消息</option>
          <option value="ATTACHMENT">附件文件</option>
        </select>
        <input
          v-model="reason"
          maxlength="1024"
          class="w-64 rounded-lg border border-[var(--color-border-strong)] bg-[var(--color-surface-card)] px-3 py-1.5 text-sm text-[var(--color-text-main)] outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-brand-primary)]"
          placeholder="原因，可空"
          :disabled="creating"
        />
        <button
          type="submit"
          class="rounded-lg bg-[var(--color-brand-primary)] px-3 py-1.5 text-sm text-white disabled:opacity-50"
          :disabled="creating || loading"
        >
          {{ creating ? '提交中…' : '申请删除' }}
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
      <div
        class="rounded-lg border border-[var(--color-border-strong)] bg-[var(--color-surface-muted)] px-3 py-2 text-xs text-[var(--color-text-muted)] space-y-1"
      >
        <p>
          提交后由管理员批准，再由后台异步删除；页面上的「删除中」是真实执行状态，不是排队装饰。
        </p>
        <p>
          「全部个人数据」会删掉会话与消息、附件（含对象存储里的文件）、知识库与向量、情景记忆与画像；
          审计、计费、额度这些记录只做脱敏保留，不再关联到你个人。
        </p>
        <p>删除不可恢复。执行失败会显示原因，可以联系管理员再发起一次。</p>
      </div>
      <p v-if="error" class="text-sm text-[var(--color-danger)]">{{ error }}</p>
      <p v-if="loading" class="text-sm text-[var(--color-text-muted)]">加载中…</p>

      <div class="overflow-x-auto bg-[var(--color-surface-card)] border border-[var(--color-border)] rounded-lg">
        <table class="w-full text-sm text-left">
          <thead class="bg-[var(--color-surface-muted)] text-[var(--color-text-muted)]">
            <tr>
              <th class="px-3 py-2 font-medium">范围</th>
              <th class="px-3 py-2 font-medium">进度</th>
              <th class="px-3 py-2 font-medium">原因</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!loading && rows.length === 0">
              <td colspan="3" class="px-3 py-6 text-center text-[var(--color-text-muted)]">
                还没有删除请求
              </td>
            </tr>
            <template v-for="row in rows" :key="row.requestId">
              <tr
                class="border-t border-[var(--color-border)] cursor-pointer"
                @click="toggleDetail(row)"
              >
                <td class="px-3 py-2 whitespace-nowrap">
                  {{ dataTypeLabel(row.dataType) }}
                  <span class="block font-mono text-xs text-[var(--color-text-muted)]">
                    {{ shortId(row.requestId) }}
                  </span>
                </td>
                <td class="px-3 py-2 whitespace-nowrap">
                  <span
                    class="inline-flex items-center rounded-full px-2 py-0.5 text-xs"
                    :class="{
                      'bg-[var(--color-surface-muted)] text-[var(--color-text-muted)]': statusTone(row.status) === 'wait',
                      'bg-[var(--color-warning)]/15 text-[var(--color-warning)]': statusTone(row.status) === 'progress',
                      'bg-[var(--color-success)]/15 text-[var(--color-success)]': statusTone(row.status) === 'done',
                      'bg-[var(--color-danger)]/15 text-[var(--color-danger)]': statusTone(row.status) === 'failed',
                    }"
                  >
                    {{ describeProgress(row) }}
                  </span>
                </td>
                <td class="px-3 py-2">
                  <span class="block truncate max-w-md">{{ filled(row.reason) || '未填写' }}</span>
                </td>
              </tr>
              <tr v-if="expandedId === row.requestId" class="border-t border-[var(--color-border)]">
                <td colspan="3" class="px-3 py-2 space-y-1 bg-[var(--color-surface-muted)]">
                  <p v-if="detailLoading" class="text-xs text-[var(--color-text-muted)]">加载中…</p>
                  <template v-else-if="detail">
                    <p class="text-xs text-[var(--color-text-main)]">
                      状态：{{ statusLabel(detail.status) }}
                    </p>
                    <p v-if="filled(detail.requestedAt)" class="text-xs text-[var(--color-text-muted)]">
                      提出 {{ detail.requestedAt }}
                    </p>
                    <p v-if="filled(detail.executedAt)" class="text-xs text-[var(--color-text-muted)]">
                      执行 {{ detail.executedAt }}
                    </p>
                    <p v-if="filled(detail.executedNote)" class="text-xs text-[var(--color-text-muted)] break-words">
                      结果 {{ detail.executedNote }}
                    </p>
                    <p v-if="filled(detail.reason)" class="text-xs text-[var(--color-text-main)] break-words">
                      原因 {{ detail.reason }}
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
