package com.zhu.audit.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * billing_record 的一行。草稿可以确认，已确认的可以开票。付款还不在这里改。
 */
@TableName("billing_record")
public class BillingRecord {

    /** 自增主键。对外不返回。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户。 */
    private String tenantId;

    /** 区间起点，含这一刻。 */
    private LocalDateTime periodStart;

    /** 区间终点，不含这一刻。 */
    private LocalDateTime periodEnd;

    /** token 合计。 */
    private Long tokenTotal;

    /** 金额合计。 */
    private BigDecimal costAmount;

    /** 币种。 */
    private String currency;

    /** DRAFT、CONFIRMED 或 INVOICED。 */
    private String status;

    /** 主键。 */
    public Long getId() {
        return id;
    }

    /** 写入主键。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** 租户。 */
    public String getTenantId() {
        return tenantId;
    }

    /** 写入租户。 */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /** 区间起点。 */
    public LocalDateTime getPeriodStart() {
        return periodStart;
    }

    /** 写入区间起点。 */
    public void setPeriodStart(LocalDateTime periodStart) {
        this.periodStart = periodStart;
    }

    /** 区间终点。 */
    public LocalDateTime getPeriodEnd() {
        return periodEnd;
    }

    /** 写入区间终点。 */
    public void setPeriodEnd(LocalDateTime periodEnd) {
        this.periodEnd = periodEnd;
    }

    /** token 合计。 */
    public Long getTokenTotal() {
        return tokenTotal;
    }

    /** 写入 token 合计。 */
    public void setTokenTotal(Long tokenTotal) {
        this.tokenTotal = tokenTotal;
    }

    /** 金额合计。 */
    public BigDecimal getCostAmount() {
        return costAmount;
    }

    /** 写入金额合计。 */
    public void setCostAmount(BigDecimal costAmount) {
        this.costAmount = costAmount;
    }

    /** 币种。 */
    public String getCurrency() {
        return currency;
    }

    /** 写入币种。 */
    public void setCurrency(String currency) {
        this.currency = currency;
    }

    /** 状态。 */
    public String getStatus() {
        return status;
    }

    /** 写入状态。 */
    public void setStatus(String status) {
        this.status = status;
    }
}
