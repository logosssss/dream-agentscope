<script setup lang="ts">
/**
 * 一篇文档。右侧目录和提问都滚到对应标题；正文里的同仓链接由渲染器替换。
 */
import { fetchDocsPage } from '@/shared/api/docs-api'
import { ApiBusinessError } from '@/shared/api/http'
import type { DocsPage } from '@/shared/types/api.types'
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import DocsAskPanel from './DocsAskPanel.vue'
import DocsMarkdown from './DocsMarkdown.vue'
import { topicLabel } from './topics'

const route = useRoute()
const page = ref<DocsPage | null>(null)
const loading = ref(false)
const error = ref('')

const docId = computed(() => {
  const value = route.params.id
  return typeof value === 'string' ? value : ''
})

const indexedAt = computed(() => formatIndexedAt(page.value?.updatedAt))

/** 索引时间给阅读页看「同步到哪」。空值说明这一行还没写过时间。 */
function formatIndexedAt(value: string | null | undefined): string {
  if (!value) {
    return '尚未同步'
  }
  const text = value.replace('T', ' ')
  return text.length >= 16 ? text.slice(0, 16) : text
}

/** 私有和没有是同一种结果。索引没装上单独说明。 */
function readable(cause: unknown): string {
  if (cause instanceof ApiBusinessError && cause.code === 'NOT_FOUND') {
    return '这篇文档不存在'
  }
  if (cause instanceof ApiBusinessError && cause.code === 'UNAVAILABLE') {
    return '文档索引暂不可用'
  }
  const message = cause instanceof Error ? cause.message : '加载失败'
  if (message.includes('unavailable') || message.includes('503')) {
    return '文档索引暂不可用'
  }
  return message
}

/** 打开一篇。id 变了就重拉，避免从目录点进下一篇还留着上一篇。 */
async function load(id: string) {
  if (!id) {
    page.value = null
    error.value = '这篇文档不存在'
    return
  }
  loading.value = true
  error.value = ''
  page.value = null
  try {
    page.value = await fetchDocsPage(id)
    await nextTick()
    const hash = route.hash.startsWith('#') ? decodeURIComponent(route.hash.slice(1)) : ''
    if (hash) {
      jump(hash)
    }
  } catch (cause) {
    page.value = null
    error.value = readable(cause)
  } finally {
    loading.value = false
  }
}

/** 目录点在文章容器里滚，不靠整页滚动。标题 id 与目录锚点一致。 */
function jump(anchor: string) {
  const node = document.getElementById(anchor)
  node?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

watch(docId, (id) => {
  void load(id)
}, { immediate: true })
</script>

<template>
  <div class="h-full min-h-0 flex flex-col">
    <header class="shrink-0 border-b border-[var(--color-border)] px-6 py-3 flex flex-col gap-2">
      <nav class="text-sm text-[var(--color-text-muted)] flex flex-wrap items-center gap-1" aria-label="面包屑">
        <RouterLink to="/docs" class="hover:text-[var(--color-text-main)]">文档</RouterLink>
        <span>/</span>
        <RouterLink
          v-if="page"
          :to="`/docs/${page.topic}`"
          class="hover:text-[var(--color-text-main)]"
        >
          {{ topicLabel(page.topic) }}
        </RouterLink>
        <template v-if="page">
          <span>/</span>
          <span class="text-[var(--color-text-main)] truncate max-w-xs">{{ page.title }}</span>
        </template>
      </nav>
      <div v-if="page" class="flex flex-wrap items-center gap-2 text-xs">
        <span class="rounded-full bg-[var(--color-surface-muted)] px-2 py-0.5">
          {{ topicLabel(page.topic) }}
        </span>
        <span class="text-[var(--color-text-muted)]">同步时间 {{ indexedAt }}</span>
      </div>
    </header>

    <p v-if="loading" class="px-6 py-4 text-sm text-[var(--color-text-muted)]">正在打开文档…</p>
    <p v-else-if="error" class="px-6 py-4 text-sm text-[var(--color-danger)]">{{ error }}</p>

    <div v-else-if="page" class="flex-1 min-h-0 flex flex-col md:flex-row">
      <article class="flex-1 min-w-0 overflow-auto px-6 py-5">
        <DocsMarkdown :source="page.markdown" :links="page.links" :toc="page.toc" />
      </article>
      <aside
        class="flex w-full shrink-0 flex-col gap-3 overflow-auto border-t border-[var(--color-border)] px-4 py-4 md:w-56 md:border-l md:border-t-0 md:py-5"
        aria-label="目录和提问"
      >
        <nav class="hidden md:flex flex-col gap-2" aria-label="目录">
          <p class="text-xs font-semibold text-[var(--color-text-muted)]">目录</p>
          <p v-if="page.toc.length === 0" class="text-xs text-[var(--color-text-muted)]">这篇没有小节。</p>
          <a
            v-for="entry in page.toc"
            :key="entry.anchor"
            :href="`#${encodeURIComponent(entry.anchor)}`"
            class="text-sm text-[var(--color-text-main)] hover:text-[var(--color-brand-primary)]"
            @click.prevent="jump(entry.anchor)"
          >
            {{ entry.heading }}
          </a>
        </nav>
        <DocsAskPanel :doc-id="docId" @jump="jump" />
      </aside>
    </div>
  </div>
</template>
