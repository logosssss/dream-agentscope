package com.zhu.access.api.dto;

/**
 * 任务列表里的一行。不带任务参数或结果引用。
 *
 * @param taskId       任务标识
 * @param tenantId     租户，没有时是空串
 * @param userId       用户
 * @param sessionId    会话，可以空
 * @param taskType     任务类型
 * @param status       RUNNING / SUCCEEDED / FAILED
 * @param errorMessage 失败时的异常类型，可以空
 */
public record TaskSummary(
        String taskId,
        String tenantId,
        String userId,
        String sessionId,
        String taskType,
        String status,
        String errorMessage) {
}
