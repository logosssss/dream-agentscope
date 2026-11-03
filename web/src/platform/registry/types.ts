import type { Component } from 'vue'

/**
 * Agent 插件注册契约。新增 Agent 时实现一份并加入 registry 列表即可。
 */
export interface AgentRegistration {
  /** 与后端 agentId 对齐，例如 companion-v1 */
  id: string
  /** 前端路由段，例如 companion → /companion */
  slug: string
  /** 导航显示名 */
  name: string
  /** 侧栏图标标识，由 AgentSwitcher 解释 */
  icon: string
  /** 进入该 Agent 时写入 --color-brand-primary */
  themeColor: string
  /** 异步加载主视图 */
  component: () => Promise<Component>
}
