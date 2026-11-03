<script setup lang="ts">
/**
 * 智能搭子主视图。会话栏与轻提示通过 ChatContainer 插槽注入，突出搭子形态。
 */
import CompanionHint from '@/agents/companion/components/CompanionHint.vue'
import ChatContainer from '@/platform/chat-core/components/ChatContainer.vue'
import { useChatStore } from '@/platform/chat-core/stores/useChatStore'
import { logout as logoutApi } from '@/shared/api/auth-api'
import { listMessages, listSessions } from '@/shared/api/session-api'
import { clearSession, loadSession } from '@/shared/auth/session'
import type { SessionSummary } from '@/shared/types/api.types'
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

const identity = computed(() => `${session?.tenantId || ''} / ${session?.userId || ''}`)
const emptyHint = computed(() =>
  historyOpened.value
    ? '该会话还没有已保存的消息（只有成功结束的对话会写入历史）。'
    : '发送一条消息开始对话，或从左侧打开历史会话。',
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

onMounted(() => {
  void refreshSessions()
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
    @sent="refreshSessions"
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
        <div class="flex-1 overflow-y-auto p-2 space-y-1">
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
      </aside>
    </template>

    <template #before-input>
      <CompanionHint />
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
</template>
