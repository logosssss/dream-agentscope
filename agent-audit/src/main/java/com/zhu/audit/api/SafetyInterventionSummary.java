package com.zhu.audit.api;

/**
 * 一条已记录的安全干预。不含用户原文。
 *
 * @param tenantId  租户，没有时是空串
 * @param userId    被干预的用户
 * @param sessionId 会话，没有时是空串
 * @param riskLevel ELEVATED 或 CRITICAL
 * @param source    KEYWORD、SEMANTIC 或 JUDGE_FAILED
 * @param summary   固定短句
 */
public record SafetyInterventionSummary(
        String tenantId,
        String userId,
        String sessionId,
        String riskLevel,
        String source,
        String summary) {
}
