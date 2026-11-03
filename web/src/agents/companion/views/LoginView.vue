<script setup lang="ts">
/**
 * 搭子联调登录页。无密码，只登记租户用户后换 Bearer。
 */
import { login } from '@/shared/api/auth-api'
import { saveSession } from '@/shared/auth/session'
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()

const tenantId = ref(import.meta.env.VITE_DEFAULT_TENANT_ID || 'demo')
const userId = ref(import.meta.env.VITE_DEFAULT_USER_ID || 'demo-user')
const error = ref('')
const loading = ref(false)

/** 调用登录接口并写入 localStorage，再进入聊天页。 */
async function onSubmit() {
  error.value = ''
  loading.value = true
  try {
    const data = await login(tenantId.value.trim(), userId.value.trim())
    saveSession({
      accessToken: data.accessToken,
      tenantId: data.tenantId,
      userId: data.userId,
    })
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/companion'
    await router.replace(redirect || '/companion')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-full flex items-center justify-center p-6">
    <form
      class="w-full max-w-md rounded-2xl bg-[var(--color-surface-card)] p-6 shadow-sm border border-[var(--color-border)] space-y-4"
      @submit.prevent="onSubmit"
    >
      <div>
        <h1 class="text-xl font-semibold">智能搭子 · 登录</h1>
        <p class="mt-1 text-sm text-[var(--color-text-muted)]">
          无密码。请确保本机库已执行 deploy/mysql/seed-demo-tenant-user.sql。
        </p>
      </div>

      <label class="block space-y-1">
        <span class="text-sm">租户 ID</span>
        <input
          v-model="tenantId"
          class="w-full rounded-lg border border-[var(--color-border-strong)] bg-[var(--color-surface-card)] text-[var(--color-text-main)] px-3 py-2 outline-none focus-visible:ring-2 focus-visible:ring-pink-400"
          autocomplete="username"
          required
        />
      </label>

      <label class="block space-y-1">
        <span class="text-sm">用户 ID</span>
        <input
          v-model="userId"
          class="w-full rounded-lg border border-[var(--color-border-strong)] bg-[var(--color-surface-card)] text-[var(--color-text-main)] px-3 py-2 outline-none focus-visible:ring-2 focus-visible:ring-pink-400"
          required
        />
      </label>

      <p v-if="error" class="text-sm text-[var(--color-danger)]">{{ error }}</p>

      <button
        type="submit"
        class="w-full rounded-lg bg-[var(--color-brand-primary)] px-4 py-2.5 text-white disabled:opacity-60"
        :disabled="loading"
      >
        {{ loading ? '登录中…' : '登录' }}
      </button>
    </form>
  </div>
</template>
