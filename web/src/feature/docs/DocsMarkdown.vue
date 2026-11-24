<script setup lang="ts">
/**
 * 文档正文。标题 id 和文档链接都来自服务端算好的目录与替换表，这里只渲染。
 */
import type { DocsResolvedLink, DocsTocEntry } from '@/shared/types/api.types'
import { computed } from 'vue'
import { renderDocsMarkdown } from './renderDocsMarkdown'

const props = defineProps<{
  source: string
  links?: DocsResolvedLink[]
  toc?: DocsTocEntry[]
}>()

const html = computed(() => renderDocsMarkdown(props.source, props.links, props.toc))
</script>

<template>
  <div class="md-content docs-md break-words text-sm leading-relaxed" v-html="html" />
</template>

<style scoped>
.docs-md :deep(h1) {
  font-size: 1.5rem;
  font-weight: 650;
  margin: 0 0 0.75rem;
  scroll-margin-top: 0.75rem;
}

.docs-md :deep(h2),
.docs-md :deep(h3) {
  font-weight: 650;
  scroll-margin-top: 0.75rem;
}

.docs-md :deep(h2) {
  font-size: 1.15rem;
  margin: 1.4rem 0 0.5rem;
}

.docs-md :deep(h3) {
  font-size: 1rem;
  margin: 1rem 0 0.4rem;
}
</style>
