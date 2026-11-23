<script setup lang="ts">
/**
 * 通用对话内核：消息列表、输入框与 SSE 发送。
 * 通过 sidebar / before-input / header-actions 插槽让 Agent 定制形态。
 */
import MarkdownContent from '@/platform/chat-core/components/MarkdownContent.vue'
import { useStreaming } from '@/platform/chat-core/composables/useStreaming'
import { useChatStore, type ChatMessageAttachment } from '@/platform/chat-core/stores/useChatStore'
import { uploadAttachmentFile, downloadAttachment } from '@/shared/api/attachment-api'
import { getQuota } from '@/shared/api/quota-api'
import { clearSession } from '@/shared/auth/session'
import type { QuotaMeter, QuotaSnapshot } from '@/shared/types/api.types'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useSlots, watch } from 'vue'
import { useRouter } from 'vue-router'

/** 随消息附件上限，与服务端一致。 */
const MAX_ATTACHMENTS = 3
/** 单文件上限 5MB。 */
const MAX_FILE_BYTES = 5 * 1024 * 1024
const IMAGE_MIME = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif'])
const DOC_EXT = new Set([
  'pdf',
  'docx',
  'txt',
  'md',
  'markdown',
  'csv',
  'json',
  'log',
  'yml',
  'yaml',
  'xml',
])

/** 输入区待发送的已上传附件。 */
interface PendingAttachment {
  attachmentId: string
  fileName: string
  mimeType: string
  previewUrl?: string
}

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
    /**
     * SSE 路径。默认 WEB ChatUi；API 控制台传 `/api/agent/chat/stream`。
     * 不改请求体字段，只换入口。
     */
    streamPath?: string
    /**
     * 是否启用随消息附件（选文件 / 拖拽 / 粘贴）。
     * 仅搭子打开；Echo / API 控制台保持纯文本。
     */
    enableAttachments?: boolean
  }>(),
  {
    identity: '',
    title: '对话',
    loadingHistory: false,
    emptyHint: '发送一条消息开始对话。',
    emptySubHint: '',
    streamPath: '/api/chat/stream',
    enableAttachments: false,
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
/** 搭子随消息待发附件；发送成功后清空。 */
const pending = ref<PendingAttachment[]>([])
const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
const dragOver = ref(false)

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
    clearPendingPreviews()
    pending.value = []
  },
)

/** 有正文或有待发附件即可点发送。 */
const canSend = computed(
  () =>
    !props.loadingHistory
    && !uploading.value
    && (!!input.value.trim() || pending.value.length > 0),
)

/** Enter 发送，Shift+Enter 换行。合成输入法回车不触发。 */
function onComposerKeydown(ev: KeyboardEvent) {
  if (ev.key !== 'Enter' || ev.shiftKey || ev.isComposing) {
    return
  }
  ev.preventDefault()
  void send()
}

/** 扩展名小写，无点。 */
function fileExt(name: string): string {
  const i = name.lastIndexOf('.')
  if (i < 0 || i === name.length - 1) {
    return ''
  }
  return name.slice(i + 1).toLowerCase()
}

/** 是否允许作为聊天附件。 */
function isAllowedFile(file: File): boolean {
  const mime = (file.type || '').toLowerCase()
  if (IMAGE_MIME.has(mime)) {
    return true
  }
  if (
    mime === 'application/pdf'
    || mime === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
    || mime.startsWith('text/')
    || mime === 'application/json'
    || mime === 'application/xml'
    || mime === 'application/yaml'
    || mime === 'application/x-yaml'
  ) {
    return true
  }
  return DOC_EXT.has(fileExt(file.name))
}

/** 释放本地预览 URL。 */
function clearPendingPreviews() {
  for (const row of pending.value) {
    if (row.previewUrl) {
      URL.revokeObjectURL(row.previewUrl)
    }
  }
}

/** 从预览条移除，不删对象存储。 */
function removePending(attachmentId: string) {
  const idx = pending.value.findIndex((row) => row.attachmentId === attachmentId)
  if (idx < 0) {
    return
  }
  const [row] = pending.value.splice(idx, 1)
  if (row?.previewUrl) {
    URL.revokeObjectURL(row.previewUrl)
  }
}

/**
 * 选文件 / 拖拽 / 粘贴后上传。超出 3 个或类型不对就提示。
 * 调用方须先把 FileList 拷成数组：清空 input.value 会掏空活的 FileList。
 */
async function ingestFiles(files: FileList | File[]) {
  if (!props.enableAttachments) {
    return
  }
  if (streaming.value || props.loadingHistory || uploading.value) {
    error.value = uploading.value
      ? '正在上传，请稍候再选'
      : streaming.value
        ? '生成中，请稍后再传附件'
        : '正在加载历史，请稍候'
    return
  }
  const list = Array.from(files)
  if (!list.length) {
    return
  }
  const room = MAX_ATTACHMENTS - pending.value.length
  if (room <= 0) {
    error.value = `一条消息最多 ${MAX_ATTACHMENTS} 个附件`
    return
  }
  const batch = list.slice(0, room)
  uploading.value = true
  error.value = ''
  try {
    for (const file of batch) {
      if (file.size <= 0 || file.size > MAX_FILE_BYTES) {
        error.value = '单个附件须在 1 字节到 5MB 之间'
        continue
      }
      if (!isAllowedFile(file)) {
        error.value = '仅支持图片（jpeg/png/webp/gif）与 PDF、Word、常见文本'
        continue
      }
      const saved = await uploadAttachmentFile(file, props.sessionId)
      const mime = (saved as { mimeType?: string }).mimeType
        || file.type
        || 'application/octet-stream'
      const previewUrl = IMAGE_MIME.has(mime.toLowerCase()) || IMAGE_MIME.has((file.type || '').toLowerCase())
        ? URL.createObjectURL(file)
        : undefined
      pending.value.push({
        attachmentId: saved.attachmentId,
        fileName: saved.fileName || file.name,
        mimeType: mime,
        previewUrl,
      })
    }
    if (list.length > room) {
      error.value = `一条消息最多 ${MAX_ATTACHMENTS} 个附件，多余的已忽略`
    }
  } catch (e) {
    const message = e instanceof Error ? e.message : '上传失败'
    error.value = message
    if (message.includes('重新登录')) {
      clearSession()
      emit('needLogin')
      await router.replace('/login')
    }
  } finally {
    uploading.value = false
  }
}

/** 文件选择器变更。先拷贝再清空，避免 FileList 被掏空导致偶发无反应。 */
function onFilePicked(ev: Event) {
  const el = ev.target as HTMLInputElement
  const list = el.files ? Array.from(el.files) : []
  el.value = ''
  if (list.length) {
    void ingestFiles(list)
  }
}

/** 打开系统选文件框；先清空以便同一文件可连选两次。 */
function openFilePicker() {
  if (!props.enableAttachments || !fileInput.value) {
    return
  }
  if (streaming.value || props.loadingHistory || uploading.value) {
    error.value = uploading.value
      ? '正在上传，请稍候再选'
      : streaming.value
        ? '生成中，请稍后再传附件'
        : '正在加载历史，请稍候'
    return
  }
  fileInput.value.value = ''
  fileInput.value.click()
}

/** 拖入输入区。 */
function onDrop(ev: DragEvent) {
  ev.preventDefault()
  dragOver.value = false
  if (!props.enableAttachments) {
    return
  }
  const files = ev.dataTransfer?.files
  if (files?.length) {
    void ingestFiles(Array.from(files))
  }
}

/** 粘贴图片进输入区。 */
function onPaste(ev: ClipboardEvent) {
  if (!props.enableAttachments || streaming.value || props.loadingHistory) {
    return
  }
  const items = ev.clipboardData?.items
  if (!items?.length) {
    return
  }
  const files: File[] = []
  for (const item of items) {
    if (item.kind === 'file' && item.type.startsWith('image/')) {
      const file = item.getAsFile()
      if (file) {
        files.push(file)
      }
    }
  }
  if (!files.length) {
    return
  }
  ev.preventDefault()
  void ingestFiles(files)
}

/** 气泡里点下载。 */
async function onDownloadAttachment(row: ChatMessageAttachment) {
  try {
    const { blob, fileName } = await downloadAttachment(row.attachmentId, row.fileName)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = fileName || row.fileName || 'download'
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '下载失败'
  }
}

/**
 * 发送用户输入并消费 SSE。
 * @param overrideText 有值时用该文案发送（重发上一条），不改输入框草稿；重发不带附件
 */
async function send(overrideText?: string) {
  const resend = overrideText !== undefined
  const text = (overrideText ?? input.value).trim()
  const attachmentIds = resend ? [] : pending.value.map((row) => row.attachmentId)
  const attachmentMeta: ChatMessageAttachment[] = resend
    ? []
    : pending.value.map((row) => ({
        attachmentId: row.attachmentId,
        fileName: row.fileName,
        mimeType: row.mimeType,
      }))
  if ((!text && attachmentIds.length === 0) || streaming.value || props.loadingHistory || uploading.value) {
    return
  }
  error.value = ''
  statusTip.value = ''
  const displayText = text || '请查看我发送的附件。'
  if (!resend) {
    input.value = ''
    clearPendingPreviews()
    pending.value = []
  }

  chatStore.appendMessage(props.agentId, props.sessionId, {
    id: crypto.randomUUID(),
    role: 'user',
    content: displayText,
    attachments: attachmentMeta.length ? attachmentMeta : undefined,
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
        text: displayText,
        attachmentIds: attachmentIds.length ? attachmentIds : undefined,
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
      props.streamPath,
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
            <div
              v-if="msg.attachments?.length"
              class="mt-2 flex flex-col gap-1.5"
              :class="msg.role === 'user' ? 'items-end' : 'items-start'"
            >
              <button
                v-for="att in msg.attachments"
                :key="att.attachmentId"
                type="button"
                class="text-left text-xs underline underline-offset-2 opacity-90 hover:opacity-100"
                @click="onDownloadAttachment(att)"
              >
                {{ att.fileName || att.attachmentId }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <div
        class="border-t border-[var(--color-border)] bg-[var(--color-surface-card)] px-4 py-3 space-y-2"
        :class="dragOver ? 'ring-2 ring-inset ring-[var(--color-brand-primary)]' : ''"
        @dragenter.prevent="enableAttachments && (dragOver = true)"
        @dragover.prevent="enableAttachments && (dragOver = true)"
        @dragleave.prevent="dragOver = false"
        @drop="onDrop"
      >
        <slot name="before-input" />
        <p v-if="statusTip" class="text-xs text-[var(--color-text-muted)]">{{ statusTip }}</p>
        <p v-if="error" class="text-sm text-[var(--color-danger)]">{{ error }}</p>
        <div v-if="enableAttachments && pending.length" class="flex flex-wrap gap-2">
          <div
            v-for="row in pending"
            :key="row.attachmentId"
            class="flex items-center gap-2 rounded-lg border border-[var(--color-border)] px-2 py-1.5 text-xs max-w-full"
          >
            <img
              v-if="row.previewUrl"
              :src="row.previewUrl"
              :alt="row.fileName"
              class="h-8 w-8 rounded object-cover shrink-0"
            />
            <span class="truncate max-w-[10rem]" :title="row.fileName">{{ row.fileName }}</span>
            <button
              type="button"
              class="text-[var(--color-text-muted)] hover:text-[var(--color-danger)] shrink-0"
              :disabled="streaming || uploading"
              @click="removePending(row.attachmentId)"
            >
              移除
            </button>
          </div>
        </div>
        <form class="flex gap-2 items-end" @submit.prevent="send()">
          <input
            v-if="enableAttachments"
            ref="fileInput"
            type="file"
            class="hidden"
            multiple
            accept="image/jpeg,image/png,image/webp,image/gif,.pdf,.docx,.txt,.md,.csv,.json,.xml,.yml,.yaml,.log"
            @change="onFilePicked"
          />
          <button
            v-if="enableAttachments"
            type="button"
            class="rounded-xl border border-[var(--color-border-strong)] px-3 py-2.5 text-sm hover:bg-[var(--color-surface-muted)] disabled:opacity-50 shrink-0"
            :disabled="streaming || loadingHistory || uploading || pending.length >= MAX_ATTACHMENTS"
            title="添加图片或文档"
            @click="openFilePicker"
          >
            附件
          </button>
          <textarea
            v-model="input"
            rows="2"
            class="flex-1 min-h-[2.75rem] max-h-40 resize-y rounded-xl border border-[var(--color-border-strong)] bg-[var(--color-surface-card)] text-[var(--color-text-main)] px-3 py-2.5 outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-brand-primary)] disabled:bg-[var(--color-surface-muted)]"
            placeholder="输入消息…（Enter 发送，Shift+Enter 换行）"
            :disabled="streaming || loadingHistory"
            @keydown="onComposerKeydown"
            @paste="onPaste"
          />
          <button
            v-if="streaming"
            type="button"
            class="rounded-xl border border-[var(--color-border-strong)] px-4 py-2.5 text-sm hover:bg-[var(--color-surface-muted)] shrink-0"
            @click="stop"
          >
            停止
          </button>
          <button
            v-else
            type="submit"
            class="rounded-xl bg-[var(--color-brand-primary)] px-4 py-2.5 text-sm text-white disabled:opacity-50 shrink-0"
            :disabled="!canSend"
          >
            发送
          </button>
        </form>
        <p v-if="enableAttachments && uploading" class="text-xs text-[var(--color-text-muted)]">正在上传附件…</p>
      </div>
    </div>
  </div>
</template>
