package com.zhu.audit.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 一张草稿账单。审计接口对外的形状，不带表主键。
 *
 * @param tenantId    租户
 * @param periodStart 区间起点，含这一刻
 * @param periodEnd   区间终点，不含这一刻
 * @param tokenTotal  区间内 token 合计
 * @param costAmount  区间内金额合计
 * @param currency    币种
 * @param status      当前固定 DRAFT
 */
public record BillingSummary(
        String tenantId,
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        long tokenTotal,
        BigDecimal costAmount,
        String currency,
        String status) {
}
