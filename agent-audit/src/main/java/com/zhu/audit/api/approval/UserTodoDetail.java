package com.zhu.audit.api.approval;

/**
 * 一条待办详情。含已关闭行和创建时间，仍不带截止时间和工具参数。
 *
 * @param id        表主键
 * @param todoType  APPROVAL、CONFIRM 或 TASK
 * @param refId     审批单、运行或任务标识
 * @param title     短标题，没有时为空
 * @param status    OPEN / DONE / CANCELLED
 * @param createdAt 创建时间，UTC，没有时区后缀；没有时为空串
 */
public record UserTodoDetail(
        Long id,
        String todoType,
        String refId,
        String title,
        String status,
        String createdAt) {
}
