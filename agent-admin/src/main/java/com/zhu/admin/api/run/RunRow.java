package com.zhu.admin.api.run;

/**
 * 管理端的一次运行摘要。列表用，不带步骤。
 *
 * @param runId         运行标识
 * @param tenantId      租户
 * @param userId        用户
 * @param sessionId     会话
 * @param requestId     调用方请求号，可能为空串
 * @param clientIp      来源地址，没有时为空串
 * @param triggerType   USER 或 API
 * @param status        SUCCEEDED、FAILED 或 CANCELLED
 * @param model         模型坐标
 * @param inputSummary  输入摘要
 * @param outputSummary 输出摘要
 * @param errorCode     失败时的异常类型
 * @param durationMs    耗时毫秒
 * @param startedAt     开始时间，ISO 字符串
 * @param finishedAt    结束时间，ISO 字符串
 */
public record RunRow(
        String runId,
        String tenantId,
        String userId,
        String sessionId,
        String requestId,
        String clientIp,
        String triggerType,
        String status,
        String model,
        String inputSummary,
        String outputSummary,
        String errorCode,
        Long durationMs,
        String startedAt,
        String finishedAt) {

    /** 列表和详情共用一个转换，避免两处字段顺序漂移。 */
    public static RunRow of(com.zhu.readmodel.entity.run.AgentRun row, String startedAt, String finishedAt) {
        return new RunRow(
                row.getRunId(),
                row.getTenantId(),
                row.getUserId(),
                row.getSessionId(),
                row.getRequestId() == null ? "" : row.getRequestId(),
                row.getClientIp() == null ? "" : row.getClientIp(),
                row.getTriggerType(),
                row.getStatus(),
                row.getModel(),
                row.getInputSummary(),
                row.getOutputSummary(),
                row.getErrorCode(),
                row.getDurationMs(),
                startedAt,
                finishedAt);
    }
}
