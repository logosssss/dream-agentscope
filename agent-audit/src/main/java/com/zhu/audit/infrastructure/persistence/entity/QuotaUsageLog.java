package com.zhu.audit.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * quota_usage_log 的一行。只表示一次已通过的每日请求，不表示运行已经开始。
 */
@TableName("quota_usage_log")
public class QuotaUsageLog {

    /** 自增主键。列表返回这一列，插入时由库生成。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户。没有时是空串。 */
    private String tenantId;

    /** 用户。没有时留空。 */
    private String userId;

    /** REQUESTS。这次不写别的类型。 */
    private String quotaType;

    /** 固定为 1。负数留给回补，这次不写。 */
    private Long amount;

    /** 入口名。超长时截到列宽。 */
    private String reason;

    /** 写入时间。插入时不填，用库的默认值。 */
    private LocalDateTime createdAt;

    /** 主键。 */
    public Long getId() {
        return id;
    }

    /** 写入主键。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** 租户标识。 */
    public String getTenantId() {
        return tenantId;
    }

    /** 写入租户标识。 */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /** 用户标识。 */
    public String getUserId() {
        return userId;
    }

    /** 写入用户标识。 */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /** 配额类型。 */
    public String getQuotaType() {
        return quotaType;
    }

    /** 写入配额类型。 */
    public void setQuotaType(String quotaType) {
        this.quotaType = quotaType;
    }

    /** 消耗量。 */
    public Long getAmount() {
        return amount;
    }

    /** 写入消耗量。 */
    public void setAmount(Long amount) {
        this.amount = amount;
    }

    /** 原因。 */
    public String getReason() {
        return reason;
    }

    /** 写入原因。 */
    public void setReason(String reason) {
        this.reason = reason;
    }

    /** 写入时间。 */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** 查询映射时填上这一列。插入路径不调用。 */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
