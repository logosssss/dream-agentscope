<script setup lang="ts">
/**
 * 平台最左侧 Agent 切换轨。只读 registry，不感知会话列表。
 */
import { agentList } from '@/platform/registry'
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const currentSlug = computed(() => {
  const seg = route.path.split('/').filter(Boolean)[0]
  return seg || ''
})

/** 把注册表里的 icon 标识收成一两个字，避免引入图标库。 */
function iconLabel(icon: string, name: string): string {
  if (icon === 'Heart') {
    return '搭'
  }
  if (icon === 'Pen') {
    return '记'
  }
  return name.slice(0, 1)
}
</script>

<template>
  <nav
    class="w-14 shrink-0 border-r border-slate-200 bg-white flex flex-col items-center py-3 gap-2"
    aria-label="Agent 切换"
  >
    <RouterLink
      v-for="agent in agentList"
      :key="agent.id"
      :to="`/${agent.slug}`"
      class="w-10 h-10 rounded-xl flex items-center justify-center text-sm font-semibold transition-colors"
      :class="currentSlug === agent.slug
        ? 'text-white'
        : 'text-[var(--color-text-muted)] hover:bg-slate-50'"
      :style="currentSlug === agent.slug ? { backgroundColor: agent.themeColor } : undefined"
      :title="agent.name"
    >
      {{ iconLabel(agent.icon, agent.name) }}
    </RouterLink>
  </nav>
</template>
