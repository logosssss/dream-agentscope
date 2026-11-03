import type { AgentRegistration } from '@/platform/registry/types'

/**
 * 速记草稿占位插件。只为验证 registry 多 Agent 切换与本地消息隔离，不是正式产品。
 */
export const sketchAgent: AgentRegistration = {
  id: 'sketch-v1',
  slug: 'sketch',
  name: '速记草稿',
  icon: 'Pen',
  themeColor: '#0d9488',
  component: () => import('./views/SketchView.vue'),
}
