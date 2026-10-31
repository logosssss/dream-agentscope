package com.zhu.audit.api;

/**
 * 一个已登记的租户。不带表主键，也不带用户。
 *
 * @param tenantId 租户业务标识
 * @param name     名称
 * @param plan     套餐，登记时是 standard
 * @param status   ACTIVE、SUSPENDED 或 CLOSED
 */
public record TenantSummary(String tenantId, String name, String plan, String status) {
}
