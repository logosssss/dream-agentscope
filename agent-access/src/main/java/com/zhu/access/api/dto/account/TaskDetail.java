package com.zhu.access.api.dto.account;

/**
 * 一次同步任务的详情。带开始结束时间，仍不读任务参数。
 *
 * @param taskId       任务标识
 * @param tenantId     租户，没有时是空串
 * @param userId       用户
 * @param sessionId    会话，可以空
 * @param taskType     任务类型
 * @param status       RUNNING / SUCCEEDED / FAILED
 * @param errorMessage 失败时的异常类型，可以空
 * @param startedAt    开始时间，UTC，没有时区后缀；没有时为空串
 * @param finishedAt   结束时间，UTC，没有时区后缀；进行中为空串
 */
public record TaskDetail(
        String taskId,
        String tenantId,
        String userId,
        String sessionId,
        String taskType,
        String status,
        String errorMessage,
        String startedAt,
        String finishedAt) {
}
