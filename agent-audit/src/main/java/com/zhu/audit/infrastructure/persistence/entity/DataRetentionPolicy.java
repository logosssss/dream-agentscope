package com.zhu.audit.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * data_retention_policy 的一行。只描述策略，不触发清理。
 */
@TableName("data_retention_policy")
public class DataRetentionPolicy {

    /** 自增主键。对外不返回。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 数据类型。 */
    private String dataType;

    /** 租户范围。* 表示全局。 */
    private String scope;

    /** 保留天数。 */
    private Integer retainDays;

    /** ARCHIVE / DELETE / ANONYMIZE。 */
    private String action;

    /** 1 启用。 */
    private Integer enabled;

    /** 主键。 */
    public Long getId() {
        return id;
    }

    /** 写入主键。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** 数据类型。 */
    public String getDataType() {
        return dataType;
    }

    /** 写入数据类型。 */
    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    /** 范围。 */
    public String getScope() {
        return scope;
    }

    /** 写入范围。 */
    public void setScope(String scope) {
        this.scope = scope;
    }

    /** 保留天数。 */
    public Integer getRetainDays() {
        return retainDays;
    }

    /** 写入保留天数。 */
    public void setRetainDays(Integer retainDays) {
        this.retainDays = retainDays;
    }

    /** 动作。 */
    public String getAction() {
        return action;
    }

    /** 写入动作。 */
    public void setAction(String action) {
        this.action = action;
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
