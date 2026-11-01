package com.zhu.admin.api.dto;

/**
 * 控制台登记的租户用户。状态由服务端固定为可用，不从这里传入角色。
 *
 * @param tenantId    已登记的租户
 * @param userId      用户业务标识
 * @param displayName 显示名，可以空
 */
public record TenantUserRequest(String tenantId, String userId, String displayName) {
}
