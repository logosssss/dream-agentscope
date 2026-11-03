<script setup lang="ts">
/**
 * 搭子输入区上方轻提示。可带当前会话待确认条数，不接额外后端。
 */
defineProps<{
  /** 当前会话仍待确认的条数；0 不展示审批句 */
  pendingCount?: number
}>()
</script>

<template>
  <div class="text-xs text-[var(--color-text-muted)] leading-relaxed space-y-1">
    <p>
      搭子会记住<strong class="font-medium text-[var(--color-brand-primary)]">成功结束</strong>的对话里的称呼与偏好；点左侧历史可一次拉齐气泡。
    </p>
    <p v-if="(pendingCount ?? 0) > 0" class="text-[var(--color-brand-primary)]">
      本会话有 {{ pendingCount }} 条工具待确认，可在左侧「待确认」批准或拒绝；批准后不会自动继续，需点「重发上一条」。
    </p>
  </div>
</template>
