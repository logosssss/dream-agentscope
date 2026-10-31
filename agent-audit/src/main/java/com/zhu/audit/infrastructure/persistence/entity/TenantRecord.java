package com.zhu.audit.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * tenant 的一行。登记后默认可用。停用不在这次写入里改。
 */
@TableName("tenant")
public class TenantRecord {

    /** 自增主键。对外不返回。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户业务标识。 */
    private String tenantId;

    /** 名称。 */
    private String name;

    /** 套餐。 */
    private String plan;

    /** ACTIVE、SUSPENDED 或 CLOSED。 */
    private String status;

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

    /** 名称。 */
    public String getName() {
        return name;
    }

    /** 写入名称。 */
    public void setName(String name) {
        this.name = name;
    }

    /** 套餐。 */
    public String getPlan() {
        return plan;
    }

    /** 写入套餐。 */
    public void setPlan(String plan) {
        this.plan = plan;
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
