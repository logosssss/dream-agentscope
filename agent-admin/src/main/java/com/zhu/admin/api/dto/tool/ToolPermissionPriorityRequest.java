package com.zhu.admin.api.dto.tool;

/**
 * 改一条工具权限规则的优先级。空白租户/角色按全局。
 *
 * @param tenantId 租户范围
 * @param roleId   角色范围
 * @param priority 新优先级，从 0 起
 */
public record ToolPermissionPriorityRequest(String tenantId, String roleId, Integer priority) {
}
