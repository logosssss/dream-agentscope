package com.zhu.audit.api;

/**
 * 一个已登记的租户用户。不带表主键，也不带角色。
 *
 * @param tenantId    租户业务标识
 * @param userId      用户业务标识
 * @param displayName 显示名，没有时为空
 * @param status      登记时是 ACTIVE
 */
public record TenantUserSummary(String tenantId, String userId, String displayName, String status) {
}
