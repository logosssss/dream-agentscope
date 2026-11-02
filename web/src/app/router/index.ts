import { hasSession } from '@/shared/auth/session'
import { createRouter, createWebHistory } from 'vue-router'

/**
 * 应用路由。Step 1 只有登录页与搭子聊天页。
 */
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
      path: '/companion',
      name: 'companion',
      component: () => import('@/agents/companion/views/ChatView.vue'),
    },
    {
      path: '/',
      redirect: '/companion',
    },
  ],
})

/** 未登录访问受保护页时踢回登录。 */
router.beforeEach((to) => {
  if (to.meta.public) {
    return true
  }
  if (!hasSession()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
