package com.zhu.audit.api;

/**
 * 一条已登记的模型配置。不带参数 JSON。
 *
 * @param configId    配置标识
 * @param model       模型名
 * @param provider    供应方
 * @param version     这次固定 1.0.0
 * @param tenantScope 租户范围，* 表示全局
 */
public record ModelConfigSummary(
        String configId,
        String model,
        String provider,
        String version,
        String tenantScope) {
}
