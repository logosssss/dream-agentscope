package com.zhu.ops.tenant;

/**
 * 一个已登记的租户用户。不带表主键，也不带角色。
 *
 * @param tenantId    租户业务标识
 * @param userId      用户业务标识
 * @param displayName 显示名，没有时为空
 * @param status      登记时是 ACTIVE
 * @param createdAt   登记时间，ISO 字符串。对话鉴权路径不读这一列，给空串
 * @param updatedAt   最近一次改动时间，ISO 字符串。没读到时为空串
 */
public record TenantUserSummary(
        String tenantId,
        String userId,
        String displayName,
        String status,
        String createdAt,
        String updatedAt) {
}
