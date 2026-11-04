<script setup lang="ts">
/**
 * 客户端注册。只要账号和密码，成功后直接进入系统。
 */
import SiteComplianceFooter from '@/platform/compliance/SiteComplianceFooter.vue'
import { register } from '@/shared/api/auth-api'
import { saveSession } from '@/shared/auth/session'
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()

const accountName = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

/** 注册成功后写入会话并进入原来要去的页面。 */
async function onSubmit() {
  error.value = ''
  loading.value = true
  try {
    const data = await register(accountName.value.trim(), password.value)
    saveSession({
      accessToken: data.accessToken,
      tenantId: data.tenantId,
      userId: data.userId,
      displayName: data.displayName,
    })
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/companion'
    await router.replace(redirect.startsWith('/') ? redirect : '/companion')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '注册失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-full flex flex-col">
    <div class="flex-1 flex items-center justify-center p-6">
    <form
      class="w-full max-w-md rounded-2xl bg-[var(--color-surface-card)] p-6 shadow-sm border border-[var(--color-border)] space-y-4"
      @submit.prevent="onSubmit"
    >
      <div>
        <h1 class="text-xl font-semibold">智能搭子 · 注册</h1>
        <p class="mt-1 text-sm text-[var(--color-text-muted)]">账号 3 到 32 个字符，密码至少 6 位。</p>
      </div>
      <label class="block space-y-1">
        <span class="text-sm">账号</span>
        <input
          v-model="accountName"
          class="w-full rounded-lg border border-[var(--color-border-strong)] bg-[var(--color-surface-card)] text-[var(--color-text-main)] px-3 py-2 outline-none focus-visible:ring-2 focus-visible:ring-pink-400"
          autocomplete="username"
          required
        />
      </label>
      <label class="block space-y-1">
        <span class="text-sm">密码</span>
        <input
          v-model="password"
          type="password"
          class="w-full rounded-lg border border-[var(--color-border-strong)] bg-[var(--color-surface-card)] text-[var(--color-text-main)] px-3 py-2 outline-none focus-visible:ring-2 focus-visible:ring-pink-400"
          autocomplete="new-password"
          required
        />
      </label>
      <p v-if="error" class="text-sm text-[var(--color-danger)]">{{ error }}</p>
      <button
        type="submit"
        class="w-full rounded-lg bg-[var(--color-brand-primary)] px-4 py-2.5 text-white disabled:opacity-60"
        :disabled="loading"
      >
        {{ loading ? '注册中…' : '注册并进入' }}
      </button>
      <p class="text-sm text-[var(--color-text-muted)]">
        已有账号？
        <RouterLink class="text-[var(--color-brand-primary)]" :to="{ path: '/login', query: route.query }">去登录</RouterLink>
      </p>
    </form>
    </div>
    <SiteComplianceFooter />
  </div>
</template>
