<script setup lang="ts">
/**
 * 阅读页右侧的单篇提问。答案按纯文本显示，引用按钮滚到正文里的标题。
 */
import { askDocs } from '@/shared/api/docs-api'
import { ApiBusinessError } from '@/shared/api/http'
import { hasSession } from '@/shared/auth/session'
import type { DocsCitation } from '@/shared/types/api.types'
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

const props = defineProps<{
  docId: string
}>()

const emit = defineEmits<{
  jump: [anchor: string]
}>()

const route = useRoute()
const open = ref(false)
const question = ref('')
const asking = ref(false)
const answer = ref('')
const citations = ref<DocsCitation[]>([])
const error = ref('')

const loginTo = computed(() => ({
  path: '/login',
  query: { redirect: route.fullPath },
}))

/** 换一篇时清掉上一篇的答案，避免串到新文档。 */
watch(
  () => props.docId,
  () => {
    question.value = ''
    answer.value = ''
    citations.value = []
    error.value = ''
  },
)

/** 登录、额度和索引失败都说人话。其它错误保留服务端原文。 */
function readable(cause: unknown): string {
  if (cause instanceof ApiBusinessError && cause.code === 'UNAUTHORIZED') {
    return '请先登录后再提问'
  }
  if (cause instanceof ApiBusinessError && cause.code === 'QUOTA_EXCEEDED') {
    return '今日额度已用完'
  }
  if (cause instanceof ApiBusinessError && cause.code === 'NOT_FOUND') {
    return '这篇文档不存在'
  }
  if (cause instanceof ApiBusinessError && cause.code === 'UNAVAILABLE') {
    return '提问暂不可用'
  }
  return cause instanceof Error ? cause.message : '提问失败'
}

/** 空白问题不发请求。答案和引用一起换掉，失败时清掉旧答案。 */
async function submit() {
  const q = question.value.trim()
  if (!q || asking.value || !hasSession()) {
    return
  }
  asking.value = true
  error.value = ''
  try {
    const result = await askDocs(props.docId, q)
    answer.value = result.answer
    citations.value = result.citations ?? []
  } catch (cause) {
    answer.value = ''
    citations.value = []
    error.value = readable(cause)
  } finally {
    asking.value = false
  }
}
</script>

<template>
  <section class="border-t border-[var(--color-border)] pt-3 md:border-t-0">
    <button
      type="button"
      class="text-sm font-semibold text-[var(--color-text-main)] hover:text-[var(--color-brand-primary)]"
      :aria-expanded="open"
      @click="open = !open"
    >
      {{ open ? '收起提问' : '就这篇提问' }}
    </button>

    <form v-if="open" class="mt-2 flex flex-col gap-2" @submit.prevent="submit">
      <p v-if="!hasSession()" class="text-xs text-[var(--color-text-muted)]">
        登录后才能就这篇提问。
        <RouterLink :to="loginTo" class="text-[var(--color-brand-primary)]">去登录</RouterLink>
      </p>
      <textarea
        v-model="question"
        rows="3"
        class="w-full rounded border border-[var(--color-border)] bg-transparent px-2 py-1 text-sm"
        placeholder="问这篇里的一个问题"
        :disabled="!hasSession() || asking"
      />
      <button
        type="submit"
        class="self-start rounded bg-[var(--color-brand-primary)] px-3 py-1 text-sm text-white disabled:opacity-50"
        :disabled="!hasSession() || asking || !question.trim()"
      >
        {{ asking ? '正在问…' : '提问' }}
      </button>
      <p v-if="error" class="text-xs text-[var(--color-danger)]">{{ error }}</p>
      <p v-if="answer" class="whitespace-pre-wrap text-sm text-[var(--color-text-main)]">{{ answer }}</p>
      <div v-if="citations.length" class="flex flex-col gap-1">
        <p class="text-xs text-[var(--color-text-muted)]">引用到的小节</p>
        <button
          v-for="(cite, index) in citations"
          :key="`${cite.anchor}-${index}`"
          type="button"
          class="text-left text-sm text-[var(--color-brand-primary)] hover:underline"
          @click="emit('jump', cite.anchor)"
        >
          {{ cite.quote }}
        </button>
      </div>
    </form>
  </section>
</template>
