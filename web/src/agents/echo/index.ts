import type { AgentRegistration } from '@/platform/registry/types'

/**
 * 路由探测 Agent 插件入口。现网小入口，agentId 与后端 echo-v1 对齐。
 */
export const echoAgent: AgentRegistration = {
  id: 'echo-v1',
  slug: 'echo',
  name: '路由探测',
  icon: 'Radar',
  themeColor: '#64748b',
  component: () => import('./views/EchoView.vue'),
}
