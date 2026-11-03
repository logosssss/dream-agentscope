package com.zhu.semantic.api;

/**
 * 一条已登记的工具权限规则。不表示已经执行过工具。
 *
 * @param toolId   工具标识
 * @param tenantId 租户范围，* 表示全部
 * @param roleId   角色范围，* 表示全部
 * @param decision ALLOW / ASK / DENY
 * @param priority 数值越小越优先
 * @param enabled  是否启用
 */
public record ToolPermissionSummary(
        String toolId,
        String tenantId,
        String roleId,
        String decision,
        int priority,
        boolean enabled) {
}
