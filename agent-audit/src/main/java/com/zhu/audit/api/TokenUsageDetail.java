package com.zhu.audit.api;

import java.math.BigDecimal;

/**
 * 一条 token 用量的详情。含记账时间，仍不带提示词，也不改金额。
 *
 * @param id           表主键
 * @param runId        关联运行
 * @param tenantId     租户，没有时是空串
 * @param sessionId    会话，可以空
 * @param model        模型坐标
 * @param inputTokens  输入 token
 * @param outputTokens 输出 token
 * @param totalTokens  合计
 * @param costAmount   金额，没有价格时为 0
 * @param currency     币种
 * @param createdAt    记账时间，UTC，没有时区后缀；没有时为空串
 */
public record TokenUsageDetail(
        Long id,
        String runId,
        String tenantId,
        String sessionId,
        String model,
        Long inputTokens,
        Long outputTokens,
        Long totalTokens,
        BigDecimal costAmount,
        String currency,
        String createdAt) {
}
