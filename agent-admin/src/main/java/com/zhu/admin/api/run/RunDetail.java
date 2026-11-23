package com.zhu.admin.api.run;

import java.util.List;

/**
 * 管理端看到的一次运行。只读摘要，不带回对话全文。
 *
 * @param runId         运行标识
 * @param tenantId      租户
 * @param userId        用户
 * @param sessionId     会话
 * @param requestId     调用方请求号，可能为空串
 * @param triggerType   USER 或 API
 * @param status        SUCCEEDED、FAILED 或 CANCELLED
 * @param model         模型坐标
 * @param inputSummary  输入摘要
 * @param outputSummary 输出摘要
 * @param errorCode     失败时的异常类型
 * @param durationMs    耗时毫秒
 * @param startedAt     开始时间，ISO 字符串
 * @param finishedAt    结束时间，ISO 字符串
 * @param steps         这一轮的工具/推理步骤
 */
public record RunDetail(
        String runId,
        String tenantId,
        String userId,
        String sessionId,
        String requestId,
        String triggerType,
        String status,
        String model,
        String inputSummary,
        String outputSummary,
        String errorCode,
        Long durationMs,
        String startedAt,
        String finishedAt,
        List<RunStepRow> steps) {
}
