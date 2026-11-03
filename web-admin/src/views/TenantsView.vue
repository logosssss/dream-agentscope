<script setup lang="ts">
/**
 * 租户目录：列表、登记、停用/关闭/恢复。
 */
import {
  closeTenant,
  listTenants,
  registerTenant,
  restoreTenant,
  suspendTenant,
} from '@/shared/api/tenant-api'
import { hasAdminToken } from '@/shared/auth/operator'
import type { TenantSummary } from '@/shared/types/api.types'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const rows = ref<TenantSummary[]>([])
const loading = ref(false)
const error = ref('')
const tenantId = ref('')
const name = ref('')
const submitting = ref(false)

/** 刷新租户列表。 */
async function refresh() {
  if (!hasAdminToken()) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    rows.value = await listTenants()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

/** 登记新租户。 */
async function onRegister() {
  const id = tenantId.value.trim()
  const label = name.value.trim()
  if (!id || !label || submitting.value) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    await registerTenant(id, label)
    tenantId.value = ''
    name.value = ''
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '登记失败'
  } finally {
    submitting.value = false
  }
}

/** 停用：需确认。 */
async function onSuspend(row: TenantSummary) {
  if (!window.confirm(`确认停用租户 ${row.tenantId}？停用后该租户无法访问用户进程。`)) {
    return
  }
  error.value = ''
  try {
    await suspendTenant(row.tenantId)
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '停用失败'
  }
}

/** 关闭：需确认。 */
async function onClose(row: TenantSummary) {
  if (!window.confirm(`确认关闭租户 ${row.tenantId}？关闭后需恢复才能再用。`)) {
    return
  }
  error.value = ''
  try {
    await closeTenant(row.tenantId)
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '关闭失败'
  }
}

/** 恢复：无需确认。 */
async function onRestore(row: TenantSummary) {
  error.value = ''
  try {
    await restoreTenant(row.tenantId)
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '恢复失败'
  }
}

/** 跳到用户页并带上租户。 */
function goUsers(row: TenantSummary) {
  void router.push({ name: 'users', query: { tenantId: row.tenantId } })
}

onMounted(() => {
  void refresh()
})
</script>

<template>
  <div class="space-y-4 max-w-5xl">
    <div class="flex items-center justify-between gap-3">
      <h2 class="text-lg font-semibold">租户</h2>
      <button
        type="button"
        class="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50"
        :disabled="loading"
        @click="refresh"
      >
        刷新
      </button>
    </div>

    <form class="flex flex-wrap gap-2 items-end bg-white border border-slate-200 rounded-lg p-3" @submit.prevent="onRegister">
      <label class="text-sm space-y-1">
        <span class="text-[var(--color-text-muted)]">租户 ID</span>
        <input
          v-model="tenantId"
          class="block w-44 rounded-md border border-slate-300 px-2 py-1.5"
          required
        />
      </label>
      <label class="text-sm space-y-1">
        <span class="text-[var(--color-text-muted)]">名称</span>
        <input
          v-model="name"
          class="block w-52 rounded-md border border-slate-300 px-2 py-1.5"
          required
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
    <p v-if="loading" class="text-sm text-[var(--color-text-muted)]">加载中…</p>

    <div class="overflow-x-auto bg-white border border-slate-200 rounded-lg">
      <table class="w-full text-sm text-left">
        <thead class="bg-slate-50 text-[var(--color-text-muted)]">
          <tr>
            <th class="px-3 py-2 font-medium">租户 ID</th>
            <th class="px-3 py-2 font-medium">名称</th>
            <th class="px-3 py-2 font-medium">套餐</th>
            <th class="px-3 py-2 font-medium">状态</th>
            <th class="px-3 py-2 font-medium">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && rows.length === 0">
            <td colspan="5" class="px-3 py-6 text-center text-[var(--color-text-muted)]">暂无租户</td>
          </tr>
          <tr v-for="row in rows" :key="row.tenantId" class="border-t border-slate-100">
            <td class="px-3 py-2 font-mono text-xs">{{ row.tenantId }}</td>
            <td class="px-3 py-2">{{ row.name }}</td>
            <td class="px-3 py-2">{{ row.plan }}</td>
            <td class="px-3 py-2">{{ row.status }}</td>
            <td class="px-3 py-2">
              <div class="flex flex-wrap gap-1.5">
                <button
                  type="button"
                  class="rounded border border-slate-300 px-2 py-0.5 text-xs hover:bg-slate-50"
                  @click="goUsers(row)"
                >
                  用户
                </button>
                <button
                  v-if="row.status === 'ACTIVE'"
                  type="button"
                  class="rounded border border-amber-300 px-2 py-0.5 text-xs text-amber-800 hover:bg-amber-50"
                  @click="onSuspend(row)"
                >
                  停用
                </button>
                <button
                  v-if="row.status !== 'CLOSED'"
                  type="button"
                  class="rounded border border-red-300 px-2 py-0.5 text-xs text-red-700 hover:bg-red-50"
                  @click="onClose(row)"
                >
                  关闭
                </button>
                <button
                  v-if="row.status === 'SUSPENDED' || row.status === 'CLOSED'"
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
