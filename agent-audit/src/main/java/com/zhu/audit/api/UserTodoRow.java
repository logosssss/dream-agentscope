package com.zhu.audit.api;

/**
 * 一条未完成的审批待办。不带工具参数，也不带截止时间。
 *
 * @param id       表主键
 * @param todoType 固定 APPROVAL
 * @param refId    审批单标识
 * @param title    工具名，没有时为空
 * @param status   固定 OPEN
 */
public record UserTodoRow(Long id, String todoType, String refId, String title, String status) {
}
