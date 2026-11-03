<script setup lang="ts">
/**
 * 平台级附件页：列表、上传、下载、停用/恢复。
 * 服务端列表只含 ACTIVE；本页停用后暂留行以便恢复，刷新后消失。
 */
import {
  disableAttachment,
  downloadAttachment,
  listAttachments,
  restoreAttachment,
  uploadAttachmentFile,
} from '@/shared/api/attachment-api'
import { clearSession, loadSession } from '@/shared/auth/session'
import type { AttachmentSummary } from '@/shared/types/api.types'
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

const identity = computed(() => `${session?.tenantId || ''} / ${session?.userId || ''}`)

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

/** 停用后暂留本页，便于恢复。 */
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
            <tr
              v-for="row in rows"
              :key="row.attachmentId"
              class="border-t border-[var(--color-border)]"
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
                  @click="onDownload(row)"
                >
                  下载
                </button>
                <button
                  v-if="row.status === 'ACTIVE'"
                  type="button"
                  class="text-sm text-[var(--color-danger)] disabled:opacity-50"
                  :disabled="busyId === row.attachmentId"
                  @click="onDisable(row)"
                >
                  停用
                </button>
                <button
                  v-else
                  type="button"
                  class="text-sm text-[var(--color-brand-primary)] disabled:opacity-50"
                  :disabled="busyId === row.attachmentId"
                  @click="onRestore(row)"
                >
                  恢复
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
