<script setup lang="ts">
/**
 * 文档搜索框。回车或按钮才提交，输入过程不打嵌入接口。
 */
const model = defineModel<string>({ default: '' })

const emit = defineEmits<{
  search: [value: string]
}>()

/** 空白问句也交出去，外层会收回结果、回到目录。 */
function onSubmit() {
  emit('search', model.value.trim())
}
</script>

<template>
  <form class="flex gap-2" @submit.prevent="onSubmit">
    <input
      v-model="model"
      type="search"
      name="q"
      placeholder="搜索文档"
      class="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-surface-card)] px-3 py-2 text-sm outline-none focus:border-[var(--color-brand-primary)]"
      aria-label="搜索文档"
    />
    <button
      type="submit"
      class="shrink-0 rounded-lg px-3 py-2 text-sm text-white bg-[var(--color-brand-primary)]"
    >
      搜索
    </button>
  </form>
</template>
