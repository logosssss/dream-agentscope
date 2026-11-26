<script setup lang="ts">
/**
 * 管理端壳：顶栏导航、操作人、本进程就绪、缺 Token 提示；子路由为业务页。
 */
import { loadAdminReadiness, type ReadinessCheck } from '@/shared/api/readiness'
import { hasAdminToken, isAdminGateway, loadOperatorId, saveOperatorId } from '@/shared/auth/operator'
import { onMounted, ref } from 'vue'

const tokenReady = hasAdminToken()
const operatorId = ref('')
const readinessStatus = ref('')
const readinessChecks = ref<ReadinessCheck[]>([])
const readinessError = ref('')

onMounted(() => {
  operatorId.value = loadOperatorId()
  void loadReadiness()
})

/** 只查管理进程。失败留一句，不挡住下面的页面。 */
async function loadReadiness() {
  readinessError.value = ''
  try {
    const snapshot = await loadAdminReadiness()
    readinessStatus.value = snapshot.status
    readinessChecks.value = snapshot.checks
  } catch (e) {
    readinessError.value = e instanceof Error ? e.message : '就绪检查失败'
  }
}

/** 只按这一项上色：DOWN 红，SKIPPED 灰。总状态 DOWN 时旁边仍是 UP 的项保持原色。 */
function readinessClass(state: string): string {
  if (state === 'DOWN') {
    return 'text-[var(--color-danger)]'
  }
  if (state === 'SKIPPED') {
    return 'text-[var(--color-text-muted)]'
  }
  return 'text-[var(--color-text-main)]'
}

/** 失焦时记住操作人。 */
function persistOperator() {
  saveOperatorId(operatorId.value)
}
</script>

<template>
  <div class="h-full flex flex-col">
    <header class="border-b border-slate-200 bg-white px-4 py-3 space-y-2">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div class="flex items-center gap-4">
          <h1 class="text-base font-semibold text-[var(--color-brand-primary)]">AgentScope 管理端</h1>
          <nav class="flex gap-2 text-sm">
            <RouterLink
              to="/tenants"
              class="rounded-md px-2.5 py-1"
              active-class="bg-blue-50 text-[var(--color-brand-primary)] font-medium"
            >
              租户
            </RouterLink>
            <RouterLink
              to="/users"
              class="rounded-md px-2.5 py-1"
              active-class="bg-blue-50 text-[var(--color-brand-primary)] font-medium"
            >
              用户
            </RouterLink>
            <RouterLink
              to="/quotas"
              class="rounded-md px-2.5 py-1"
              active-class="bg-blue-50 text-[var(--color-brand-primary)] font-medium"
            >
              额度
            </RouterLink>
            <RouterLink
              to="/models"
              class="rounded-md px-2.5 py-1"
              active-class="bg-blue-50 text-[var(--color-brand-primary)] font-medium"
            >
              模型
            </RouterLink>
            <RouterLink
              to="/audits"
              class="rounded-md px-2.5 py-1"
              active-class="bg-blue-50 text-[var(--color-brand-primary)] font-medium"
            >
              审计
            </RouterLink>
            <RouterLink
              to="/runs"
              class="rounded-md px-2.5 py-1"
              active-class="bg-blue-50 text-[var(--color-brand-primary)] font-medium"
            >
              运行
            </RouterLink>
            <RouterLink
              to="/retention"
              class="rounded-md px-2.5 py-1"
              active-class="bg-blue-50 text-[var(--color-brand-primary)] font-medium"
            >
              保留
            </RouterLink>
            <RouterLink
              to="/schedules"
              class="rounded-md px-2.5 py-1"
              active-class="bg-blue-50 text-[var(--color-brand-primary)] font-medium"
            >
              调度
            </RouterLink>
            <RouterLink
              to="/evals"
              class="rounded-md px-2.5 py-1"
              active-class="bg-blue-50 text-[var(--color-brand-primary)] font-medium"
            >
              评测
            </RouterLink>
            <RouterLink
              to="/billing"
              class="rounded-md px-2.5 py-1"
              active-class="bg-blue-50 text-[var(--color-brand-primary)] font-medium"
            >
              账单
            </RouterLink>
            <RouterLink
              to="/deletions"
              class="rounded-md px-2.5 py-1"
              active-class="bg-blue-50 text-[var(--color-brand-primary)] font-medium"
            >
              删除
            </RouterLink>
            <RouterLink
              to="/tools"
              class="rounded-md px-2.5 py-1"
              active-class="bg-blue-50 text-[var(--color-brand-primary)] font-medium"
            >
              工具
            </RouterLink>
          </nav>
        </div>
        <label class="flex items-center gap-2 text-sm text-[var(--color-text-muted)]">
          <span>操作人</span>
          <input
            v-model="operatorId"
            class="w-40 rounded-md border border-slate-300 px-2 py-1 text-[var(--color-text-main)] outline-none focus-visible:ring-2 focus-visible:ring-blue-400"
            placeholder="可空，默认 admin"
            :disabled="!tokenReady"
            @blur="persistOperator"
          />
        </label>
      </div>
      <p class="text-xs text-[var(--color-text-muted)]">
        本进程就绪
        <span v-if="readinessError" class="text-[var(--color-danger)]">{{ readinessError }}</span>
        <span v-else-if="!readinessStatus">检查中…</span>
        <span v-else class="inline-flex flex-wrap gap-x-3">
          <span :class="readinessClass(readinessStatus)">{{ readinessStatus }}</span>
          <span v-for="item in readinessChecks" :key="item.name" :class="readinessClass(item.state)">
            {{ item.name }} {{ item.state }}
          </span>
        </span>
      </p>
      <p v-if="!tokenReady && !isAdminGateway()" class="text-sm text-[var(--color-danger)]">
        未配置 VITE_ADMIN_TOKEN。请复制 web-admin/.env.example 为 .env.local，填写与 AGENTSCOPE_ADMIN_TOKEN 一致的值后重启 pnpm dev。列表与操作已禁用。
      </p>
    </header>

    <main class="flex-1 min-h-0 overflow-auto p-4" :class="{ 'opacity-50 pointer-events-none': !tokenReady }">
      <RouterView />
    </main>
  </div>
</template>
