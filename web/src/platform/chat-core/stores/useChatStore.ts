import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 多 Agent 可扩展的消息存储。复合键 agentId:sessionId 隔离会话。
 */

/** 一条展示用消息。streaming 表示助手气泡仍在追加。 */
export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  streaming?: boolean
}

export const useChatStore = defineStore('chat', () => {
  const sessions = ref<Record<string, ChatMessage[]>>({})

  /** 取或创建某会话的消息列表引用。 */
  function getMessages(agentId: string, sessionId: string): ChatMessage[] {
    const key = `${agentId}:${sessionId}`
    if (!sessions.value[key]) {
      sessions.value[key] = []
    }
    return sessions.value[key]
  }

  /** 追加一条消息并返回它，便于流式改 content。 */
  function appendMessage(agentId: string, sessionId: string, message: ChatMessage): ChatMessage {
    const list = getMessages(agentId, sessionId)
    list.push(message)
    return message
  }

  /** 清空某会话本地消息（新对话时用）。 */
  function clearMessages(agentId: string, sessionId: string): void {
    sessions.value[`${agentId}:${sessionId}`] = []
  }

  return { sessions, getMessages, appendMessage, clearMessages }
})
