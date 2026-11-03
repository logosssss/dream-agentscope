package com.zhu.audit.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * token_usage 的一行。只放模型和 token 数，不放提示词或回复。
 */
@TableName("token_usage")
public class TokenUsage {

    /** 自增主键。对外用 runId 关联运行。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 这次运行的标识。 */
    private String runId;

    /** 租户。没有租户时是空串。 */
    private String tenantId;

    /** 用户。 */
    private String userId;

    /** 会话。没有时留空。 */
    private String sessionId;

    /** 模型坐标。 */
    private String model;

    /** 输入 token。小于 0 的值在写入前改成 0。 */
    private Long inputTokens;

    /** 输出 token。小于 0 的值在写入前改成 0。 */
    private Long outputTokens;

    /** 输入加输出。 */
    private Long totalTokens;

    /** 金额。没有价格表时为 0。 */
    private BigDecimal costAmount;

    /** 币种。当前固定 CNY。 */
    private String currency;

    /** 记账时间。详情才读，列表不带。 */
    private LocalDateTime createdAt;

    /** 主键。 */
    public Long getId() {
        return id;
    }

    /** 写入主键。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** 运行标识。 */
    public String getRunId() {
        return runId;
    }

    /** 写入运行标识。 */
    public void setRunId(String runId) {
        this.runId = runId;
    }

    /** 租户。 */
    public String getTenantId() {
        return tenantId;
    }

    /** 写入租户。 */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /** 用户。 */
    public String getUserId() {
        return userId;
    }

    /** 写入用户。 */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /** 会话。 */
    public String getSessionId() {
        return sessionId;
    }

    /** 写入会话。 */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /** 模型坐标。 */
    public String getModel() {
        return model;
    }

    /** 写入模型坐标。 */
    public void setModel(String model) {
        this.model = model;
    }

    /** 输入 token。 */
    public Long getInputTokens() {
        return inputTokens;
    }

    /** 写入输入 token。 */
    public void setInputTokens(Long inputTokens) {
        this.inputTokens = inputTokens;
    }

    /** 输出 token。 */
    public Long getOutputTokens() {
        return outputTokens;
    }

    /** 写入输出 token。 */
    public void setOutputTokens(Long outputTokens) {
        this.outputTokens = outputTokens;
    }

    /** 合计 token。 */
    public Long getTotalTokens() {
        return totalTokens;
    }

    /** 写入合计 token。 */
    public void setTotalTokens(Long totalTokens) {
        this.totalTokens = totalTokens;
    }

    /** 金额。 */
    public BigDecimal getCostAmount() {
        return costAmount;
    }

    /** 写入金额。 */
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

    /** 记账时间。 */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** 写入记账时间。 */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
