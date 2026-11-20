package com.zhu.admin.api.dto.model;

/**
 * 登记一条模型配置。版本固定 1.0.0，不带参数 JSON。
 *
 * @param configId    配置标识
 * @param model       模型名
 * @param provider    供应方
 * @param tenantScope 租户范围，空白按全局
 */
public record ModelConfigRequest(String configId, String model, String provider, String tenantScope) {
}
