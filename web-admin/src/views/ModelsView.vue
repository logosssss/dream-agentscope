<script setup lang="ts">
/**
 * 模型配置与单价：列表 + 登记。配置不改旧版；单价只追加新行。
 */
import {
  addModelPrice,
  listModelConfigs,
  listModelPrices,
  registerModelConfig,
} from '@/shared/api/model-api'
import { hasAdminToken } from '@/shared/auth/operator'
import type { ModelConfigSummary, ModelPriceEntry } from '@/shared/types/api.types'
import { onMounted, ref, watch } from 'vue'

type Tab = 'configs' | 'prices'

const tab = ref<Tab>('configs')
const configs = ref<ModelConfigSummary[]>([])
const prices = ref<ModelPriceEntry[]>([])
const loading = ref(false)
const error = ref('')
const submitting = ref(false)

const configId = ref('')
const model = ref('')
const provider = ref('')
const tenantScope = ref('')

const priceModel = ref('')
const currency = ref('CNY')
const inputPer1k = ref('')
const outputPer1k = ref('')
const effectiveFrom = ref('')
const effectiveTo = ref('')

/** 按当前页签刷新。 */
async function refresh() {
  if (!hasAdminToken()) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    if (tab.value === 'configs') {
      configs.value = await listModelConfigs()
    } else {
      prices.value = await listModelPrices()
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

/** 登记模型配置。 */
async function onRegisterConfig() {
  const id = configId.value.trim()
  const m = model.value.trim()
  const p = provider.value.trim()
  if (!id || !m || !p || submitting.value) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    await registerModelConfig({
      configId: id,
      model: m,
      provider: p,
      tenantScope: tenantScope.value.trim(),
    })
    configId.value = ''
    model.value = ''
    provider.value = ''
    tenantScope.value = ''
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '登记失败'
  } finally {
    submitting.value = false
  }
}

/** 追加模型单价。 */
async function onAddPrice() {
  const m = priceModel.value.trim()
  if (!m || !inputPer1k.value.trim() || !outputPer1k.value.trim() || submitting.value) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    await addModelPrice({
      model: m,
      currency: currency.value.trim(),
      inputPer1k: inputPer1k.value.trim(),
      outputPer1k: outputPer1k.value.trim(),
      effectiveFrom: effectiveFrom.value.trim(),
      effectiveTo: effectiveTo.value.trim(),
    })
    priceModel.value = ''
    inputPer1k.value = ''
    outputPer1k.value = ''
    effectiveFrom.value = ''
    effectiveTo.value = ''
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '登记失败'
  } finally {
    submitting.value = false
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
      <h2 class="text-lg font-semibold">模型</h2>
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
        :class="tab === 'configs'
          ? 'bg-blue-50 text-[var(--color-brand-primary)] font-medium'
          : 'text-[var(--color-text-muted)] hover:bg-slate-50'"
        @click="tab = 'configs'"
      >
        配置
      </button>
      <button
        type="button"
        class="rounded-md px-3 py-1.5 text-sm"
        :class="tab === 'prices'
          ? 'bg-blue-50 text-[var(--color-brand-primary)] font-medium'
          : 'text-[var(--color-text-muted)] hover:bg-slate-50'"
        @click="tab = 'prices'"
      >
        单价
      </button>
    </div>

    <p v-if="error" class="text-sm text-[var(--color-danger)]">{{ error }}</p>
    <p v-if="loading" class="text-sm text-[var(--color-text-muted)]">加载中…</p>

    <template v-if="tab === 'configs'">
      <p class="text-xs text-[var(--color-text-muted)]">
        版本固定 1.0.0；租户范围空表示全局 `*`。不写参数 JSON。
      </p>
      <form
        class="flex flex-wrap gap-2 items-end bg-white border border-slate-200 rounded-lg p-3"
        @submit.prevent="onRegisterConfig"
      >
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">配置 ID</span>
          <input v-model="configId" class="block w-40 rounded-md border border-slate-300 px-2 py-1.5" required />
        </label>
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">模型</span>
          <input v-model="model" class="block w-44 rounded-md border border-slate-300 px-2 py-1.5" required />
        </label>
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">供应方</span>
          <input v-model="provider" class="block w-36 rounded-md border border-slate-300 px-2 py-1.5" required />
        </label>
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">租户范围</span>
          <input
            v-model="tenantScope"
            class="block w-36 rounded-md border border-slate-300 px-2 py-1.5"
            placeholder="空=全局"
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
              <th class="px-3 py-2 font-medium">配置 ID</th>
              <th class="px-3 py-2 font-medium">模型</th>
              <th class="px-3 py-2 font-medium">供应方</th>
              <th class="px-3 py-2 font-medium">版本</th>
              <th class="px-3 py-2 font-medium">租户范围</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!loading && configs.length === 0">
              <td colspan="5" class="px-3 py-6 text-center text-[var(--color-text-muted)]">暂无配置</td>
            </tr>
            <tr v-for="row in configs" :key="`${row.configId}:${row.version}`" class="border-t border-slate-100">
              <td class="px-3 py-2 font-mono text-xs">{{ row.configId }}</td>
              <td class="px-3 py-2">{{ row.model }}</td>
              <td class="px-3 py-2">{{ row.provider }}</td>
              <td class="px-3 py-2">{{ row.version }}</td>
              <td class="px-3 py-2 font-mono text-xs">{{ row.tenantScope }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <template v-else>
      <p class="text-xs text-[var(--color-text-muted)]">
        只追加新行，不改旧价。生效开始空则用服务端现在；结束空表示仍有效。单价为每千 token。
      </p>
      <form
        class="flex flex-wrap gap-2 items-end bg-white border border-slate-200 rounded-lg p-3"
        @submit.prevent="onAddPrice"
      >
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">模型</span>
          <input v-model="priceModel" class="block w-44 rounded-md border border-slate-300 px-2 py-1.5" required />
        </label>
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">币种</span>
          <input v-model="currency" class="block w-20 rounded-md border border-slate-300 px-2 py-1.5" />
        </label>
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">输入/千</span>
          <input
            v-model="inputPer1k"
            type="number"
            min="0"
            step="0.000001"
            class="block w-28 rounded-md border border-slate-300 px-2 py-1.5"
            required
          />
        </label>
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">输出/千</span>
          <input
            v-model="outputPer1k"
            type="number"
            min="0"
            step="0.000001"
            class="block w-28 rounded-md border border-slate-300 px-2 py-1.5"
            required
          />
        </label>
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">生效开始</span>
          <input
            v-model="effectiveFrom"
            type="datetime-local"
            class="block rounded-md border border-slate-300 px-2 py-1.5"
          />
        </label>
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">生效结束</span>
          <input
            v-model="effectiveTo"
            type="datetime-local"
            class="block rounded-md border border-slate-300 px-2 py-1.5"
          />
        </label>
        <button
          type="submit"
          class="rounded-md bg-[var(--color-brand-primary)] px-3 py-1.5 text-sm text-white disabled:opacity-50"
          :disabled="submitting"
        >
          追加
        </button>
      </form>

      <div class="overflow-x-auto bg-white border border-slate-200 rounded-lg">
        <table class="w-full text-sm text-left">
          <thead class="bg-slate-50 text-[var(--color-text-muted)]">
            <tr>
              <th class="px-3 py-2 font-medium">ID</th>
              <th class="px-3 py-2 font-medium">模型</th>
              <th class="px-3 py-2 font-medium">币种</th>
              <th class="px-3 py-2 font-medium">输入/千</th>
              <th class="px-3 py-2 font-medium">输出/千</th>
              <th class="px-3 py-2 font-medium">生效</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!loading && prices.length === 0">
              <td colspan="6" class="px-3 py-6 text-center text-[var(--color-text-muted)]">暂无单价</td>
            </tr>
            <tr v-for="row in prices" :key="row.id" class="border-t border-slate-100">
              <td class="px-3 py-2 font-mono text-xs">{{ row.id }}</td>
              <td class="px-3 py-2">{{ row.model }}</td>
              <td class="px-3 py-2">{{ row.currency }}</td>
              <td class="px-3 py-2">{{ row.inputPer1k }}</td>
              <td class="px-3 py-2">{{ row.outputPer1k }}</td>
              <td class="px-3 py-2 text-xs text-[var(--color-text-muted)]">
                {{ row.effectiveFrom || '—' }}
                ~
                {{ row.effectiveTo || '仍有效' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>
