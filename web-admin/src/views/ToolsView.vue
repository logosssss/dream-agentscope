<script setup lang="ts">
/**
 * 工具目录：列表 + 登记。不执行工具，也不写权限规则。
 */
import { listToolRegistries, registerToolRegistry } from '@/shared/api/tool-api'
import { hasAdminToken } from '@/shared/auth/operator'
import type { ToolRegistrySummary } from '@/shared/types/api.types'
import { onMounted, ref } from 'vue'

const rows = ref<ToolRegistrySummary[]>([])
const loading = ref(false)
const error = ref('')
const submitting = ref(false)

const toolId = ref('')
const name = ref('')
const moduleName = ref('')
const description = ref('')

/** 刷新最近目录。 */
async function refresh() {
  if (!hasAdminToken()) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    rows.value = await listToolRegistries()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

/** 登记一条；版本由服务端固定 1.0.0。 */
async function onRegister() {
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

onMounted(() => {
  void refresh()
})
</script>

<template>
  <div class="space-y-4 max-w-5xl">
    <div class="flex items-center justify-between gap-3">
      <h2 class="text-lg font-semibold">工具目录</h2>
      <button
        type="button"
        class="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50"
        :disabled="loading"
        @click="refresh"
      >
        刷新
      </button>
    </div>

    <p class="text-xs text-[var(--color-text-muted)]">
      版本固定 1.0.0。只登记标识；没有对应代码实现时运行层仍不会挂到 Toolkit。不写权限规则。
    </p>

    <p v-if="error" class="text-sm text-[var(--color-danger)]">{{ error }}</p>
    <p v-if="loading" class="text-sm text-[var(--color-text-muted)]">加载中…</p>

    <form
      class="flex flex-wrap gap-2 items-end bg-white border border-slate-200 rounded-lg p-3"
      @submit.prevent="onRegister"
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
          <tr v-if="!loading && rows.length === 0">
            <td colspan="5" class="px-3 py-6 text-center text-[var(--color-text-muted)]">暂无工具</td>
          </tr>
          <tr v-for="row in rows" :key="row.toolId" class="border-t border-slate-100">
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
  </div>
</template>
