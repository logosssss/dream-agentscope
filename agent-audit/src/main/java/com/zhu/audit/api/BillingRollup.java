package com.zhu.audit.api;

import java.time.LocalDateTime;

/**
 * 把一个租户在一段时间内的 token 用量收成账单。付款不在这里。
 */
public interface BillingRollup {

    /**
     * 生成一张 DRAFT。同一租户和同一区间只能有一张。
     * 区间按 token_usage.created_at 比较，最长 366 天。多种币种时拒绝，避免把不同币种加在一起。
     */
    BillingSummary draft(String tenantId, LocalDateTime periodStart, LocalDateTime periodEnd);

    /**
     * 把已有草稿改成 CONFIRMED。不重算 token 和金额。
     * 没有这张账单时按找不到处理。已经不是草稿时按冲突处理。
     */
    BillingSummary confirm(String tenantId, LocalDateTime periodStart, LocalDateTime periodEnd);

    /**
     * 把已确认的账单改成 INVOICED。不重算 token 和金额。
     * 草稿不能直接开票。没有这张账单时按找不到处理。已经不是已确认时按冲突处理。
     */
    BillingSummary invoice(String tenantId, LocalDateTime periodStart, LocalDateTime periodEnd);
}
