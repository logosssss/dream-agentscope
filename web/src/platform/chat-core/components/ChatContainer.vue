<script setup lang="ts">
/**
 * 通用对话内核：消息列表、输入框与 SSE 发送。
 * 通过 sidebar / before-input / header-actions 插槽让 Agent 定制形态。
 */
import MarkdownContent from '@/platform/chat-core/components/MarkdownContent.vue'
import { useStreaming } from '@/platform/chat-core/composables/useStreaming'
import { useChatStore } from '@/platform/chat-core/stores/useChatStore'
import { getQuota } from '@/shared/api/quota-api'
import { clearSession } from '@/shared/auth/session'
import type { QuotaMeter, QuotaSnapshot } from '@/shared/types/api.types'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useSlots, watch } from 'vue'
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
    /** 空态副文案；没有就不渲染 */
    emptySubHint?: string
  }>(),
  {
    identity: '',
    title: '对话',
    loadingHistory: false,
    emptyHint: '发送一条消息开始对话。',
    emptySubHint: '',
  },
)

const emit = defineEmits<{
  /** 一轮流式成功结束后通知外层（例如刷新会话列表） */
  sent: []
  /** 需要重新登录时抛出，外层可清路由 */
  needLogin: []
  /** 非正文状态提示，例如等待确认；外层可据此刷新待确认 */
  status: [message: string]
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
const quota = ref<QuotaSnapshot | null>(null)
const quotaError = ref('')

/** 顶栏里的一项额度。over 表示已达到正数上限。 */
interface QuotaPart {
  text: string
  over: boolean
}

let abortController: AbortController | null = null

const messages = computed(() => chatStore.getMessages(props.agentId, props.sessionId))
const shortSession = computed(() => props.sessionId.slice(0, 8))
/** 有 sidebar 插槽时才留左侧栏位，占位 Agent 可全宽。 */
const hasSidebar = computed(() => !!slots.sidebar)

/** 有上限才展示 token、费用和同时运行；请求始终展示。 */
const quotaParts = computed(() => {
  const snapshot = quota.value
  if (!snapshot) {
    return [] as QuotaPart[]
  }
  return [
    meterPart('请求', snapshot.requests, true),
    meterPart('token', snapshot.tokens, false),
    meterPart('费用', snapshot.cost, false),
    meterPart('同时运行', snapshot.concurrent, false),
  ].filter((part): part is QuotaPart => part !== null)
})

/**
 * limit 为空表示这项没有配置，非请求项不占位。
 * 0 表示不限额。正数上限且已用达到上限时标红。
 */
function meterPart(label: string, meter: QuotaMeter | null | undefined, always: boolean): QuotaPart | null {
  if (!meter || (meter.limit == null && !always)) {
    return null
  }
  const over = meter.limit != null && meter.limit > 0 && meter.used >= meter.limit
  let text = `${label} ${meter.used}`
  if (meter.limit != null && meter.limit > 0) {
    text += ` / ${meter.limit}`
  } else if (meter.limit === 0) {
    text += '（不限额）'
  }
  return { text, over }
}

/** 拉当日额度。失败只写顶栏，不挡输入。登录失效仍回登录页。 */
async function refreshQuota() {
  quotaError.value = ''
  try {
    quota.value = await getQuota()
  } catch (e) {
    const message = e instanceof Error ? e.message : '额度加载失败'
    if (message.includes('重新登录')) {
      clearSession()
      emit('needLogin')
      await router.replace('/login')
      return
    }
    quota.value = null
    quotaError.value = '额度暂不可用'
  }
}

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

/**
 * 发送用户输入并消费 SSE。
 * @param overrideText 有值时用该文案发送（重发上一条），不改输入框草稿
 */
async function send(overrideText?: string) {
  const text = (overrideText ?? input.value).trim()
  if (!text || streaming.value || props.loadingHistory) {
    return
  }
  error.value = ''
  statusTip.value = ''
  if (overrideText === undefined) {
    input.value = ''
  }

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
          emit('status', tip)
        },
      },
      abortController.signal,
    )
    emit('sent')
    void refreshQuota()
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
      if (!assistant.content) {
        chatStore.dropMessage(props.agentId, props.sessionId, assistant.id)
      }
      void refreshQuota()
    }
  } finally {
    assistant.streaming = false
    streaming.value = false
    abortController = null
  }
}

/**
 * 把当前会话最后一条用户消息再发一轮。没有用户气泡时返回 false。
 * 批准后由外层按钮触发，不自动调用。
 */
async function resendLastUserMessage(): Promise<boolean> {
  if (streaming.value || props.loadingHistory) {
    return false
  }
  const msgs = chatStore.getMessages(props.agentId, props.sessionId)
  for (let i = msgs.length - 1; i >= 0; i -= 1) {
    const row = msgs[i]
    if (row.role === 'user' && row.content.trim()) {
      await send(row.content)
      return true
    }
  }
  return false
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
  resendLastUserMessage,
  /** 外层把列表/历史错误展示到输入区上方。 */
  setError(message: string) {
    error.value = message
  },
  clearError() {
    error.value = ''
  },
})

onMounted(() => {
  void refreshQuota()
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
      <header class="flex items-center justify-between gap-3 border-b border-[var(--color-border)] bg-[var(--color-surface-card)] px-4 py-3">
        <div class="min-w-0 space-y-1">
          <h1 class="text-lg font-semibold text-[var(--color-brand-primary)] truncate">{{ title }}</h1>
          <p v-if="identity" class="text-sm text-[var(--color-text-main)] truncate">
            {{ identity }}
          </p>
          <p v-if="quotaError" class="text-xs text-[var(--color-danger)]">{{ quotaError }}</p>
          <p v-else-if="quotaParts.length" class="text-xs text-[var(--color-text-muted)]">
            今日用量
            <template v-for="(part, index) in quotaParts" :key="part.text">
              <span v-if="index === 0">：</span>
              <span v-else> · </span>
              <span :class="part.over ? 'text-[var(--color-danger)] font-medium' : ''">{{ part.text }}</span>
            </template>
          </p>
          <p class="text-[11px] text-[var(--color-text-muted)]/80 font-mono truncate" :title="sessionId">
            会话 {{ shortSession }}
          </p>
        </div>
        <div class="flex items-center gap-2 shrink-0">
          <slot name="header-actions" />
        </div>
      </header>

      <div ref="listEl" class="flex-1 min-h-0 overflow-y-auto px-4 py-4 space-y-4">
        <p v-if="loadingHistory" class="text-sm text-[var(--color-text-muted)] text-center mt-12">
          正在加载历史消息…
        </p>
        <div
          v-else-if="messages.length === 0"
          class="flex flex-col items-center justify-center text-center px-6 pt-16 pb-8 gap-2"
        >
          <p class="text-base text-[var(--color-text-main)]">{{ emptyHint }}</p>
          <p
            v-if="emptySubHint"
            class="text-sm text-[var(--color-text-muted)] max-w-sm leading-relaxed"
          >
            {{ emptySubHint }}
          </p>
        </div>
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
              : 'bg-[var(--color-surface-card)] border border-[var(--color-border)] text-[var(--color-text-main)] rounded-[var(--chat-bubble-radius)] rounded-bl-sm'"
          >
            <template v-if="msg.role === 'assistant' && msg.content">
              <MarkdownContent :source="msg.content" />
            </template>
            <span v-else-if="msg.content" class="whitespace-pre-wrap">{{ msg.content }}</span>
            <span v-else-if="msg.streaming" class="text-[var(--color-text-muted)]">…</span>
          </div>
        </div>
      </div>

      <div class="border-t border-[var(--color-border)] bg-[var(--color-surface-card)] px-4 py-3 space-y-2">
        <slot name="before-input" />
        <p v-if="statusTip" class="text-xs text-[var(--color-text-muted)]">{{ statusTip }}</p>
        <p v-if="error" class="text-sm text-[var(--color-danger)]">{{ error }}</p>
        <form class="flex gap-2" @submit.prevent="send()">
          <input
            v-model="input"
            class="flex-1 rounded-xl border border-[var(--color-border-strong)] bg-[var(--color-surface-card)] text-[var(--color-text-main)] px-3 py-2.5 outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-brand-primary)] disabled:bg-[var(--color-surface-muted)]"
            placeholder="输入消息…"
            :disabled="streaming || loadingHistory"
          />
          <button
            v-if="streaming"
            type="button"
            class="rounded-xl border border-[var(--color-border-strong)] px-4 py-2.5 text-sm hover:bg-[var(--color-surface-muted)]"
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
