<script setup lang="ts">
/**
 * 租户用户：必须先选租户，再列表/登记/停用/恢复。
 */
import {
  disableTenantUser,
  listTenantUsers,
  listTenants,
  registerTenantUser,
  restoreTenantUser,
} from '@/shared/api/tenant-api'
import { hasAdminToken } from '@/shared/auth/operator'
import type { TenantSummary, TenantUserSummary } from '@/shared/types/api.types'
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const tenants = ref<TenantSummary[]>([])
const tenantId = ref('')
const rows = ref<TenantUserSummary[]>([])
const loading = ref(false)
const error = ref('')
const userId = ref('')
const displayName = ref('')
const submitting = ref(false)

/** 拉取租户下拉。 */
async function loadTenantOptions() {
  if (!hasAdminToken()) {
    return
  }
  try {
    tenants.value = await listTenants(100)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '租户列表加载失败'
  }
}

/** 按当前租户刷新用户。 */
async function refreshUsers() {
  if (!hasAdminToken() || !tenantId.value.trim()) {
    rows.value = []
    return
  }
  loading.value = true
  error.value = ''
  try {
    rows.value = await listTenantUsers(tenantId.value.trim())
  } catch (e) {
    error.value = e instanceof Error ? e.message : '用户列表加载失败'
  } finally {
    loading.value = false
  }
}

/** 切换租户时同步到 query，便于分享链接。 */
function onTenantChange() {
  const id = tenantId.value.trim()
  void router.replace({ name: 'users', query: id ? { tenantId: id } : {} })
  void refreshUsers()
}

/** 登记用户。 */
async function onRegister() {
  const tid = tenantId.value.trim()
  const uid = userId.value.trim()
  if (!tid || !uid || submitting.value) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    await registerTenantUser(tid, uid, displayName.value.trim())
    userId.value = ''
    displayName.value = ''
    await refreshUsers()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '登记失败'
  } finally {
    submitting.value = false
  }
}

/** 停用：需确认。 */
async function onDisable(row: TenantUserSummary) {
  if (!window.confirm(`确认停用用户 ${row.userId}（租户 ${row.tenantId}）？`)) {
    return
  }
  error.value = ''
  try {
    await disableTenantUser(row.tenantId, row.userId)
    await refreshUsers()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '停用失败'
  }
}

/** 恢复：无需确认。 */
async function onRestore(row: TenantUserSummary) {
  error.value = ''
  try {
    await restoreTenantUser(row.tenantId, row.userId)
    await refreshUsers()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '恢复失败'
  }
}

watch(
  () => route.query.tenantId,
  (value) => {
    const next = typeof value === 'string' ? value : ''
    if (next !== tenantId.value) {
      tenantId.value = next
      void refreshUsers()
    }
  },
)

onMounted(async () => {
  const fromQuery = typeof route.query.tenantId === 'string' ? route.query.tenantId : ''
  tenantId.value = fromQuery
  await loadTenantOptions()
  await refreshUsers()
})
</script>

<template>
  <div class="space-y-4 max-w-5xl">
    <div class="flex items-center justify-between gap-3">
      <h2 class="text-lg font-semibold">用户</h2>
      <button
        type="button"
        class="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50"
        :disabled="loading || !tenantId"
        @click="refreshUsers"
      >
        刷新
      </button>
    </div>

    <label class="flex flex-wrap items-center gap-2 text-sm bg-white border border-slate-200 rounded-lg p-3">
      <span class="text-[var(--color-text-muted)]">租户</span>
      <select
        v-model="tenantId"
        class="rounded-md border border-slate-300 px-2 py-1.5 min-w-48"
        @change="onTenantChange"
      >
        <option value="">请选择租户</option>
        <option v-for="t in tenants" :key="t.tenantId" :value="t.tenantId">
          {{ t.tenantId }}（{{ t.name }} · {{ t.status }}）
        </option>
      </select>
    </label>

    <form
      v-if="tenantId"
      class="flex flex-wrap gap-2 items-end bg-white border border-slate-200 rounded-lg p-3"
      @submit.prevent="onRegister"
    >
      <label class="text-sm space-y-1">
        <span class="text-[var(--color-text-muted)]">用户 ID</span>
        <input
          v-model="userId"
          class="block w-44 rounded-md border border-slate-300 px-2 py-1.5"
          required
        />
      </label>
      <label class="text-sm space-y-1">
        <span class="text-[var(--color-text-muted)]">显示名（可空）</span>
        <input
          v-model="displayName"
          class="block w-52 rounded-md border border-slate-300 px-2 py-1.5"
        />
      </label>
      <button
        type="submit"
        class="rounded-md bg-[var(--color-brand-primary)] px-3 py-1.5 text-sm text-white disabled:opacity-50"
        :disabled="submitting"
      >
        登记
      </button>
    </form>

    <p v-if="error" class="text-sm text-[var(--color-danger)]">{{ error }}</p>
    <p v-if="!tenantId" class="text-sm text-[var(--color-text-muted)]">请先选择租户。</p>
    <p v-else-if="loading" class="text-sm text-[var(--color-text-muted)]">加载中…</p>

    <div v-if="tenantId" class="overflow-x-auto bg-white border border-slate-200 rounded-lg">
      <table class="w-full text-sm text-left">
        <thead class="bg-slate-50 text-[var(--color-text-muted)]">
          <tr>
            <th class="px-3 py-2 font-medium">用户 ID</th>
            <th class="px-3 py-2 font-medium">显示名</th>
            <th class="px-3 py-2 font-medium">状态</th>
            <th class="px-3 py-2 font-medium">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && rows.length === 0">
            <td colspan="4" class="px-3 py-6 text-center text-[var(--color-text-muted)]">该租户暂无用户</td>
          </tr>
          <tr v-for="row in rows" :key="`${row.tenantId}:${row.userId}`" class="border-t border-slate-100">
            <td class="px-3 py-2 font-mono text-xs">{{ row.userId }}</td>
            <td class="px-3 py-2">{{ row.displayName || '—' }}</td>
            <td class="px-3 py-2">{{ row.status }}</td>
            <td class="px-3 py-2">
              <div class="flex flex-wrap gap-1.5">
                <button
                  v-if="row.status === 'ACTIVE'"
                  type="button"
                  class="rounded border border-amber-300 px-2 py-0.5 text-xs text-amber-800 hover:bg-amber-50"
                  @click="onDisable(row)"
                >
                  停用
                </button>
                <button
                  v-if="row.status === 'DISABLED'"
                  type="button"
                  class="rounded border border-emerald-300 px-2 py-0.5 text-xs text-emerald-800 hover:bg-emerald-50"
                  @click="onRestore(row)"
                >
                  恢复
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
