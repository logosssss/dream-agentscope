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

  /**
   * 追加一条消息并返回数组里的响应式对象。
   * 必须返回 list 里的项，不能返回入参：改入参不会触发视图更新，流式会看起来像一次蹦出。
   */
  function appendMessage(agentId: string, sessionId: string, message: ChatMessage): ChatMessage {
    const list = getMessages(agentId, sessionId)
    list.push(message)
    return list[list.length - 1]
  }

  /** 清空某会话本地消息（新对话时用）。 */
  function clearMessages(agentId: string, sessionId: string): void {
    sessions.value[`${agentId}:${sessionId}`] = []
  }

  /** 按 id 去掉一条。发送失败且助手还没正文时用，避免留下空白气泡。 */
  function dropMessage(agentId: string, sessionId: string, messageId: string): void {
    const list = sessions.value[`${agentId}:${sessionId}`]
    if (!list) {
      return
    }
    const index = list.findIndex((item) => item.id === messageId)
    if (index >= 0) {
      list.splice(index, 1)
    }
  }

  /**
   * 用服务端历史整表替换本地气泡。
   * 切会话时调用，避免和内存里未完成的流式半截混在一起。
   */
  function replaceMessages(agentId: string, sessionId: string, messages: ChatMessage[]): void {
    sessions.value[`${agentId}:${sessionId}`] = messages.map((m) => ({ ...m }))
  }

  return { sessions, getMessages, appendMessage, clearMessages, dropMessage, replaceMessages }
})
