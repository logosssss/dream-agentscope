package com.zhu.semantic.api;

import java.util.List;

/**
 * 管理进程登记、停用、恢复和改优先级工具权限规则。不执行工具，也不解释条件 JSON。
 */
public interface ToolPermissionCatalog {

    /**
     * 新增一条。同一工具+租户+角色再登记是冲突。conditions 固定为空。
     */
    ToolPermissionSummary register(
            String toolId, String tenantId, String roleId, String decision, Integer priority);

    /**
     * 把启用规则改成停用。没有这一行或已停用由实现返回失败。
     */
    ToolPermissionSummary disable(String toolId, String tenantId, String roleId);

    /**
     * 把停用规则改回启用。没有这一行或已启用由实现返回失败。
     */
    ToolPermissionSummary restore(String toolId, String tenantId, String roleId);

    /**
     * 改已有规则的优先级。不改决定与启用状态。没有这一行返回找不到。
     */
    ToolPermissionSummary updatePriority(
            String toolId, String tenantId, String roleId, Integer priority);

    /**
     * 最近登记的规则，含停用。不读 conditions 正文以外的解释。
     */
    List<ToolPermissionSummary> recent(int limit);
}
