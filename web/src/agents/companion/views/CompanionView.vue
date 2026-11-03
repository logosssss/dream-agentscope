<script setup lang="ts">
/**
 * 智能搭子主视图。会话栏、待确认与轻提示通过 ChatContainer 插槽注入。
 */
import CompanionHint from '@/agents/companion/components/CompanionHint.vue'
import ChatContainer from '@/platform/chat-core/components/ChatContainer.vue'
import { useChatStore } from '@/platform/chat-core/stores/useChatStore'
import { decideApproval, listPendingApprovals } from '@/shared/api/approval-api'
import { logout as logoutApi } from '@/shared/api/auth-api'
import { listMessages, listSessions } from '@/shared/api/session-api'
import { clearSession, loadSession } from '@/shared/auth/session'
import type { PendingApproval, SessionSummary } from '@/shared/types/api.types'
import { computed, onMounted, ref, unref, type Ref } from 'vue'
import { useRouter } from 'vue-router'

const AGENT_ID = 'companion-v1'

const router = useRouter()
const chatStore = useChatStore()

const session = loadSession()
const sessionId = ref<string>(crypto.randomUUID())
/** 当前是否正在看「库里拉下来的会话」（用于空态文案） */
const historyOpened = ref(false)
const loadingHistory = ref(false)
const loadingSessions = ref(false)
const sessionRows = ref<SessionSummary[]>([])
const chatRef = ref<InstanceType<typeof ChatContainer> | null>(null)

const pendingRows = ref<PendingApproval[]>([])
const loadingApprovals = ref(false)
const approvalBusyId = ref('')
const approvalError = ref('')
/** 批准后提示重发；不自动发。绑原 sessionId。 */
const resendOffer = ref<PendingApproval | null>(null)
/** 拒绝二次确认目标；不用 window.confirm。 */
const rejectTarget = ref<PendingApproval | null>(null)

const identity = computed(() => `${session?.tenantId || ''} / ${session?.userId || ''}`)
const emptyHint = computed(() =>
  historyOpened.value
    ? '该会话还没有已保存的消息（只有成功结束的对话会写入历史）。'
    : '发送一条消息开始对话，或从左侧打开历史会话。',
)

/** 当前会话仍待确认条数，给输入区轻提示。 */
const currentPendingCount = computed(
  () => pendingRows.value.filter((row) => row.sessionId === sessionId.value).length,
)

/** 子组件 expose 的 streaming 是 Ref，要用 unref 才能跟上流式状态。 */
const busy = computed(() => {
  const flag = chatRef.value?.streaming as Ref<boolean> | boolean | undefined
  return loadingHistory.value || !!unref(flag)
})

/** 拉侧栏会话。失败展示到聊天内核错误区。 */
async function refreshSessions() {
  loadingSessions.value = true
  try {
    sessionRows.value = await listSessions()
  } catch (e) {
    const message = e instanceof Error ? e.message : '会话列表加载失败'
    chatRef.value?.setError(message)
    if (message.includes('重新登录')) {
      clearSession()
      await router.replace('/login')
    }
  } finally {
    loadingSessions.value = false
  }
}

/** 拉本人待确认。失败写侧栏错误，不挡对话。 */
async function refreshApprovals() {
  loadingApprovals.value = true
  approvalError.value = ''
  try {
    pendingRows.value = await listPendingApprovals()
  } catch (e) {
    const message = e instanceof Error ? e.message : '待确认加载失败'
    approvalError.value = message
    if (message.includes('重新登录')) {
      clearSession()
      await router.replace('/login')
    }
  } finally {
    loadingApprovals.value = false
  }
}

/**
 * 把服务端气泡灌进本地 store。
 * role 只认 user/assistant，其它丢掉，避免脏数据进气泡。
 */
async function loadHistory(targetSessionId: string) {
  loadingHistory.value = true
  chatRef.value?.clearError()
  try {
    const rows = await listMessages(targetSessionId)
    chatStore.replaceMessages(
      AGENT_ID,
      targetSessionId,
      rows
        .filter((row) => row.role === 'user' || row.role === 'assistant')
        .map((row) => ({
          id: `hist-${row.seq}`,
          role: row.role as 'user' | 'assistant',
          content: row.content ?? '',
        })),
    )
  } catch (e) {
    const message = e instanceof Error ? e.message : '消息历史加载失败'
    chatRef.value?.setError(message)
    if (message.includes('重新登录')) {
      clearSession()
      await router.replace('/login')
    }
  } finally {
    loadingHistory.value = false
  }
}

/** 侧栏点开已有会话：停流、切 id、拉历史。 */
async function openSession(row: SessionSummary) {
  if (row.sessionId === sessionId.value || busy.value) {
    return
  }
  chatRef.value?.abortIfStreaming()
  sessionId.value = row.sessionId
  historyOpened.value = true
  await loadHistory(row.sessionId)
}

/** 换新 sessionId，不拉历史。 */
function newChat() {
  chatRef.value?.abortIfStreaming()
  sessionId.value = crypto.randomUUID()
  historyOpened.value = false
  chatStore.clearMessages(AGENT_ID, sessionId.value)
  chatRef.value?.clearError()
  resendOffer.value = null
}

/** 调退出接口并回登录页。 */
async function onLogout() {
  chatRef.value?.abortIfStreaming()
  const token = loadSession()?.accessToken
  try {
    if (token) {
      await logoutApi(token)
    }
  } catch {
    // 本地仍清会话，避免退不出去
  }
  clearSession()
  await router.replace('/login')
}

/** 列表标题：空标题用短 id。 */
function rowLabel(row: SessionSummary): string {
  const title = (row.title || '').trim()
  if (title) {
    return title
  }
  return `${row.sessionId.slice(0, 8)}…`
}

/** 待确认行上的短会话号。 */
function shortSession(id: string): string {
  return `${id.slice(0, 8)}…`
}

/** 一轮对话结束后刷新会话与待确认。 */
async function onSent() {
  await Promise.all([refreshSessions(), refreshApprovals()])
}

/** 流里出现等待确认等状态时刷新待确认列表。 */
function onStatus(tip: string) {
  if (tip.includes('等待确认') || tip.includes('等待用户确认')) {
    void refreshApprovals()
  }
}

/** 批准：记下重发提示，不自动发。 */
async function onApprove(row: PendingApproval) {
  rejectTarget.value = null
  await decide(row, 'APPROVED')
}

/** 打开拒绝确认层。 */
function onReject(row: PendingApproval) {
  if (busy.value || approvalBusyId.value) {
    return
  }
  rejectTarget.value = row
}

/** 关闭拒绝确认层。 */
function cancelReject() {
  rejectTarget.value = null
}

/** 确认拒绝并提交。 */
async function confirmReject() {
  const row = rejectTarget.value
  if (!row) {
    return
  }
  rejectTarget.value = null
  await decide(row, 'REJECTED')
}

/** 提交决定后刷新列表；仅批准时给出重发入口。 */
async function decide(row: PendingApproval, decision: 'APPROVED' | 'REJECTED') {
  if (approvalBusyId.value) {
    return
  }
  approvalBusyId.value = row.approvalId
  approvalError.value = ''
  try {
    await decideApproval(row.approvalId, decision)
    await refreshApprovals()
    if (decision === 'APPROVED') {
      resendOffer.value = row
    } else if (resendOffer.value?.approvalId === row.approvalId) {
      resendOffer.value = null
    }
  } catch (e) {
    approvalError.value = e instanceof Error ? e.message : '决定失败'
    if (approvalError.value.includes('重新登录')) {
      clearSession()
      await router.replace('/login')
    }
  } finally {
    approvalBusyId.value = ''
  }
}

/**
 * 切到批准单所属会话（如需）后重发上一条用户消息。
 * 认领仍绑同一 session，不能跨会话发。
 */
async function onResend() {
  const offer = resendOffer.value
  if (!offer || busy.value) {
    return
  }
  if (offer.sessionId !== sessionId.value) {
    chatRef.value?.abortIfStreaming()
    sessionId.value = offer.sessionId
    historyOpened.value = true
    await loadHistory(offer.sessionId)
  }
  const ok = await chatRef.value?.resendLastUserMessage()
  if (ok) {
    resendOffer.value = null
  } else {
    approvalError.value = '当前会话没有可重发的用户消息'
  }
}

function dismissResend() {
  resendOffer.value = null
}

onMounted(() => {
  void refreshSessions()
  void refreshApprovals()
})
</script>

<template>
  <ChatContainer
    ref="chatRef"
    class="h-full"
    :agent-id="AGENT_ID"
    :session-id="sessionId"
    title="智能搭子"
    :identity="identity"
    :loading-history="loadingHistory"
    :empty-hint="emptyHint"
    @sent="onSent"
    @status="onStatus"
    @need-login="() => {}"
  >
    <template #sidebar>
      <aside class="w-64 h-full border-r border-[var(--color-border)] bg-[var(--color-surface-card)] flex flex-col">
        <div class="p-3 border-b border-[var(--color-border)] space-y-2">
          <p class="text-sm font-semibold text-[var(--color-brand-primary)]">会话</p>
          <button
            type="button"
            class="w-full rounded-lg bg-[var(--color-brand-primary)] px-3 py-2 text-sm text-white disabled:opacity-50"
            :disabled="busy"
            @click="newChat"
          >
            新对话
          </button>
        </div>
        <div class="flex-1 min-h-0 overflow-y-auto p-2 space-y-1">
          <p v-if="loadingSessions" class="px-2 py-2 text-xs text-[var(--color-text-muted)]">加载中…</p>
          <p
            v-else-if="sessionRows.length === 0"
            class="px-2 py-2 text-xs text-[var(--color-text-muted)]"
          >
            还没有历史会话
          </p>
          <button
            v-for="row in sessionRows"
            :key="row.sessionId"
            type="button"
            class="w-full text-left rounded-lg px-2.5 py-2 text-sm hover:bg-[var(--color-surface-muted)] disabled:opacity-50"
            :class="row.sessionId === sessionId
              ? 'text-[var(--color-brand-primary)] font-medium'
              : 'text-[var(--color-text-main)]'"
            :style="row.sessionId === sessionId
              ? { backgroundColor: 'color-mix(in srgb, var(--color-brand-primary) 12%, var(--color-surface-card))' }
              : undefined"
            :disabled="busy"
            @click="openSession(row)"
          >
            <span class="block truncate font-medium">{{ rowLabel(row) }}</span>
            <span class="block truncate text-xs text-[var(--color-text-muted)]">{{ row.lastActiveAt }}</span>
          </button>
        </div>

        <div class="border-t border-[var(--color-border)] flex flex-col max-h-[42%] min-h-[8rem]">
          <div class="px-3 py-2 flex items-center justify-between gap-2">
            <p class="text-sm font-semibold text-[var(--color-brand-primary)]">
              待确认
              <span v-if="pendingRows.length" class="font-normal text-[var(--color-text-muted)]">
                · {{ pendingRows.length }}
              </span>
            </p>
            <button
              type="button"
              class="text-xs text-[var(--color-text-muted)] hover:text-[var(--color-brand-primary)] disabled:opacity-50"
              :disabled="loadingApprovals || !!approvalBusyId"
              @click="refreshApprovals"
            >
              刷新
            </button>
          </div>
          <p v-if="approvalError" class="px-3 pb-1 text-xs text-[var(--color-danger)]">{{ approvalError }}</p>
          <div class="flex-1 overflow-y-auto px-2 pb-2 space-y-1.5">
            <p v-if="loadingApprovals" class="px-1 py-2 text-xs text-[var(--color-text-muted)]">加载中…</p>
            <p
              v-else-if="pendingRows.length === 0"
              class="px-1 py-2 text-xs text-[var(--color-text-muted)]"
            >
              暂无待确认
            </p>
            <div
              v-for="row in pendingRows"
              :key="row.approvalId"
              class="rounded-lg border border-[var(--color-border)] px-2.5 py-2 space-y-1.5"
              :class="row.sessionId === sessionId
                ? 'border-[var(--color-brand-primary)]'
                : ''"
            >
              <p class="text-sm font-medium text-[var(--color-text-main)] truncate">{{ row.toolId }}</p>
              <p class="text-xs text-[var(--color-text-muted)] truncate">会话 {{ shortSession(row.sessionId) }}</p>
              <div class="flex gap-1.5">
                <button
                  type="button"
                  class="flex-1 rounded-md bg-[var(--color-brand-primary)] px-2 py-1 text-xs text-white disabled:opacity-50"
                  :disabled="busy || !!approvalBusyId"
                  @click="onApprove(row)"
                >
                  批准
                </button>
                <button
                  type="button"
                  class="flex-1 rounded-md border border-[var(--color-border-strong)] px-2 py-1 text-xs hover:bg-[var(--color-surface-muted)] disabled:opacity-50"
                  :disabled="busy || !!approvalBusyId"
                  @click="onReject(row)"
                >
                  拒绝
                </button>
              </div>
            </div>
          </div>
        </div>
      </aside>
    </template>

    <template #before-input>
      <CompanionHint :pending-count="currentPendingCount" />
      <div
        v-if="resendOffer"
        class="mt-2 flex flex-wrap items-center gap-2 rounded-lg border border-[var(--color-border)] bg-[var(--color-surface-muted)] px-3 py-2"
      >
        <p class="text-xs text-[var(--color-text-main)] flex-1 min-w-[12rem]">
          已批准「{{ resendOffer.toolId }}」。不会自动继续，请在同一会话重发上一条。
        </p>
        <button
          type="button"
          class="rounded-lg bg-[var(--color-brand-primary)] px-3 py-1.5 text-xs text-white disabled:opacity-50"
          :disabled="busy"
          @click="onResend"
        >
          重发上一条
        </button>
        <button
          type="button"
          class="rounded-lg border border-[var(--color-border-strong)] px-3 py-1.5 text-xs hover:bg-[var(--color-surface-card)]"
          :disabled="busy"
          @click="dismissResend"
        >
          关闭
        </button>
      </div>
    </template>

    <template #header-actions>
      <button
        type="button"
        class="rounded-lg border border-[var(--color-border-strong)] px-3 py-1.5 text-sm hover:bg-[var(--color-surface-muted)]"
        @click="onLogout"
      >
        退出
      </button>
    </template>
  </ChatContainer>

  <Teleport to="body">
    <div
      v-if="rejectTarget"
      class="fixed inset-0 z-50 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="reject-title"
    >
      <button
        type="button"
        class="absolute inset-0 bg-black/40 border-0 cursor-default"
        aria-label="关闭"
        @click="cancelReject"
      />
      <div
        class="relative w-full max-w-sm rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface-card)] p-5 shadow-lg space-y-4"
      >
        <div class="space-y-1.5">
          <h2 id="reject-title" class="text-base font-semibold text-[var(--color-text-main)]">
            确认拒绝？
          </h2>
          <p class="text-sm text-[var(--color-text-muted)] leading-relaxed">
            将拒绝工具
            <span class="font-medium text-[var(--color-brand-primary)]">{{ rejectTarget.toolId }}</span>
            。拒绝后不会执行该工具，可在同一会话再次触发。
          </p>
        </div>
        <div class="flex justify-end gap-2">
          <button
            type="button"
            class="rounded-xl border border-[var(--color-border-strong)] px-4 py-2 text-sm hover:bg-[var(--color-surface-muted)] disabled:opacity-50"
            :disabled="!!approvalBusyId"
            @click="cancelReject"
          >
            取消
          </button>
          <button
            type="button"
            class="rounded-xl bg-[var(--color-danger)] px-4 py-2 text-sm text-white disabled:opacity-50"
            :disabled="busy || !!approvalBusyId"
            @click="confirmReject"
          >
            确认拒绝
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
