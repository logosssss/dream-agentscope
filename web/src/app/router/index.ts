import { agentList } from '@/platform/registry'
import { hasSession } from '@/shared/auth/session'
import { createRouter, createWebHistory } from 'vue-router'

/**
 * 应用路由。登录页独立；业务页挂在 AppLayout 下，由 registry 生成。
 */
const defaultAgentPath = agentList[0] ? `/${agentList[0].slug}` : '/companion'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/agents/companion/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: () => import('@/platform/layout/AppLayout.vue'),
      children: [
        {
          path: '',
          redirect: defaultAgentPath,
        },
        ...agentList.map((agent) => ({
          path: agent.slug,
          name: agent.slug,
          component: agent.component,
          meta: {
            themeColor: agent.themeColor,
            agentId: agent.id,
          },
        })),
      ],
    },
  ],
})

/** 把路由上的主题色写到根节点，保证守卫阶段就能换色。 */
function applyThemeFromRoute(themeColor: unknown) {
  if (typeof themeColor === 'string' && themeColor.trim()) {
    document.documentElement.style.setProperty('--color-brand-primary', themeColor.trim())
  }
}

/** 未登录踢回登录；已登录则按 Agent 主题上色。 */
router.beforeEach((to) => {
  if (to.meta.public) {
    return true
  }
  if (!hasSession()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  applyThemeFromRoute(to.meta.themeColor)
  return true
})

export default router
