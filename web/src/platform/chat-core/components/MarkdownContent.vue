<script setup lang="ts">
/**
 * 助手气泡 Markdown 渲染。关闭 html，降低 XSS；流式半截语法也能尽量显示。
 */
import MarkdownIt from 'markdown-it'
import { computed } from 'vue'

const props = defineProps<{
  /** 原始 Markdown / 纯文本 */
  source: string
}>()

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

const html = computed(() => {
  const raw = props.source ?? ''
  if (!raw) {
    return ''
  }
  return md.render(raw)
})
</script>

<template>
  <div class="md-content break-words text-sm leading-relaxed" v-html="html" />
</template>
