package com.zhu.admin.api.dto.billing;

import java.time.LocalDateTime;

/**
 * 控制台发起的草稿账单。时间与 token_usage.created_at 使用同一时钟。
 *
 * @param tenantId    租户
 * @param periodStart 区间起点，含这一刻
 * @param periodEnd   区间终点，不含这一刻
 */
public record BillingDraftRequest(String tenantId, LocalDateTime periodStart, LocalDateTime periodEnd) {
}
