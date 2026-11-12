package com.zhu.access.api.dto.account;

/**
 * 当前用户登记的确认或任务待办。审批待办不从这里开。
 *
 * @param todoType CONFIRM 或 TASK
 * @param refId    运行或任务标识
 * @param title    短标题，可以空
 */
public record TodoRequest(String todoType, String refId, String title) {
}
