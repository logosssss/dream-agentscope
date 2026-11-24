<script setup lang="ts">
/**
 * 文档列表。`/docs` 展示七个主题，`/docs/:topic` 只留一个。
 * 搜索走服务端：有向量用向量，没有才关键词。输入框不再本地筛标题。
 */
import { fetchDocsTree, searchDocs } from '@/shared/api/docs-api'
import { ApiBusinessError } from '@/shared/api/http'
import type { DocsSearchHit, DocsTopicGroup } from '@/shared/types/api.types'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import DocsSearchBar from './DocsSearchBar.vue'
import { highlightParts } from './highlight'
import { DOC_TOPICS, docTypeLabel, topicLabel } from './topics'

const route = useRoute()
const groups = ref<DocsTopicGroup[]>([])
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const hits = ref<DocsSearchHit[] | null>(null)
const searching = ref(false)
const searchError = ref('')

const topicParam = computed(() => {
  const value = route.params.topic
  return typeof value === 'string' ? value : ''
})

const scopedTopic = computed(() =>
  DOC_TOPICS.some((item) => item.id === topicParam.value) ? topicParam.value : '',
)

const visibleGroups = computed(() => {
  const selected = scopedTopic.value
  return groups.value.filter((group) => !selected || group.topic === selected)
})

/** 索引没装上时说人话。其它失败原样留下。 */
function readable(cause: unknown): string {
  const message = cause instanceof Error ? cause.message : '加载失败'
  if (cause instanceof ApiBusinessError && cause.code === 'UNAVAILABLE') {
    return '文档索引暂不可用'
  }
  if (message.includes('unavailable') || message.includes('503')) {
    return '文档索引暂不可用'
  }
  return message
}

/** 拉整棵树。搜索结果出来之后树先让开。 */
async function refresh() {
  loading.value = true
  error.value = ''
  try {
    const tree = await fetchDocsTree()
    groups.value = tree.topics ?? []
  } catch (cause) {
    groups.value = []
    error.value = readable(cause)
  } finally {
    loading.value = false
  }
}

/**
 * 回车搜索。问句空则回到目录。
 * 主题页把主题带上，避免在「架构」里搜出别的主题。
 */
async function onSearch(value: string) {
  const q = value.trim()
  keyword.value = q
  if (!q) {
    hits.value = null
    searchError.value = ''
    return
  }
  searching.value = true
  searchError.value = ''
  try {
    const result = await searchDocs({
      q,
      topic: scopedTopic.value || undefined,
    })
    hits.value = result.hits ?? []
  } catch (cause) {
    hits.value = null
    searchError.value = readable(cause)
  } finally {
    searching.value = false
  }
}

/** 锚点跟在文档 id 后面，打开后滚到那一节。 */
function docLink(hit: DocsSearchHit): string {
  if (!hit.anchor) {
    return `/docs/doc/${hit.docId}`
  }
  return `/docs/doc/${hit.docId}#${encodeURIComponent(hit.anchor)}`
}

onMounted(() => {
  void refresh()
})

watch(
  () => route.params.topic,
  () => {
    keyword.value = ''
    hits.value = null
    searchError.value = ''
  },
)
</script>

<template>
  <div class="h-full overflow-auto">
    <div class="max-w-3xl mx-auto px-6 py-6 flex flex-col gap-5">
      <header class="flex flex-col gap-3">
        <div class="flex items-baseline justify-between gap-3">
          <h1 class="text-lg font-semibold">文档</h1>
          <RouterLink
            v-if="topicParam"
            to="/docs"
            class="text-sm text-[var(--color-text-muted)] hover:text-[var(--color-text-main)]"
          >
            全部主题
          </RouterLink>
        </div>
        <DocsSearchBar v-model="keyword" @search="onSearch" />
      </header>

      <p v-if="searching" class="text-sm text-[var(--color-text-muted)]">正在搜索…</p>
      <p v-else-if="searchError" class="text-sm text-[var(--color-danger)]">{{ searchError }}</p>
      <template v-else-if="hits">
        <p v-if="hits.length === 0" class="text-sm text-[var(--color-text-muted)]">没有匹配的文档。</p>
        <ul v-else class="flex flex-col gap-2">
          <li v-for="hit in hits" :key="`${hit.docId}-${hit.anchor}`">
            <RouterLink
              :to="docLink(hit)"
              class="block rounded-lg px-3 py-3 bg-[var(--color-surface-card)] border border-[var(--color-border)] hover:border-[var(--color-brand-primary)]"
            >
              <span class="block text-sm font-semibold">{{ hit.title }}</span>
              <span v-if="hit.heading" class="block text-xs text-[var(--color-text-muted)] mt-1">
                {{ hit.heading }}
              </span>
              <p class="mt-2 text-sm leading-relaxed line-clamp-4">
                <template v-for="(part, index) in highlightParts(hit.snippet, keyword)" :key="index">
                  <mark v-if="part.hit" class="bg-[color-mix(in_srgb,var(--color-warning)_35%,transparent)]">{{ part.text }}</mark>
                  <template v-else>{{ part.text }}</template>
                </template>
              </p>
            </RouterLink>
          </li>
        </ul>
      </template>

      <template v-else>
        <p v-if="loading" class="text-sm text-[var(--color-text-muted)]">正在加载文档…</p>
        <p v-else-if="error" class="text-sm text-[var(--color-danger)]">{{ error }}</p>
        <p
          v-else-if="topicParam && !DOC_TOPICS.some((item) => item.id === topicParam)"
          class="text-sm text-[var(--color-text-muted)]"
        >
          没有这个主题。
        </p>
        <p v-else-if="visibleGroups.length === 0" class="text-sm text-[var(--color-text-muted)]">
          还没有文档。
        </p>

        <template v-if="!loading && !error && (!topicParam || scopedTopic)">
          <section v-for="group in visibleGroups" :key="group.topic" class="flex flex-col gap-2">
          <h2 class="text-sm font-semibold">
            <RouterLink
              :to="`/docs/${group.topic}`"
              class="hover:text-[var(--color-brand-primary)]"
            >
              {{ topicLabel(group.topic) }}
            </RouterLink>
          </h2>
          <p v-if="group.documents.length === 0" class="text-sm text-[var(--color-text-muted)]">
            这个主题还没有文档。
          </p>
          <ul v-else class="flex flex-col gap-1">
            <li v-for="item in group.documents" :key="item.id">
              <RouterLink
                :to="`/docs/doc/${item.id}`"
                class="flex items-center gap-2 rounded-lg px-3 py-2 bg-[var(--color-surface-card)] border border-[var(--color-border)] hover:border-[var(--color-brand-primary)]"
              >
                <span class="flex-1 min-w-0 truncate text-sm">{{ item.title }}</span>
                <span class="shrink-0 text-xs text-[var(--color-text-muted)]">{{ docTypeLabel(item.docType) }}</span>
                <span
                  v-if="item.visibility === 'private'"
                  class="shrink-0 text-xs text-[var(--color-warning)]"
                >
                  私有
                </span>
              </RouterLink>
            </li>
          </ul>
          </section>
        </template>
      </template>
    </div>
  </div>
</template>
