<script setup lang="ts">
/**
 * 智能搭子主视图。会话栏、待确认、通知与轻提示通过 ChatContainer 插槽注入。
 */
import CompanionHint from '@/agents/companion/components/CompanionHint.vue'
import ChatContainer from '@/platform/chat-core/components/ChatContainer.vue'
import { useChatStore } from '@/platform/chat-core/stores/useChatStore'
import { decideApproval, getApproval, listPendingApprovals } from '@/shared/api/approval-api'
import { getNotification, listNotifications } from '@/shared/api/notification-api'
import { ApiBusinessError } from '@/shared/api/http'
import { logout as logoutApi } from '@/shared/api/auth-api'
import { getSession, listMessages, listSessions } from '@/shared/api/session-api'
import { getRun } from '@/shared/api/run-api'
import { clearSession, loadSession } from '@/shared/auth/session'
import type {
  ApprovalDetail,
  ApprovalNotice,
  ApprovalNoticeDetail,
  PendingApproval,
  RunDetail,
  SessionDetail,
  SessionSummary,
} from '@/shared/types/api.types'
import { computed, onMounted, ref, unref, watch, type Ref } from 'vue'
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
const sessionDetail = ref<SessionDetail | null>(null)
const sessionDetailError = ref('')
const sessionDetailLoading = ref(false)
/** 丢掉过期的会话详情，避免慢请求盖住后打开的另一条。 */
let sessionDetailSeq = 0
const runDetail = ref<RunDetail | null>(null)
const runError = ref('')
const runLoading = ref(false)
/** 最近运行标识还在，但详情接口说这条运行已经不在。 */
const runMissing = ref(false)
/** 丢掉过期的运行详情。 */
let runSeq = 0
const chatRef = ref<InstanceType<typeof ChatContainer> | null>(null)

const pendingRows = ref<PendingApproval[]>([])
const loadingApprovals = ref(false)
const approvalBusyId = ref('')
const approvalError = ref('')
/** 当前展开的待确认。同时只展开一张，再点收起。 */
const expandedApprovalId = ref('')
const approvalDetail = ref<ApprovalDetail | null>(null)
const approvalDetailError = ref('')
const approvalDetailLoading = ref(false)
/** 丢掉过期的详情响应，避免慢请求盖住后点开的另一张。 */
let approvalDetailSeq = 0
const noticeRows = ref<ApprovalNotice[]>([])
const loadingNotices = ref(false)
const noticeError = ref('')
/** 当前展开的通知。同时只展开一张，再点收起。 */
const expandedNoticeId = ref<number | null>(null)
const noticeDetail = ref<ApprovalNoticeDetail | null>(null)
const noticeDetailError = ref('')
const noticeDetailLoading = ref(false)
/** 丢掉过期的通知详情。 */
let noticeDetailSeq = 0
/** 批准后提示重发；不自动发。绑原 sessionId。 */
const resendOffer = ref<PendingApproval | null>(null)
/** 拒绝二次确认目标；不用 window.confirm。 */
const rejectTarget = ref<PendingApproval | null>(null)

const identity = computed(() => session?.displayName || '已登录')
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
    syncSessionDetail()
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
    if (
      expandedApprovalId.value
      && !pendingRows.value.some((row) => row.approvalId === expandedApprovalId.value)
    ) {
      collapseApprovalDetail()
    }
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

/** 拉本人通知。失败只写在通知区，不挡对话，也不改发送状态。 */
async function refreshNotices() {
  loadingNotices.value = true
  noticeError.value = ''
  try {
    noticeRows.value = await listNotifications()
    if (
      expandedNoticeId.value != null
      && !noticeRows.value.some((row) => row.id === expandedNoticeId.value)
    ) {
      collapseNoticeDetail()
    }
  } catch (e) {
    const message = e instanceof Error ? e.message : '通知加载失败'
    noticeError.value = message.includes('unavailable') || message.includes('503')
      ? '通知暂不可用'
      : message
    if (message.includes('重新登录')) {
      clearSession()
      await router.replace('/login')
    }
  } finally {
    loadingNotices.value = false
  }
}

/** 收起通知详情。不改列表，也不改发送状态。 */
function collapseNoticeDetail() {
  noticeDetailSeq += 1
  expandedNoticeId.value = null
  noticeDetail.value = null
  noticeDetailError.value = ''
  noticeDetailLoading.value = false
}

/** 通知状态的短标签。不表示消息已经送到。 */
function noticeStatusLabel(status: string | null | undefined): string {
  if (status === 'PENDING') {
    return '待发送'
  }
  if (status === 'SENT') {
    return '已记录'
  }
  if (status === 'FAILED') {
    return '未送达'
  }
  return (status ?? '').trim()
}

/** 通道的短标签。 */
function noticeChannelLabel(channel: string | null | undefined): string {
  if (channel === 'WEB') {
    return '页面'
  }
  return (channel ?? '').trim()
}

/**
 * 点通知展开创建时间，再点收起。同时只展开一张。
 * 找不到就刷新列表。不切换会话，也不发送。
 */
async function toggleNoticeDetail(row: ApprovalNotice) {
  if (expandedNoticeId.value === row.id) {
    collapseNoticeDetail()
    return
  }
  const seq = ++noticeDetailSeq
  expandedNoticeId.value = row.id
  noticeDetail.value = null
  noticeDetailError.value = ''
  noticeDetailLoading.value = true
  try {
    const detail = await getNotification(row.id)
    if (seq !== noticeDetailSeq) {
      return
    }
    noticeDetail.value = detail
  } catch (e) {
    if (seq !== noticeDetailSeq) {
      return
    }
    const message = e instanceof Error ? e.message : '通知详情加载失败'
    if (message.includes('重新登录')) {
      clearSession()
      await router.replace('/login')
      return
    }
    if (e instanceof ApiBusinessError && e.code === 'NOT_FOUND') {
      collapseNoticeDetail()
      await refreshNotices()
      return
    }
    noticeDetailError.value = message.includes('unavailable') || message.includes('503')
      ? '通知暂不可用'
      : message
  } finally {
    if (seq === noticeDetailSeq) {
      noticeDetailLoading.value = false
    }
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

/** 侧栏点开已有会话：停流、切 id、拉历史。详情由 sessionId 变化单独拉。 */
async function openSession(row: SessionSummary) {
  if (row.sessionId === sessionId.value || busy.value) {
    return
  }
  chatRef.value?.abortIfStreaming()
  sessionId.value = row.sessionId
  historyOpened.value = true
  await loadHistory(row.sessionId)
}

/** 换新 sessionId，不拉历史。未入库的新会话不请求详情。 */
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

/** 丢掉过期的运行详情。不改会话。 */
function clearRunDetail() {
  runSeq += 1
  runDetail.value = null
  runError.value = ''
  runLoading.value = false
  runMissing.value = false
}

/** 丢掉过期的会话详情。不改当前会话，也不改列表。 */
function clearSessionDetail() {
  sessionDetailSeq += 1
  sessionDetail.value = null
  sessionDetailError.value = ''
  sessionDetailLoading.value = false
  clearRunDetail()
}

/**
 * 只有已经出现在列表里的会话才拉详情。
 * 新对话的临时 id 还没入库，不请求，避免当成错误。
 */
function syncSessionDetail() {
  if (sessionRows.value.some((row) => row.sessionId === sessionId.value)) {
    void loadSessionDetail(sessionId.value)
    return
  }
  clearSessionDetail()
}

/**
 * 当前选中会话的状态、轮数和最近运行。不拉正文，也不切换会话。
 * 找不到只刷新列表。其它失败留在这一行。
 */
async function loadSessionDetail(id: string) {
  const seq = ++sessionDetailSeq
  if (sessionDetail.value?.sessionId !== id) {
    sessionDetail.value = null
    clearRunDetail()
  }
  sessionDetailError.value = ''
  sessionDetailLoading.value = true
  try {
    const detail = await getSession(id)
    if (seq !== sessionDetailSeq) {
      return
    }
    sessionDetail.value = detail
    const runId = (detail.lastRunId || '').trim()
    if (runId) {
      void loadRun(runId)
    } else {
      clearRunDetail()
    }
  } catch (e) {
    if (seq !== sessionDetailSeq) {
      return
    }
    const message = e instanceof Error ? e.message : '会话详情加载失败'
    if (message.includes('重新登录')) {
      clearSession()
      await router.replace('/login')
      return
    }
    if (e instanceof ApiBusinessError && e.code === 'NOT_FOUND') {
      clearSessionDetail()
      try {
        sessionRows.value = await listSessions()
      } catch (listError) {
        const listMessage = listError instanceof Error ? listError.message : '会话列表加载失败'
        sessionDetailError.value = listMessage
      }
      return
    }
    sessionDetailError.value = message
  } finally {
    if (seq === sessionDetailSeq) {
      sessionDetailLoading.value = false
    }
  }
}

/** 会话状态的短标签。不认识的原样显示。 */
function sessionStatusLabel(status: string | null | undefined): string {
  if (status === 'ACTIVE') {
    return '进行中'
  }
  if (status === 'ARCHIVED') {
    return '已归档'
  }
  return (status ?? '').trim()
}

/**
 * 最近一次运行的状态和起止时间。不展示步骤、输入输出摘要。
 * 找不到只标运行已不在，不刷新会话列表。
 */
async function loadRun(runId: string) {
  const seq = ++runSeq
  if (runDetail.value?.run.runId !== runId) {
    runDetail.value = null
  }
  runError.value = ''
  runMissing.value = false
  runLoading.value = true
  try {
    const detail = await getRun(runId)
    if (seq !== runSeq) {
      return
    }
    runDetail.value = detail
  } catch (e) {
    if (seq !== runSeq) {
      return
    }
    const message = e instanceof Error ? e.message : '运行详情加载失败'
    if (message.includes('重新登录')) {
      clearSession()
      await router.replace('/login')
      return
    }
    if (e instanceof ApiBusinessError && e.code === 'NOT_FOUND') {
      runDetail.value = null
      runMissing.value = true
      return
    }
    runError.value = message
  } finally {
    if (seq === runSeq) {
      runLoading.value = false
    }
  }
}

/** 运行状态的短标签。不认识的原样显示。 */
function runStatusLabel(status: string | null | undefined): string {
  if (status === 'SUCCEEDED') {
    return '已完成'
  }
  if (status === 'FAILED') {
    return '失败'
  }
  if (status === 'CANCELLED') {
    return '已取消'
  }
  return (status ?? '').trim()
}

/** 毫秒写成可读耗时。没有或负数不占一行。 */
function formatDuration(durationMs: number | null | undefined): string {
  if (durationMs == null || !Number.isFinite(durationMs) || durationMs < 0) {
    return ''
  }
  if (durationMs < 1000) {
    return `${durationMs} 毫秒`
  }
  return `${(durationMs / 1000).toFixed(1)} 秒`
}

/** 收起详情。不改待确认列表。 */
function collapseApprovalDetail() {
  approvalDetailSeq += 1
  expandedApprovalId.value = ''
  approvalDetail.value = null
  approvalDetailError.value = ''
  approvalDetailLoading.value = false
}

/** 待确认在这张列表里固定写成待确认。其它决定只在接口真返回时出现。 */
function approvalDecisionLabel(decision: string | null | undefined): string {
  if (!decision || decision === 'PENDING') {
    return '待确认'
  }
  if (decision === 'APPROVED') {
    return '已批准'
  }
  if (decision === 'REJECTED') {
    return '已拒绝'
  }
  if (decision === 'EXPIRED') {
    return '已过期'
  }
  return decision
}

/** 空串不占一行。 */
function filled(value: string | null | undefined): string {
  return (value ?? '').trim()
}

/**
 * 点卡片展开详情，再点收起。同时只展开一张。
 * 找不到就刷新列表。其它失败留在卡片上，不挡批准和拒绝。不切换会话。
 */
async function toggleApprovalDetail(row: PendingApproval) {
  if (expandedApprovalId.value === row.approvalId) {
    collapseApprovalDetail()
    return
  }
  const seq = ++approvalDetailSeq
  expandedApprovalId.value = row.approvalId
  approvalDetail.value = null
  approvalDetailError.value = ''
  approvalDetailLoading.value = true
  try {
    const detail = await getApproval(row.approvalId)
    if (seq !== approvalDetailSeq) {
      return
    }
    approvalDetail.value = detail
  } catch (e) {
    if (seq !== approvalDetailSeq) {
      return
    }
    const message = e instanceof Error ? e.message : '审批详情加载失败'
    if (message.includes('重新登录')) {
      clearSession()
      await router.replace('/login')
      return
    }
    if (e instanceof ApiBusinessError && e.code === 'NOT_FOUND') {
      collapseApprovalDetail()
      await refreshApprovals()
      return
    }
    approvalDetailError.value = message
  } finally {
    if (seq === approvalDetailSeq) {
      approvalDetailLoading.value = false
    }
  }
}

/** 一轮对话结束后刷新会话与待确认。 */
async function onSent() {
  await Promise.all([refreshSessions(), refreshApprovals(), refreshNotices()])
}

/** 流里出现等待确认等状态时刷新待确认列表。 */
function onStatus(tip: string) {
  if (tip.includes('等待确认') || tip.includes('等待用户确认')) {
    void refreshApprovals()
    void refreshNotices()
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
    void refreshNotices()
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
  void refreshNotices()
})

/** 切换会话时补详情。列表刷新也会再对齐一次。 */
watch(sessionId, () => {
  syncSessionDetail()
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
            <template v-if="row.sessionId === sessionId">
              <span
                v-if="sessionDetailLoading && !sessionDetail"
                class="block truncate text-xs text-[var(--color-text-muted)]"
              >
                加载中…
              </span>
              <template v-else-if="sessionDetail">
                <span class="block truncate text-xs text-[var(--color-text-muted)]">
                  {{ sessionStatusLabel(sessionDetail.status) }} · {{ sessionDetail.messageCount }} 轮
                </span>
                <span
                  v-if="filled(sessionDetail.lastRunId)"
                  class="block truncate text-xs text-[var(--color-text-muted)]"
                >
                  最近运行 {{ shortSession(sessionDetail.lastRunId || '') }}
                </span>
                <template v-if="filled(sessionDetail.lastRunId)">
                  <span
                    v-if="runLoading && !runDetail"
                    class="block truncate text-xs text-[var(--color-text-muted)]"
                  >
                    运行加载中…
                  </span>
                  <template v-else-if="runDetail">
                    <span class="block truncate text-xs text-[var(--color-text-muted)]">
                      {{ runStatusLabel(runDetail.run.status) }}
                      <template v-if="formatDuration(runDetail.run.durationMs)">
                        · {{ formatDuration(runDetail.run.durationMs) }}
                      </template>
                    </span>
                    <span
                      v-if="filled(runDetail.startedAt)"
                      class="block truncate text-xs text-[var(--color-text-muted)]"
                    >
                      开始 {{ runDetail.startedAt }}
                    </span>
                    <span
                      v-if="filled(runDetail.finishedAt)"
                      class="block truncate text-xs text-[var(--color-text-muted)]"
                    >
                      结束 {{ runDetail.finishedAt }}
                    </span>
                  </template>
                  <span v-if="runMissing" class="block truncate text-xs text-[var(--color-text-muted)]">
                    运行已不在
                  </span>
                  <span v-if="runError" class="block truncate text-xs text-[var(--color-danger)]">
                    {{ runError }}
                  </span>
                </template>
              </template>
              <span v-if="sessionDetailError" class="block truncate text-xs text-[var(--color-danger)]">
                {{ sessionDetailError }}
              </span>
            </template>
          </button>
        </div>

        <div class="border-t border-[var(--color-border)] flex flex-col max-h-[34%] min-h-[7rem]">
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
              class="rounded-lg border border-[var(--color-border)] px-2.5 py-2 space-y-1.5 cursor-pointer"
              :class="row.sessionId === sessionId
                ? 'border-[var(--color-brand-primary)]'
                : ''"
              @click="toggleApprovalDetail(row)"
            >
              <p class="text-sm font-medium text-[var(--color-text-main)] truncate">{{ row.toolId }}</p>
              <p class="text-xs text-[var(--color-text-muted)] truncate">会话 {{ shortSession(row.sessionId) }}</p>
              <div
                v-if="expandedApprovalId === row.approvalId"
                class="space-y-0.5"
              >
                <p v-if="approvalDetailLoading" class="text-xs text-[var(--color-text-muted)]">加载中…</p>
                <template v-else-if="approvalDetail">
                  <p class="text-xs text-[var(--color-text-main)]">
                    {{ approvalDecisionLabel(approvalDetail.decision) }}
                  </p>
                  <p
                    v-if="filled(approvalDetail.requestedAt)"
                    class="text-xs text-[var(--color-text-muted)] truncate"
                  >
                    申请 {{ approvalDetail.requestedAt }}
                  </p>
                  <p
                    v-if="filled(approvalDetail.reason)"
                    class="text-xs text-[var(--color-text-muted)] break-words"
                  >
                    意见 {{ approvalDetail.reason }}
                  </p>
                  <p
                    v-if="filled(approvalDetail.decidedAt)"
                    class="text-xs text-[var(--color-text-muted)] truncate"
                  >
                    决定 {{ approvalDetail.decidedAt }}
                  </p>
                </template>
                <p v-if="approvalDetailError" class="text-xs text-[var(--color-danger)]">
                  {{ approvalDetailError }}
                </p>
              </div>
              <div class="flex gap-1.5">
                <button
                  type="button"
                  class="flex-1 rounded-md bg-[var(--color-brand-primary)] px-2 py-1 text-xs text-white disabled:opacity-50"
                  :disabled="busy || !!approvalBusyId"
                  @click.stop="onApprove(row)"
                >
                  批准
                </button>
                <button
                  type="button"
                  class="flex-1 rounded-md border border-[var(--color-border-strong)] px-2 py-1 text-xs hover:bg-[var(--color-surface-muted)] disabled:opacity-50"
                  :disabled="busy || !!approvalBusyId"
                  @click.stop="onReject(row)"
                >
                  拒绝
                </button>
              </div>
            </div>
          </div>
        </div>

        <div class="border-t border-[var(--color-border)] flex flex-col max-h-[28%] min-h-[6rem]">
          <div class="px-3 py-2 flex items-center justify-between gap-2">
            <p class="text-sm font-semibold text-[var(--color-brand-primary)]">
              通知
              <span v-if="noticeRows.length" class="font-normal text-[var(--color-text-muted)]">
                · {{ noticeRows.length }}
              </span>
            </p>
            <button
              type="button"
              class="text-xs text-[var(--color-text-muted)] hover:text-[var(--color-brand-primary)] disabled:opacity-50"
              :disabled="loadingNotices"
              @click="refreshNotices"
            >
              刷新
            </button>
          </div>
          <p v-if="noticeError" class="px-3 pb-1 text-xs text-[var(--color-danger)]">{{ noticeError }}</p>
          <div class="flex-1 overflow-y-auto px-2 pb-2 space-y-1.5">
            <p v-if="loadingNotices" class="px-1 py-2 text-xs text-[var(--color-text-muted)]">加载中…</p>
            <p
              v-else-if="noticeRows.length === 0"
              class="px-1 py-2 text-xs text-[var(--color-text-muted)]"
            >
              暂无通知
            </p>
            <div
              v-for="row in noticeRows"
              :key="row.id"
              class="rounded-lg border border-[var(--color-border)] px-2.5 py-2 space-y-1 cursor-pointer"
              @click="toggleNoticeDetail(row)"
            >
              <p class="text-sm font-medium text-[var(--color-text-main)] truncate">
                审批 {{ shortSession(row.approvalId) }}
              </p>
              <p class="text-xs text-[var(--color-text-muted)] truncate">
                {{ noticeStatusLabel(row.sendStatus) }}
                <template v-if="noticeChannelLabel(row.channel)"> · {{ noticeChannelLabel(row.channel) }}</template>
              </p>
              <div v-if="expandedNoticeId === row.id" class="space-y-0.5">
                <p v-if="noticeDetailLoading" class="text-xs text-[var(--color-text-muted)]">加载中…</p>
                <template v-else-if="noticeDetail">
                  <p
                    v-if="filled(noticeDetail.createdAt)"
                    class="text-xs text-[var(--color-text-muted)] truncate"
                  >
                    创建 {{ noticeDetail.createdAt }}
                  </p>
                  <p v-else class="text-xs text-[var(--color-text-muted)]">没有创建时间</p>
                </template>
                <p v-if="noticeDetailError" class="text-xs text-[var(--color-danger)]">
                  {{ noticeDetailError }}
                </p>
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
