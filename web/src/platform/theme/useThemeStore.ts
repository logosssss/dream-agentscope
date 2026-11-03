import { defineStore } from 'pinia'
import { ref } from 'vue'

const STORAGE_KEY = 'agentscope.theme'

export type ThemeMode = 'light' | 'dark'

/**
 * 客户端明暗主题。写入 html[data-theme]，并记住到 localStorage。
 * 不改品牌色；Agent 路由仍覆盖 --color-brand-primary。
 */
export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>('light')

  /** 把当前模式落到根节点与本地。 */
  function apply(next: ThemeMode) {
    mode.value = next
    document.documentElement.dataset.theme = next
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // 隐私模式写失败不影响当次显示
    }
  }

  /** 启动时读本地；非法值按 light。 */
  function hydrate() {
    let saved: string | null = null
    try {
      saved = localStorage.getItem(STORAGE_KEY)
    } catch {
      saved = null
    }
    apply(saved === 'dark' ? 'dark' : 'light')
  }

  /** 明暗互切。 */
  function toggle() {
    apply(mode.value === 'dark' ? 'light' : 'dark')
  }

  return { mode, hydrate, toggle, apply }
})
