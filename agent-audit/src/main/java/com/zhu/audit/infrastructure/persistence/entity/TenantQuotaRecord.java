package com.zhu.audit.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * tenant_quota 的一行。只登记和改每日请求上限，已用量留给 Redis。
 */
@TableName("tenant_quota")
public class TenantQuotaRecord {

    /** 自增主键。对外不返回。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户业务标识。 */
    private String tenantId;

    /** REQUESTS。这次不写别的类型。 */
    private String quotaType;

    /** DAILY。这次不写月或累计。 */
    private String period;

    /** 上限。0 表示不限额。 */
    private Long limitValue;

    /** 固定为 1。停用行不在这次写入。 */
    private Integer enabled;

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

    /** 配额类型。 */
    public String getQuotaType() {
        return quotaType;
    }

    /** 写入配额类型。 */
    public void setQuotaType(String quotaType) {
        this.quotaType = quotaType;
    }

    /** 周期。 */
    public String getPeriod() {
        return period;
    }

    /** 写入周期。 */
    public void setPeriod(String period) {
        this.period = period;
    }

    /** 上限。 */
    public Long getLimitValue() {
        return limitValue;
    }

    /** 写入上限。 */
    public void setLimitValue(Long limitValue) {
        this.limitValue = limitValue;
    }

    /** 是否启用。 */
    public Integer getEnabled() {
        return enabled;
    }

    /** 写入启用标记。 */
    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
