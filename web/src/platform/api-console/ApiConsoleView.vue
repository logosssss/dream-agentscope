<script setup lang="ts">
/**
 * API Channel 联调控制台。复用 chat-core，SSE 只打 /api/agent/chat/stream。
 * 侧栏只列 channel=API 的会话；不替代 companion 主聊天，也不挂待确认/通知。
 */
import ChatContainer from '@/platform/chat-core/components/ChatContainer.vue'
import { useChatStore } from '@/platform/chat-core/stores/useChatStore'
import { ApiBusinessError } from '@/shared/api/http'
import { getSession, listMessages, listSessions } from '@/shared/api/session-api'
import { clearSession, loadSession } from '@/shared/auth/session'
import type { SessionDetail, SessionSummary } from '@/shared/types/api.types'
import { computed, onBeforeUnmount, onMounted, ref, unref, watch, type Ref } from 'vue'
import { useRouter } from 'vue-router'

/** 与搭子同一 agent；请求体 agentId 走真实注册名。 */
const AGENT_ID = 'companion-v1'
/** 入库 channel 枚举名；Harness 侧 id 是 api，会话表写 API。 */
const API_SESSION_CHANNEL = 'API'
const STREAM_PATH = '/api/agent/chat/stream'

const router = useRouter()
const chatStore = useChatStore()

const session = loadSession()
const sessionId = ref<string>(crypto.randomUUID())
/** 当前是否正在看库里拉下来的会话（空态文案）。 */
const historyOpened = ref(false)
const loadingHistory = ref(false)
const loadingSessions = ref(false)
const sessionRows = ref<SessionSummary[]>([])
const sessionDetail = ref<SessionDetail | null>(null)
const sessionDetailError = ref('')
const sessionDetailLoading = ref(false)
/** 丢掉过期的会话详情，避免慢请求盖住后打开的另一条。 */
let sessionDetailSeq = 0
const chatRef = ref<InstanceType<typeof ChatContainer> | null>(null)

const identity = computed(() => session?.displayName || '已登录')
/** 当前 id 还不在 API 历史列表里：空白新对话草稿。 */
const isDraftSession = computed(
  () => !sessionRows.value.some((row) => row.sessionId === sessionId.value),
)
const emptyHint = computed(() => {
  if (historyOpened.value) {
    return '该 API 会话还没有已保存的消息（只有成功结束的对话会写入历史）。'
  }
  if (isDraftSession.value) {
    return '这是 API Channel 新对话。发送后左侧会出现 channel=API 的历史。'
  }
  return '发送一条消息，或从左侧打开 API 历史会话。'
})
const emptySubHint = computed(() => {
  if (historyOpened.value) {
    return ''
  }
  return '本页只调用 /api/agent/chat/stream，不走 /api/chat/stream。'
})
const newChatFlash = ref('')
let newChatFlashTimer: ReturnType<typeof setTimeout> | null = null

/** 子组件 expose 的 streaming 是 Ref，要用 unref。 */
const busy = computed(() => {
  const flag = chatRef.value?.streaming as Ref<boolean> | boolean | undefined
  return loadingHistory.value || !!unref(flag)
})

/** 拉本人会话后只保留 API 通道，避免和 WEB 搭子历史混在一起。 */
async function refreshSessions() {
  loadingSessions.value = true
  try {
    const all = await listSessions()
    sessionRows.value = all.filter((row) => (row.channel || '').toUpperCase() === API_SESSION_CHANNEL)
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

/**
 * 把服务端气泡灌进本地 store。
 * role 只认 user/assistant，其它丢掉。
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

/** 侧栏点开已有 API 会话：停流、切 id、拉历史。 */
async function openSession(row: SessionSummary) {
  if (row.sessionId === sessionId.value || busy.value) {
    return
  }
  chatRef.value?.abortIfStreaming()
  sessionId.value = row.sessionId
  historyOpened.value = true
  await loadHistory(row.sessionId)
}

/**
 * 换新 sessionId。已在空白草稿上再点时不换 id，只给反馈。
 */
function newChat() {
  chatRef.value?.abortIfStreaming()
  chatRef.value?.clearError()
  const alreadyDraft =
    isDraftSession.value && chatStore.getMessages(AGENT_ID, sessionId.value).length === 0
  if (alreadyDraft) {
    flashNewChat('已是空白新对话，直接输入即可')
    return
  }
  const previousId = sessionId.value
  sessionId.value = crypto.randomUUID()
  historyOpened.value = false
  sessionDetail.value = null
  sessionDetailError.value = ''
  if (previousId) {
    chatStore.clearMessages(AGENT_ID, previousId)
  }
  flashNewChat('已开始 API 新对话')
}

/** 顶栏短提示。重复点击会重置计时。 */
function flashNewChat(message: string) {
  newChatFlash.value = message
  if (newChatFlashTimer) {
    clearTimeout(newChatFlashTimer)
  }
  newChatFlashTimer = setTimeout(() => {
    newChatFlash.value = ''
    newChatFlashTimer = null
  }, 2200)
}

/** 列表标题：空标题用短 id。 */
function rowLabel(row: SessionSummary): string {
  const title = (row.title || '').trim()
  if (title) {
    return title
  }
  return `${row.sessionId.slice(0, 8)}…`
}

function clearSessionDetail() {
  sessionDetailSeq += 1
  sessionDetail.value = null
  sessionDetailError.value = ''
  sessionDetailLoading.value = false
}

/** 只有已出现在 API 列表里的会话才拉详情。 */
function syncSessionDetail() {
  if (sessionRows.value.some((row) => row.sessionId === sessionId.value)) {
    void loadSessionDetail(sessionId.value)
    return
  }
  clearSessionDetail()
}

async function loadSessionDetail(id: string) {
  const seq = ++sessionDetailSeq
  if (sessionDetail.value?.sessionId !== id) {
    sessionDetail.value = null
  }
  sessionDetailError.value = ''
  sessionDetailLoading.value = true
  try {
    const detail = await getSession(id)
    if (seq !== sessionDetailSeq) {
      return
    }
    sessionDetail.value = detail
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
      await refreshSessions()
      return
    }
    sessionDetailError.value = message
  } finally {
    if (seq === sessionDetailSeq) {
      sessionDetailLoading.value = false
    }
  }
}

function sessionStatusLabel(status: string | null | undefined): string {
  if (status === 'ACTIVE') {
    return '进行中'
  }
  if (status === 'CLOSED') {
    return '已结束'
  }
  return (status ?? '').trim()
}

function formatRelativeActiveAt(raw: string | null | undefined): string {
  const text = (raw ?? '').trim()
  if (!text) {
    return ''
  }
  const time = Date.parse(text)
  if (!Number.isFinite(time)) {
    return text
  }
  const delta = Date.now() - time
  if (delta < 0) {
    return text
  }
  const minute = 60_000
  const hour = 60 * minute
  const day = 24 * hour
  if (delta < minute) {
    return '刚刚'
  }
  if (delta < hour) {
    return `${Math.floor(delta / minute)} 分钟前`
  }
  if (delta < day) {
    return `${Math.floor(delta / hour)} 小时前`
  }
  if (delta < 7 * day) {
    return `${Math.floor(delta / day)} 天前`
  }
  return text.slice(0, 16).replace('T', ' ')
}

/** 发送成功后刷新 API 会话列表。 */
async function onSent() {
  await refreshSessions()
}

onMounted(() => {
  void refreshSessions()
})

onBeforeUnmount(() => {
  if (newChatFlashTimer) {
    clearTimeout(newChatFlashTimer)
    newChatFlashTimer = null
  }
})

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
    title="API 控制台"
    :identity="identity"
    :loading-history="loadingHistory"
    :empty-hint="emptyHint"
    :empty-sub-hint="emptySubHint"
    :stream-path="STREAM_PATH"
    @sent="onSent"
    @need-login="() => {}"
  >
    <template #sidebar>
      <aside class="w-64 h-full border-r border-[var(--color-border)] bg-[var(--color-surface-card)] flex flex-col">
        <div class="p-3 border-b border-[var(--color-border)] space-y-2">
          <p class="text-sm font-semibold text-[var(--color-brand-primary)]">API 会话</p>
          <p class="text-xs text-[var(--color-text-muted)] leading-snug">
            仅 channel=API；流式走 /api/agent/chat/stream
          </p>
          <button
            type="button"
            class="w-full rounded-lg px-3 py-2 text-sm disabled:opacity-50 transition-colors"
            :class="isDraftSession
              ? 'border-2 border-[var(--color-brand-primary)] bg-[color-mix(in_srgb,var(--color-brand-primary)_14%,var(--color-surface-card))] text-[var(--color-brand-primary)] font-medium'
              : 'bg-[var(--color-brand-primary)] text-white'"
            :disabled="busy"
            @click="newChat"
          >
            {{ isDraftSession ? '新对话（当前）' : '新对话' }}
          </button>
          <p
            v-if="newChatFlash"
            class="text-xs text-[var(--color-brand-primary)] leading-snug"
            role="status"
          >
            {{ newChatFlash }}
          </p>
        </div>
        <div class="flex-1 min-h-0 overflow-y-auto p-2 space-y-1.5">
          <div
            v-if="isDraftSession"
            class="w-full text-left rounded-lg px-2.5 py-2.5 text-sm font-medium text-[var(--color-brand-primary)] ring-1 ring-[var(--color-brand-primary)]"
            :style="{ backgroundColor: 'color-mix(in srgb, var(--color-brand-primary) 12%, var(--color-surface-card))' }"
          >
            <span class="block truncate">新对话（未发送）</span>
            <span class="block truncate text-xs text-[var(--color-text-muted)] font-normal">
              草稿 · API
            </span>
          </div>
          <p v-if="loadingSessions" class="px-2 py-2 text-xs text-[var(--color-text-muted)]">加载中…</p>
          <p
            v-else-if="sessionRows.length === 0 && !isDraftSession"
            class="px-2 py-2 text-xs text-[var(--color-text-muted)]"
          >
            还没有 API 历史会话
          </p>
          <button
            v-for="row in sessionRows"
            :key="row.sessionId"
            type="button"
            class="w-full text-left rounded-lg px-2.5 py-2.5 text-sm hover:bg-[var(--color-surface-muted)] disabled:opacity-50"
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
            <span
              class="block truncate text-xs text-[var(--color-text-muted)]"
              :title="row.lastActiveAt || undefined"
            >
              {{ formatRelativeActiveAt(row.lastActiveAt) }}
            </span>
            <template v-if="row.sessionId === sessionId">
              <span
                v-if="sessionDetailLoading && !sessionDetail"
                class="block truncate text-xs text-[var(--color-text-muted)]"
              >
                加载中…
              </span>
              <span
                v-else-if="sessionDetail"
                class="block truncate text-xs text-[var(--color-text-muted)]"
              >
                {{ sessionStatusLabel(sessionDetail.status) }} · {{ sessionDetail.messageCount }} 轮
              </span>
              <span v-if="sessionDetailError" class="block truncate text-xs text-[var(--color-danger)]">
                {{ sessionDetailError }}
              </span>
            </template>
          </button>
        </div>
      </aside>
    </template>
  </ChatContainer>
</template>
