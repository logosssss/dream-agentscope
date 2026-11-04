import { agentList } from '@/platform/registry'
import { hasSession } from '@/shared/auth/session'
import { createRouter, createWebHistory } from 'vue-router'

/**
 * 应用路由。`/` 公开介绍页；登录/注册公开；业务页挂在 AppLayout 下。
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'landing',
      component: () => import('@/platform/landing/LandingView.vue'),
      meta: { public: true },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/agents/companion/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/agents/companion/views/RegisterView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: () => import('@/platform/layout/AppLayout.vue'),
      children: [
        {
          path: 'attachments',
          name: 'attachments',
          component: () => import('@/platform/attachments/AttachmentsView.vue'),
          meta: {
            themeColor: '#2563eb',
          },
        },
        {
          path: 'kb',
          name: 'kb',
          component: () => import('@/platform/knowledge/KnowledgeView.vue'),
          meta: {
            themeColor: '#0f766e',
          },
        },
        {
          path: 'todos',
          name: 'todos',
          component: () => import('@/platform/todos/TodosView.vue'),
          meta: {
            themeColor: '#b45309',
          },
        },
        {
          path: 'usage',
          name: 'usage',
          component: () => import('@/platform/usage/UsageView.vue'),
          meta: {
            themeColor: '#475569',
          },
        },
        {
          path: 'feedback',
          name: 'feedback',
          component: () => import('@/platform/feedback/FeedbackView.vue'),
          meta: {
            themeColor: '#be123c',
          },
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
