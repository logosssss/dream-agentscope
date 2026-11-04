<script setup lang="ts">
/**
 * 平台级知识库页：列出本人的库、按名称建库、展开后写入纯文本或上传文本文件。
 * 展开期间会刷新待索引和索引中的状态，失败或仍待索引可以重试。
 * 可以删除一篇文档，也可以删除整个库。
 */
import {
  createKnowledgeBase,
  createKnowledgeDocument,
  deleteKnowledgeBase,
  deleteKnowledgeDocument,
  getKnowledgeBase,
  listKnowledgeBases,
  listKnowledgeDocuments,
  reindexKnowledgeDocument,
  uploadKnowledgeFile,
} from '@/shared/api/knowledge-api'
import { ApiBusinessError } from '@/shared/api/http'
import { clearSession, loadSession } from '@/shared/auth/session'
import type { KnowledgeBaseDetail, KnowledgeBaseItem, KnowledgeDocumentItem } from '@/shared/types/api.types'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const session = loadSession()

const rows = ref<KnowledgeBaseItem[]>([])
const loading = ref(false)
const error = ref('')
const name = ref('')
const creating = ref(false)
const expandedId = ref('')
const detail = ref<KnowledgeBaseDetail | null>(null)
const detailError = ref('')
const detailLoading = ref(false)
const documents = ref<KnowledgeDocumentItem[]>([])
const documentsError = ref('')
const documentsLoading = ref(false)
const docTitle = ref('')
const docContent = ref('')
const docFormError = ref('')
const uploadingDoc = ref(false)
const retryingDocId = ref('')
const deleteDocId = ref('')
const deletingDocId = ref('')
const confirmingBase = ref(false)
const deletingBase = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
let detailSeq = 0
let pollTimer: ReturnType<typeof setTimeout> | null = null
let pollLeft = 0

/** 展开后最多跟 20 次，每次间隔 3 秒。收起或状态稳定就停。 */
const POLL_MS = 3000
const POLL_LIMIT = 20

/** 与接入层允许的文本扩展名一致。 */
const TEXT_EXTENSIONS = new Set(['txt', 'md', 'markdown', 'csv', 'json', 'log', 'yml', 'yaml', 'xml'])

/** 正文上限与接入层一致，按 UTF-8 字节。 */
const MAX_DOCUMENT_BYTES = 2 * 1024 * 1024

const identity = computed(() => session?.displayName || '已登录')

/** 登录失效时清会话并回登录。 */
async function bounceIfAuth(message: string) {
  if (message.includes('重新登录')) {
    clearSession()
    await router.replace('/login')
  }
}

/** 刷新本人的库。展开的库若不在新列表里就收起，还在就重拉文档。 */
async function refresh() {
  loading.value = true
  error.value = ''
  const openId = expandedId.value
  try {
    rows.value = await listKnowledgeBases()
    if (openId && !rows.value.some((row) => row.kbId === openId)) {
      collapseDetail()
      return
    }
    if (openId) {
      documents.value = await listKnowledgeDocuments(openId)
      documentsError.value = ''
      schedulePoll()
    }
  } catch (e) {
    const message = e instanceof Error ? e.message : '加载失败'
    error.value = message
    await bounceIfAuth(message)
  } finally {
    loading.value = false
  }
}

/** 按名称建库。空白名称不提交。不传 Agent，由服务端绑定默认搭子。 */
async function onCreate() {
  const nextName = name.value.trim()
  if (!nextName || creating.value) {
    if (!nextName) {
      error.value = '请填写库名'
    }
    return
  }
  if (nextName.length > 128) {
    error.value = '库名不能超过 128 字'
    return
  }
  creating.value = true
  error.value = ''
  try {
    const saved = await createKnowledgeBase(nextName)
    name.value = ''
    rows.value = [saved, ...rows.value.filter((row) => row.kbId !== saved.kbId)]
  } catch (e) {
    const message = e instanceof Error ? e.message : '建库失败'
    error.value = message.includes('unavailable') || message.includes('503')
      ? '知识库暂不可用'
      : message
    await bounceIfAuth(message)
  } finally {
    creating.value = false
  }
}

/** 收起详情。迟到的响应不能再写回。 */
function collapseDetail() {
  stopPoll()
  detailSeq += 1
  expandedId.value = ''
  detail.value = null
  detailError.value = ''
  detailLoading.value = false
  documents.value = []
  documentsError.value = ''
  documentsLoading.value = false
  docTitle.value = ''
  docContent.value = ''
  docFormError.value = ''
  deleteDocId.value = ''
  deletingDocId.value = ''
  confirmingBase.value = false
  deletingBase.value = false
}

/** 空串不占一行。 */
function filled(value: string | null | undefined): string {
  return (value ?? '').trim()
}

/**
 * 点一行展开创建时间和文档，再点收起。同时只展开一行。
 * 找不到就刷新列表。其它失败留在这一行。不展示正文。
 */
async function toggleDetail(row: KnowledgeBaseItem) {
  if (expandedId.value === row.kbId) {
    collapseDetail()
    return
  }
  const seq = ++detailSeq
  stopPoll()
  expandedId.value = row.kbId
  detail.value = null
  detailError.value = ''
  detailLoading.value = true
  documents.value = []
  documentsError.value = ''
  documentsLoading.value = true
  docTitle.value = ''
  docContent.value = ''
  docFormError.value = ''
  deleteDocId.value = ''
  confirmingBase.value = false
  try {
    const [detailResult, documentsResult] = await Promise.allSettled([
      getKnowledgeBase(row.kbId),
      listKnowledgeDocuments(row.kbId),
    ])
    if (seq !== detailSeq) {
      return
    }
    const rejected = [detailResult, documentsResult].filter(
      (item): item is PromiseRejectedResult => item.status === 'rejected',
    )
    if (rejected.some((item) => failureKind(item.reason) === 'auth')) {
      clearSession()
      await router.replace('/login')
      return
    }
    if (rejected.some((item) => failureKind(item.reason) === 'missing')) {
      collapseDetail()
      await refresh()
      return
    }
    if (detailResult.status === 'fulfilled') {
      detail.value = detailResult.value
    } else {
      detailError.value = failureKind(detailResult.reason)
    }
    if (documentsResult.status === 'fulfilled') {
      documents.value = documentsResult.value
      schedulePoll()
    } else {
      documentsError.value = failureKind(documentsResult.reason)
    }
  } finally {
    if (seq === detailSeq) {
      detailLoading.value = false
      documentsLoading.value = false
    }
  }
}

/** 登录失效、找不到，或留给这一行的错误文案。 */
function failureKind(error: unknown): 'auth' | 'missing' | string {
  const message = error instanceof Error ? error.message : '加载失败'
  if (message.includes('重新登录')) {
    return 'auth'
  }
  if (error instanceof ApiBusinessError && error.code === 'NOT_FOUND') {
    return 'missing'
  }
  if (message.includes('unavailable') || message.includes('503')) {
    return '知识库暂不可用'
  }
  return message
}

/** 文档状态的短标签。不认识的原样显示。 */
function documentStatusLabel(status: string | null | undefined): string {
  if (status === 'PENDING') {
    return '待索引'
  }
  if (status === 'PROCESSING') {
    return '索引中'
  }
  if (status === 'SUCCESS') {
    return '已索引'
  }
  if (status === 'FAILED') {
    return '失败'
  }
  if (status === 'ARCHIVED') {
    return '已归档'
  }
  return (status ?? '').trim()
}

/**
 * 往当前展开的库写入一篇纯文本。不传文档标识，由服务端新建。
 * 成功后只刷新这篇库的文档列表，不轮询索引。
 */
async function onUpload(row: KnowledgeBaseItem) {
  const content = docContent.value.trim()
  const title = docTitle.value.trim()
  if (!content || uploadingDoc.value) {
    if (!content) {
      docFormError.value = '请填写正文'
    }
    return
  }
  if (title.length > 255) {
    docFormError.value = '标题不能超过 255 字'
    return
  }
  if (new TextEncoder().encode(content).length > MAX_DOCUMENT_BYTES) {
    docFormError.value = '正文不能超过 2MB'
    return
  }
  uploadingDoc.value = true
  docFormError.value = ''
  try {
    await createKnowledgeDocument(row.kbId, title, content)
    if (expandedId.value !== row.kbId) {
      return
    }
    docTitle.value = ''
    docContent.value = ''
    const listed = await listKnowledgeDocuments(row.kbId)
    if (expandedId.value !== row.kbId) {
      return
    }
    documents.value = listed
    documentsError.value = ''
    schedulePoll()
  } catch (e) {
    const kind = failureKind(e)
    if (kind === 'auth') {
      clearSession()
      await router.replace('/login')
      return
    }
    if (kind === 'missing') {
      collapseDetail()
      await refresh()
      return
    }
    docFormError.value = kind
  } finally {
    uploadingDoc.value = false
  }
}

/** 文件名扩展名，没有点时为空。 */
function fileExtension(fileName: string): string {
  const base = fileName.split(/[/\\]/).pop() || ''
  const dot = base.lastIndexOf('.')
  return dot > 0 ? base.slice(dot + 1).toLowerCase() : ''
}

/** 扩展名在白名单里，或浏览器标明是 text。PDF 不放行。 */
function isTextFile(file: File): boolean {
  if (TEXT_EXTENSIONS.has(fileExtension(file.name))) {
    return true
  }
  return (file.type || '').toLowerCase().startsWith('text/')
}

/** 把服务端的文件拒绝说成中文。其它文案原样留下。 */
function fileErrorMessage(message: string): string {
  if (message.includes('only plain text')) {
    return '只接受纯文本文件'
  }
  if (message.includes('exceeds 2MB')) {
    return '文件不能超过 2MB'
  }
  if (message.includes('binary')) {
    return '不能上传二进制文件'
  }
  if (message.includes('blank') || message.includes('empty')) {
    return '文件是空的'
  }
  return message
}

/**
 * 打开隐藏的文件选择器。input 不能放在 v-for 里用同名 ref，否则会变成数组，click 不是函数。
 */
function pickFile() {
  fileInput.value?.click()
}

/**
 * 选中文本文件后立即上传。标题框有字就当作标题，否则用文件名。
 * 不传文档标识。成功后刷新当前库的文档列表。
 */
async function onFile(ev: Event) {
  const input = ev.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  const kbId = expandedId.value
  if (!file || !kbId || uploadingDoc.value) {
    return
  }
  if (file.size === 0) {
    docFormError.value = '文件是空的'
    return
  }
  if (file.size > MAX_DOCUMENT_BYTES) {
    docFormError.value = '文件不能超过 2MB'
    return
  }
  if (!isTextFile(file)) {
    docFormError.value = '只接受纯文本文件'
    return
  }
  uploadingDoc.value = true
  docFormError.value = ''
  try {
    await uploadKnowledgeFile(kbId, file, docTitle.value)
    if (expandedId.value !== kbId) {
      return
    }
    docTitle.value = ''
    docContent.value = ''
    const listed = await listKnowledgeDocuments(kbId)
    if (expandedId.value !== kbId) {
      return
    }
    documents.value = listed
    documentsError.value = ''
    schedulePoll()
  } catch (e) {
    const kind = failureKind(e)
    if (kind === 'auth') {
      clearSession()
      await router.replace('/login')
      return
    }
    if (kind === 'missing') {
      collapseDetail()
      await refresh()
      return
    }
    docFormError.value = fileErrorMessage(kind)
  } finally {
    uploadingDoc.value = false
  }
}

/** 待索引或索引中才继续看。成功、失败、归档不再请求。 */
function needsPoll(items: KnowledgeDocumentItem[]): boolean {
  return items.some((doc) => doc.status === 'PENDING' || doc.status === 'PROCESSING')
}

/** 停掉未发出的下一次刷新。 */
function stopPoll() {
  if (pollTimer != null) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
  pollLeft = 0
}

/**
 * 展开的库里还有未完成索引时，隔几秒重拉文档列表。
 * 收起、离开页面或次数用完就停。不展示正文。
 */
function schedulePoll() {
  stopPoll()
  const kbId = expandedId.value
  if (!kbId || !needsPoll(documents.value)) {
    return
  }
  pollLeft = POLL_LIMIT
  const tick = async () => {
    pollTimer = null
    if (expandedId.value !== kbId || pollLeft <= 0 || !needsPoll(documents.value)) {
      return
    }
    pollLeft -= 1
    try {
      const listed = await listKnowledgeDocuments(kbId)
      if (expandedId.value !== kbId) {
        return
      }
      documents.value = listed
    } catch (e) {
      const kind = failureKind(e)
      if (kind === 'auth') {
        clearSession()
        await router.replace('/login')
        return
      }
      if (kind === 'missing') {
        collapseDetail()
        await refresh()
        return
      }
      docFormError.value = kind
      return
    }
    if (expandedId.value === kbId && pollLeft > 0 && needsPoll(documents.value)) {
      pollTimer = setTimeout(() => {
        void tick()
      }, POLL_MS)
    }
  }
  pollTimer = setTimeout(() => {
    void tick()
  }, POLL_MS)
}

/**
 * 重试仍待索引或已失败的版本。不改正文。
 * 服务端拒绝时写在表单下，不收起整库。
 */
async function onRetry(doc: KnowledgeDocumentItem) {
  if (retryingDocId.value || uploadingDoc.value) {
    return
  }
  if (doc.status !== 'PENDING' && doc.status !== 'FAILED') {
    return
  }
  const kbId = expandedId.value
  if (!kbId) {
    return
  }
  retryingDocId.value = doc.docId
  docFormError.value = ''
  try {
    await reindexKnowledgeDocument(doc.docId, doc.version)
    if (expandedId.value !== kbId) {
      return
    }
    documents.value = await listKnowledgeDocuments(kbId)
    if (expandedId.value !== kbId) {
      return
    }
    schedulePoll()
  } catch (e) {
    const kind = failureKind(e)
    if (kind === 'auth') {
      clearSession()
      await router.replace('/login')
      return
    }
    if (kind === 'missing') {
      collapseDetail()
      await refresh()
      return
    }
    docFormError.value = kind.includes('not retryable') ? '这份文档现在不能重试' : kind
  } finally {
    retryingDocId.value = ''
  }
}

/** 先在这一行确认，再删全部版本。不删库。 */
function askDelete(doc: KnowledgeDocumentItem) {
  if (uploadingDoc.value || retryingDocId.value || deletingDocId.value) {
    return
  }
  deleteDocId.value = doc.docId
}

/** 取消这一行的删除确认。 */
function cancelDelete() {
  deleteDocId.value = ''
}

/**
 * 删除这篇文档的全部版本、切块和对象。
 * 找不到就只刷新当前库的文档。其它失败留在表单下。
 */
async function confirmDelete(doc: KnowledgeDocumentItem) {
  if (deletingDocId.value || uploadingDoc.value) {
    return
  }
  const kbId = expandedId.value
  if (!kbId) {
    return
  }
  deletingDocId.value = doc.docId
  docFormError.value = ''
  try {
    await deleteKnowledgeDocument(doc.docId)
    if (expandedId.value !== kbId) {
      return
    }
    deleteDocId.value = ''
    documents.value = await listKnowledgeDocuments(kbId)
    if (expandedId.value !== kbId) {
      return
    }
    documentsError.value = ''
    schedulePoll()
  } catch (e) {
    const kind = failureKind(e)
    if (kind === 'auth') {
      clearSession()
      await router.replace('/login')
      return
    }
    if (kind === 'missing') {
      deleteDocId.value = ''
      try {
        documents.value = await listKnowledgeDocuments(kbId)
        documentsError.value = ''
      } catch (listError) {
        const listKind = failureKind(listError)
        if (listKind === 'auth') {
          clearSession()
          await router.replace('/login')
          return
        }
        if (listKind === 'missing') {
          collapseDetail()
          await refresh()
          return
        }
        docFormError.value = listKind
      }
      return
    }
    docFormError.value = kind
  } finally {
    deletingDocId.value = ''
  }
}

/** 展开区里先确认，再删整个库。写入或删文档进行中不打开确认。 */
function askDeleteBase() {
  if (uploadingDoc.value || retryingDocId.value || deletingDocId.value || deletingBase.value) {
    return
  }
  confirmingBase.value = true
}

/** 取消删库确认。 */
function cancelDeleteBase() {
  confirmingBase.value = false
}

/**
 * 删除这个库以及下面的文档、切块和对象。
 * 成功后收起并刷新列表。找不到也按已经不在处理。
 */
async function confirmDeleteBase(row: KnowledgeBaseItem) {
  if (deletingBase.value || uploadingDoc.value) {
    return
  }
  deletingBase.value = true
  docFormError.value = ''
  try {
    await deleteKnowledgeBase(row.kbId)
    collapseDetail()
    await refresh()
  } catch (e) {
    const kind = failureKind(e)
    if (kind === 'auth') {
      clearSession()
      await router.replace('/login')
      return
    }
    if (kind === 'missing') {
      collapseDetail()
      await refresh()
      return
    }
    docFormError.value = kind
  } finally {
    deletingBase.value = false
  }
}

onMounted(() => {
  void refresh()
})

onBeforeUnmount(() => {
  stopPoll()
})
</script>

<template>
  <div class="h-full flex flex-col bg-[var(--color-surface-bg)]">
    <!-- 单例文件选择，挂在页根；上传目标用当前 expandedId。 -->
    <input
      ref="fileInput"
      type="file"
      class="hidden"
      accept=".txt,.md,.markdown,.csv,.json,.log,.yml,.yaml,.xml,text/plain"
      @change="onFile"
    />
    <header
      class="shrink-0 border-b border-[var(--color-border)] bg-[var(--color-surface-card)] px-4 py-3 flex flex-wrap items-center gap-3"
    >
      <div class="min-w-0">
        <h1 class="text-lg font-semibold text-[var(--color-text-main)]">知识库</h1>
        <p class="text-xs text-[var(--color-text-muted)] truncate">{{ identity }}</p>
      </div>
      <form class="ml-auto flex flex-wrap gap-2" @submit.prevent="onCreate">
        <input
          v-model="name"
          maxlength="128"
          class="rounded-lg border border-[var(--color-border-strong)] bg-[var(--color-surface-card)] px-3 py-1.5 text-sm text-[var(--color-text-main)] outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-brand-primary)]"
          placeholder="库名"
          :disabled="creating"
        />
        <button
          type="submit"
          class="rounded-lg bg-[var(--color-brand-primary)] px-3 py-1.5 text-sm text-white disabled:opacity-50"
          :disabled="creating || loading"
        >
          {{ creating ? '建库中…' : '建库' }}
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
      <p class="text-xs text-[var(--color-text-muted)]">
        库会绑到默认搭子。点一行后可写入正文或上传纯文本。待索引和索引中会自动刷新。文档和整个库都可以删除。
      </p>
      <p v-if="error" class="text-sm text-[var(--color-danger)]">{{ error }}</p>
      <p v-if="loading" class="text-sm text-[var(--color-text-muted)]">加载中…</p>

      <div class="overflow-x-auto bg-[var(--color-surface-card)] border border-[var(--color-border)] rounded-lg">
        <table class="w-full text-sm text-left">
          <thead class="bg-[var(--color-surface-muted)] text-[var(--color-text-muted)]">
            <tr>
              <th class="px-3 py-2 font-medium">名称</th>
              <th class="px-3 py-2 font-medium">Agent</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!loading && rows.length === 0">
              <td colspan="2" class="px-3 py-6 text-center text-[var(--color-text-muted)]">还没有知识库</td>
            </tr>
            <template v-for="row in rows" :key="row.kbId">
              <tr
                class="border-t border-[var(--color-border)] cursor-pointer"
                @click="toggleDetail(row)"
              >
                <td class="px-3 py-2">
                  <span class="block truncate max-w-xs">{{ row.name }}</span>
                  <span class="block font-mono text-xs text-[var(--color-text-muted)] truncate">
                    {{ row.kbId }}
                  </span>
                </td>
                <td class="px-3 py-2 font-mono text-xs">{{ row.agentId }}</td>
              </tr>
              <tr v-if="expandedId === row.kbId" class="border-t border-[var(--color-border)]">
                <td colspan="2" class="px-3 py-2 space-y-0.5 bg-[var(--color-surface-muted)]">
                  <p v-if="detailLoading" class="text-xs text-[var(--color-text-muted)]">加载中…</p>
                  <template v-else-if="detail">
                    <p v-if="filled(detail.createdAt)" class="text-xs text-[var(--color-text-muted)]">
                      创建 {{ detail.createdAt }}
                    </p>
                    <p v-else class="text-xs text-[var(--color-text-muted)]">没有创建时间</p>
                    <div class="flex flex-wrap items-center gap-x-2 pt-1 text-xs" @click.stop>
                      <template v-if="confirmingBase">
                        <span class="text-[var(--color-text-muted)]">删除这个库和里面的文档？</span>
                        <button
                          type="button"
                          class="text-[var(--color-danger)] disabled:opacity-50"
                          :disabled="deletingBase"
                          @click="confirmDeleteBase(row)"
                        >
                          {{ deletingBase ? '删除中…' : '确认' }}
                        </button>
                        <button
                          type="button"
                          class="text-[var(--color-text-muted)]"
                          :disabled="deletingBase"
                          @click="cancelDeleteBase"
                        >
                          取消
                        </button>
                      </template>
                      <button
                        v-else
                        type="button"
                        class="text-[var(--color-danger)] disabled:opacity-50"
                        :disabled="uploadingDoc || !!deletingDocId || deletingBase"
                        @click="askDeleteBase"
                      >
                        删除库
                      </button>
                    </div>
                  </template>
                  <p v-if="detailError" class="text-xs text-[var(--color-danger)]">{{ detailError }}</p>
                  <p v-if="documentsLoading" class="text-xs text-[var(--color-text-muted)]">文档加载中…</p>
                  <p v-else-if="documentsError" class="text-xs text-[var(--color-danger)]">{{ documentsError }}</p>
                  <template v-else>
                    <p v-if="documents.length === 0" class="text-xs text-[var(--color-text-muted)]">还没有文档</p>
                    <div
                      v-for="doc in documents"
                      :key="doc.docId"
                      class="flex flex-wrap items-center gap-x-2 text-xs text-[var(--color-text-muted)]"
                    >
                      <span class="truncate">
                        {{ filled(doc.title) || '无标题' }} · {{ documentStatusLabel(doc.status) }}
                        <template v-if="filled(doc.errorMsg)"> · {{ doc.errorMsg }}</template>
                      </span>
                      <button
                        v-if="doc.status === 'PENDING' || doc.status === 'FAILED'"
                        type="button"
                        class="text-[var(--color-brand-primary)] disabled:opacity-50"
                        :disabled="retryingDocId === doc.docId || uploadingDoc || deletingDocId === doc.docId"
                        @click.stop="onRetry(doc)"
                      >
                        {{ retryingDocId === doc.docId ? '重试中…' : '重试' }}
                      </button>
                      <template v-if="deleteDocId === doc.docId">
                        <span>删除全部版本？</span>
                        <button
                          type="button"
                          class="text-[var(--color-danger)] disabled:opacity-50"
                          :disabled="deletingDocId === doc.docId"
                          @click.stop="confirmDelete(doc)"
                        >
                          {{ deletingDocId === doc.docId ? '删除中…' : '确认' }}
                        </button>
                        <button
                          type="button"
                          class="text-[var(--color-text-muted)]"
                          :disabled="deletingDocId === doc.docId"
                          @click.stop="cancelDelete"
                        >
                          取消
                        </button>
                      </template>
                      <button
                        v-else
                        type="button"
                        class="text-[var(--color-danger)] disabled:opacity-50"
                        :disabled="uploadingDoc || !!retryingDocId || !!deletingDocId"
                        @click.stop="askDelete(doc)"
                      >
                        删除
                      </button>
                    </div>
                  </template>
                  <form class="space-y-2 pt-2" @submit.prevent="onUpload(row)" @click.stop>
                    <input
                      v-model="docTitle"
                      maxlength="255"
                      class="w-full rounded-lg border border-[var(--color-border-strong)] bg-[var(--color-surface-card)] px-3 py-1.5 text-sm text-[var(--color-text-main)] outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-brand-primary)]"
                      placeholder="标题，可空"
                      :disabled="uploadingDoc"
                    />
                    <textarea
                      v-model="docContent"
                      rows="4"
                      class="w-full rounded-lg border border-[var(--color-border-strong)] bg-[var(--color-surface-card)] px-3 py-1.5 text-sm text-[var(--color-text-main)] outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-brand-primary)]"
                      placeholder="正文"
                      :disabled="uploadingDoc"
                    />
                    <p v-if="docFormError" class="text-xs text-[var(--color-danger)]">{{ docFormError }}</p>
                    <div class="flex flex-wrap gap-2">
                      <button
                        type="submit"
                        class="rounded-lg bg-[var(--color-brand-primary)] px-3 py-1.5 text-sm text-white disabled:opacity-50"
                        :disabled="uploadingDoc"
                      >
                        {{ uploadingDoc ? '写入中…' : '写入' }}
                      </button>
                      <button
                        type="button"
                        class="rounded-lg border border-[var(--color-border-strong)] px-3 py-1.5 text-sm hover:bg-[var(--color-surface-muted)] disabled:opacity-50"
                        :disabled="uploadingDoc"
                        @click="pickFile"
                      >
                        上传文本
                      </button>
                    </div>
                  </form>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
