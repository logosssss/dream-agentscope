<script setup lang="ts">
/**
 * 模型配置与单价：列表 + 登记。配置不改旧版；单价只追加新行。
 */
import {
  addModelPrice,
  disableModelConfig,
  listModelConfigs,
  listModelPrices,
  registerModelConfig,
  restoreModelConfig,
} from '@/shared/api/model-api'
import { hasAdminToken } from '@/shared/auth/operator'
import type { ModelConfigSummary, ModelPriceEntry } from '@/shared/types/api.types'
import { computed, onMounted, ref, watch } from 'vue'

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

/** 与运行层 resolveModel 一致：供应方:模型。 */
const configCoordPreview = computed(() => {
  const p = provider.value.trim()
  const m = model.value.trim()
  if (!p || !m) {
    return ''
  }
  return `${p}:${m}`
})

/** 列表行上的运行坐标。 */
function runtimeCoord(row: ModelConfigSummary): string {
  return `${row.provider}:${row.model}`
}

const priceModel = ref('')
const currency = ref('CNY')
const inputPer1m = ref('')
const outputPer1m = ref('')
const effectiveFrom = ref('')
const effectiveTo = ref('')
const busyId = ref('')

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

/** 停用仍启用的配置。 */
async function onDisableConfig(row: ModelConfigSummary) {
  if (busyId.value) {
    return
  }
  if (!window.confirm(`停用模型配置 ${row.configId}？对话将不再选用它。`)) {
    return
  }
  busyId.value = row.configId
  error.value = ''
  try {
    await disableModelConfig(row.configId)
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '停用失败'
  } finally {
    busyId.value = ''
  }
}

/** 恢复已停用的配置。 */
async function onRestoreConfig(row: ModelConfigSummary) {
  if (busyId.value) {
    return
  }
  busyId.value = row.configId
  error.value = ''
  try {
    await restoreModelConfig(row.configId)
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '恢复失败'
  } finally {
    busyId.value = ''
  }
}

/**
 * 表单值转成可 trim 的字符串。
 * type=number 的 v-model 可能是 number，直接 .trim 会报错。
 */
function asTrimmed(value: string | number | null | undefined): string {
  if (value == null) {
    return ''
  }
  return String(value).trim()
}

/** 追加模型单价。 */
async function onAddPrice() {
  const m = asTrimmed(priceModel.value)
  const input = asTrimmed(inputPer1m.value)
  const output = asTrimmed(outputPer1m.value)
  if (!m || !input || !output || submitting.value) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    await addModelPrice({
      model: m,
      currency: asTrimmed(currency.value),
      inputPer1m: input,
      outputPer1m: output,
      effectiveFrom: asTrimmed(effectiveFrom.value),
      effectiveTo: asTrimmed(effectiveTo.value),
    })
    priceModel.value = ''
    inputPer1m.value = ''
    outputPer1m.value = ''
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
      <p class="text-xs text-[var(--color-text-muted)] leading-relaxed">
        版本固定 1.0.0；租户范围空表示全局 `*`。不写参数 JSON。
        运行时会拼成 <code class="font-mono">供应方:模型</code>，必须是 AgentScope 能识别的英文坐标（如
        <code class="font-mono">dashscope:qwen-plus</code>），不要写「百炼」这类展示名。
        同配置 ID 若已停用，再次登记会<strong>改登并启用</strong>（覆盖供应方/模型/范围），无需删除；仍启用则拒绝重复。
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
          <input
            v-model="model"
            class="block w-44 rounded-md border border-slate-300 px-2 py-1.5"
            placeholder="qwen-plus"
            required
          />
        </label>
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">供应方（英文）</span>
          <input
            v-model="provider"
            class="block w-40 rounded-md border border-slate-300 px-2 py-1.5"
            placeholder="dashscope"
            required
          />
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
        <p
          v-if="configCoordPreview"
          class="basis-full text-xs text-[var(--color-text-muted)]"
        >
          运行坐标预览：
          <code class="font-mono text-[var(--color-brand-primary)]">{{ configCoordPreview }}</code>
        </p>
      </form>

      <div class="overflow-x-auto bg-white border border-slate-200 rounded-lg">
        <table class="w-full text-sm text-left">
          <thead class="bg-slate-50 text-[var(--color-text-muted)]">
            <tr>
              <th class="px-3 py-2 font-medium">配置 ID</th>
              <th class="px-3 py-2 font-medium">模型</th>
              <th class="px-3 py-2 font-medium">供应方</th>
              <th class="px-3 py-2 font-medium">运行坐标</th>
              <th class="px-3 py-2 font-medium">版本</th>
              <th class="px-3 py-2 font-medium">租户范围</th>
              <th class="px-3 py-2 font-medium">启用</th>
              <th class="px-3 py-2 font-medium">动作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!loading && configs.length === 0">
              <td colspan="8" class="px-3 py-6 text-center text-[var(--color-text-muted)]">暂无配置</td>
            </tr>
            <tr v-for="row in configs" :key="`${row.configId}:${row.version}`" class="border-t border-slate-100">
              <td class="px-3 py-2 font-mono text-xs">{{ row.configId }}</td>
              <td class="px-3 py-2">{{ row.model }}</td>
              <td class="px-3 py-2">{{ row.provider }}</td>
              <td class="px-3 py-2 font-mono text-xs">{{ runtimeCoord(row) }}</td>
              <td class="px-3 py-2">{{ row.version }}</td>
              <td class="px-3 py-2 font-mono text-xs">{{ row.tenantScope }}</td>
              <td class="px-3 py-2">{{ row.enabled ? '是' : '否' }}</td>
              <td class="px-3 py-2 whitespace-nowrap">
                <button
                  v-if="row.enabled"
                  type="button"
                  class="text-sm text-[var(--color-danger)] disabled:opacity-50"
                  :disabled="busyId === row.configId"
                  @click="onDisableConfig(row)"
                >
                  停用
                </button>
                <button
                  v-else
                  type="button"
                  class="text-sm text-[var(--color-brand-primary)] disabled:opacity-50"
                  :disabled="busyId === row.configId"
                  @click="onRestoreConfig(row)"
                >
                  恢复
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <template v-else>
      <p class="text-xs text-[var(--color-text-muted)]">
        只追加新行，不改旧价。生效开始空则用服务端现在；结束空表示仍有效。单价为元/百万 token（与百炼等一致）。
        「模型」填完整运行坐标，须与配置页一致，例如
        <code class="font-mono">dashscope:qwen-plus</code>。
      </p>
      <form
        class="flex flex-wrap gap-2 items-end bg-white border border-slate-200 rounded-lg p-3"
        @submit.prevent="onAddPrice"
      >
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">模型（运行坐标）</span>
          <input
            v-model="priceModel"
            class="block w-56 rounded-md border border-slate-300 px-2 py-1.5"
            placeholder="dashscope:qwen-plus"
            required
          />
        </label>
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">币种</span>
          <input v-model="currency" class="block w-20 rounded-md border border-slate-300 px-2 py-1.5" />
        </label>
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">输入/百万</span>
          <input
            v-model="inputPer1m"
            type="number"
            min="0"
            step="0.000001"
            class="block w-28 rounded-md border border-slate-300 px-2 py-1.5"
            required
          />
        </label>
        <label class="text-sm space-y-1">
          <span class="text-[var(--color-text-muted)]">输出/百万</span>
          <input
            v-model="outputPer1m"
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
              <th class="px-3 py-2 font-medium">输入/百万</th>
              <th class="px-3 py-2 font-medium">输出/百万</th>
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
              <td class="px-3 py-2">{{ row.inputPer1m }}</td>
              <td class="px-3 py-2">{{ row.outputPer1m }}</td>
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
