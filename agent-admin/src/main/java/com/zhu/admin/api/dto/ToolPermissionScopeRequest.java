package com.zhu.admin.api.dto;

/**
 * 定位一条工具权限规则的租户与角色。空白按全局。
 *
 * @param tenantId 租户范围
 * @param roleId   角色范围
 */
public record ToolPermissionScopeRequest(String tenantId, String roleId) {
}
