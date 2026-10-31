package com.zhu.semantic.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * tool_permission 的一行。规则来自表，不在这里写决定。
 */
@TableName("tool_permission")
public class ToolPermission {

    /** 自增主键。查询用，匹配不看它。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工具标识。和调用方的工具名精确对应。 */
    private String toolId;

    /** 租户。* 表示全部租户。 */
    private String tenantId;

    /** 角色。* 表示全部角色。 */
    private String roleId;

    /** ALLOW、ASK 或 DENY。 */
    private String decision;

    /** 条件表达式。非空时这条规则先不参与匹配。 */
    private String conditions;

    /** 数值越小越优先。 */
    private Integer priority;

    /** 关闭的规则不查出。 */
    private Boolean enabled;

    /** 主键。 */
    public Long getId() {
        return id;
    }

    /** 写入主键。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** 工具标识。 */
    public String getToolId() {
        return toolId;
    }

    /** 写入工具标识。 */
    public void setToolId(String toolId) {
        this.toolId = toolId;
    }

    /** 租户范围。 */
    public String getTenantId() {
        return tenantId;
    }

    /** 写入租户范围。 */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /** 角色范围。 */
    public String getRoleId() {
        return roleId;
    }

    /** 写入角色范围。 */
    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    /** 决定文本。 */
    public String getDecision() {
        return decision;
    }

    /** 写入决定文本。 */
    public void setDecision(String decision) {
        this.decision = decision;
    }

    /** 条件。空表示无条件。 */
    public String getConditions() {
        return conditions;
    }

    /** 写入条件。 */
    public void setConditions(String conditions) {
        this.conditions = conditions;
    }

    /** 优先级。空时按 100。 */
    public Integer getPriority() {
        return priority;
    }

    /** 写入优先级。 */
    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    /** 是否启用。 */
    public Boolean getEnabled() {
        return enabled;
    }

    /** 写入是否启用。 */
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
