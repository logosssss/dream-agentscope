package com.zhu.admin.api.dto.retention;

/**
 * 定位一条数据保留策略的范围。空白按全局。
 *
 * @param scope 租户范围
 */
public record RetentionPolicyScopeRequest(String scope) {
}
