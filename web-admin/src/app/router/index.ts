import { createRouter, createWebHistory } from 'vue-router'

/**
 * 管理端路由。无登录页；缺 Token 由壳层禁用操作。
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: () => import('@/layout/AdminShell.vue'),
      children: [
        { path: '', redirect: '/tenants' },
        {
          path: 'tenants',
          name: 'tenants',
          component: () => import('@/views/TenantsView.vue'),
        },
        {
          path: 'users',
          name: 'users',
          component: () => import('@/views/UsersView.vue'),
        },
      ],
    },
  ],
})

export default router
