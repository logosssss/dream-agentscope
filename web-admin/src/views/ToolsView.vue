<script setup lang="ts">
/**
 * 工具：目录登记与权限规则登记。不执行工具。
 */
import {
  disableToolPermission,
  listToolPermissions,
  listToolRegistries,
  registerToolPermission,
  registerToolRegistry,
  restoreToolPermission,
  updateToolPermissionPriority,
} from '@/shared/api/tool-api'
import { hasAdminToken } from '@/shared/auth/operator'
import type { ToolPermissionSummary, ToolRegistrySummary } from '@/shared/types/api.types'
import { onMounted, ref, watch } from 'vue'

type Tab = 'registry' | 'permissions'

const DECISIONS = ['ALLOW', 'ASK', 'DENY'] as const

const tab = ref<Tab>('registry')
const registries = ref<ToolRegistrySummary[]>([])
const permissions = ref<ToolPermissionSummary[]>([])
const loading = ref(false)
const error = ref('')
const submitting = ref(false)

const toolId = ref('')
const name = ref('')
const moduleName = ref('')
const description = ref('')

const permToolId = ref('')
const tenantId = ref('')
const roleId = ref('')
const decision = ref<(typeof DECISIONS)[number]>('ASK')
const priority = ref('100')
const busyKey = ref('')
const editPriority = ref<Record<string, string>>({})

/** 按当前页签刷新。 */
async function refresh() {
  if (!hasAdminToken()) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    if (tab.value === 'registry') {
      registries.value = await listToolRegistries()
    } else {
      permissions.value = await listToolPermissions()
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

/** 登记目录行。 */
async function onRegisterRegistry() {
  const id = toolId.value.trim()
  const n = name.value.trim()
  if (!id || !n || submitting.value) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    await registerToolRegistry({
      toolId: id,
      name: n,
      module: moduleName.value.trim(),
      description: description.value.trim(),
    })
    toolId.value = ''
    name.value = ''
    moduleName.value = ''
    description.value = ''
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '登记失败'
  } finally {
    submitting.value = false
  }
}

/** 列表行键。 */
function rowKey(row: ToolPermissionSummary): string {
  return `${row.toolId}:${row.tenantId}:${row.roleId}`
}

/** 登记权限规则。 */
async function onRegisterPermission() {
  const id = permToolId.value.trim()
  if (!id || submitting.value) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    await registerToolPermission({
      toolId: id,
      tenantId: tenantId.value.trim(),
      roleId: roleId.value.trim(),
      decision: decision.value,
      priority: priority.value,
    })
    permToolId.value = ''
    tenantId.value = ''
    roleId.value = ''
    priority.value = '100'
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '登记失败'
  } finally {
    submitting.value = false
  }
}

/** 停用仍启用的规则。 */
async function onDisable(row: ToolPermissionSummary) {
  const key = rowKey(row)
  if (busyKey.value) {
    return
  }
  if (!window.confirm(`停用规则 ${row.toolId}（${row.tenantId}/${row.roleId}）？`)) {
    return
  }
  busyKey.value = key
  error.value = ''
  try {
    await disableToolPermission(row.toolId, row.tenantId, row.roleId)
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '停用失败'
  } finally {
    busyKey.value = ''
  }
}

/** 恢复已停用的规则。 */
async function onRestore(row: ToolPermissionSummary) {
  const key = rowKey(row)
  if (busyKey.value) {
    return
  }
  busyKey.value = key
  error.value = ''
  try {
    await restoreToolPermission(row.toolId, row.tenantId, row.roleId)
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '恢复失败'
  } finally {
    busyKey.value = ''
  }
}

/** 改这一行的优先级。 */
async function onUpdatePriority(row: ToolPermissionSummary) {
  const key = rowKey(row)
  if (busyKey.value) {
    return
  }
  const raw = (editPriority.value[key] ?? String(row.priority)).trim()
  const next = Number.parseInt(raw, 10)
  if (!Number.isFinite(next) || next < 0) {
    error.value = '优先级须从 0 起'
    return
  }
  busyKey.value = key
  error.value = ''
  try {
    await updateToolPermissionPriority(row.toolId, row.tenantId, row.roleId, next)
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '改优先级失败'
  } finally {
    busyKey.value = ''
  }
}

watch(tab, () => {
  void refresh()
})

onMounted(() => {
  void refresh()
})
</script>

<template>
  <div class="space-y-4 max-w-5xl">
    <div class="flex items-center justify-between gap-3">
      <h2 class="text-lg font-semibold">工具</h2>
      <button
        type="button"
        class="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50"
        :disabled="loading"
        @click="refresh"
      >
        刷新
      </button>
    </div>

    <div class="flex flex-wrap gap-1 border-b border-slate-200 pb-2">
      <button
        type="button"
        class="rounded-md px-3 py-1.5 text-sm"
        :class="tab === 'registry'
          ? 'bg-blue-50 text-[var(--color-brand-primary)] font-medium'
          : 'text-[var(--color-text-muted)] hover:bg-slate-50'"
        @click="tab = 'registry'"
      >
        目录
      </button>
      <button
        type="button"
        class="rounded-md px-3 py-1.5 text-sm"
        :class="tab === 'permissions'
          ? 'bg-blue-50 text-[var(--color-brand-primary)] font-medium'
          : 'text-[var(--color-text-muted)] hover:bg-slate-50'"
        @click="tab = 'permissions'"
      >
        权限
      </button>
    </div>

    <p v-if="error" class="text-sm text-[var(--color-danger)]">{{ error }}</p>
    <p v-if="loading" class="text-sm text-[var(--color-text-muted)]">加载中…</p>

    <template v-if="tab === 'registry'">
      <p class="text-xs text-[var(--color-text-muted)]">
        版本固定 1.0.0。只登记标识；没有对应代码实现时运行层仍不会挂到 Toolkit。
      </p>
      <form
        class="flex flex-wrap gap-2 items-end bg-white border border-slate-200 rounded-lg p-3"
        @submit.prevent="onRegisterRegistry"
      >
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">工具 ID</span>
          <input
            v-model="toolId"
            class="block w-40 rounded-md border border-slate-300 px-2 py-1.5 font-mono text-xs"
            required
          />
        </label>
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">展示名</span>
          <input v-model="name" class="block w-36 rounded-md border border-slate-300 px-2 py-1.5" required />
        </label>
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">模块</span>
          <input
            v-model="moduleName"
            class="block w-32 rounded-md border border-slate-300 px-2 py-1.5"
            placeholder="可空"
          />
        </label>
        <label class="text-sm space-y-1 grow min-w-[12rem]">
          <span class="text-[var(--color-text-muted)]">说明</span>
          <input v-model="description" class="block w-full rounded-md border border-slate-300 px-2 py-1.5" />
        </label>
        <button
          type="submit"
          class="rounded-md bg-[var(--color-brand-primary)] px-3 py-1.5 text-sm text-white disabled:opacity-50"
          :disabled="submitting"
        >
          登记
        </button>
      </form>

      <div class="overflow-x-auto bg-white border border-slate-200 rounded-lg">
        <table class="w-full text-sm text-left">
          <thead class="bg-slate-50 text-[var(--color-text-muted)]">
            <tr>
              <th class="px-3 py-2 font-medium">工具 ID</th>
              <th class="px-3 py-2 font-medium">名称</th>
              <th class="px-3 py-2 font-medium">版本</th>
              <th class="px-3 py-2 font-medium">模块</th>
              <th class="px-3 py-2 font-medium">说明</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!loading && registries.length === 0">
              <td colspan="5" class="px-3 py-6 text-center text-[var(--color-text-muted)]">暂无工具</td>
            </tr>
            <tr v-for="row in registries" :key="row.toolId" class="border-t border-slate-100">
              <td class="px-3 py-2 font-mono text-xs">{{ row.toolId }}</td>
              <td class="px-3 py-2">{{ row.name }}</td>
              <td class="px-3 py-2">{{ row.version }}</td>
              <td class="px-3 py-2 font-mono text-xs">{{ row.module || '—' }}</td>
              <td class="px-3 py-2 text-[var(--color-text-muted)] max-w-xs truncate">
                {{ row.description || '—' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <template v-else>
      <p class="text-xs text-[var(--color-text-muted)]">
        租户/角色空表示全局 `*`；优先级空按 100。conditions 固定为空，当前版本才能命中。不执行工具。
      </p>
      <form
        class="flex flex-wrap gap-2 items-end bg-white border border-slate-200 rounded-lg p-3"
        @submit.prevent="onRegisterPermission"
      >
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">工具 ID</span>
          <input
            v-model="permToolId"
            class="block w-40 rounded-md border border-slate-300 px-2 py-1.5 font-mono text-xs"
            required
          />
        </label>
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">租户</span>
          <input
            v-model="tenantId"
            class="block w-32 rounded-md border border-slate-300 px-2 py-1.5"
            placeholder="空=全局"
          />
        </label>
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">角色</span>
          <input
            v-model="roleId"
            class="block w-32 rounded-md border border-slate-300 px-2 py-1.5"
            placeholder="空=全局"
          />
        </label>
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">决定</span>
          <select v-model="decision" class="block w-28 rounded-md border border-slate-300 px-2 py-1.5">
            <option v-for="d in DECISIONS" :key="d" :value="d">{{ d }}</option>
          </select>
        </label>
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">优先级</span>
          <input
            v-model="priority"
            type="number"
            min="0"
            class="block w-24 rounded-md border border-slate-300 px-2 py-1.5"
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

      <div class="overflow-x-auto bg-white border border-slate-200 rounded-lg">
        <table class="w-full text-sm text-left">
          <thead class="bg-slate-50 text-[var(--color-text-muted)]">
            <tr>
              <th class="px-3 py-2 font-medium">工具 ID</th>
              <th class="px-3 py-2 font-medium">租户</th>
              <th class="px-3 py-2 font-medium">角色</th>
              <th class="px-3 py-2 font-medium">决定</th>
              <th class="px-3 py-2 font-medium">优先级</th>
              <th class="px-3 py-2 font-medium">启用</th>
              <th class="px-3 py-2 font-medium">动作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!loading && permissions.length === 0">
              <td colspan="7" class="px-3 py-6 text-center text-[var(--color-text-muted)]">暂无规则</td>
            </tr>
            <tr
              v-for="row in permissions"
              :key="rowKey(row)"
              class="border-t border-slate-100"
            >
              <td class="px-3 py-2 font-mono text-xs">{{ row.toolId }}</td>
              <td class="px-3 py-2 font-mono text-xs">{{ row.tenantId }}</td>
              <td class="px-3 py-2 font-mono text-xs">{{ row.roleId }}</td>
              <td class="px-3 py-2">{{ row.decision }}</td>
              <td class="px-3 py-2">
                <input
                  v-model="editPriority[rowKey(row)]"
                  type="number"
                  min="0"
                  class="w-20 rounded-md border border-slate-300 px-1.5 py-1"
                  :placeholder="String(row.priority)"
                />
              </td>
              <td class="px-3 py-2">{{ row.enabled ? '是' : '否' }}</td>
              <td class="px-3 py-2 space-x-2 whitespace-nowrap">
                <button
                  type="button"
                  class="text-sm text-[var(--color-brand-primary)] disabled:opacity-50"
                  :disabled="busyKey === rowKey(row)"
                  @click="onUpdatePriority(row)"
                >
                  改优先级
                </button>
                <button
                  v-if="row.enabled"
                  type="button"
                  class="text-sm text-[var(--color-danger)] disabled:opacity-50"
                  :disabled="busyKey === rowKey(row)"
                  @click="onDisable(row)"
                >
                  停用
                </button>
                <button
                  v-else
                  type="button"
                  class="text-sm text-[var(--color-brand-primary)] disabled:opacity-50"
                  :disabled="busyKey === rowKey(row)"
                  @click="onRestore(row)"
                >
                  恢复
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>
