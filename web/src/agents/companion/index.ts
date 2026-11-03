import type { AgentRegistration } from '@/platform/registry/types'

/**
 * 智能搭子插件入口。只导出注册对象，供 platform/registry 收集。
 */
export const companionAgent: AgentRegistration = {
  id: 'companion-v1',
  slug: 'companion',
  name: '智能搭子',
  icon: 'Heart',
  themeColor: '#ec4899',
  component: () => import('./views/CompanionView.vue'),
}
