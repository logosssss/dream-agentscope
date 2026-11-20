import { companionAgent } from '@/agents/companion'
import { echoAgent } from '@/agents/echo'
import { sketchAgent } from '@/agents/sketch'
import type { AgentRegistration } from '@/platform/registry/types'

/**
 * 已注册的 Agent 插件表。顺序即导航顺序。
 * platform 只收集，不引用各 Agent 内部业务模块以外的入口。
 */
export const agentList: AgentRegistration[] = [companionAgent, echoAgent, sketchAgent]

/** 按 slug 查找；没有时返回 undefined。 */
export function findAgentBySlug(slug: string): AgentRegistration | undefined {
  return agentList.find((a) => a.slug === slug)
}
