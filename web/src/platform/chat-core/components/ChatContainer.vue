<script setup lang="ts">
/**
 * 通用对话内核：消息列表、输入框与 SSE 发送。
 * 通过 sidebar / before-input / header-actions 插槽让 Agent 定制形态。
 */
import MarkdownContent from '@/platform/chat-core/components/MarkdownContent.vue'
import { useStreaming } from '@/platform/chat-core/composables/useStreaming'
import { useChatStore } from '@/platform/chat-core/stores/useChatStore'
import { clearSession } from '@/shared/auth/session'
import { computed, nextTick, onBeforeUnmount, ref, useSlots, watch } from 'vue'
import { useRouter } from 'vue-router'

const props = withDefaults(
  defineProps<{
    agentId: string
    sessionId: string
    /** 顶栏副标题，一般是租户/用户 */
    identity?: string
    /** 页面标题 */
    title?: string
    /** 拉历史时锁定输入 */
    loadingHistory?: boolean
    /** 空列表提示 */
    emptyHint?: string
  }>(),
  {
    identity: '',
    title: '对话',
    loadingHistory: false,
    emptyHint: '发送一条消息开始对话。',
  },
)

const emit = defineEmits<{
  /** 一轮流式成功结束后通知外层（例如刷新会话列表） */
  sent: []
  /** 需要重新登录时抛出，外层可清路由 */
  needLogin: []
}>()

const router = useRouter()
const slots = useSlots()
const chatStore = useChatStore()
const { startStream } = useStreaming()

const input = ref('')
const error = ref('')
const statusTip = ref('')
const streaming = ref(false)
const listEl = ref<HTMLElement | null>(null)

let abortController: AbortController | null = null

const messages = computed(() => chatStore.getMessages(props.agentId, props.sessionId))
const shortSession = computed(() => props.sessionId.slice(0, 8))
/** 有 sidebar 插槽时才留左侧栏位，占位 Agent 可全宽。 */
const hasSidebar = computed(() => !!slots.sidebar)

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

watch(
  () => props.sessionId,
  () => {
    error.value = ''
    statusTip.value = ''
    input.value = ''
  },
)

/** 发送用户输入并消费 SSE。 */
async function send() {
  const text = input.value.trim()
  if (!text || streaming.value || props.loadingHistory) {
    return
  }
  error.value = ''
  statusTip.value = ''
  input.value = ''

  chatStore.appendMessage(props.agentId, props.sessionId, {
    id: crypto.randomUUID(),
    role: 'user',
    content: text,
  })

  const assistant = chatStore.appendMessage(props.agentId, props.sessionId, {
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
        agentId: props.agentId,
        sessionId: props.sessionId,
        text,
      },
      {
        onDelta: (chunk) => {
          assistant.content += chunk
        },
        onFinal: (full) => {
          // 人设/危机出口可能只给全文；有增量时也用终态覆盖，避免和护栏改写不一致
          assistant.content = full
        },
        onStatus: (tip) => {
          statusTip.value = tip
        },
      },
      abortController.signal,
    )
    emit('sent')
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') {
      statusTip.value = '已停止生成'
    } else {
      error.value = e instanceof Error ? e.message : '发送失败'
      if (error.value.includes('重新登录')) {
        clearSession()
        emit('needLogin')
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

/** 外层切会话前可调用，避免旧流写到新会话。 */
function abortIfStreaming() {
  if (streaming.value) {
    stop()
  }
}

defineExpose({
  streaming,
  abortIfStreaming,
  /** 外层把列表/历史错误展示到输入区上方。 */
  setError(message: string) {
    error.value = message
  },
  clearError() {
    error.value = ''
  },
})

onBeforeUnmount(() => {
  abortController?.abort()
})
</script>

<template>
  <div class="h-full min-w-0 flex">
    <div v-if="hasSidebar" class="shrink-0 h-full">
      <slot name="sidebar" />
    </div>

    <div class="flex-1 min-w-0 h-full flex flex-col">
      <header class="flex items-center justify-between gap-3 border-b border-slate-200 bg-white px-4 py-3">
        <div>
          <h1 class="text-lg font-semibold text-[var(--color-brand-primary)]">{{ title }}</h1>
          <p class="text-xs text-[var(--color-text-muted)]">
            <span v-if="identity">{{ identity }} · </span>{{ shortSession }}…
          </p>
        </div>
        <div class="flex items-center gap-2">
          <slot name="header-actions" />
        </div>
      </header>

      <div ref="listEl" class="flex-1 overflow-y-auto px-4 py-4 space-y-4">
        <p v-if="loadingHistory" class="text-sm text-[var(--color-text-muted)] text-center mt-12">
          正在加载历史消息…
        </p>
        <p
          v-else-if="messages.length === 0"
          class="text-sm text-[var(--color-text-muted)] text-center mt-12"
        >
          {{ emptyHint }}
        </p>
        <div
          v-for="msg in messages"
          :key="msg.id"
          class="flex"
          :class="msg.role === 'user' ? 'justify-end' : 'justify-start'"
        >
          <div
            class="max-w-[80%] break-words px-4 py-2.5 text-sm leading-relaxed"
            :class="msg.role === 'user'
              ? 'whitespace-pre-wrap bg-[var(--color-brand-primary)] text-white rounded-[var(--chat-bubble-radius)] rounded-br-sm'
              : 'bg-white border border-slate-200 text-[var(--color-text-main)] rounded-[var(--chat-bubble-radius)] rounded-bl-sm'"
          >
            <template v-if="msg.role === 'assistant' && msg.content">
              <MarkdownContent :source="msg.content" />
            </template>
            <span v-else-if="msg.content" class="whitespace-pre-wrap">{{ msg.content }}</span>
            <span v-else-if="msg.streaming" class="text-[var(--color-text-muted)]">…</span>
          </div>
        </div>
      </div>

      <div class="border-t border-slate-200 bg-white px-4 py-3 space-y-2">
        <slot name="before-input" />
        <p v-if="statusTip" class="text-xs text-[var(--color-text-muted)]">{{ statusTip }}</p>
        <p v-if="error" class="text-sm text-[var(--color-danger)]">{{ error }}</p>
        <form class="flex gap-2" @submit.prevent="send">
          <input
            v-model="input"
            class="flex-1 rounded-xl border border-slate-300 px-3 py-2.5 outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-brand-primary)] disabled:bg-slate-50"
            placeholder="输入消息…"
            :disabled="streaming || loadingHistory"
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
            :disabled="!input.trim() || loadingHistory"
          >
            发送
          </button>
        </form>
      </div>
    </div>
  </div>
</template>
