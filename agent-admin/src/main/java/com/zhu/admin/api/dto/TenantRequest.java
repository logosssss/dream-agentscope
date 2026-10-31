package com.zhu.admin.api.dto;

/**
 * 控制台登记的租户。套餐和状态由服务端固定，不从这里传入。
 *
 * @param tenantId 租户业务标识
 * @param name     名称
 */
public record TenantRequest(String tenantId, String name) {
}
