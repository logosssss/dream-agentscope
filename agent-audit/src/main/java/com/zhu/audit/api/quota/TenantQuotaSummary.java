package com.zhu.audit.api.quota;

/**
 * 一条已登记的每日请求上限。不带已用量。
 *
 * @param tenantId  租户业务标识
 * @param quotaType 固定 REQUESTS
 * @param period    固定 DAILY
 * @param limit     上限，0 表示不限额
 */
public record TenantQuotaSummary(String tenantId, String quotaType, String period, long limit) {
}
