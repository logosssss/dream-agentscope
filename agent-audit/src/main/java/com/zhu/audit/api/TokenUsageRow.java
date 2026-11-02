package com.zhu.audit.api;

import java.math.BigDecimal;

/**
 * 一条模型调用的 token 用量。不带提示词或回复。
 *
 * @param id           表主键
 * @param runId        关联运行
 * @param tenantId     租户，没有时是空串
 * @param userId       用户
 * @param sessionId    会话，可以空
 * @param model        模型坐标
 * @param inputTokens  输入 token
 * @param outputTokens 输出 token
 * @param totalTokens  合计
 * @param costAmount   金额，没有价格时为 0
 * @param currency     币种
 */
public record TokenUsageRow(
        Long id,
        String runId,
        String tenantId,
        String userId,
        String sessionId,
        String model,
        Long inputTokens,
        Long outputTokens,
        Long totalTokens,
        BigDecimal costAmount,
        String currency) {
}
