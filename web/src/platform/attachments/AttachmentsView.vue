<script setup lang="ts">
/**
 * 平台级附件页：列表、上传、下载、停用/恢复。展开一行看登记信息和引用。
 * 服务端列表只含 ACTIVE；本页停用后暂留行以便恢复，刷新后消失。
 */
import {
  disableAttachment,
  downloadAttachment,
  getAttachment,
  listAttachmentRefs,
  listAttachments,
  restoreAttachment,
  uploadAttachmentFile,
} from '@/shared/api/attachment-api'
import { ApiBusinessError } from '@/shared/api/http'
import { clearSession, loadSession } from '@/shared/auth/session'
import type { AttachmentDetail, AttachmentRefItem, AttachmentSummary } from '@/shared/types/api.types'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const session = loadSession()

const rows = ref<AttachmentSummary[]>([])
const loading = ref(false)
const error = ref('')
const uploading = ref(false)
const busyId = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const expandedId = ref('')
const detail = ref<AttachmentDetail | null>(null)
const detailError = ref('')
const detailLoading = ref(false)
const refs = ref<AttachmentRefItem[]>([])
const refsError = ref('')
const refsLoading = ref(false)
let detailSeq = 0

const identity = computed(() => session?.displayName || '已登录')

/** 登录失效时清会话并回登录。 */
async function bounceIfAuth(message: string) {
  if (message.includes('重新登录')) {
    clearSession()
    await router.replace('/login')
  }
}

/** 刷新仍启用的附件；本页刚停用的行会丢掉。 */
async function refresh() {
  loading.value = true
  error.value = ''
  try {
    rows.value = await listAttachments()
    if (expandedId.value && !rows.value.some((row) => row.attachmentId === expandedId.value)) {
      collapseDetail()
    }
  } catch (e) {
    const message = e instanceof Error ? e.message : '加载失败'
    error.value = message
    await bounceIfAuth(message)
  } finally {
    loading.value = false
  }
}

/** 选文件后立即上传。 */
async function onFileChange(ev: Event) {
  const input = ev.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || uploading.value) {
    return
  }
  uploading.value = true
  error.value = ''
  try {
    const saved = await uploadAttachmentFile(file)
    rows.value = [saved, ...rows.value.filter((r) => r.attachmentId !== saved.attachmentId)]
  } catch (e) {
    const message = e instanceof Error ? e.message : '上传失败'
    error.value = message
    await bounceIfAuth(message)
  } finally {
    uploading.value = false
  }
}

/** 触发隐藏的 file input。 */
function pickFile() {
  fileInput.value?.click()
}

/** 下载正文到本机。 */
async function onDownload(row: AttachmentSummary) {
  if (busyId.value || row.status !== 'ACTIVE') {
    return
  }
  busyId.value = row.attachmentId
  error.value = ''
  try {
    const { blob, fileName } = await downloadAttachment(row.attachmentId, row.fileName)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = fileName
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    const message = e instanceof Error ? e.message : '下载失败'
    error.value = message
    await bounceIfAuth(message)
  } finally {
    busyId.value = ''
  }
}

/** 收起详情。迟到的响应不能再写回。 */
function collapseDetail() {
  detailSeq += 1
  expandedId.value = ''
  detail.value = null
  detailError.value = ''
  detailLoading.value = false
  refs.value = []
  refsError.value = ''
  refsLoading.value = false
}

/** 空串不占一行。 */
function filled(value: string | null | undefined): string {
  return (value ?? '').trim()
}

/** 按字节写成可读大小。负数不当成有效大小。 */
function formatSize(bytes: number | null | undefined): string {
  if (bytes == null || !Number.isFinite(bytes) || bytes < 0) {
    return ''
  }
  if (bytes < 1024) {
    return `${bytes} 字节`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

/**
 * 点启用中的行展开详情和引用，再点收起。同时只展开一行。
 * 找不到就刷新列表。其它失败留在这一行。不展示存储路径，也不核引用对象是否存在。
 */
async function toggleDetail(row: AttachmentSummary) {
  if (row.status !== 'ACTIVE') {
    return
  }
  if (expandedId.value === row.attachmentId) {
    collapseDetail()
    return
  }
  const seq = ++detailSeq
  expandedId.value = row.attachmentId
  detail.value = null
  detailError.value = ''
  detailLoading.value = true
  refs.value = []
  refsError.value = ''
  refsLoading.value = true
  try {
    const [detailResult, refsResult] = await Promise.allSettled([
      getAttachment(row.attachmentId),
      listAttachmentRefs(row.attachmentId),
    ])
    if (seq !== detailSeq) {
      return
    }
    const rejected = [detailResult, refsResult].filter(
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
    if (refsResult.status === 'fulfilled') {
      refs.value = refsResult.value
    } else {
      refsError.value = failureKind(refsResult.reason)
    }
  } finally {
    if (seq === detailSeq) {
      detailLoading.value = false
      refsLoading.value = false
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
  return message
}

/** 引用类型的短标签。不认识的原样显示。 */
function refLabel(refType: string): string {
  if (refType === 'MESSAGE') {
    return '消息'
  }
  if (refType === 'RUN') {
    return '运行'
  }
  if (refType === 'DOCUMENT') {
    return '文档'
  }
  return refType
}

/** 停用后暂留本页，便于恢复。详情接口只认仍启用的行，所以收起。 */
async function onDisable(row: AttachmentSummary) {
  if (busyId.value || row.status !== 'ACTIVE') {
    return
  }
  if (!window.confirm(`停用附件 ${row.fileName}？不删对象存储。`)) {
    return
  }
  busyId.value = row.attachmentId
  error.value = ''
  try {
    const saved = await disableAttachment(row.attachmentId)
    if (expandedId.value === row.attachmentId) {
      collapseDetail()
    }
    rows.value = rows.value.map((r) => (r.attachmentId === saved.attachmentId ? saved : r))
  } catch (e) {
    const message = e instanceof Error ? e.message : '停用失败'
    error.value = message
    await bounceIfAuth(message)
  } finally {
    busyId.value = ''
  }
}

/** 恢复本页仍可见的已停用行。 */
async function onRestore(row: AttachmentSummary) {
  if (busyId.value || row.status === 'ACTIVE') {
    return
  }
  busyId.value = row.attachmentId
  error.value = ''
  try {
    const saved = await restoreAttachment(row.attachmentId)
    rows.value = rows.value.map((r) => (r.attachmentId === saved.attachmentId ? saved : r))
  } catch (e) {
    const message = e instanceof Error ? e.message : '恢复失败'
    error.value = message
    await bounceIfAuth(message)
  } finally {
    busyId.value = ''
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
        <h1 class="text-lg font-semibold text-[var(--color-text-main)]">附件</h1>
        <p class="text-xs text-[var(--color-text-muted)] truncate">{{ identity }}</p>
      </div>
      <div class="ml-auto flex flex-wrap gap-2">
        <input
          ref="fileInput"
          type="file"
          class="hidden"
          @change="onFileChange"
        />
        <button
          type="button"
          class="rounded-lg bg-[var(--color-brand-primary)] px-3 py-1.5 text-sm text-white disabled:opacity-50"
          :disabled="uploading || loading"
          @click="pickFile"
        >
          {{ uploading ? '上传中…' : '上传' }}
        </button>
        <button
          type="button"
          class="rounded-lg border border-[var(--color-border-strong)] px-3 py-1.5 text-sm hover:bg-[var(--color-surface-muted)] disabled:opacity-50"
          :disabled="loading"
          @click="refresh"
        >
          刷新
        </button>
      </div>
    </header>

    <div class="flex-1 overflow-auto p-4 space-y-3">
      <p class="text-xs text-[var(--color-text-muted)]">
        列表默认只含启用中的附件。停用后本页暂留以便恢复；刷新后停用行不再出现。不删对象存储。
      </p>
      <p v-if="error" class="text-sm text-[var(--color-danger)]">{{ error }}</p>
      <p v-if="loading" class="text-sm text-[var(--color-text-muted)]">加载中…</p>

      <div class="overflow-x-auto bg-[var(--color-surface-card)] border border-[var(--color-border)] rounded-lg">
        <table class="w-full text-sm text-left">
          <thead class="bg-[var(--color-surface-muted)] text-[var(--color-text-muted)]">
            <tr>
              <th class="px-3 py-2 font-medium">文件名</th>
              <th class="px-3 py-2 font-medium">类型</th>
              <th class="px-3 py-2 font-medium">状态</th>
              <th class="px-3 py-2 font-medium">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!loading && rows.length === 0">
              <td colspan="4" class="px-3 py-6 text-center text-[var(--color-text-muted)]">暂无附件</td>
            </tr>
            <template v-for="row in rows" :key="row.attachmentId">
              <tr
                class="border-t border-[var(--color-border)]"
                :class="row.status === 'ACTIVE' ? 'cursor-pointer' : ''"
                @click="toggleDetail(row)"
              >
                <td class="px-3 py-2">
                  <span class="block truncate max-w-xs">{{ row.fileName }}</span>
                  <span class="block font-mono text-xs text-[var(--color-text-muted)] truncate">
                    {{ row.attachmentId }}
                  </span>
                </td>
                <td class="px-3 py-2 font-mono text-xs">{{ row.storageType }}</td>
                <td class="px-3 py-2">{{ row.status }}</td>
                <td class="px-3 py-2 whitespace-nowrap space-x-2">
                  <button
                    v-if="row.status === 'ACTIVE'"
                    type="button"
                    class="text-sm text-[var(--color-brand-primary)] disabled:opacity-50"
                    :disabled="busyId === row.attachmentId"
                    @click.stop="onDownload(row)"
                  >
                    下载
                  </button>
                  <button
                    v-if="row.status === 'ACTIVE'"
                    type="button"
                    class="text-sm text-[var(--color-danger)] disabled:opacity-50"
                    :disabled="busyId === row.attachmentId"
                    @click.stop="onDisable(row)"
                  >
                    停用
                  </button>
                  <button
                    v-else
                    type="button"
                    class="text-sm text-[var(--color-brand-primary)] disabled:opacity-50"
                    :disabled="busyId === row.attachmentId"
                    @click.stop="onRestore(row)"
                  >
                    恢复
                  </button>
                </td>
              </tr>
              <tr v-if="expandedId === row.attachmentId" class="border-t border-[var(--color-border)]">
                <td colspan="4" class="px-3 py-2 space-y-0.5 bg-[var(--color-surface-muted)]">
                  <p v-if="detailLoading" class="text-xs text-[var(--color-text-muted)]">加载中…</p>
                  <template v-else-if="detail">
                    <p v-if="filled(detail.mimeType)" class="text-xs text-[var(--color-text-muted)]">
                      文件类型 {{ detail.mimeType }}
                    </p>
                    <p v-if="formatSize(detail.sizeBytes)" class="text-xs text-[var(--color-text-muted)]">
                      大小 {{ formatSize(detail.sizeBytes) }}
                    </p>
                    <p v-if="filled(detail.createdAt)" class="text-xs text-[var(--color-text-muted)]">
                      登记 {{ detail.createdAt }}
                    </p>
                    <p
                      v-if="!filled(detail.mimeType) && !formatSize(detail.sizeBytes) && !filled(detail.createdAt)"
                      class="text-xs text-[var(--color-text-muted)]"
                    >
                      没有更多登记信息
                    </p>
                  </template>
                  <p v-if="detailError" class="text-xs text-[var(--color-danger)]">{{ detailError }}</p>
                  <p v-if="refsLoading" class="text-xs text-[var(--color-text-muted)]">引用加载中…</p>
                  <template v-else-if="!refsError">
                    <p v-if="refs.length === 0" class="text-xs text-[var(--color-text-muted)]">暂无引用</p>
                    <p
                      v-for="item in refs"
                      :key="`${item.refType}:${item.refId}`"
                      class="text-xs text-[var(--color-text-muted)] truncate"
                    >
                      {{ refLabel(item.refType) }} {{ item.refId }}
                      <template v-if="filled(item.createdAt)"> · 挂上 {{ item.createdAt }}</template>
                    </p>
                  </template>
                  <p v-if="refsError" class="text-xs text-[var(--color-danger)]">{{ refsError }}</p>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
