package com.zhu.access.api.dto.chat;

/**
 * 运行列表里的一行。只带短摘要，不包含对话全文或异常原文。
 *
 * @param runId         这次运行的标识
 * @param tenantId      租户，没有时是空串
 * @param userId        用户
 * @param sessionId     会话
 * @param triggerType   USER 或 API
 * @param status        SUCCEEDED / FAILED / CANCELLED
 * @param model         模型坐标，可以空
 * @param inputSummary  输入摘要
 * @param outputSummary 输出摘要
 * @param errorCode     失败时的异常类型，可以空
 * @param durationMs    耗时毫秒，可以空
 */
public record RunSummary(
        String runId,
        String tenantId,
        String userId,
        String sessionId,
        String triggerType,
        String status,
        String model,
        String inputSummary,
        String outputSummary,
        String errorCode,
        Long durationMs) {
}
