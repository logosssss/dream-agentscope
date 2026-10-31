package com.zhu.audit.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * operation_audit 的一行。只记人在后台做了什么，不记 Agent 事件。
 */
@TableName("operation_audit")
public class OperationAudit {

    /** 自增主键。对外不返回。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人。 */
    private String operatorId;

    /** 租户。没有时是空串。 */
    private String tenantId;

    /** 动作。 */
    private String operation;

    /** 对象类型。 */
    private String objectType;

    /** 对象标识。没有时留空。 */
    private String objectId;

    /** 主键。 */
    public Long getId() {
        return id;
    }

    /** 写入主键。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** 操作人。 */
    public String getOperatorId() {
        return operatorId;
    }

    /** 写入操作人。 */
    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    /** 租户。 */
    public String getTenantId() {
        return tenantId;
    }

    /** 写入租户。 */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /** 动作。 */
    public String getOperation() {
        return operation;
    }

    /** 写入动作。 */
    public void setOperation(String operation) {
        this.operation = operation;
    }

    /** 对象类型。 */
    public String getObjectType() {
        return objectType;
    }

    /** 写入对象类型。 */
    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    /** 对象标识。 */
    public String getObjectId() {
        return objectId;
    }

    /** 写入对象标识。 */
    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }
}
