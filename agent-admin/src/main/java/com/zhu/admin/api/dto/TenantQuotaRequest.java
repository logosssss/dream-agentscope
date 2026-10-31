package com.zhu.admin.api.dto;

/**
 * 控制台登记的每日请求上限。类型和周期由服务端固定，不从这里传入。
 *
 * @param tenantId 租户业务标识
 * @param limit    上限，0 表示不限额
 */
public record TenantQuotaRequest(String tenantId, Long limit) {
}
