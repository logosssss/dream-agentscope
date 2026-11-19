import { createRouter, createWebHistory } from 'vue-router'

/**
 * 管理端路由。无登录页；缺 Token 由壳层禁用操作。
 */
const router = createRouter({
  // BASE_URL 跟 Vite base 走；现网 /ops/ 时路由也要挂在子路径下。
  history: createWebHistory(import.meta.env.BASE_URL),
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
        {
          path: 'quotas',
          name: 'quotas',
          component: () => import('@/views/QuotasView.vue'),
        },
        {
          path: 'models',
          name: 'models',
          component: () => import('@/views/ModelsView.vue'),
        },
        {
          path: 'audits',
          name: 'audits',
          component: () => import('@/views/AuditsView.vue'),
        },
        {
          path: 'retention',
          name: 'retention',
          component: () => import('@/views/RetentionView.vue'),
        },
        {
          path: 'schedules',
          name: 'schedules',
          component: () => import('@/views/SchedulesView.vue'),
        },
        {
          path: 'evals',
          name: 'evals',
          component: () => import('@/views/EvalsView.vue'),
        },
        {
          path: 'billing',
          name: 'billing',
          component: () => import('@/views/BillingView.vue'),
        },
        {
          path: 'deletions',
          name: 'deletions',
          component: () => import('@/views/DeletionsView.vue'),
        },
        {
          path: 'tools',
          name: 'tools',
          component: () => import('@/views/ToolsView.vue'),
        },
      ],
    },
  ],
})

export default router
