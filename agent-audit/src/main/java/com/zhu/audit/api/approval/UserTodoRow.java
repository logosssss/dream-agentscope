package com.zhu.audit.api.approval;

/**
 * 一条未完成的待办。不带工具参数，也不带截止时间。
 *
 * @param id       表主键
 * @param todoType APPROVAL、CONFIRM 或 TASK
 * @param refId    审批单、运行或任务标识
 * @param title    短标题，没有时为空
 * @param status   开着是 OPEN
 */
public record UserTodoRow(Long id, String todoType, String refId, String title, String status) {
}
