<script setup lang="ts">
/**
 * 搭子 Step 1 聊天页。消息只在浏览器内存；流式只吃 text_block_delta。
 */
import { useStreaming } from '@/platform/chat-core/composables/useStreaming'
import { useChatStore } from '@/platform/chat-core/stores/useChatStore'
import { logout as logoutApi } from '@/shared/api/auth-api'
import { clearSession, loadSession } from '@/shared/auth/session'
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

const AGENT_ID = 'companion-v1'

const router = useRouter()
const chatStore = useChatStore()
const { startStream } = useStreaming()

const session = loadSession()
const sessionId = ref(crypto.randomUUID())
const input = ref('')
const error = ref('')
const statusTip = ref('')
const streaming = ref(false)
const listEl = ref<HTMLElement | null>(null)

let abortController: AbortController | null = null

const messages = computed(() => chatStore.getMessages(AGENT_ID, sessionId.value))
const identity = computed(() => `${session?.tenantId || ''} / ${session?.userId || ''}`)

/** 滚动到底部，流式追加时保持贴底。 */
async function scrollToBottom() {
  await nextTick()
  if (listEl.value) {
    listEl.value.scrollTop = listEl.value.scrollHeight
  }
}

watch(messages, () => {
  void scrollToBottom()
}, { deep: true })

/** 发送用户输入并消费 SSE。 */
async function send() {
  const text = input.value.trim()
  if (!text || streaming.value) {
    return
  }
  error.value = ''
  statusTip.value = ''
  input.value = ''

  chatStore.appendMessage(AGENT_ID, sessionId.value, {
    id: crypto.randomUUID(),
    role: 'user',
    content: text,
  })

  const assistant = chatStore.appendMessage(AGENT_ID, sessionId.value, {
    id: crypto.randomUUID(),
    role: 'assistant',
    content: '',
    streaming: true,
  })

  streaming.value = true
  abortController = new AbortController()

  try {
    await startStream(
      {
        agentId: AGENT_ID,
        sessionId: sessionId.value,
        text,
      },
      {
        onDelta: (chunk) => {
          assistant.content += chunk
        },
        onStatus: (tip) => {
          statusTip.value = tip
        },
      },
      abortController.signal,
    )
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') {
      statusTip.value = '已停止生成'
    } else {
      error.value = e instanceof Error ? e.message : '发送失败'
      if (error.value.includes('重新登录')) {
        clearSession()
        await router.replace('/login')
      }
    }
  } finally {
    assistant.streaming = false
    streaming.value = false
    abortController = null
  }
}

/** 取消当前流，解锁输入框。 */
function stop() {
  abortController?.abort()
}

/** 换新 sessionId 并清空本地消息。 */
function newChat() {
  if (streaming.value) {
    stop()
  }
  sessionId.value = crypto.randomUUID()
  chatStore.clearMessages(AGENT_ID, sessionId.value)
  error.value = ''
  statusTip.value = ''
  input.value = ''
}

/** 调退出接口并回登录页。 */
async function onLogout() {
  if (streaming.value) {
    stop()
  }
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

onBeforeUnmount(() => {
  abortController?.abort()
})
</script>

<template>
  <div class="h-full flex flex-col">
    <header class="flex items-center justify-between gap-3 border-b border-slate-200 bg-white px-4 py-3">
      <div>
        <h1 class="text-lg font-semibold text-[var(--color-brand-primary)]">智能搭子</h1>
        <p class="text-xs text-[var(--color-text-muted)]">{{ identity }} · {{ sessionId.slice(0, 8) }}…</p>
      </div>
      <div class="flex items-center gap-2">
        <button
          type="button"
          class="rounded-lg border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50"
          @click="newChat"
        >
          新对话
        </button>
        <button
          type="button"
          class="rounded-lg border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50"
          @click="onLogout"
        >
          退出
        </button>
      </div>
    </header>

    <div ref="listEl" class="flex-1 overflow-y-auto px-4 py-4 space-y-4">
      <p v-if="messages.length === 0" class="text-sm text-[var(--color-text-muted)] text-center mt-12">
        发送一条消息，验证 /api/chat/stream 是否通。
      </p>
      <div
        v-for="msg in messages"
        :key="msg.id"
        class="flex"
        :class="msg.role === 'user' ? 'justify-end' : 'justify-start'"
      >
        <div
          class="max-w-[80%] whitespace-pre-wrap break-words px-4 py-2.5 text-sm leading-relaxed"
          :class="msg.role === 'user'
            ? 'bg-[var(--color-brand-primary)] text-white rounded-[var(--chat-bubble-radius)] rounded-br-sm'
            : 'bg-white border border-slate-200 text-[var(--color-text-main)] rounded-[var(--chat-bubble-radius)] rounded-bl-sm'"
        >
          <span v-if="msg.content">{{ msg.content }}</span>
          <span v-else-if="msg.streaming" class="text-[var(--color-text-muted)]">…</span>
        </div>
      </div>
    </div>

    <div class="border-t border-slate-200 bg-white px-4 py-3 space-y-2">
      <p v-if="statusTip" class="text-xs text-[var(--color-text-muted)]">{{ statusTip }}</p>
      <p v-if="error" class="text-sm text-[var(--color-danger)]">{{ error }}</p>
      <form class="flex gap-2" @submit.prevent="send">
        <input
          v-model="input"
          class="flex-1 rounded-xl border border-slate-300 px-3 py-2.5 outline-none focus-visible:ring-2 focus-visible:ring-pink-400 disabled:bg-slate-50"
          placeholder="输入消息…"
          :disabled="streaming"
        />
        <button
          v-if="streaming"
          type="button"
          class="rounded-xl border border-slate-300 px-4 py-2.5 text-sm"
          @click="stop"
        >
          停止
        </button>
        <button
          v-else
          type="submit"
          class="rounded-xl bg-[var(--color-brand-primary)] px-4 py-2.5 text-sm text-white disabled:opacity-50"
          :disabled="!input.trim()"
        >
          发送
        </button>
      </form>
    </div>
  </div>
</template>
