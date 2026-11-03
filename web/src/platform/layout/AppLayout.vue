<script setup lang="ts">
/**
 * 登录后的平台骨架：左侧 Agent 切换 + 右侧当前 Agent 视图。
 * 根据路由 meta.themeColor 写入品牌色 CSS 变量。
 */
import AgentSwitcher from '@/platform/layout/AgentSwitcher.vue'
import { watch } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

/** 把当前 Agent 主题色落到根节点，子树共用 --color-brand-primary。 */
function applyTheme(color: unknown) {
  if (typeof color === 'string' && color.trim()) {
    document.documentElement.style.setProperty('--color-brand-primary', color.trim())
  }
}

watch(
  () => route.meta.themeColor,
  (color) => {
    applyTheme(color)
  },
  { immediate: true },
)
</script>

<template>
  <div class="h-full flex">
    <AgentSwitcher />
    <div class="flex-1 min-w-0 h-full">
      <RouterView />
    </div>
  </div>
</template>
