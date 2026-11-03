import { apiJson } from '@/shared/api/http'
import type { UserTodoDetail, UserTodoRow } from '@/shared/types/api.types'

/**
 * 当前用户的待办。列表只含还开着的，也可以登记确认或任务。
 * 完成和取消只作用于确认和任务，审批仍走搭子里的决定。
 */

/** 还开着的待办。默认 50 条。 */
export function listTodos(limit = 50): Promise<UserTodoRow[]> {
  const q = new URLSearchParams({ limit: String(limit) })
  return apiJson<UserTodoRow[]>(`/api/todos?${q}`)
}

/** 一条待办的类型、状态和创建时间。别人的主键按找不到。 */
export function getTodo(todoId: number): Promise<UserTodoDetail> {
  return apiJson<UserTodoDetail>(`/api/todos/${encodeURIComponent(String(todoId))}`)
}

/** 把还开着的确认或任务改成完成。 */
export function completeTodo(todoId: number): Promise<UserTodoRow> {
  return apiJson<UserTodoRow>(`/api/todos/${encodeURIComponent(String(todoId))}/done`, {
    method: 'POST',
  })
}

/** 把还开着的确认或任务取消。 */
export function cancelTodo(todoId: number): Promise<UserTodoRow> {
  return apiJson<UserTodoRow>(`/api/todos/${encodeURIComponent(String(todoId))}/cancel`, {
    method: 'POST',
  })
}

/**
 * 新开一条确认或任务。不传审批类型。
 * 不检查关联标识是否存在。同一用户、类型和标识再开一次会冲突。
 */
export function createTodo(
  todoType: 'CONFIRM' | 'TASK',
  refId: string,
  title: string,
): Promise<UserTodoRow> {
  return apiJson<UserTodoRow>('/api/todos', {
    method: 'POST',
    body: JSON.stringify({
      todoType,
      refId,
      title: title || null,
    }),
  })
}
