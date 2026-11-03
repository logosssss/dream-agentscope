<script setup lang="ts">
/**
 * 占位 Agent 页：只挂 ChatContainer，验证与搭子切换后消息互不串。
 * 不做会话侧栏与历史拉取。
 */
import ChatContainer from '@/platform/chat-core/components/ChatContainer.vue'
import { logout as logoutApi } from '@/shared/api/auth-api'
import { clearSession, loadSession } from '@/shared/auth/session'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

const AGENT_ID = 'sketch-v1'

const router = useRouter()
const session = loadSession()
const sessionId = ref<string>(crypto.randomUUID())
const identity = computed(() => session?.displayName || '已登录')

/** 换新会话，本地气泡清空由新 key 自然隔离。 */
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
    title="速记草稿"
    :identity="identity"
    empty-hint="这是占位 Agent，用来验证侧栏切换。消息与搭子互不共享。"
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
