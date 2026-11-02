package com.zhu.access.api.dto;

/**
 * 用户登录入参。没有密码字段，只核对已登记且仍可用的租户用户。
 *
 * @param tenantId 租户。空串按空租户交给门
 * @param userId   用户业务标识
 */
public record LoginRequest(String tenantId, String userId) {
}
