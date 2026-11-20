<script setup lang="ts">
/**
 * 路由探测页：只挂 ChatContainer，显式打 echo-v1。
 * 验 Gateway 按 agentId 路由；真模型、过额度；不做会话侧栏与历史。
 */
import ChatContainer from '@/platform/chat-core/components/ChatContainer.vue'
import { logout as logoutApi } from '@/shared/api/auth-api'
import { clearSession, loadSession } from '@/shared/auth/session'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

const AGENT_ID = 'echo-v1'

const router = useRouter()
const session = loadSession()
const sessionId = ref<string>(crypto.randomUUID())
const identity = computed(() => session?.displayName || '已登录')

/** 换新会话，本地气泡按新 sessionId 隔离。 */
function newChat() {
  sessionId.value = crypto.randomUUID()
}

/** 退出登录。 */
async function onLogout() {
  const token = loadSession()?.accessToken
  try {
    if (token) {
      await logoutApi(token)
    }
  } catch {
    // 仍清本地
  }
  clearSession()
  await router.replace('/login')
}
</script>

<template>
  <ChatContainer
    class="h-full"
    :agent-id="AGENT_ID"
    :session-id="sessionId"
    title="路由探测"
    :identity="identity"
    empty-hint="这是 Gateway 路由探测：走 echo-v1（真模型、短提示、无搭子工具）。与智能搭子共用额度。"
  >
    <template #header-actions>
      <button
        type="button"
        class="rounded-lg border border-[var(--color-border-strong)] px-3 py-1.5 text-sm hover:bg-[var(--color-surface-muted)]"
        @click="newChat"
      >
        新对话
      </button>
      <button
        type="button"
        class="rounded-lg border border-[var(--color-border-strong)] px-3 py-1.5 text-sm hover:bg-[var(--color-surface-muted)]"
        @click="onLogout"
      >
        退出
      </button>
    </template>
  </ChatContainer>
</template>
