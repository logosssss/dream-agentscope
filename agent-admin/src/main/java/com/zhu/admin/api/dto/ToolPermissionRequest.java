package com.zhu.admin.api.dto;

/**
 * 控制台登记的工具权限规则。conditions 不从这里传入。
 *
 * @param toolId   工具标识
 * @param tenantId 租户范围，空白按全局
 * @param roleId   角色范围，空白按全局
 * @param decision ALLOW / ASK / DENY
 * @param priority 优先级，空白按 100
 */
public record ToolPermissionRequest(
        String toolId, String tenantId, String roleId, String decision, Integer priority) {
}
