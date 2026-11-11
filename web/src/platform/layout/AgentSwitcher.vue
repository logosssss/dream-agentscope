<script setup lang="ts">
/**
 * 平台最左侧 Agent 切换轨。只读 registry，不感知会话列表。
 * 底部提供明暗主题切换。
 */
import { agentList } from '@/platform/registry'
import { useThemeStore } from '@/platform/theme/useThemeStore'
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const theme = useThemeStore()
const currentSlug = computed(() => {
  const seg = route.path.split('/').filter(Boolean)[0]
  return seg || ''
})

/** 把注册表里的 icon 标识收成一两个字，避免引入图标库。 */
function iconLabel(icon: string, name: string): string {
  if (icon === 'Heart') {
    return '搭'
  }
  if (icon === 'Radar') {
    return '探'
  }
  if (icon === 'Pen') {
    return '记'
  }
  return name.slice(0, 1)
}
</script>

<template>
  <nav
    class="w-14 shrink-0 border-r border-[var(--color-border)] bg-[var(--color-surface-card)] flex flex-col items-center py-3 gap-2"
    aria-label="Agent 切换"
  >
    <RouterLink
      v-for="agent in agentList"
      :key="agent.id"
      :to="`/${agent.slug}`"
      class="w-10 h-10 rounded-xl flex items-center justify-center text-sm font-semibold transition-colors"
      :class="currentSlug === agent.slug
        ? 'text-white'
        : 'text-[var(--color-text-muted)] hover:bg-[var(--color-surface-muted)]'"
      :style="currentSlug === agent.slug ? { backgroundColor: agent.themeColor } : undefined"
      :title="agent.name"
    >
      {{ iconLabel(agent.icon, agent.name) }}
    </RouterLink>

    <RouterLink
      to="/attachments"
      class="w-10 h-10 rounded-xl flex items-center justify-center text-sm font-semibold transition-colors"
      :class="currentSlug === 'attachments'
        ? 'text-white bg-[var(--color-brand-primary)]'
        : 'text-[var(--color-text-muted)] hover:bg-[var(--color-surface-muted)]'"
      title="附件"
    >
      附
    </RouterLink>

    <RouterLink
      to="/kb"
      class="w-10 h-10 rounded-xl flex items-center justify-center text-sm font-semibold transition-colors"
      :class="currentSlug === 'kb'
        ? 'text-white bg-[var(--color-brand-primary)]'
        : 'text-[var(--color-text-muted)] hover:bg-[var(--color-surface-muted)]'"
      title="知识库"
    >
      知
    </RouterLink>

    <RouterLink
      to="/todos"
      class="w-10 h-10 rounded-xl flex items-center justify-center text-sm font-semibold transition-colors"
      :class="currentSlug === 'todos'
        ? 'text-white bg-[var(--color-brand-primary)]'
        : 'text-[var(--color-text-muted)] hover:bg-[var(--color-surface-muted)]'"
      title="待办"
    >
      办
    </RouterLink>

    <RouterLink
      to="/usage"
      class="w-10 h-10 rounded-xl flex items-center justify-center text-sm font-semibold transition-colors"
      :class="currentSlug === 'usage'
        ? 'text-white bg-[var(--color-brand-primary)]'
        : 'text-[var(--color-text-muted)] hover:bg-[var(--color-surface-muted)]'"
      title="用量"
    >
      用
    </RouterLink>

    <RouterLink
      to="/feedback"
      class="w-10 h-10 rounded-xl flex items-center justify-center text-sm font-semibold transition-colors"
      :class="currentSlug === 'feedback'
        ? 'text-white bg-[var(--color-brand-primary)]'
        : 'text-[var(--color-text-muted)] hover:bg-[var(--color-surface-muted)]'"
      title="反馈"
    >
      评
    </RouterLink>

    <button
      type="button"
      class="mt-auto w-10 h-10 rounded-xl text-xs font-medium text-[var(--color-text-muted)] hover:bg-[var(--color-surface-muted)]"
      :title="theme.mode === 'dark' ? '切换为浅色' : '切换为深色'"
      @click="theme.toggle()"
    >
      {{ theme.mode === 'dark' ? '浅' : '深' }}
    </button>
  </nav>
</template>
