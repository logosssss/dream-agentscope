<script setup lang="ts">
/**
 * 客户端登录。账号密码，密钥配齐时再显示微信扫码。
 * 不填写租户和用户标识。
 */
import { authConfig, createWxTicket, login, pollWxTicket } from '@/shared/api/auth-api'
import { saveSession } from '@/shared/auth/session'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()

const accountName = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)
const wxEnabled = ref(false)
const mode = ref<'account' | 'wx'>('account')
const wxLoading = ref(false)
const wxError = ref('')
const wxHint = ref('微信扫码后，在小程序中确认登录')
const qrcodeDataUrl = ref('')
const ticketId = ref('')
let pollTimer: ReturnType<typeof setInterval> | null = null

/** 登录成功后进入原来要去的页面。 */
async function enter(session: {
  accessToken: string
  tenantId: string
  userId: string
  displayName: string
}) {
  saveSession(session)
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/companion'
  await router.replace(redirect.startsWith('/') ? redirect : '/companion')
}

function stopPoll() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

/** 向服务器要一张票并开始查询。查询失败不打断，过期才停。 */
async function refreshWxQr() {
  wxLoading.value = true
  wxError.value = ''
  stopPoll()
  qrcodeDataUrl.value = ''
  try {
    const data = await createWxTicket()
    ticketId.value = data.ticketId
    qrcodeDataUrl.value = data.qrcodeDataUrl
    wxHint.value = '微信扫码后，在小程序中确认登录'
    pollTimer = setInterval(async () => {
      try {
        const state = await pollWxTicket(ticketId.value)
        if (state.status === 'CONFIRMED' && state.login) {
          stopPoll()
          await enter(state.login)
        } else if (state.status === 'EXPIRED') {
          stopPoll()
          wxHint.value = '二维码已过期，请点击刷新'
          wxError.value = '二维码已过期'
        }
      } catch {
        /* 单次查询失败留到下一次 */
      }
    }, 2000)
  } catch (e) {
    wxError.value = e instanceof Error ? e.message : '二维码加载失败'
  } finally {
    wxLoading.value = false
  }
}

/** 账号密码登录。 */
async function onSubmit() {
  error.value = ''
  loading.value = true
  try {
    await enter(await login(accountName.value.trim(), password.value))
  } catch (e) {
    error.value = e instanceof Error ? e.message : '登录失败'
  } finally {
    loading.value = false
  }
}

/** 切到扫码时才出码。切走就停止查询。 */
function choose(next: 'account' | 'wx') {
  mode.value = next
  if (next === 'wx' && !qrcodeDataUrl.value) {
    void refreshWxQr()
  }
  if (next !== 'wx') {
    stopPoll()
  }
}

onMounted(async () => {
  try {
    const config = await authConfig()
    wxEnabled.value = !!config.wxScanLoginEnabled
  } catch {
    wxEnabled.value = false
  }
})

onBeforeUnmount(() => stopPoll())
</script>

<template>
  <div class="min-h-full flex items-center justify-center p-6">
    <div
      class="w-full max-w-md rounded-2xl bg-[var(--color-surface-card)] p-6 shadow-sm border border-[var(--color-border)] space-y-4"
    >
      <div>
        <h1 class="text-xl font-semibold">智能搭子 · 登录</h1>
        <p class="mt-1 text-sm text-[var(--color-text-muted)]">使用账号密码登录。扫码需要已配置的小程序。</p>
      </div>

      <div v-if="wxEnabled" class="grid grid-cols-2 gap-2">
        <button
          type="button"
          class="rounded-lg border px-3 py-2 text-sm"
          :class="mode === 'account' ? 'border-[var(--color-brand-primary)]' : 'border-[var(--color-border)]'"
          @click="choose('account')"
        >
          账号密码
        </button>
        <button
          type="button"
          class="rounded-lg border px-3 py-2 text-sm"
          :class="mode === 'wx' ? 'border-[var(--color-brand-primary)]' : 'border-[var(--color-border)]'"
          @click="choose('wx')"
        >
          微信扫码
        </button>
      </div>

      <div v-if="mode === 'wx' && wxEnabled" class="space-y-3 text-center">
        <div class="mx-auto flex h-52 w-52 items-center justify-center rounded-xl border border-[var(--color-border)]">
          <img v-if="qrcodeDataUrl" :src="qrcodeDataUrl" alt="微信小程序码" class="h-48 w-48" />
          <span v-else class="text-sm text-[var(--color-text-muted)]">{{ wxLoading ? '生成中…' : '暂无二维码' }}</span>
        </div>
        <p class="text-sm text-[var(--color-text-muted)]">{{ wxHint }}</p>
        <p v-if="wxError" class="text-sm text-[var(--color-danger)]">{{ wxError }}</p>
        <button
          type="button"
          class="rounded-lg border border-[var(--color-border-strong)] px-4 py-2 text-sm disabled:opacity-60"
          :disabled="wxLoading"
          @click="refreshWxQr"
        >
          刷新二维码
        </button>
      </div>

      <form v-else class="space-y-4" @submit.prevent="onSubmit">
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
            autocomplete="current-password"
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

      <p class="text-sm text-[var(--color-text-muted)]">
        还没有账号？
        <RouterLink class="text-[var(--color-brand-primary)]" :to="{ path: '/register', query: route.query }">去注册</RouterLink>
      </p>
    </div>
  </div>
</template>
